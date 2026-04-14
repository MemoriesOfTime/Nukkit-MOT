package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemCoastArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemCoastArmorTrimSmithingTemplate() {
        super(COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "Coast Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.COAST_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_80;
    }
}
