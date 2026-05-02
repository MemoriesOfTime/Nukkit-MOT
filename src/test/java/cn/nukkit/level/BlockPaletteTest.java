package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockPaletteTest {

    @BeforeAll
    /**
     * Boots the shared mock server once so palette construction uses initialized registries.
     */
    static void initServer() {
        MockServer.init();
    }

    @Test
    /**
     * Verifies that a freshly built palette remains locked until the caller explicitly clears it.
     */
    void constructorLocksPaletteUntilItIsCleared() {
        BlockPalette palette = new BlockPalette(GameVersion.getFeatureVersion());
        CompoundTag customState = new CompoundTag()
                .putString("name", "nukkit:test_block")
                .putCompound("states", new CompoundTag());

        Assertions.assertThrows(IllegalStateException.class, () -> palette.registerState(1, 0, 0, customState));

        palette.clearStates();

        Assertions.assertDoesNotThrow(() -> palette.registerState(1, 0, 0, customState));
        Assertions.assertEquals(0, palette.getRuntimeId(1, 0));
    }

    @Test
    /**
     * Verifies that hash-id and block-state reverse lookups share the same canonical mapping.
     */
    void hashIdReverseLookupUsesTheStateHashMapping() {
        BlockPalette palette = new BlockPalette(GameVersion.getFeatureVersion());
        CompoundTag customState = new CompoundTag()
                .putString("name", "nukkit:test_block")
                .putCompound("states", new CompoundTag().putBoolean("lit", true));

        palette.clearStates();
        palette.registerState(1, 0, 123, customState);

        int fullId = 1 << cn.nukkit.block.Block.DATA_BITS;
        int hashId = palette.getHashId(1, 0);

        Assertions.assertEquals(fullId, palette.getLegacyFullIdFromHashId(hashId));
        Assertions.assertEquals(fullId, palette.getLegacyFullId(customState));
    }

    @Test
    /**
     * Verifies that crafter states using the triggered bit are mapped instead of falling back to data 0.
     */
    void crafterTriggeredStatesHaveRuntimeMappings() {
        assertCrafterTriggeredStateMapped(new BlockPalette(GameVersion.getLastVersion()));
        assertCrafterTriggeredStateMapped(new BlockPalette(GameVersion.V1_21_50_NETEASE));
    }

    @Test
    /**
     * Verifies copper lantern ground and hanging states are mapped by the palettes that include them.
     */
    void copperLanternStatesHaveRuntimeMappings() {
        assertCopperLanternStatesMapped(new BlockPalette(GameVersion.V1_21_110));
        assertCopperLanternStatesMapped(new BlockPalette(GameVersion.V1_26_10));
    }

    private static void assertCrafterTriggeredStateMapped(BlockPalette palette) {
        int defaultRuntimeId = palette.getRuntimeId(Block.CRAFTER, 0);
        int triggeredCraftingRuntimeId = palette.getRuntimeId(Block.CRAFTER, 0x37);

        Assertions.assertNotEquals(defaultRuntimeId, triggeredCraftingRuntimeId);
    }

    private static void assertCopperLanternStatesMapped(BlockPalette palette) {
        int infoUpdateRuntimeId = palette.getRuntimeId(Block.INFO_UPDATE, 0);
        int infoUpdateHashId = palette.getHashId(Block.INFO_UPDATE, 0);
        int[] ids = {
                Block.COPPER_LANTERN,
                Block.EXPOSED_COPPER_LANTERN,
                Block.WEATHERED_COPPER_LANTERN,
                Block.OXIDIZED_COPPER_LANTERN,
                Block.WAXED_COPPER_LANTERN,
                Block.WAXED_EXPOSED_COPPER_LANTERN,
                Block.WAXED_WEATHERED_COPPER_LANTERN,
                Block.WAXED_OXIDIZED_COPPER_LANTERN
        };

        for (int id : ids) {
            int groundRuntimeId = palette.getRuntimeId(id, 0);
            int hangingRuntimeId = palette.getRuntimeId(id, 1);

            Assertions.assertNotEquals(infoUpdateRuntimeId, groundRuntimeId);
            Assertions.assertNotEquals(infoUpdateRuntimeId, hangingRuntimeId);
            Assertions.assertNotEquals(groundRuntimeId, hangingRuntimeId);
            Assertions.assertNotEquals(infoUpdateHashId, palette.getHashId(id, 0));
            Assertions.assertNotEquals(infoUpdateHashId, palette.getHashId(id, 1));
        }
    }
}
