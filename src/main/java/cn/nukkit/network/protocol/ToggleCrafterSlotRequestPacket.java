package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(doNotUseGetters = true)
@ToString(doNotUseGetters = true)
public class ToggleCrafterSlotRequestPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.TOGGLE_CRAFTER_SLOT_REQUEST;

    public Vector3f blockPosition;
    public byte slot;
    public boolean disabled;

    @Override
    public byte pid() {
        return ProtocolInfo.__INTERNAL__TOGGLE_CRAFTER_SLOT_REQUEST;
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.blockPosition = this.getVector3f();
        this.slot = (byte) this.getByte();
        this.disabled = this.getBoolean();
    }

    @Override
    public void encode() {
        this.reset();
        this.putVector3f(this.blockPosition);
        this.putByte(this.slot);
        this.putBoolean(this.disabled);
    }
}
