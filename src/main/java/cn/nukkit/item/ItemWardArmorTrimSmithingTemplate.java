package cn.nukkit.item;

import cn.nukkit.GameVersion;
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
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_80;
    }
}
