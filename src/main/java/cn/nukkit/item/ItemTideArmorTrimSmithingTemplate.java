package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemTideArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemTideArmorTrimSmithingTemplate() {
        super("minecraft:tide_armor_trim_smithing_template", "Tide Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.TIDE_ARMOR_TRIM;
    }
}
