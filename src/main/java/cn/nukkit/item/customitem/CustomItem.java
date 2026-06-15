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
     * 从注册表读取当前物品定义的 NBT。注册表保存的是注册时的快照，
     * 比 {@link #getDefinition()}（每次调用都重建）更可靠、更高效。
     * 若物品未注册（如测试或注册前访问），回退到 {@link #getDefinition()}。
     * <p>
     * Reads this item definition's NBT from the registry. The registry holds the
     * snapshot taken at registration time, which is more reliable and efficient
     * than {@link #getDefinition()} (rebuilt on every call). Falls back to
     * {@link #getDefinition()} when the item is not registered (e.g. in tests).
     *
     * @return 定义 NBT
     */
    default CompoundTag getDefinitionNbt() {
        var definitions = Item.getCustomItemDefinition();
        var definition = definitions.get(this.getNamespaceId());
        if (definition == null) {
            // 未注册时回退到实时定义，保证测试和边界场景可用
            definition = this.getDefinition();
        }
        return definition.getNbt();
    }
}
