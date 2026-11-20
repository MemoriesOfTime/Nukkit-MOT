package cn.nukkit.block;

import cn.nukkit.math.BlockFace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

/**
 * 1.26.50+ 连接规则：族互通、逐面支撑、例外表、楼梯/活板门/台阶/栅栏门状态特例。
 * <p>
 * Connection rules since 1.26.50: family links, per-face support, exception list and the
 * stairs/trapdoor/slab/fence-gate state special cases, measured against BDS 1.26.65.1.
 */
class BlockConnectionRulesTest {

    private static final BlockFace[] STAIR_SUPPORT = {
            BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH
    };
    private static final BlockFace[] TRAPDOOR_SUPPORT = {
            BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH
    };

    @BeforeAll
    static void init() {
        Block.init();
    }

    private static boolean thin(Block thin, Block neighbor, BlockFace face) {
        return ((BlockThin) thin).canConnect(neighbor, face);
    }

    private static boolean fence(Block fence, Block neighbor, BlockFace face) {
        return ((BlockFence) fence).canConnect(neighbor, face);
    }

    private static boolean wall(Block wall, Block neighbor, BlockFace face) {
        return ((BlockWall) wall).canConnect(neighbor, face);
    }

    @Test
    void thinFamilyConnectsAcrossAllPaneAndBarTypes() {
        Block[] thins = {
                Block.get(BlockID.GLASS_PANE),
                Block.get(BlockID.STAINED_GLASS_PANE, 1),
                Block.get(BlockID.HARD_GLASS_PANE),
                Block.get(BlockID.HARD_STAINED_GLASS_PANE, 1),
                Block.get(BlockID.IRON_BARS),
                Block.get(BlockID.COPPER_BARS),
                Block.get(BlockID.EXPOSED_COPPER_BARS),
        };
        for (Block from : thins) {
            for (Block to : thins) {
                Assertions.assertTrue(thin(from, to, BlockFace.NORTH),
                        from.getClass().getSimpleName() + " -> " + to.getClass().getSimpleName());
            }
            Assertions.assertTrue(thin(from, Block.get(BlockID.COBBLESTONE_WALL), BlockFace.NORTH),
                    "pane/bar connects to walls");
        }
    }

    @Test
    void thinConnectsToFullFaceButNotToOtherFamilies() {
        Block pane = Block.get(BlockID.GLASS_PANE);
        for (int id : new int[]{
                BlockID.STONE, BlockID.GLASS, BlockID.SOUL_SAND, BlockID.MUD, BlockID.STRUCTURE_VOID,
                BlockID.DOUBLE_STONE_SLAB, BlockID.BORDER_BLOCK
        }) {
            Assertions.assertTrue(thin(pane, Block.get(id), BlockFace.NORTH), "expected support from id " + id);
        }
        for (int id : new int[]{
                BlockID.LEAVES, BlockID.BARRIER, BlockID.PUMPKIN, BlockID.MELON_BLOCK, BlockID.CHEST,
                BlockID.HOPPER_BLOCK, BlockID.CAULDRON_BLOCK, BlockID.FARMLAND, BlockID.ENCHANTING_TABLE,
                BlockID.ANVIL, BlockID.STONE_SLAB, BlockID.CHAIN_BLOCK, BlockID.FENCE, BlockID.CHERRY_FENCE,
                BlockID.NETHER_BRICK_FENCE, BlockID.FENCE_GATE, BlockID.TRAPDOOR, BlockID.CHORUS_FLOWER
        }) {
            Assertions.assertFalse(thin(pane, Block.get(id), BlockFace.NORTH), "unexpected support from id " + id);
        }
    }

    @Test
    void fenceFamilyIsSplitBetweenWoodenAndNetherBrick() {
        Block oak = Block.get(BlockID.FENCE);
        Block spruce = Block.get(BlockID.FENCE, 1);
        Block cherry = Block.get(BlockID.CHERRY_FENCE);
        Block nether = Block.get(BlockID.NETHER_BRICK_FENCE);

        Assertions.assertTrue(fence(oak, spruce, BlockFace.NORTH));
        Assertions.assertTrue(fence(oak, cherry, BlockFace.NORTH));
        Assertions.assertTrue(fence(nether, nether, BlockFace.NORTH));

        Assertions.assertFalse(fence(oak, nether, BlockFace.NORTH));
        Assertions.assertFalse(fence(nether, oak, BlockFace.NORTH));
    }

    @Test
    void fenceConnectsToGatesOnlyOnTheGatePanelAxis() {
        Block fence = Block.get(BlockID.FENCE);
        Block gate = Block.get(BlockID.FENCE_GATE, 0); // facing south -> panel axis X
        Assertions.assertTrue(fence(fence, gate, BlockFace.EAST));
        Assertions.assertTrue(fence(fence, gate, BlockFace.WEST));
        Assertions.assertFalse(fence(fence, gate, BlockFace.NORTH));
        Assertions.assertFalse(fence(fence, gate, BlockFace.SOUTH));

        Block eastGate = Block.get(BlockID.FENCE_GATE, 3); // facing east -> panel axis Z
        Assertions.assertTrue(fence(fence, eastGate, BlockFace.NORTH));
        Assertions.assertTrue(fence(fence, eastGate, BlockFace.SOUTH));
        Assertions.assertFalse(fence(fence, eastGate, BlockFace.EAST));
    }

    @Test
    void fenceSupportRules() {
        Block fence = Block.get(BlockID.FENCE);
        for (int id : new int[]{
                BlockID.STONE, BlockID.GLASS, BlockID.SOUL_SAND, BlockID.MUD, BlockID.STRUCTURE_VOID,
                BlockID.DOUBLE_STONE_SLAB
        }) {
            Assertions.assertTrue(fence(fence, Block.get(id), BlockFace.NORTH), "expected support from id " + id);
        }
        for (int id : new int[]{
                BlockID.LEAVES, BlockID.BARRIER, BlockID.PUMPKIN, BlockID.MELON_BLOCK, BlockID.CHEST,
                BlockID.ANVIL, BlockID.STONE_SLAB, BlockID.CHAIN_BLOCK, BlockID.GLASS_PANE, BlockID.IRON_BARS,
                BlockID.COBBLESTONE_WALL, BlockID.TRAPDOOR, BlockID.BORDER_BLOCK
        }) {
            Assertions.assertFalse(fence(fence, Block.get(id), BlockFace.NORTH), "unexpected support from id " + id);
        }
    }

    @Test
    void wallFamilyRules() {
        Block wall = Block.get(BlockID.COBBLESTONE_WALL);
        Assertions.assertTrue(wall(wall, Block.get(BlockID.COBBLESTONE_WALL), BlockFace.NORTH));
        Assertions.assertTrue(wall(wall, Block.get(BlockID.GLASS_PANE), BlockFace.NORTH));
        Assertions.assertTrue(wall(wall, Block.get(BlockID.IRON_BARS), BlockFace.NORTH));
        Assertions.assertFalse(wall(wall, Block.get(BlockID.FENCE), BlockFace.NORTH));
        Assertions.assertFalse(wall(wall, Block.get(BlockID.PUMPKIN), BlockFace.NORTH));
        Assertions.assertFalse(wall(wall, Block.get(BlockID.BARRIER), BlockFace.NORTH));
        Assertions.assertTrue(wall(wall, Block.get(BlockID.BORDER_BLOCK), BlockFace.NORTH));

        Block gate = Block.get(BlockID.FENCE_GATE, 0); // facing south -> panel axis X
        Assertions.assertTrue(wall(wall, gate, BlockFace.EAST));
        Assertions.assertFalse(wall(wall, gate, BlockFace.NORTH));
    }

    @Test
    void stairsOnlySupportTheirFullBackFace() {
        Block pane = Block.get(BlockID.GLASS_PANE);
        for (int meta = 0; meta < 4; meta++) {
            Block stairs = Block.get(BlockID.WOODEN_STAIRS, meta);
            for (BlockFace face : BlockFace.Plane.HORIZONTAL) {
                Assertions.assertEquals(face == STAIR_SUPPORT[meta], thin(pane, stairs, face),
                        "stairs meta " + meta + " face " + face);
                Assertions.assertEquals(face == STAIR_SUPPORT[meta], fence(Block.get(BlockID.FENCE), stairs, face),
                        "fence vs stairs meta " + meta + " face " + face);
            }
        }
    }

    @Test
    void trapdoorOnlySupportsItsOpenFlushFace() {
        Block pane = Block.get(BlockID.GLASS_PANE);
        for (int meta = 0; meta < 4; meta++) {
            Block closed = Block.get(BlockID.TRAPDOOR, meta);
            Block open = Block.get(BlockID.TRAPDOOR, meta | BlockTrapdoor.TRAPDOOR_OPEN_BIT);
            for (BlockFace face : BlockFace.Plane.HORIZONTAL) {
                Assertions.assertFalse(thin(pane, closed, face), "closed trapdoor " + meta + " face " + face);
                Assertions.assertEquals(face == TRAPDOOR_SUPPORT[meta], thin(pane, open, face),
                        "open trapdoor " + meta + " face " + face);
            }
        }
    }

    @Test
    void singleSlabsDoNotSupportWhileDoubleSlabsDo() {
        Block pane = Block.get(BlockID.GLASS_PANE);
        Assertions.assertFalse(thin(pane, Block.get(BlockID.STONE_SLAB), BlockFace.NORTH));
        Assertions.assertTrue(thin(pane, Block.get(BlockID.DOUBLE_STONE_SLAB), BlockFace.NORTH));
    }

    @Test
    void fenceGateInWallTracksThePerpendicularWalls() {
        Block wall = Block.get(BlockID.COBBLESTONE_WALL);
        Block fence = Block.get(BlockID.FENCE);

        TestGate southGate = new TestGate(0); // facing south -> side walls east/west
        southGate.setSide(BlockFace.EAST, wall);
        southGate.setSide(BlockFace.WEST, wall);
        Assertions.assertTrue(southGate.updateInWall());
        Assertions.assertNotEquals(0, southGate.getDamage() & BlockFenceGate.IN_WALL_BIT);
        Assertions.assertFalse(southGate.updateInWall(), "must be idempotent");

        TestGate facingWallsOnly = new TestGate(0);
        facingWallsOnly.setSide(BlockFace.NORTH, wall);
        facingWallsOnly.setSide(BlockFace.SOUTH, wall);
        Assertions.assertFalse(facingWallsOnly.updateInWall());

        TestGate fenceOnly = new TestGate(0);
        fenceOnly.setSide(BlockFace.EAST, fence);
        fenceOnly.setSide(BlockFace.WEST, fence);
        Assertions.assertFalse(fenceOnly.updateInWall(), "only walls set in_wall");

        TestGate eastGate = new TestGate(3); // facing east -> side walls north/south
        eastGate.setSide(BlockFace.NORTH, wall);
        eastGate.setSide(BlockFace.SOUTH, wall);
        Assertions.assertTrue(eastGate.updateInWall());
        Assertions.assertNotEquals(0, eastGate.getDamage() & BlockFenceGate.IN_WALL_BIT);
    }

    @Test
    void chainHasNoConnectionsAndRotatesItsCollisionBox() {
        Block pane = Block.get(BlockID.GLASS_PANE);
        Block chain = Block.get(BlockID.CHAIN_BLOCK);
        Assertions.assertFalse(chain.isSolid());
        for (BlockFace face : BlockFace.Plane.HORIZONTAL) {
            Assertions.assertFalse(thin(pane, chain, face));
            Assertions.assertFalse(fence(Block.get(BlockID.FENCE), chain, face));
            Assertions.assertFalse(wall(Block.get(BlockID.COBBLESTONE_WALL), chain, face));
        }

        double lo = 6.5 / 16.0;
        double hi = 9.5 / 16.0;
        Assertions.assertEquals(lo, chain.getMinX(), 1e-9);
        Assertions.assertEquals(hi, chain.getMaxX(), 1e-9);
        Assertions.assertEquals(0.0, chain.getMinY(), 1e-9);
        Assertions.assertEquals(1.0, chain.getMaxY(), 1e-9);
        Assertions.assertEquals(lo, chain.getMinZ(), 1e-9);
        Assertions.assertEquals(hi, chain.getMaxZ(), 1e-9);

        Block xChain = Block.get(BlockID.CHAIN_BLOCK, 1);
        Assertions.assertEquals(0.0, xChain.getMinX(), 1e-9);
        Assertions.assertEquals(1.0, xChain.getMaxX(), 1e-9);
        Assertions.assertEquals(lo, xChain.getMinY(), 1e-9);
        Assertions.assertEquals(hi, xChain.getMaxY(), 1e-9);
        Assertions.assertEquals(lo, xChain.getMinZ(), 1e-9);
        Assertions.assertEquals(hi, xChain.getMaxZ(), 1e-9);

        Block zChain = Block.get(BlockID.CHAIN_BLOCK, 2);
        Assertions.assertEquals(lo, zChain.getMinX(), 1e-9);
        Assertions.assertEquals(hi, zChain.getMaxX(), 1e-9);
        Assertions.assertEquals(lo, zChain.getMinY(), 1e-9);
        Assertions.assertEquals(hi, zChain.getMaxY(), 1e-9);
        Assertions.assertEquals(0.0, zChain.getMinZ(), 1e-9);
        Assertions.assertEquals(1.0, zChain.getMaxZ(), 1e-9);
    }

    @Test
    void legacyCanConnectOverridesStillDriveConnections() {
        LegacyOverrideFence fence = new LegacyOverrideFence();
        Assertions.assertFalse(fence.updateConnections(), "a fence override must veto every arm");
        Assertions.assertEquals(0, fence.getDamage() & BlockFence.CONNECTION_FLAGS);

        LegacyOverridePane pane = new LegacyOverridePane();
        Assertions.assertFalse(pane.updateConnections(), "a pane override must veto every arm");
        Assertions.assertEquals(0, pane.getDamage() & BlockThin.CONNECTION_FLAGS);

        LegacyOverrideWall wall = new LegacyOverrideWall();
        wall.autoConfigureState();
        Assertions.assertFalse(wall.hasConnections(), "a wall override must veto every connection");
        Assertions.assertTrue(wall.isWallPost());
    }

    private static final class LegacyOverrideFence extends BlockFence {
        @Override
        public boolean canConnect(Block block) {
            return false;
        }

        @Override
        public Block getSideAtLayer(int layer, BlockFace face, int step) {
            return Block.get(BlockID.STONE);
        }
    }

    private static final class LegacyOverridePane extends BlockGlassPane {
        @Override
        public boolean canConnect(Block block) {
            return false;
        }

        @Override
        public Block getSideAtLayer(int layer, BlockFace face, int step) {
            return Block.get(BlockID.STONE);
        }
    }

    private static final class LegacyOverrideWall extends BlockWall {
        @Override
        public boolean canConnect(Block block) {
            return false;
        }

        @Override
        public Block getSideAtLayer(int layer, BlockFace face) {
            return face.getAxis() == BlockFace.Axis.Y ? Block.get(BlockID.AIR) : Block.get(BlockID.STONE);
        }
    }

    private static final class TestGate extends BlockFenceGate {
        private final Map<BlockFace, Block> sides = new EnumMap<>(BlockFace.class);

        private TestGate(int meta) {
            super(meta);
        }

        private void setSide(BlockFace face, Block block) {
            this.sides.put(face, block);
        }

        @Override
        public Block getSide(BlockFace face) {
            return this.sides.getOrDefault(face, Block.get(BlockID.AIR));
        }
    }
}
