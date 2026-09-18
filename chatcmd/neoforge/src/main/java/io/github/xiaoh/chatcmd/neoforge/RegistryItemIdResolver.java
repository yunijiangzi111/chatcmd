package io.github.xiaoh.chatcmd.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.xiaoh.chatcmd.core.CommandParser;
import io.github.xiaoh.chatcmd.core.ItemIdResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 用游戏物品注册表实现 core 侧的 {@link ItemIdResolver} 接口。
 *
 * <p>两级查询：
 * <ol>
 *   <li><b>本地化名称表</b>：第一次用到时遍历整个物品注册表，把「物品的中文显示名 → 物品 ID」
 *       全量建出来。这样整合包里那几百个模组的物品都能认，不必手写进 core 的别名表。</li>
 *   <li><b>原版 ID</b>：输入本来就写成 {@code diamond_sword} / {@code create:cogwheel} 时直接查注册表。</li>
 * </ol>
 *
 * <p>这样 core 保持零 Minecraft 依赖，同时又能校验物品是否真实存在。
 */
public final class RegistryItemIdResolver implements ItemIdResolver {

    /** 本地化名称表：已归一化的显示名 → 物品 ID。第一次用到时才建，之后一直复用。 */
    private volatile Map<String, String> localizedNames;

    @Override
    public Optional<String> resolve(String alias) {
        Optional<String> byName = Optional.ofNullable(localizedNames().get(alias));
        if (byName.isPresent()) {
            return byName;
        }
        // 中文别名走 core 的手工表；这里只按原版 ID 查注册表
        String id = alias.contains(":") ? alias : "minecraft:" + alias;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.containsKey(key) ? Optional.of(key.toString()) : Optional.empty();
    }

    /**
     * 惰性建表。
     *
     * <p>刻意不在模组构造阶段建：那时 {@code Minecraft} 还没构造完，语言文件（lang file）也没加载，
     * {@code Component#getString()} 取到的会是 {@code item.minecraft.xxx} 这种 key 而不是中文。
     * 玩家能打字时必然已经进游戏了，所以「第一次用到」是最省心也最安全的时机。
     */
    private Map<String, String> localizedNames() {
        Map<String, String> table = localizedNames;
        if (table == null) {
            table = buildLocalizedNames();
            localizedNames = table;
        }
        return table;
    }

    /** 遍历物品注册表，把每个物品的当前语言显示名映射到它的 ID。 */
    private static Map<String, String> buildLocalizedNames() {
        Map<String, String> table = new HashMap<>(8192);
        for (Item item : BuiltInRegistries.ITEM) {
            String descriptionId = item.getDescriptionId();
            String name = Component.translatable(descriptionId).getString();
            if (name.isEmpty() || name.equals(descriptionId)) {
                // 取到的还是 key 本身，说明这个物品没有当前语言的翻译，跳过
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            // 同名物品保留注册表里靠前的那个（原版先注册，因此优先命中原版）
            table.putIfAbsent(CommandParser.normalize(name), id.toString());
        }
        return table;
    }
}
