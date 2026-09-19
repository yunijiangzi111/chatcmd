package io.github.xiaoh.chatcmd.core;

import java.util.List;
import java.util.Optional;

/**
 * 解析结果状态机。
 *
 * <p>
 * 注意：{@code NO_PERMISSION} <b>不属于</b>这里 —— 解析与执行解耦，
 * 能否生效完全由服务端按玩家权限判定，客户端无从得知。
 *
 * <p>
 * v0.4 起一次解析可以产出<b>多条指令</b>（例如「永为白昼」= 调到白天 + 关掉昼夜交替），
 * 也可以携带<b>可点击的候选</b>（解析失败时让用户点一下就把正确写法填回聊天框）。
 */
public final class ParseResult {

    public enum Status {
        /** 成功，携带可发送的指令。 */
        OK,
        /** 不归本模组管，原样放行。 */
        NOT_MY_INPUT,
        /** 逃生通道：去掉一个 {@code #} 后作为普通聊天发出。 */
        ESCAPE_CHAT,
        /** 没识别出动词。 */
        UNKNOWN_VERB,
        /** 动词歧义（当前「最长前缀匹配」的别名表结构下不会产生，保留以稳定契约）。 */
        AMBIGUOUS_VERB,
        /** 参数个数或格式错。 */
        BAD_ARGS,
        /** 枚举值或物品名不在表里。 */
        UNKNOWN_VALUE,
        /**
         * 高危指令：解析成功，但要先由玩家二次确认才执行。
         *
         * <p>载荷与 {@link #OK} 完全一样（{@link #commands()} 就是待执行的指令），
         * 区别只在<b>要不要先问一句</b>。判定与是否放行由适配层负责 ——
         * 适配层把 {@link #commands()} 暂存下来，等玩家再发一条 {@code #确认} 才发出去。
         */
        CONFIRM,
        /** 玩家发来了确认词（{@code #确认} 之类）。是否真的执行由适配层按暂存内容决定。 */
        CONFIRM_ACCEPT,
        /** 玩家发来了取消词（{@code #取消} 之类）。 */
        CONFIRM_DECLINE
    }

    /** 多指令分隔符：一句中文口语可以对应多条原版指令。 */
    static final String COMMAND_SEPARATOR = "\n";

    private final Status status;
    private final String payload;
    private final String hint;
    private final List<String> commands;
    private final List<String> suggestions;

    private ParseResult(Status status, String payload, String hint,
            List<String> commands, List<String> suggestions) {
        this.status = status;
        this.payload = payload;
        this.hint = hint;
        this.commands = commands;
        this.suggestions = suggestions;
    }

    /**
     * 解析成功。
     *
     * @param command 生成的指令，<b>不含前导斜杠</b>，可直接传给 {@code sendChatCommand}
     */
    public static ParseResult ok(String command) {
        return ok(command, "");
    }

    /**
     * 解析成功，并附带一条给用户看的补充说明（例如「已按模糊匹配识别为 X」）。
     *
     * @param command 生成的指令，<b>不含前导斜杠</b>；要一次发多条时用 {@code \n} 分隔
     * @param hint    成功时的补充说明；没有就传空串
     */
    public static ParseResult ok(String command, String hint) {
        return new ParseResult(Status.OK, command, hint,
                List.of(command.split(COMMAND_SEPARATOR)), List.of());
    }

    /** 不归本模组管，原样放行。 */
    public static ParseResult notMyInput() {
        return new ParseResult(Status.NOT_MY_INPUT, "", "", List.of(), List.of());
    }

    /**
     * 高危指令：解析成功，但先不执行，等玩家确认。
     *
     * @param command     待执行的指令（<b>不含前导斜杠</b>，多条用 {@code \n} 分隔）
     * @param hint        给玩家看的警告：会执行什么、会造成什么后果
     * @param suggestions 可点击的回填候选，通常是 {@code #确认} / {@code #取消}
     */
    public static ParseResult confirm(String command, String hint, List<String> suggestions) {
        return new ParseResult(Status.CONFIRM, command, hint,
                List.of(command.split(COMMAND_SEPARATOR)), List.copyOf(suggestions));
    }

    /** 玩家发来了确认词。 */
    public static ParseResult confirmAccept() {
        return new ParseResult(Status.CONFIRM_ACCEPT, "", "", List.of(), List.of());
    }

    /** 玩家发来了取消词。 */
    public static ParseResult confirmDecline() {
        return new ParseResult(Status.CONFIRM_DECLINE, "", "", List.of(), List.of());
    }

    /** 逃生通道：{@code text} 是要原样作为聊天发出的文本。 */
    public static ParseResult escapeChat(String text) {
        return new ParseResult(Status.ESCAPE_CHAT, text, "", List.of(), List.of());
    }

    /** 解析失败，{@code hint} 是给用户看的用法提示。 */
    public static ParseResult error(Status status, String hint) {
        return error(status, hint, List.of());
    }

    /**
     * 解析失败，并附上若干<b>可回填</b>的候选。
     *
     * @param suggestions 每项都是完整的一行输入（含前导 {@code #}），点一下即填回聊天框
     */
    public static ParseResult error(Status status, String hint, List<String> suggestions) {
        return new ParseResult(status, "", hint, List.of(), List.copyOf(suggestions));
    }

    public Status status() {
        return status;
    }

    /**
     * 结果载荷。
     *
     * <p>
     * {@link Status#OK} 和 {@link Status#CONFIRM} 时是生成的指令（不含前导斜杠，多条用 {@code \n} 分隔）；
     * {@link Status#ESCAPE_CHAT} 时是要原样发出的聊天文本；其余状态为空串。
     */
    public String payload() {
        return payload;
    }

    /** 给用户看的提示：失败时是纠错说明，成功时可能是模糊匹配的补充说明；没有则为空串。 */
    public String hint() {
        return hint;
    }

    /**
     * 拆好的指令列表，按顺序发送。
     *
     * <p>
     * {@link Status#OK} 与 {@link Status#CONFIRM} 时非空（后者是「待执行」，要先经玩家确认）；
     * 单条指令时长度为 1，行为与 v0.3 完全一致。
     */
    public List<String> commands() {
        return commands;
    }

    /** 解析失败时给出的候选；每项都是可直接填回聊天框的完整输入（含前导 {@code #}）。 */
    public List<String> suggestions() {
        return suggestions;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    /** 仅当解析成功时返回指令。 */
    public Optional<String> command() {
        return status == Status.OK ? Optional.of(payload) : Optional.empty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ParseResult{").append(status);
        if (!payload.isEmpty()) {
            sb.append(", '").append(payload).append('\'');
        }
        if (!hint.isEmpty()) {
            sb.append(", hint='").append(hint).append('\'');
        }
        if (!suggestions.isEmpty()) {
            sb.append(", suggestions=").append(suggestions);
        }
        return sb.append('}').toString();
    }
}
