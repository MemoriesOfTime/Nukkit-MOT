package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemSpireArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSpireArmorTrimSmithingTemplate() {
        super(SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, "Spire Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SPIRE_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_80;
    }
}
