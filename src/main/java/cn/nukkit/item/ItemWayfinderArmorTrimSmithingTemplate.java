package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemWayfinderArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemWayfinderArmorTrimSmithingTemplate() {
        super(WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, "Wayfinder Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.WAYFINDER_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
