package cn.nukkit.block.custom;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.custom.container.BlockContainer;
import cn.nukkit.block.custom.container.CustomBlock;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.NukkitLegacyMapper;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.network.protocol.BiomeDefinitionListPacket;
import cn.nukkit.utils.Hash;
import cn.nukkit.utils.serverconfig.ServerConfig;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 回归 issue #1210：自定义方块注册后须保留原版正反向映射，包括被重复 legacy 键覆盖的状态。
 */
class CustomBlockPaletteReverseMappingTest {

    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        MockServer.init();
    }

    private CustomBlockManager manager() throws ReflectiveOperationException {
        ServerConfig config = new ServerConfig();
        config.customBlockSettings().autoDownloadVanillaPalette(false);
        Server server = mock(Server.class);
        when(server.getServerConfig()).thenReturn(config);
        when(server.getDataPath()).thenReturn(directory.toString());
        Constructor<CustomBlockManager> constructor = CustomBlockManager.class.getDeclaredConstructor(Server.class);
        constructor.setAccessible(true);
        return constructor.newInstance(server);
    }

    private Supplier<BlockContainer> factory(CustomBlockManager manager, String identifier) {
        return () -> {
            int id = manager.getBlockId(identifier);
            return new CustomBlock(identifier, id == -1 ? BlockID.INFO_UPDATE : id);
        };
    }

    private static Set<Integer> canonicalWinners(BlockPalette palette) {
        Set<Integer> canonical = new HashSet<>();
        for (Int2IntMap.Entry entry : palette.getLegacyToRuntimeIdMap().int2IntEntrySet()) {
            if (palette.getLegacyFullId(entry.getIntValue()) == entry.getIntKey()) {
                canonical.add(entry.getIntKey());
            }
        }
        return canonical;
    }

    @Test
    void rebuildPreservesInitializedStorageMappings() throws Exception {
        CustomBlockManager manager = manager();
        BlockPalette palette = new BlockPalette(GameVersion.getFeatureVersion());
        BlockStateMapping mapping = new BlockStateMapping(GameVersion.getFeatureVersion(), new NukkitLegacyMapper());
        NukkitLegacyMapper.registerStates(mapping);

        try (MockedStatic<BiomeDefinitionListPacket> biomes = mockStatic(BiomeDefinitionListPacket.class);
             MockedStatic<GlobalBlockPalette> palettes = mockStatic(GlobalBlockPalette.class);
             MockedStatic<BlockStateMapping> mappings = mockStatic(BlockStateMapping.class);
             MockedStatic<RuntimeItems> items = mockStatic(RuntimeItems.class);
             MockedStatic<Item> creative = mockStatic(Item.class)) {
            palettes.when(() -> GlobalBlockPalette.getPaletteByProtocol(any(GameVersion.class))).thenReturn(palette);
            mappings.when(BlockStateMapping::get).thenReturn(mapping);
            items.when(RuntimeItems::values).thenReturn(new RuntimeItemMapping[0]);

            Map<NbtMap, Integer> expectedFullIds = new LinkedHashMap<>();
            int stateCount = NukkitLegacyMapper.loadBlockPalette().size();
            boolean includesLavaCauldron = false;
            for (int runtimeId = 0; runtimeId < stateCount; runtimeId++) {
                if (palette.getLegacyFullId(runtimeId) == -1) {
                    continue;
                }
                BlockStateSnapshot state = mapping.getState(runtimeId);
                expectedFullIds.put(state.getVanillaState(),
                        (state.getLegacyId() << Block.DATA_BITS) | state.getLegacyData());
                includesLavaCauldron |= state.getLegacyId() == BlockID.LAVA_CAULDRON;
            }
            assertTrue(includesLavaCauldron, "baseline must include the storage-specific legacy override");

            String identifier = "test:breeze_grass";
            manager.registerCustomBlock(identifier, factory(manager, identifier));
            assertTrue(manager.closeRegistry());

            for (Map.Entry<NbtMap, Integer> entry : expectedFullIds.entrySet()) {
                BlockStateSnapshot state = mapping.getStateUnsafe(entry.getKey());
                assertNotNull(state, () -> "storage state disappeared: " + entry.getKey());
                assertEquals(entry.getValue().intValue(),
                        (state.getLegacyId() << Block.DATA_BITS) | state.getLegacyData(),
                        () -> "storage block changed after custom registration: " + entry.getKey());
            }
            int customId = manager.getBlockId(identifier);
            assertEquals(customId, mapping.getState(customId, 0).getLegacyId());
        }
    }

    @Test
    void rebuildKeepsAllVanillaMappings() throws Exception {
        CustomBlockManager manager = manager();
        BlockPalette sharedPalette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.getFeatureVersion());
        long sharedPaletteVersion = sharedPalette.getNetworkVersion();
        BlockStateMapping sharedMapping = BlockStateMapping.get();
        BlockStateSnapshot sharedStone = sharedMapping.getState(BlockID.STONE, 0);
        boolean sharedHashedNetworkIds = GlobalBlockPalette.useHashedBlockNetworkIds();
        BlockPalette palette = new BlockPalette(GameVersion.getFeatureVersion());
        BlockStateMapping mapping = new BlockStateMapping(GameVersion.getFeatureVersion());

        List<NbtMap> vanillaStates = NukkitLegacyMapper.loadBlockPalette().stream().map(state -> {
            var builder = state.toBuilder();
            builder.remove("network_id");
            builder.remove("name_hash");
            builder.remove("block_id");
            return builder.build();
        }).toList();
        Map<NbtMap, Integer> runtimeReverseBefore = new LinkedHashMap<>();
        Map<Integer, Integer> hashReverseBefore = new HashMap<>();
        for (int runtimeId = 0; runtimeId < vanillaStates.size(); runtimeId++) {
            NbtMap state = vanillaStates.get(runtimeId);
            runtimeReverseBefore.put(state, palette.getLegacyFullId(runtimeId));
            int hashId = Hash.hashBlock(CustomBlockManager.convertNbtMap(state));
            hashReverseBefore.put(hashId, palette.getLegacyFullIdFromHashId(hashId));
        }
        Map<Integer, NbtMap> forwardStatesBefore = new HashMap<>();
        Map<Integer, Integer> forwardHashesBefore = new HashMap<>();
        for (Int2IntMap.Entry entry : palette.getLegacyToRuntimeIdMap().int2IntEntrySet()) {
            forwardStatesBefore.put(entry.getIntKey(), vanillaStates.get(entry.getIntValue()));
            forwardHashesBefore.put(entry.getIntKey(), palette.getHashIdByFullId(entry.getIntKey()));
        }

        Set<Integer> before = canonicalWinners(palette);
        assertTrue(before.contains(162 << Block.DATA_BITS));       // acacia_log axis y
        assertTrue(before.contains(5 << Block.DATA_BITS));         // oak planks
        assertTrue(before.contains(263 << Block.DATA_BITS));       // stripped_acacia_log
        assertFalse(before.contains((162 << Block.DATA_BITS) | 3)); // junk axis meta is not canonical

        String identifier = "test:breeze_grass";
        // 执行实际注册与重建流程，同时隔离全局状态变更。
        try (MockedStatic<BiomeDefinitionListPacket> biomes = mockStatic(BiomeDefinitionListPacket.class);
             MockedStatic<GlobalBlockPalette> palettes = mockStatic(GlobalBlockPalette.class);
             MockedStatic<BlockStateMapping> mappings = mockStatic(BlockStateMapping.class);
             MockedStatic<RuntimeItems> items = mockStatic(RuntimeItems.class);
             MockedStatic<Item> creative = mockStatic(Item.class)) {
            palettes.when(() -> GlobalBlockPalette.getPaletteByProtocol(any(GameVersion.class))).thenReturn(palette);
            mappings.when(BlockStateMapping::get).thenReturn(mapping);
            items.when(RuntimeItems::values).thenReturn(new RuntimeItemMapping[0]);

            manager.registerCustomBlock(identifier, factory(manager, identifier));
            assertTrue(manager.hasCustomBlocks());
            assertTrue(manager.closeRegistry(), "storage palette rebuild should have run");
        }

        Set<Integer> after = canonicalWinners(palette);
        Set<Integer> gained = new HashSet<>(after);
        gained.removeAll(before);
        Set<Integer> lost = new HashSet<>(before);
        lost.removeAll(after);
        assertEquals(Set.of(), lost, "pre-rebuild winners must survive the rebuild");
        assertEquals(Set.of(manager.getBlockId(identifier) << Block.DATA_BITS), gained);

        assertEquals(162 << Block.DATA_BITS, palette.getLegacyFullId(palette.getRuntimeId(162, 0)));
        assertEquals(5 << Block.DATA_BITS, palette.getLegacyFullId(palette.getRuntimeId(5, 0)));
        assertEquals(263 << Block.DATA_BITS, palette.getLegacyFullId(palette.getRuntimeId(263, 0)));
        assertEquals(17 << Block.DATA_BITS, palette.getLegacyFullId(palette.getRuntimeId(17, 0)));

        for (Map.Entry<NbtMap, Integer> entry : runtimeReverseBefore.entrySet()) {
            BlockStateSnapshot state = mapping.getStateUnsafe(entry.getKey());
            assertNotNull(state, () -> "vanilla state disappeared: " + entry.getKey());
            assertEquals(entry.getValue().intValue(), palette.getLegacyFullId(state.getRuntimeId()),
                    () -> "runtime reverse mapping changed for " + entry.getKey());
        }
        for (Map.Entry<Integer, Integer> entry : hashReverseBefore.entrySet()) {
            assertEquals(entry.getValue().intValue(), palette.getLegacyFullIdFromHashId(entry.getKey()),
                    () -> "hash reverse mapping changed for " + entry.getKey());
        }
        for (Map.Entry<Integer, NbtMap> entry : forwardStatesBefore.entrySet()) {
            int runtimeId = palette.getRuntimeIdByFullId(entry.getKey());
            assertEquals(entry.getValue(), mapping.getState(runtimeId).getVanillaState(),
                    () -> "forward state changed for legacy " + entry.getKey());
            assertEquals(forwardHashesBefore.get(entry.getKey()).intValue(), palette.getHashIdByFullId(entry.getKey()),
                    () -> "forward hash changed for legacy " + entry.getKey());
        }

        assertEquals(sharedPaletteVersion, sharedPalette.getNetworkVersion(), "shared palette must not be rebuilt");
        assertSame(sharedStone, sharedMapping.getState(BlockID.STONE, 0), "shared LevelDB mapping must be preserved");
        assertEquals(sharedHashedNetworkIds, GlobalBlockPalette.useHashedBlockNetworkIds(),
                "shared hashed network ID setting must be preserved");
    }
}
