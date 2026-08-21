package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class MoveEntityDeltaPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MOVE_ENTITY_DELTA_PACKET;

    public static final int FLAG_HAS_X = 0b1;
    public static final int FLAG_HAS_Y = 0b10;
    public static final int FLAG_HAS_Z = 0b100;
    public static final int FLAG_HAS_PITCH = 0B1000;
    public static final int FLAG_HAS_YAW = 0B10000;
    public static final int FLAG_HAS_HEAD_YAW = 0B100000;
    public static final int FLAG_ON_GROUND = 0B1000000;
    public static final int FLAG_TELEPORTING = 0B10000000;
    public static final int FLAG_FORCE_MOVE_LOCAL_ENTITY = 0B100000000;
    public static final int FLAG_FORCE_COMPLETION = 0B1000000000;

    public long eid;
    public int flags = 0;
    public float x = 0;
    public float y = 0;
    public float z = 0;
    public double yawDelta = 0;
    public double headYawDelta = 0;
    public double pitchDelta = 0;

    /**
     * @since v2168 1.26.40
     */
    public boolean onGround;
    /**
     * @since v2168 1.26.40
     */
    public boolean forceMove;
    /**
     * @since v2168 1.26.40
     */
    public boolean forceMoveLocalEntity;
    /**
     * @since v2168 1.26.40
     */
    public boolean forceCompletion;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_26_40) {
            this.eid = this.getEntityRuntimeId();
            // v2168: per-axis optional booleans; mirror them into the legacy flags bitmask
            // so downstream readers can still query FLAG_HAS_* as on older protocols.
            this.flags = 0;
            boolean hasX = this.getBoolean();
            if (hasX) { this.flags |= FLAG_HAS_X; this.x = this.getLFloat(); } else { this.x = 0f; }
            boolean hasY = this.getBoolean();
            if (hasY) { this.flags |= FLAG_HAS_Y; this.y = this.getLFloat(); } else { this.y = 0f; }
            boolean hasZ = this.getBoolean();
            if (hasZ) { this.flags |= FLAG_HAS_Z; this.z = this.getLFloat(); } else { this.z = 0f; }
            boolean hasPitch = this.getBoolean();
            if (hasPitch) { this.flags |= FLAG_HAS_PITCH; this.pitchDelta = this.getByte() * 1.40625; } else { this.pitchDelta = 0d; }
            boolean hasYaw = this.getBoolean();
            if (hasYaw) { this.flags |= FLAG_HAS_YAW; this.yawDelta = this.getByte() * 1.40625; } else { this.yawDelta = 0d; }
            boolean hasHeadYaw = this.getBoolean();
            if (hasHeadYaw) { this.flags |= FLAG_HAS_HEAD_YAW; this.headYawDelta = this.getByte() * 1.40625; } else { this.headYawDelta = 0d; }
            this.onGround = this.getBoolean();
            if (this.onGround) { this.flags |= FLAG_ON_GROUND; }
            this.forceMove = this.getBoolean();
            this.forceMoveLocalEntity = this.getBoolean();
            if (this.forceMoveLocalEntity) { this.flags |= FLAG_FORCE_MOVE_LOCAL_ENTITY; }
            this.forceCompletion = this.getBoolean();
        } else {
            this.getEntityRuntimeId();
            if (protocol >= ProtocolInfo.v1_13_0) {
                this.flags = this.getLShort();
            }else {
                this.flags = this.getByte();
            }
            this.x = getCoordinate(FLAG_HAS_X);
            this.y = getCoordinate(FLAG_HAS_Y);
            this.z = getCoordinate(FLAG_HAS_Z);
            this.pitchDelta = getRotation(FLAG_HAS_PITCH);
            this.yawDelta = getRotation(FLAG_HAS_YAW);
            this.headYawDelta = getRotation(FLAG_HAS_HEAD_YAW);
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_26_40) {
            this.putEntityRuntimeId(this.eid);
            this.putBoolean((this.flags & FLAG_HAS_X) != 0);
            if ((this.flags & FLAG_HAS_X) != 0) {
                this.putLFloat(this.x);
            }
            this.putBoolean((this.flags & FLAG_HAS_Y) != 0);
            if ((this.flags & FLAG_HAS_Y) != 0) {
                this.putLFloat(this.y);
            }
            this.putBoolean((this.flags & FLAG_HAS_Z) != 0);
            if ((this.flags & FLAG_HAS_Z) != 0) {
                this.putLFloat(this.z);
            }
            this.putBoolean((this.flags & FLAG_HAS_PITCH) != 0);
            if ((this.flags & FLAG_HAS_PITCH) != 0) {
                this.putByte((byte) (this.pitchDelta / 1.40625));
            }
            this.putBoolean((this.flags & FLAG_HAS_YAW) != 0);
            if ((this.flags & FLAG_HAS_YAW) != 0) {
                this.putByte((byte) (this.yawDelta / 1.40625));
            }
            this.putBoolean((this.flags & FLAG_HAS_HEAD_YAW) != 0);
            if ((this.flags & FLAG_HAS_HEAD_YAW) != 0) {
                this.putByte((byte) (this.headYawDelta / 1.40625));
            }
            this.putBoolean((this.flags & FLAG_ON_GROUND) != 0 || this.onGround);
            this.putBoolean(this.forceMove);
            this.putBoolean((this.flags & FLAG_FORCE_MOVE_LOCAL_ENTITY) != 0 || this.forceMoveLocalEntity);
            this.putBoolean((this.flags & FLAG_FORCE_COMPLETION) != 0 || this.forceCompletion);
        } else {
            this.putEntityRuntimeId(this.eid);
            if (protocol >= ProtocolInfo.v1_13_0) {
                this.putLShort(this.flags);
            }else {
                this.putByte((byte) flags);
            }
            putCoordinate(FLAG_HAS_X, this.x);
            putCoordinate(FLAG_HAS_Y, this.y);
            putCoordinate(FLAG_HAS_Z, this.z);
            putRotation(FLAG_HAS_PITCH, this.pitchDelta);
            putRotation(FLAG_HAS_YAW, this.yawDelta);
            putRotation(FLAG_HAS_HEAD_YAW, this.headYawDelta);
        }
    }

    private float getCoordinate(int flag) {
        if ((flags & flag) != 0) {
            if (protocol < ProtocolInfo.v1_16_100) {
                return (float) this.getVarInt();
            }
            return this.getLFloat();
        }
        return 0;
    }

    private double getRotation(int flag) {
        if ((flags & flag) != 0) {
            return this.getByte() * 1.40625;
        }
        return 0d;
    }

    private void putCoordinate(int flag, float value) {
        if ((flags & flag) != 0) {
            if (protocol < ProtocolInfo.v1_16_100) {
                this.putVarInt((int) value);
            } else {
                this.putLFloat(value);
            }
        }
    }

    private void putRotation(int flag, double value) {
        if ((flags & flag) != 0) {
            this.putByte((byte) (value / 1.40625));
        }
    }
}
