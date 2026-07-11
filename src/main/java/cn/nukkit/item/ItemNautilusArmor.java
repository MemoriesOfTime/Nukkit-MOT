package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public abstract class ItemNautilusArmor extends StringItemBase {

    private final int armorPoints;

    protected ItemNautilusArmor(String namespaceId, String name, int armorPoints) {
        super(namespaceId, name);
        this.armorPoints = armorPoints;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getArmorPoints() {
        return this.armorPoints;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_130_28;
    }
}
