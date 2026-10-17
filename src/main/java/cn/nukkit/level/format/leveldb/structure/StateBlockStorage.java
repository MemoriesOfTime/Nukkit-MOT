package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.anvil.util.BlockStorage;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.updater.BlockStateUpdaterVanilla;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.SUB_CHUNK_SIZE;

@Log4j2
public class StateBlockStorage {

    /**
     * PocketMine-MP writes this private key into every block state it stores. Vanilla palettes
     * do not contain it and states are matched as whole NBT maps, so a world saved by PMMP
     * matches nothing at all - down to minecraft:air - and loads as solid info_update.
     * Dropping the key here keeps everything downstream working with plain vanilla states.
     */
    private static final String PMMP_DATA_VERSION = "PMMPDataVersion";

    private static final int SECTION_SIZE = 16 * 16 * 16;

    // No chunk/provider references. Limit retained serialized bytes across all loaded sections/versions.
    private static final Cache<NetworkKey, byte[]> NETWORK_BYTES = Caffeine.newBuilder()
            .maximumWeight(Math.min(32L << 20, Runtime.getRuntime().maxMemory() / 64))
            .weigher((NetworkKey key, byte[] bytes) -> bytes.length + 256 + 5 * key.paletteSize())
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    private List<BlockStateSnapshot> palette;
    private BitArray bitArray;
    // Snapshot acknowledgements must also notice public storage mutations that bypass section setters.
    private volatile int version;
    private volatile NetworkSnapshot networkSnapshot;
    private boolean networkCacheable = true;

    //用于兼容1.13以下版本
    private byte[] blockIds;
    private NibbleArray blockData;

    public StateBlockStorage() {
        this(BitArrayVersion.V2);
    }

    public StateBlockStorage(BitArrayVersion version) {
        this.bitArray = version.createPalette();
        this.palette = new ObjectArrayList<>(16);
        this.palette.add(BlockStateMapping.get().getState(0, 0));

        this.blockIds = null;
        this.blockData = null;
    }

    protected StateBlockStorage(BitArray bitArray, List<BlockStateSnapshot> palette, byte[] blockIds, NibbleArray blockData) {
        // Protected callers retain access to these arrays/lists; their edits have no version callback.
        this.networkCacheable = false;
        this.palette = palette;
        this.bitArray = bitArray;
        this.blockIds = blockIds;
        this.blockData = blockData;
    }

    private static int getPaletteHeader(BitArrayVersion version, boolean runtime) {
        return (version.getId() << 1) | (runtime ? 1 : 0);
    }

    public void writeToStorage(ByteBuf byteBuf) {
        int paletteSize = this.palette.size();
        BitArrayVersion version = paletteSize <= 1 ? BitArrayVersion.V0 : bitArray.getVersion();
        byteBuf.writeByte(getPaletteHeader(version, false));

        if (version != BitArrayVersion.V0) {
            for (int word : bitArray.getWords()) {
                byteBuf.writeIntLE(word);
            }

            byteBuf.writeIntLE(paletteSize);
        }

        NBTOutputStream outputStream = null;
        try {
            outputStream = NbtUtils.createWriterLE(new ByteBufOutputStream(byteBuf));

            for (BlockStateSnapshot state : this.palette) {
                outputStream.writeTag(state.getVanillaState());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error("Failed to close NBT stream", e);
                }
            }
        }
    }

    public synchronized void readFromStorage(ByteBuf buffer, ChunkBuilder chunkBuilder) {
        this.networkSnapshot = null;
        this.version++;
        short header = buffer.readUnsignedByte();

        if (header == -1) {
            return;
        }

        this.palette.clear();

        BitArrayVersion version = BitArrayVersion.get(header >> 1, true);

        int paletteSize = 1;
        if (version == BitArrayVersion.V0) {
            this.bitArray = version.createPalette(SUB_CHUNK_SIZE, null);
        } else {
            int expectedWordSize = version.getWordsForSize(SUB_CHUNK_SIZE);
            int[] words = new int[expectedWordSize];
            for (int i = 0; i < expectedWordSize; ++i) {
                words[i] = buffer.readIntLE();
            }
            paletteSize = buffer.readIntLE();
            this.bitArray = version.createPalette(SUB_CHUNK_SIZE, words);
        }

        if (version.getMaxEntryValue() < paletteSize - 1) {
            throw new IllegalArgumentException(
                    chunkBuilder.debugString() + " Palette (version " + version.name() + ") is too large. Max size " + version.getMaxEntryValue() + ". Actual size " + paletteSize
            );
        }

        NBTInputStream inputStream = null;
        try {
            inputStream = NbtUtils.createReaderLE(new ByteBufInputStream(buffer));
            for (int i = 0; i < paletteSize; ++i) {
                try {
                    NbtMap state = (NbtMap) inputStream.readTag();
                    if (state.containsKey(PMMP_DATA_VERSION)) {
                        NbtMapBuilder withoutForeignKeys = state.toBuilder();
                        withoutForeignKeys.remove(PMMP_DATA_VERSION);
                        state = withoutForeignKeys.build();
                    }
                    //noinspection ResultOfMethodCallIgnored
                    state.hashCode(); // cache hashCode

                    BlockStateSnapshot blockState = BlockStateMapping.get().getStateUnsafe(state);
                    if (blockState == null) {
                        // 磁盘状态缺 1.26.50 corner/connection 键 => 区块发送前需按邻居重算连接位
                        // Serialized state lacks 1.26.50 corner/connection keys => recompute from neighbours before sending
                        if (BlockStateUpdaterVanilla.needsConnectionRecompute(state)) {
                            chunkBuilder.needsLegacyConnectionFix();
                        }
                        NbtMap updatedState = BlockStateMapping.get().updateVanillaState(state);
                        blockState = BlockStateMapping.get().getUpdatedOrCustom(state, updatedState);
                        if (!blockState.isCustom()) {
                            if (Nukkit.DEBUG > 1) {
                                log.info("[{}] Updated unmapped block state: {} => {}", chunkBuilder.debugString(), state, blockState.getVanillaState());
                            }
                            chunkBuilder.dirty();
                        }

                        if (Nukkit.DEBUG > 1 && blockState.getRuntimeId() == BlockStateMapping.get().getDefaultRuntimeId()) {
                            log.info("[{}] Chunk contains unknown block {}  => {}", chunkBuilder.debugString(), state, updatedState);
                        }
                    }

                    if (Nukkit.DEBUG > 1 && this.palette.contains(blockState)) {
                        log.info("[{}] Palette contains block state twice: {}", chunkBuilder.debugString(), state);
                    }
                    this.palette.add(blockState);
                } catch (Exception e) {
                    log.error("[{}] Unable to deserialize chunk block state", chunkBuilder.debugString(), e);
                }
            }
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                log.error("Failed to close NBT stream", e);
            }
        }
    }

    public BlockStateSnapshot getBlockState(int index) {
        return this.palette.get(this.bitArray.get(index));
    }

    public BlockStateSnapshot getBlockState(int x, int y, int z) {
        return this.getBlockState(elementIndex(x, y, z));
    }

    public int get(int index) {
        BlockStateSnapshot snapshot = this.palette.get(this.bitArray.get(index));
        return snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData();
    }

    public int get(int x, int y, int z) {
        return this.get(elementIndex(x, y, z));
    }

    public int get(BlockVector3 pos) {
        return this.get(elementIndex(pos.x, pos.y, pos.z));
    }

    public synchronized void set(int index, BlockStateSnapshot value) {
        this.networkSnapshot = null;
        try {
            int paletteIndex = this.getOrAdd(value);
            this.bitArray.set(index, paletteIndex);
            if(this.blockIds != null && this.blockData != null) {
                this.blockIds[index] = (byte) (value.getLegacyId() & 0xff);
                this.blockData.set(index, (byte) (value.getLegacyData() & 0xf));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to set value: " + value + ", palette: " + palette, e);
        } finally {
            this.version++;
        }
    }

    public int getVersion() {
        return this.version;
    }

    public void set(int x, int y, int z, BlockStateSnapshot value) {
        this.set(elementIndex(x, y, z), value);
    }

    public void set(int x, int y, int z, int value) {
        this.set(elementIndex(x, y, z), BlockStateMapping.get().getBlockStateFromFullId(value));
    }

    public void set(BlockVector3 pos, int value) {
        this.set(elementIndex(pos.x, pos.y, pos.z), BlockStateMapping.get().getBlockStateFromFullId(value));
    }

    @Deprecated
    public void writeTo(int protocol, BinaryStream stream, boolean antiXray) {
        this.writeTo(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), stream, antiXray);
    }

    public synchronized void writeTo(GameVersion protocol, BinaryStream stream, boolean antiXray) {
        if (!this.networkCacheable || getClass() != StateBlockStorage.class || protocol.getProtocol() < ProtocolInfo.v1_20_0_23
                || protocol.getProtocol() > ProtocolInfo.v1_26_50) {
            this.writeToUncached(protocol, stream, antiXray);
            return;
        }
        BlockPalette blockPalette = GlobalBlockPalette.getPaletteByProtocol(protocol);
        long paletteVersion = blockPalette.getNetworkVersion();
        if (blockPalette.getClass() != BlockPalette.class || paletteVersion < 0) {
            this.writeToUncached(protocol, stream, antiXray);
            return;
        }
        for (BlockStateSnapshot state : this.palette) {
            // Foreign-version snapshots may resolve legacy IDs through a replaceable live mapper on every read.
            if (state != null && (state.getClass() != BlockStateSnapshot.class
                    || state.getVersion() != BlockStateMapping.get().getVersion())) {
                this.writeToUncached(protocol, stream, antiXray);
                return;
            }
        }
        NetworkSnapshot snapshot = this.networkSnapshot();
        Object identity = snapshot.identity(this.palette, this.bitArray, antiXray);
        boolean useHash = GlobalBlockPalette.shouldUseHashedBlockNetworkIds(protocol);
        NetworkKey key = new NetworkKey(identity, blockPalette, paletteVersion, useHash, this.palette.size());
        // Caffeine's atomic load also coalesces concurrent cold requests from independent async copies.
        byte[] bytes = NETWORK_BYTES.get(key, ignored -> {
            BinaryStream encoded = new BinaryStream();
            this.writeToUncached(protocol, encoded, antiXray, blockPalette, useHash,
                    identity instanceof XrayMask mask ? mask.concealed : null);
            return encoded.getBuffer();
        });
        if (blockPalette.getNetworkVersion() != paletteVersion
                || GlobalBlockPalette.shouldUseHashedBlockNetworkIds(protocol) != useHash
                || snapshot.identity(this.palette, this.bitArray, antiXray) != identity) {
            NETWORK_BYTES.invalidate(key);
            this.writeToUncached(protocol, stream, antiXray);
            return;
        }
        stream.put(bytes);
    }

    private void writeToUncached(GameVersion protocol, BinaryStream stream, boolean antiXray) {
        BlockPalette blockPalette = protocol.getProtocol() >= ProtocolInfo.v1_16_100
                ? GlobalBlockPalette.getPaletteByProtocol(protocol) : null;
        boolean useHash = protocol.getProtocol() >= ProtocolInfo.v1_16_100
                && GlobalBlockPalette.shouldUseHashedBlockNetworkIds(protocol);
        this.writeToUncached(protocol, stream, antiXray, blockPalette, useHash, null);
    }

    private void writeToUncached(GameVersion protocol, BinaryStream stream, boolean antiXray,
                                 BlockPalette blockPalette, boolean useHash, boolean[] concealed) {
        if (protocol.getProtocol() >= ProtocolInfo.v1_16_100 && this.palette.size() > 4) {
            writeSizedNetworkPalette(stream, antiXray, blockPalette, useHash, concealed);
            return;
        }
        PalettedBlockStorage palettedBlockStorage = protocol.getProtocol() >= ProtocolInfo.v1_16_100
                ? PalettedBlockStorage.createWithDefaultState(useHash ? blockPalette.getHashId(0, 0) : blockPalette.getRuntimeId(0, 0))
                : PalettedBlockStorage.createFromBlockPalette(protocol);

        // Resolve the palette and network-ID format once per section, as in anvil BlockStorage.
        // Older protocols still need GlobalBlockPalette's legacy runtime-ID tables.
        if (protocol.getProtocol() >= ProtocolInfo.v1_16_100) {
            // A section holds 4096 cells but only a handful of states. Translate each palette entry once, when
            // a cell first uses it, instead of looking the state up in the protocol palette for every cell.
            // Cells are still fed to the network storage one by one in the same order with the same ids, so
            // the encoded section is byte for byte what the per-cell lookup produced, for every protocol.
            // The network palette index a cell gets depends only on its state and on which states came first,
            // so it is resolved once per storage palette entry as well; -1 marks an entry not met yet.
            int paletteSize = this.palette.size();
            int[] networkIndex = new int[paletteSize];
            Arrays.fill(networkIndex, -1);
            for (int i = 0; i < SECTION_SIZE; i++) {
                int local = this.bitArray.get(i);
                if (local >= 0 && local < paletteSize) {
                    int known = networkIndex[local];
                    if (known >= 0) {
                        palettedBlockStorage.setPaletteIndex(i, known);
                    } else {
                        BlockStateSnapshot snapshot = this.palette.get(local);
                        int networkId = networkId(blockPalette, useHash, antiXray,
                                snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData(), concealed, local);
                        networkIndex[local] = palettedBlockStorage.setBlockReturningIndex(i, networkId);
                    }
                } else {
                    // Out of the palette: keep the per-cell read, including the exception it throws.
                    palettedBlockStorage.setBlock(i, networkId(blockPalette, useHash, antiXray, get(i), concealed, local));
                }
            }
        } else {
            for (int i = 0; i < SECTION_SIZE; i++) {
                int fullId = get(i);
                int id = fullId >> Block.DATA_BITS;
                int meta = fullId & Block.DATA_MASK;
                if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                    id = Block.STONE;
                    meta = 0;
                }
                palettedBlockStorage.setBlock(i, GlobalBlockPalette.getOrCreateRuntimeId(protocol, id, meta));
            }
        }

        palettedBlockStorage.writeTo(stream);
    }

    private static int networkId(BlockPalette blockPalette, boolean useHash, boolean antiXray, int fullId,
                                 boolean[] concealed, int local) {
        int id = fullId >> Block.DATA_BITS;
        int meta = fullId & Block.DATA_MASK;
        boolean hide = concealed == null ? antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]
                : id < 0 ? Level.xrayableBlocks[id] : concealed[local];
        if (hide) {
            id = Block.STONE;
            meta = 0;
        }
        return useHash ? blockPalette.getHashId(id, meta) : blockPalette.getRuntimeId(id, meta);
    }

    /**
     * Discover the emitted palette before allocating cell words. Growing V2 through every intermediate
     * width allocates another 4096-cell array and copies all cells at each boundary. Keep the original
     * first-use ordering, air at index zero, alias merging and minimum V2 width on the wire.
     */
    private void writeSizedNetworkPalette(BinaryStream stream, boolean antiXray,
                                         BlockPalette blockPalette, boolean useHash, boolean[] concealed) {
        int[] networkIndex = new int[this.palette.size()];
        Arrays.fill(networkIndex, -1);
        IntArrayList networkPalette = new IntArrayList(16);
        Int2IntOpenHashMap paletteIndex = new Int2IntOpenHashMap(16);
        paletteIndex.defaultReturnValue(-1);
        int air = useHash ? blockPalette.getHashId(0, 0) : blockPalette.getRuntimeId(0, 0);
        networkPalette.add(air);
        paletteIndex.put(air, 0);

        int remaining = networkIndex.length;
        for (int i = 0; i < SECTION_SIZE; i++) {
            int local = this.bitArray.get(i);
            if (local < 0 || local >= networkIndex.length) {
                get(i); // Preserve the original palette-read exception for corrupt cell indices.
            }
            if (networkIndex[local] >= 0) {
                continue;
            }
            BlockStateSnapshot snapshot = this.palette.get(local);
            int fullId = snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData();
            int id = networkId(blockPalette, useHash, antiXray, fullId, concealed, local);
            int index = paletteIndex.get(id);
            if (index < 0) {
                index = networkPalette.size();
                networkPalette.add(id);
                paletteIndex.put(id, index);
            }
            networkIndex[local] = index;
            if (--remaining == 0) {
                break;
            }
        }

        BitArrayVersion version = BitArrayVersion.V2;
        while (networkPalette.size() - 1 > version.getMaxEntryValue()) {
            version = version.next();
        }
        BitArray words = version.createPalette(SECTION_SIZE);
        for (int i = 0; i < SECTION_SIZE; i++) {
            int local = this.bitArray.get(i);
            if (local < 0 || local >= networkIndex.length) {
                get(i); // Also validate cells beyond an early palette-discovery exit.
            }
            words.set(i, networkIndex[local]);
        }

        stream.putByte((byte) getPaletteHeader(version, true));
        for (int word : words.getWords()) {
            stream.putLInt(word);
        }
        stream.putVarInt(networkPalette.size());
        for (int i = 0; i < networkPalette.size(); i++) {
            stream.putVarInt(networkPalette.getInt(i));
        }
    }

    private void grow(BitArrayVersion version) {
        BitArray newBitArray = version.createPalette(SECTION_SIZE);
        for (int i = 0; i < SECTION_SIZE; i++) {
            newBitArray.set(i, this.bitArray.get(i));
        }
        this.bitArray = newBitArray;
    }

    /**
     * @return palette index
     */
    private int getOrAdd(BlockStateSnapshot snapshot) {
        int index = this.palette.indexOf(snapshot);
        if (index != -1) {
            return index;
        }

        index = this.palette.size();
        BitArrayVersion version = this.bitArray.getVersion();
        if (index > version.getMaxEntryValue()) {
            BitArrayVersion next = version.next();
            if (next != null) {
                this.grow(next);
            } else if (!this.compress()) {
                throw new IndexOutOfBoundsException("too many elements");
            }
        }
        this.palette.add(snapshot);
        return index;
    }

    public boolean isEmpty() {
        if (this.palette.size() == 1) {
            return true;
        }

        for (int word : this.bitArray.getWords()) {
            if (Integer.toUnsignedLong(word) != 0L) {
                return false;
            }
        }
        return true;
    }

    public byte[] getBlockIds() {
        if (!this.isEmpty()) {
            this.computeOldData();
            return Arrays.copyOf(blockIds, blockIds.length);
        } else {
            return new byte[BlockStorage.SECTION_SIZE];
        }
    }

    public byte[] getBlockData() {
        if (!this.isEmpty()) {
            this.computeOldData();
            return this.blockData.getData();
        } else {
            return new byte[2048];
        }
    }

    /**
     * @return dirty
     */
    public synchronized boolean compress() {
        if (this.palette.isEmpty()) {
            return false;
        }

        int count = this.palette.size();
        if (count == 1 && this.palette.get(0).getLegacyId() == BlockID.AIR) {
            return false;
        }

        boolean noBlock = true;
        for (int i = 0; i < count; i++) {
            int id = this.palette.get(i).getLegacyId();
            if (id == BlockID.AIR) {
                continue;
            }
            noBlock = false;
            break;
        }
        if (noBlock) {
            this.networkSnapshot = null;
            BlockStateSnapshot firstId = this.palette.get(0);
            this.palette.clear();
            this.palette.add(firstId);

//            Arrays.fill(this.bitArray.getWords(), 0);
            this.bitArray = BitArrayVersion.V1.createPalette(SECTION_SIZE);
            this.version++;
            return true;
        }

        BitArrayVersion version = BitArrayVersion.V2;
        BitArray newArray = version.createPalette(SECTION_SIZE);
        List<BlockStateSnapshot> newPalette = new ObjectArrayList<>(count);
        newPalette.add(this.palette.get(0));
        // Remap each old palette entry once, instead of searching an expanding list
        // for every cell. Keep entry zero and first-use order exactly as before.
        int[] remapped = new int[count];
        Arrays.fill(remapped, -1);
        remapped[0] = 0;
        Object2IntOpenHashMap<BlockStateSnapshot> indices = new Object2IntOpenHashMap<>(count);
        indices.defaultReturnValue(-1);
        indices.put(this.palette.get(0), 0);
        for (int i = 0; i < SECTION_SIZE; i++) {
            int paletteIndex = this.bitArray.get(i);
            int newIndex = remapped[paletteIndex];
            if (newIndex == -1) {
                BlockStateSnapshot snapshot = this.palette.get(paletteIndex);
                newIndex = indices.getInt(snapshot);
                if (newIndex == -1) {
                    newIndex = newPalette.size();
                    newPalette.add(snapshot);
                    indices.put(snapshot, newIndex);
                }
                remapped[paletteIndex] = newIndex;

                if (newIndex > version.getMaxEntryValue()) {
                    version = version.next();
                    BitArray growArray = version.createPalette(SECTION_SIZE);
                    for (int j = 0; j < i; j++) {
                        growArray.set(j, newArray.get(j));
                    }
                    newArray = growArray;
                }
            }

            newArray.set(i, newIndex);
        }
        this.networkSnapshot = null;
        this.bitArray = newArray;
        this.palette = newPalette;
        this.version++;
        return true;
    }

    protected void computeOldData() {
        if (this.blockIds == null || this.blockData == null) {
            this.blockIds = new byte[SECTION_SIZE];
            this.blockData = new NibbleArray(BlockStorage.SECTION_SIZE);

            for (int i = 0; i < this.bitArray.size(); i++) {
                int fullId = this.get(i);
                this.blockIds[i] = (byte) ((fullId >> Block.DATA_BITS) & 0xff);
                this.blockData.set(i, (byte) (fullId & 0xf));
            }
        }
    }

    public synchronized StateBlockStorage copy() {
        StateBlockStorage copy = new StateBlockStorage(
                this.bitArray.copy(),
                new ObjectArrayList<>(this.palette),
                this.blockIds != null ? this.blockIds.clone() : null,
                this.blockData != null ? this.blockData.copy(): null
        );
        // copy() and writeTo() are called under the section read lock. Each mutator detaches only
        // its own snapshot, so a worker's old clone remains valid after a live block edit.
        copy.networkCacheable = true;
        copy.networkSnapshot = this.networkCacheable ? this.networkSnapshot() : null;
        return copy;
    }

    private NetworkSnapshot networkSnapshot() {
        NetworkSnapshot snapshot = this.networkSnapshot;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = this.networkSnapshot;
                if (snapshot == null) this.networkSnapshot = snapshot = new NetworkSnapshot();
            }
        }
        return snapshot;
    }

    private record NetworkKey(Object snapshot, BlockPalette palette, long paletteVersion, boolean hashed,
                              int paletteSize) { }

    private static final class NetworkSnapshot {
        private XrayMask mask;
        private int[] used;

        private Object identity(List<BlockStateSnapshot> states, BitArray cells, boolean antiXray) {
            if (!antiXray) return this;
            synchronized (this) {
                if (this.used == null) {
                    boolean[] present = new boolean[states.size()];
                    int count = 0;
                    for (int i = 0; i < SECTION_SIZE; i++) {
                        int local = cells.get(i);
                        // Leave corrupt-index exceptions to the original writer, in its original order.
                        if (local >= 0 && local < present.length && !present[local]) {
                            present[local] = true;
                            count++;
                        }
                    }
                    this.used = new int[count];
                    for (int i = 0, n = 0; i < present.length; i++) if (present[i]) this.used[n++] = i;
                }
                if (this.mask != null) {
                    boolean matches = true;
                    for (int i : this.used) {
                        if (this.mask.concealed[i] != concealed(states.get(i))) { matches = false; break; }
                    }
                    if (matches) return this.mask;
                }
                boolean[] concealed = new boolean[states.size()];
                for (int i : this.used) concealed[i] = concealed(states.get(i));
                return this.mask = new XrayMask(concealed);
            }
        }

        private static boolean concealed(BlockStateSnapshot state) {
            if (state == null) return false;
            int id = (state.getLegacyId() << Block.DATA_BITS | state.getLegacyData()) >> Block.DATA_BITS;
            return id >= 0 && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id];
        }
    }

    // Identity equality is deliberate: never treat a hash of the mutable obfuscation mask as proof.
    private static final class XrayMask {
        private final boolean[] concealed;
        private XrayMask(boolean[] concealed) { this.concealed = concealed; }
    }

    /**
     * Check if this storage may contain light-emitting blocks.
     * This is a fast check that only looks at the palette, not every block position.
     * If this returns false, the storage definitely has no light sources.
     * If this returns true, the storage may have light sources (needs full scan).
     *
     * @return true if light sources may exist, false if definitely none
     */
    public boolean maybeHasLightSource() {
        for (BlockStateSnapshot state : this.palette) {
            if (Block.getBlockLight(state.getLegacyId()) > 0) {
                return true;
            }
        }
        return false;
    }

    public static int elementIndex(int x, int y, int z) {
        int index = (x << 8) | (z << 4) | y;
        if (index < 0 || index >= SUB_CHUNK_SIZE) {
            throw new IllegalArgumentException("Invalid index: " + x + ", " + y + ", " + z);
        }
        return index;
    }
}
