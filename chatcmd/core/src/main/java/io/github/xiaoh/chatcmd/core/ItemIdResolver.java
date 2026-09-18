package io.github.xiaoh.chatcmd.core;

import java.util.Optional;

/**
 * 别名 → 原版物品 ID 的解析器接口。
 *
 * <p>core 模块要求零 Minecraft 依赖，所以不能直接查游戏注册表。
 * 这里用依赖倒置（Dependency Inversion）把「查注册表」这件事交给适配层实现：
 * 适配层用 {@code BuiltInRegistries.ITEM} 实现它，即可支持全量物品、模组新增物品和存在性校验。
 *
 * <p>v0.1 可以先用 {@link #EMPTY} 降级，后续替换实现时 core 一行不用改。
 */
public interface ItemIdResolver {

    /**
     * 把别名解析成原版物品 ID（形如 {@code minecraft:diamond_sword}）。
     *
     * @param alias 已归一化的别名，例如 {@code diamond_sword} 或 {@code minecraft:stone}
     * @return 解析成功返回物品 ID；无法确定时返回 {@link Optional#empty()}
     */
    Optional<String> resolve(String alias);

    /** 什么都不知道的空实现，用于单元测试和降级。 */
    ItemIdResolver EMPTY = alias -> Optional.empty();
}
