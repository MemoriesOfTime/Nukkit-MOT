package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemShaperArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemShaperArmorTrimSmithingTemplate() {
        super("minecraft:shaper_armor_trim_smithing_template", "Shaper Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SHAPER_ARMOR_TRIM;
    }
}
