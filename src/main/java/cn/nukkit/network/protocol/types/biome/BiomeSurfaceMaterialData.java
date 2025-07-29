package cn.nukkit.network.protocol.types.biome;

import cn.nukkit.block.Block;
import lombok.Value;

@Value
public class BiomeSurfaceMaterialData {

    Block topBlock;
    Block midBlock;
    Block seaFloorBlock;
    Block foundationBlock;
    Block seaBlock;
    int seaFloorDepth;
}
