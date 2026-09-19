package io.github.xiaoh.chatcmd.core;

import java.util.Collection;
import java.util.List;
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

    /**
     * 当前已知的全部候选别名，供 core 在认不出物品时列出「你是不是想用」的候选。
     *
     * <p>只用于<b>提示</b>，绝不参与自动纠错 —— 模组物品名彼此高度相似
     * （齿轮 / 大齿轮 / 小齿轮），自动猜错等于静默给错东西，所以这里必须精准匹配。
     *
     * <p>默认什么都不提供；适配层覆写后可返回全量本地化物品名。
     */
    default Collection<String> candidates() {
        return List.of();
    }

    /** 什么都不知道的空实现，用于单元测试和降级。 */
    ItemIdResolver EMPTY = alias -> Optional.empty();
}
