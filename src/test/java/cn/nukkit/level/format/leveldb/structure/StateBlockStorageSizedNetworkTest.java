package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StateBlockStorageSizedNetworkTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    // The upstream master writer, kept independent of the candidate's palette sizing.
    private static byte[] reference(StateBlockStorage storage, List<BlockStateSnapshot> palette,
                                    BitArray cells, GameVersion version, boolean antiXray) {
        PalettedBlockStorage output = PalettedBlockStorage.createFromBlockPalette(version);
        if (version.getProtocol() >= ProtocolInfo.v1_16_100) {
            BlockPalette protocolPalette = GlobalBlockPalette.getPaletteByProtocol(version);
            boolean useHash = GlobalBlockPalette.shouldUseHashedBlockNetworkIds(version);
            for (int i = 0; i < 4096; i++) {
                int fullId = storage.get(i);
                int id = fullId >> Block.DATA_BITS;
                int meta = fullId & Block.DATA_MASK;
                if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                    id = Block.STONE;
                    meta = 0;
                }
                output.setBlock(i, useHash ? protocolPalette.getHashId(id, meta)
                        : protocolPalette.getRuntimeId(id, meta));
            }
        } else {
            for (int i = 0; i < 4096; i++) {
                int fullId = storage.get(i);
                int id = fullId >> Block.DATA_BITS;
                int meta = fullId & Block.DATA_MASK;
                if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                    id = Block.STONE;
                    meta = 0;
                }
                output.setBlock(i, GlobalBlockPalette.getOrCreateRuntimeId(version, id, meta));
            }
        }
        BinaryStream stream = new BinaryStream();
        output.writeTo(stream);
        return stream.getBuffer();
    }

    private static List<BlockStateSnapshot> uniqueStates() {
        BlockPalette protocolPalette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.getLastVersion());
        HashSet<Integer> seen = new HashSet<>();
        List<BlockStateSnapshot> states = new ArrayList<>();
        for (int id = 0; id < Block.list.length && states.size() < 300; id++) {
            if (Block.list[id] == null) continue;
            for (int meta = 0; meta < 16 && states.size() < 300; meta++) {
                BlockStateSnapshot state = BlockStateMapping.get().getState(id, meta);
                if (state != null && seen.add(protocolPalette.getRuntimeId(state.getLegacyId(), state.getLegacyData()))) {
                    states.add(state);
                }
            }
        }
        assertEquals(300, states.size());
        return states;
    }

    @Test
    void allWidthsKeepExactBytesAndDoNotMutateSourceAcrossTheClientWindow() {
        List<BlockStateSnapshot> states = uniqueStates();
        boolean originalHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        int comparisons = 0;
        try {
            for (int distinct : new int[]{1, 2, 3, 4, 5, 8, 9, 16, 17, 32, 33, 64, 65, 256, 257}) {
                // Reverse first-use order; keep two duplicate aliases and unused entries at the end.
                List<BlockStateSnapshot> palette = new ObjectArrayList<>(states.subList(0, distinct));
                palette.add(states.get(1));
                palette.add(states.get(1));
                palette.add(states.get(299));
                BitArrayVersion bitsVersion = BitArrayVersion.V2;
                while (bitsVersion.getMaxEntryValue() < palette.size() - 1) bitsVersion = bitsVersion.next();
                BitArray cells = bitsVersion.createPalette(4096);
                for (int i = 0; i < 4096; i++) cells.set(i, distinct - 1 - i % distinct);
                cells.set(4000, distinct); // A second local entry resolves to an already-used runtime ID.
                StateBlockStorage storage = new StateBlockStorage(cells, palette, null, null);
                int[] beforeWords = cells.getWords().clone();
                List<BlockStateSnapshot> beforePalette = new ArrayList<>(palette);
                for (GameVersion version : GameVersion.getValues()) {
                    // Earlier chunk formats do not use this runtime-ID writer.
                    if (version.getProtocol() < ProtocolInfo.v1_2_13) continue;
                    for (boolean hash : new boolean[]{false, true}) {
                        GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                        for (boolean antiXray : new boolean[]{false, true}) {
                            BinaryStream result = new BinaryStream();
                            storage.writeTo(version, result, antiXray);
                            assertArrayEquals(reference(storage, palette, cells, version, antiXray), result.getBuffer(),
                                    version + "/" + distinct + "/hash=" + hash + "/xray=" + antiXray);
                            comparisons++;
                        }
                    }
                }
                assertArrayEquals(beforeWords, cells.getWords());
                assertEquals(beforePalette, palette);
            }
        } finally {
            GlobalBlockPalette.setUseHashedBlockNetworkIds(originalHash);
        }
        assertTrue(comparisons > 2000, "comparisons=" + comparisons);
        System.out.println("Sized network palette byte comparisons=" + comparisons);
    }

    @Test
    void stalePaletteDoesNotSelectTheWordWidthOrExposeUnusedStates() {
        List<BlockStateSnapshot> palette = uniqueStates();
        BitArray cells = BitArrayVersion.V16.createPalette(4096);
        for (int i = 0; i < 4096; i++) cells.set(i, 299);
        StateBlockStorage storage = new StateBlockStorage(cells, palette, null, null);
        BinaryStream result = new BinaryStream();
        storage.writeTo(GameVersion.getLastVersion(), result, true);
        assertArrayEquals(reference(storage, palette, cells, GameVersion.getLastVersion(), true), result.getBuffer());
        assertEquals(5, result.getBuffer()[0]); // V2 even when local storage uses V16.
    }

    @Test
    void corruptCellAfterAllPaletteEntriesWereDiscoveredKeepsTheOriginalException() {
        List<BlockStateSnapshot> palette = uniqueStates().subList(0, 5);
        BitArray cells = BitArrayVersion.V4.createPalette(4096);
        for (int i = 0; i < 5; i++) cells.set(i, i);
        cells.set(4000, 9);
        StateBlockStorage storage = new StateBlockStorage(cells, palette, null, null);
        RuntimeException before = assertThrows(RuntimeException.class,
                () -> reference(storage, palette, cells, GameVersion.getLastVersion(), false));
        RuntimeException after = assertThrows(RuntimeException.class,
                () -> storage.writeTo(GameVersion.getLastVersion(), new BinaryStream(), false));
        assertEquals(before.getClass(), after.getClass());
    }

    @Test
    void corruptWidePaletteKeepsTheOriginalException() {
        List<BlockStateSnapshot> palette = uniqueStates().subList(0, 5);
        BitArray cells = BitArrayVersion.V4.createPalette(4096);
        cells.set(12, 9);
        StateBlockStorage storage = new StateBlockStorage(cells, palette, null, null);
        RuntimeException before = assertThrows(RuntimeException.class,
                () -> reference(storage, palette, cells, GameVersion.getLastVersion(), false));
        RuntimeException after = assertThrows(RuntimeException.class,
                () -> storage.writeTo(GameVersion.getLastVersion(), new BinaryStream(), false));
        assertEquals(before.getClass(), after.getClass());
    }
}
