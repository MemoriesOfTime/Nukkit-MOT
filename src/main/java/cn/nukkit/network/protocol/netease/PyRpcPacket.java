package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcProtocol;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacketCodec;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.RawPyRpcSubPacket;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * NetEase packet used for Python scripting RPC calls.
 */
@OnlyNetEase
@ToString
public class PyRpcPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PY_RPC_PACKET;

    public static final long DEFAULT_MSG_ID = 9753608L;

    public byte[] data = new byte[0];
    public long msgId;
    public PyRpcMessage message;

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
        this.message = decodeMessage(this.data);
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
        this.message = null;
    }

    public long getMsgId() {
        return this.msgId;
    }

    public void setMsgId(long msgId) {
        this.msgId = msgId & 0xffffffffL;
    }

    public PyRpcMessage getMessage() {
        return this.message;
    }

    public PyRpcSubPacket getSubPacket() {
        return this.message != null ? this.message.getSubPacket() : null;
    }

    public static <T extends PyRpcSubPacket> void registerSubPacketCodec(PyRpcSubPacketCodec<T> codec) {
        PyRpcProtocol.DEFAULT.register(codec);
    }

    public static PyRpcPacket createModEventPacket(String modName, String systemName, String eventName,
                                                   Map<String, ?> eventData) {
        return createSubPacket(new ModEventPyRpcSubPacket(
                ModEventPyRpcSubPacket.SERVER_TO_CLIENT_METHOD,
                modName,
                systemName,
                eventName,
                eventData));
    }

    public static PyRpcPacket createEncryptedModEventPacket(String modName, String systemName, String eventName,
                                                            String data, Function<String, String> encMethod) {
        Objects.requireNonNull(encMethod, "encMethod");
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("data", Objects.requireNonNull(encMethod.apply(data), "encrypted data"));
        return createModEventPacket(modName, systemName, eventName, eventData);
    }

    public static PyRpcPacket createCustomPacket(String method, List<?> arguments) {
        return createCustomPacket(method, arguments, DEFAULT_MSG_ID);
    }

    public static PyRpcPacket createCustomPacket(String method, List<?> arguments, long msgId) {
        return createSubPacket(new RawPyRpcSubPacket(method, arguments, null, null), msgId);
    }

    public static PyRpcPacket createSubPacket(PyRpcSubPacket subPacket) {
        return createSubPacket(subPacket, DEFAULT_MSG_ID);
    }

    public static PyRpcPacket createSubPacket(PyRpcSubPacket subPacket, long msgId) {
        Objects.requireNonNull(subPacket, "subPacket");
        PyRpcPacket packet = new PyRpcPacket();
        packet.setData(PyRpcProtocol.DEFAULT.encode(subPacket));
        packet.setMsgId(msgId);
        PyRpcMessage decodedMessage = decodeMessage(packet.getData());
        packet.message = decodedMessage != null
                ? decodedMessage.withSubPacket(subPacket)
                : new PyRpcMessage(
                        subPacket.getMethod(),
                        PyRpcProtocol.DEFAULT.argumentsOf(subPacket),
                        null,
                        packet.getData(),
                        subPacket);
        return packet;
    }

    public static PyRpcMessage decodeMessage(byte[] data) {
        return PyRpcProtocol.DEFAULT.decode(data);
    }
}
