package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemEyeArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemEyeArmorTrimSmithingTemplate() {
        super("minecraft:eye_armor_trim_smithing_template", "Eye Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.EYE_ARMOR_TRIM;
    }
}
