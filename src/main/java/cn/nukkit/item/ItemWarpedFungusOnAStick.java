package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author PetteriM1
 */
public class ItemWarpedFungusOnAStick extends ItemTool {

    public ItemWarpedFungusOnAStick() {
        this(0, 1);
    }

    public ItemWarpedFungusOnAStick(Integer meta) {
        this(meta, 1);
    }

    public ItemWarpedFungusOnAStick(Integer meta, int count) {
        super(WARPED_FUNGUS_ON_A_STICK, meta, count, "Warped Fungus on a Stick");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_WARPED_FUNGUS_ON_A_STICK;
    }

    @Override
    public boolean noDamageOnBreak() {
        return true;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_16_0;
    }
}
