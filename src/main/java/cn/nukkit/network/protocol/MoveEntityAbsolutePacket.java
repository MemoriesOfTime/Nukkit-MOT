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
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.eid = this.getEntityRuntimeId();
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
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.eid);
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
