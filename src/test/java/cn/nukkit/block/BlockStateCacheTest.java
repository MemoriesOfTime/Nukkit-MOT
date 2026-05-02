package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.utils.BlockColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockStateCacheTest {

    @BeforeAll
    /**
     * Boots the shared mock server once so block registries are available to all tests in this class.
     */
    static void initServer() {
        MockServer.init();
    }

    @Test
    /**
     * Verifies that the sparse cache only allocates entries for states that have actually been requested.
     */
    void sparseCacheOnlyStoresObservedStates() {
        Assertions.assertTrue(Block.getCachedStateCountForTesting(Block.PLANKS) > 0);
        Assertions.assertTrue(Block.getCachedStateCountForTesting(Block.PLANKS) < Block.DATA_SIZE);

        Assertions.assertTrue(Block.getCachedStateCountForTesting(Block.WATER) > 0);
        Assertions.assertTrue(Block.getCachedStateCountForTesting(Block.WATER) < Block.DATA_SIZE);
    }

    @Test
    /**
     * Ensures cached prototypes are cloned so callers still receive independent mutable block instances.
     */
    void cachedStatesStillReturnIndependentInstances() {
        Block first = Block.get(Block.WATER, 0);
        Block second = Block.get(Block.WATER, 0);

        Assertions.assertNotSame(first, second);

        first.setDamage(5);

        Assertions.assertEquals(0, second.getDamage());
        Assertions.assertEquals(0, Block.get(Block.WATER, 0).getDamage());
    }

    @Test
    /**
     * Confirms that an invalid normalized meta value still resolves to {@link BlockUnknown}.
     */
    void invalidNormalizedMetaStillReturnsUnknown() {
        Block invalid = Block.get(Block.PLANKS, 6);
        Block valid = Block.get(Block.PLANKS, 5);

        Assertions.assertInstanceOf(BlockUnknown.class, invalid);
        Assertions.assertFalse(valid instanceof BlockUnknown);
        Assertions.assertEquals(5, valid.getDamage());
    }

    @Test
    /**
     * Ensures `Block.get(id)` preserves the historical `meta 0` default state instead of cloning the no-arg prototype.
     */
    void getWithoutMetaUsesMetaZeroState() {
        Block tallGrass = Block.get(Block.TALL_GRASS);

        Assertions.assertEquals(0, tallGrass.getDamage());
    }

    @Test
    /**
     * Ensures copper lantern variants are registered and keep the hanging bit as block meta.
     */
    void copperLanternVariantsAreRegisteredAndPreserveHangingState() {
        assertCopperLantern(Block.COPPER_LANTERN, BlockCopperLantern.class, "Copper Lantern", OxidizationLevel.UNAFFECTED, false);
        assertCopperLantern(Block.EXPOSED_COPPER_LANTERN, BlockCopperLanternExposed.class, "Exposed Copper Lantern", OxidizationLevel.EXPOSED, false);
        assertCopperLantern(Block.WEATHERED_COPPER_LANTERN, BlockCopperLanternWeathered.class, "Weathered Copper Lantern", OxidizationLevel.WEATHERED, false);
        assertCopperLantern(Block.OXIDIZED_COPPER_LANTERN, BlockCopperLanternOxidized.class, "Oxidized Copper Lantern", OxidizationLevel.OXIDIZED, false);
        assertCopperLantern(Block.WAXED_COPPER_LANTERN, BlockCopperLanternWaxed.class, "Waxed Copper Lantern", OxidizationLevel.UNAFFECTED, true);
        assertCopperLantern(Block.WAXED_EXPOSED_COPPER_LANTERN, BlockCopperLanternExposedWaxed.class, "Waxed Exposed Copper Lantern", OxidizationLevel.EXPOSED, true);
        assertCopperLantern(Block.WAXED_WEATHERED_COPPER_LANTERN, BlockCopperLanternWeatheredWaxed.class, "Waxed Weathered Copper Lantern", OxidizationLevel.WEATHERED, true);
        assertCopperLantern(Block.WAXED_OXIDIZED_COPPER_LANTERN, BlockCopperLanternOxidizedWaxed.class, "Waxed Oxidized Copper Lantern", OxidizationLevel.OXIDIZED, true);
    }

    @Test
    /**
     * Verifies oxidation state conversion preserves the copper lantern hanging state.
     */
    void copperLanternOxidationMappingPreservesHangingState() {
        BlockCopperLanternBase copperLantern = (BlockCopperLanternBase) Block.get(Block.COPPER_LANTERN, BlockLantern.HANGING_BIT);
        BlockCopperLanternBase waxedCopperLantern = (BlockCopperLanternBase) Block.get(Block.WAXED_COPPER_LANTERN, BlockLantern.HANGING_BIT);

        Block exposed = copperLantern.getStateWithOxidizationLevel(OxidizationLevel.EXPOSED);
        Block waxedExposed = waxedCopperLantern.getStateWithOxidizationLevel(OxidizationLevel.EXPOSED);

        Assertions.assertInstanceOf(BlockCopperLanternExposed.class, exposed);
        Assertions.assertInstanceOf(BlockCopperLanternExposedWaxed.class, waxedExposed);
        Assertions.assertEquals(BlockLantern.HANGING_BIT, exposed.getDamage());
        Assertions.assertEquals(BlockLantern.HANGING_BIT, waxedExposed.getDamage());
        Assertions.assertTrue(((BlockLantern) exposed).isHanging());
        Assertions.assertTrue(((BlockLantern) waxedExposed).isHanging());
    }

    private static void assertCopperLantern(int id, Class<? extends BlockCopperLanternBase> type, String name,
                                           OxidizationLevel oxidizationLevel, boolean waxed) {
        Block ground = Block.get(id, 0);
        Block hanging = Block.get(id, BlockLantern.HANGING_BIT);

        Assertions.assertInstanceOf(type, ground);
        Assertions.assertInstanceOf(type, hanging);
        Assertions.assertFalse(ground instanceof BlockUnknown);
        Assertions.assertFalse(hanging instanceof BlockUnknown);
        Assertions.assertEquals(id, ground.getId());
        Assertions.assertEquals(id, hanging.getId());
        Assertions.assertEquals(name, ground.getName());
        Assertions.assertEquals(0, ground.getDamage());
        Assertions.assertEquals(BlockLantern.HANGING_BIT, hanging.getDamage());
        Assertions.assertFalse(((BlockLantern) ground).isHanging());
        Assertions.assertTrue(((BlockLantern) hanging).isHanging());
        Assertions.assertEquals(oxidizationLevel, ((BlockCopperLanternBase) ground).getOxidizationLevel());
        Assertions.assertEquals(waxed, ((BlockCopperLanternBase) ground).isWaxed());
        Assertions.assertEquals(BlockColor.IRON_BLOCK_COLOR, ground.getColor());
        Assertions.assertEquals(BlockColor.IRON_BLOCK_COLOR, hanging.getColor());
    }
}
