package cn.nukkit.network.protocol.serializer;

import cn.nukkit.network.protocol.DataPacket;

public interface DataPacketSerializer<T extends DataPacket> {

    void serialize(T packet);

    void deserialize(T packet);
}