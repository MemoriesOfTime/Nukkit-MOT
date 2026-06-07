package cn.nukkit.network.protocol.netease.pyrpc;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.netease.pyrpc.io.PyRpcWriter;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.RawPyRpcSubPacket;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for typed NetEase PyRpc sub-packet codecs.
 */
@OnlyNetEase
public final class PyRpcCodecRegistry {

    private final Map<String, PyRpcSubPacketCodec<? extends PyRpcSubPacket>> codecsByMethod = new ConcurrentHashMap<>();
    private final Map<Class<? extends PyRpcSubPacket>, PyRpcSubPacketCodec<? extends PyRpcSubPacket>> codecsByClass = new ConcurrentHashMap<>();

    public <T extends PyRpcSubPacket> void register(PyRpcSubPacketCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(codec.getMethod(), "codec method");
        Objects.requireNonNull(codec.getSubPacketClass(), "codec sub-packet class");
        this.codecsByMethod.put(codec.getMethod(), codec);
        this.codecsByClass.put(codec.getSubPacketClass(), codec);
    }

    public PyRpcSubPacket decode(PyRpcMessage message) {
        Objects.requireNonNull(message, "message");
        PyRpcSubPacketCodec<? extends PyRpcSubPacket> codec = this.codecsByMethod.get(message.getMethod());
        if (codec == null) {
            return new RawPyRpcSubPacket(
                    message.getMethod(),
                    message.getArguments(),
                    message.getRawRoot(),
                    message.getRawPayload());
        }
        return codec.decode(message);
    }

    public byte[] encode(PyRpcSubPacket packet) {
        Objects.requireNonNull(packet, "packet");
        PyRpcSubPacketCodec<? extends PyRpcSubPacket> codec = findCodec(packet);
        PyRpcWriter writer = new PyRpcWriter();
        if (codec == null) {
            if (packet instanceof RawPyRpcSubPacket raw) {
                writer.writeMessage(raw.getMethod(), raw.getArguments());
                return writer.toByteArray();
            }
            throw new IllegalArgumentException("No PyRpc codec registered for " + packet.getClass().getName());
        }
        encodeWithCodec(codec, packet, writer);
        return writer.toByteArray();
    }

    private PyRpcSubPacketCodec<? extends PyRpcSubPacket> findCodec(PyRpcSubPacket packet) {
        PyRpcSubPacketCodec<? extends PyRpcSubPacket> codec = this.codecsByClass.get(packet.getClass());
        if (codec != null) {
            return codec;
        }
        for (PyRpcSubPacketCodec<? extends PyRpcSubPacket> registered : this.codecsByClass.values()) {
            if (registered.getSubPacketClass().isInstance(packet)) {
                return registered;
            }
        }
        return this.codecsByMethod.get(packet.getMethod());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void encodeWithCodec(PyRpcSubPacketCodec codec, PyRpcSubPacket packet, PyRpcWriter writer) {
        codec.encode(packet, writer);
    }
}
