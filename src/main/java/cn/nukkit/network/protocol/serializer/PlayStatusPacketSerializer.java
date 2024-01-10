package cn.nukkit.network.protocol.serializer;

import cn.nukkit.network.protocol.PlayStatusPacket;

/**
 * @author LT_Name
 */
public class PlayStatusPacketSerializer implements DataPacketSerializer<PlayStatusPacket> {
    @Override
    public void serialize(PlayStatusPacket packet) {
        packet.reset();
        packet.putInt(packet.status);
    }

    @Override
    public void deserialize(PlayStatusPacket packet) {
        packet.status = packet.getInt();
    }
}
