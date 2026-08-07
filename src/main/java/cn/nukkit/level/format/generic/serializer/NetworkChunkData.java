package cn.nukkit.level.format.generic.serializer;

import cn.nukkit.GameVersion;
import cn.nukkit.level.DimensionData;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NetworkChunkData {
    private GameVersion gameVersion;
    private int chunkSections;
    private boolean antiXray;
    private final DimensionData dimensionData;
}
