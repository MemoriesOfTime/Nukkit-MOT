package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemWayfinderArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemWayfinderArmorTrimSmithingTemplate() {
        super("minecraft:wayfinder_armor_trim_smithing_template", "Wayfinder Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.WAYFINDER_ARMOR_TRIM;
    }
}
