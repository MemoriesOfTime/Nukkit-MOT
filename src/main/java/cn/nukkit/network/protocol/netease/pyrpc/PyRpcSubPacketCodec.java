package cn.nukkit.network.protocol.netease.pyrpc;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.netease.pyrpc.io.PyRpcWriter;

/**
 * Codec for one typed NetEase PyRpc method.
 *
 * @param <T> sub-packet type handled by this codec
 */
@OnlyNetEase
public interface PyRpcSubPacketCodec<T extends PyRpcSubPacket> {

    String getMethod();

    Class<T> getSubPacketClass();

    T decode(PyRpcMessage message);

    void encode(T packet, PyRpcWriter writer);
}
