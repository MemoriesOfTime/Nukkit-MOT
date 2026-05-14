package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLightningRodBase;
import cn.nukkit.math.BlockFace;
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

    @Test
    /**
     * Verifies lightning rod facing and powered states are mapped by the palettes that include them.
     */
    void lightningRodStatesHaveRuntimeMappings() {
        assertLightningRodStatesMapped(new BlockPalette(GameVersion.V1_21_110));
        assertLightningRodStatesMapped(new BlockPalette(GameVersion.V1_26_10));
    }

    @Test
    /**
     * Verifies shelf wood variants and their 32 block states do not fall back to oak or info_update.
     */
    void shelfStatesHaveVariantSpecificRuntimeMappings() {
        assertShelfStatesMapped(new BlockPalette(GameVersion.V1_21_110));
        assertShelfStatesMapped(new BlockPalette(GameVersion.V1_26_10));
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

    private static void assertLightningRodStatesMapped(BlockPalette palette) {
        int infoUpdateRuntimeId = palette.getRuntimeId(Block.INFO_UPDATE, 0);
        int infoUpdateHashId = palette.getHashId(Block.INFO_UPDATE, 0);
        int[] ids = {
                Block.LIGHTNING_ROD,
                Block.EXPOSED_LIGHTNING_ROD,
                Block.WEATHERED_LIGHTNING_ROD,
                Block.OXIDIZED_LIGHTNING_ROD,
                Block.WAXED_LIGHTNING_ROD,
                Block.WAXED_EXPOSED_LIGHTNING_ROD,
                Block.WAXED_WEATHERED_LIGHTNING_ROD,
                Block.WAXED_OXIDIZED_LIGHTNING_ROD
        };

        for (int id : ids) {
            for (BlockFace face : BlockFace.values()) {
                int unpoweredMeta = face.getIndex();
                int poweredMeta = face.getIndex() | BlockLightningRodBase.POWERED_BIT;
                int unpoweredRuntimeId = palette.getRuntimeId(id, unpoweredMeta);
                int poweredRuntimeId = palette.getRuntimeId(id, poweredMeta);

                Assertions.assertNotEquals(infoUpdateRuntimeId, unpoweredRuntimeId);
                Assertions.assertNotEquals(infoUpdateRuntimeId, poweredRuntimeId);
                Assertions.assertNotEquals(unpoweredRuntimeId, poweredRuntimeId);
                Assertions.assertNotEquals(infoUpdateHashId, palette.getHashId(id, unpoweredMeta));
                Assertions.assertNotEquals(infoUpdateHashId, palette.getHashId(id, poweredMeta));
                Assertions.assertNotEquals(palette.getHashId(id, unpoweredMeta), palette.getHashId(id, poweredMeta));
            }
        }
    }

    private static void assertShelfStatesMapped(BlockPalette palette) {
        int infoUpdateRuntimeId = palette.getRuntimeId(Block.INFO_UPDATE, 0);
        int infoUpdateHashId = palette.getHashId(Block.INFO_UPDATE, 0);
        int[] ids = {
                Block.OAK_SHELF,
                Block.SPRUCE_SHELF,
                Block.BIRCH_SHELF,
                Block.JUNGLE_SHELF,
                Block.ACACIA_SHELF,
                Block.DARK_OAK_SHELF,
                Block.MANGROVE_SHELF,
                Block.CHERRY_SHELF,
                Block.PALE_OAK_SHELF,
                Block.BAMBOO_SHELF,
                Block.CRIMSON_SHELF,
                Block.WARPED_SHELF
        };

        for (int id : ids) {
            for (int meta = 0; meta < 32; meta++) {
                int runtimeId = palette.getRuntimeId(id, meta);
                int hashId = palette.getHashId(id, meta);
                int legacyFullId = id << Block.DATA_BITS | meta;

                Assertions.assertNotEquals(infoUpdateRuntimeId, runtimeId, "runtime fallback for " + id + ':' + meta);
                Assertions.assertNotEquals(infoUpdateHashId, hashId, "hash fallback for " + id + ':' + meta);
                Assertions.assertEquals(legacyFullId, palette.getLegacyFullId(runtimeId), "runtime reverse mapping for " + id + ':' + meta);
                Assertions.assertEquals(legacyFullId, palette.getLegacyFullIdFromHashId(hashId), "hash reverse mapping for " + id + ':' + meta);
            }
        }
    }
}
