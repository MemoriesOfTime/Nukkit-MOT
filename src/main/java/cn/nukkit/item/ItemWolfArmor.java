package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

public class ItemWolfArmor extends StringItemBase implements ItemDurable {

    public ItemWolfArmor() {
        super(WOLF_ARMOR, "Wolf Armor");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxDurability() {
        return 64;
    }

    @Override
    public int getArmorPoints() {
        return 11;
    }

    public ItemWolfArmor setColor(int dyeColor) {
        BlockColor blockColor = DyeColor.getByDyeData(dyeColor).getColor();
        return setColor(blockColor);
    }

    public ItemWolfArmor setColor(DyeColor dyeColor) {
        return setColor(dyeColor.getColor());
    }

    public ItemWolfArmor setColor(BlockColor color) {
        return setColor(color.getRed(), color.getGreen(), color.getBlue());
    }

    public ItemWolfArmor setColor(int r, int g, int b) {
        int rgb = r << 16 | g << 8 | b;
        CompoundTag tag = this.hasCompoundTag() ? this.getNamedTag() : new CompoundTag();
        tag.putInt("customColor", rgb);
        this.setNamedTag(tag);
        return this;
    }

    public BlockColor getColor() {
        if (!this.hasCompoundTag()) {
            return null;
        }
        CompoundTag tag = this.getNamedTag();
        if (!tag.exist("customColor")) {
            return null;
        }
        return new BlockColor(tag.getInt("customColor"));
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_20_60;
    }
}
