package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemBootsCopper extends StringItemBase {

    public ItemBootsCopper() {
        super(COPPER_BOOTS, "Copper Boots");
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
    public boolean isBoots() {
        return true;
    }

    @Override
    public int getArmorPoints() {
        return 1;
    }

    @Override
    public int getMaxDurability() {
        return 144;
    }
}
