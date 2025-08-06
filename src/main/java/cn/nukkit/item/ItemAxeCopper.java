package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemAxeCopper extends StringItemToolBase {

    public ItemAxeCopper() {
        super(COPPER_AXE, "Copper Axe");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_100;
    }

    @Override
    public boolean isAxe() {
        return true;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_COPPER;
    }

    @Override
    public int getMaxDurability() {
        return 191;
    }

    @Override
    public int getAttackDamage() {
        return 5;
    }
}
