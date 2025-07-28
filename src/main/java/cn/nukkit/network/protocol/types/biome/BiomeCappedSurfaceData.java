package cn.nukkit.network.protocol.types.biome;

import cn.nukkit.block.Block;
import lombok.Value;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

@Value
public class BiomeCappedSurfaceData {
    List<Block> floorBlocks;
    List<Block> ceilingBlocks;
    @Nullable
    Block seaBlock;
    @Nullable
    Block foundationBlock;
    @Nullable
    Block beachBlock;
}
