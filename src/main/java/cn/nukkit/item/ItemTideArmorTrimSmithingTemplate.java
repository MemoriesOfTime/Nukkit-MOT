package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemTideArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemTideArmorTrimSmithingTemplate() {
        super("minecraft:tide_armor_trim_smithing_template", "Tide Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.TIDE_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
