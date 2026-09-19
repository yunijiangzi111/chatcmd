package io.github.xiaoh.chatcmd.neoforge;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;

import io.github.xiaoh.chatcmd.core.AliasTable;
import io.github.xiaoh.chatcmd.core.CommandParser;
import io.github.xiaoh.chatcmd.core.EnchantSyntax;
import io.github.xiaoh.chatcmd.core.ParseResult;
import io.github.xiaoh.chatcmd.core.ServerCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * ChatCmd 聊天指令模组的主入口。
 *
 * <p>本模组只是「中文指令翻译宏」：拦截聊天框里以 {@code #} 开头的输入，
 * 翻译成原版指令，再以玩家自己的身份发出去。
 * <b>它不绕过任何权限</b> —— 能否生效完全由服务端按玩家权限判定。
 *
 * <p>声明 {@code dist = Dist.CLIENT}：这里是客户端模组，
 * 误装到专用服务器上时不会加载这些客户端类，也就不会崩服。
 */
@Mod(value = ChatCmdMod.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ChatCmdMod.MOD_ID, value = Dist.CLIENT)
public class ChatCmdMod {

    public static final String MOD_ID = "chatcmd";
    public static final Logger LOGGER = LogUtils.getLogger();

    // MC 1.21.1 用物品组件语法：give @s stick[enchantments={...}]
    private static final CommandParser PARSER = new CommandParser(AliasTable.defaultTable(),
            new RegistryItemIdResolver(), EnchantSyntax.ITEM_COMPONENT);

    /**
     * 逃生通道的重入守卫。
     *
     * <p>为什么需要它：{@code ClientChatEvent}（客户端聊天事件）就是在
     * {@code ClientPacketListener.sendChat}（客户端连接层的「发聊天」方法）<b>第一行</b>触发的。
     * 逃生通道若直接调 {@code sendChat("#你好")}，会立刻再触发一次本事件，
     * 而内层消息只有一个 {@code #}，于是被当成「认不出动词」弹出红字报错，
     * 同时被 {@code setCanceled(true)} 拦下 —— 结果玩家看到一句莫名其妙的报错，那句话其实没发出去。
     *
     * <p>事件是<b>同步</b>触发的，所以用一个静态标志位就足够：置位期间内层事件直接放行。
     */
    private static boolean sendingEscapeChat;

    /**
     * 「正在等结构查找的回执」标记。
     *
     * <p>{@code /locate} 的结果是服务器异步回话的，发完指令拿不到坐标，
     * 只能先挂个标记，等紧接着收到的那条系统消息里去找坐标。
     * 不管那条是不是回执，收到就清掉 —— 免得误伤后面某条恰好带坐标的聊天。
     */
    private static boolean awaitingLocate;

    /**
     * 待确认的高危指令。
     *
     * <p>{@code #清除 怪物}、{@code #重置 全部} 这类不可逆的指令不会被立刻发出去，
     * 而是先存在这里并弹一句警告；玩家再发一条 {@code #确认} 才真正执行，
     * 发任何别的输入（包括普通聊天）都当放弃。
     */
    private static List<String> pendingConfirm = List.of();

    /** 回执里的坐标格式：{@code [123, ~, 456]}，中英文逗号都收。 */
    private static final Pattern LOCATE_COORDS =
            Pattern.compile("\\[\\s*(-?\\d+)\\s*[,，]\\s*(~|-?\\d+)\\s*[,，]\\s*(-?\\d+)\\s*\\]");

    /**
     * 快捷键：按 {@code #}（也就是 Shift+3）直接打开聊天框并预填 {@code #}，
     * 省掉「先按 T 再打 #」这一步。原版的 {@code /} 键就是这么干的。
     *
     * <p>玩家可在「选项 → 控制」里自行改键。注意：数字键本身也是原版的快捷栏切换键，
     * 所以按住 Shift 按 3 有可能连带切换快捷栏第 3 格 —— 嫌烦就改绑到别的键。
     */
    public static final KeyMapping OPEN_CHAT = new KeyMapping(
            "key.chatcmd.open_chat",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_3,
            "key.categories.chatcmd");

    public ChatCmdMod(IEventBus modEventBus) {
        // 快捷键注册属于模组事件总线（IModBusEvent），不能挂在游戏事件总线上
        modEventBus.addListener(ChatCmdMod::onRegisterKeyMappings);
        LOGGER.info("ChatCmd 聊天指令模组已加载，输入 # 开头的消息即可使用");
    }

    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAT);
    }

    /** 轮询快捷键：按下就把聊天框打开，并预填一个触发前缀。 */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
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
        // 除了「确认 / 取消」这两句回话，任何别的输入都让上一次的待确认作废
        if (result.status() != ParseResult.Status.CONFIRM
                && result.status() != ParseResult.Status.CONFIRM_ACCEPT
                && result.status() != ParseResult.Status.CONFIRM_DECLINE) {
            pendingConfirm = List.of();
        }
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
                    executeCommands(player, result.commands());
                    // 模糊匹配纠错时必须明说，绝不静默改词
                    if (!result.hint().isEmpty()) {
                        player.displayClientMessage(
                                Component.literal("[ChatCmd] " + result.hint())
                                        .withStyle(ChatFormatting.YELLOW),
                                false);
                    }
                }
            }
            case CONFIRM -> {
                // 高危指令：先不发，把待执行的指令暂存下来，等玩家再确认一次
                event.setCanceled(true);
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    pendingConfirm = result.commands();
                    player.displayClientMessage(
                            Component.literal("[ChatCmd] " + result.hint())
                                    .withStyle(ChatFormatting.GOLD),
                            false);
                    displaySuggestions(player, "点这里确认或取消：", result.suggestions());
                }
            }
            case CONFIRM_ACCEPT -> {
                event.setCanceled(true);
                LocalPlayer player = Minecraft.getInstance().player;
                List<String> pending = pendingConfirm;
                pendingConfirm = List.of();
                if (player != null) {
                    if (pending.isEmpty()) {
                        player.displayClientMessage(
                                Component.literal("[ChatCmd] 当前没有待确认的指令")
                                        .withStyle(ChatFormatting.GRAY),
                                false);
                    } else {
                        executeCommands(player, pending);
                    }
                }
            }
            case CONFIRM_DECLINE -> {
                event.setCanceled(true);
                LocalPlayer player = Minecraft.getInstance().player;
                boolean hadPending = !pendingConfirm.isEmpty();
                pendingConfirm = List.of();
                if (player != null) {
                    player.displayClientMessage(
                            Component.literal("[ChatCmd] "
                                    + (hadPending ? "已取消待确认的指令" : "当前没有待确认的指令"))
                                    .withStyle(ChatFormatting.GRAY),
                            false);
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
                    displaySuggestions(player, "你是不是想用：", result.suggestions());
                }
            }
        }
    }

    /**
     * 按顺序把指令发出去，并逐条本地回显。
     *
     * <p>与原版 {@code ChatScreen.handleChatInput} 完全一致：指令走
     * {@code connection.sendCommand}（不含斜杠的指令串）。
     * <b>刻意不绕过任何权限校验</b>，能否生效由服务端按玩家权限判定。
     */
    private static void executeCommands(LocalPlayer player, List<String> commands) {
        // 指令树是服务器下发的，先看一眼这台服务器的 /enchant 是不是子指令形式
        boolean enchantNeedsAdd = serverEnchantNeedsAdd();
        for (String raw : commands) {
            String command = ServerCompat.adaptEnchant(raw, enchantNeedsAdd);
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
    }

    /**
     * 这台服务器的 {@code /enchant} 是否被换成了 {@code enchant add|remove}（添加 / 移除）形式。
     *
     * <p>原版是 {@code /enchant <目标> <附魔> [等级]}，没有子指令。部分服务器（或服务端插件）
     * 会把它换成带 {@code add} / {@code remove} 子指令的版本，这时再发原版写法只会得到
     * 「错误的命令参数」。指令树由服务器下发到客户端，直接读树就能判断，不必猜。
     *
     * <p>只有原版参数节点（{@code targets}）<b>不在</b>、而 {@code add} 子指令
     * <b>在</b>时才认为需要补 —— 两者并存说明原版写法仍然可用，就不插手。
     */
    private static boolean serverEnchantNeedsAdd() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        CommandNode<SharedSuggestionProvider> enchant = connection.getCommands().getRoot().getChild("enchant");
        if (enchant == null || enchant.getChild("targets") != null) {
            return false;
        }
        return enchant.getChild("add") instanceof LiteralCommandNode;
    }

    /**
     * 把 {@code /locate} 回执里的坐标渲染成「点一下直接传送」。
     *
     * <p>原版自己的坐标用的是 {@code SUGGEST_COMMAND}（点击只填入聊天框，还得再按回车），
     * 这里额外补一行 {@code RUN_COMMAND}（点击立即执行）的，点一下就到。
     *
     * <p>注意 {@code RUN_COMMAND} 的值<b>必须以 {@code /} 开头</b>，
     * 否则客户端会直接拒绝执行（原版 {@code Screen.handleComponentClicked} 里的硬性判断）。
     *
     * <p>纵坐标沿用原版回执里的 {@code ~}（保持玩家当前高度），与原版建议的传送指令一致。
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
     * <p>用 {@code SUGGEST_COMMAND}（「填入聊天框」动作）而不是 {@code RUN_COMMAND}（「直接执行」动作）：
     * 点一下只是把候选填进聊天框，玩家还能改，回车才真正发出去 —— 猜错也不会造成后果。
     */
    private static void displaySuggestions(LocalPlayer player, String tip, List<String> suggestions) {
        if (suggestions.isEmpty()) {
            return;
        }
        MutableComponent line = Component.literal("[ChatCmd] " + tip)
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
