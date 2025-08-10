package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemShovelCopper extends StringItemToolBase {

    public ItemShovelCopper() {
        super(COPPER_SHOVEL, "Copper Shovel");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_100;
    }

    @Override
    public boolean isShovel() {
        return true;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_COPPER;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_COPPER;
    }

    @Override
    public int getAttackDamage() {
        return 4;
    }
}
