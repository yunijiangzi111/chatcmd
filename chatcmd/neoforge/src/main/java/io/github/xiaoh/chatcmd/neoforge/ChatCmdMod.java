package io.github.xiaoh.chatcmd.neoforge;

import com.mojang.logging.LogUtils;

import io.github.xiaoh.chatcmd.core.AliasTable;
import io.github.xiaoh.chatcmd.core.CommandParser;
import io.github.xiaoh.chatcmd.core.ParseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatEvent;
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

    public ChatCmdMod() {
        LOGGER.info("ChatCmd 聊天指令模组已加载，输入 # 开头的消息即可使用");
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
