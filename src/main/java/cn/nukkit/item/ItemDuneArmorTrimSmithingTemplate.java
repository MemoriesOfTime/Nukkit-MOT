package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemDuneArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemDuneArmorTrimSmithingTemplate() {
        super(DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, "Dune Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.DUNE_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
