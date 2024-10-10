package cn.nukkit.block.properties;

import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.block.custom.properties.BooleanBlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.math.BlockFace;

public interface VanillaProperties {

    BooleanBlockProperty UPPER_BLOCK = new BooleanBlockProperty("upper_block_bit", false);

    BlockProperty<BlockFace> DIRECTION = new EnumBlockProperty<>("direction", false,
            new BlockFace[]{ BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST }).ordinal(true);

    BlockProperty<BlockFace> CARDINAL_DIRECTION = new EnumBlockProperty<>("minecraft:cardinal_direction", false,
            new BlockFace[]{ BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST }).ordinal(true);

    BlockProperty<BlockFace> FACING_DIRECTION = new EnumBlockProperty<>("facing_direction", false,
            new BlockFace[] { BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST }).ordinal(true);

    BlockProperty<BlockFace> STAIRS_DIRECTION = new EnumBlockProperty<>("weirdo_direction", false,
            new BlockFace[]{ BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH }).ordinal(true);
}
