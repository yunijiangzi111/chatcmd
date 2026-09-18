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
 * <p>匹配算法（计划 §6.4）分 5 步：归一化 → 触发判定 → 动词最长前缀匹配 → 参数切分 → 逐参解析组装。
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

        // 3. 动词匹配（最长前缀优先）
        Optional<AliasTable.VerbMatch> matchOpt = aliasTable.findLongestVerbPrefix(body);
        if (matchOpt.isEmpty()) {
            return ParseResult.error(ParseResult.Status.UNKNOWN_VERB,
                    "没认出这条指令。可用的动词：" + aliasTable.verbHint());
        }
        AliasTable.VerbMatch match = matchOpt.get();
        String rest = body.substring(match.alias().length()).trim();

        // 4. 参数切分
        List<String> args = splitArgs(rest, match.type());
        if (args.size() < match.type().minArgs() || args.size() > match.type().maxArgs()) {
            return ParseResult.error(ParseResult.Status.BAD_ARGS,
                    "参数个数不对。用法：" + match.type().usage());
        }

        // 5. 逐参解析 + 组装
        return assemble(match.type(), args);
    }

    /**
     * 归一化：全角转半角（含全角空格、全角数字）→ 去首尾空白 → 英文转小写。
     * 中文不受影响。
     */
    static String normalize(String raw) {
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
    private ParseResult assemble(CommandType type, List<String> args) {
        return switch (type) {
            case GIVE -> assembleGive(args);
            case TP -> assembleTp(args);
            case TIME, GAMEMODE, WEATHER, DIFFICULTY -> assembleEnum(type, args);
        };
    }

    /** 枚举类：查值别名表。 */
    private ParseResult assembleEnum(CommandType type, List<String> args) {
        String raw = args.get(0);
        Optional<String> value = aliasTable.value(type, raw);
        if (value.isEmpty()) {
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "「" + raw + "」不是有效的取值。用法：" + type.usage());
        }
        return ParseResult.ok(type.commandPrefix() + " " + value.get());
    }

    /** 给予物品：{@code give @s <item> <count>}，数量缺省为 1。 */
    private ParseResult assembleGive(List<String> args) {
        String itemRaw = args.get(0);
        Optional<String> itemId = resolveItem(itemRaw);
        if (itemId.isEmpty()) {
            return ParseResult.error(ParseResult.Status.UNKNOWN_VALUE,
                    "不认识物品「" + itemRaw + "」。用法：" + CommandType.GIVE.usage());
        }

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
        return ParseResult.ok(CommandType.GIVE.commandPrefix() + " @s " + itemId.get() + " " + count);
    }

    /**
     * 物品名解析三级回退（计划 §6.2）：
     * <ol>
     *   <li>查 {@link AliasTable} 手工表</li>
     *   <li>问 {@link ItemIdResolver}（适配层查游戏注册表）</li>
     *   <li>兜底：含 {@code :} 原样透传；否则补 {@code minecraft:} 前缀 + 空格转下划线</li>
     * </ol>
     */
    private Optional<String> resolveItem(String raw) {
        Optional<String> fromTable = aliasTable.item(raw);
        if (fromTable.isPresent()) {
            return fromTable;
        }
        Optional<String> fromRegistry = itemIdResolver.resolve(raw);
        if (fromRegistry.isPresent()) {
            return fromRegistry;
        }
        // 输入已在 normalize 里转过小写，这里只需补前缀和转下划线
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
}
