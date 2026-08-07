package cn.nukkit.block.properties.enums;

import cn.nukkit.math.BlockFace;

public enum CrafterOrientation {
    DOWN_EAST("down_east", BlockFace.DOWN),
    DOWN_NORTH("down_north", BlockFace.DOWN),
    DOWN_SOUTH("down_south", BlockFace.DOWN),
    DOWN_WEST("down_west", BlockFace.DOWN),
    EAST_UP("east_up", BlockFace.EAST),
    NORTH_UP("north_up", BlockFace.NORTH),
    SOUTH_UP("south_up", BlockFace.SOUTH),
    UP_EAST("up_east", BlockFace.UP),
    UP_NORTH("up_north", BlockFace.UP),
    UP_SOUTH("up_south", BlockFace.UP),
    UP_WEST("up_west", BlockFace.UP),
    WEST_UP("west_up", BlockFace.WEST);

    private final String name;
    private final BlockFace primaryFace;

    CrafterOrientation(String name, BlockFace primaryFace) {
        this.name = name;
        this.primaryFace = primaryFace;
    }

    public BlockFace getPrimaryFace() {
        return this.primaryFace;
    }

    public static CrafterOrientation byFaces(BlockFace primary, BlockFace secondary) {
        return switch (primary) {
            case DOWN -> switch (secondary) {
                case NORTH -> DOWN_NORTH;
                case SOUTH -> DOWN_SOUTH;
                case WEST -> DOWN_WEST;
                default -> DOWN_EAST;
            };
            case EAST -> EAST_UP;
            case NORTH -> NORTH_UP;
            case SOUTH -> SOUTH_UP;
            case UP -> switch (secondary) {
                case NORTH -> UP_NORTH;
                case SOUTH -> UP_SOUTH;
                case WEST -> UP_WEST;
                default -> UP_EAST;
            };
            case WEST -> WEST_UP;
        };
    }

    @Override
    public String toString() {
        return this.name;
    }
}
