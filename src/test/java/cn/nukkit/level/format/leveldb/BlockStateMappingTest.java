package cn.nukkit.level.format.leveldb;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
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

    @BeforeAll
    /**
     * Boots the shared mock server once so block state mappings can be initialized safely.
     */
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

    @Test
    /**
     * Verifies that the custom state cache stays bounded and starts evicting older entries.
     */
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
}
