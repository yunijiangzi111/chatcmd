package io.github.xiaoh.chatcmd.core;

import java.util.Optional;

/**
 * 解析结果状态机。
 *
 * <p>注意：{@code NO_PERMISSION} <b>不属于</b>这里 —— 解析与执行解耦，
 * 能否生效完全由服务端按玩家权限判定，客户端无从得知。
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
        UNKNOWN_VALUE
    }

    private final Status status;
    private final String payload;
    private final String hint;

    private ParseResult(Status status, String payload, String hint) {
        this.status = status;
        this.payload = payload;
        this.hint = hint;
    }

    /**
     * 解析成功。
     *
     * @param command 生成的指令，<b>不含前导斜杠</b>，可直接传给 {@code sendChatCommand}
     */
    public static ParseResult ok(String command) {
        return new ParseResult(Status.OK, command, "");
    }

    /**
     * 解析成功，并附带一条给用户看的补充说明（例如「已按模糊匹配识别为 X」）。
     *
     * @param command 生成的指令，<b>不含前导斜杠</b>
     * @param hint    成功时的补充说明；没有就传空串
     */
    public static ParseResult ok(String command, String hint) {
        return new ParseResult(Status.OK, command, hint);
    }

    /** 不归本模组管，原样放行。 */
    public static ParseResult notMyInput() {
        return new ParseResult(Status.NOT_MY_INPUT, "", "");
    }

    /** 逃生通道：{@code text} 是要原样作为聊天发出的文本。 */
    public static ParseResult escapeChat(String text) {
        return new ParseResult(Status.ESCAPE_CHAT, text, "");
    }

    /** 解析失败，{@code hint} 是给用户看的用法提示。 */
    public static ParseResult error(Status status, String hint) {
        return new ParseResult(status, "", hint);
    }

    public Status status() {
        return status;
    }

    /**
     * 结果载荷。
     *
     * <p>{@link Status#OK} 时是生成的指令（不含前导斜杠）；
     * {@link Status#ESCAPE_CHAT} 时是要原样发出的聊天文本；其余状态为空串。
     */
    public String payload() {
        return payload;
    }

    /** 给用户看的提示：失败时是纠错说明，成功时可能是模糊匹配的补充说明；没有则为空串。 */
    public String hint() {
        return hint;
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
        return sb.append('}').toString();
    }
}
