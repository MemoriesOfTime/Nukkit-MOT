package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemRibArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemRibArmorTrimSmithingTemplate() {
        super("minecraft:rib_armor_trim_smithing_template", "Rib Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.RIB_ARMOR_TRIM;
    }
}
