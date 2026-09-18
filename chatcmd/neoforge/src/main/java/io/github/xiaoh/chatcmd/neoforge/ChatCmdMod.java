package io.github.xiaoh.chatcmd.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;

import io.github.xiaoh.chatcmd.core.AliasTable;
import io.github.xiaoh.chatcmd.core.CommandParser;
import io.github.xiaoh.chatcmd.core.ParseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatEvent;
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

    private static final CommandParser PARSER =
            new CommandParser(AliasTable.defaultTable(), new RegistryItemIdResolver());

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
                    player.connection.sendChat(result.payload());
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
                    // 与原版 ChatScreen.handleChatInput 完全一致：
                    // 指令走 connection.sendCommand(不含斜杠的指令串)，普通聊天走 sendChat。
                    // 这里刻意不绕过任何权限校验，能否生效由服务端按玩家权限判定。
                    player.connection.sendCommand(result.payload());
                    // 保留本地回显，让玩家看得见实际发出了什么
                    player.displayClientMessage(
                            Component.literal("[ChatCmd] 已执行：/" + result.payload())
                                    .withStyle(ChatFormatting.GRAY),
                            false);
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
                }
            }
        }
    }
}
