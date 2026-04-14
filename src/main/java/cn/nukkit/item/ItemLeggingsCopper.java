package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemLeggingsCopper extends StringItemBase {

    public ItemLeggingsCopper() {
        super(COPPER_LEGGINGS, "Copper Leggings");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_100;
    }

    @Override
    public int getTier() {
        return ItemArmor.TIER_COPPER;
    }

    @Override
    public boolean isLeggings() {
        return true;
    }

    @Override
    public int getArmorPoints() {
        return 3;
    }

    @Override
    public int getMaxDurability() {
        return 166;
    }
}
