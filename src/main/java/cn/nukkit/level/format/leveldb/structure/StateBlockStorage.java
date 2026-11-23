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
import cn.nukkit.level.util.PaddedBitArray;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.level.util.Pow2BitArray;
import cn.nukkit.level.util.SingletonBitArray;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ThreadCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

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
    private static final int MAX_VAR_INT_BYTES = 5;
    private static final int MAX_CACHED_PALETTE_ENTRIES = 512;
    private static final int LINEAR_PALETTE_LOOKUP_LIMIT = 8;

    private static final ThreadLocal<NetworkScratch> NETWORK_SCRATCH = new ThreadLocal<>();

    private static NetworkScratch acquireScratch() {
        NetworkScratch scratch = NETWORK_SCRATCH.get();
        if (scratch != null) {
            NETWORK_SCRATCH.set(null);
            return scratch;
        }
        return new NetworkScratch();
    }

    private static void releaseScratch(NetworkScratch scratch) {
        if (scratch.retainable() && NETWORK_SCRATCH.get() == null) {
            NETWORK_SCRATCH.set(scratch);
        }
    }

    private static final class NetworkScratch {
        int[] networkIds = new int[32];
        int[] networkIndex = new int[32];
        int[] networkPalette = new int[32];
        Int2IntOpenHashMap paletteLookup;

        int[] ensureIds(int size) {
            if (networkIds.length < size) {
                networkIds = new int[Math.max(size, networkIds.length << 1)];
            }
            return networkIds;
        }

        int[] ensureIndex(int size) {
            if (networkIndex.length < size) {
                networkIndex = new int[Math.max(size, networkIndex.length << 1)];
            }
            Arrays.fill(networkIndex, 0, size, -1);
            return networkIndex;
        }

        int[] ensurePalette(int size) {
            if (networkPalette.length < size) {
                networkPalette = new int[Math.max(size, networkPalette.length << 1)];
            }
            return networkPalette;
        }

        Int2IntOpenHashMap lookup() {
            if (paletteLookup == null) {
                paletteLookup = new Int2IntOpenHashMap(32);
                paletteLookup.defaultReturnValue(-1);
            } else {
                paletteLookup.clear();
            }
            return paletteLookup;
        }

        boolean retainable() {
            return networkIds.length <= MAX_CACHED_PALETTE_ENTRIES
                    && networkIndex.length <= MAX_CACHED_PALETTE_ENTRIES
                    && networkPalette.length <= MAX_CACHED_PALETTE_ENTRIES + 1
                    && (paletteLookup == null || paletteLookup.size() <= MAX_CACHED_PALETTE_ENTRIES);
        }
    }

    // No chunk/provider references. Limit retained serialized bytes across all loaded sections/versions.
    private static final Cache<Long, NetworkEntry> NETWORK_BYTES = Caffeine.newBuilder()
            .maximumWeight(Math.min(32L << 20, Runtime.getRuntime().maxMemory() / 64))
            .weigher((Long key, NetworkEntry entry) -> entry.weight())
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    private List<BlockStateSnapshot> palette;
    private BitArray bitArray;
    private volatile boolean shared;
    // Snapshot acknowledgements must also notice public storage mutations that bypass section setters.

    /**
     * 单元与调色板的发布锁:写独占,读乐观,仅观测到并发写时退化为共享读。
     * <p>
     * Publication lock for cells and palette: exclusive writes, optimistic reads that fall
     * back to the shared lock only when a writer was observed.
     */
    private final StampedLock cellsLock = new StampedLock();

    /**
     * Bumped by every change of the stored states. Whoever read cells together with a version knows
     * they still hold while {@link #getVersion()} returns the same value.
     */
    private volatile int version;
    private boolean networkCacheable = true;

    //用于兼容1.13以下版本
    private byte[] blockIds;
    private NibbleArray blockData;

    public StateBlockStorage() {
        // A fresh layer contains only air: allocate cell words on its first mutation.
        this(BitArrayVersion.V0);
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
        long stamp = this.cellsLock.writeLock();
        try {
            this.readFromStorageLocked(buffer, chunkBuilder);
        } finally {
            this.cellsLock.unlockWrite(stamp);
        }
    }

    private void readFromStorageLocked(ByteBuf buffer, ChunkBuilder chunkBuilder) {
        this.version++;
        short header = buffer.readUnsignedByte();

        if (header == -1) {
            return;
        }

        this.ensureWritable();
        this.palette.clear();
        this.version++;

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
            // Invalidate observations made while the previous palette was still being replaced.
            this.version++;
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
        // 乐观读:无竞争时零锁开销,只有读到 grow/compress/readFromStorage 重塑的中间态才加锁重读。
        // Optimistic read: no locking when uncontended; a mid-rebuild state from grow/compress/
        // readFromStorage re-reads under the lock.
        long stamp = this.cellsLock.tryOptimisticRead();
        try {
            List<BlockStateSnapshot> palette = this.palette;
            BitArray cells = this.bitArray;
            int paletteIndex;
            if (cells instanceof Pow2BitArray pow2) {
                paletteIndex = pow2.get(index);
            } else if (cells instanceof PaddedBitArray padded) {
                paletteIndex = padded.get(index);
            } else {
                paletteIndex = cells.get(index);
            }
            if (paletteIndex >= 0 && paletteIndex < palette.size()) {
                BlockStateSnapshot snapshot = palette.get(paletteIndex);
                if (snapshot != null && this.cellsLock.validate(stamp)) {
                    return snapshot;
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
            // A rebuild shrank the palette under this read; the locked path reports any real error.
        }
        return this.getBlockStateLocked(index);
    }

    private BlockStateSnapshot getBlockStateLocked(int index) {
        long stamp = this.cellsLock.readLock();
        try {
            return this.palette.get(this.bitArray.get(index));
        } finally {
            this.cellsLock.unlockRead(stamp);
        }
    }

    public BlockStateSnapshot getBlockState(int x, int y, int z) {
        return this.getBlockState(elementIndex(x, y, z));
    }

    public int get(int index) {
        long stamp = this.cellsLock.tryOptimisticRead();
        try {
            List<BlockStateSnapshot> palette = this.palette;
            BitArray cells = this.bitArray;
            int paletteIndex;
            if (cells instanceof Pow2BitArray pow2) {
                paletteIndex = pow2.get(index);
            } else if (cells instanceof PaddedBitArray padded) {
                paletteIndex = padded.get(index);
            } else {
                paletteIndex = cells.get(index);
            }
            if (paletteIndex >= 0 && paletteIndex < palette.size()) {
                BlockStateSnapshot snapshot = palette.get(paletteIndex);
                if (snapshot != null) {
                    int legacyId = snapshot.getLegacyId();
                    int legacyData = snapshot.getLegacyData();
                    if (this.cellsLock.validate(stamp)) {
                        return legacyId << Block.DATA_BITS | legacyData;
                    }
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
            // A rebuild shrank the palette under this read; the locked path reports any real error.
        }
        return this.getLocked(index);
    }

    private int getLocked(int index) {
        long stamp = this.cellsLock.readLock();
        try {
            BlockStateSnapshot snapshot = this.palette.get(this.bitArray.get(index));
            return snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData();
        } finally {
            this.cellsLock.unlockRead(stamp);
        }
    }

    public int get(int x, int y, int z) {
        return this.get(elementIndex(x, y, z));
    }

    public int get(BlockVector3 pos) {
        return this.get(elementIndex(pos.x, pos.y, pos.z));
    }

    public synchronized void set(int index, BlockStateSnapshot value) {
        long stamp = this.cellsLock.writeLock();
        try {
            this.ensureWritable();
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
            this.cellsLock.unlockWrite(stamp);
        }
    }

    /** See {@link #version}. */
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
        // No upper protocol bound: from v1_16_100 on the layer bytes depend on the protocol only through the
        // block palette, the hash-ID mode and the network ids, and all three are part of the cache key below.
        // A future protocol that changes the layer encoding itself must add its own key component.
        if (!this.networkCacheable || getClass() != StateBlockStorage.class || protocol.getProtocol() < ProtocolInfo.v1_20_0_23) {
            this.writeToUncached(protocol, stream, antiXray);
            return;
        }
        BlockPalette blockPalette = GlobalBlockPalette.getPaletteByProtocol(protocol);
        long paletteVersion = blockPalette.getNetworkVersion();
        if (blockPalette.getClass() != BlockPalette.class || paletteVersion < 0) {
            this.writeToUncached(protocol, stream, antiXray);
            return;
        }
        int paletteSize = this.palette.size();
        GameVersion mappingVersion = BlockStateMapping.get().getVersion();
        for (int i = 0; i < paletteSize; i++) {
            BlockStateSnapshot state = this.palette.get(i);
            // Foreign-version snapshots may resolve legacy IDs through a replaceable live mapper on every read.
            // Null entries keep the lazy per-cell read of the uncoded path instead of failing the id precompute.
            if (state == null || state.getClass() != BlockStateSnapshot.class
                    || state.getVersion() != mappingVersion) {
                this.writeToUncached(protocol, stream, antiXray);
                return;
            }
        }
        boolean useHash = GlobalBlockPalette.shouldUseHashedBlockNetworkIds(protocol);
        NetworkScratch scratch = acquireScratch();
        try {
            int[] networkIds = scratch.ensureIds(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                BlockStateSnapshot state = this.palette.get(i);
                networkIds[i] = networkId(blockPalette, useHash, antiXray,
                        state.getLegacyId() << Block.DATA_BITS | state.getLegacyData());
            }
            BitArrayVersion cellsVersion = this.bitArray.getVersion();
            int[] cells = this.bitArray.getWords();
            // V0 keeps its only index in SingletonBitArray.value while getWords() stays empty.
            boolean singleton = cellsVersion == BitArrayVersion.V0;
            int singletonIndex = singleton ? this.bitArray.get(0) : 0;
            // 内容寻址:cells 词表(或 V0 单值)加逐调色板项网络 ID 完全决定编码字节,与对象身份无关;
            // 命中以逐词比对确认,指纹碰撞不可能误命中。
            // <p>
            // Content addressing: the cell words (or V0 singleton) plus per-entry network ids fully
            // determine the encoded bytes; every hit is confirmed word-by-word, so a collision never mis-hits.
            Long fingerprint = contentFingerprint(cellsVersion, singletonIndex, cells, networkIds, paletteSize,
                    blockPalette, paletteVersion, useHash);
            NetworkEntry entry = NETWORK_BYTES.getIfPresent(fingerprint);
            if (entry == null) {
                // Caffeine's atomic load coalesces concurrent cold requests from independent async copies.
                entry = NETWORK_BYTES.get(fingerprint, ignored -> {
                    BinaryStream encoded = ThreadCache.acquireBinaryStream();
                    try {
                        this.writeToUncached(protocol, encoded, antiXray, blockPalette, useHash, networkIds, paletteSize, scratch);
                        return new NetworkEntry(cellsVersion, singletonIndex, cells, networkIds, paletteSize,
                                encoded.getBuffer(), blockPalette, paletteVersion, useHash);
                    } finally {
                        ThreadCache.releaseBinaryStream(encoded);
                    }
                });
            }
            if (blockPalette.getNetworkVersion() != paletteVersion
                    || GlobalBlockPalette.shouldUseHashedBlockNetworkIds(protocol) != useHash
                    || !entry.matches(cellsVersion, singletonIndex, cells, networkIds, paletteSize,
                    blockPalette, paletteVersion, useHash)) {
                NETWORK_BYTES.invalidate(fingerprint);
                this.writeToUncached(protocol, stream, antiXray, blockPalette, useHash, null, 0, scratch);
                return;
            }
            stream.put(entry.bytes);
        } finally {
            releaseScratch(scratch);
        }
    }

    private void writeToUncached(GameVersion protocol, BinaryStream stream, boolean antiXray) {
        BlockPalette blockPalette = protocol.getProtocol() >= ProtocolInfo.v1_16_100
                ? GlobalBlockPalette.getPaletteByProtocol(protocol) : null;
        boolean useHash = protocol.getProtocol() >= ProtocolInfo.v1_16_100
                && GlobalBlockPalette.shouldUseHashedBlockNetworkIds(protocol);
        NetworkScratch scratch = acquireScratch();
        try {
            this.writeToUncached(protocol, stream, antiXray, blockPalette, useHash, null, 0, scratch);
        } finally {
            releaseScratch(scratch);
        }
    }

    private void writeToUncached(GameVersion protocol, BinaryStream stream, boolean antiXray,
                                 BlockPalette blockPalette, boolean useHash, int[] networkIds,
                                 int networkIdCount, NetworkScratch scratch) {
        if (protocol.getProtocol() >= ProtocolInfo.v1_16_100 && getClass() == StateBlockStorage.class) {
            writeSizedNetworkPalette(stream, antiXray, blockPalette, useHash, networkIds, networkIdCount, scratch);
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
            int[] networkIndex = scratch.ensureIndex(paletteSize);
            for (int i = 0; i < SECTION_SIZE; i++) {
                int local = this.bitArray.get(i);
                if (local >= 0 && local < paletteSize) {
                    int known = networkIndex[local];
                    if (known >= 0) {
                        palettedBlockStorage.setPaletteIndex(i, known);
                    } else {
                        BlockStateSnapshot snapshot = this.palette.get(local);
                        int networkId = networkIds != null && local < networkIdCount ? networkIds[local]
                                : networkId(blockPalette, useHash, antiXray,
                                snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData());
                        networkIndex[local] = palettedBlockStorage.setBlockReturningIndex(i, networkId);
                    }
                } else {
                    // Out of the palette: keep the per-cell read, including the exception it throws.
                    palettedBlockStorage.setBlock(i, networkId(blockPalette, useHash, antiXray, get(i)));
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

    private static int networkId(BlockPalette blockPalette, boolean useHash, boolean antiXray, int fullId) {
        int id = fullId >> Block.DATA_BITS;
        int meta = fullId & Block.DATA_MASK;
        if (antiXray && id >= 0 && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
            id = Block.STONE;
            meta = 0;
        }
        return useHash ? blockPalette.getHashId(id, meta) : blockPalette.getRuntimeId(id, meta);
    }

    /**
     * Discover the emitted palette using thread-local scratch arrays and pack 32-bit cell words directly
     * into the output stream without intermediate BitArray/int[]/IntArrayList/Int2IntOpenHashMap allocations.
     * Keeps the original first-use ordering, air at index zero, alias merging and minimum V2 width on the wire.
     */
    private void writeSizedNetworkPalette(BinaryStream stream, boolean antiXray,
                                          BlockPalette blockPalette, boolean useHash,
                                          int[] networkIds, int networkIdCount, NetworkScratch scratch) {
        int paletteSize = this.palette.size();
        int air = useHash ? blockPalette.getHashId(0, 0) : blockPalette.getRuntimeId(0, 0);

        if (this.bitArray.getClass() == SingletonBitArray.class && this.bitArray.size() == SECTION_SIZE) {
            int local = this.bitArray.get(0);
            if (local < 0 || local >= paletteSize) {
                get(0);
            }
            BlockStateSnapshot snapshot = this.palette.get(local);
            int id = networkIds != null && local < networkIdCount ? networkIds[local]
                    : networkId(blockPalette, useHash, antiXray,
                    snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData());
            stream.putByte((byte) getPaletteHeader(BitArrayVersion.V2, true));
            stream.reserve(256 * Integer.BYTES + 3 * MAX_VAR_INT_BYTES);
            int fillWord = id == air ? 0 : 0x55555555;
            for (int w = 0; w < 256; w++) {
                stream.putLInt(fillWord);
            }
            if (id == air) {
                stream.putVarInt(1);
                stream.putVarInt(air);
            } else {
                stream.putVarInt(2);
                stream.putVarInt(air);
                stream.putVarInt(id);
            }
            return;
        }

        int[] networkIndex = scratch.ensureIndex(paletteSize);
        int[] networkPalette = scratch.ensurePalette(Math.max(2, paletteSize + 1));
        networkPalette[0] = air;
        int networkPaletteSize = 1;
        Int2IntOpenHashMap paletteLookup = null;

        Class<?> bitArrayClass = this.bitArray.getClass();
        boolean directWords = (bitArrayClass == Pow2BitArray.class || bitArrayClass == PaddedBitArray.class)
                && this.bitArray.size() == SECTION_SIZE;
        int[] srcWords = directWords ? this.bitArray.getWords() : null;
        int srcBits = directWords ? this.bitArray.getVersion().getId() : 0;
        int srcEntries = directWords ? 32 / srcBits : 0;
        int srcMask = directWords ? this.bitArray.getVersion().getMaxEntryValue() : 0;

        int remaining = paletteSize;
        if (directWords) {
            int cellIndex = 0;
            outer:
            for (int sw = 0; sw < srcWords.length && cellIndex < SECTION_SIZE; sw++) {
                int srcWord = srcWords[sw];
                int limit = Math.min(SECTION_SIZE, cellIndex + srcEntries);
                while (cellIndex < limit) {
                    int local = srcWord & srcMask;
                    srcWord >>>= srcBits;
                    if (local < 0 || local >= paletteSize) {
                        get(cellIndex); // Preserve the original palette-read exception for corrupt cell indices.
                    }
                    if (networkIndex[local] < 0) {
                        networkPaletteSize = assignNetworkPaletteSlot(local, antiXray, blockPalette, useHash,
                                networkIds, networkIdCount, networkIndex, networkPalette, networkPaletteSize,
                                paletteLookup, scratch);
                        if (paletteLookup == null && networkPaletteSize > LINEAR_PALETTE_LOOKUP_LIMIT) {
                            paletteLookup = scratch.paletteLookup;
                        }
                        if (--remaining == 0) {
                            break outer;
                        }
                    }
                    cellIndex++;
                }
            }
        } else {
            for (int i = 0; i < SECTION_SIZE; i++) {
                int local = this.bitArray.get(i);
                if (local < 0 || local >= paletteSize) {
                    get(i); // Preserve the original palette-read exception for corrupt cell indices.
                }
                if (networkIndex[local] >= 0) {
                    continue;
                }
                networkPaletteSize = assignNetworkPaletteSlot(local, antiXray, blockPalette, useHash,
                        networkIds, networkIdCount, networkIndex, networkPalette, networkPaletteSize,
                        paletteLookup, scratch);
                if (paletteLookup == null && networkPaletteSize > LINEAR_PALETTE_LOOKUP_LIMIT) {
                    paletteLookup = scratch.paletteLookup;
                }
                if (--remaining == 0) {
                    break;
                }
            }
        }

        BitArrayVersion version = BitArrayVersion.V2;
        while (networkPaletteSize - 1 > version.getMaxEntryValue()) {
            version = version.next();
        }
        int outWordCount = version.getWordsForSize(SECTION_SIZE);
        int dstBits = version.getId();
        int dstEntries = 32 / dstBits;

        int streamStart = stream.getCount();
        try {
            stream.putByte((byte) getPaletteHeader(version, true));
            int wordBytes = Math.multiplyExact(outWordCount, Integer.BYTES);
            int paletteBytes = Math.multiplyExact(Math.addExact(networkPaletteSize, 1), MAX_VAR_INT_BYTES);
            stream.reserve(Math.addExact(wordBytes, paletteBytes));

            int cellIndex = 0;
            if (directWords) {
                int srcWordIdx = 0;
                int srcSlot = 0;
                int srcWord = srcWords[0];
                for (int w = 0; w < outWordCount; w++) {
                    int outWord = 0;
                    int dstShift = 0;
                    int limit = Math.min(SECTION_SIZE, cellIndex + dstEntries);
                    while (cellIndex < limit) {
                        if (srcSlot == srcEntries) {
                            srcWord = srcWords[++srcWordIdx];
                            srcSlot = 0;
                        }
                        int local = srcWord & srcMask;
                        srcWord >>>= srcBits;
                        srcSlot++;
                        if (local < 0 || local >= paletteSize) {
                            get(cellIndex); // Also validate cells beyond an early palette-discovery exit.
                        }
                        outWord |= networkIndex[local] << dstShift;
                        dstShift += dstBits;
                        cellIndex++;
                    }
                    stream.putLInt(outWord);
                }
            } else {
                for (int w = 0; w < outWordCount; w++) {
                    int outWord = 0;
                    int dstShift = 0;
                    int limit = Math.min(SECTION_SIZE, cellIndex + dstEntries);
                    while (cellIndex < limit) {
                        int local = this.bitArray.get(cellIndex);
                        if (local < 0 || local >= paletteSize) {
                            get(cellIndex); // Also validate cells beyond an early palette-discovery exit.
                        }
                        outWord |= networkIndex[local] << dstShift;
                        dstShift += dstBits;
                        cellIndex++;
                    }
                    stream.putLInt(outWord);
                }
            }

            stream.putVarInt(networkPaletteSize);
            for (int i = 0; i < networkPaletteSize; i++) {
                stream.putVarInt(networkPalette[i]);
            }
        } catch (RuntimeException | Error failure) {
            stream.setCount(streamStart);
            throw failure;
        }
    }

    private int assignNetworkPaletteSlot(int local, boolean antiXray, BlockPalette blockPalette, boolean useHash,
                                         int[] networkIds, int networkIdCount, int[] networkIndex,
                                         int[] networkPalette, int networkPaletteSize,
                                         Int2IntOpenHashMap paletteLookup, NetworkScratch scratch) {
        BlockStateSnapshot snapshot = this.palette.get(local);
        int id = networkIds != null && local < networkIdCount ? networkIds[local]
                : networkId(blockPalette, useHash, antiXray,
                snapshot.getLegacyId() << Block.DATA_BITS | snapshot.getLegacyData());
        int index = -1;
        if (paletteLookup != null) {
            index = paletteLookup.get(id);
        } else {
            for (int p = 0; p < networkPaletteSize; p++) {
                if (networkPalette[p] == id) {
                    index = p;
                    break;
                }
            }
        }
        if (index < 0) {
            index = networkPaletteSize++;
            networkPalette[index] = id;
            if (paletteLookup != null) {
                paletteLookup.put(id, index);
            } else if (networkPaletteSize > LINEAR_PALETTE_LOOKUP_LIMIT) {
                Int2IntOpenHashMap map = scratch.lookup();
                for (int p = 0; p < networkPaletteSize; p++) {
                    map.put(networkPalette[p], p);
                }
            }
        }
        networkIndex[local] = index;
        return networkPaletteSize;
    }

    private void grow(BitArrayVersion version) {
        if (this.bitArray.getVersion() == BitArrayVersion.V0 && this.bitArray.get(0) == 0) {
            // A singleton has only index zero. Start at the previous default width
            // without copying 4096 zeros or immediately growing through V1 again.
            this.bitArray = BitArrayVersion.V2.createPalette(SECTION_SIZE);
            return;
        }
        BitArray newBitArray = version.createPalette(SECTION_SIZE);
        for (int i = 0; i < SECTION_SIZE; i++) {
            newBitArray.set(i, this.bitArray.get(i));
        }
        this.bitArray = newBitArray;
    }

    /**
     * 调用方须已持有 cellsLock 写锁:公共 {@link #compress()} 会重入不可重入的写锁而死锁,
     * 容量耗尽只能走 {@link #compressLocked()}。
     * <p>
     * Callers must already hold the cellsLock write lock: the public {@link #compress()} would
     * re-enter the non-reentrant write lock and deadlock, so exhaustion goes to {@link #compressLocked()}.
     *
     * @return palette index
     */
    private int getOrAdd(BlockStateSnapshot snapshot) {
        int index = this.palette.indexOf(snapshot);
        if (index != -1) {
            return index;
        }

        boolean compressed = false;
        while (true) {
            index = this.palette.size();
            BitArrayVersion version = this.bitArray.getVersion();
            if (index <= version.getMaxEntryValue()) {
                this.palette.add(snapshot);
                return index;
            }
            BitArrayVersion next = version.next();
            if (next != null) {
                this.grow(next);
            } else if (compressed || !this.compressLocked()) {
                throw new IndexOutOfBoundsException("too many elements");
            } else {
                // 压缩重建了调色板与位阵,压缩前算出的插入点与位宽已失效,交给循环头重算。
                // Compression rebuilt palette and cells, so the loop head recomputes both.
                compressed = true;
            }
        }
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

    /** Unknown/custom states are not evidence that a connection-driven block is absent. */
    public boolean mayContainBlockIds(IntSet blockIds) {
        if (this.palette == null || this.palette.isEmpty()) {
            return true;
        }
        for (BlockStateSnapshot state : this.palette) {
            if (state == null || state.isCustom()) {
                return true;
            }
            int id = state.getLegacyId();
            if (id < 0 || id >= Block.MAX_BLOCK_ID || id == Block.INFO_UPDATE || blockIds.contains(id)) {
                return true;
            }
        }
        return false;
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
        long stamp = this.cellsLock.writeLock();
        try {
            return this.compressLocked();
        } finally {
            this.cellsLock.unlockWrite(stamp);
        }
    }

    private boolean compressLocked() {
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
            this.version++;
            BlockStateSnapshot firstId = this.palette.get(0);
            this.palette = new ObjectArrayList<>(1);
            this.palette.add(firstId);

//            Arrays.fill(this.bitArray.getWords(), 0);
            this.bitArray = BitArrayVersion.V1.createPalette(SECTION_SIZE);
            this.shared = false;
            this.version++;
            return true;
        }

        BitArrayVersion version = BitArrayVersion.V2;
        BitArray newArray = version.createPalette(SECTION_SIZE);
        ObjectArrayList<BlockStateSnapshot> newPalette = new ObjectArrayList<>(count);
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
        // Drop the historical high-water capacity together with unused states.
        newPalette.trim();
        this.bitArray = newArray;
        this.palette = newPalette;
        this.shared = false;
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

    private void ensureWritable() {
        if (this.shared) {
            this.bitArray = this.bitArray.copy();
            this.palette = new ObjectArrayList<>(this.palette);
            this.shared = false;
        }
    }

    public synchronized StateBlockStorage copy() {
        // External constructor aliases and subclass hooks cannot participate in COW.
        boolean share = this.networkCacheable && getClass() == StateBlockStorage.class;
        StateBlockStorage copy = new StateBlockStorage(
                share ? this.bitArray : this.bitArray.copy(),
                share ? this.palette : new ObjectArrayList<>(this.palette),
                this.blockIds != null ? this.blockIds.clone() : null,
                this.blockData != null ? this.blockData.copy(): null
        );
        // The storage monitor orders snapshot creation with live writes. Both owners detach
        // before touching shared cells/palette; legacy arrays remain private deep copies.
        // Executor submission publishes the initialized clone (including the volatile flag)
        // to the serializer: all pre-submit writes happen-before its reads. Later live writes
        // touch only detached arrays, so the worker never reads partially changed words.
        if (share) {
            this.shared = true;
            copy.shared = true;
        }
        copy.networkCacheable = true;
        return copy;
    }

    /**
     * 内容寻址条目:持有 cells、V0 单值与逐项网络 ID 的快照副本用于逐词命中确认,指纹碰撞不可能误命中;
     * 数组构造时克隆,条目不随后续原地修改漂移。
     * <p>
     * Content-addressed entry: snapshot copies of the cells, V0 singleton value and per-entry
     * network ids for the word-by-word hit check; arrays are cloned, so the entry never drifts.
     */
    private static final class NetworkEntry {
        private final BitArrayVersion version;
        private final int singletonIndex;
        private final int[] words;
        private final int[] networkIds;
        private final byte[] bytes;
        private final BlockPalette palette;
        private final long paletteVersion;
        private final boolean hashed;

        private NetworkEntry(BitArrayVersion version, int singletonIndex, int[] words, int[] networkIds,
                             int networkIdCount, byte[] bytes, BlockPalette palette, long paletteVersion,
                             boolean hashed) {
            this.version = version;
            this.singletonIndex = singletonIndex;
            this.words = words.clone();
            this.networkIds = Arrays.copyOf(networkIds, networkIdCount);
            this.bytes = bytes;
            this.palette = palette;
            this.paletteVersion = paletteVersion;
            this.hashed = hashed;
        }

        private boolean matches(BitArrayVersion version, int singletonIndex, int[] words, int[] networkIds,
                                int networkIdCount, BlockPalette palette, long paletteVersion, boolean hashed) {
            return this.version == version && this.singletonIndex == singletonIndex && this.hashed == hashed
                    && this.paletteVersion == paletteVersion && this.palette == palette
                    && this.networkIds.length == networkIdCount
                    && Arrays.equals(this.words, words)
                    && Arrays.equals(this.networkIds, 0, networkIdCount, networkIds, 0, networkIdCount);
        }

        private int weight() {
            return this.bytes.length + 256 + 4 * (this.words.length + this.networkIds.length);
        }
    }

    private static final long CELL_PRIME = 0x9E3779B97F4A7C15L;
    private static final long PALETTE_PRIME = 0xC2B2AE35D247B573L;
    private static final long BLOCK_PRIME = 0x9E3779B185EBCA87L;

    /**
     * murmur3 式逐词步进:不可简化为"旋转后异或"的仿射折叠——其位置权重以 64 为周期,
     * 相隔 64 个 word(或调色板位)的贡献会同权重碰撞,失配校验反而逐出正确条目,缓存每次重建。
     * <p>
     * Murmur3-style per-word step. A plain rotate-then-xor fold must not replace it: its positional
     * weights repeat every 64 rounds, so contributions 64 words (or palette slots) apart collide
     * and the failed match evicts the good entry, rebuilding the cache on every encode.
     */
    private static long mixWord(long h, long word, long prime) {
        long k = word * prime;
        k = Long.rotateLeft(k, 31);
        k *= BLOCK_PRIME;
        h ^= k;
        return Long.rotateLeft(h, 27) * 5 + 0x52DCE729;
    }

    /** Murmur-style 64-bit mix; quality only affects hit rate, exactness is restored by {@link NetworkEntry#matches}. */
    private static long contentFingerprint(BitArrayVersion version, int singletonIndex, int[] cells, int[] networkIds,
                                           int networkIdCount, BlockPalette palette, long paletteVersion, boolean hashed) {
        long h = 0x9E3779B97F4A7C15L;
        h = mixWord(h, singletonIndex & 0xFFFFFFFFL, CELL_PRIME);
        for (int cell : cells) {
            h = mixWord(h, cell & 0xFFFFFFFFL, CELL_PRIME);
        }
        for (int i = 0; i < networkIdCount; i++) {
            h = mixWord(h, networkIds[i] & 0xFFFFFFFFL, PALETTE_PRIME);
        }
        h = mixWord(h, version.ordinal(), PALETTE_PRIME);
        h = mixWord(h, cells.length, PALETTE_PRIME);
        h = mixWord(h, networkIdCount, PALETTE_PRIME);
        h = mixWord(h, paletteVersion, PALETTE_PRIME);
        h = mixWord(h, System.identityHashCode(palette) & 0xFFFFFFFFL, PALETTE_PRIME);
        if (hashed) {
            h ^= CELL_PRIME;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
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

    /** Unused palette entries are harmless false positives; do not scan all 4096 cells. */
    public boolean mayHaveRandomTickBlocks() {
        // Legacy subclasses may expose ticking blocks through their virtual reads.
        if (this.getClass() != StateBlockStorage.class) {
            return true;
        }
        // Large or long-lived palettes cost more to inspect than the usual three samples.
        // Keep those on the existing path rather than trading three reads for a full scan.
        if (this.palette.size() > 16) {
            return true;
        }
        for (int i = 0; i < this.palette.size(); i++) {
            if (Level.isRandomTickBlock(this.palette.get(i).getLegacyId())) {
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
