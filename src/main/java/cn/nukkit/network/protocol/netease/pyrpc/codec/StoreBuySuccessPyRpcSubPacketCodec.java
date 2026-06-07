package cn.nukkit.network.protocol.netease.pyrpc.codec;

import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacketCodec;
import cn.nukkit.network.protocol.netease.pyrpc.io.PyRpcWriter;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.StoreBuySuccessPyRpcSubPacket;

/**
 * Codec for NetEase store purchase success PyRpc payloads.
 */
public final class StoreBuySuccessPyRpcSubPacketCodec implements PyRpcSubPacketCodec<StoreBuySuccessPyRpcSubPacket> {

    @Override
    public String getMethod() {
        return StoreBuySuccessPyRpcSubPacket.METHOD;
    }

    @Override
    public Class<StoreBuySuccessPyRpcSubPacket> getSubPacketClass() {
        return StoreBuySuccessPyRpcSubPacket.class;
    }

    @Override
    public StoreBuySuccessPyRpcSubPacket decode(PyRpcMessage message) {
        return new StoreBuySuccessPyRpcSubPacket();
    }

    @Override
    public void encode(StoreBuySuccessPyRpcSubPacket packet, PyRpcWriter writer) {
        writer.writeArrayHeader(1);
        writer.writeBinaryString(StoreBuySuccessPyRpcSubPacket.METHOD);
    }
}
