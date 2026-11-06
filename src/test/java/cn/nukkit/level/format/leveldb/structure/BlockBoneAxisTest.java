package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockBone;
import cn.nukkit.block.BlockUnknown;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Bone pillar axes survive materialization, placement and explicit native storage writes. */
class BlockBoneAxisTest {
    private static final Map<Integer, String> AXES = Map.of(0, "y", 4, "x", 8, "z");
    private static final String OLD_PALETTE = "runtime_block_states_589.dat";
    private static final String NEW_PALETTE = "runtime_block_states_2193.dat";
    private static Map<String, List<NbtMap>> fixtures;

    @BeforeAll
    static void initializeRealRegistriesAndIndependentNbt() throws Exception {
        MockServer.init(); // Real Block.init / GlobalBlockPalette / RuntimeItems / Item.init.
        fixtures = Map.of(OLD_PALETTE, readPalette(OLD_PALETTE),
                NEW_PALETTE, readPalette(NEW_PALETTE),
                "leveldb_palette.nbt", readPalette("leveldb_palette.nbt"));
        assertEquals(BlockBone.class, Block.list[216]);
    }

    @Test
    void endpointPaletteAxisOraclesAreLegalAndDistinct() {
        for (String resource : List.of(OLD_PALETTE, NEW_PALETTE)) {
            List<NbtMap> boneRows = fixtures.get(resource).stream()
                    .filter(row -> "minecraft:bone_block".equals(row.getString("name"))).toList();
            Set<Integer> runtimeIds = new HashSet<>();
            for (Map.Entry<Integer, String> expected : AXES.entrySet()) {
                NbtMap row = networkRow(resource, expected.getKey());
                assertEquals("minecraft:bone_block", row.getString("name"));
                assertEquals(expected.getValue(), axis(row), resource);
                List<NbtMap> canonicalRows = boneRows.stream()
                        .filter(candidate -> expected.getValue().equals(axis(candidate)))
                        .filter(candidate -> candidate.getCompound("states").get("deprecated") instanceof Number deprecated
                                && deprecated.intValue() == 0)
                        .toList();
                assertEquals(List.of(row.getInt("runtimeId")), canonicalRows.stream()
                        .map(candidate -> candidate.getInt("runtimeId")).distinct().toList(),
                        resource + " name/axis canonical rows must identify exactly one runtime state");
                runtimeIds.add(row.getInt("runtimeId"));
            }
            assertEquals(3, runtimeIds.size(), resource + " must encode three distinct axes");
        }
    }

    @ParameterizedTest(name = "meta={0}, axis={1}")
    @CsvSource({"0,y", "4,x", "8,z"})
    void directBlockGetPreservesAxis(int meta, String expectedAxis) {
        Block block = Block.get(216, meta);
        assertInstanceOf(BlockBone.class, block);
        assertAll("registered bone materialization",
                () -> assertEquals(meta, block.getDamage(), "requested legal bone metadata"),
                () -> assertEquals(expectedAxis, axis(networkRow(NEW_PALETTE, block.getDamage())),
                        "axis from independent palette, not BlockBone.getBlockFace"));
    }

    @ParameterizedTest(name = "meta={0}, axis={1}")
    @CsvSource({"0,y", "4,x", "8,z"})
    void nativeNbtRoundTripKeepsLegalRawStates(int meta, String expectedAxis) {
        StateBlockStorage decoded = storageFromIndependentNbt(meta, expectedAxis);
        assertEquals(216 << Block.DATA_BITS | meta, decoded.get(1, 1, 1));
        assertEquals(expectedAxis, axis(decoded.getBlockState(1, 1, 1).getVanillaState()));
        StateBlockStorage reopened = roundTrip(decoded);
        assertEquals(216 << Block.DATA_BITS | meta, reopened.get(1, 1, 1));
        assertEquals(expectedAxis, axis(reopened.getBlockState(1, 1, 1).getVanillaState()));
    }

    @ParameterizedTest(name = "meta={0}, axis={1}")
    @CsvSource({"0,y", "4,x", "8,z"})
    void loadedReadPreservesAxisWithoutMutatingStorage(int meta, String expectedAxis) {
        Fixture fixture = loaded(storageFromIndependentNbt(meta, expectedAxis));
        int version = fixture.storage().getVersion();
        long changes = fixture.chunk().getChanges();
        Block block = readLoaded(fixture.level(), 1);
        verify(fixture.level()).getChunkIfLoaded(0, 0);
        verify(fixture.level(), never()).getChunk(anyInt(), anyInt());
        assertAll("real Level.getBlock on a real loaded LevelDBChunk/section",
                () -> assertInstanceOf(BlockBone.class, block),
                () -> assertEquals(meta, block.getDamage(), "loaded bone metadata"),
                () -> assertEquals(expectedAxis, axis(networkRow(NEW_PALETTE, block.getDamage()))),
                () -> assertEquals(216 << Block.DATA_BITS | meta, fixture.storage().get(1, 1, 1)),
                () -> assertEquals(expectedAxis, axis(fixture.storage().getBlockState(1, 1, 1).getVanillaState())),
                () -> assertEquals(version, fixture.storage().getVersion(), "read must not mutate storage"),
                () -> assertEquals(changes, fixture.chunk().getChanges(), "read must not dirty chunk"));
    }

    @ParameterizedTest(name = "meta={0}, axis={1}")
    @CsvSource({"0,y", "4,x", "8,z"})
    void loadedReadExplicitWriteRoundTripPreservesAxis(int meta, String expectedAxis) {
        Fixture fixture = loaded(storageFromIndependentNbt(meta, expectedAxis));
        Block read = readLoaded(fixture.level(), 1);
        // Explicit write into a different cell. Reading alone must not be called durable corruption.
        Block previous = fixture.chunk().getAndSetBlock(2, 1, 1, 0, read);
        assertEquals(Block.AIR, previous.getId());
        StateBlockStorage reopened = roundTrip(fixture.storage());
        assertAll("real BaseChunk/section write and native NBT re-encode/decode",
                () -> assertEquals(216 << Block.DATA_BITS | meta, reopened.get(2, 1, 1),
                        "explicit destination write preserves raw state"),
                () -> assertEquals(expectedAxis, axis(reopened.getBlockState(2, 1, 1).getVanillaState()),
                        "explicit destination write preserves independent NBT axis"),
                () -> assertEquals(216 << Block.DATA_BITS | meta, reopened.get(1, 1, 1),
                        "source cell unchanged"),
                () -> assertEquals(expectedAxis, axis(reopened.getBlockState(1, 1, 1).getVanillaState())));
    }

    @ParameterizedTest(name = "face={0}, meta={1}, axis={2}")
    @CsvSource({"DOWN,0,y", "UP,0,y", "NORTH,8,z", "SOUTH,8,z", "WEST,4,x", "EAST,4,x"})
    void placementKeepsAllSixFaceAxesAndItemCount(BlockFace face, int meta, String expectedAxis) {
        Fixture fixture = loaded(storageFromIndependentNbt(0, "y"));
        Level level = fixture.level();
        Block bone = Block.get(Block.BONE_BLOCK, 0, level, 2, 1, 1);
        Block destination = Block.get(Block.AIR, 0, level, 2, 1, 1);
        Block target = Block.get(Block.STONE, 0, level, 2, 0, 1);
        Item held = new ItemBlock(Block.get(Block.BONE_BLOCK), 0, 3);
        // Isolate Level's event/lighting plumbing; the actual chunk/section write remains native.
        // This is not an end-to-end proof of placement permissions or event cancellation.
        when(level.setBlock(same(destination), same(bone), eq(true))).thenAnswer(call -> {
            fixture.chunk().getAndSetBlock(2, 1, 1, 0, bone);
            return true;
        });
        assertTrue(bone.place(held, destination, target, face, 0.5, 0.5, 0.5, null));
        verify(level, times(1)).setBlock(same(destination), same(bone), eq(true));
        StateBlockStorage reopened = roundTrip(fixture.storage());
        assertEquals(meta, bone.getDamage());
        assertEquals(Block.BONE_BLOCK << Block.DATA_BITS | meta, reopened.get(2, 1, 1));
        assertEquals(expectedAxis, axis(reopened.getBlockState(2, 1, 1).getVanillaState()));
        assertEquals(Block.BONE_BLOCK << Block.DATA_BITS, reopened.get(1, 1, 1), "source cell unchanged");
        assertEquals(3, held.getCount(), "Level owns item consumption; BlockBone must not consume again");
        assertEquals(0, held.getDamage());
    }

    @ParameterizedTest(name = "meta={0}, axis={1}")
    @CsvSource({"0,y", "4,x", "8,z"})
    void pickaxeDropsStayOneCanonicalBoneItemAndOtherToolsDropNothing(int meta, String expectedAxis) {
        Block bone = Block.get(Block.BONE_BLOCK, meta);
        Item tool = Item.get(Item.WOODEN_PICKAXE);
        Item[] drops = bone.getDrops(tool);
        assertEquals(1, drops.length);
        ItemBlock drop = assertInstanceOf(ItemBlock.class, drops[0]);
        assertEquals(Block.BONE_BLOCK, drop.getId());
        assertEquals(0, drop.getDamage(), "placed pillar axis is not item metadata");
        assertEquals(1, drop.getCount());
        assertEquals(64, drop.getMaxStackSize());
        assertFalse(drop.hasCompoundTag(), "ordinary stackable bone drops need no server tags");
        assertEquals(0, bone.getDrops(Item.get(Item.AIR)).length);
        assertEquals(0, bone.getDrops(Item.get(Item.WOODEN_AXE)).length);
        assertEquals(meta, bone.getDamage());
        assertEquals(expectedAxis, axis(networkRow(NEW_PALETTE, bone.getDamage())));
        assertEquals(1, tool.getCount());
        assertEquals(0, tool.getDamage());
        assertEquals(ItemTool.TYPE_PICKAXE, bone.getToolType());
        assertEquals(ItemTool.TIER_WOODEN, bone.getToolTier());
        assertEquals(2.0, bone.getHardness());
        assertEquals(10.0, bone.getResistance());
    }

    @ParameterizedTest(name = "meta={0}, axis={1}")
    @CsvSource({"0,y", "4,x", "8,z"})
    void boneClonesAndCachedPrototypesKeepIndependentAxes(int meta, String expectedAxis) {
        Block first = Block.get(Block.BONE_BLOCK, meta);
        Block second = Block.get(Block.BONE_BLOCK, meta);
        Block clone = first.clone();
        assertNotSame(first, second);
        assertNotSame(first, clone);
        assertEquals(meta, clone.getDamage());
        clone.setDamage(meta == 4 ? 8 : 4);
        assertEquals(meta, first.getDamage());
        assertEquals(meta, second.getDamage());
        assertEquals(meta, Block.get(Block.BONE_BLOCK, meta).getDamage());
        assertEquals(expectedAxis, axis(networkRow(NEW_PALETTE, second.getDamage())));
    }

    @Test
    void legalNeighborsCloneIsolationAndInvalidMetaRemainControlled() {
        assertEquals(Block.AIR, Block.get(Block.AIR, 0).getId());
        assertEquals(Block.STONE, Block.get(Block.STONE, 0).getId());
        assertFalse(Block.get(Block.LIGHTNING_ROD, 5) instanceof BlockUnknown);
        assertInstanceOf(BlockUnknown.class, Block.get(Block.LIGHTNING_ROD, 6),
                "face 6 is genuinely invalid; do not legalize arbitrary metadata");
        Block first = Block.get(Block.WATER, 0);
        Block second = Block.get(Block.WATER, 0);
        assertNotSame(first, second);
        first.setDamage(5);
        assertEquals(0, second.getDamage());
        assertEquals(0, Block.get(Block.WATER, 0).getDamage());
    }

    @Test
    void loadedEmptyNeighborReadsAirWithoutMutation() {
        Fixture fixture = loaded(storageFromIndependentNbt(0, "y"));
        int version = fixture.storage().getVersion();
        long changes = fixture.chunk().getChanges();
        assertEquals(Block.AIR, readLoaded(fixture.level(), 3).getId());
        assertEquals(version, fixture.storage().getVersion());
        assertEquals(changes, fixture.chunk().getChanges());
        assertEquals("y", axis(fixture.storage().getBlockState(1, 1, 1).getVanillaState()));
    }

    @Test
    void unloadedReadDoesNotRequestChunkLoad() {
        Level level = reader(null);
        assertEquals(Block.AIR, readLoaded(level, 1).getId());
        verify(level).getChunkIfLoaded(0, 0);
        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    private static List<NbtMap> readPalette(String resource) throws IOException {
        InputStream stream = BlockBoneAxisTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try (NBTInputStream input = NbtUtils.createGZIPReader(stream)) {
            Object root = input.readTag();
            Object rows = root instanceof NbtMap compound ? compound.get("blocks") : root;
            NbtList<?> list = assertInstanceOf(NbtList.class, rows, resource + " compound rows");
            assertEquals(NbtType.COMPOUND, list.getType(), resource + " row tag type");
            List<NbtMap> checked = new ArrayList<>(list.size());
            for (Object entry : list) {
                checked.add(assertInstanceOf(NbtMap.class, entry, resource + " row " + checked.size()));
            }
            return List.copyOf(checked);
        }
    }

    private static NbtMap networkRow(String resource, int meta) {
        List<NbtMap> matches = fixtures.get(resource).stream()
                .filter(row -> row.getInt("id") == 216 && row.getShort("data") == meta).toList();
        assertEquals(1, matches.size(), resource + " legal bone216:" + meta);
        return matches.get(0);
    }

    private static String axis(NbtMap state) {
        return state.getCompound("states").getString("pillar_axis");
    }

    private static StateBlockStorage storageFromIndependentNbt(int meta, String expectedAxis) {
        assertEquals(expectedAxis, AXES.get(meta));
        List<NbtMap> matches = fixtures.get("leveldb_palette.nbt").stream()
                .filter(row -> "minecraft:bone_block".equals(row.getString("name")))
                .filter(row -> expectedAxis.equals(axis(row)))
                .filter(row -> ((Number) row.getCompound("states").getOrDefault("deprecated", (byte) 0)).intValue() == 0)
                .toList();
        assertEquals(1, matches.size(), "independent disk NBT bone axis=" + expectedAxis);
        BlockStateSnapshot state = BlockStateMapping.get().getStateUnsafe(matches.get(0));
        assertNotNull(state, "real registry must recognize independent disk NBT");
        assertEquals(216, state.getLegacyId());
        assertEquals(meta, state.getLegacyData());
        StateBlockStorage input = new StateBlockStorage();
        input.set(1, 1, 1, state);
        return roundTrip(input);
    }

    private static StateBlockStorage roundTrip(StateBlockStorage source) {
        ByteBuf bytes = Unpooled.buffer();
        try {
            source.writeToStorage(bytes);
            StateBlockStorage decoded = new StateBlockStorage();
            decoded.readFromStorage(bytes, new ChunkBuilder(0, 0, mock(LevelDBProvider.class)));
            assertEquals(0, bytes.readableBytes(), "native decoder must consume complete storage");
            return decoded;
        } finally {
            bytes.release();
        }
    }

    private static Fixture loaded(StateBlockStorage storage) {
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage, new StateBlockStorage()}, false);
        LevelDBChunk chunk = new ChunkBuilder(0, 0, mock(LevelDBProvider.class))
                .sections(new ChunkSection[]{section}).build();
        assertSame(section, chunk.getSection(0));
        return new Fixture(reader(chunk), chunk, storage);
    }

    private static Level reader(LevelDBChunk chunk) {
        // Mock only unrelated Level plumbing. Registry, mapping, materialization, chunk, storage,
        // NBT encoding and NBT decoding remain native; no mocked implementation of the defect.
        Level level = mock(Level.class);
        when(level.getMinBlockY()).thenReturn(0);
        when(level.getMaxBlockY()).thenReturn(255);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        doCallRealMethod().when(level).isYInRange(anyInt());
        doCallRealMethod().when(level).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(),
                anyInt(), anyBoolean());
        return level;
    }

    private static Block readLoaded(Level level, int x) {
        return level.getBlock((FullChunk) null, x, 1, 1, 0, false);
    }

    private record Fixture(Level level, LevelDBChunk chunk, StateBlockStorage storage) { }
}
