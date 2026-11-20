package cn.nukkit.block;

import cn.nukkit.math.BlockFace;

/**
 * 1.26.50+ 原版连接支撑判定，按官方 BDS 1.26.65.1 实测。
 * <p>
 * Vanilla connection support rules since 1.26.50, measured against BDS 1.26.65.1.
 */
final class ConnectionSupport {

    /**
     * 开启活板门按 direction 贴边支撑的面 / Open trapdoor flush face per direction.
     */
    private static final BlockFace[] TRAPDOOR_OPEN_FACE = {
            BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH
    };

    private ConnectionSupport() {
    }

    /**
     * 邻居朝向本方块的那一面是否提供完整连接支撑。
     * <p>
     * Whether the neighbour's face towards this block provides full connection support.
     *
     * @param neighbor     相邻方块 / the neighbouring block
     * @param neighborFace 邻居朝向本方块的那一面 / the neighbour's face touching this block
     */
    static boolean providesFullSupport(Block neighbor, BlockFace neighborFace) {
        if (isExcepted(neighbor)) {
            return false;
        }
        if (neighbor instanceof BlockStairs stairs) {
            // 楼梯只认整高背面，corner/upside_down 不影响水平连接
            // Stairs only expose their full-height back face; corner/upside_down do not matter
            return stairs.getStairsFacing() == neighborFace;
        }
        if (neighbor instanceof BlockTrapdoor trapdoor) {
            return trapdoor.isOpen()
                    && TRAPDOOR_OPEN_FACE[trapdoor.getDamage() & BlockTrapdoor.DIRECTION_MASK] == neighborFace;
        }
        if (neighbor instanceof BlockSlab) {
            // 双层台阶是独立的 BlockDoubleSlab，不继承 BlockSlab
            // Double slabs are separate BlockDoubleSlab blocks, not BlockSlab subclasses
            return false;
        }
        if (neighbor instanceof BlockThin || neighbor instanceof BlockWall
                || neighbor instanceof BlockFence || neighbor instanceof BlockFenceGate) {
            // 家族互连由各 canConnect 处理；这里不能走 bbox，否则 fence/pane 的
            // recalculateBoundingBox 会互相递归
            // Family links are handled by each canConnect; going through the bounding box here
            // would recurse between fence and pane recalculateBoundingBox
            return false;
        }
        if (neighbor instanceof BlockSoulSand || neighbor instanceof BlockMud
                || neighbor instanceof BlockStructureVoid || neighbor instanceof BlockBorder) {
            // 这些方块在原版按整格接受连接（BDS 实测；border_block 只有栅栏不连，由 BlockFence 单独排除）
            // Vanilla accepts connections from these as full blocks (measured on BDS; border_block is
            // only refused by fences, excluded separately in BlockFence)
            return true;
        }
        return neighbor.isSolid(neighborFace);
    }

    private static boolean isExcepted(Block block) {
        return block instanceof BlockLeaves
                || block instanceof BlockBarrier
                || block instanceof BlockPumpkin
                || block instanceof BlockMelon
                || block instanceof BlockShulkerBox
                || block instanceof BlockDoor
                || block instanceof BlockHopper
                || block instanceof BlockCauldron
                || block instanceof BlockPistonHead
                || block instanceof BlockEndPortal
                || block instanceof BlockEndGateway
                || block instanceof BlockDragonEgg
                || block instanceof BlockStructureBlock
                || block instanceof BlockCamera
                || block instanceof BlockConduit
                || block instanceof BlockGrindstone
                || block instanceof BlockPowderSnow
                || block instanceof BlockSculkSensor
                || block instanceof BlockSculkShrieker
                || block instanceof BlockCaveVines
                || block instanceof BlockDripleafBig
                || block instanceof BlockDecoratedPot
                || block instanceof BlockDriedGhast
                || block instanceof BlockCopperGolemStatue
                || block instanceof BlockChorusPlant
                || block instanceof BlockChorusFlower;
    }

    /**
     * 旧布尔 API 的方向推断：相邻方块取邻居朝向 {@code base} 的面，同坐标时返回 null。
     * <p>
     * Direction inference for the legacy boolean API: adjacent blocks use the neighbour's face
     * towards {@code base}; co-located prototypes return null.
     */
    static BlockFace faceFromNeighbor(Block base, Block neighbor) {
        double dx = neighbor.x - base.x;
        double dz = neighbor.z - base.z;
        if (dx > 0) {
            return BlockFace.WEST;
        }
        if (dx < 0) {
            return BlockFace.EAST;
        }
        if (dz > 0) {
            return BlockFace.NORTH;
        }
        if (dz < 0) {
            return BlockFace.SOUTH;
        }
        return null;
    }
}
