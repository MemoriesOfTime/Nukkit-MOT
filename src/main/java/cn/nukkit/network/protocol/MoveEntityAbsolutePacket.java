package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class MoveEntityAbsolutePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MOVE_ENTITY_ABSOLUTE_PACKET;

    public static final byte FLAG_GROUND = 0x01;
    public static final byte FLAG_TELEPORT = 0x02;
    public static final byte FLAG_FORCE_MOVE_LOCAL_ENTITY = 0x04;

    public long eid;
    public double x;
    public double y;
    public double z;
    public double yaw;
    public double headYaw;
    public double pitch;
    public boolean onGround;
    public boolean teleport;
    public boolean forceMoveLocalEntity;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_13_2){
            int length = getInt();
        }
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.eid = this.getLong();
            this.x = this.getFloat();
            this.y = this.getFloat();
            this.z = this.getFloat();
            this.pitch = this.getRotationByte();
            this.yaw = this.getRotationByte();
            this.headYaw = this.getRotationByte();
            return;
        }
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.eid = this.getEntityRuntimeId();
        }else {
            this.eid = this.getEntityUniqueId();
        }
        if (protocol >= 274) {
            int flags = this.getByte();
            onGround = (flags & FLAG_GROUND) != 0;
            teleport = (flags & FLAG_TELEPORT) != 0;
            forceMoveLocalEntity = (flags & FLAG_FORCE_MOVE_LOCAL_ENTITY) != 0;
        }
        Vector3f v = this.getVector3f();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        this.pitch = this.getRotationByte();
        this.headYaw = this.getRotationByte();
        this.yaw = this.getRotationByte();
        if (this.protocol < ProtocolInfo.v1_2_0 && this.protocol >= ProtocolInfo.v_1_0_0){
            this.onGround = this.getBoolean();
            this.teleport = this.getBoolean();
        }
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_0_13_2){
            this.tryReset();
            this.putInt( 1 );
            this.putLong(this.eid); //eid
            this.putFloat((float) this.x);
            this.putFloat((float) this.y);
            this.putFloat((float) this.z);
            this.putFloat((float) this.yaw);
            if(this.protocol > ProtocolInfo.v_0_11_0){
                this.putFloat((float) this.headYaw);
            }
            this.putFloat((float) this.pitch);
            return;
        }
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.tryReset();
            this.putLong(this.eid);
            this.putFloat((float) this.x);
            this.putFloat((float) this.y);
            this.putFloat((float) this.z);
            this.putRotationByte((byte) (this.pitch));
            this.putRotationByte((byte) (this.headYaw));
            this.putRotationByte((byte) (this.yaw));
            return;
        }
        this.reset();
        if(this.protocol >= ProtocolInfo.v1_2_0) {
            this.putEntityRuntimeId(this.eid);
        }else{
            this.putEntityUniqueId(this.eid);
        }
        if (protocol >= 274) {
            byte flags = 0;
            if (onGround) {
                flags |= FLAG_GROUND;
            }
            if (teleport) {
                flags |= FLAG_TELEPORT;
            }
            if (forceMoveLocalEntity) {
                flags |= FLAG_FORCE_MOVE_LOCAL_ENTITY;
            }
            this.putByte(flags);
        }
        this.putVector3f((float) this.x, (float) this.y, (float) this.z);
        this.putRotationByte(this.pitch);
        this.putRotationByte(this.headYaw);
        this.putRotationByte(this.yaw);
        if (protocol <= 261) {
            this.putBoolean(this.onGround);
            this.putBoolean(this.teleport);
        }
    }
}
