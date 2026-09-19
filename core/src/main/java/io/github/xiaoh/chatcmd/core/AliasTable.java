package io.github.xiaoh.chatcmd.core;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 中文别名表：动词别名 → 指令类型、枚举值别名 → 原版取值、物品别名 → 原版物品 ID、
 * 游戏规则别名 → 原版规则名、实体别名 → 原版实体 ID、附魔别名 → 原版附魔 ID、
 * 整句短语 → 完整指令。
 *
 * <p>查询分两层：
 * <ol>
 *   <li><b>精确查询</b>（{@link #findLongestVerbPrefix} / {@code value} / {@code item} / {@code phrase}）</li>
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

    /** 附魔前缀匹配结果：命中的别名 + 附魔 ID + 吃掉的字符数（剩下的就是等级数字）。 */
    public record EnchantPrefix(String name, String id, int matchedLength) {
    }

    /** 前缀匹配的中间结果：最小编辑距离 + 取得该距离的前缀长度。 */
    private record Prefix(int distance, int length) {
    }

    /** 多指令分隔符，见 {@link ParseResult#COMMAND_SEPARATOR}。 */
    private static final String SEP = ParseResult.COMMAND_SEPARATOR;

    /** 重置「时间」：把昼夜交替打开，用来结束「永为白昼 / 永为黑夜」。 */
    private static final String RESET_TIME = "gamerule doDaylightCycle true";
    /** 重置「天气」：先开回天气循环，再放晴。 */
    private static final String RESET_WEATHER = "gamerule doWeatherCycle true" + SEP + "weather clear";
    /** 重置「效果」：清空身上全部药水效果。 */
    private static final String RESET_EFFECTS = "effect clear @s";
    /** 重置「规则」：把 ChatCmd 能改的游戏规则还原成原版默认值。 */
    private static final String RESET_RULES = "gamerule keepInventory false" + SEP + "gamerule mobGriefing true";
    /** 重置「全部」：以上四类全做。 */
    private static final String RESET_ALL =
            RESET_TIME + SEP + RESET_WEATHER + SEP + RESET_EFFECTS + SEP + RESET_RULES;

    /**
     * 「清除怪物」用的实体选择器。
     *
     * <p>排除玩家、掉落物和经验球，剩下的<b>全部</b>清掉 —— 原版没有「只杀敌对生物」的选择器，
     * 所以动物和村民也会被一起清掉。这一点在 {@link CommandType#CLEAR} 的提示里已明确告知。
     */
    private static final String CLEAR_MOBS =
            "kill @e[type=!player,type=!minecraft:item,type=!minecraft:experience_orb]";

    /** {@link #valueSuggestions} 最多列几项 —— 效果有 40 个，全列出来聊天框会刷屏。 */
    private static final int MAX_SUGGESTIONS = 8;

    private final Map<String, CommandType> verbAliases;
    private final Map<CommandType, Map<String, String>> valueAliases;
    private final Map<String, String> itemAliases;
    private final Map<String, String> gameruleAliases;
    private final Map<String, String> entityAliases;
    private final Map<String, String> enchantAliases;
    private final Map<String, String> phraseAliases;

    private AliasTable(Map<String, CommandType> verbAliases,
                       Map<CommandType, Map<String, String>> valueAliases,
                       Map<String, String> itemAliases,
                       Map<String, String> gameruleAliases,
                       Map<String, String> entityAliases,
                       Map<String, String> enchantAliases,
                       Map<String, String> phraseAliases) {
        this.verbAliases = Collections.unmodifiableMap(verbAliases);
        this.valueAliases = Collections.unmodifiableMap(valueAliases);
        this.itemAliases = Collections.unmodifiableMap(itemAliases);
        this.gameruleAliases = Collections.unmodifiableMap(gameruleAliases);
        this.entityAliases = Collections.unmodifiableMap(entityAliases);
        this.enchantAliases = Collections.unmodifiableMap(enchantAliases);
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
     *
     * <p>{@link CommandType#strictVerbs()} 为真的动词直接跳过：这类指令一执行就不可逆，
     * 打错一个字就批量杀实体，代价太大。
     */
    public Optional<VerbMatch> findFuzzyVerbPrefix(String input) {
        VerbMatch best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (Map.Entry<String, CommandType> entry : verbAliases.entrySet()) {
            if (entry.getValue().strictVerbs()) {
                continue;
            }
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

    // ---------- 附魔 ----------

    /**
     * 附魔名<b>最长前缀</b>匹配。
     *
     * <p>这是「名词组合」的拆解点：{@code 击退255} 会被拆成附魔 {@code minecraft:knockback}
     * 加剩余串 {@code 255}，剩余串由调用方当等级解析。
     *
     * <p>取最长前缀是为了让「火焰保护」不会被「火焰附加」抢先命中。
     */
    public Optional<EnchantPrefix> enchantPrefix(String input) {
        EnchantPrefix best = null;
        for (Map.Entry<String, String> entry : enchantAliases.entrySet()) {
            String alias = entry.getKey();
            if (input.startsWith(alias) && (best == null || alias.length() > best.name().length())) {
                best = new EnchantPrefix(alias, entry.getValue(), alias.length());
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * 附魔名的模糊兜底：<b>精确前缀认不出之后</b>才调用。
     *
     * <p>先把尾巴上的等级数字剥掉，再拿名字部分按编辑距离找<b>唯一最近</b>的附魔名
     * （并列就放弃，见 {@link Fuzzy#uniqueBest}）。命中的名字后面接回原来的数字当等级 ——
     * 玩家写对的那部分不该丢。
     *
     * <p>返回的 {@code matchedLength} 是「名字部分」的长度，所以调用方照旧用
     * {@code input.substring(matchedLength())} 就能取到等级，和 {@link #enchantPrefix} 同构。
     *
     * <p>为什么排在精确前缀后面：「击退r255」这种能被「击退」前缀命中、只是等级写坏的，
     * 应该走原路报等级错，而不是被模糊纠成别的附魔。
     */
    public Optional<EnchantPrefix> enchantFuzzy(String input) {
        String level = trailingDigits(input);
        String name = input.substring(0, input.length() - level.length());
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Fuzzy.uniqueBest(name, enchantAliases.keySet(), Fuzzy.autoThreshold(name))
                .map(alias -> new EnchantPrefix(alias, enchantAliases.get(alias), name.length()));
    }

    /** 尾部连续的阿拉伯数字（等级），没有就返回空串。 */
    private static String trailingDigits(String s) {
        int i = s.length();
        while (i > 0 && Character.isDigit(s.charAt(i - 1))) {
            i--;
        }
        return s.substring(i);
    }

    /** 最接近的附魔名，只用于提示。 */
    public Optional<String> nearestEnchant(String input) {
        return Fuzzy.nearest(input, enchantAliases.keySet(), Fuzzy.autoThreshold(input) + 1);
    }

    /** 距离最近的若干附魔名候选。 */
    public List<String> nearestEnchants(String input, int limit) {
        return Fuzzy.nearestList(input, enchantAliases.keySet(),
                Fuzzy.autoThreshold(input) + 2, limit);
    }

    /** 全部附魔名，每个附魔只留最顺口的一个别名，用于一个候选都列不出来时的兜底。 */
    public List<String> enchantSuggestions() {
        return distinctValues(enchantAliases);
    }

    // ---------- 游戏规则 ----------

    /** 查游戏规则名（精确），如 {@code 死亡不掉落 → keepInventory}。 */
    public Optional<String> gamerule(String key) {
        return Optional.ofNullable(gameruleAliases.get(key));
    }

    /**
     * 规则名模糊匹配，仅在精确失败后调用。
     *
     * <p>规则的中文说法特别杂（同一条 {@code naturalRegeneration} 有人叫「自然回血」、
     * 有人叫「生命恢复」、有人叫「生命自然恢复」），表里已经收了一批同义词；
     * 剩下的错别字靠这里兜底。
     */
    public Optional<Match> fuzzyGamerule(String key) {
        return fuzzyPick(key, gameruleAliases);
    }

    /** 距离最近的若干规则名候选。 */
    public List<String> nearestGamerules(String key, int limit) {
        return Fuzzy.nearestList(key, gameruleAliases.keySet(), Fuzzy.autoThreshold(key) + 2, limit);
    }

    /** 全部规则名，每条规则只留最顺口的一个别名，用于候选都列不出来时的兜底。 */
    public List<String> gameruleSuggestions() {
        return distinctValues(gameruleAliases);
    }

    // ---------- 实体（生成指令用） ----------

    /** 查实体别名（精确），如 {@code 僵尸 → minecraft:zombie}。 */
    public Optional<String> entity(String key) {
        return Optional.ofNullable(entityAliases.get(key));
    }

    /**
     * 实体名模糊匹配，仅在精确失败后调用。
     *
     * <p>靠 {@link Fuzzy#compatible} 的「首字必须相同」把关：「坚守者」和「守卫者」
     * 只差一个字，但首字不同，永远不会互相纠错。
     */
    public Optional<Match> fuzzyEntity(String key) {
        return fuzzyPick(key, entityAliases);
    }

    /** 距离最近的若干实体候选。 */
    public List<String> nearestEntities(String key, int limit) {
        return Fuzzy.nearestList(key, entityAliases.keySet(), Fuzzy.autoThreshold(key) + 2, limit);
    }

    /** 全部实体名，每个实体只留最顺口的一个别名。 */
    public List<String> entitySuggestions() {
        return distinctValues(entityAliases);
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

    // ---------- 候选列表（「多项近义给选择」） ----------

    /**
     * 距离最近的若干动词候选，用于把 {@code #模组 创造} 提示成 {@code #模式 创造}。
     *
     * <p>比自动纠错的阈值宽松两档：候选只是填回聊天框，用户还要按回车才生效，
     * 所以宁可多列几个也不要让用户重打。
     */
    public List<String> nearestVerbs(String input, int limit) {
        return Fuzzy.nearestList(input, verbAliases.keySet(), Fuzzy.autoThreshold(input) + 2, limit);
    }

    /** 距离最近的若干枚举取值候选。 */
    public List<String> nearestValues(CommandType type, String key, int limit) {
        return Fuzzy.nearestList(key, valueAliases.getOrDefault(type, Map.of()).keySet(),
                Fuzzy.autoThreshold(key) + 2, limit);
    }

    /**
     * 距离最近的若干物品候选。
     *
     * @param extra 适配层提供的额外候选（游戏注册表里的全量本地化物品名），可为空集合
     */
    public List<String> nearestItems(String key, Collection<String> extra, int limit) {
        Set<String> all = new LinkedHashSet<>(itemAliases.keySet());
        all.addAll(extra);
        return Fuzzy.nearestList(key, all, Fuzzy.autoThreshold(key) + 2, limit);
    }

    /**
     * 某个指令类型的全部候选取值，每个取值只保留最顺口的一个别名（优先中文、其次最短）。
     *
     * <p>用于枚举取值离得比较远、模糊候选一个都列不出来时（如 {@code #模式飞行}），
     * 干脆把「创造 / 生存 / 冒险 / 旁观」全摆出来让用户点。
     * 上限 {@link #MAX_SUGGESTIONS} 项，避免效果那 40 个取值把聊天框刷爆。
     */
    public List<String> valueSuggestions(CommandType type) {
        return distinctValues(valueAliases.getOrDefault(type, Map.of()));
    }

    /** 无参数重置时用的「全部」指令串。 */
    public String resetAll() {
        return RESET_ALL;
    }

    /** 把「别名 → 取值」的表压成「每个取值只留最顺口的一个别名」，并截断到上限。 */
    private static List<String> distinctValues(Map<String, String> table) {
        // 目标取值 -> 最顺口的别名；LinkedHashMap 保证输出顺序稳定
        Map<String, String> best = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : table.entrySet()) {
            String current = best.get(entry.getValue());
            if (current == null || preferAlias(entry.getKey(), current)) {
                best.put(entry.getValue(), entry.getKey());
            }
        }
        return best.values().stream().limit(MAX_SUGGESTIONS).toList();
    }

    /** 两个别名指向同一个取值时，选更顺口的那个：先中文、再短的。 */
    private static boolean preferAlias(String candidate, String current) {
        boolean candidateCjk = Fuzzy.hasCjk(candidate);
        if (candidateCjk != Fuzzy.hasCjk(current)) {
            return candidateCjk;
        }
        return candidate.length() < current.length();
    }

    /**
     * 每个指令类型只取一个最顺口的动词别名，用于拼「可用的动词」提示。
     *
     * <p>不列全部别名：v0.5 起别名有 30 多个，全列出来会把提示刷成三行。
     */
    public String verbHint() {
        Map<CommandType, String> best = new LinkedHashMap<>();
        for (Map.Entry<String, CommandType> entry : verbAliases.entrySet()) {
            String current = best.get(entry.getValue());
            if (current == null || preferAlias(entry.getKey(), current)) {
                best.put(entry.getValue(), entry.getKey());
            }
        }
        return String.join("、", best.values());
    }

    // ---------- 手写高频表 ----------

    /** v0.1 的手写高频表（v0.2 增补同义词、游戏规则与整句短语，v0.5 增补效果 / 附魔 / 重置 / 清除，
     * v0.8 增补规则同义词与实体表）。 */
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
        verbs.put("重置", CommandType.RESET);
        verbs.put("恢复", CommandType.RESET);
        verbs.put("复原", CommandType.RESET);
        verbs.put("效果", CommandType.EFFECT);
        verbs.put("状态效果", CommandType.EFFECT);
        verbs.put("buff", CommandType.EFFECT);
        // 「附魔手持」必须比「附魔」长，最长前缀匹配才会优先取它
        verbs.put("附魔手持", CommandType.ENCHANT_HELD);
        verbs.put("手持附魔", CommandType.ENCHANT_HELD);
        verbs.put("enchant", CommandType.ENCHANT_HELD);
        verbs.put("附魔", CommandType.ENCHANT);
        // 「最近的村庄」「找村庄」「查找 远古城市」都收
        verbs.put("最近的", CommandType.LOCATE);
        verbs.put("最近", CommandType.LOCATE);
        verbs.put("找结构", CommandType.LOCATE);
        verbs.put("寻找", CommandType.LOCATE);
        verbs.put("查找", CommandType.LOCATE);
        verbs.put("找", CommandType.LOCATE);
        verbs.put("locate", CommandType.LOCATE);
        verbs.put("经验", CommandType.XP);
        verbs.put("经验值", CommandType.XP);
        verbs.put("xp", CommandType.XP);
        verbs.put("清除", CommandType.CLEAR);
        verbs.put("清掉", CommandType.CLEAR);
        // 生成实体
        verbs.put("生成", CommandType.SUMMON);
        verbs.put("召唤", CommandType.SUMMON);
        verbs.put("刷怪", CommandType.SUMMON);
        verbs.put("summon", CommandType.SUMMON);
        verbs.put("spawn", CommandType.SUMMON);

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
        // 重置：取值本身就是完整指令（可多条），无参数时走 resetAll()
        values.put(CommandType.RESET, mapOf(
                "时间", RESET_TIME,
                "昼夜", RESET_TIME,
                "昼夜交替", RESET_TIME,
                "白天黑夜", RESET_TIME,
                "天气", RESET_WEATHER,
                "天气变化", RESET_WEATHER,
                "效果", RESET_EFFECTS,
                "药水效果", RESET_EFFECTS,
                "状态", RESET_EFFECTS,
                "规则", RESET_RULES,
                "游戏规则", RESET_RULES,
                "全部", RESET_ALL,
                "所有", RESET_ALL,
                "一切", RESET_ALL));
        // 批量清除：取值本身就是完整指令
        values.put(CommandType.CLEAR, mapOf(
                "掉落物", "kill @e[type=minecraft:item]",
                "物品", "kill @e[type=minecraft:item]",
                "地上的东西", "kill @e[type=minecraft:item]",
                "经验球", "kill @e[type=minecraft:experience_orb]",
                "经验", "kill @e[type=minecraft:experience_orb]",
                // 「清除 效果」和「重置 效果」等价，两种说法都收
                "效果", RESET_EFFECTS,
                "药水效果", RESET_EFFECTS,
                "怪物", CLEAR_MOBS,
                "生物", CLEAR_MOBS));
        // 结构查找：中文结构名 → 原版结构 ID。能用结构标签（tag）的优先用标签，
        // 这样「村庄」一次就能找到平原/沙漠/雪原等全部变体。
        values.put(CommandType.LOCATE, mapOf(
                "村庄", "#minecraft:village",
                "村子", "#minecraft:village",
                "平原村庄", "minecraft:village_plains",
                "沙漠村庄", "minecraft:village_desert",
                "雪原村庄", "minecraft:village_snowy",
                "热带草原村庄", "minecraft:village_savanna",
                "针叶林村庄", "minecraft:village_taiga",
                "掠夺者前哨站", "minecraft:pillager_outpost",
                "掠夺者哨塔", "minecraft:pillager_outpost",
                "哨塔", "minecraft:pillager_outpost",
                "林地府邸", "minecraft:mansion",
                "府邸", "minecraft:mansion",
                "要塞", "minecraft:stronghold",
                "废弃矿井", "minecraft:mineshaft",
                "矿洞", "#minecraft:mineshaft",
                "海底神殿", "minecraft:monument",
                "海底宫殿", "minecraft:monument",
                "沉船", "#minecraft:shipwreck",
                "海底废墟", "#minecraft:ocean_ruin",
                "古迹废墟", "minecraft:trail_ruins",
                "沙漠神殿", "minecraft:desert_pyramid",
                "丛林神庙", "minecraft:jungle_pyramid",
                "神庙", "minecraft:jungle_pyramid",
                "沼泽小屋", "minecraft:swamp_hut",
                "女巫小屋", "minecraft:swamp_hut",
                "雪屋", "minecraft:igloo",
                "冰屋", "minecraft:igloo",
                "远古城市", "minecraft:ancient_city",
                "试炼密室", "minecraft:trial_chambers",
                "试炼厅", "minecraft:trial_chambers",
                "废弃传送门", "#minecraft:ruined_portal",
                "下界要塞", "minecraft:fortress",
                "地狱要塞", "minecraft:fortress",
                "堡垒遗迹", "minecraft:bastion_remnant",
                "堡垒", "minecraft:bastion_remnant",
                "末地城", "minecraft:end_city",
                "紫水晶洞", "minecraft:amethyst_geode",
                "沙漠水井", "minecraft:desert_well",
                "埋藏的宝藏", "minecraft:buried_treasure",
                "下界化石", "minecraft:nether_fossil"));
        // 药水效果：中文名 → 原版效果 ID
        values.put(CommandType.EFFECT, mapOf(
                "速度", "minecraft:speed",
                "迅捷", "minecraft:speed",
                "speed", "minecraft:speed",
                "缓慢", "minecraft:slowness",
                "迟缓", "minecraft:slowness",
                "slowness", "minecraft:slowness",
                "急迫", "minecraft:haste",
                "急速", "minecraft:haste",
                "haste", "minecraft:haste",
                "挖掘疲劳", "minecraft:mining_fatigue",
                "mining_fatigue", "minecraft:mining_fatigue",
                "力量", "minecraft:strength",
                "strength", "minecraft:strength",
                "瞬间治疗", "minecraft:instant_health",
                "instant_health", "minecraft:instant_health",
                "瞬间伤害", "minecraft:instant_damage",
                "instant_damage", "minecraft:instant_damage",
                "跳跃提升", "minecraft:jump_boost",
                "jump_boost", "minecraft:jump_boost",
                "反胃", "minecraft:nausea",
                "nausea", "minecraft:nausea",
                "生命恢复", "minecraft:regeneration",
                "regeneration", "minecraft:regeneration",
                "抗性提升", "minecraft:resistance",
                "resistance", "minecraft:resistance",
                "防火", "minecraft:fire_resistance",
                "fire_resistance", "minecraft:fire_resistance",
                "水下呼吸", "minecraft:water_breathing",
                "water_breathing", "minecraft:water_breathing",
                "隐身", "minecraft:invisibility",
                "invisibility", "minecraft:invisibility",
                "失明", "minecraft:blindness",
                "blindness", "minecraft:blindness",
                "夜视", "minecraft:night_vision",
                "night_vision", "minecraft:night_vision",
                "饥饿", "minecraft:hunger",
                "hunger", "minecraft:hunger",
                "虚弱", "minecraft:weakness",
                "weakness", "minecraft:weakness",
                "中毒", "minecraft:poison",
                "poison", "minecraft:poison",
                "凋零", "minecraft:wither",
                "wither", "minecraft:wither",
                "生命提升", "minecraft:health_boost",
                "health_boost", "minecraft:health_boost",
                "伤害吸收", "minecraft:absorption",
                "absorption", "minecraft:absorption",
                "饱和", "minecraft:saturation",
                "saturation", "minecraft:saturation",
                "发光", "minecraft:glowing",
                "glowing", "minecraft:glowing",
                "漂浮", "minecraft:levitation",
                "levitation", "minecraft:levitation",
                "幸运", "minecraft:luck",
                "luck", "minecraft:luck",
                "霉运", "minecraft:unluck",
                "unluck", "minecraft:unluck",
                "缓降", "minecraft:slow_falling",
                "slow_falling", "minecraft:slow_falling",
                "潮涌能量", "minecraft:conduit_power",
                "conduit_power", "minecraft:conduit_power",
                "海豚的恩惠", "minecraft:dolphins_grace",
                "dolphins_grace", "minecraft:dolphins_grace",
                "不祥之兆", "minecraft:bad_omen",
                "bad_omen", "minecraft:bad_omen",
                "村庄英雄", "minecraft:hero_of_the_village",
                "hero_of_the_village", "minecraft:hero_of_the_village",
                "黑暗", "minecraft:darkness",
                "darkness", "minecraft:darkness",
                "试炼之兆", "minecraft:trial_omen",
                "trial_omen", "minecraft:trial_omen",
                "袭击之兆", "minecraft:raid_omen",
                "raid_omen", "minecraft:raid_omen",
                "风袭", "minecraft:wind_charged",
                "wind_charged", "minecraft:wind_charged",
                "织网", "minecraft:weaving",
                "weaving", "minecraft:weaving",
                "渗浆", "minecraft:oozing",
                "oozing", "minecraft:oozing",
                "寄生", "minecraft:infested",
                "infested", "minecraft:infested"));

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
        //
        // 中文说法一个人一个叫法，所以每条规则都尽量把常见叫法收全：
        // 以 naturalRegeneration 为例，「自然回血 / 生命恢复 / 生命自然恢复 / 自动回血」
        // 都得能命中，否则玩家只能靠猜。
        Map<String, String> gamerules = mapOf(
                "死亡不掉落", "keepInventory",
                "保留物品", "keepInventory",
                "不掉落物品", "keepInventory",
                "死亡不丢东西", "keepInventory",
                "keepinventory", "keepInventory",
                "生物破坏", "mobGriefing",
                "怪物破坏", "mobGriefing",
                "生物变方块", "mobGriefing",
                "mobgriefing", "mobGriefing",
                "昼夜交替", "doDaylightCycle",
                "日夜交替", "doDaylightCycle",
                "白天黑夜交替", "doDaylightCycle",
                "dodaylightcycle", "doDaylightCycle",
                "天气变化", "doWeatherCycle",
                "天气循环", "doWeatherCycle",
                "doweathercycle", "doWeatherCycle",
                "自然回血", "naturalRegeneration",
                "自然恢复", "naturalRegeneration",
                "自然回复", "naturalRegeneration",
                "生命恢复", "naturalRegeneration",
                "生命回复", "naturalRegeneration",
                "生命自然恢复", "naturalRegeneration",
                "自动回血", "naturalRegeneration",
                "回血", "naturalRegeneration",
                "naturalregeneration", "naturalRegeneration",
                "死亡消息", "showDeathMessages",
                "死亡提示", "showDeathMessages",
                "showdeathmessages", "showDeathMessages",
                "怪物生成", "doMobSpawning",
                "生物生成", "doMobSpawning",
                "刷怪", "doMobSpawning",
                "domobspawning", "doMobSpawning",
                "火势蔓延", "doFireTick",
                "火焰蔓延", "doFireTick",
                "dofiretick", "doFireTick",
                "立即重生", "doImmediateRespawn",
                "立刻重生", "doImmediateRespawn",
                "doimmediaterespawn", "doImmediateRespawn");

        // 附魔：中文名 → 原版附魔 ID。前缀匹配用，所以「火焰保护」必须能被
        // 「火焰附加」之外单独命中（靠最长前缀，不靠顺序）。
        Map<String, String> enchants = mapOf(
                "锋利", "minecraft:sharpness",
                "sharpness", "minecraft:sharpness",
                "亡灵杀手", "minecraft:smite",
                "smite", "minecraft:smite",
                "节肢杀手", "minecraft:bane_of_arthropods",
                "bane_of_arthropods", "minecraft:bane_of_arthropods",
                "击退", "minecraft:knockback",
                "knockback", "minecraft:knockback",
                "火焰附加", "minecraft:fire_aspect",
                "fire_aspect", "minecraft:fire_aspect",
                "抢夺", "minecraft:looting",
                "looting", "minecraft:looting",
                "横扫之刃", "minecraft:sweeping_edge",
                "sweeping_edge", "minecraft:sweeping_edge",
                "效率", "minecraft:efficiency",
                "efficiency", "minecraft:efficiency",
                "精准采集", "minecraft:silk_touch",
                "silk_touch", "minecraft:silk_touch",
                "耐久", "minecraft:unbreaking",
                "unbreaking", "minecraft:unbreaking",
                "时运", "minecraft:fortune",
                "fortune", "minecraft:fortune",
                "力量", "minecraft:power",
                "power", "minecraft:power",
                "冲击", "minecraft:punch",
                "punch", "minecraft:punch",
                "火矢", "minecraft:flame",
                "flame", "minecraft:flame",
                "无限", "minecraft:infinity",
                "infinity", "minecraft:infinity",
                "引雷", "minecraft:channeling",
                "channeling", "minecraft:channeling",
                "忠诚", "minecraft:loyalty",
                "loyalty", "minecraft:loyalty",
                "激流", "minecraft:riptide",
                "riptide", "minecraft:riptide",
                "穿刺", "minecraft:impaling",
                "impaling", "minecraft:impaling",
                "多重射击", "minecraft:multishot",
                "multishot", "minecraft:multishot",
                "快速装填", "minecraft:quick_charge",
                "quick_charge", "minecraft:quick_charge",
                "穿透", "minecraft:piercing",
                "piercing", "minecraft:piercing",
                "保护", "minecraft:protection",
                "protection", "minecraft:protection",
                "火焰保护", "minecraft:fire_protection",
                "fire_protection", "minecraft:fire_protection",
                "摔落保护", "minecraft:feather_falling",
                "feather_falling", "minecraft:feather_falling",
                "爆炸保护", "minecraft:blast_protection",
                "blast_protection", "minecraft:blast_protection",
                "弹射物保护", "minecraft:projectile_protection",
                "projectile_protection", "minecraft:projectile_protection",
                "水下呼吸", "minecraft:respiration",
                "respiration", "minecraft:respiration",
                "水下速掘", "minecraft:aqua_affinity",
                "aqua_affinity", "minecraft:aqua_affinity",
                "荆棘", "minecraft:thorns",
                "thorns", "minecraft:thorns",
                "深海探索者", "minecraft:depth_strider",
                "depth_strider", "minecraft:depth_strider",
                "冰霜行者", "minecraft:frost_walker",
                "frost_walker", "minecraft:frost_walker",
                "灵魂疾行", "minecraft:soul_speed",
                "soul_speed", "minecraft:soul_speed",
                "迅捷潜行", "minecraft:swift_sneak",
                "swift_sneak", "minecraft:swift_sneak",
                "经验修补", "minecraft:mending",
                "mending", "minecraft:mending",
                "绑定诅咒", "minecraft:binding_curse",
                "binding_curse", "minecraft:binding_curse",
                "消失诅咒", "minecraft:vanishing_curse",
                "vanishing_curse", "minecraft:vanishing_curse");

        // 实体：中文名 → 原版实体 ID，供「生成」指令用。
        // 「坚守者」是玩家最常用的叫法，官方译名其实是「监守者」，两个都收。
        Map<String, String> entities = mapOf(
                // —— 敌对 ——
                "僵尸", "minecraft:zombie",
                "僵尸村民", "minecraft:zombie_villager",
                "尸壳", "minecraft:husk",
                "溺尸", "minecraft:drowned",
                "骷髅", "minecraft:skeleton",
                "弓箭手", "minecraft:skeleton",
                "流浪者", "minecraft:stray",
                "凋灵骷髅", "minecraft:wither_skeleton",
                "苦力怕", "minecraft:creeper",
                "爬行者", "minecraft:creeper",
                "蜘蛛", "minecraft:spider",
                "洞穴蜘蛛", "minecraft:cave_spider",
                "末影人", "minecraft:enderman",
                "小黑", "minecraft:enderman",
                "末影螨", "minecraft:endermite",
                "蠹虫", "minecraft:silverfish",
                "史莱姆", "minecraft:slime",
                "岩浆怪", "minecraft:magma_cube",
                "恶魂", "minecraft:ghast",
                "烈焰人", "minecraft:blaze",
                "女巫", "minecraft:witch",
                "掠夺者", "minecraft:pillager",
                "卫道士", "minecraft:vindicator",
                "唤魔者", "minecraft:evoker",
                "幻术师", "minecraft:illusioner",
                "劫掠兽", "minecraft:ravager",
                "潜影贝", "minecraft:shulker",
                "幻翼", "minecraft:phantom",
                "监守者", "minecraft:warden",
                "坚守者", "minecraft:warden",
                "远古守卫者", "minecraft:elder_guardian",
                "守卫者", "minecraft:guardian",
                // —— 下界 ——
                "猪灵", "minecraft:piglin",
                "猪灵蛮兵", "minecraft:piglin_brute",
                "疣猪兽", "minecraft:hoglin",
                "僵尸猪灵", "minecraft:zombified_piglin",
                "僵尸猪人", "minecraft:zombified_piglin",
                "僵尸疣猪兽", "minecraft:zoglin",
                // —— BOSS ——
                "末影龙", "minecraft:ender_dragon",
                "凋灵", "minecraft:wither",
                // —— 友好 ——
                "村民", "minecraft:villager",
                "流浪商人", "minecraft:wandering_trader",
                "铁傀儡", "minecraft:iron_golem",
                "雪傀儡", "minecraft:snow_golem",
                "豹猫", "minecraft:ocelot",
                "猫", "minecraft:cat",
                "狼", "minecraft:wolf",
                "狗", "minecraft:wolf",
                "狐狸", "minecraft:fox",
                "熊猫", "minecraft:panda",
                "北极熊", "minecraft:polar_bear",
                "羊驼", "minecraft:llama",
                "马", "minecraft:horse",
                "驴", "minecraft:donkey",
                "骡", "minecraft:mule",
                "骷髅马", "minecraft:skeleton_horse",
                "僵尸马", "minecraft:zombie_horse",
                "骆驼", "minecraft:camel",
                "猪", "minecraft:pig",
                "牛", "minecraft:cow",
                "哞菇", "minecraft:mooshroom",
                "羊", "minecraft:sheep",
                "鸡", "minecraft:chicken",
                "兔子", "minecraft:rabbit",
                "青蛙", "minecraft:frog",
                "美西螈", "minecraft:axolotl",
                "海龟", "minecraft:turtle",
                "海豚", "minecraft:dolphin",
                "鱿鱼", "minecraft:squid",
                "发光鱿鱼", "minecraft:glow_squid",
                "蜜蜂", "minecraft:bee",
                "蝙蝠", "minecraft:bat",
                "鹦鹉", "minecraft:parrot",
                "山羊", "minecraft:goat",
                "盔甲架", "minecraft:armor_stand");

        // 整句短语：接住「归别的指令管」或「一句顶多条指令」的说法。
        // 例：死亡不掉落属于 gamerule keepInventory，不归 gamemode 管。
        // 值里出现 \n 表示这一句要按顺序发多条指令（见 ParseResult#commands()）。
        Map<String, String> phrases = mapOf(
                "死亡不掉落", "gamerule keepInventory true",
                "开启死亡不掉落", "gamerule keepInventory true",
                "打开死亡不掉落", "gamerule keepInventory true",
                "关闭死亡不掉落", "gamerule keepInventory false",
                "关掉死亡不掉落", "gamerule keepInventory false",
                // 时间：直接说「白天」「夜晚」也算一条指令，不必先说「时间」
                "白天", "time set day",
                "白昼", "time set day",
                "天亮", "time set day",
                "夜晚", "time set night",
                "黑夜", "time set night",
                // 永为白昼 = 先调到白天，再关掉昼夜交替（一条口语对应两条指令）
                "永为白昼", "time set day" + SEP + "gamerule doDaylightCycle false",
                "永远白天", "time set day" + SEP + "gamerule doDaylightCycle false",
                "永为黑夜", "time set night" + SEP + "gamerule doDaylightCycle false",
                "永远黑夜", "time set night" + SEP + "gamerule doDaylightCycle false",
                // 后悔了：把昼夜交替打开
                "恢复昼夜交替", "gamerule doDaylightCycle true",
                // 清除效果：三种说法都收，和「重置 效果」等价
                "清除效果", RESET_EFFECTS,
                "清除所有效果", RESET_EFFECTS,
                "清除药水效果", RESET_EFFECTS,
                "设置重生点", "spawnpoint",
                "设置出生点", "spawnpoint",
                "杀死自己", "kill @s",
                "自杀", "kill @s");

        return new AliasTable(verbs, values, items, gamerules, entities, enchants, phrases);
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
