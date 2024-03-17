package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class SetEntityMotionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_ENTITY_MOTION_PACKET;

    public long eid;
    public float motionX;
    public float motionY;
    public float motionZ;
    /**
     * @since v662 1.20.70
     */
    public long tick;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.eid);
        this.putVector3f(this.motionX, this.motionY, this.motionZ);
        if (protocol >= ProtocolInfo.v1_20_70) {
            this.putUnsignedVarLong(this.tick);
        }
    }
}
