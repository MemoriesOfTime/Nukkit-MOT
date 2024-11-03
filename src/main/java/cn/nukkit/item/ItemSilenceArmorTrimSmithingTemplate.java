package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemSilenceArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSilenceArmorTrimSmithingTemplate() {
        super(SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "Silence Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SILENCE_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
