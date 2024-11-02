package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemVexArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemVexArmorTrimSmithingTemplate() {
        super(VEX_ARMOR_TRIM_SMITHING_TEMPLATE, "Vex Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.VEX_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
