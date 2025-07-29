package cn.nukkit.block.properties;

import cn.nukkit.block.BlockWall;
import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.block.custom.properties.BooleanBlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.block.properties.enums.CrackedState;
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

    EnumBlockProperty<CrackedState> CRACKED_STATE = new EnumBlockProperty<>("cracked_state", false, CrackedState.class);

    BlockProperty<BlockWall.WallConnectionType> WALL_CONNECTION_TYPE_EAST = new EnumBlockProperty<>("wall_connection_type_east",
            false,
            new BlockWall.WallConnectionType[] {
                    BlockWall.WallConnectionType.NONE,
                    BlockWall.WallConnectionType.SHORT,
                    BlockWall.WallConnectionType.TALL});

    BlockProperty<BlockWall.WallConnectionType> WALL_CONNECTION_TYPE_NORTH = new EnumBlockProperty<>("wall_connection_type_north",
            false,
            new BlockWall.WallConnectionType[] {
                    BlockWall.WallConnectionType.NONE,
                    BlockWall.WallConnectionType.SHORT,
                    BlockWall.WallConnectionType.TALL});

    BlockProperty<BlockWall.WallConnectionType> WALL_CONNECTION_TYPE_SOUTH = new EnumBlockProperty<>("wall_connection_type_south",
            false,
            new BlockWall.WallConnectionType[] {
                    BlockWall.WallConnectionType.NONE,
                    BlockWall.WallConnectionType.SHORT,
                    BlockWall.WallConnectionType.TALL});

    BlockProperty<BlockWall.WallConnectionType> WALL_CONNECTION_TYPE_WEST = new EnumBlockProperty<>("wall_connection_type_west",
            false,
            new BlockWall.WallConnectionType[] {
                    BlockWall.WallConnectionType.NONE,
                    BlockWall.WallConnectionType.SHORT,
                    BlockWall.WallConnectionType.TALL});

    BooleanBlockProperty WALL_POST = new BooleanBlockProperty("wall_post_bit", false);

}
