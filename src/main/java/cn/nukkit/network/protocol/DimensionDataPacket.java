package cn.nukkit.network.protocol;

import cn.nukkit.level.DimensionData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

@ToString
public class DimensionDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.DIMENSION_DATA_PACKET;

    private final List<DimensionData> definitions = new ObjectArrayList<>();

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {

    }
}
