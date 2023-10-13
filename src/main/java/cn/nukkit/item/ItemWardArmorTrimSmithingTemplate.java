package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemWardArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemWardArmorTrimSmithingTemplate() {
        super("minecraft:ward_armor_trim_smithing_template", "Ward Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.WARD_ARMOR_TRIM;
    }
}
