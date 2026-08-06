package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author Nukkit Project Team
 */
@ToString
public class RespawnPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RESPAWN_PACKET;

    public static final int STATE_SEARCHING_FOR_SPAWN = 0;
    public static final int STATE_READY_TO_SPAWN = 1;
    public static final int STATE_CLIENT_READY_TO_SPAWN = 2;

    public float x;
    public float y;
    public float z;
    public int respawnState = STATE_SEARCHING_FOR_SPAWN;
    public long runtimeEntityId;

    @Override
    public void decode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                Vector3f v = this.getVector3f();
                this.x = v.x;
                this.y = v.y;
                this.z = v.z;
                return;
            }
            if(this.protocol <= ProtocolInfo.v_0_10_0){
                int eid = this.getInt();
            }
            this.x = getFloat();
            this.y = getFloat();
            this.z = getFloat();
            return;
        }
        Vector3f v = this.getVector3f();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        if (protocol >= 388) {
            this.respawnState = this.getByte();
            this.runtimeEntityId  = this.getEntityRuntimeId();
        }
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putVector3f(this.x, this.y, this.z);
                return;
            }
            if(this.protocol <= ProtocolInfo.v_0_10_0){
                int eid = 0;
                this.putInt(eid);
            }
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            return;
        }
        this.reset();
        this.putVector3f(this.x, this.y, this.z);
        if (protocol >= 388) {
            this.putByte((byte) respawnState);
            this.putEntityRuntimeId(runtimeEntityId);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.RESPAWN_PACKET;
        }
        return NETWORK_ID;
    }
}
