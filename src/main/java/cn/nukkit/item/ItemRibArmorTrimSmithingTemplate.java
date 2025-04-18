package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemRibArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemRibArmorTrimSmithingTemplate() {
        super(RIB_ARMOR_TRIM_SMITHING_TEMPLATE, "Rib Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.RIB_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
