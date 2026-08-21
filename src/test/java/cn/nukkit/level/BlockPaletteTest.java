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

    /**
     * 最新有专属调色板资源的版本。虚拟协议号（协议号未变的热修复，如 1.26.44 的 2169）
     * 没有专属资源文件，生产中经 GlobalBlockPalette 的阈值回退复用本版本的调色板。
     * <p>
     * The latest version with a dedicated palette resource. Virtual protocols (same-wire hotfixes
     * like 1.26.44's 2169) ship no resource and reuse this version's palette in production via
     * GlobalBlockPalette's threshold fallback.
     */
    private static final GameVersion LAST_PALETTE_VERSION = GameVersion.V1_26_40;

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
        assertCrafterTriggeredStateMapped(new BlockPalette(LAST_PALETTE_VERSION));
        assertCrafterTriggeredStateMapped(new BlockPalette(GameVersion.V1_21_50_NETEASE));
    }

    @Test
    /**
     * 验证 GlobalBlockPalette 的 floor 查询不会把最新版本降级到上一个调色板版本
     * Verifies GlobalBlockPalette floor lookup does not down-grade the latest version to the previous palette version
     */
    void globalBlockPaletteResolvesLatestVersion() {
        Assertions.assertEquals(
                GameVersion.V1_26_40.getProtocol(),
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_40).getProtocol()
        );
        Assertions.assertEquals(
                GameVersion.V1_26_30.getProtocol(),
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_30).getProtocol()
        );
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
     * 岩浆炼药锅经 LevelDB 调色板往返的回归测试。
     * <p>
     * Regression test for lava cauldron round-trip via the LevelDB block-state palette.
     * 验证 cauldron+lava 状态反查返回 LAVA_CAULDRON(465) 而非被 CAULDRON_BLOCK(118) 遮蔽。
     * <p>
     * Verifies that the cauldron+lava state reverse-maps to LAVA_CAULDRON(465) instead of
     * being shadowed by CAULDRON_BLOCK(118), so bucket interactions remain correct.
     */
    void lavaCauldronRoundTripPreservesLegacyId() {
        cn.nukkit.block.Block lavaCauldron = cn.nukkit.block.Block.get(cn.nukkit.block.BlockID.LAVA_CAULDRON, 14);
        Assertions.assertTrue(lavaCauldron instanceof cn.nukkit.block.BlockCauldronLava,
                "Block.get(LAVA_CAULDRON, 14) must return BlockCauldronLava");

        // Simulate the LevelDB save path: legacy (465, 14) -> BlockStateSnapshot
        cn.nukkit.level.format.leveldb.BlockStateMapping mapping = cn.nukkit.level.format.leveldb.BlockStateMapping.get();
        cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot saved = mapping.getBlockStateFromFullId(lavaCauldron.getFullId());
        Assertions.assertNotNull(saved, "Missing BlockStateSnapshot for lava cauldron");

        // The reverse lookup must return LAVA_CAULDRON (465), not CAULDRON_BLOCK (118)
        Assertions.assertEquals(cn.nukkit.block.BlockID.LAVA_CAULDRON, saved.getLegacyId(),
                "cauldron+lava state must reverse-map to LAVA_CAULDRON(465), not CAULDRON_BLOCK(118)");

        // And the reconstructed block must be a BlockCauldronLava
        cn.nukkit.block.Block reconstructed = saved.getBlock();
        Assertions.assertTrue(reconstructed instanceof cn.nukkit.block.BlockCauldronLava,
                "Reconstructed block must be BlockCauldronLava, got " + reconstructed.getClass().getSimpleName());
    }

    @Test
    /**
     * 验证岩浆炼药锅经 LevelDB 磁盘读取路径正确加载。
     * <p>
     * Verifies lava cauldron reloads correctly via the actual LevelDB disk-read path.
     * 磁盘存储的 NbtMap 不含 version 字段，经升级后命中 paletteMap，确认预设 legacyId=465 生效。
     * <p>
     * Disk NbtMaps lack the version field; after upgrade they hit paletteMap, confirming the
     * preset legacyId=465 takes effect for disk-loaded chunks, not just forward-constructed states.
     */
    void lavaCauldronDiskReadPathReturnsBlockCauldronLava() {
        // Simulate the exact NbtMap stored on disk: {name, states} without version
        org.cloudburstmc.nbt.NbtMap diskState = org.cloudburstmc.nbt.NbtMap.builder()
                .putString("name", "minecraft:cauldron")
                .putCompound("states", org.cloudburstmc.nbt.NbtMap.builder()
                        .putString("cauldron_liquid", "lava")
                        .putInt("fill_level", 6)
                        .build())
                .build();

        cn.nukkit.level.format.leveldb.BlockStateMapping mapping = cn.nukkit.level.format.leveldb.BlockStateMapping.get();
        // This is the exact call sequence from StateBlockStorage.readFromStorage
        cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot snapshot = mapping.getStateUnsafe(diskState);
        if (snapshot == null) {
            // Disk states without version go through the upgrade + custom-cache path
            snapshot = mapping.getUpdatedOrCustom(diskState);
        }

        Assertions.assertFalse(snapshot.isCustom(),
                "cauldron+lava disk state should map to a known palette state, not a custom one. " +
                        "If this fails, the paletteMap key mismatch (version field) prevents lookup.");
        Assertions.assertEquals(cn.nukkit.block.BlockID.LAVA_CAULDRON, snapshot.getLegacyId(),
                "Disk-loaded cauldron+lava must reverse-map to LAVA_CAULDRON(465)");
        int legacyData = snapshot.getLegacyData();
        cn.nukkit.block.Block block = snapshot.getBlock();
        Assertions.assertTrue(block instanceof cn.nukkit.block.BlockCauldronLava,
                "Disk-loaded cauldron+lava must reconstruct as BlockCauldronLava, got " + block.getClass().getSimpleName());
        Assertions.assertEquals(14, legacyData,
                "cauldron+lava fill_level=6 must map to legacyData=14 (full lava, bit3 set), got " + legacyData);
        Assertions.assertTrue(((cn.nukkit.block.BlockCauldronLava) block).isFull(),
                "Reconstructed lava cauldron must be full (damage=14) so bucket extraction works");
    }

    @Test
    /**
     * Verifies item frame vertical states use dedicated legacy data so wall map states remain separate.
     */
    void itemFrameVerticalStatesHaveRuntimeMappings() {
        assertItemFrameVerticalStatesMapped(new BlockPalette(LAST_PALETTE_VERSION), Block.ITEM_FRAME_BLOCK);
        assertItemFrameVerticalStatesMapped(new BlockPalette(LAST_PALETTE_VERSION), Block.GLOW_FRAME);
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
