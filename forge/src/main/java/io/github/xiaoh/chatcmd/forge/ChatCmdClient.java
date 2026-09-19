package io.github.xiaoh.chatcmd.forge;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.xiaoh.chatcmd.core.AliasTable;
import io.github.xiaoh.chatcmd.core.CommandParser;
import io.github.xiaoh.chatcmd.core.EnchantSyntax;
import io.github.xiaoh.chatcmd.core.ParseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/**
 * ChatCmd 在 Forge 1.20.1 上的客户端实现：聊天拦截、坐标点击传送、候选渲染、快捷键。
 *
 * <p>
 * 整个类只在客户端加载（{@code value = Dist.CLIENT}）。入口 {@link ChatCmdMod}
 * 用 {@code DistExecutor} 延迟引用它，因此专用服务器上这个类连同它引用的
 * {@code KeyMapping} 等客户端专属类都不会被加载。
 *
 * <p>
 * 解析引擎用的是 core 的 NBT 档位：1.20.1 还没有物品组件语法，
 * 附魔要写成 {@code give @s minecraft:stick{Enchantments:[{id:"...",lvl:255s}]}}。
 */
@Mod.EventBusSubscriber(modid = ChatCmdMod.MOD_ID, value = Dist.CLIENT)
public final class ChatCmdClient {

    private static final CommandParser PARSER =
            new CommandParser(AliasTable.defaultTable(), new RegistryItemIdResolver(), EnchantSyntax.NBT);

    /**
     * 逃生通道的重入守卫。
     *
     * <p>
     * 为什么需要它：{@code ClientChatEvent}（客户端聊天事件）就是在
     * {@code ClientPacketListener.sendChat}（客户端连接层的「发聊天」方法）<b>第一行</b>触发的。
     * 逃生通道若直接调 {@code sendChat("#你好")}，会立刻再触发一次本事件，
     * 而内层消息只有一个 {@code #}，于是被当成「认不出动词」弹出红字报错，
     * 同时被 {@code setCanceled(true)} 拦下 —— 结果玩家看到一句莫名其妙的报错，那句话其实没发出去。
     *
     * <p>
     * 事件是<b>同步</b>触发的，所以用一个静态标志位就足够：置位期间内层事件直接放行。
     */
    private static boolean sendingEscapeChat;

    /**
     * 「正在等结构查找的回执」标记。
     *
     * <p>
     * {@code /locate} 的结果是服务器异步回话的，发完指令拿不到坐标，
     * 只能先挂个标记，等紧接着收到的那条系统消息里去找坐标。
     * 不管那条是不是回执，收到就清掉 —— 免得误伤后面某条恰好带坐标的聊天。
     */
    private static boolean awaitingLocate;

    /** 回执里的坐标格式：{@code [123, ~, 456]}，中英文逗号都收。 */
    private static final Pattern LOCATE_COORDS =
            Pattern.compile("\\[\\s*(-?\\d+)\\s*[,，]\\s*(~|-?\\d+)\\s*[,，]\\s*(-?\\d+)\\s*\\]");

    /**
     * 快捷键：按 {@code #}（也就是 Shift+3）直接打开聊天框并预填 {@code #}，
     * 省掉「先按 T 再打 #」这一步。原版的 {@code /} 键就是这么干的。
     *
     * <p>
     * 玩家可在「选项 → 控制」里自行改键。注意：数字键本身也是原版的快捷栏切换键，
     * 所以按住 Shift 按 3 有可能连带切换快捷栏第 3 格 —— 嫌烦就改绑到别的键。
     */
    private static final KeyMapping OPEN_CHAT = new KeyMapping(
            "key.chatcmd.open_chat",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_3,
            "key.categories.chatcmd");

    /** 本类全是静态内容，不允许实例化。 */
    private ChatCmdClient() {
    }

    /**
     * 把快捷键挂到模组事件总线上。
     *
     * <p>
     * 由 {@link ChatCmdMod} 的构造函数经 {@code DistExecutor} 只在客户端调用 ——
     * 所以这里可以放心引用 {@code RegisterKeyMappingsEvent} 这类客户端专属的类。
     */
    static void registerKeyMappings() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ChatCmdClient::onRegisterKeyMappings);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAT);
    }

    /**
     * 轮询快捷键：按下就把聊天框打开，并预填一个触发前缀。
     *
     * <p>
     * 1.20.1 没有 NeoForge 那种「只在一 tick 末尾触发一次」的 {@code ClientTickEvent.Post}，
     * Forge 的 {@code TickEvent.ClientTickEvent} 一 tick 会发两次（START / END），
     * 所以这里显式只处理 END，避免重复处理按键。
     */
    @SubscribeEvent
    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CHAT.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new ChatScreen(CommandParser.TRIGGER));
            }
        }
    }

    /** 拦截聊天框提交。 */
    @SubscribeEvent
    static void onClientChat(ClientChatEvent event) {
        if (sendingEscapeChat) {
            // 这是逃生通道自己发出去的普通聊天，放行即可，不能再拦一次
            return;
        }

        ParseResult result = PARSER.parse(event.getMessage());
        switch (result.status()) {
            case NOT_MY_INPUT -> {
                // 不归本模组管，原样放行
            }
            case ESCAPE_CHAT -> {
                // 逃生通道：去掉一个 # 后作为普通聊天发出
                event.setCanceled(true);
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    sendingEscapeChat = true;
                    try {
                        player.connection.sendChat(result.payload());
                    } finally {
                        sendingEscapeChat = false;
                    }
                    // 明确告诉玩家「这条没被当成指令」，避免以为自己写错了
                    player.displayClientMessage(
                            Component.literal("[ChatCmd] 已作为普通聊天发出：" + result.payload())
                                    .withStyle(ChatFormatting.GRAY),
                            false);
                }
            }
            case OK -> {
                event.setCanceled(true);
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    // 一句中文口语可能对应多条指令（如「永为白昼」），按顺序逐条发。
                    // 与原版 ChatScreen.handleChatInput 完全一致：
                    // 指令走 connection.sendCommand(不含斜杠的指令串)，普通聊天走 sendChat。
                    // 这里刻意不绕过任何权限校验，能否生效由服务端按玩家权限判定。
                    for (String command : result.commands()) {
                        player.connection.sendCommand(command);
                        // 结构查找的回执要等服务器回话，先挂上「等下一条消息」的标记
                        if (command.startsWith("locate structure ")) {
                            awaitingLocate = true;
                        }
                        // 保留本地回显，让玩家看得见实际发出了什么
                        player.displayClientMessage(
                                Component.literal("[ChatCmd] 已执行：/" + command)
                                        .withStyle(ChatFormatting.GRAY),
                                false);
                    }
                    // 模糊匹配纠错时必须明说，绝不静默改词
                    if (!result.hint().isEmpty()) {
                        player.displayClientMessage(
                                Component.literal("[ChatCmd] " + result.hint())
                                        .withStyle(ChatFormatting.YELLOW),
                                false);
                    }
                }
            }
            default -> {
                // UNKNOWN_VERB / AMBIGUOUS_VERB / BAD_ARGS / UNKNOWN_VALUE
                event.setCanceled(true);
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(
                            Component.literal("[ChatCmd] " + result.hint())
                                    .withStyle(ChatFormatting.RED),
                            false);
                    displaySuggestions(player, result.suggestions());
                }
            }
        }
    }

    /**
     * 把 {@code /locate} 回执里的坐标渲染成「点一下直接传送」。
     *
     * <p>
     * 原版自己的坐标用的是 {@code SUGGEST_COMMAND}（点击只填入聊天框，还得再按回车），
     * 这里额外补一行 {@code RUN_COMMAND}（点击立即执行）的，点一下就到。
     *
     * <p>
     * 注意 {@code RUN_COMMAND} 的值<b>必须以 {@code /} 开头</b>，
     * 否则客户端会直接拒绝执行（原版 {@code Screen.handleComponentClicked} 里的硬性判断）。
     *
     * <p>
     * 纵坐标沿用原版回执里的 {@code ~}（保持玩家当前高度），与原版建议的传送指令一致。
     */
    @SubscribeEvent
    static void onClientChatReceived(ClientChatReceivedEvent event) {
        if (!awaitingLocate) {
            return;
        }
        // 只等紧跟着的那一条，不管是不是回执都清掉标记
        awaitingLocate = false;
        Matcher matcher = LOCATE_COORDS.matcher(event.getMessage().getString());
        if (!matcher.find()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String target = matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3);
        Style style = Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @s " + target))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("点击直接传送到 " + target)));
        player.displayClientMessage(
                Component.literal("[ChatCmd] 点击传送 → ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("[" + target + "]").withStyle(style)),
                false);
    }

    /**
     * 把候选渲染成一行<b>可点击</b>的文字。
     *
     * <p>
     * 用 {@code SUGGEST_COMMAND}（「填入聊天框」动作）而不是 {@code RUN_COMMAND}（「直接执行」动作）：
     * 点一下只是把候选填进聊天框，玩家还能改，回车才真正发出去 —— 猜错也不会造成后果。
     */
    private static void displaySuggestions(LocalPlayer player, List<String> suggestions) {
        if (suggestions.isEmpty()) {
            return;
        }
        MutableComponent line = Component.literal("[ChatCmd] 你是不是想用：")
                .withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < suggestions.size(); i++) {
            if (i > 0) {
                line.append(Component.literal("  ").withStyle(ChatFormatting.GRAY));
            }
            String suggestion = suggestions.get(i);
            Style style = Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击填入聊天框，回车前还能改")));
            line.append(Component.literal(suggestion).withStyle(style));
        }
        player.displayClientMessage(line, false);
    }
}