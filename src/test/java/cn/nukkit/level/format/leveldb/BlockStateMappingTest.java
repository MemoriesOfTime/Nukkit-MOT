package cn.nukkit.level.format.leveldb;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockStairs;
import cn.nukkit.block.BlockThin;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.mockito.Mockito.mockStatic;

class BlockStateMappingTest {

    /**
     * Boots the shared mock server once so block state mappings can be initialized safely.
     */
    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void runtimeRemapPreservesArbitraryOverridesWithoutResolvingLazyStates() {
        BlockStateMapping mapping = new BlockStateMapping(GameVersion.getFeatureVersion());
        NbtMap futureState = NbtMap.builder().putString("name", "test:future_merged_block")
                .putCompound("states", NbtMap.builder().putInt("variant", 7).build()).build();
        NbtMap lazyState = NbtMap.builder().putString("name", "test:lazy_block")
                .putCompound("states", NbtMap.EMPTY).build();
        mapping.registerState(10, futureState, 1234, 37);
        mapping.registerState(11, lazyState);
        BlockStateSnapshot original = mapping.getState(10);

        try (MockedStatic<BlockStateMapping> globals = mockStatic(BlockStateMapping.class)) {
            globals.when(BlockStateMapping::get).thenThrow(new AssertionError("unexpected legacy resolution"));
            mapping.remapRuntimeIds(new Int2IntOpenHashMap(Map.of(10, 11, 11, 10)));
            BlockStateSnapshot remapped = mapping.getStateUnsafe(futureState);
            Assertions.assertSame(mapping.getState(11), remapped);
            Assertions.assertEquals(11, remapped.getRuntimeId());
            Assertions.assertEquals(1234, remapped.getLegacyId());
            Assertions.assertEquals(37, remapped.getLegacyData());
            Assertions.assertEquals(GameVersion.getFeatureVersion(), remapped.getVersion());
            Assertions.assertSame(mapping.getState(10), mapping.getStateUnsafe(lazyState));
            Assertions.assertNotSame(original, remapped);
            Assertions.assertEquals(10, original.getRuntimeId(), "retained old snapshots must not be mutated");
        }
    }

    @Test
    void runtimeRemapMovesChosenFallbackAndInvalidatesUnknownStateCache() {
        BlockStateMapping mapping = new BlockStateMapping(GameVersion.getFeatureVersion(), new NukkitLegacyMapper());
        NukkitLegacyMapper.registerStates(mapping);
        mapping.setDefaultBlock(Block.STONE, 0);
        BlockStateSnapshot originalDefault = mapping.getDefaultState();
        NbtMap unknown = NbtMap.builder().putString("name", "test:unknown_state")
                .putCompound("states", NbtMap.EMPTY).build();
        BlockStateSnapshot originalUnknown = mapping.getUpdatedOrCustom(unknown, unknown);

        int stateCount = NukkitLegacyMapper.loadBlockPalette().size();
        Int2IntOpenHashMap replacements = new Int2IntOpenHashMap();
        for (int i = 0; i < stateCount; i++) {
            replacements.put(i, (i + 1) % stateCount);
        }
        mapping.remapRuntimeIds(replacements);

        int expectedRuntimeId = replacements.get(originalDefault.getRuntimeId());
        Assertions.assertEquals(expectedRuntimeId, mapping.getDefaultRuntimeId());
        Assertions.assertSame(mapping.getState(expectedRuntimeId), mapping.getDefaultState());
        Assertions.assertEquals(originalDefault.getVanillaState(), mapping.getDefaultState().getVanillaState());
        BlockStateSnapshot updatedUnknown = mapping.getUpdatedOrCustom(unknown, unknown);
        Assertions.assertNotSame(originalUnknown, updatedUnknown);
        Assertions.assertTrue(updatedUnknown.isCustom());
        Assertions.assertEquals(unknown, updatedUnknown.getVanillaState());
        Assertions.assertEquals(expectedRuntimeId, updatedUnknown.getRuntimeId());
        Assertions.assertSame(mapping.getDefaultState(), mapping.getState(stateCount + 1));
    }

    /**
     * Verifies that the custom state cache stays bounded and starts evicting older entries.
     */
    @Test
    void customStateCacheIsBounded() {
        BlockStateMapping mapping = new BlockStateMapping(GameVersion.getFeatureVersion());
        mapping.setLegacyMapper(new NukkitLegacyMapper());
        NukkitLegacyMapper.registerStates(mapping);

        int totalStates = BlockStateMapping.MAX_CUSTOM_STATE_CACHE_SIZE + 32;
        for (int i = 0; i < totalStates; i++) {
            NbtMap state = NbtMap.builder()
                    .putString("name", "nukkit:test_unknown_" + i)
                    .putCompound("states", NbtMap.builder().putInt("variant", i).build())
                    .putInt("version", 1)
                    .build();
            mapping.getUpdatedOrCustom(state, state);
        }

        Assertions.assertEquals(BlockStateMapping.MAX_CUSTOM_STATE_CACHE_SIZE, mapping.getCustomCacheSizeForTesting());
        Assertions.assertTrue(mapping.getCustomCacheEvictionsForTesting() > 0);
    }

    // ------------------------------------------------------------------
    // Meta alignment (from BlockStateMetaAlignmentTest).
    // meta 连接/角落位与调色板状态字符串的方向对齐：corner 位值必须映射到对应 minecraft:corner
    // 枚举、连接位必须映射到对应方向的 connection_* 键且互不串扰——错位会导致 1.26.50 客户端
    // 全员旋转/镜像渲染。
    // <p>
    // Directional alignment between meta connection/corner bits and palette state strings: corner
    // bit values must map to the matching minecraft:corner enum and connection bits to their own
    // direction keys without crosstalk — a misalignment would rotate/mirror rendering for all
    // 1.26.50 clients.
    // ------------------------------------------------------------------

    @Test
    void cornerBitsMapToCornerEnum() {
        String[] cornerNames = {null, "inner_left", "inner_right", "outer_left", "outer_right"};
        for (int corner = 1; corner <= 4; corner++) {
            NbtMap states = BlockStateMapping.get()
                    .getState(53, 2 | (corner << BlockStairs.CORNER_SHIFT))
                    .getVanillaState().getCompound("states");
            Assertions.assertEquals(cornerNames[corner], states.get("minecraft:corner"), "corner " + corner);
            // 朝向与半层基础位不受 corner 影响 / base orientation and half are unaffected by corner bits
            Assertions.assertEquals(2, states.getInt("weirdo_direction"));
            Assertions.assertEquals(false, states.getBoolean("upside_down_bit"));
        }
    }

    @Test
    void connectionBitsMapToDirectionKeys() {
        String[] keys = {"minecraft:connection_north", "minecraft:connection_east",
                "minecraft:connection_south", "minecraft:connection_west"};
        for (int i = 0; i < 4; i++) {
            NbtMap states = BlockStateMapping.get().getState(102, 0x10 << i).getVanillaState().getCompound("states");
            Assertions.assertEquals((byte) 1, states.get(keys[i]), "bit 0x" + Integer.toHexString(0x10 << i));
            for (int j = 1; j < 4; j++) {
                Assertions.assertEquals((byte) 0, states.get(keys[(i + j) % 4]),
                        "bit 0x" + Integer.toHexString(0x10 << i) + " must not leak into " + keys[(i + j) % 4]);
            }
        }

        // 组合位：N+S 同时连接、E+W 保持 0 / combos set both directions, others stay 0
        NbtMap both = BlockStateMapping.get()
                .getState(102, BlockThin.FLAG_CONNECTION_NORTH | BlockThin.FLAG_CONNECTION_SOUTH)
                .getVanillaState().getCompound("states");
        Assertions.assertEquals((byte) 1, both.get("minecraft:connection_north"));
        Assertions.assertEquals((byte) 1, both.get("minecraft:connection_south"));
        Assertions.assertEquals((byte) 0, both.get("minecraft:connection_east"));
        Assertions.assertEquals((byte) 0, both.get("minecraft:connection_west"));
    }

    @Test
    void fenceConnectionBitsKeepWoodType() {
        // 栅栏：连接位在 meta 高 4 位，低 3 位木种必须保留 / fences carry connections in the
        // upper meta nibble; the wood type in the low bits must survive
        for (int i = 0; i < 4; i++) {
            BlockStateSnapshot state = BlockStateMapping.get().getState(85, 0x10 << i);
            Assertions.assertEquals("minecraft:oak_fence", state.getVanillaState().getString("name"));
            Assertions.assertEquals((byte) 1, state.getVanillaState().getCompound("states")
                    .get("minecraft:connection_" + new String[]{"north", "east", "south", "west"}[i]),
                    "bit 0x" + Integer.toHexString(0x10 << i));
        }

        BlockStateSnapshot spruce = BlockStateMapping.get().getState(85, 0x11);
        Assertions.assertEquals("minecraft:spruce_fence", spruce.getVanillaState().getString("name"));
        Assertions.assertEquals((byte) 1, spruce.getVanillaState().getCompound("states").get("minecraft:connection_north"));
    }
}
