package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemRaiserArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemRaiserArmorTrimSmithingTemplate() {
        super("minecraft:raiser_armor_trim_smithing_template", "Raiser Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.RAISER_ARMOR_TRIM_ARMOR_TRIM;
    }
}
