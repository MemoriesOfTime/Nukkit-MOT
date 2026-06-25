package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLightningRodBase;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

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
        assertCopperLanternStatesMapped(new BlockPalette(GameVersion.V1_21_111));
        assertCopperLanternStatesMapped(new BlockPalette(GameVersion.V1_26_10));
    }

    @Test
    /**
     * Verifies lightning rod facing and powered states are mapped by the palettes that include them.
     */
    void lightningRodStatesHaveRuntimeMappings() {
        assertLightningRodStatesMapped(new BlockPalette(GameVersion.V1_21_111));
        assertLightningRodStatesMapped(new BlockPalette(GameVersion.V1_26_10));
    }

    @Test
    /**
     * Verifies item frame vertical states use dedicated legacy data so wall map states remain separate.
     */
    void itemFrameVerticalStatesHaveRuntimeMappings() {
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.getLastVersion()), Block.ITEM_FRAME_BLOCK);
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.getLastVersion()), Block.GLOW_FRAME);
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.V1_20_50_NETEASE), Block.ITEM_FRAME_BLOCK);
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.V1_21_2_NETEASE), Block.ITEM_FRAME_BLOCK);
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.V1_21_50_NETEASE), Block.ITEM_FRAME_BLOCK);
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.V1_21_50_NETEASE), Block.GLOW_FRAME);
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.V1_21_93_NETEASE), Block.ITEM_FRAME_BLOCK);
        assertItemFrameVerticalStatesMapped(new BlockPalette(GameVersion.V1_21_93_NETEASE), Block.GLOW_FRAME);
    }

    @Test
    /**
     * Verifies item frame vertical compatibility mappings do not overwrite real runtime reverse mappings.
     */
    void itemFrameVerticalStatesDoNotOverwriteRealRuntimeMappings() throws IOException {
        BlockPalette palette = new BlockPalette(GameVersion.V1_16_100);
        Map<Integer, CompoundTag> rawStates = loadRawRuntimeStates(GameVersion.V1_16_100);
        CompoundTag mapNorthFrame = rawState(rawStates, Block.ITEM_FRAME_BLOCK, 7);

        assertRealRuntimeReverseMappingPreserved(palette, rawStates, mapNorthFrame.getInt("runtimeId") + 5);
        assertRealRuntimeReverseMappingPreserved(palette, rawStates, mapNorthFrame.getInt("runtimeId") + 10);
    }

    @Test
    /**
     * Verifies shelf wood variants and their 32 block states do not fall back to oak or info_update.
     */
    void shelfStatesHaveVariantSpecificRuntimeMappings() {
        assertShelfStatesMapped(new BlockPalette(GameVersion.V1_21_111));
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

    private static void assertItemFrameVerticalStatesMapped(BlockPalette palette, int id) {
        int downMeta = 8;
        int upMeta = 9;
        int downFullId = id << Block.DATA_BITS | downMeta;
        int upFullId = id << Block.DATA_BITS | upMeta;

        Assertions.assertEquals(downFullId, palette.getLegacyFullId(palette.getRuntimeId(id, downMeta)));
        Assertions.assertEquals(upFullId, palette.getLegacyFullId(palette.getRuntimeId(id, upMeta)));
        Assertions.assertEquals(downFullId, palette.getLegacyFullIdFromHashId(palette.getHashId(id, downMeta)));
        Assertions.assertEquals(upFullId, palette.getLegacyFullIdFromHashId(palette.getHashId(id, upMeta)));
        Assertions.assertNotEquals(palette.getRuntimeId(id, 4), palette.getRuntimeId(id, downMeta));
        Assertions.assertNotEquals(palette.getRuntimeId(id, 5), palette.getRuntimeId(id, upMeta));
        assertRuntimeMatchesRawItemFrameState(palette, id, downMeta, 0);
        assertRuntimeMatchesRawItemFrameState(palette, id, upMeta, 1);
    }

    private static void assertRuntimeMatchesRawItemFrameState(BlockPalette palette, int id, int meta, int facingDirection) {
        try {
            Map<Integer, CompoundTag> rawStates = loadRawRuntimeStates(palette.getGameVersion());
            int runtimeId = palette.getRuntimeId(id, meta);
            CompoundTag rawState = rawStates.get(runtimeId);

            Assertions.assertNotNull(rawState, "missing raw runtime state for " + palette.getGameVersion() + ' ' + id + ':' + meta + " runtimeId=" + runtimeId);
            Assertions.assertEquals(id, rawState.getInt("id"), "raw id for runtimeId " + runtimeId);
            Assertions.assertEquals(meta, rawState.getShort("data"), "raw data for runtimeId " + runtimeId);
            CompoundTag states = rawState.getCompound("states");
            Assertions.assertEquals(facingDirection, states.getInt("facing_direction"), "facing_direction for " + id + ':' + meta);
            Assertions.assertEquals(0, states.getByte("item_frame_map_bit"), "item_frame_map_bit for " + id + ':' + meta);
            if (states.contains("item_frame_photo_bit")) {
                Assertions.assertEquals(0, states.getByte("item_frame_photo_bit"), "item_frame_photo_bit for " + id + ':' + meta);
            }
        } catch (IOException e) {
            throw new AssertionError("Unable to load raw runtime states for " + palette.getGameVersion(), e);
        }
    }

    private static void assertRealRuntimeReverseMappingPreserved(BlockPalette palette,
                                                                Map<Integer, CompoundTag> rawStates,
                                                                int runtimeId) {
        CompoundTag rawState = rawStates.get(runtimeId);

        Assertions.assertNotNull(rawState, "expected real raw state for runtimeId " + runtimeId);
        int expectedFullId = rawState.getInt("id") << Block.DATA_BITS | rawState.getShort("data");
        Assertions.assertEquals(expectedFullId, palette.getLegacyFullId(runtimeId), "real runtime reverse mapping for runtimeId " + runtimeId);
    }

    private static CompoundTag rawState(Map<Integer, CompoundTag> rawStates, int id, int data) {
        for (CompoundTag rawState : rawStates.values()) {
            if (rawState.getInt("id") == id && rawState.getShort("data") == data) {
                return rawState;
            }
        }
        throw new AssertionError("Missing raw state for " + id + ':' + data);
    }

    private static Map<Integer, CompoundTag> loadRawRuntimeStates(GameVersion gameVersion) throws IOException {
        String name = (gameVersion.isNetEase() ? "runtime_block_states_netease_" : "runtime_block_states_")
                + gameVersion.getProtocol() + ".dat";
        try (InputStream stream = BlockPaletteTest.class.getClassLoader().getResourceAsStream(name)) {
            Assertions.assertNotNull(stream, "missing runtime state resource " + name);
            @SuppressWarnings("unchecked")
            ListTag<CompoundTag> states = (ListTag<CompoundTag>) NBTIO.readTag(
                    new BufferedInputStream(new GZIPInputStream(stream)), ByteOrder.BIG_ENDIAN, false);

            Map<Integer, CompoundTag> byRuntimeId = new HashMap<>();
            for (CompoundTag state : states.getAll()) {
                byRuntimeId.put(state.getInt("runtimeId"), state);
            }
            return byRuntimeId;
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
