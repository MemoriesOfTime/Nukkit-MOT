package cn.nukkit.item.customitem;

import cn.nukkit.item.*;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lt_name
 */
public abstract class ItemCustomTool extends StringItemToolBase implements ItemDurable, CustomItem {

    private final String textureName;

    public ItemCustomTool(@NotNull String id, @Nullable String name) {
        super(id, StringItem.notEmpty(name));
        this.textureName = name;
    }

    public ItemCustomTool(@NotNull String id, @Nullable String name, @NotNull String textureName) {
        super(id, StringItem.notEmpty(name));
        this.textureName = textureName;
    }

    @Override
    public String getTextureName() {
        return textureName;
    }

    /**
     * 判断物品是否含有指定的 item_tag（写入 {@code components.item_tags} 中的标签）。
     * <p>
     * Checks whether the item has the given item_tag (written into {@code components.item_tags}).
     */
    private boolean hasItemTag(@NotNull String expected) {
        CompoundTag components = this.getDefinitionNbt().getCompound("components");
        if (!components.contains("item_tags")) {
            return false;
        }
        ListTag<? extends Tag> list = components.getList("item_tags");
        for (Tag tag : list.getAll()) {
            if (tag instanceof StringTag stringTag && expected.equals(stringTag.parseValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMaxDurability() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("minecraft:durability")
                .getInt("max_durability");
    }

    @Override
    public int getTier() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getInt("tier");
    }

    @Override
    public int getAttackDamage() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getInt("damage");
    }

    @Override
    public boolean isPickaxe() {
        return hasItemTag("minecraft:is_pickaxe");
    }

    @Override
    public boolean isAxe() {
        return hasItemTag("minecraft:is_axe");
    }

    @Override
    public boolean isShovel() {
        return hasItemTag("minecraft:is_shovel");
    }

    @Override
    public boolean isHoe() {
        return hasItemTag("minecraft:is_hoe");
    }

    @Override
    public boolean isShears() {
        return hasItemTag("minecraft:is_shears");
    }

    @Override
    public boolean isSword() {
        //剑无 item_tag，通过 enchantable_slot 判定
        //Swords have no item tag, so determine via enchantable_slot
        String slot = this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getString("enchantable_slot");
        return "sword".equals(slot);
    }

    @Nullable
    public final Integer getSpeed() {
        var nbt = this.getDefinitionNbt();
        if (!nbt.getCompound("components").contains("minecraft:digger")) return null;
        return nbt.getCompound("components")
                .getCompound("minecraft:digger")
                .getList("destroy_speeds", CompoundTag.class).get(0).getInt("speed");
    }

    @Override
    public ItemCustomTool clone() {
        return (ItemCustomTool) super.clone();
    }
}
