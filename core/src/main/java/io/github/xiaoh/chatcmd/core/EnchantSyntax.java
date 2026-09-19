package io.github.xiaoh.chatcmd.core;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 附魔指令的语法档位。
 *
 * <p>
 * 「#附魔 木棍 击退255」这一句话，在不同 Minecraft 版本上要拼成不同的写法：
 *
 * <ul>
 * <li>1.20.5 起改用<b>物品组件（item components）</b>语法：
 * {@code give @s minecraft:stick[enchantments={"minecraft:knockback":255}]}</li>
 * <li>1.20.4 及以前用 <b>NBT</b> 语法：
 * {@code give @s minecraft:stick{Enchantments:[{id:"minecraft:knockback",lvl:255s}]}}</li>
 * </ul>
 *
 * <p>
 * core 本身不认识 Minecraft，所以这里只提供两种「怎么拼串」的档位，
 * 由平台适配层按目标版本挑一个，从 {@link CommandParser} 的构造参数传进来。
 */
public enum EnchantSyntax {

    /** 物品组件语法（Minecraft 1.20.5+，:neoforge 走这条）。 */
    ITEM_COMPONENT {
        @Override
        public String assemble(String itemId, List<Enchant> enchants) {
            return itemId + "[enchantments={" + enchants.stream()
                    .map(enchant -> "\"" + enchant.id() + "\":" + enchant.level())
                    .collect(Collectors.joining(",")) + "}]";
        }
    },

    /** NBT 语法（Minecraft 1.20.4 及以前，:forge 走这条）。 */
    NBT {
        @Override
        public String assemble(String itemId, List<Enchant> enchants) {
            return itemId + "{Enchantments:[" + enchants.stream()
                    .map(enchant -> "{id:\"" + enchant.id() + "\",lvl:" + enchant.level() + "s}")
                    .collect(Collectors.joining(",")) + "]}";
        }
    };

    /**
     * 按本档位把物品 ID 和附魔列表拼成 {@code /give} 的物品参数。
     *
     * @param itemId   物品注册表 ID，如 {@code minecraft:diamond_sword}
     * @param enchants 附魔列表，至少一项
     * @return 供 {@code give @s <这里>} 使用的物品参数
     */
    public abstract String assemble(String itemId, List<Enchant> enchants);

    /** 一条附魔：注册表 ID + 等级。 */
    public record Enchant(String id, int level) {
    }
}