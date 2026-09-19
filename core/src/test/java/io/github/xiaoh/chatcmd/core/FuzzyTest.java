package io.github.xiaoh.chatcmd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Fuzzy} 的单元测试：只测算法本身，不涉及别名表。
 */
class FuzzyTest {

    @Test
    @DisplayName("编辑距离（Levenshtein Distance）基本用例")
    void distance() {
        assertEquals(0, Fuzzy.distance("钻石剑", "钻石剑"));
        assertEquals(1, Fuzzy.distance("钻右剑", "钻石剑"));
        assertEquals(1, Fuzzy.distance("中午", "正午"));
        assertEquals(3, Fuzzy.distance("kitten", "sitting"));
        assertEquals(3, Fuzzy.distance("", "abc"));
        assertEquals(3, Fuzzy.distance("abc", ""));
    }

    @Test
    @DisplayName("阈值：2 字以内中文不模糊，3~4 字中文与短英文允许差 1")
    void thresholds() {
        assertEquals(0, Fuzzy.autoThreshold("下雪"));
        assertEquals(0, Fuzzy.autoThreshold("模式"));
        assertEquals(1, Fuzzy.autoThreshold("钻石剑"));
        assertEquals(1, Fuzzy.autoThreshold("give"));
        assertEquals(2, Fuzzy.autoThreshold("死亡不掉落"));
    }

    @Test
    @DisplayName("兼容性：跨书写系统或首字符不同就不比较")
    void compatibility() {
        assertFalse(Fuzzy.compatible("跳舞", "tp"));
        assertFalse(Fuzzy.compatible("跳舞", "给"));
        assertTrue(Fuzzy.compatible("模组", "模式"));
        assertTrue(Fuzzy.compatible("giv", "give"));
        assertTrue(Fuzzy.compatible("下雪", "下雨"));
    }

    @Test
    @DisplayName("并列最优解不猜，返回空")
    void tieReturnsEmpty() {
        // 「钻石剑」和「钻石镐」离「钻石x」一样近，此时必须放弃而不是随便挑一个
        Optional<String> hit = Fuzzy.uniqueBest("钻石x", List.of("钻石剑", "钻石镐"), 1);
        assertTrue(hit.isEmpty());
    }

    @Test
    @DisplayName("唯一最优解才返回")
    void uniqueBestReturnsOnlyUnambiguous() {
        assertEquals(Optional.of("钻石剑"), Fuzzy.uniqueBest("钻右剑", List.of("钻石剑", "钻石镐"), 1));
    }

    @Test
    @DisplayName("nearest 用于提示时更宽松，但完全相等时不提示")
    void nearestIsLooserButSkipsExact() {
        assertEquals(Optional.of("模式"), Fuzzy.nearest("模组", List.of("模式"), 1));
        assertTrue(Fuzzy.nearest("模式", List.of("模式"), 1).isEmpty());
    }
}
