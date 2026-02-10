package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemWindCharge extends StringItemProjectileBase {

    public ItemWindCharge() {
        super("minecraft:wind_charge", "Wind Charge");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }

    @Override
    public String getProjectileEntityType() {
        return "WindCharge";
    }

    @Override
    public float getThrowForce() {
        return 1.5f;
    }
}