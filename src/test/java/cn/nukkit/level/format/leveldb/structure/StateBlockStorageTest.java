package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.LegacyStateMapper;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StateBlockStorageTest {
    private boolean originalHashedIds;

    @BeforeAll
    static void init() { MockServer.init(); }

    @AfterAll
    static void cleanup() { MockServer.reset(); }

    @BeforeEach
    void saveNetworkIdSetting() {
        originalHashedIds = GlobalBlockPalette.useHashedBlockNetworkIds();
    }

    @AfterEach
    void restoreNetworkIdSetting() {
        GlobalBlockPalette.setUseHashedBlockNetworkIds(originalHashedIds);
    }

    // Dynamic legacy-mapper replacement must not leak old serialized layers.

    private static final int RUNTIME=237000000;

    @ParameterizedTest @ValueSource(booleans={false,true})
    void foreignVersionSnapshotRetainsLivePublicMapperReads(boolean antiXray){
        BlockStateMapping mapping=BlockStateMapping.get();LegacyStateMapper previous=mapping.getLegacyMapper();
        assertNotSame(GameVersion.V1_20_0,mapping.getVersion());
        var state=BlockStateSnapshot.builder().runtimeId(RUNTIME).version(GameVersion.V1_20_0).build();
        assertEquals(BlockStateSnapshot.class,state.getClass());
        StateBlockStorage storage=new StateBlockStorage();storage.set(0,state);
        try {
            mapping.setLegacyMapper(mapper(previous,Block.STONE));
            byte[] before=encoded(storage,antiXray);assertArrayEquals(reference(storage,antiXray),before);
            mapping.setLegacyMapper(mapper(previous,Block.PLANKS));
            byte[] expected=reference(storage,antiXray);assertFalse(Arrays.equals(before,expected));
            assertArrayEquals(expected,encoded(storage,antiXray),"public mapper replacement must not reuse old layer bytes");
            assertArrayEquals(expected,encoded(storage.copy(),antiXray));
        } finally {mapping.setLegacyMapper(previous);}
    }
    @Test void presetFeatureSnapshotRetainsItsOriginalLegacyIds(){
        BlockStateMapping mapping=BlockStateMapping.get();LegacyStateMapper previous=mapping.getLegacyMapper();
        StateBlockStorage storage=new StateBlockStorage();storage.set(0,BlockStateSnapshot.builder().runtimeId(RUNTIME)
                .version(mapping.getVersion()).legacyId(Block.STONE).legacyData(0).build());
        try {
            mapping.setLegacyMapper(mapper(previous,Block.STONE));byte[] before=encoded(storage,false);
            mapping.setLegacyMapper(mapper(previous,Block.PLANKS));assertArrayEquals(before,encoded(storage,false));
            assertArrayEquals(reference(storage,false),encoded(storage.copy(),false));
        } finally {mapping.setLegacyMapper(previous);}
    }
    static LegacyStateMapper mapper(LegacyStateMapper delegate,int id){return new LegacyStateMapper(){
        public int legacyToRuntime(int legacyId,int meta){return delegate.legacyToRuntime(legacyId,meta);}
        public int runtimeToFullId(int runtime){return runtime==RUNTIME?id<<Block.DATA_BITS:delegate.runtimeToFullId(runtime);}
        public int runtimeToLegacyId(int runtime){return runtime==RUNTIME?id:delegate.runtimeToLegacyId(runtime);}
        public int runtimeToLegacyData(int runtime){return runtime==RUNTIME?0:delegate.runtimeToLegacyData(runtime);}
    };}
    static byte[] encoded(StateBlockStorage storage,boolean anti){BinaryStream out=new BinaryStream();storage.writeTo(GameVersion.V1_26_50,out,anti);return out.getBuffer();}
    static byte[] reference(StateBlockStorage storage,boolean anti){
        var palette=GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50);
        boolean hash=GlobalBlockPalette.shouldUseHashedBlockNetworkIds(GameVersion.V1_26_50);
        var cells=PalettedBlockStorage.createFromBlockPalette(GameVersion.V1_26_50);
        for(int i=0;i<4096;i++){
            int full=storage.get(i),id=full>>Block.DATA_BITS,meta=full&Block.DATA_MASK;
            if(anti&&id<Block.MAX_BLOCK_ID&&cn.nukkit.level.Level.xrayableBlocks[id]){id=Block.STONE;meta=0;}
            cells.setBlock(i,hash?palette.getHashId(id,meta):palette.getRuntimeId(id,meta));
        }
        BinaryStream out=new BinaryStream();cells.writeTo(out);return out.getBuffer();
    }

    // Lazy allocation: fresh air layers stay word-free and serialize exactly as before.

    @Test
    void freshAirLayerHasNoCellWordsAndSerializesExactlyAsBefore() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        assertEquals(0, bits(storage).getWords().length);
        assertEquals(BitArrayVersion.V0, bits(storage).getVersion());
        for (int cell = 0; cell < 4096; cell++) assertEquals(BlockID.AIR, storage.getBlockState(cell).getLegacyId());
        assertArrayEquals(serialized(new StateBlockStorage(BitArrayVersion.V2)), serialized(storage));
    }

    @Test
    void firstMutationAndCopyLeaveAllOtherCellsAndOriginalIndependent() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        StateBlockStorage copy = storage.copy();
        var stone = BlockStateMapping.get().getState(BlockID.STONE, 0);
        var water = BlockStateMapping.get().getState(BlockID.WATER, 0);
        storage.set(2048, stone);
        assertEquals(BitArrayVersion.V2, bits(storage).getVersion(), "skip an intermediate V1 and zero-index scan");
        copy.set(4095, water);
        for (int cell = 0; cell < 4096; cell++) {
            assertEquals(cell == 2048 ? BlockID.STONE : BlockID.AIR, storage.getBlockState(cell).getLegacyId());
            assertEquals(cell == 4095 ? BlockID.WATER : BlockID.AIR, copy.getBlockState(cell).getLegacyId());
        }
    }

    @Test
    void mutationsRoundTripThroughTheExistingDiskReader() {
        StateBlockStorage storage = new StateBlockStorage();
        int[] ids = {BlockID.STONE, BlockID.DIRT, BlockID.WATER, BlockID.GLASS, BlockID.SAND};
        for (int cell = 0; cell < 4096; cell += 7) storage.set(cell, BlockStateMapping.get().getState(ids[cell % ids.length], 0));
        ByteBuf bytes = Unpooled.wrappedBuffer(serialized(storage));
        try {
            StateBlockStorage restored = new StateBlockStorage();
            restored.readFromStorage(bytes, null);
            for (int cell = 0; cell < 4096; cell++) assertSame(storage.getBlockState(cell), restored.getBlockState(cell));
        } finally { bytes.release(); }
    }

    @Test
    void customNonzeroSingletonStillCopiesIndicesWhenGrown() throws Exception {
        BitArray bits = BitArrayVersion.V0.createPalette(4096);
        bits.set(0, 1);
        StateBlockStorage storage = new StateBlockStorage(bits, new java.util.ArrayList<>(java.util.List.of(
                BlockStateMapping.get().getState(BlockID.AIR, 0),
                BlockStateMapping.get().getState(BlockID.STONE, 0))), null, null);
        var grow = StateBlockStorage.class.getDeclaredMethod("grow", BitArrayVersion.class);
        grow.setAccessible(true);
        grow.invoke(storage, BitArrayVersion.V2);
        for (int cell = 0; cell < 4096; cell++) assertEquals(BlockID.STONE, storage.getBlockState(cell).getLegacyId());
    }

    @Test
    void everySupportedProtocolKeepsThePreviousV2NetworkBytes() {
        var versions = java.util.Arrays.stream(GameVersion.getValues())
                .filter(v -> v.getProtocol() >= 589 && v.getProtocol() <= 2193).toList();
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 589));
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 2193));
        boolean originalHashedIds = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (int scenario = 0; scenario < 4; scenario++) {
                StateBlockStorage before = new StateBlockStorage(BitArrayVersion.V2);
                StateBlockStorage after = new StateBlockStorage();
                int[] ids = {BlockID.STONE, BlockID.DIRT, BlockID.DIAMOND_ORE, BlockID.WATER};
                int writes = switch (scenario) { case 0 -> 0; case 1 -> 1; case 2 -> 2; default -> 4096; };
                for (int cell = 0; cell < writes; cell++) {
                    var state = BlockStateMapping.get().getState(ids[cell % ids.length], 0);
                    before.set(cell, state);
                    after.set(cell, state);
                }
                for (GameVersion version : versions) {
                    for (boolean hashed : new boolean[]{false, true}) {
                        GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                        for (boolean antiXray : new boolean[]{false, true}) {
                            assertArrayEquals(networkBytes(before, version, antiXray), networkBytes(after, version, antiXray),
                                    version + ", scenario=" + scenario + ", hashed=" + hashed + ", antiXray=" + antiXray);
                        }
                    }
                }
            }
        } finally {
            GlobalBlockPalette.setUseHashedBlockNetworkIds(originalHashedIds);
        }
    }

    // Palette capacity: compaction drops unused historical capacity without changing current cells.

    @Test
    void compactionDropsUnusedHistoricalCapacityWithoutChangingCurrentCells() throws Exception {
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>(10001);
        palette.add(bareState(BlockID.AIR, 0));
        for (int i = 1; i <= 10000; i++) palette.add(bareState(BlockID.STONE, i));
        BitArray bits = BitArrayVersion.V16.createPalette(4096);
        bits.set(123, 10000);
        BlockStateSnapshot air = palette.get(0), last = palette.get(10000);
        StateBlockStorage storage = new StateBlockStorage(bits, palette, null, null);
        assertTrue(storage.compress());
        assertEquals(2, palette(storage).size());
        assertEquals(2, ((ObjectArrayList<?>) palette(storage)).elements().length);
        for (int cell = 0; cell < 4096; cell++) assertSame(cell == 123 ? last : air, storage.getBlockState(cell));
        var replacement = bareState(BlockID.DIRT, 0);
        storage.set(124, replacement);
        assertSame(replacement, storage.getBlockState(124));
        assertSame(last, storage.getBlockState(123));
    }

    @Test
    void allAirSpecialCaseAlsoReleasesHistoricalCapacity() throws Exception {
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>(10001);
        for (int i = 0; i <= 10000; i++) palette.add(bareState(BlockID.AIR, i));
        StateBlockStorage storage = new StateBlockStorage(BitArrayVersion.V16.createPalette(4096), palette, null, null);
        assertTrue(storage.compress());
        assertEquals(1, palette(storage).size());
        assertEquals(1, ((ObjectArrayList<?>) palette(storage)).elements().length);
        storage.set(123, bareState(BlockID.STONE, 0));
        assertEquals(BlockID.STONE, storage.getBlockState(123).getLegacyId());
        assertEquals(BlockID.AIR, storage.getBlockState(124).getLegacyId());
    }

    @Test
    void everySupportedProtocolKeepsThePreCompactionNetworkBytes() {
        var versions = java.util.Arrays.stream(GameVersion.getValues())
                .filter(v -> v.getProtocol() >= 589 && v.getProtocol() <= 2193).toList();
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 589));
        assertTrue(versions.stream().anyMatch(v -> v.getProtocol() == 2193));
        boolean originalHashedIds = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (int scenario = 0; scenario < 3; scenario++) {
                ObjectArrayList<BlockStateSnapshot> history = new ObjectArrayList<>(257);
                int[] ids = {BlockID.AIR, BlockID.STONE, BlockID.DIAMOND_ORE, BlockID.WATER};
                for (int entry = 0; entry < 257; entry++) {
                    history.add(BlockStateMapping.get().getState(scenario == 0 ? BlockID.AIR : ids[entry % ids.length], 0));
                }
                BitArray bits = BitArrayVersion.V16.createPalette(4096);
                for (int cell = 0; cell < 4096; cell++) bits.set(cell, scenario == 2 ? cell % 257 : 255);
                StateBlockStorage after = new StateBlockStorage(bits, history, null, null);
                StateBlockStorage before = after.copy();
                assertTrue(after.compress());
                for (GameVersion version : versions) {
                    for (boolean hashed : new boolean[]{false, true}) {
                        GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                        for (boolean antiXray : new boolean[]{false, true}) {
                            assertArrayEquals(networkBytes(before, version, antiXray), networkBytes(after, version, antiXray),
                                    version + ", scenario=" + scenario + ", hashed=" + hashed + ", antiXray=" + antiXray);
                        }
                    }
                }
            }
        } finally {
            GlobalBlockPalette.setUseHashedBlockNetworkIds(originalHashedIds);
        }
    }

    // Compression: palette compaction must preserve every cell, palette order, bit words and serialized NBT.

    private static final int SIZE = 4096;

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 16, 17, 256, 257, 1024, 4096})
    void preservesEveryCellPaletteOrderBitWordsAndSerializedNbt(int count) throws Exception {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        for (int i = 0; i < count; i++) palette.add(state(i == 0 ? BlockID.AIR : 1 + i % 250, i, i));
        // Same object under a second palette index is legal after legacy state upgrades.
        if (count > 3) palette.set(2, palette.get(1));
        int[] cells = new int[SIZE];
        Random random = new Random(713L + count);
        for (int i = 0; i < SIZE; i++) cells[i] = i < count ? count - 1 - i : random.nextInt(count);
        assertEquivalent(palette, cells);
    }

    @Test
    void unusedEntryZeroSurvivesAndUnusedOtherEntriesDisappear() throws Exception {
        var air = state(BlockID.AIR, 0, 0);
        var stone = state(BlockID.STONE, 0, 1);
        List<BlockStateSnapshot> palette = List.of(air, state(BlockID.DIRT, 0, 2), stone);
        int[] cells = new int[SIZE];
        Arrays.fill(cells, 2);
        StateBlockStorage actual = assertEquivalent(palette, cells);
        assertEquals(List.of(air, stone), palette(actual));
    }

    @Test
    void allAirVariantsUseExistingSpecialCaseAndOneAirRemainsUnchanged() throws Exception {
        int[] cells = new int[SIZE];
        for (int i = 0; i < SIZE; i++) cells[i] = i % 2;
        var first = state(BlockID.AIR, 0, 1);
        StateBlockStorage actual = assertEquivalent(List.of(first, state(BlockID.AIR, 1, 2)), cells);
        assertEquals(List.of(first), palette(actual));
        assertEquals(BitArrayVersion.V1, bits(actual).getVersion());
        assertEquivalent(List.of(first), new int[SIZE]);
    }

    @Test
    void emptyPaletteRetainsExistingNoOp() {
        StateBlockStorage storage = new StateBlockStorage(BitArrayVersion.V2.createPalette(SIZE), new ArrayList<>(), null, null);
        assertFalse(storage.compress());
    }

    @Test
    void layerStoragesDoNotSharePalettesAndCompressionAllowsLaterMutation() throws Exception {
        List<BlockStateSnapshot> palette = List.of(state(BlockID.AIR, 0, 0), state(BlockID.STONE, 1, 1));
        int[] cells = new int[SIZE];
        Arrays.fill(cells, 1);
        StateBlockStorage layer0 = assertEquivalent(palette, cells);
        StateBlockStorage layer1 = storage(palette, new int[SIZE]);
        layer1.compress();
        var replacement = state(BlockID.WATER, 3, 8);
        layer0.set(123, replacement);
        assertSame(replacement, layer0.getBlockState(123));
        assertSame(palette.get(0), layer1.getBlockState(123));
        layer0.compress();
        assertSame(replacement, layer0.getBlockState(123));
        assertSame(palette.get(1), layer0.getBlockState(122));
    }

    private static StateBlockStorage assertEquivalent(List<BlockStateSnapshot> input, int[] cells) throws Exception {
        StateBlockStorage expected = storage(input, cells);
        StateBlockStorage actual = storage(input, cells);
        // Cached legacy arrays must remain consistent with the same cells.
        expected.getBlockIds(); expected.getBlockData();
        actual.getBlockIds(); actual.getBlockData();
        assertEquals(referenceCompress(expected), actual.compress());
        assertEquals(palette(expected), palette(actual));
        assertEquals(bits(expected).getVersion(), bits(actual).getVersion());
        assertArrayEquals(bits(expected).getWords(), bits(actual).getWords());
        for (int i = 0; i < SIZE; i++) assertSame(expected.getBlockState(i), actual.getBlockState(i), "cell " + i);
        assertArrayEquals(serialized(expected), serialized(actual));
        assertArrayEquals(expected.getBlockIds(), actual.getBlockIds());
        assertArrayEquals(expected.getBlockData(), actual.getBlockData());
        assertEquals(referenceCompress(expected), actual.compress());
        assertArrayEquals(serialized(expected), serialized(actual), "second compression must have the same semantics");
        return actual;
    }

    private static StateBlockStorage storage(List<BlockStateSnapshot> palette, int[] cells) {
        BitArray array = BitArrayVersion.V16.createPalette(SIZE);
        for (int i = 0; i < SIZE; i++) array.set(i, cells[i]);
        return new StateBlockStorage(array, new ArrayList<>(palette), null, null);
    }

    private static BlockStateSnapshot state(int legacyId, int legacyData, int unique) {
        return BlockStateSnapshot.builder().legacyId(legacyId).legacyData(legacyData).runtimeId(unique)
                .vanillaState(NbtMap.builder().putString("name", "test:block_" + unique)
                        .putCompound("states", NbtMap.EMPTY).putInt("version", 1).build()).build();
    }

    // Previous implementation as an oracle: compare actual serialized storage, not
    // merely each implementation's reconstructed legacy ids (which can be equal
    // for different custom block states).
    private static boolean referenceCompress(StateBlockStorage storage) throws Exception {
        List<BlockStateSnapshot> palette = palette(storage);
        if (palette.isEmpty()) return false;
        int count = palette.size();
        if (count == 1 && palette.get(0).getLegacyId() == BlockID.AIR) return false;
        if (palette.stream().allMatch(s -> s.getLegacyId() == BlockID.AIR)) {
            var first = palette.get(0); palette.clear(); palette.add(first);
            setBits(storage, BitArrayVersion.V1.createPalette(SIZE));
            return true;
        }
        BitArrayVersion version = BitArrayVersion.V2;
        BitArray original = bits(storage);
        BitArray compacted = version.createPalette(SIZE);
        List<BlockStateSnapshot> compactPalette = new ArrayList<>();
        compactPalette.add(palette.get(0));
        for (int i = 0; i < SIZE; i++) {
            var snapshot = palette.get(original.get(i));
            int newIndex = compactPalette.indexOf(snapshot);
            if (newIndex == -1) {
                newIndex = compactPalette.size(); compactPalette.add(snapshot);
                if (newIndex > version.getMaxEntryValue()) {
                    version = version.next();
                    BitArray grown = version.createPalette(SIZE);
                    for (int j = 0; j < i; j++) grown.set(j, compacted.get(j));
                    compacted = grown;
                }
            }
            compacted.set(i, newIndex);
        }
        setBits(storage, compacted);
        palette.clear(); palette.addAll(compactPalette);
        return true;
    }

    private static void setBits(StateBlockStorage storage, BitArray bits) throws Exception {
        Field field = StateBlockStorage.class.getDeclaredField("bitArray"); field.setAccessible(true); field.set(storage, bits);
    }

    /*
     * writeTo translates each palette entry once instead of every cell. For every protocol of the client window
     * 1.20.0 (589) … 1.26.50 (2193), NetEase included, with plain and hashed network ids and with and without
     * anti-xray, the encoded section must be byte for byte the former per-cell encoding: empty and one-state
     * sections, unused and duplicated palette entries, states without a mapping, and palettes up to 16 bits.
     * Equal bytes decode to equal blocks on every client.
     */

    private static final int MIN_PROTOCOL = 589;
    private static final int MAX_PROTOCOL = 2193;

    private static List<GameVersion> window() {
        List<GameVersion> versions = new ArrayList<>();
        for (GameVersion version : GameVersion.getValues()) {
            if (version.getProtocol() >= MIN_PROTOCOL && version.getProtocol() <= MAX_PROTOCOL) {
                versions.add(version);
            }
        }
        return versions;
    }

    /** The encoder this patch replaced, verbatim: one protocol palette lookup per cell. */
    private static byte[] perCellReference(StateBlockStorage storage, GameVersion protocol, boolean antiXray) {
        PalettedBlockStorage palettedBlockStorage = PalettedBlockStorage.createFromBlockPalette(protocol);
        assertTrue(protocol.getProtocol() >= ProtocolInfo.v1_16_100);
        BlockPalette blockPalette = GlobalBlockPalette.getPaletteByProtocol(protocol);
        boolean useHash = GlobalBlockPalette.shouldUseHashedBlockNetworkIds(protocol);
        for (int i = 0; i < 4096; i++) {
            int fullId = storage.get(i);
            int id = fullId >> Block.DATA_BITS;
            int meta = fullId & Block.DATA_MASK;
            if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                id = Block.STONE;
                meta = 0;
            }
            palettedBlockStorage.setBlock(i, useHash ? blockPalette.getHashId(id, meta)
                    : blockPalette.getRuntimeId(id, meta));
        }
        BinaryStream stream = new BinaryStream();
        palettedBlockStorage.writeTo(stream);
        return stream.getBuffer();
    }

    private static BlockStateSnapshot state(int id, int meta) {
        return BlockStateMapping.get().getState(id, meta);
    }

    /** Every vanilla state the mapping knows, plus a few it has to fall back for. */
    private static List<BlockStateSnapshot> statePool() {
        List<BlockStateSnapshot> pool = new ArrayList<>();
        for (int id = 1; id < Block.list.length; id++) {
            if (Block.list[id] == null) continue;
            for (int meta = 0; meta < 16; meta++) {
                BlockStateSnapshot snapshot;
                try {
                    snapshot = state(id, meta);
                } catch (RuntimeException unmapped) {
                    continue;
                }
                if (snapshot != null) pool.add(snapshot);
            }
        }
        return pool;
    }

    /** A storage with exactly this palette (duplicates and unused entries allowed) and these cell indices. */
    private static StateBlockStorage raw(List<BlockStateSnapshot> palette, int[] cells) {
        int max = Arrays.stream(cells).max().orElse(0);
        BitArrayVersion version = BitArrayVersion.V1;
        while (version.getMaxEntryValue() < Math.max(max, palette.size() - 1)) {
            version = version.next();
        }
        BitArray bits = version.createPalette(4096);
        for (int i = 0; i < 4096; i++) {
            bits.set(i, cells[i]);
        }
        return new StateBlockStorage(bits, new ObjectArrayList<>(palette), null, null);
    }

    private static List<StateBlockStorage> sections(List<BlockStateSnapshot> pool) {
        Random random = new Random(589_2193L);
        List<StateBlockStorage> sections = new ArrayList<>();

        sections.add(new StateBlockStorage()); // empty: palette [air], every cell air

        StateBlockStorage single = new StateBlockStorage(); // one state, air left unused in the palette
        for (int i = 0; i < 4096; i++) single.set(i, state(Block.STONE, 0));
        sections.add(single);

        StateBlockStorage ores = new StateBlockStorage(); // anti-xray targets next to their replacement
        int[][] oreStates = {{Block.DIAMOND_ORE, 0}, {Block.STONE, 0}, {Block.GOLD_ORE, 0}, {Block.AIR, 0},
                {Block.IRON_ORE, 0}, {Block.EMERALD_ORE, 0}, {Block.LAPIS_ORE, 0}, {Block.COAL_ORE, 0}};
        for (int i = 0; i < 4096; i++) {
            int[] s = oreStates[random.nextInt(oreStates.length)];
            ores.set(i, state(s[0], s[1]));
        }
        sections.add(ores);

        // Duplicated and unused palette entries, as a storage read from disk may carry them.
        BlockStateSnapshot stone = state(Block.STONE, 0);
        List<BlockStateSnapshot> duplicated = List.of(state(Block.AIR, 0), stone, state(Block.DIRT, 0), stone,
                state(Block.DIAMOND_ORE, 0), state(Block.GRASS, 0), state(Block.DIAMOND_ORE, 0));
        int[] cells = new int[4096];
        for (int i = 0; i < 4096; i++) cells[i] = random.nextInt(5) == 0 ? 3 : random.nextInt(5);
        sections.add(raw(duplicated, cells));

        // A state no protocol maps (falls back to info_update).
        StateBlockStorage unmapped = new StateBlockStorage();
        BlockStateSnapshot odd = BlockStateMapping.get().getBlockStateFromFullId(Block.STONE << Block.DATA_BITS | 15);
        for (int i = 0; i < 4096; i++) unmapped.set(i, i % 3 == 0 ? odd : stone);
        sections.add(unmapped);

        // Random mixes from a few states up to a 16-bit network palette.
        for (int distinct : new int[]{2, 7, 17, 33, 65, 200, 700, 3000}) {
            StateBlockStorage mixed = new StateBlockStorage();
            List<BlockStateSnapshot> chosen = new ArrayList<>();
            for (int k = 0; k < distinct; k++) chosen.add(pool.get(random.nextInt(pool.size())));
            for (int i = 0; i < 4096; i++) mixed.set(i, chosen.get(random.nextInt(chosen.size())));
            sections.add(mixed);
        }
        return sections;
    }

    @Test
    void everyProtocolOfTheWindowEncodesSectionsByteForByteAsBefore() {
        List<GameVersion> window = window();
        assertTrue(window.stream().anyMatch(v -> v.getProtocol() == MIN_PROTOCOL), "1.20.0 is in the window");
        assertTrue(window.stream().anyMatch(v -> v.getProtocol() == MAX_PROTOCOL), "1.26.50 is in the window");
        List<StateBlockStorage> sections = sections(statePool());
        int compared = 0;
        for (GameVersion protocol : window) {
            for (boolean hashed : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                for (boolean antiXray : new boolean[]{false, true}) {
                    for (int s = 0; s < sections.size(); s++) {
                        StateBlockStorage section = sections.get(s);
                        byte[] expected = perCellReference(section, protocol, antiXray);
                        BinaryStream actual = new BinaryStream();
                        section.writeTo(protocol, actual, antiXray);
                        assertArrayEquals(expected, actual.getBuffer(), protocol + " (" + protocol.getProtocol()
                                + "), hashed=" + hashed + ", antiXray=" + antiXray + ", section " + s);
                        compared++;
                    }
                }
            }
        }
        assertTrue(compared >= window.size() * 4 * 13, "compared " + compared);
    }

    @Test
    void aCellPointingPastThePaletteFailsLikeBefore() {
        GameVersion protocol = GameVersion.getLastVersion();
        int[] cells = new int[4096];
        cells[100] = 5; // palette has two entries
        StateBlockStorage broken = raw(List.of(state(Block.AIR, 0), state(Block.STONE, 0)), cells);
        RuntimeException before = assertThrows(RuntimeException.class, () -> perCellReference(broken, protocol, false));
        RuntimeException after = assertThrows(RuntimeException.class, () -> broken.writeTo(protocol, new BinaryStream(), false));
        assertEquals(before.getClass(), after.getClass());
    }

    private static byte[] networkBytes(StateBlockStorage storage, GameVersion version, boolean antiXray) {
        BinaryStream stream = new BinaryStream();
        storage.writeTo(version, stream, antiXray);
        return stream.getBuffer();
    }

    private static BitArray bits(StateBlockStorage storage) throws Exception {
        Field f = StateBlockStorage.class.getDeclaredField("bitArray"); f.setAccessible(true);
        return (BitArray) f.get(storage);
    }

    private static byte[] serialized(StateBlockStorage storage) {
        ByteBuf bytes = Unpooled.buffer();
        try { storage.writeToStorage(bytes); byte[] result = new byte[bytes.readableBytes()]; bytes.readBytes(result); return result; }
        finally { bytes.release(); }
    }

    private static BlockStateSnapshot bareState(int id, int data) {
        return BlockStateSnapshot.builder().legacyId(id).legacyData(data).build();
    }

    @SuppressWarnings("unchecked")
    private static List<BlockStateSnapshot> palette(StateBlockStorage storage) throws Exception {
        Field f = StateBlockStorage.class.getDeclaredField("palette"); f.setAccessible(true);
        return (List<BlockStateSnapshot>) f.get(storage);
    }
}
