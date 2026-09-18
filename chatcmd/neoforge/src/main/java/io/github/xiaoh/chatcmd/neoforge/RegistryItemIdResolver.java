package io.github.xiaoh.chatcmd.neoforge;

import java.util.Optional;

import io.github.xiaoh.chatcmd.core.ItemIdResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * 用游戏物品注册表实现 core 侧的 {@link ItemIdResolver} 接口。
 *
 * <p>这样 core 保持零 Minecraft 依赖，同时又能校验物品是否真实存在，
 * 并顺带支持模组新增的物品。
 */
public final class RegistryItemIdResolver implements ItemIdResolver {

    @Override
    public Optional<String> resolve(String alias) {
        // 中文别名走 core 的手工表；这里只按原版 ID 查注册表
        String id = alias.contains(":") ? alias : "minecraft:" + alias;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.containsKey(key) ? Optional.of(key.toString()) : Optional.empty();
    }
}
