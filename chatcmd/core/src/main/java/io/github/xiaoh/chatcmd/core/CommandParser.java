package io.github.xiaoh.chatcmd.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 中文指令解析引擎，零 Minecraft 依赖。
 *
 * <p>输入语法（计划 §6.1）：
 * <pre>
 * Input = "#" , Verb , [ Separator , Args ]
 * </pre>
 *
 * <p>解析成功后产出的是<b>不含前导斜杠</b>的原版指令串，可直接交给客户端发送。
 *
 * <p>匹配算法（计划 §6.4）分 6 步：归一化 → 触发判定 → 整句短语 → 动词最长前缀匹配
 * → 参数切分 → 逐参解析组装。
 *
 * <p>精确匹配全部失败后才走 {@link Fuzzy} 的编辑距离兜底，并且分两级：
 * <ul>
 *   <li><b>自动纠错</b>（严格阈值）：直接执行，同时在 {@link ParseResult#hint()} 里注明
 *       「已按模糊匹配识别为 X」，绝不静默改词。</li>
 *   <li><b>提示纠错</b>（宽松一档）：不执行，只在报错里附一句「你是不是想用 #X ？」。</li>
 * </ul>
 */
public final class CommandParser {

    /** 触发前缀。 */
    public static final String TRIGGER = "#";

    /** 逃生通道前缀：{@code ##任意文本} 会去掉一个 # 后作为普通聊天发出。 */
    public static final String ESCAPE = "##";

    /** 坐标：支持 {@code ~} / {@code ~5} / {@code ~-3} / {@code 100} / {@code -64.5}。 */
    private static final Pattern COORD = Pattern.compile("^(~|~?-?\\d+(\\.\\d+)?)$");

    /** 正整数数量。 */
    private static final Pattern COUNT = Pattern.compile("^\\d+$");

    private final AliasTable aliasTable;
    private final ItemIdResolver itemIdResolver;

    public CommandParser(AliasTable aliasTable, ItemIdResolver itemIdResolver) {
        this.aliasTable = aliasTable;
        this.itemIdResolver = itemIdResolver;
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

        // 3. 整句短语精确匹配：口语整句 → 完整指令（如「死亡不掉落」）
        Optional<String> phrase = aliasTable.phrase(body);
        if (phrase.isPresent()) {
            return ParseResult.ok(phrase.get());
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
                return ParseResult.error(ParseResult.Status.UNKNOWN_VERB, unknownVerbHint(body));
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
        return assemble(match.type(), args, note);
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
     * <p>对适配层公开：从游戏注册表里取到的本地化物品名必须走同一套归一化，
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

    /** 按指令类型组装。 */
    private ParseResult assemble(CommandType type, List<String> args, String note) {
        return switch (type) {
            case GIVE -> assembleGive(args, note);
            case TP -> assembleTp(args);
            case GAMERULE -> assembleGamerule(args, note);
            case TIME, GAMEMODE, WEATHER, DIFFICULTY -> assembleEnum(type, args, note);
        };
    }

    /** 枚举类：查值别名表，精确失败后模糊兜底。 */
    private ParseResult assembleEnum(CommandType type, List<String> args, String note) {
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
            return ParseResult.ok(type.commandPrefix() + " " + value.get(), note);
        }
        return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE, unknownValueHint(type, raw));
    }

    /**
     * 取值不合法时的提示。
     *
     * <p>如果这个取值其实是某条整句短语（例如 {@code #模式 死亡不掉落} 里的「死亡不掉落」），
     * 就直接告诉用户正确的说法 —— 这类错误是「走错动词」而不是「打错字」。
     */
    private String unknownValueHint(CommandType type, String raw) {
        Optional<String> suggest = aliasTable.phrase(raw).isPresent()
                ? Optional.of(raw)
                : aliasTable.nearestPhrase(raw);
        if (suggest.isPresent()) {
            return "「" + raw + "」不归 " + type.commandPrefix() + " 管。你是不是想用：#"
                    + suggest.get() + " ？";
        }
        return "「" + raw + "」不是有效的取值。用法：" + type.usage();
    }

    /** 给予物品：{@code give @s <item> <count>}，数量缺省为 1。 */
    private ParseResult assembleGive(List<String> args, String note) {
        String itemRaw = args.get(0);
        ItemResolution item = resolveItem(itemRaw, note);
        note = item.note();

        if (item.value().isEmpty()) {
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "不认识物品「" + itemRaw + "」。用法：" + CommandType.GIVE.usage());
        }
        String itemId = item.value().get();

        int count = 1;
        if (args.size() >= 2) {
            String countRaw = args.get(1);
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
        return ParseResult.ok(CommandType.GIVE.commandPrefix() + " @s " + itemId + " " + count, note);
    }

    /** 物品解析结果：解析出的物品 ID + 需要回显给用户的模糊匹配说明。 */
    private record ItemResolution(Optional<String> value, String note) {
    }

    /**
     * 物品名解析四级回退（计划 §6.2）：
     * <ol>
     *   <li>查 {@link AliasTable} 手工表（精确）</li>
     *   <li>问 {@link ItemIdResolver}（适配层查游戏注册表）</li>
     *   <li>手工表模糊兜底（错别字，如「钻右剑」→「钻石剑」）</li>
     *   <li>兜底：含 {@code :} 原样透传；否则补 {@code minecraft:} 前缀 + 空格转下划线</li>
     * </ol>
     */
    private ItemResolution resolveItem(String raw, String note) {
        Optional<String> fromTable = aliasTable.item(raw);
        if (fromTable.isPresent()) {
            return new ItemResolution(fromTable, note);
        }
        Optional<String> fromRegistry = itemIdResolver.resolve(raw);
        if (fromRegistry.isPresent()) {
            return new ItemResolution(fromRegistry, note);
        }
        Optional<AliasTable.Match> fuzzy = aliasTable.fuzzyItem(raw);
        if (fuzzy.isPresent()) {
            return new ItemResolution(Optional.of(fuzzy.get().value()),
                    join(note, fuzzyNote(fuzzy.get().key())));
        }
        // 输入已在 normalize 里转过小写，这里只需补前缀和转下划线
        String id = raw.replace(' ', '_');
        return new ItemResolution(Optional.of(id.contains(":") ? id : "minecraft:" + id), note);
    }

    /** 游戏规则：{@code gamerule <rule> <true|false>}，规则名与开关都支持中文别名。 */
    private ParseResult assembleGamerule(List<String> args, String note) {
        String rule = aliasTable.gamerule(args.get(0)).orElse(args.get(0));

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
                    "「" + boolRaw + "」不是有效的开关。用法：" + CommandType.GAMERULE.usage());
        }
        return ParseResult.ok(CommandType.GAMERULE.commandPrefix() + " " + rule + " " + boolValue.get(), note);
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
}
