package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemPickaxeCopper extends StringItemToolBase {

    public ItemPickaxeCopper() {
        super(COPPER_PICKAXE, "Copper Pickaxe");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_100;
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
        return ItemTool.DURABILITY_COPPER;
    }

    @Override
    public int getAttackDamage() {
        return 5;
    }
}
