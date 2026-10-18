package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * 客户端缓存 miss 补发响应（哈希+原文列表），服务端→客户端。
 * <p>
 * Client cache miss response (hash + payload pairs), server to client.
 */
@ToString
public class ClientCacheMissResponsePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CLIENT_CACHE_MISS_RESPONSE_PACKET;

    public long[] hashes = new long[0];
    public byte[][] payloads = new byte[0][];

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
        this.putUnsignedVarInt(this.hashes.length);
        for (int i = 0; i < this.hashes.length; i++) {
            this.putLLong(this.hashes[i]);
            this.putByteArray(this.payloads[i]);
        }
    }
}
