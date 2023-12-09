package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimPatternType;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemSentryArmorTrimSmithingTemplate extends StringItemBase implements ItemTrimPattern {

    public ItemSentryArmorTrimSmithingTemplate() {
        super("minecraft:sentry_armor_trim_smithing_template", "Sentry Armor Trim Smithing Template");
    }

    @Override
    public ItemTrimPatternType getPattern() {
        return ItemTrimPatternType.SENTRY_ARMOR_TRIM;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_80;
    }
}
