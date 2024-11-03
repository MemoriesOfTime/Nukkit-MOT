package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemWildArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemWildArmorTrimSmithingTemplate() {
        super(WILD_ARMOR_TRIM_SMITHING_TEMPLATE, "Wild Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.WILD_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
