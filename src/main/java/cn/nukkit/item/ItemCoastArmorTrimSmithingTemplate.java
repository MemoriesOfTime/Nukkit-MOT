package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemCoastArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemCoastArmorTrimSmithingTemplate() {
        super("minecraft:coast_armor_trim_smithing_template", "Coast Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.COAST_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
