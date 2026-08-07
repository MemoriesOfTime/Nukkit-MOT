package cn.nukkit.network.protocol.types.biome;

import cn.nukkit.block.Block;
import lombok.Value;

@Value
public class BiomeMesaSurfaceData {

    Block clayMaterial;
    Block hardClayMaterial;
    boolean brycePillars;
    boolean hasForest;
}
