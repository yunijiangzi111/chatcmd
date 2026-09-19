package io.github.xiaoh.chatcmd.core;

/**
 * 服务器实况适配：有些服务器的指令语法和原版不一样，照原版发会被服务端判错。
 *
 * <p>目前只有一处：部分服务器的 {@code /enchant}（附魔）被换成了带子指令的
 * {@code enchant add|remove}（添加 / 移除）形式，直接发原版写法
 * {@code enchant @s <附魔> <等级>} 会得到「错误的命令参数」。
 *
 * <p>这件事客户端是能自己看出来的：指令树由服务器下发，直接读树就知道这台服务器要哪种写法。
 * 读树那一步在平台层做（要用 Minecraft 的类），本类只负责纯字符串改写，方便写单测。
 */
public final class ServerCompat {

    /** 原版 {@code /enchant} 的字面量，含尾随空格。 */
    private static final String ENCHANT = "enchant ";

    /** {@code add}（添加）子指令，含尾随空格。 */
    private static final String ADD = "add ";

    private ServerCompat() {
    }

    /**
     * 服务器要求 {@code /enchant} 走子指令形式时，给它补上 {@code add}。
     *
     * <p>例：{@code enchant @s minecraft:efficiency 5}
     * → {@code enchant add @s minecraft:efficiency 5}（目标和其余参数都保持原样）。
     *
     * @param command        已经拼好的指令串（不含前导斜杠）
     * @param serverNeedsAdd 服务器下发的指令树显示 {@code /enchant} 必须有 {@code add} 子指令
     * @return 适配后的指令串；不是 enchant 指令、或服务器没这个要求时原样返回
     */
    public static String adaptEnchant(String command, boolean serverNeedsAdd) {
        if (!serverNeedsAdd || !command.startsWith(ENCHANT)) {
            return command;
        }
        String rest = command.substring(ENCHANT.length());
        // 已经是子指令写法就别再插一次
        return rest.startsWith(ADD) ? command : ENCHANT + ADD + rest;
    }
}