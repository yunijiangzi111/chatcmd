package io.github.xiaoh.chatcmd.forge;

import com.mojang.logging.LogUtils;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * ChatCmd 聊天指令模组的 Forge 1.20.1 主入口。
 *
 * <p>
 * 本模组只是「中文指令翻译宏」：拦截聊天框里以 {@code #} 开头的输入，
 * 翻译成原版指令，再以玩家自己的身份发出去。
 * <b>它不绕过任何权限</b> —— 能否生效完全由服务端按玩家权限判定。
 *
 * <p>
 * <b>为什么这里只有一个构造函数、别的什么都不写：</b>
 * Forge 1.20.1 的 {@code @Mod} 注解没有 NeoForge 那种 {@code dist} 参数，
 * 主类在任何物理侧（客户端 / 专用服务器）都会被加载。
 * 而 Forge 官方文档明确要求「一侧模组也应当能在另一侧加载并什么都不做」，
 * 所以客户端代码整体放进 {@link ChatCmdClient}，这里只经 {@link DistExecutor}
 * 延迟引用 —— 在专用服务器上那个 lambda 根本不会执行，
 * {@code KeyMapping} 之类只存在于客户端的类也就不会被加载，自然不会崩服。
 */
@Mod(ChatCmdMod.MOD_ID)
public class ChatCmdMod {

    public static final String MOD_ID = "chatcmd";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChatCmdMod() {
        // 快捷键注册属于模组事件总线（IModBusEvent），不能挂在游戏事件总线上
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ChatCmdClient::registerKeyMappings);
        LOGGER.info("ChatCmd 聊天指令模组已加载，输入 # 开头的消息即可使用");
    }
}