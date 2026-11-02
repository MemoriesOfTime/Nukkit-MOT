package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StateBlockStorageCompressionTest {
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

    private static byte[] serialized(StateBlockStorage storage) {
        ByteBuf buf = Unpooled.buffer();
        try {
            storage.writeToStorage(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally { buf.release(); }
    }

    @SuppressWarnings("unchecked")
    private static List<BlockStateSnapshot> palette(StateBlockStorage storage) throws Exception {
        Field field = StateBlockStorage.class.getDeclaredField("palette"); field.setAccessible(true);
        return (List<BlockStateSnapshot>) field.get(storage);
    }

    private static BitArray bits(StateBlockStorage storage) throws Exception {
        Field field = StateBlockStorage.class.getDeclaredField("bitArray"); field.setAccessible(true);
        return (BitArray) field.get(storage);
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
}
