package io.github.xiaoh.chatcmd.core;

/**
 * 支持的指令模板类型。
 *
 * <p>
 * 每个类型描述「生成什么指令」和「需要几个参数」，具体组装逻辑在 {@link CommandParser} 里。
 *
 * <p>
 * {@code commandPrefix} 为空串的类型（{@link #RESET}、{@link #CLEAR}）表示
 * 「别名表里的取值本身就是完整指令」，组装时不再拼前缀，取值里出现 {@code \n}
 * 就表示这一条要按顺序发多条指令。
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
    GAMERULE("gamerule", 2, 2, false),
    /** 状态重置：取值本身就是完整指令（可多条），无参数时等同「全部」 */
    RESET("", 0, 1, true),
    /** 施加药水效果：{@code /effect give @s <effect> [时长] [等级]} */
    EFFECT("effect give @s", 1, 3, false),
    /** 附魔一件新物品：{@code /give @s <item>[enchantments={...}]} */
    ENCHANT("give @s", 2, 3, false),
    /** 给手上拿的物品附魔：{@code /enchant @s <enchant> <level>} */
    ENCHANT_HELD("enchant @s", 1, 2, false),
    /** 增加经验：{@code /xp add @s <amount>} */
    XP("xp add @s", 1, 1, false),
    /** 批量清除实体：{@code /kill @e[...]}，取值本身就是完整指令；禁止模糊纠错 */
    CLEAR("", 1, 1, true, true),
    /** 查找结构：{@code /locate structure <id>} */
    LOCATE("locate structure", 1, 1, true),
    /** 生成实体：{@code /summon <entity> [x y z]}；数量大于 1 时拆成多条依次发 */
    SUMMON("summon", 1, 4, false);

    private final String commandPrefix;
    private final int minArgs;
    private final int maxArgs;
    private final boolean enumArgs;
    private final boolean strictVerbs;

    CommandType(String commandPrefix, int minArgs, int maxArgs, boolean enumArgs) {
        this(commandPrefix, minArgs, maxArgs, enumArgs, false);
    }

    CommandType(String commandPrefix, int minArgs, int maxArgs, boolean enumArgs, boolean strictVerbs) {
        this.commandPrefix = commandPrefix;
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.enumArgs = enumArgs;
        this.strictVerbs = strictVerbs;
    }

    /** 生成的指令前缀，不含前导斜杠；空串表示取值即完整指令。 */
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
     * <p>
     * 为 {@code true} 时，剩余串会去掉全部内部空格后整体查表，
     * 这样 {@code #时间 白 天} 也能命中。
     */
    public boolean enumArgs() {
        return enumArgs;
    }

    /**
     * 是否禁止模糊纠错。
     *
     * <p>
     * 只对会造成不可逆破坏的动词开启（目前只有 {@link #CLEAR}）：
     * 打错一个字就批量杀实体，代价太大，宁可让用户自己重打。
     */
    public boolean strictVerbs() {
        return strictVerbs;
    }

    /** 中文名，用于拼提示语（如「不归 清除 管」）。 */
    public String displayName() {
        return switch (this) {
            case GIVE -> "给我";
            case TIME -> "时间";
            case TP -> "传送";
            case GAMEMODE -> "模式";
            case WEATHER -> "天气";
            case DIFFICULTY -> "难度";
            case GAMERULE -> "规则";
            case RESET -> "重置";
            case EFFECT -> "效果";
            case ENCHANT, ENCHANT_HELD -> "附魔";
            case XP -> "经验";
            case CLEAR -> "清除";
            case LOCATE -> "查找";
            case SUMMON -> "生成";
        };
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
            case RESET -> "#重置 [时间|天气|效果|规则|全部]";
            case EFFECT -> "#效果 <效果名> [等级] [时长]（如 #效果 速度 2 30）";
            case ENCHANT -> "#附魔 <物品> <附魔><等级>（多个用逗号隔开）";
            case ENCHANT_HELD -> "#附魔手持 <附魔><等级>（多个用逗号隔开，如 #附魔手持 锋利5,耐久3）";
            case XP -> "#经验 <数量>";
            case CLEAR -> "#清除 <掉落物|经验球|怪物>";
            case LOCATE -> "#找 <结构名>（如 #找 村庄、#最近的村庄）";
            case SUMMON -> "#生成 <实体> [数量] [x y z]（如 #生成 僵尸、#生成 坚守者 3）";
        };
    }
}
