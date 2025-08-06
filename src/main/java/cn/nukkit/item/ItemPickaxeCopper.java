package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemPickaxeCopper extends StringItemToolBase {

    public ItemPickaxeCopper() {
        super(COPPER_PICKAXE, "Copper Pickaxe");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_100;
    }

    @Override
    public boolean isPickaxe() {
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
