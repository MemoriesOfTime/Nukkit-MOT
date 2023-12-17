package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemShaperArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemShaperArmorTrimSmithingTemplate() {
        super("minecraft:shaper_armor_trim_smithing_template", "Shaper Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SHAPER_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
