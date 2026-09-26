package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class SetHealthPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_HEALTH_PACKET;

    public int health;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        // signed zigzag varint on the wire (CB writeInt) even though health is non-negative
        this.putVarInt(this.health);
    }
}
