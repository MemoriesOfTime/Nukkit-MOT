package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemPickaxeNetherite extends ItemTool {

    public ItemPickaxeNetherite() {
        this(0, 1);
    }

    public ItemPickaxeNetherite(Integer meta) {
        this(meta, 1);
    }

    public ItemPickaxeNetherite(Integer meta, int count) {
        super(NETHERITE_PICKAXE, meta, count, "Netherite Pickaxe");
    }

    @Override
    public boolean isPickaxe() {
        return true;
    }

    @Override
    public int getAttackDamage() {
        return 6;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_NETHERITE;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_NETHERITE;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_16_0;
    }
}
