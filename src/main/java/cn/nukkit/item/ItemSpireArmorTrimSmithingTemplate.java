package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemSpireArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSpireArmorTrimSmithingTemplate() {
        super("minecraft:spire_armor_trim_smithing_template", "Spire Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SPIRE_ARMOR_TRIM;
    }
}
