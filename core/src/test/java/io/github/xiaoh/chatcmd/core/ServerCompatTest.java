package io.github.xiaoh.chatcmd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ServerCompat} 的单元测试：只测纯字符串改写，读指令树那步在平台层。
 */
class ServerCompatTest {

    @Test
    @DisplayName("服务器要求子指令时，在 enchant 后面补 add，其余原样")
    void addsSubcommand() {
        assertEquals("enchant add @s minecraft:efficiency 5",
                ServerCompat.adaptEnchant("enchant @s minecraft:efficiency 5", true));
        assertEquals("enchant add @s minecraft:sharpness 5",
                ServerCompat.adaptEnchant("enchant @s minecraft:sharpness 5", true));
    }

    @Test
    @DisplayName("服务器没这个要求时，一个字符都不动")
    void leavesVanillaAlone() {
        assertEquals("enchant @s minecraft:efficiency 5",
                ServerCompat.adaptEnchant("enchant @s minecraft:efficiency 5", false));
    }

    @Test
    @DisplayName("其它指令不受影响")
    void ignoresOtherCommands() {
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:knockback\":2}]",
                ServerCompat.adaptEnchant(
                        "give @s minecraft:stick[enchantments={\"minecraft:knockback\":2}]", true));
        assertEquals("effect give @s minecraft:speed 30 2",
                ServerCompat.adaptEnchant("effect give @s minecraft:speed 30 2", true));
        assertEquals("xp add @s 10", ServerCompat.adaptEnchant("xp add @s 10", true));
    }

    @Test
    @DisplayName("已经是 add 写法时不重复插")
    void doesNotDoubleInsert() {
        assertEquals("enchant add @s minecraft:efficiency 5",
                ServerCompat.adaptEnchant("enchant add @s minecraft:efficiency 5", true));
    }
}