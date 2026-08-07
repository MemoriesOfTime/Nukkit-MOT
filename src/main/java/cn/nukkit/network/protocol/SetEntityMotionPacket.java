package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
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
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.SET_ENTITY_MOTION_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putVarLong(this.eid);
                this.putVector3f(this.motionX, this.motionY, this.motionZ);
                return;
            }
            if(this.protocol <= ProtocolInfo.v_0_14_3) this.putInt(1);// 0.15.10 NO HAS
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(this.eid);
                this.putFloat(this.motionX);
                this.putFloat(this.motionY);
                this.putFloat(this.motionZ);
            }else{
                this.putInt((int) this.eid);
                this.putShort((short) this.motionX);
                this.putShort((short) this.motionY);
                this.putShort((short) this.motionZ);
            }
//            this.putInt(this.entities.length);
//            for (Entry entry : this.entities) {
//                this.putLong(entry.entityId);
//                this.putFloat((float) entry.motionX);
//                this.putFloat((float) entry.motionY);
//                this.putFloat((float) entry.motionZ);
//            }
            return;
        }
        this.reset();
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.eid);
        }else{
            this.putEntityUniqueId(this.eid);
        }
        this.putVector3f(this.motionX, this.motionY, this.motionZ);
        if (protocol >= ProtocolInfo.v1_20_70) {
            this.putUnsignedVarLong(this.tick);
        }
    }
}
