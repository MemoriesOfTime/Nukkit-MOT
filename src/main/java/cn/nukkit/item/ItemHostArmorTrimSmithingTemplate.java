package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;

/**
 * @author Glorydark
 */
public class ItemHostArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemHostArmorTrimSmithingTemplate() {
        super("minecraft:host_armor_trim_smithing_template", "Host Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.HOST_ARMOR_TRIM;
    }
}
