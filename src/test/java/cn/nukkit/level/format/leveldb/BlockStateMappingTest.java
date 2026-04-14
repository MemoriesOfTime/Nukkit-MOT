package cn.nukkit.level.format.leveldb;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockStateMappingTest {

    @BeforeAll
    /**
     * Boots the shared mock server once so block state mappings can be initialized safely.
     */
    static void initServer() {
        MockServer.init();
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
