package cn.nukkit.item.customitem;

import cn.nukkit.GameVersion;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.StringItem;
import cn.nukkit.nbt.tag.CompoundTag;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * @author lt_name
 */
public abstract class ItemCustomArmor extends ItemArmor implements CustomItem {
    private final String id;
    private final String textureName;

    public ItemCustomArmor(@NotNull String id, @Nullable String name) {
        super(ItemID.STRING_IDENTIFIED_ITEM, 0, 1, StringItem.notEmpty(name));
        this.id = id;
        this.textureName = name;
    }

    public ItemCustomArmor(@NotNull String id, @Nullable String name, @NotNull String textureName) {
        super(ItemID.STRING_IDENTIFIED_ITEM, 0, 1, StringItem.notEmpty(name));
        this.id = id;
        this.textureName = textureName;
    }

    @Override
    public String getTextureName() {
        return textureName;
    }

    @Override
    public String getNamespaceId() {
        return id;
    }

    @Override
    public String getNamespaceId(GameVersion protocolId) {
        return this.getNamespaceId();
    }

    @Override
    public final int getId() {
        return CustomItem.super.getId();
    }

    /**
     * 读取 {@code minecraft:wearable.slot} 判定装备槽位。
     * <p>
     * Reads {@code minecraft:wearable.slot} to determine the equipment slot.
     */
    private boolean wearableSlotEquals(@NotNull String expected) {
        String slot = this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("minecraft:wearable")
                .getString("slot");
        return expected.equals(slot);
    }

    @Override
    public boolean isHelmet() {
        return wearableSlotEquals("slot.armor.head");
    }

    @Override
    public boolean isChestplate() {
        return wearableSlotEquals("slot.armor.chest");
    }

    @Override
    public boolean isLeggings() {
        return wearableSlotEquals("slot.armor.legs");
    }

    @Override
    public boolean isBoots() {
        return wearableSlotEquals("slot.armor.feet");
    }

    @Override
    public int getArmorPoints() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("minecraft:wearable")
                .getInt("protection");
    }

    @Override
    public int getToughness() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("minecraft:wearable")
                .getInt("toughness");
    }

    @Override
    public int getTier() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("item_properties")
                .getInt("tier");
    }

    @Override
    public int getMaxDurability() {
        return this.getDefinitionNbt()
                .getCompound("components")
                .getCompound("minecraft:durability")
                .getInt("max_durability");
    }

    @Override
    public ItemCustomArmor clone() {
        return (ItemCustomArmor) super.clone();
    }
}
