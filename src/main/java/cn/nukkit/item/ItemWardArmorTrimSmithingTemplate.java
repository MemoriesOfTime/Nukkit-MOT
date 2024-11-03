package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemWardArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemWardArmorTrimSmithingTemplate() {
        super(WARD_ARMOR_TRIM_SMITHING_TEMPLATE, "Ward Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.WARD_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
