package io.github.xiaoh.chatcmd.core;

/**
 * 支持的指令模板类型。
 *
 * <p>每个类型描述「生成什么指令」和「需要几个参数」，具体组装逻辑在 {@link CommandParser} 里。
 */
public enum CommandType {

    /** 给予物品：{@code /give @s <item> <count>} */
    GIVE("give", 1, 2, false),
    /** 设置时间：{@code /time set <enum>} */
    TIME("time set", 1, 1, true),
    /** 传送：{@code /tp @s <x> <y> <z>} */
    TP("tp", 3, 3, false),
    /** 切换游戏模式：{@code /gamemode <enum>} */
    GAMEMODE("gamemode", 1, 1, true),
    /** 设置天气：{@code /weather <enum>} */
    WEATHER("weather", 1, 1, true),
    /** 设置难度：{@code /difficulty <enum>} */
    DIFFICULTY("difficulty", 1, 1, true),
    /** 修改游戏规则：{@code /gamerule <rule> <true|false>} */
    GAMERULE("gamerule", 2, 2, false);

    private final String commandPrefix;
    private final int minArgs;
    private final int maxArgs;
    private final boolean enumArgs;

    CommandType(String commandPrefix, int minArgs, int maxArgs, boolean enumArgs) {
        this.commandPrefix = commandPrefix;
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.enumArgs = enumArgs;
    }

    /** 生成的指令前缀，不含前导斜杠。 */
    public String commandPrefix() {
        return commandPrefix;
    }

    /** 最少参数个数。 */
    public int minArgs() {
        return minArgs;
    }

    /** 最多参数个数。 */
    public int maxArgs() {
        return maxArgs;
    }

    /**
     * 参数是否为枚举值。
     *
     * <p>为 {@code true} 时，剩余串会去掉全部内部空格后整体查表，
     * 这样 {@code #时间 白 天} 也能命中。
     */
    public boolean enumArgs() {
        return enumArgs;
    }

    /** 给用户看的用法示例，解析失败时作为提示返回。 */
    public String usage() {
        return switch (this) {
            case GIVE -> "#给我 <物品> [数量]";
            case TIME -> "#时间 <白天|正午|夜晚|午夜>";
            case TP -> "#传送 <x> <y> <z>";
            case GAMEMODE -> "#模式 <创造|生存|冒险|旁观>";
            case WEATHER -> "#天气 <晴|雨|雷>";
            case DIFFICULTY -> "#难度 <和平|简单|普通|困难>";
            case GAMERULE -> "#规则 <规则> <开|关>";
        };
    }
}
