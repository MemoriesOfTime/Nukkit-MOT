package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemCoastArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemCoastArmorTrimSmithingTemplate() {
        super("minecraft:coast_armor_trim_smithing_template", "Coast Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.COAST_ARMOR_TRIM;
    }
}
