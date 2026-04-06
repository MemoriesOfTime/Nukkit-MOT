package cn.nukkit.block;

import cn.nukkit.MockServer;
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
}
