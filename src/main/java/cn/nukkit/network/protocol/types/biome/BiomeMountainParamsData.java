package cn.nukkit.network.protocol.types.biome;

import cn.nukkit.block.Block;
import lombok.Value;

@Value
public class BiomeMountainParamsData {

    Block steepBlock;
    boolean northSlopes;
    boolean southSlopes;
    boolean westSlopes;
    boolean eastSlopes;
    boolean topSlideEnabled;
}
