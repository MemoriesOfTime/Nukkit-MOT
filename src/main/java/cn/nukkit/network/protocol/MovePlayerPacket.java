package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import lombok.ToString;

@ToString
public class MovePlayerPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MOVE_PLAYER_PACKET;

    public static final int MODE_NORMAL = 0;
    public static final int MODE_RESET = 1;
    public static final int MODE_TELEPORT = 2;
    public static final int MODE_PITCH = 3;

    public long eid;
    public float x;
    public float y;
    public float z;
    public float yaw;
    public float headYaw;
    public float pitch;
    public int mode = MODE_NORMAL;
    public boolean onGround;
    public long ridingEid;
    public int teleportCause = 0;
    public int teleportItem = 0;
    public long frame;

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.eid = getLong();
            }else{
                this.eid = getInt();
            }
            this.x = getFloat();
            this.y = getFloat();
            this.z = getFloat();
            this.yaw = getFloat();
            if(this.protocol <= ProtocolInfo.v_0_10_0){
                this.pitch = getFloat();
                this.headYaw = getFloat();
                this.mode = MODE_NORMAL;
                return;
            }
            this.headYaw = getFloat();
            this.pitch = getFloat();
            this.mode = (byte) getByte();
            this.onGround = getByte() > 0;
            return;
        }
        this.eid = this.getEntityRuntimeId();
        Vector3f v = this.getVector3f();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        this.pitch = this.getLFloat();
        if(this.protocol >= ProtocolInfo.v1_2_0) {
            this.yaw = this.getLFloat();
            this.headYaw = this.getLFloat();
        }else{
            this.headYaw = this.getLFloat();
            this.yaw = this.getLFloat();
        }
        this.mode = (byte) this.getByte();
        this.onGround = this.getBoolean();
        if(this.protocol <= ProtocolInfo.v_1_0_0){
            return;
        }
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.ridingEid = this.getEntityRuntimeId();
        }else{
            this.ridingEid = this.getEntityUniqueId();
        }
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            // v2168 wraps the teleport block in a single outer boolean
            if (this.getBoolean()) {
                this.teleportCause = this.getLInt();
                this.teleportItem = this.getLInt();
            }
        } else if (this.mode == MODE_TELEPORT) {
            this.teleportCause = this.getLInt();
            this.teleportItem = this.getLInt();
        }
        if (protocol >= ProtocolInfo.v1_16_100) {
            this.frame = this.getUnsignedVarLong();
        }
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putEntityRuntimeId(this.eid);
                this.putVector3f(this.x, this.y, this.z);
                this.putLFloat(this.pitch);
                this.putLFloat(this.yaw);
                this.putLFloat(this.headYaw);
                this.putByte((byte) this.mode);
                this.putBoolean(this.onGround);
                return;
            }
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(this.eid);
            }else{
                this.putInt((int) this.eid);
            }
            this.putFloat(x);
            this.putFloat(y);
            this.putFloat(z);
            if(this.protocol <= ProtocolInfo.v_0_10_0){
                this.putFloat(yaw);
                this.putFloat(pitch);
                this.putFloat(headYaw);
                this.putByte((byte) 0x00);
                return;
            }
            this.putFloat(yaw);
            this.putFloat(headYaw);
            this.putFloat(pitch);
            this.putByte((byte) (mode & 0xff));
            if(this.protocol >= ProtocolInfo.v_0_10_0){
                this.putByte(onGround ? (byte) 1 : 0);
            }
            return;
        }
        this.reset();
        this.putEntityRuntimeId(this.eid);
        this.putVector3f(this.x, this.y, this.z);
        this.putLFloat(this.pitch);
        this.putLFloat(this.yaw);
        this.putLFloat(this.headYaw);
        this.putByte((byte) this.mode);
        this.putBoolean(this.onGround);
        this.putEntityRuntimeId(this.ridingEid);
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putBoolean(this.mode == MODE_TELEPORT);
            if (this.mode == MODE_TELEPORT) {
                this.putLInt(this.teleportCause);
                this.putLInt(this.teleportItem);
            }
        } else if (this.mode == MODE_TELEPORT) {
            this.putLInt(this.teleportCause);
            this.putLInt(this.teleportItem);
        }
        if (protocol >= ProtocolInfo.v1_16_100) {
            this.putUnsignedVarLong(this.frame);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }
}
