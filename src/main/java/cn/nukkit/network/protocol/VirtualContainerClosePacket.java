package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.VirtualContainerCloseCause;
import cn.nukkit.network.protocol.types.VirtualContainerCloseKind;
import lombok.ToString;

/**
 * 协调虚拟容器界面的关闭：服务端发 Request/Confirmed，客户端发 Closed。
 * <p>
 * Coordinates closing a virtual container screen: the server sends Request/Confirmed,
 * the client answers with Closed.
 *
 * @since v2250 1.26.70.23
 */
@ToString
public class VirtualContainerClosePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.VIRTUAL_CONTAINER_CLOSE_PACKET;

    public long sessionId;
    public VirtualContainerCloseKind kind = VirtualContainerCloseKind.CLOSED;
    public VirtualContainerCloseCause cause = VirtualContainerCloseCause.UNKNOWN;

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
        this.sessionId = this.getUnsignedVarLong();
        this.kind = VirtualContainerCloseKind.fromId(this.getByte() & 0xff);
        this.cause = VirtualContainerCloseCause.fromId(this.getByte() & 0xff);
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarLong(this.sessionId);
        this.putByte((byte) this.kind.getId());
        this.putByte((byte) this.cause.getId());
    }
}
