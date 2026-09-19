package io.github.xiaoh.chatcmd.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文指令解析引擎，零 Minecraft 依赖。
 *
 * <p>
 * 输入语法（计划 §6.1）：
 * 
 * <pre>
 * Input = "#" , Verb , [ Separator , Args ]
 * </pre>
 *
 * <p>
 * 解析成功后产出的是<b>不含前导斜杠</b>的原版指令串，可直接交给客户端发送。
 *
 * <p>
 * 匹配算法（计划 §6.4）分 6 步：归一化 → 触发判定 → 整句短语 → 动词最长前缀匹配
 * → 参数切分 → 逐参解析组装。
 *
 * <p>
 * 精确匹配全部失败后才走 {@link Fuzzy} 的编辑距离兜底，并且分三级：
 * <ul>
 * <li><b>自动纠错</b>（严格阈值）：直接执行，同时在 {@link ParseResult#hint()} 里注明
 * 「已按模糊匹配识别为 X」，绝不静默改词。</li>
 * <li><b>提示纠错</b>（宽松一档）：不执行，只在报错里附一句「你是不是想用 #X ？」。</li>
 * <li><b>候选列表</b>（{@link ParseResult#suggestions()}）：把几个可能的正确写法摆出来，
 * 用户点一下填回聊天框，回车前还能改。</li>
 * </ul>
 *
 * <p>
 * 另有一道<b>高危闸门</b>：{@link CommandType#CLEAR} 与「重置 全部」这类不可逆指令解析成功后
 * 不直接给 {@link ParseResult.Status#OK}，而是给 {@link ParseResult.Status#CONFIRM}，
 * 由适配层暂存，等玩家再发一条 {@code #确认} 才真正发出去。
 */
public final class CommandParser {

    /** 触发前缀。 */
    public static final String TRIGGER = "#";

    /** 逃生通道前缀：{@code ##任意文本} 会去掉一个 # 后作为普通聊天发出。 */
    public static final String ESCAPE = "##";

    /**
     * 确认词：整行<b>只有</b>这些词才算「确认」，防止和正常指令抢词。
     *
     * <p>高危指令（见 {@link #requireConfirm}）解析成功后不会立刻执行，
     * 而是把待执行指令交给适配层暂存，玩家再发一条这个表里的词才真发出去。
     */
    private static final Set<String> CONFIRM_WORDS =
            Set.of("确认", "确定", "是", "好", "好的", "可以", "执行", "yes", "y", "ok");

    /** 取消词：整行只有这些词才算「取消」，把待确认的指令丢掉。 */
    private static final Set<String> DECLINE_WORDS =
            Set.of("取消", "算了", "不要", "否", "不", "no", "n");

    /** 高危指令确认界面上的可点击候选（会拼上触发前缀）。 */
    private static final List<String> CONFIRM_SUGGESTIONS =
            List.of(TRIGGER + "确认", TRIGGER + "取消");

    /** 「生成」一次最多生成几个：原版 /summon 一次只能一个，数量大了回显会把聊天框刷爆。 */
    private static final int MAX_SUMMON = 16;

    /** 坐标：支持 {@code ~} / {@code ~5} / {@code ~-3} / {@code 100} / {@code -64.5}。 */
    private static final Pattern COORD = Pattern.compile("^(~|~?-?\\d+(\\.\\d+)?)$");

    /** 正整数数量。 */
    private static final Pattern COUNT = Pattern.compile("^\\d+$");

    /** 带单位的时长参数：{@code 30秒} / {@code 30s}。有单位的那个永远算时长，不受顺序影响。 */
    private static final Pattern SECOND_TOKEN = Pattern.compile("^(\\d+)\\s*(?:秒|s)$");

    /** 口语整句里的时长片段：{@code 给我30秒的速度2效果}。 */
    private static final Pattern SECOND_INLINE = Pattern.compile("(\\d+)\\s*(?:秒|s)");

    /** 口语整句里的等级片段：{@code 速度2级} / {@code 速度2等级}。 */
    private static final Pattern LEVEL_INLINE = Pattern.compile("(\\d+)\\s*(?:级|等级)");

    /** 口语整句效果的开头引导词，按长的在前匹配。 */
    private static final String[] EFFECT_LEADERS = { "给我", "帮我", "我要", "给", "要", "来" };

    /** 口语整句效果的结尾标记词；句子里出现它才允许对效果名做模糊纠错。 */
    private static final String[] EFFECT_TAILS = { "状态效果", "效果", "状态" };

    /** 量词：中文里说数量时几乎都会带一个，「1个齿轮」「1把钻石剑」都靠它。 */
    private static final String MEASURE_WORD = "(?:个|把|块|组|颗|支|张|件|只|条|根|桶|瓶|箱|份|套|堆|枚|面|双|对|台|辆)?";

    /** 数量前移：{@code 1把钻石剑} / {@code 3个 齿轮} / {@code 2钻石块}。 */
    private static final Pattern LEADING_COUNT = Pattern.compile("^(\\d+)\\s*" + MEASURE_WORD + "\\s*(.+)$");

    /** 整个参数就是一个数量（可带量词）：{@code 1} / {@code 1个} / {@code 3把}。 */
    private static final Pattern COUNT_TOKEN = Pattern.compile("^(\\d+)\\s*" + MEASURE_WORD + "$");

    /** 尾部数量：{@code 齿轮1} 拆成 {@code 齿轮} + {@code 1}。 */
    private static final Pattern TRAILING_COUNT = Pattern.compile("^(.+?)(\\d+)$");

    /**
     * 看起来像原版 ID 的串（纯 ASCII 的小写字母/数字/下划线/点/横线，可带命名空间）。
     *
     * <p>
     * 只有这种才允许「原样透传」。否则 {@code 齿轮1} 会被硬拼成 {@code minecraft:齿轮1}
     * 丢给服务端，玩家看到的是一句莫名其妙的「未知的物品 minecraft:齿轮1」。
     */
    private static final Pattern RAW_ID = Pattern.compile("^[a-z0-9_.\\-]+(:[a-z0-9_./\\-]+)?$");

    private final AliasTable aliasTable;
    private final ItemIdResolver itemIdResolver;
    private final EnchantSyntax enchantSyntax;

    /** 默认按 1.20.5+ 的物品组件语法组装附魔指令。 */
    public CommandParser(AliasTable aliasTable, ItemIdResolver itemIdResolver) {
        this(aliasTable, itemIdResolver, EnchantSyntax.ITEM_COMPONENT);
    }

    /**
     * @param enchantSyntax 附魔指令的语法档位，由平台适配层按目标 Minecraft 版本指定
     */
    public CommandParser(AliasTable aliasTable, ItemIdResolver itemIdResolver, EnchantSyntax enchantSyntax) {
        this.aliasTable = aliasTable;
        this.itemIdResolver = itemIdResolver;
        this.enchantSyntax = enchantSyntax;
    }

    /**
     * 解析一行聊天输入。
     *
     * @param rawInput 玩家在聊天框里输入的原文，可为 {@code null}
     * @return 解析结果；不以 {@code #} 开头时返回 {@link ParseResult.Status#NOT_MY_INPUT}
     */
    public ParseResult parse(String rawInput) {
        if (rawInput == null) {
            return ParseResult.notMyInput();
        }

        // 1. 归一化
        String input = normalize(rawInput);

        // 2. 触发判定
        if (!input.startsWith(TRIGGER)) {
            return ParseResult.notMyInput();
        }
        // 逃生通道：##任意文本 → 去掉一个 # 作为普通聊天发出
        if (input.startsWith(ESCAPE)) {
            return ParseResult.escapeChat(input.substring(1));
        }

        String body = input.substring(TRIGGER.length()).trim();
        if (body.isEmpty()) {
            return ParseResult.error(ParseResult.Status.UNKNOWN_VERB,
                    "在 # 后面写上指令，例如 " + CommandType.GIVE.usage());
        }

        // 2b. 高危指令的二次确认回话：整行就是「确认」或「取消」
        if (CONFIRM_WORDS.contains(body)) {
            return ParseResult.confirmAccept();
        }
        if (DECLINE_WORDS.contains(body)) {
            return ParseResult.confirmDecline();
        }

        // 3. 整句短语精确匹配：口语整句 → 完整指令（如「死亡不掉落」「永为白昼」）
        Optional<String> phrase = aliasTable.phrase(body);
        if (phrase.isPresent()) {
            return ParseResult.ok(phrase.get());
        }

        // 3b. 口语整句效果：「给我30秒的速度2效果」→ effect give @s minecraft:speed 30 2
        Optional<ParseResult> sentence = parseEffectSentence(body);
        if (sentence.isPresent()) {
            return sentence.get();
        }

        // 4. 动词匹配（最长前缀优先）
        String note = "";
        Optional<AliasTable.VerbMatch> matchOpt = aliasTable.findLongestVerbPrefix(body);
        if (matchOpt.isEmpty()) {
            // 4a. 整句短语模糊兜底（整句打错字）
            Optional<AliasTable.Match> fuzzyPhrase = aliasTable.fuzzyPhrase(body);
            if (fuzzyPhrase.isPresent()) {
                return ParseResult.ok(fuzzyPhrase.get().value(),
                        fuzzyNote(fuzzyPhrase.get().key()));
            }
            // 4b. 动词模糊兜底（动词打错字）
            Optional<AliasTable.VerbMatch> fuzzyVerb = aliasTable.findFuzzyVerbPrefix(body);
            if (fuzzyVerb.isEmpty()) {
                return ParseResult.error(ParseResult.Status.UNKNOWN_VERB, unknownVerbHint(body),
                        unknownVerbSuggestions(body));
            }
            matchOpt = fuzzyVerb;
            note = fuzzyNote(fuzzyVerb.get().alias());
        }
        AliasTable.VerbMatch match = matchOpt.get();
        String rest = body.substring(match.matchedLength()).trim();

        // 5. 参数切分
        List<String> args = splitArgs(rest, match.type());
        if (args.size() < match.type().minArgs() || args.size() > match.type().maxArgs()) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "参数个数不对。用法：" + match.type().usage());
        }

        // 6. 逐参解析 + 组装
        return assemble(match.type(), args, note, input);
    }

    /**
     * 认不出动词时给出的可点击候选：把输入开头的那个词换成最接近的动词，后面的参数原样保留。
     *
     * <p>
     * 例如 {@code #模组 创造} → 候选 {@code #模式 创造}，点一下就填回聊天框，
     * 不用把参数重打一遍。
     */
    private List<String> unknownVerbSuggestions(String body) {
        int space = body.indexOf(' ');
        String head = space < 0 ? body : body.substring(0, space);
        String tail = space < 0 ? "" : body.substring(space);
        return aliasTable.nearestVerbs(head, 5).stream()
                .map(verb -> TRIGGER + verb + tail)
                .toList();
    }

    /**
     * 把整行输入里的某个片段换成候选，拼出可直接填回聊天框的一行。
     *
     * <p>
     * 取<b>最后</b>一次出现的位置：参数总在动词之后，这样不会误改动词里碰巧相同的字。
     */
    private static String replaceLast(String input, String target, String replacement) {
        int index = input.lastIndexOf(target);
        if (index < 0) {
            return TRIGGER + replacement;
        }
        return input.substring(0, index) + replacement + input.substring(index + target.length());
    }

    /** 把若干候选词拼成「整行输入」形式的候选列表，供适配层渲染成可点击的文字。 */
    private static List<String> suggestionsFor(String input, String target, List<String> candidates) {
        return candidates.stream().map(candidate -> replaceLast(input, target, candidate)).toList();
    }

    /** 认不出动词时的提示：附一句最接近的候选，但不自动执行。 */
    private String unknownVerbHint(String body) {
        String hint = "没认出「" + body + "」这条指令。可用的动词：" + aliasTable.verbHint();
        Optional<String> suggest = aliasTable.nearestVerb(body);
        return suggest.map(s -> hint + "。你是不是想用：#" + s + " ？").orElse(hint);
    }

    /** 模糊匹配命中的统一回显文案。 */
    private static String fuzzyNote(String key) {
        return "已按模糊匹配识别为「" + key + "」";
    }

    /** 拼接两条说明，空串自动跳过。 */
    private static String join(String a, String b) {
        if (a.isEmpty()) {
            return b;
        }
        return b.isEmpty() ? a : a + "；" + b;
    }

    /**
     * 归一化：全角转半角（含全角空格、全角数字）→ 去首尾空白 → 英文转小写。
     * 中文不受影响。
     *
     * <p>
     * 对适配层公开：从游戏注册表里取到的本地化物品名必须走同一套归一化，
     * 才能和玩家输入对得上。
     */
    public static String normalize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u3000') {
                // 全角空格
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                // 全角 ASCII 区，整体偏移 0xFEE0 变半角
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }

    /** 参数切分：枚举类去空格整体查表；其它先按空格切，只得 1 段但要多参数时再按逗号切。 */
    private static List<String> splitArgs(String rest, CommandType type) {
        if (rest.isEmpty()) {
            return List.of();
        }
        if (type.enumArgs()) {
            // 枚举类：去掉全部内部空格后整体作为一个参数，"时间 白 天" 也能命中
            return List.of(rest.replace(" ", ""));
        }

        List<String> parts = new ArrayList<>();
        for (String piece : rest.split("\\s+")) {
            if (!piece.isEmpty()) {
                parts.add(piece);
            }
        }
        if (parts.size() == 1 && type.maxArgs() > 1) {
            // 只切出 1 段但需要多个参数 → 再按逗号切（如 "100,64,100"）
            String[] byComma = parts.get(0).split(",");
            if (byComma.length > 1) {
                parts = new ArrayList<>();
                for (String piece : byComma) {
                    String trimmed = piece.trim();
                    if (!trimmed.isEmpty()) {
                        parts.add(trimmed);
                    }
                }
            }
        }
        return parts;
    }

    /** 按指令类型组装。{@code input} 是归一化后的整行输入，用来拼可点击候选。 */
    private ParseResult assemble(CommandType type, List<String> args, String note, String input) {
        // 组装出结果后统一过高危闸门：CLEAR 与「重置 全部」会转成待确认状态
        return requireConfirm(type, switch (type) {
            case GIVE -> assembleGive(args, note, input);
            case TP -> assembleTp(args);
            case GAMERULE -> assembleGamerule(args, note, input);
            case TIME, GAMEMODE, WEATHER, DIFFICULTY, CLEAR, LOCATE -> assembleEnum(type, args, note, input);
            case SUMMON -> assembleSummon(args, note, input);
            case RESET -> assembleReset(args, note, input);
            case EFFECT -> assembleEffect(args, note, input);
            case ENCHANT -> assembleEnchant(args, note, input);
            case ENCHANT_HELD -> assembleEnchantHeld(args, note, input);
            case XP -> assembleXp(args);
        });
    }

    // ---------- 高危指令的二次确认 ----------

    /**
     * 高危指令统一加一道「二次确认」闸门。
     *
     * <p>判据是<b>真会批量删东西</b>的动词：{@link CommandType#CLEAR}（取值全是 {@code kill @e}）
     * 和「重置 全部」（把时间 / 天气 / 效果 / 规则一次性还原）。其余指令不拦，照旧一步到位。
     *
     * <p>为什么要拦：{@code #清除 怪物} 生成的是
     * {@code kill @e[type=!player,type=!minecraft:item,type=!minecraft:experience_orb]} ——
     * 原版没有「只杀敌对生物」的选择器，动物、村民、宠物会一起被清掉。
     * 新手以为只清怪物，一次误操作就没了。
     *
     * @return 命中高危时返回 {@link ParseResult.Status#CONFIRM}（载荷与成功时相同，只是先不执行）
     */
    private ParseResult requireConfirm(CommandType type, ParseResult result) {
        if (!result.isOk()) {
            return result;
        }
        String danger = dangerNote(type, result.payload());
        if (danger == null) {
            return result;
        }
        StringBuilder shown = new StringBuilder();
        for (String command : result.commands()) {
            if (shown.length() > 0) {
                shown.append("  ");
            }
            shown.append('/').append(command);
        }
        return ParseResult.confirm(result.payload(),
                (result.hint().isEmpty() ? "" : result.hint() + "；")
                        + "高危指令，先确认再执行：将执行 " + shown
                        + "。" + danger
                        + "。确认无误请再发一条 #确认；发别的指令即作废",
                CONFIRM_SUGGESTIONS);
    }

    /**
     * 这条指令危险在哪 —— 返回 {@code null} 表示不危险，无需确认。
     *
     * <p>文案按<b>实际生成的指令</b>给，不按动词给：{@code #清除 怪物} 和 {@code #清除 掉落物}
     * 都是「清除」，后果却完全不同，警告必须说准。
     */
    private String dangerNote(CommandType type, String payload) {
        if (type == CommandType.RESET) {
            return payload.equals(aliasTable.resetAll())
                    ? "会把时间、天气、效果、规则一起还原成原版默认值"
                    : null;
        }
        if (type != CommandType.CLEAR) {
            return null;
        }
        if (payload.contains("type=!player")) {
            return "会清掉除玩家、掉落物、经验球之外的所有实体 —— 原版没有「只杀敌对生物」的"
                    + "选择器，动物、村民、已驯服的宠物也会一起被清掉";
        }
        if (payload.contains("experience_orb")) {
            return "会清掉地上全部经验球";
        }
        if (payload.contains("minecraft:item")) {
            return "会清掉地上全部掉落物";
        }
        return "会清空身上全部药水效果";
    }

    /** 枚举类：查值别名表，精确失败后模糊兜底，再失败就把候选列出来让用户点。 */
    private ParseResult assembleEnum(CommandType type, List<String> args, String note, String input) {
        String raw = args.get(0);
        Optional<String> value = aliasTable.value(type, raw);
        if (value.isEmpty()) {
            Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyValue(type, raw);
            if (fuzzy.isPresent()) {
                value = Optional.of(fuzzy.get().value());
                note = join(note, fuzzyNote(fuzzy.get().key()));
            }
        }
        if (value.isPresent()) {
            // 前缀为空串的类型（重置 / 清除）取值本身就是完整指令，不再拼前缀
            String command = type.commandPrefix().isEmpty()
                    ? value.get()
                    : type.commandPrefix() + " " + value.get();
            return ParseResult.ok(command, note);
        }
        // 候选：先列离得近的；一个都列不出来（如「#模式飞行」）就把该类型的取值全摆出来
        List<String> candidates = aliasTable.nearestValues(type, raw, 5);
        if (candidates.isEmpty()) {
            candidates = aliasTable.valueSuggestions(type);
        }
        return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE, unknownValueHint(type, raw),
                suggestionsFor(input, raw, candidates));
    }

    /**
     * 取值不合法时的提示。
     *
     * <p>
     * 如果这个取值其实是某条整句短语（例如 {@code #模式 死亡不掉落} 里的「死亡不掉落」），
     * 就直接告诉用户正确的说法 —— 这类错误是「走错动词」而不是「打错字」。
     */
    private String unknownValueHint(CommandType type, String raw) {
        Optional<String> suggest = aliasTable.phrase(raw).isPresent()
                ? Optional.of(raw)
                : aliasTable.nearestPhrase(raw);
        if (suggest.isPresent()) {
            return "「" + raw + "」不归 " + type.displayName() + " 管。你是不是想用：#"
                    + suggest.get() + " ？";
        }
        return "「" + raw + "」不是有效的取值。用法：" + type.usage();
    }

    /**
     * 给予物品：{@code give @s <item> <count>}，数量缺省为 1。
     *
     * <p>
     * 数量支持三种写法（顺序即优先级）：
     * <ol>
     * <li>数量在最后，名字和数量之间必须有空格：{@code 给我 钻石剑 5}</li>
     * <li>数量在前并带量词：{@code 给我1个齿轮} / {@code 给我 1把钻石剑} / {@code 给我 2个 石头}</li>
     * <li>名字和数量粘在一起：{@code 给我 齿轮1} —— 只在<b>精确匹配失败</b>时才把尾部数字
     * 当数量，这样名字本身带数字的物品不会被误伤</li>
     * </ol>
     */
    private ParseResult assembleGive(List<String> args, String note, String input) {
        String itemRaw = args.get(0);
        String countRaw = args.size() >= 2 ? args.get(1) : null;

        if (args.size() == 1) {
            Matcher leading = LEADING_COUNT.matcher(itemRaw);
            if (leading.matches()) {
                countRaw = leading.group(1);
                itemRaw = leading.group(2);
            }
        } else {
            Matcher countToken = COUNT_TOKEN.matcher(itemRaw);
            if (countToken.matches()) {
                // 数量自己占了一个参数位且排在前面：「给我 1个 齿轮」
                countRaw = countToken.group(1);
                itemRaw = args.get(1);
            }
        }

        // 物品先走精确匹配（手工表 + 游戏注册表）
        Optional<String> itemId = resolveItemExact(itemRaw);
        if (itemId.isEmpty() && countRaw == null) {
            // 名字和数量粘在一起（「齿轮1」）：精确匹配失败，才把尾部数字拆出来当数量。
            // 必须放在模糊匹配之前，否则「钻石剑3」会先被模糊匹配吃掉，
            // 结果变成给 1 个钻石剑，而不是 3 个。
            Matcher trailing = TRAILING_COUNT.matcher(itemRaw);
            if (trailing.matches()) {
                itemId = resolveItemExact(trailing.group(1));
                if (itemId.isPresent()) {
                    itemRaw = trailing.group(1);
                    countRaw = trailing.group(2);
                }
            }
        }
        // 精确匹配全失败，才走模糊纠错 + 原版 ID 透传
        if (itemId.isEmpty()) {
            ItemResolution loose = resolveItemLoosely(itemRaw, note);
            itemId = loose.value();
            note = loose.note();
        }

        if (itemId.isEmpty()) {
            // 本地就报错，并列出候选 —— 绝不把中文名硬拼成 minecraft: 前缀丢给服务端
            List<String> candidates = aliasTable.nearestItems(itemRaw, itemIdResolver.candidates(), 5);
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "不认识物品「" + itemRaw + "」。用法：" + CommandType.GIVE.usage(),
                    suggestionsFor(input, itemRaw, candidates));
        }

        int count = 1;
        if (countRaw != null) {
            if (!COUNT.matcher(countRaw).matches()) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "数量「" + countRaw + "」不是正整数。用法：" + CommandType.GIVE.usage());
            }
            try {
                count = Integer.parseInt(countRaw);
            } catch (NumberFormatException e) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "数量「" + countRaw + "」超出范围。");
            }
            if (count <= 0) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS, "数量必须是正整数。");
            }
        }
        return ParseResult.ok(
                CommandType.GIVE.commandPrefix() + " @s " + itemId.get() + " " + count, note);
    }

    /** 物品解析结果：解析出的物品 ID + 需要回显给用户的模糊匹配说明。 */
    private record ItemResolution(Optional<String> value, String note) {
    }

    /**
     * 物品精确匹配：手工别名表 → 游戏注册表。
     *
     * <p>
     * 模组物品（整合包里那几百个）只认这一条路。刻意不做模糊匹配：
     * 齿轮 / 大齿轮 / 小齿轮 彼此只差一个字，自动猜错等于静默给错东西。
     */
    private Optional<String> resolveItemExact(String raw) {
        Optional<String> fromTable = aliasTable.item(raw);
        if (fromTable.isPresent()) {
            return fromTable;
        }
        return itemIdResolver.resolve(raw);
    }

    /**
     * 精确匹配失败后的兜底（计划 §6.2 的后两级）：
     * <ol>
     * <li>手工表模糊纠错（错别字，如「钻右剑」→「钻石剑」）</li>
     * <li>看起来像原版 ID 才原样透传；否则认输，交给上层报错并给候选</li>
     * </ol>
     */
    private ItemResolution resolveItemLoosely(String raw, String note) {
        Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyItem(raw);
        if (fuzzy.isPresent()) {
            return new ItemResolution(Optional.of(fuzzy.get().value()),
                    join(note, fuzzyNote(fuzzy.get().key())));
        }
        if (!RAW_ID.matcher(raw).matches()) {
            return new ItemResolution(Optional.empty(), note);
        }
        // 输入已在 normalize 里转过小写，这里只需补前缀和转下划线
        String id = raw.replace(' ', '_');
        return new ItemResolution(Optional.of(id.contains(":") ? id : "minecraft:" + id), note);
    }

    /** 游戏规则：{@code gamerule <rule> <true|false>}，规则名与开关都支持中文别名。 */
    private ParseResult assembleGamerule(List<String> args, String note, String input) {
        String ruleRaw = args.get(0);
        // 规则名的中文说法一路同义词 + 一路模糊兜底：同一条 naturalRegeneration
        // 有人叫「自然回血」有人叫「生命恢复」，精确表收同义词，错别字交给编辑距离。
        Optional<String> rule = aliasTable.gamerule(ruleRaw);
        if (rule.isEmpty()) {
            Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyGamerule(ruleRaw);
            if (fuzzy.isPresent()) {
                rule = Optional.of(fuzzy.get().value());
                note = join(note, fuzzyNote(fuzzy.get().key()));
            }
        }
        if (rule.isEmpty()) {
            List<String> candidates = aliasTable.nearestGamerules(ruleRaw, 5);
            if (candidates.isEmpty()) {
                candidates = aliasTable.gameruleSuggestions();
            }
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "不认识规则「" + ruleRaw + "」。用法：" + CommandType.GAMERULE.usage(),
                    suggestionsFor(input, ruleRaw, candidates));
        }

        String boolRaw = args.get(1);
        Optional<String> boolValue = aliasTable.value(CommandType.GAMERULE, boolRaw);
        if (boolValue.isEmpty()) {
            Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyValue(CommandType.GAMERULE, boolRaw);
            if (fuzzy.isPresent()) {
                boolValue = Optional.of(fuzzy.get().value());
                note = join(note, fuzzyNote(fuzzy.get().key()));
            }
        }
        if (boolValue.isEmpty()) {
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "「" + boolRaw + "」不是有效的开关。用法：" + CommandType.GAMERULE.usage(),
                    suggestionsFor(input, boolRaw, aliasTable.valueSuggestions(CommandType.GAMERULE)));
        }
        return ParseResult.ok(
                CommandType.GAMERULE.commandPrefix() + " " + rule.get() + " " + boolValue.get(), note);
    }

    /**
     * 生成实体：{@code summon <entity> [数量] [x y z]}。
     *
     * <p>支持三种参数写法：
     * <ol>
     *   <li>{@code #生成 僵尸} —— 就在自己脚下生成一个</li>
     *   <li>{@code #生成 僵尸 5} —— 生成 5 个（原版一次只能一个，所以拆成 5 条依次发）</li>
     *   <li>{@code #生成 僵尸 ~ ~1 ~} —— 生成在指定坐标</li>
     * </ol>
     */
    private ParseResult assembleSummon(List<String> args, String note, String input) {
        String entityRaw = args.get(0);
        Optional<String> entity = resolveEntityExact(entityRaw);
        if (entity.isEmpty()) {
            Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyEntity(entityRaw);
            if (fuzzy.isPresent()) {
                entity = Optional.of(fuzzy.get().value());
                note = join(note, fuzzyNote(fuzzy.get().key()));
            }
        }
        if (entity.isEmpty()) {
            List<String> candidates = aliasTable.nearestEntities(entityRaw, 5);
            if (candidates.isEmpty()) {
                candidates = aliasTable.entitySuggestions();
            }
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "不认识实体「" + entityRaw + "」。用法：" + CommandType.SUMMON.usage(),
                    suggestionsFor(input, entityRaw, candidates));
        }

        List<String> rest = args.subList(1, args.size());
        int count = 1;
        String position = "";
        if (rest.size() == 3 && rest.stream().allMatch(arg -> COORD.matcher(arg).matches())) {
            position = " " + String.join(" ", rest);
        } else if (rest.size() == 1 && COUNT.matcher(rest.get(0)).matches()) {
            try {
                count = Integer.parseInt(rest.get(0));
            } catch (NumberFormatException e) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "数量「" + rest.get(0) + "」超出范围。用法：" + CommandType.SUMMON.usage());
            }
            if (count < 1) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "数量必须是正整数。用法：" + CommandType.SUMMON.usage());
            }
            if (count > MAX_SUMMON) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "一次最多生成 " + MAX_SUMMON + " 个（原版 /summon 一次只能生成一个，"
                                + "数量多了会刷屏）。用法：" + CommandType.SUMMON.usage());
            }
        } else if (!rest.isEmpty()) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "参数不对。用法：" + CommandType.SUMMON.usage());
        }

        String single = CommandType.SUMMON.commandPrefix() + " " + entity.get() + position;
        if (count == 1) {
            return ParseResult.ok(single, note);
        }
        note = join(note, "原版 /summon 一次只能生成一个，已拆成 " + count + " 条依次执行");
        return ParseResult.ok(String.join(ParseResult.COMMAND_SEPARATOR,
                Collections.nCopies(count, single)), note);
    }

    /**
     * 实体精确匹配：手工别名表 → 看起来像原版 ID 就原样透传。
     *
     * <p>透传是为了让模组实体（例如 {@code 某整合包:某某boss}）也能用；
     * 中文名一律走表，认不出就报错并列候选，绝不硬拼成 {@code minecraft:僵尸} 丢给服务端。
     */
    private Optional<String> resolveEntityExact(String raw) {
        Optional<String> fromTable = aliasTable.entity(raw);
        if (fromTable.isPresent()) {
            return fromTable;
        }
        if (!RAW_ID.matcher(raw).matches()) {
            return Optional.empty();
        }
        String id = raw.replace(' ', '_');
        return Optional.of(id.contains(":") ? id : "minecraft:" + id);
    }

    /** 传送：{@code tp @s <x> <y> <z>}。 */
    private ParseResult assembleTp(List<String> args) {
        for (String arg : args) {
            if (!COORD.matcher(arg).matches()) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "坐标「" + arg + "」格式不对。用法：" + CommandType.TP.usage());
            }
        }
        return ParseResult.ok(CommandType.TP.commandPrefix() + " @s " + String.join(" ", args));
    }

    /** 状态重置：取值本身就是完整指令，所以这里只需处理「不带对象 = 全部」这一种情况。 */
    private ParseResult assembleReset(List<String> args, String note, String input) {
        if (args.isEmpty()) {
            return ParseResult.ok(aliasTable.resetAll());
        }
        return assembleEnum(CommandType.RESET, args, note, input);
    }

    /** 施加药水效果：{@code effect give @s <effect> [时长] [等级]}，后两个参数原样透传。 */
    private ParseResult assembleEffect(List<String> args, String note, String input) {
        String nameRaw = args.get(0);
        Optional<String> effect = aliasTable.value(CommandType.EFFECT, nameRaw);
        if (effect.isEmpty()) {
            Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyValue(CommandType.EFFECT, nameRaw);
            if (fuzzy.isPresent()) {
                effect = Optional.of(fuzzy.get().value());
                note = join(note, fuzzyNote(fuzzy.get().key()));
            }
        }
        if (effect.isEmpty()) {
            List<String> candidates = aliasTable.nearestValues(CommandType.EFFECT, nameRaw, 5);
            if (candidates.isEmpty()) {
                candidates = aliasTable.valueSuggestions(CommandType.EFFECT);
            }
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "不认识效果「" + nameRaw + "」。用法：" + CommandType.EFFECT.usage(),
                    suggestionsFor(input, nameRaw, candidates));
        }
        // 等级在前、时长在后（跟口语习惯一致）：「#效果 速度 2 30」= 等级 2、持续 30 秒。
        // 带「秒」单位的那个永远算时长，所以「#效果 速度 30秒 2」也认。
        Integer level = null;
        Integer seconds = null;
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            Matcher withUnit = SECOND_TOKEN.matcher(arg);
            if (withUnit.matches()) {
                if (seconds != null) {
                    return ParseResult.error(ParseResult.Status.BAD_ARGS,
                            "时长写了两次。用法：" + CommandType.EFFECT.usage());
                }
                seconds = Integer.valueOf(withUnit.group(1));
                continue;
            }
            if (!COUNT.matcher(arg).matches()) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "「" + arg + "」不是正整数。用法：" + CommandType.EFFECT.usage());
            }
            if (level == null) {
                level = Integer.valueOf(arg);
            } else if (seconds == null) {
                seconds = Integer.valueOf(arg);
            } else {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "参数太多。用法：" + CommandType.EFFECT.usage());
            }
        }
        return assembleEffectWith(effect.get(), level, seconds, note);
    }

    /**
     * 组装效果指令：{@code effect give @s <效果> <时长> <amplifier>}。
     *
     * <p>
     * 原版这条指令的第 3 个参数是 <b>amplifier（放大器）</b>，0 才是 I 级，而玩家说的
     * 「等级 2」是 II 级 —— 所以这里减 1，并在回显里注明换算，免得看到 {@code 1} 以为写错了。
     *
     * <p>
     * 时长缺省 30 秒（原版 {@code /effect give} 不写时长时的默认值），等级缺省 I 级。
     */
    private ParseResult assembleEffectWith(String effectId, Integer level, Integer seconds, String note) {
        if (level != null && level < 1) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "效果等级从 1 开始（1 = I 级）。用法：" + CommandType.EFFECT.usage());
        }
        if (seconds != null && seconds < 1) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "时长至少 1 秒。用法：" + CommandType.EFFECT.usage());
        }
        if (level != null && level > 1) {
            note = join(note, "等级" + level + " = 原版 amplifier " + (level - 1));
        }
        return ParseResult.ok(CommandType.EFFECT.commandPrefix() + " " + effectId
                + " " + (seconds == null ? 30 : seconds)
                + " " + (level == null ? 0 : level - 1), note);
    }

    /**
     * 口语整句形式的效果指令：「给我30秒的速度2效果」。
     *
     * <p>
     * 只在<b>确实指向一个效果</b>时才认，认不出就原样交回常规路径 ——
     * 这样 {@code #给我 钻石剑 5} 不会被抢走。
     *
     * <p>
     * 认可的形式：
     * <ul>
     * <li>{@code #给我30秒的速度2效果} —— 时长 + 名字 + 等级</li>
     * <li>{@code #给我速度2} —— 名字 + 等级（名字必须精确命中效果表，且不是物品名）</li>
     * <li>{@code #给我30秒的夜视} —— 时长 + 名字</li>
     * <li>{@code #给我速度2状态} —— 带「状态/效果」标记时，名字允许模糊纠错</li>
     * </ul>
     */
    private Optional<ParseResult> parseEffectSentence(String body) {
        String rest = body;
        for (String leader : EFFECT_LEADERS) {
            if (rest.startsWith(leader)) {
                rest = rest.substring(leader.length());
                break;
            }
        }
        if (rest.equals(body)) {
            // 没有引导词，说明玩家就是在打「#效果 ...」这种正式写法
            return Optional.empty();
        }
        rest = rest.trim();

        boolean marked = false;
        Integer seconds = null;
        Matcher second = SECOND_INLINE.matcher(rest);
        if (second.find()) {
            seconds = Integer.valueOf(second.group(1));
            rest = rest.substring(0, second.start()) + rest.substring(second.end());
            marked = true;
        }
        Integer level = null;
        Matcher inlineLevel = LEVEL_INLINE.matcher(rest);
        if (inlineLevel.find()) {
            level = Integer.valueOf(inlineLevel.group(1));
            rest = rest.substring(0, inlineLevel.start()) + rest.substring(inlineLevel.end());
            marked = true;
        }
        for (String tail : EFFECT_TAILS) {
            if (rest.endsWith(tail)) {
                rest = rest.substring(0, rest.length() - tail.length());
                marked = true;
                break;
            }
        }
        rest = rest.replace("的", "").trim();
        Matcher trailing = TRAILING_COUNT.matcher(rest);
        if (trailing.matches()) {
            int number = Integer.parseInt(trailing.group(2));
            if (level == null) {
                level = number;
            } else if (seconds == null) {
                seconds = number;
            }
            rest = trailing.group(1).trim();
        }
        if (rest.isEmpty()) {
            return Optional.empty();
        }

        String note = "";
        Optional<String> effect = aliasTable.value(CommandType.EFFECT, rest);
        if (effect.isEmpty() && marked) {
            Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyValue(CommandType.EFFECT, rest);
            if (fuzzy.isPresent()) {
                effect = Optional.of(fuzzy.get().value());
                note = fuzzyNote(fuzzy.get().key());
            }
        }
        // 名字得是个效果，而且不能同时是物品名 —— 是物品说明玩家想说的是「给我 <物品>」
        if (effect.isEmpty() || resolveItemExact(rest).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(assembleEffectWith(effect.get(), level, seconds, note));
    }

    /**
     * 附魔一件新物品：把物品和附魔交给 {@link EnchantSyntax} 拼成 {@code /give} 的物品参数。
     *
     * <p>
     * 具体写法随 Minecraft 版本变化（1.20.5 起是物品组件语法，之前是 NBT 语法），
     * 由平台适配层在构造 {@link CommandParser} 时选定档位，这里不写死版本。
     */
    private ParseResult assembleEnchant(List<String> args, String note, String input) {
        String itemRaw = args.get(0);
        Optional<String> itemId = resolveItemExact(itemRaw);
        if (itemId.isEmpty()) {
            ItemResolution loose = resolveItemLoosely(itemRaw, note);
            itemId = loose.value();
            note = loose.note();
        }
        if (itemId.isEmpty()) {
            List<String> candidates = aliasTable.nearestItems(itemRaw, itemIdResolver.candidates(), 5);
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "不认识物品「" + itemRaw + "」。用法：" + CommandType.ENCHANT.usage(),
                    suggestionsFor(input, itemRaw, candidates));
        }
        // 第 2 个及之后的参数：每一项都是一个附魔（空格或逗号分隔都行）
        List<String> pieces = enchantPieces(args.subList(1, args.size()));
        List<EnchantSyntax.Enchant> enchants = new ArrayList<>();
        for (String piece : pieces) {
            Optional<AliasTable.EnchantPrefix> hit = aliasTable.enchantPrefix(piece);
            if (hit.isEmpty()) {
                // 精确前缀认不出 → 模糊兜底（首字相同 + 距离≤阈值 + 唯一最近才认）
                hit = aliasTable.enchantFuzzy(piece);
                if (hit.isEmpty()) {
                    return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                            "不认识附魔「" + piece + "」。用法：" + CommandType.ENCHANT.usage(),
                            suggestionsFor(input, piece, enchantCandidates(piece)));
                }
                note = join(note, fuzzyNote(hit.get().name()));
            }
            String levelRaw = piece.substring(hit.get().matchedLength());
            Optional<Integer> level = parseLevel(levelRaw);
            if (level.isEmpty()) {
                // 附魔名认出来了，只是尾巴写坏（如「击退r255」）。给出只带附魔名的候选，
                // 点一下填回聊天框，玩家补上等级即可，不用整句重打。
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "附魔等级「" + levelRaw + "」不是正整数。用法：" + CommandType.ENCHANT.usage(),
                        suggestionsFor(input, piece, List.of(hit.get().name())));
            }
            enchants.add(new EnchantSyntax.Enchant(hit.get().id(), level.get()));
        }
        if (enchants.isEmpty()) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "没写附魔。用法：" + CommandType.ENCHANT.usage());
        }
        return ParseResult.ok(CommandType.ENCHANT.commandPrefix() + " "
                + enchantSyntax.assemble(itemId.get(), enchants), note);
    }

    /**
     * 给手上拿的物品附魔：{@code enchant @s <enchant> <level>}。
     *
     * <p>原版这条指令<b>一次只能附一个</b>，所以写多个附魔时会拆成多条指令依次发出去。
     */
    private ParseResult assembleEnchantHeld(List<String> args, String note, String input) {
        List<String> pieces = enchantPieces(args);
        List<String> commands = new ArrayList<>();
        for (String piece : pieces) {
            Optional<AliasTable.EnchantPrefix> hit = aliasTable.enchantPrefix(piece);
            if (hit.isEmpty()) {
                // 精确前缀认不出 → 模糊兜底（首字相同 + 距离≤阈值 + 唯一最近才认）
                hit = aliasTable.enchantFuzzy(piece);
                if (hit.isEmpty()) {
                    return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                            "不认识附魔「" + piece + "」。用法：" + CommandType.ENCHANT_HELD.usage(),
                            suggestionsFor(input, piece, enchantCandidates(piece)));
                }
                note = join(note, fuzzyNote(hit.get().name()));
            }
            String levelRaw = piece.substring(hit.get().matchedLength());
            Optional<Integer> level = parseLevel(levelRaw);
            if (level.isEmpty()) {
                return ParseResult.error(ParseResult.Status.BAD_ARGS,
                        "附魔等级「" + levelRaw + "」不是正整数。用法：" + CommandType.ENCHANT_HELD.usage(),
                        suggestionsFor(input, piece, List.of(hit.get().name())));
            }
            commands.add(CommandType.ENCHANT_HELD.commandPrefix() + " " + hit.get().id() + " " + level.get());
        }
        if (commands.isEmpty()) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "没写附魔。用法：" + CommandType.ENCHANT_HELD.usage());
        }
        if (commands.size() > 1) {
            note = join(note, "原版 /enchant 一次只能附一个，已拆成 " + commands.size() + " 条依次执行");
        }
        return ParseResult.ok(String.join(ParseResult.COMMAND_SEPARATOR, commands), note);
    }

    /**
     * 把附魔参数拆成「一个附魔一项」。
     *
     * <p>拆两层：先按逗号拆（{@code 锋利5,耐久3}），再把光秃秃的数字并回前一项
     * （{@code 击退 255} 是「击退 255 级」，不是「击退」+ 一个叫 255 的附魔）。
     *
     * <p>判断依据是「前一项不以数字结尾」：{@code 锋利5 255} 不会被并，
     * 因为 5 已经吃掉了等级的位置。
     */
    private static List<String> enchantPieces(List<String> args) {
        List<String> merged = new ArrayList<>();
        for (String arg : args) {
            for (String piece : arg.split(",")) {
                if (piece.isEmpty()) {
                    continue;
                }
                boolean bareLevel = piece.chars().allMatch(Character::isDigit)
                        && !merged.isEmpty()
                        && !Character.isDigit(merged.get(merged.size() - 1).charAt(merged.get(merged.size() - 1).length() - 1));
                if (bareLevel) {
                    merged.set(merged.size() - 1, merged.get(merged.size() - 1) + piece);
                } else {
                    merged.add(piece);
                }
            }
        }
        return merged;
    }

    /** 增加经验：{@code xp add @s <amount>}。 */
    private ParseResult assembleXp(List<String> args) {
        String amount = args.get(0);
        if (!COUNT.matcher(amount).matches()) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "经验数量「" + amount + "」不是正整数。用法：" + CommandType.XP.usage());
        }
        return ParseResult.ok(CommandType.XP.commandPrefix() + " " + amount);
    }

    /**
     * 解析附魔等级：空串默认 1 级，其余必须是正整数。
     *
     * <p>
     * 等级<b>原样透传</b>给原版 {@code enchantments} 组件，不做「口语减一」的换算，
     * 这样玩家对着回显出来的指令就能自己核对。
     */
    private static Optional<Integer> parseLevel(String raw) {
        if (raw.isEmpty()) {
            return Optional.of(1);
        }
        if (!COUNT.matcher(raw).matches()) {
            return Optional.empty();
        }
        try {
            int level = Integer.parseInt(raw);
            return level > 0 ? Optional.of(level) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * 附魔名候选。
     *
     * <p>
     * 先把尾巴上的数字剥掉再比 —— 用户打的是 {@code 击退r255}，真正打错的只是「击退r」。
     */
    private List<String> enchantCandidates(String raw) {
        List<String> candidates = aliasTable.nearestEnchants(raw.replaceAll("\\d+$", ""), 5);
        return candidates.isEmpty() ? aliasTable.enchantSuggestions() : candidates;
    }
}
