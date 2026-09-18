package io.github.xiaoh.chatcmd.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 中文别名表：动词别名 → 指令类型、枚举值别名 → 原版取值、物品别名 → 原版物品 ID。
 *
 * <p>v0.1 先手写高频表；后续可无缝换成从注册表自动生成的表，调用方无需改动。
 *
 * <p><b>键必须是归一化之后的形式</b>：英文小写、半角。
 * 因为 {@link CommandParser} 会先把输入做全角转半角 + 英文转小写，再查这张表。
 */
public final class AliasTable {

    /** 动词匹配结果：命中的别名原文 + 它对应的指令类型。 */
    public record VerbMatch(String alias, CommandType type) {
    }

    private final Map<String, CommandType> verbAliases;
    private final Map<CommandType, Map<String, String>> valueAliases;
    private final Map<String, String> itemAliases;

    private AliasTable(Map<String, CommandType> verbAliases,
                       Map<CommandType, Map<String, String>> valueAliases,
                       Map<String, String> itemAliases) {
        this.verbAliases = Collections.unmodifiableMap(verbAliases);
        this.valueAliases = Collections.unmodifiableMap(valueAliases);
        this.itemAliases = Collections.unmodifiableMap(itemAliases);
    }

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
                best = new VerbMatch(alias, entry.getValue());
            }
        }
        return Optional.ofNullable(best);
    }

    /** 查某个指令类型的枚举值别名。 */
    public Optional<String> value(CommandType type, String key) {
        return Optional.ofNullable(valueAliases.getOrDefault(type, Map.of()).get(key));
    }

    /** 查物品别名。 */
    public Optional<String> item(String key) {
        return Optional.ofNullable(itemAliases.get(key));
    }

    /** 所有动词别名，按长度从长到短排列，用于拼「可用指令」提示。 */
    public String verbHint() {
        return String.join("、", verbAliases.keySet());
    }

    /** v0.1 的手写高频表。 */
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

        Map<CommandType, Map<String, String>> values = new EnumMap<>(CommandType.class);
        values.put(CommandType.TIME, mapOf(
                "白天", "day",
                "正午", "noon",
                "夜晚", "night",
                "晚上", "night",
                "午夜", "midnight",
                "day", "day",
                "noon", "noon",
                "night", "night",
                "midnight", "midnight"));
        values.put(CommandType.GAMEMODE, mapOf(
                "创造", "creative",
                "生存", "survival",
                "冒险", "adventure",
                "旁观", "spectator",
                "creative", "creative",
                "survival", "survival",
                "adventure", "adventure",
                "spectator", "spectator"));
        values.put(CommandType.WEATHER, mapOf(
                "晴", "clear",
                "晴天", "clear",
                "放晴", "clear",
                "雨", "rain",
                "下雨", "rain",
                "雷", "thunder",
                "雷雨", "thunder",
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

        return new AliasTable(verbs, values, items);
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
