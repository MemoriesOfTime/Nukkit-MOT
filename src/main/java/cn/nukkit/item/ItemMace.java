package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author glorydark
 */
public class ItemMace extends StringItemToolBase {

    public ItemMace() {
        super("minecraft:mace", "Mace");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_BRUSH;
    }

    @Override
    public int getAttackDamage() {
        return 5;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_DIAMOND;
    }

    @Override
    public boolean isSword() {
        return true;
    }
}