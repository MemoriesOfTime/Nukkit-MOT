package cn.nukkit.network.protocol.netease.pyrpc.codec;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcProtocol;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacketCodec;
import cn.nukkit.network.protocol.netease.pyrpc.io.PyRpcWriter;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket;

import java.util.List;

/**
 * Codec for NetEase ModEventC2S/ModEventS2C PyRpc payloads.
 */
@OnlyNetEase
public final class ModEventPyRpcSubPacketCodec implements PyRpcSubPacketCodec<ModEventPyRpcSubPacket> {

    @Override
    public String getMethod() {
        return ModEventPyRpcSubPacket.CLIENT_TO_SERVER_METHOD;
    }

    @Override
    public Class<ModEventPyRpcSubPacket> getSubPacketClass() {
        return ModEventPyRpcSubPacket.class;
    }

    @Override
    public ModEventPyRpcSubPacket decode(PyRpcMessage message) {
        if (!ModEventPyRpcSubPacket.CLIENT_TO_SERVER_METHOD.equals(message.getMethod())) {
            return null;
        }
        List<Object> args = message.getArguments();
        if (args.size() < 4) {
            return null;
        }

        String modName = PyRpcProtocol.asString(args.get(0));
        String systemName = PyRpcProtocol.asString(args.get(1));
        String eventName = PyRpcProtocol.asString(args.get(2));
        if (modName == null || systemName == null || eventName == null) {
            return null;
        }

        return new ModEventPyRpcSubPacket(
                ModEventPyRpcSubPacket.CLIENT_TO_SERVER_METHOD,
                modName,
                systemName,
                eventName,
                PyRpcProtocol.asStringMap(args.get(3)));
    }

    @Override
    public void encode(ModEventPyRpcSubPacket packet, PyRpcWriter writer) {
        writer.writeArrayHeader(3);
        writer.writeBinaryString(packet.getMethod());
        writer.writeArrayHeader(4);
        writer.writeBinaryString(packet.getModName());
        writer.writeBinaryString(packet.getSystemName());
        writer.writeBinaryString(packet.getEventName());
        writer.writeObject(packet.getEventData());
        writer.writeNil();
    }
}
