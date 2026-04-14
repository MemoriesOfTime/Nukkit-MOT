package cn.nukkit.network.protocol;

import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.network.protocol.types.DimensionDefinition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ToString
public class DimensionDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.DIMENSION_DATA_PACKET;

    public final List<DimensionDefinition> definitions = new ObjectArrayList<>();

    /**
     * Creates a packet containing only modified dimension definitions.
     * Returns null if no dimensions have been modified.
     */
    @Nullable
    public static DimensionDataPacket createIfModified() {
        DimensionDataPacket packet = new DimensionDataPacket();
        for (DimensionEnum dimension : DimensionEnum.values()) {
            if (!dimension.isVanillaBounds()) {
                packet.definitions.add(fromDimensionEnum(dimension));
            }
        }
        return packet.definitions.isEmpty() ? null : packet;
    }

    private static DimensionDefinition fromDimensionEnum(DimensionEnum dimension) {
        var dimensionData = dimension.getDimensionData();
        return new DimensionDefinition(
                dimension.getIdentifier(),
                // Bedrock dimension definitions use an open upper bound.
                dimensionData.getMaxHeight() + 1,
                dimensionData.getMinHeight(),
                getGeneratorType(dimension)
        );
    }

    private static int getGeneratorType(DimensionEnum dimension) {
        return dimension.isVanillaBounds() ? dimension.getGeneratorType() : Generator.TYPE_VOID;
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putArray(this.definitions, dimensionDefinition -> {
            this.putString(dimensionDefinition.getId());
            this.putVarInt(dimensionDefinition.getMaximumHeight());
            this.putVarInt(dimensionDefinition.getMinimumHeight());
            this.putVarInt(dimensionDefinition.getGeneratorType());
        });
    }
}
