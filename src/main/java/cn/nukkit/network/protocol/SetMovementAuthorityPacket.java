package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.ServerAuthMovementMode;
import lombok.ToString;

@ToString
public class SetMovementAuthorityPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SET_MOVEMENT_AUTHORITY_PACKET;

    public ServerAuthMovementMode serverAuthMovementMode;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        int id = this.getByte();
        this.serverAuthMovementMode = ServerAuthMovementMode.values()[id];
    }

    @Override
    public void encode() {
        this.putByte((byte) serverAuthMovementMode.ordinal());
    }
}
