package io.github.xiaoh.chatcmd.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 中文别名表：动词别名 → 指令类型、枚举值别名 → 原版取值、物品别名 → 原版物品 ID、
 * 游戏规则别名 → 原版规则名、整句短语 → 完整指令。
 *
 * <p>查询分两层：
 * <ol>
 *   <li><b>精确查询</b>（{@link #findLongestVerbPrefix} / {@link #value} / {@link #item} / {@link #phrase}）</li>
 *   <li><b>模糊查询</b>（{@code findFuzzy*} / {@code fuzzy*}），只在精确失败后调用，
 *       内部走 {@link Fuzzy} 的编辑距离，并要求唯一最优解</li>
 * </ol>
 *
 * <p><b>键必须是归一化之后的形式</b>：英文小写、半角。
 * 因为 {@link CommandParser} 会先把输入做全角转半角 + 英文转小写，再查这张表。
 */
public final class AliasTable {

    /** 动词匹配结果：命中的别名原文 + 对应指令类型 + 实际吃掉的字符数。 */
    public record VerbMatch(String alias, CommandType type, int matchedLength) {
    }

    /** 模糊匹配结果：命中的别名（用来回显「已按模糊匹配识别为 X」）+ 它映射到的值。 */
    public record Match(String key, String value) {
    }

    /** 前缀匹配的中间结果：最小编辑距离 + 取得该距离的前缀长度。 */
    private record Prefix(int distance, int length) {
    }

    private final Map<String, CommandType> verbAliases;
    private final Map<CommandType, Map<String, String>> valueAliases;
    private final Map<String, String> itemAliases;
    private final Map<String, String> gameruleAliases;
    private final Map<String, String> phraseAliases;

    private AliasTable(Map<String, CommandType> verbAliases,
                       Map<CommandType, Map<String, String>> valueAliases,
                       Map<String, String> itemAliases,
                       Map<String, String> gameruleAliases,
                       Map<String, String> phraseAliases) {
        this.verbAliases = Collections.unmodifiableMap(verbAliases);
        this.valueAliases = Collections.unmodifiableMap(valueAliases);
        this.itemAliases = Collections.unmodifiableMap(itemAliases);
        this.gameruleAliases = Collections.unmodifiableMap(gameruleAliases);
        this.phraseAliases = Collections.unmodifiableMap(phraseAliases);
    }

    // ---------- 动词 ----------

    /**
     * 最长前缀优先地匹配动词。
     *
     * <p>例如输入 {@code 给我 钻石剑} 时，{@code 给} 和 {@code 给我} 同时命中，取更长的 {@code 给我}。
     *
     * @param input 已归一化的输入（{@code #} 之后的部分）
     * @return 命中的最长别名；一个都没命中时返回 {@link Optional#empty()}
     */
    public Optional<VerbMatch> findLongestVerbPrefix(String input) {
        VerbMatch best = null;
        for (Map.Entry<String, CommandType> entry : verbAliases.entrySet()) {
            String alias = entry.getKey();
            if (input.startsWith(alias) && (best == null || alias.length() > best.alias().length())) {
                best = new VerbMatch(alias, entry.getValue(), alias.length());
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * 模糊动词前缀匹配，仅在精确匹配失败后调用。
     *
     * <p>逐个别名取「长度相近的前缀」算编辑距离，取唯一最优解；并列则放弃。
     */
    public Optional<VerbMatch> findFuzzyVerbPrefix(String input) {
        VerbMatch best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (Map.Entry<String, CommandType> entry : verbAliases.entrySet()) {
            String alias = entry.getKey();
            Prefix prefix = prefixDistance(input, alias);
            if (prefix == null || prefix.distance() > Fuzzy.autoThreshold(alias)) {
                continue;
            }
            if (prefix.distance() < bestDistance) {
                bestDistance = prefix.distance();
                best = new VerbMatch(alias, entry.getValue(), prefix.length());
                tie = false;
            } else if (prefix.distance() == bestDistance) {
                tie = true;
            }
        }
        return best == null || tie ? Optional.empty() : Optional.of(best);
    }

    /** 最接近的动词，只用于拼「你是不是想用 #X？」提示，比自动纠错宽松一档。 */
    public Optional<String> nearestVerb(String input) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (String alias : verbAliases.keySet()) {
            Prefix prefix = prefixDistance(input, alias);
            if (prefix == null || prefix.distance() > Fuzzy.autoThreshold(alias) + 1) {
                continue;
            }
            if (prefix.distance() < bestDistance) {
                bestDistance = prefix.distance();
                best = alias;
                tie = false;
            } else if (prefix.distance() == bestDistance) {
                tie = true;
            }
        }
        return best == null || tie ? Optional.empty() : Optional.of(best);
    }

    /**
     * 把输入前缀和别名做编辑距离比较。
     *
     * <p>前缀长度在 {@code [别名长度-1, 别名长度+1]} 之间都试一遍，覆盖「多打/少打一个字」的情况。
     * 逐前缀做 {@link Fuzzy#compatible} 检查，避免英文动词被中文参数带偏。
     */
    private static Prefix prefixDistance(String input, String alias) {
        int aliasLength = alias.length();
        Prefix best = null;
        int from = Math.max(1, aliasLength - 1);
        int to = Math.min(input.length(), aliasLength + 1);
        for (int length = from; length <= to; length++) {
            String candidate = input.substring(0, length);
            if (!Fuzzy.compatible(candidate, alias)) {
                continue;
            }
            int d = Fuzzy.distance(candidate, alias);
            if (best == null || d < best.distance()) {
                best = new Prefix(d, length);
            }
        }
        return best;
    }

    // ---------- 枚举取值 ----------

    /** 查某个指令类型的枚举值别名（精确）。 */
    public Optional<String> value(CommandType type, String key) {
        return Optional.ofNullable(valueAliases.getOrDefault(type, Map.of()).get(key));
    }

    /** 枚举值模糊匹配，仅在精确失败后调用。 */
    public Optional<Match> fuzzyValue(CommandType type, String key) {
        return fuzzyPick(key, valueAliases.getOrDefault(type, Map.of()));
    }

    /** 最接近的枚举取值，只用于提示。 */
    public Optional<String> nearestValue(CommandType type, String key) {
        return nearestPick(key, valueAliases.getOrDefault(type, Map.of()));
    }

    // ---------- 物品 ----------

    /** 查物品别名（精确）。 */
    public Optional<String> item(String key) {
        return Optional.ofNullable(itemAliases.get(key));
    }

    /** 物品模糊匹配，仅在精确失败后调用。 */
    public Optional<Match> fuzzyItem(String key) {
        return fuzzyPick(key, itemAliases);
    }

    /** 最接近的物品，只用于提示。 */
    public Optional<String> nearestItem(String key) {
        return nearestPick(key, itemAliases);
    }

    // ---------- 游戏规则 ----------

    /** 查游戏规则名（精确），如 {@code 死亡不掉落 → keepInventory}。 */
    public Optional<String> gamerule(String key) {
        return Optional.ofNullable(gameruleAliases.get(key));
    }

    // ---------- 整句短语 ----------

    /**
     * 整句短语精确匹配：口语整句 → 完整指令。
     *
     * <p>用来接住「语义上不归某个动词管」的说法，例如 {@code 死亡不掉落} 其实属于 gamerule，
     * 而不是 gamemode。
     */
    public Optional<String> phrase(String key) {
        return Optional.ofNullable(phraseAliases.get(key));
    }

    /** 整句短语模糊匹配，仅在精确失败后调用。 */
    public Optional<Match> fuzzyPhrase(String key) {
        return fuzzyPick(key, phraseAliases);
    }

    /** 最接近的短语，只用于提示。 */
    public Optional<String> nearestPhrase(String key) {
        return nearestPick(key, phraseAliases);
    }

    // ---------- 通用模糊工具 ----------

    private static Optional<Match> fuzzyPick(String key, Map<String, String> table) {
        return Fuzzy.uniqueBest(key, table.keySet(), Fuzzy.autoThreshold(key))
                .map(hit -> new Match(hit, table.get(hit)));
    }

    private static Optional<String> nearestPick(String key, Map<String, String> table) {
        return Fuzzy.nearest(key, table.keySet(), Fuzzy.autoThreshold(key) + 1);
    }

    /** 所有动词别名，用于拼「可用指令」提示。 */
    public String verbHint() {
        return String.join("、", verbAliases.keySet());
    }

    // ---------- 手写高频表 ----------

    /** v0.1 的手写高频表（v0.2 增补同义词、游戏规则与整句短语）。 */
    public static AliasTable defaultTable() {
        Map<String, CommandType> verbs = new LinkedHashMap<>();
        verbs.put("给我", CommandType.GIVE);
        verbs.put("给", CommandType.GIVE);
        verbs.put("give", CommandType.GIVE);
        verbs.put("时间", CommandType.TIME);
        verbs.put("时刻", CommandType.TIME);
        verbs.put("time", CommandType.TIME);
        verbs.put("传送", CommandType.TP);
        verbs.put("tp", CommandType.TP);
        verbs.put("teleport", CommandType.TP);
        verbs.put("模式", CommandType.GAMEMODE);
        verbs.put("gamemode", CommandType.GAMEMODE);
        verbs.put("天气", CommandType.WEATHER);
        verbs.put("weather", CommandType.WEATHER);
        verbs.put("难度", CommandType.DIFFICULTY);
        verbs.put("difficulty", CommandType.DIFFICULTY);
        verbs.put("游戏规则", CommandType.GAMERULE);
        verbs.put("规则", CommandType.GAMERULE);
        verbs.put("gamerule", CommandType.GAMERULE);

        Map<CommandType, Map<String, String>> values = new EnumMap<>(CommandType.class);
        values.put(CommandType.TIME, mapOf(
                "白天", "day",
                "早上", "day",
                "早晨", "day",
                "正午", "noon",
                "中午", "noon",
                "夜晚", "night",
                "晚上", "night",
                "傍晚", "night",
                "黄昏", "night",
                "午夜", "midnight",
                "凌晨", "midnight",
                "day", "day",
                "noon", "noon",
                "night", "night",
                "midnight", "midnight"));
        values.put(CommandType.GAMEMODE, mapOf(
                "创造", "creative",
                "创意", "creative",
                "生存", "survival",
                "冒险", "adventure",
                "冒险家", "adventure",
                "旁观", "spectator",
                "旁观者", "spectator",
                "creative", "creative",
                "survival", "survival",
                "adventure", "adventure",
                "spectator", "spectator"));
        values.put(CommandType.WEATHER, mapOf(
                "晴", "clear",
                "晴天", "clear",
                "晴朗", "clear",
                "放晴", "clear",
                "雨", "rain",
                "下雨", "rain",
                "雷", "thunder",
                "雷雨", "thunder",
                "打雷", "thunder",
                "clear", "clear",
                "rain", "rain",
                "thunder", "thunder"));
        values.put(CommandType.DIFFICULTY, mapOf(
                "和平", "peaceful",
                "简单", "easy",
                "普通", "normal",
                "困难", "hard",
                "peaceful", "peaceful",
                "easy", "easy",
                "normal", "normal",
                "hard", "hard"));
        // 游戏规则的开关取值：中文口语 + 英文
        values.put(CommandType.GAMERULE, mapOf(
                "开", "true",
                "开启", "true",
                "打开", "true",
                "启用", "true",
                "是", "true",
                "true", "true",
                "on", "true",
                "关", "false",
                "关闭", "false",
                "关掉", "false",
                "禁用", "false",
                "否", "false",
                "false", "false",
                "off", "false"));

        Map<String, String> items = mapOf(
                "钻石剑", "minecraft:diamond_sword",
                "钻石镐", "minecraft:diamond_pickaxe",
                "钻石斧", "minecraft:diamond_axe",
                "钻石锹", "minecraft:diamond_shovel",
                "钻石锄", "minecraft:diamond_hoe",
                "钻石", "minecraft:diamond",
                "钻石块", "minecraft:diamond_block",
                "铁剑", "minecraft:iron_sword",
                "铁镐", "minecraft:iron_pickaxe",
                "铁锭", "minecraft:iron_ingot",
                "金剑", "minecraft:golden_sword",
                "金锭", "minecraft:gold_ingot",
                "石剑", "minecraft:stone_sword",
                "木剑", "minecraft:wooden_sword",
                "下界合金剑", "minecraft:netherite_sword",
                "下界合金锭", "minecraft:netherite_ingot",
                "弓", "minecraft:bow",
                "箭", "minecraft:arrow",
                "盾牌", "minecraft:shield",
                "石头", "minecraft:stone",
                "圆石", "minecraft:cobblestone",
                "泥土", "minecraft:dirt",
                "草方块", "minecraft:grass_block",
                "橡木原木", "minecraft:oak_log",
                "木板", "minecraft:oak_planks",
                "玻璃", "minecraft:glass",
                "沙子", "minecraft:sand",
                "黑曜石", "minecraft:obsidian",
                "基岩", "minecraft:bedrock",
                "火把", "minecraft:torch",
                "箱子", "minecraft:chest",
                "工作台", "minecraft:crafting_table",
                "熔炉", "minecraft:furnace",
                "漏斗", "minecraft:hopper",
                "发射器", "minecraft:dispenser",
                "命令方块", "minecraft:command_block",
                "红石", "minecraft:redstone",
                "煤炭", "minecraft:coal",
                "木棍", "minecraft:stick",
                "苹果", "minecraft:apple",
                "金苹果", "minecraft:golden_apple",
                "附魔金苹果", "minecraft:enchanted_golden_apple",
                "面包", "minecraft:bread",
                "小麦", "minecraft:wheat",
                "胡萝卜", "minecraft:carrot",
                "马铃薯", "minecraft:potato",
                "甘蔗", "minecraft:sugar_cane",
                "生牛肉", "minecraft:beef",
                "牛排", "minecraft:cooked_beef",
                "腐肉", "minecraft:rotten_flesh",
                "末影珍珠", "minecraft:ender_pearl",
                "药水", "minecraft:potion",
                "水桶", "minecraft:water_bucket",
                "熔岩桶", "minecraft:lava_bucket",
                "tnt", "minecraft:tnt");

        // 游戏规则名。英文键必须写成小写，因为输入在 normalize 阶段已全部转小写；
        // 值必须是原版驼峰写法，原版指令参数大小写敏感。
        Map<String, String> gamerules = mapOf(
                "死亡不掉落", "keepInventory",
                "保留物品", "keepInventory",
                "keepinventory", "keepInventory",
                "生物破坏", "mobGriefing",
                "mobgriefing", "mobGriefing",
                "昼夜交替", "doDaylightCycle",
                "dodaylightcycle", "doDaylightCycle",
                "天气变化", "doWeatherCycle",
                "doweathercycle", "doWeatherCycle",
                "自然回血", "naturalRegeneration",
                "naturalregeneration", "naturalRegeneration",
                "死亡消息", "showDeathMessages",
                "showdeathmessages", "showDeathMessages",
                "怪物生成", "doMobSpawning",
                "domobspawning", "doMobSpawning",
                "火势蔓延", "doFireTick",
                "dofiretick", "doFireTick",
                "立即重生", "doImmediateRespawn",
                "doimmediaterespawn", "doImmediateRespawn");

        // 整句短语：接住「归别的指令管」的说法。
        // 例：死亡不掉落属于 gamerule keepInventory，不归 gamemode 管。
        Map<String, String> phrases = mapOf(
                "死亡不掉落", "gamerule keepInventory true",
                "开启死亡不掉落", "gamerule keepInventory true",
                "打开死亡不掉落", "gamerule keepInventory true",
                "关闭死亡不掉落", "gamerule keepInventory false",
                "关掉死亡不掉落", "gamerule keepInventory false");

        return new AliasTable(verbs, values, items, gamerules, phrases);
    }

    /** 小工具：用可变参数拼 Map，避免手写一串 put。 */
    private static Map<String, String> mapOf(String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("键值必须成对出现");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
