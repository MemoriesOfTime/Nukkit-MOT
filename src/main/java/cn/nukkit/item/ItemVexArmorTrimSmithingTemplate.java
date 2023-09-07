package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemVexArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemVexArmorTrimSmithingTemplate() {
        super("minecraft:vex_armor_trim_smithing_template", "Vex Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.VEX_ARMOR_TRIM;
    }
}
