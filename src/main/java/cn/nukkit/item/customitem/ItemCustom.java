package cn.nukkit.item.customitem;

import cn.nukkit.item.Item;
import cn.nukkit.item.StringItem;
import cn.nukkit.item.StringItemBase;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * 继承这个类实现自定义物品,重写{@link Item}中的方法控制方块属性
 * <p>
 * Inherit this class to implement a custom item, override the methods in {@link Item} to control the feature of the item.
 *
 * @author lt_name
 */
public abstract class ItemCustom extends StringItemBase implements CustomItem {
    private final String textureName;

    public ItemCustom(@NotNull String id, @Nullable String name) {
        super(id, StringItem.notEmpty(name));
        this.textureName = name;
    }

    public ItemCustom(@NotNull String id, @Nullable String name, @NotNull String textureName) {
        super(id, StringItem.notEmpty(name));
        this.textureName = textureName;
    }

    @Override
    public String getTextureName() {
        return textureName;
    }

    /**
     * 该方法设置自定义物品的定义
     * <p>
     * This method sets the definition of custom item
     */
    @Override
    public abstract CustomItemDefinition getDefinition();

    /**
     * Component-based 模式下，仅当 {@link CustomItemDefinition.SimpleBuilder#allowOffHand(boolean)}
     * 设置为 {@code true} 时允许放入副手槽。
     * <p>
     * Legacy 模式的物品定义由客户端 behavior pack 提供，服务端不持有 {@code allow_off_hand}
     * 信息，因此信任客户端裁决（返回 {@code true}），避免服务端误拒 behavior pack 已放行的物品。
     * <p>
     * For component-based items, the off-hand slot is allowed only when
     * {@link CustomItemDefinition.SimpleBuilder#allowOffHand(boolean)} is {@code true}.
     * Legacy-mode items are defined by the client behavior pack, so the server has no
     * {@code allow_off_hand} information and defers to the client (returns {@code true}).
     */
    @Override
    public boolean canBePutInOffhandSlot() {
        var def = this.resolveDefinition();
        if (!def.isComponentBased()) {
            // Legacy 模式：物品定义在 behavior pack，服务端无 allow_off_hand 信息，信任客户端
            return true;
        }
        return def.getNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getBoolean("allow_off_hand");
    }

    @Override
    public ItemCustom clone() {
        return (ItemCustom) super.clone();
    }
}
