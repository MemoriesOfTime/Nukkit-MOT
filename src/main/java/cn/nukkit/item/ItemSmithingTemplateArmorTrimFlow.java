package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemSmithingTemplateArmorTrimFlow extends StringItemBase implements ItemTrimPattern {

    public ItemSmithingTemplateArmorTrimFlow() {
        super(FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, "Flow Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.FLOW_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}
