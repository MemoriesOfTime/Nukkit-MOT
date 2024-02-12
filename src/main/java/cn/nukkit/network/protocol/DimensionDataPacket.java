package cn.nukkit.network.protocol;

import cn.nukkit.level.DimensionData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

@ToString
public class DimensionDataPacket extends DataPacket {
    private final List<DimensionData> definitions = new ObjectArrayList<>();

    @Override
    public byte pid() {
        return ProtocolInfo.DIMENSION_DATA_PACKET;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {

    }
}
