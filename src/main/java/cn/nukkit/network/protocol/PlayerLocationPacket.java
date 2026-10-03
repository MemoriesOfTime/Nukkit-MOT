package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @since 1.21.80 (800)
 */
@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
@ToString(doNotUseGetters = true)
public class PlayerLocationPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PLAYER_LOCATIONS_PACKET;

    public Type type;
    public long targetEntityId;
    public Vector3f position;

    @Override
    public byte pid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.targetEntityId = this.getEntityUniqueId();
            this.type = Type.values()[(int) this.getUnsignedVarInt()];
            this.getVarInt(); // always-zero filler / 始终为 0 的填充字段
        } else {
            this.type = Type.values()[this.getLInt()];
            this.targetEntityId = this.getEntityUniqueId();
        }
        if (this.type == Type.COORDINATES) {
            this.position = this.getVector3f();
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putEntityUniqueId(this.targetEntityId);
            this.putUnsignedVarInt(this.type.ordinal());
            this.putVarInt(0); // always-zero filler / 始终为 0 的填充字段
        } else {
            this.putLInt(this.type.ordinal());
            this.putEntityUniqueId(this.targetEntityId);
        }
        if (this.type == Type.COORDINATES) {
            this.putVector3f(this.position);
        }
    }

    public enum Type {
        COORDINATES,
        HIDE
    }
}
