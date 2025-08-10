package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemChestplateCopper extends StringItemBase {

    public ItemChestplateCopper() {
        super(COPPER_CHESTPLATE, "Copper Chestplate");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_100;
    }

    @Override
    public int getTier() {
        return ItemArmor.TIER_COPPER;
    }

    @Override
    public boolean isChestplate() {
        return true;
    }

    @Override
    public int getArmorPoints() {
        return 4;
    }

    @Override
    public int getMaxDurability() {
        return 177;
    }
}
