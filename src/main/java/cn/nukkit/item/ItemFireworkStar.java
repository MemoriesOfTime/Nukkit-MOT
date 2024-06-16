package cn.nukkit.item;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

/**
 * @author PetteriM1
 */
public class ItemFireworkStar extends Item {

    public ItemFireworkStar() {
        this(0, 1);
    }

    public ItemFireworkStar(Integer meta) {
        this(meta, 1);
    }

    public ItemFireworkStar(Integer meta, int count) {
        super(FIREWORKSCHARGE, meta, count, "Firework Star");

        if (!hasCompoundTag() || !this.getNamedTag().contains("customColor")) {
            setColor(DyeColor.getByDyeData(meta).getColor());
        }
    }

    public void setColor(BlockColor color) {
        setColor(color.getRed(), color.getGreen(), color.getBlue());
    }

    public void setColor(int r, int g, int b) {
        int color = ~(~(r << 16 | g << 8 | b) & 0xffffff);
        CompoundTag tag;
        if (!this.hasCompoundTag()) {
            tag = new CompoundTag();
        } else {
            tag = this.getNamedTag();
        }
        tag.putInt("customColor", color);
        this.setNamedTag(tag);
    }

    public BlockColor getColor() {
        if (!this.hasCompoundTag()) return null;
        CompoundTag tag = this.getNamedTag();
        if (!tag.exist("customColor")) return null;
        int rgb = tag.getInt("customColor");
        return new BlockColor(rgb);
    }

}
