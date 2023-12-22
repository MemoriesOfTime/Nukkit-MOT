package cn.nukkit.network.serializer;

import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.BinaryStream;

public interface BedrockPacketSerializer<T extends DataPacket> {

    void serialize(BinaryStream stream, T packet);

    void deserialize(BinaryStream stream, T packet);
}