package io.github.xiaoh.chatcmd.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 编辑距离（Levenshtein Distance，莱文斯坦距离）模糊匹配工具。
 *
 * <p>纯算法、零依赖，供 {@link AliasTable} 在<b>精确匹配失败之后</b>做兜底纠错。
 *
 * <p>这里刻意加了两道安全阀，因为中文短词区分度极低：
 * <ol>
 *   <li><b>书写系统 + 首字符必须一致</b>：中文别名只和中文输入比、英文别名只和英文输入比，
 *       并且首字符要相同。否则「跳舞」会被纠成「tp」、「飞行」会被纠成别的动词。</li>
 *   <li><b>中文阈值更严</b>：「下雨」和「下雪」只差一个字，语义却完全不同。
 *       所以 2 字以内的中文要求完全相等（阈值 0），3~4 字才允许差 1 个字。</li>
 * </ol>
 *
 * <p>阈值表：
 * <table border="1">
 *   <caption>自动纠错阈值</caption>
 *   <tr><th>词长</th><th>中文</th><th>英文/数字</th></tr>
 *   <tr><td>≤ 2</td><td>0（不模糊）</td><td>1</td></tr>
 *   <tr><td>3 ~ 4</td><td>1</td><td>1</td></tr>
 *   <tr><td>≥ 5</td><td>2</td><td>2</td></tr>
 * </table>
 */
final class Fuzzy {

    private Fuzzy() {
    }

    /** 是否含中日韩汉字（CJK，Chinese/Japanese/Korean）字符。 */
    static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') {
                return true;
            }
        }
        return false;
    }

    /**
     * 两个词是否「同一书写系统 + 首字符相同」。
     *
     * <p>不满足就直接不比较，从根上掐掉跨语种、跨字的离谱纠错。
     */
    static boolean compatible(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (hasCjk(a) != hasCjk(b)) {
            return false;
        }
        return a.codePointAt(0) == b.codePointAt(0);
    }

    /** 自动纠错允许的编辑距离上限；超过就宁可报错也不猜。 */
    static int autoThreshold(String word) {
        int len = word.codePointCount(0, word.length());
        if (hasCjk(word)) {
            if (len <= 2) {
                return 0;
            }
            return len <= 4 ? 1 : 2;
        }
        return len <= 4 ? 1 : 2;
    }

    /** 标准 Levenshtein 距离，按码点（code point）逐位比较。 */
    static int distance(String a, String b) {
        int[] ca = a.codePoints().toArray();
        int[] cb = b.codePoints().toArray();
        if (ca.length == 0) {
            return cb.length;
        }
        if (cb.length == 0) {
            return ca.length;
        }

        int[] prev = new int[cb.length + 1];
        int[] cur = new int[cb.length + 1];
        for (int j = 0; j <= cb.length; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= ca.length; i++) {
            cur[0] = i;
            for (int j = 1; j <= cb.length; j++) {
                int cost = ca[i - 1] == cb[j - 1] ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[cb.length];
    }

    /**
     * 在候选里找<b>唯一</b>的、距离不超过 {@code threshold} 的最优解。
     *
     * <p>出现并列（两个候选一样近）就返回空 —— 宁可让用户重打，也不瞎猜。
     *
     * @param input      已归一化的用户输入
     * @param candidates 候选词集合
     * @param threshold  允许的最大编辑距离
     */
    static Optional<String> uniqueBest(String input, Collection<String> candidates, int threshold) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (String candidate : candidates) {
            if (!compatible(input, candidate)) {
                continue;
            }
            int d = distance(input, candidate);
            if (d > threshold) {
                continue;
            }
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
                tie = false;
            } else if (d == bestDistance) {
                tie = true;
            }
        }
        return best == null || tie ? Optional.empty() : Optional.of(best);
    }

    /**
     * 找最接近的候选，只用于拼「你是不是想用 X？」的提示，<b>不会自动执行</b>。
     *
     * <p>比 {@link #uniqueBest} 宽松一档（{@code limit} 由调用方给），因为提示错了用户一眼能看出来，
     * 而自动执行错了就是静默改词。
     */
    static Optional<String> nearest(String input, Collection<String> candidates, int limit) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (String candidate : candidates) {
            if (!compatible(input, candidate)) {
                continue;
            }
            int d = distance(input, candidate);
            if (d > limit) {
                continue;
            }
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
                tie = false;
            } else if (d == bestDistance) {
                tie = true;
            }
        }
        return best == null || tie || bestDistance == 0 ? Optional.empty() : Optional.of(best);
    }

    /**
     * 按编辑距离从近到远列出候选，供「多项近义给选择」用。
     *
     * <p>与 {@link #uniqueBest} 的关键差别：这里<b>允许并列</b>。并列时自动纠错必须放弃
     * （猜错就是静默改词），但候选是给用户点的，全列出来反而更好选。
     *
     * <p>与 {@link #nearest} 一样跳过距离 0 的项 —— 完全相等说明本来就认出来了，
     * 不该出现在「你是不是想用」里。
     *
     * @param input      已归一化的用户输入
     * @param candidates 候选词集合
     * @param limit      允许的最大编辑距离
     * @param maxResults 最多返回几个
     */
    static List<String> nearestList(String input, Collection<String> candidates, int limit, int maxResults) {
        if (input.isEmpty() || maxResults <= 0) {
            return List.of();
        }
        List<String> hits = new ArrayList<>();
        for (String candidate : candidates) {
            if (!compatible(input, candidate)) {
                continue;
            }
            int d = distance(input, candidate);
            if (d == 0 || d > limit) {
                continue;
            }
            hits.add(candidate);
        }
        hits.sort(Comparator.comparingInt(candidate -> distance(input, candidate)));
        return hits.size() <= maxResults ? List.copyOf(hits) : List.copyOf(hits.subList(0, maxResults));
    }
}
