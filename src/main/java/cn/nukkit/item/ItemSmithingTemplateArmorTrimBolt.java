package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author LT_Name
 */
public class ItemSmithingTemplateArmorTrimBolt extends StringItemBase implements ItemTrimPattern {

    public ItemSmithingTemplateArmorTrimBolt() {
        super(BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, "Bolt Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.BOLT_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}
