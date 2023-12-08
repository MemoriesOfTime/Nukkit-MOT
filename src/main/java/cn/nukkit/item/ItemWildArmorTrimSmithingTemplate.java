package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemWildArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemWildArmorTrimSmithingTemplate() {
        super("minecraft:wild_armor_trim_smithing_template", "Wild Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.WILD_ARMOR_TRIM;
    }
}
