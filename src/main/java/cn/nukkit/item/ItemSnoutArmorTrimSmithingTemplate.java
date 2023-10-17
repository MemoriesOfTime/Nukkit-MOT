package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemSnoutArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSnoutArmorTrimSmithingTemplate() {
        super("minecraft:snout_armor_trim_smithing_template", "Snout Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SNOUT_ARMOR_TRIM;
    }
}
