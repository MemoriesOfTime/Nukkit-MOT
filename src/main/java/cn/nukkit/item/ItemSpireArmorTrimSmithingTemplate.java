package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemSpireArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSpireArmorTrimSmithingTemplate() {
        super("minecraft:spire_armor_trim_smithing_template", "Spire Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SPIRE_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
