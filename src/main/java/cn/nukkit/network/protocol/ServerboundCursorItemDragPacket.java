package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class ServerboundCursorItemDragPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_CURSOR_ITEM_DRAG_PACKET;

    public static final byte STATE_START = 0;
    public static final byte STATE_STOP = 1;

    public byte state;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.state = (byte) this.getByte();
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte(this.state);
    }
}
