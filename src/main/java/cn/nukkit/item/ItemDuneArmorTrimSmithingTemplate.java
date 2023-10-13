package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemDuneArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemDuneArmorTrimSmithingTemplate() {
        super("minecraft:dune_armor_trim_smithing_template", "Dune Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.DUNE_ARMOR_TRIM;
    }
}
