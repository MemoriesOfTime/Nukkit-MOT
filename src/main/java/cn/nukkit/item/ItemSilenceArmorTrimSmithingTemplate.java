package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemSilenceArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSilenceArmorTrimSmithingTemplate() {
        super("minecraft:silence_armor_trim_smithing_template", "Silence Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SILENCE_ARMOR_TRIM;
    }
}
