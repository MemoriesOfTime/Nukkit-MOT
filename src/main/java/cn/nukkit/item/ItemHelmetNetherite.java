package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemHelmetNetherite extends ItemArmor {

    public ItemHelmetNetherite() {
        this(0, 1);
    }

    public ItemHelmetNetherite(Integer meta) {
        this(meta, 1);
    }

    public ItemHelmetNetherite(Integer meta, int count) {
        super(NETHERITE_HELMET, meta, count, "Netherite Helmet");
    }

    @Override
    public boolean isHelmet() {
        return true;
    }

    @Override
    public int getTier() {
        return ItemArmor.TIER_NETHERITE;
    }

    @Override
    public int getMaxDurability() {
        return 407;
    }

    @Override
    public int getArmorPoints() {
        return 3;
    }

    @Override
    public int getToughness() {
        return 2;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_16_0;
    }
}
