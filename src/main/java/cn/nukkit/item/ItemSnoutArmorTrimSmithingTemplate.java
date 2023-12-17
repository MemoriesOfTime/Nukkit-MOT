package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemSnoutArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSnoutArmorTrimSmithingTemplate() {
        super("minecraft:snout_armor_trim_smithing_template", "Snout Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SNOUT_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
