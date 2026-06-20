package cn.nukkit.item.customitem;

import cn.nukkit.item.Item;
import cn.nukkit.item.StringItem;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * 继承这个类实现自定义物品,重写{@link Item}中的方法控制方块属性
 * <p>
 * Inherit this class to implement a custom item, override the methods in {@link Item} to control the feature of the item.
 *
 * @author lt_name
 */
public interface CustomItem extends StringItem {
    /**
     * 定义该自定义物品的材质
     * <p>
     * Define the texture of this custom item
     *
     * @return the texture name
     */
    String getTextureName();

    /**
     * 该方法设置自定义物品的定义
     * <p>
     * This method sets the definition of custom item
     */
    CustomItemDefinition getDefinition();

    /**
     * 从注册表读取当前物品的定义。注册表保存的是注册时的快照，
     * 比 {@link #getDefinition()}（每次调用都重建）更可靠、更高效。
     * 若物品未注册（如测试或注册前访问），回退到 {@link #getDefinition()}。
     * <p>
     * Reads this item's definition from the registry. The registry holds the
     * snapshot taken at registration time, which is more reliable and efficient
     * than {@link #getDefinition()} (rebuilt on every call). Falls back to
     * {@link #getDefinition()} when the item is not registered (e.g. in tests).
     *
     * @return 物品定义
     */
    default CustomItemDefinition resolveDefinition() {
        var definition = Item.getCustomItemDefinition(this.getNamespaceId());
        return definition != null ? definition : this.getDefinition();
    }

    /**
     * 从注册表读取当前物品定义的 NBT。等价于
     * {@code resolveDefinition().getNbt()}。
     * <p>
     * Reads this item definition's NBT from the registry. Equivalent to
     * {@code resolveDefinition().getNbt()}.
     *
     * @return 定义 NBT
     */
    default CompoundTag getDefinitionNbt() {
        return this.resolveDefinition().getNbt();
    }

    /**
     * 判断自定义物品是否允许放入副手槽。Component-based 模式遵循 {@code allow_off_hand}；
     * legacy 模式交由客户端 behavior pack 裁决（返回 {@code true}）。
     * <p>
     * 因 Java "类优先"规则，无法作为接口 default 覆盖 {@link cn.nukkit.item.Item#canBePutInOffhandSlot()}，
     * 各 {@code ItemCustom*} 子类需自行 {@code @Override} 并委托本方法。
     * <p>
     * Whether a custom item may enter the off-hand slot. Component-based honors {@code allow_off_hand};
     * legacy defers to the client behavior pack (returns {@code true}). Cannot be a {@code default}
     * override of {@code Item.canBePutInOffhandSlot()} due to Java's "class wins" rule — each
     * {@code ItemCustom*} subclass must {@code @Override} and delegate here.
     */
    static boolean isAllowedInOffHand(CustomItem item) {
        var def = item.resolveDefinition();
        if (!def.isComponentBased()) {
            return true;
        }
        return def.getNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getBoolean("allow_off_hand");
    }
}
