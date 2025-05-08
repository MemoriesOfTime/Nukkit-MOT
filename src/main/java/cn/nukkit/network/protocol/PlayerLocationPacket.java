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
        this.type = Type.values()[this.getLInt()];
        this.targetEntityId = this.getEntityUniqueId();
        if (this.type == Type.COORDINATES) {
            this.position = this.getVector3f();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putLInt(this.type.ordinal());
        this.putEntityUniqueId(this.targetEntityId);
        if (this.type == Type.COORDINATES) {
            this.putVector3f(this.position);
        }
    }

    public enum Type {
        COORDINATES,
        HIDE
    }
}
