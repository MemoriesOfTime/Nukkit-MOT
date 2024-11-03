package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemHostArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemHostArmorTrimSmithingTemplate() {
        super(HOST_ARMOR_TRIM_SMITHING_TEMPLATE, "Host Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.HOST_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
