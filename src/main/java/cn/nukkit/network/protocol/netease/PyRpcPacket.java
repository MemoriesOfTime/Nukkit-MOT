package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

/**
 * NetEase packet used for Python scripting RPC calls.
 */
@OnlyNetEase
@ToString
public class PyRpcPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PY_RPC_PACKET;

    public byte[] data = new byte[0];
    public long msgId;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        this.data = this.getByteArray();
        this.msgId = this.getLInt() & 0xffffffffL;
    }

    @Override
    public void encode() {
        this.reset();
        this.putByteArray(this.data != null ? this.data : new byte[0]);
        this.putLInt((int) this.msgId);
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

    public long getMsgId() {
        return this.msgId;
    }

    public void setMsgId(long msgId) {
        this.msgId = msgId & 0xffffffffL;
    }
}
