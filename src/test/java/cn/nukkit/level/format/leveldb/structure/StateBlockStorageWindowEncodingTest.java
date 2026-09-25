package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * writeTo translates each palette entry once instead of every cell. For every protocol of the client window
 * 1.20.0 (589) … 1.26.50 (2193), NetEase included, with plain and hashed network ids and with and without
 * anti-xray, the encoded section must be byte for byte the former per-cell encoding: empty and one-state
 * sections, unused and duplicated palette entries, states without a mapping, and palettes up to 16 bits.
 * Equal bytes decode to equal blocks on every client.
 */
class StateBlockStorageWindowEncodingTest {
    private static final int MIN_PROTOCOL = 589;
    private static final int MAX_PROTOCOL = 2193;

    private boolean originalHashedIds;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void saveNetworkIdSetting() {
        originalHashedIds = GlobalBlockPalette.useHashedBlockNetworkIds();
    }

    @AfterEach
    void restoreNetworkIdSetting() {
        GlobalBlockPalette.setUseHashedBlockNetworkIds(originalHashedIds);
    }

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
}
