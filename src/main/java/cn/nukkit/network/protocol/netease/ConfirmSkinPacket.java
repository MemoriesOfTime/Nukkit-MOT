package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@OnlyNetEase
@ToString
public class ConfirmSkinPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PACKET_CONFIRM_SKIN;

    public List<UUID> uuids = new ObjectArrayList<>();

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        return (byte) NETWORK_ID;
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putArray(this.uuids, (stream, value) -> {
            stream.putBoolean(true);
            stream.putUUID(value);
            stream.putString("");
        });
    }
}
