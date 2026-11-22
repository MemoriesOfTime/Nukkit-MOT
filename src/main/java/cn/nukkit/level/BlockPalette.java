package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.Hash;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

@Log4j2
public class BlockPalette {

    private static final int MISSING_LEGACY_MAPPING_CACHE_SIZE = 1024;

    private final int protocol;
    private final GameVersion gameVersion;
    private final Int2IntOpenHashMap legacyToRuntimeId = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap runtimeIdToLegacy = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap stateHashToLegacy = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap legacyToHashId = new Int2IntOpenHashMap();

    private final Cache<Integer, Integer> legacyToRuntimeIdCache = CacheBuilder.newBuilder()
            .maximumSize(MISSING_LEGACY_MAPPING_CACHE_SIZE)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    private volatile boolean locked;
    private volatile long networkVersion;
    private volatile boolean networkMappingReady;
    /**
     * 旧版方块 ID → 调色板原始 state NBT（name/version/states），仅结构导出需要，首次使用时构建。
     * <p>
     * Legacy block id to raw palette state NBT (name/version/states); built on first use, only structure export needs it.
     */
    private volatile Map<Integer, CompoundTag> legacyStateTags;
    /**
     * 方块名 → 调色板状态条目，供跨版本状态回退使用，首次需要时构建。
     * <p>
     * Block name to palette state entries, built on first use for cross-version state fallback.
     */
    private volatile Map<String, List<Map.Entry<Integer, CompoundTag>>> stateTagsByName;

    @Deprecated
    public BlockPalette(int protocol) {
        this(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode));
    }

    public BlockPalette(GameVersion gameVersion) {
        this.protocol = gameVersion.getProtocol();
        this.gameVersion = gameVersion;

        legacyToRuntimeId.defaultReturnValue(-1);
        runtimeIdToLegacy.defaultReturnValue(-1);
        stateHashToLegacy.defaultReturnValue(-1);
        legacyToHashId.defaultReturnValue(-1);

        loadBlockStates(paletteFor(protocol));
        this.lock();
    }

    private ListTag<CompoundTag> paletteFor(int protocol) {
        String name = locatePaletteResource(protocol);
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new AssertionError("Unable to locate block state nbt " + protocol);
            }
            //noinspection unchecked
            return (ListTag<CompoundTag>) NBTIO.readTag(new BufferedInputStream(new GZIPInputStream(stream)), ByteOrder.BIG_ENDIAN, false);
        } catch (IOException e) {
            throw new AssertionError("Unable to load block palette " + protocol, e);
        }
    }

    /**
     * 无专属 dat 时按协议号向下回退到最近有资源的版本；网易版优先借同协议标准调色板（hashid 使 rid 序号不进线）。
     * <p>
     * Falls back to the nearest lower protocol with a resource; a NetEase version prefers the
     * standard palette of the same protocol (hash ids keep rid ordering off the wire).
     */
    private String locatePaletteResource(int protocol) {
        if (gameVersion.isNetEase()) {
            String ownName = "runtime_block_states_netease_" + protocol + ".dat";
            if (resourceExists(ownName)) {
                return ownName;
            }
            // borrow the same-protocol standard palette (floored) first, then the NetEase ladder
            String standard = findNearestPaletteResource("runtime_block_states_", protocol);
            if (standard != null) {
                log.info("{} missing dedicated palette, using standard palette {}", gameVersion, standard);
                return standard;
            }
            String netease = findNearestPaletteResource("runtime_block_states_netease_", protocol);
            return netease != null ? netease : ownName;
        }
        String standard = findNearestPaletteResource("runtime_block_states_", protocol);
        return standard != null ? standard : "runtime_block_states_" + protocol + ".dat";
    }

    private boolean resourceExists(String name) {
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream(name)) {
            return stream != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private String findNearestPaletteResource(String prefix, int protocol) {
        GameVersion[] values = GameVersion.values();
        boolean netease = prefix.endsWith("netease_");
        for (int i = values.length - 1; i >= 0; i--) { // values() declared ascending; backwards = descending protocol
            GameVersion candidate = values[i];
            if (candidate.isNetEase() != netease || candidate.getProtocol() > protocol) {
                continue;
            }
            String name = prefix + candidate.getProtocol() + ".dat";
            try (InputStream stream = Server.class.getClassLoader().getResourceAsStream(name)) {
                if (stream != null) {
                    if (candidate.getProtocol() != protocol) {
                        log.info("{} missing dedicated palette, falling back to {} palette", gameVersion, candidate);
                    }
                    return name;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private void loadBlockStates(ListTag<CompoundTag> blockStates) {
        List<CompoundTag> stateOverloads = new ObjectArrayList<>();
        for (CompoundTag state : blockStates.getAll()) {
            if (!this.registerBlockState(state, false)) {
                stateOverloads.add(state);
            }
        }

        for (CompoundTag state : stateOverloads) {
            log.debug("[{}] Registering block palette overload: {}", this.getProtocol(), state.getString("name"));
            this.registerBlockState(state, true);
        }
    }

    private boolean registerBlockState(CompoundTag state, boolean force) {
        int id = state.getInt("id");
        int data = state.getShort("data");
        int runtimeId = state.getInt("runtimeId");
        boolean stateOverload = state.getBoolean("stateOverload");

        if (stateOverload && !force) {
            return false;
        }

        CompoundTag vanillaState = state
                .remove("id")
                .remove("data")
                .remove("runtimeId")
                .remove("stateOverload");
        this.registerState(id, data, runtimeId, vanillaState);
        return true;
    }

    public int getProtocol() {
        return this.protocol;
    }

    public GameVersion getGameVersion() {
        return this.gameVersion;
    }

    public Int2IntMap getLegacyToRuntimeIdMap() {
        return Int2IntMaps.unmodifiable(this.legacyToRuntimeId);
    }

    /** Generation of the completed network mapping; an in-progress rebuild cannot be cached. */
    public long getNetworkVersion() {
        return this.locked && this.networkMappingReady ? this.networkVersion : -1;
    }

    public synchronized void clearStates() {
        this.networkMappingReady = false;
        this.locked = false;
        this.legacyToRuntimeId.clear();
        this.runtimeIdToLegacy.clear();
        this.stateHashToLegacy.clear();
        this.legacyToHashId.clear();
        this.legacyToRuntimeIdCache.invalidateAll();
    }

    public synchronized void remapRuntimeIds(Int2IntMap runtimeIdMapping, int vanillaStateCount) {
        if (vanillaStateCount < 0) {
            throw new IllegalArgumentException("Vanilla state count must be nonnegative");
        }
        Int2IntOpenHashMap remappedLegacyToRuntime = new Int2IntOpenHashMap(this.legacyToRuntimeId.size());
        for (Int2IntMap.Entry entry : this.legacyToRuntimeId.int2IntEntrySet()) {
            int runtimeId = entry.getIntValue();
            if (runtimeId >= 0 && runtimeId < vanillaStateCount) {
                remappedLegacyToRuntime.put(entry.getIntKey(), remapRuntimeId(runtimeIdMapping, runtimeId));
            }
        }
        Int2IntOpenHashMap remappedRuntimeToLegacy = new Int2IntOpenHashMap(this.runtimeIdToLegacy.size());
        for (Int2IntMap.Entry entry : this.runtimeIdToLegacy.int2IntEntrySet()) {
            if (entry.getIntKey() < 0 || entry.getIntKey() >= vanillaStateCount) {
                continue;
            }
            int replacement = remapRuntimeId(runtimeIdMapping, entry.getIntKey());
            if (remappedRuntimeToLegacy.containsKey(replacement)) {
                throw new IllegalArgumentException("Duplicate replacement for runtime ID " + replacement
                        + " in palette for " + this.gameVersion + " (protocol " + this.protocol + ")");
            }
            remappedRuntimeToLegacy.put(replacement, entry.getIntValue());
        }

        this.networkMappingReady = false;
        this.locked = false;
        this.legacyToRuntimeId.clear();
        this.legacyToRuntimeId.putAll(remappedLegacyToRuntime);
        this.runtimeIdToLegacy.clear();
        this.runtimeIdToLegacy.putAll(remappedRuntimeToLegacy);
        this.legacyToRuntimeIdCache.invalidateAll();
    }

    private int remapRuntimeId(Int2IntMap mapping, int runtimeId) {
        int replacement = mapping.getOrDefault(runtimeId, -1);
        if (replacement < 0) {
            throw new IllegalArgumentException("Missing replacement for runtime ID " + runtimeId
                    + " in palette for " + this.gameVersion + " (protocol " + this.protocol + ")");
        }
        return replacement;
    }

    public synchronized void registerState(int blockId, int data, int runtimeId, CompoundTag blockState) {
        if (this.locked) {
            throw new IllegalStateException("Block palette is already locked!");
        }

        Block.registerKnownState(blockId, data);

        int legacyId = blockId << Block.DATA_BITS | data;
        this.legacyToRuntimeId.put(legacyId, runtimeId);
        this.runtimeIdToLegacy.putIfAbsent(runtimeId, legacyId);
        int stateHash = Hash.hashBlock(blockState);
        this.stateHashToLegacy.putIfAbsent(stateHash, legacyId);
        this.legacyToHashId.putIfAbsent(legacyId, stateHash);
    }

    /**
     * 老调色板只有一个骷髅头方块，头型存在方块实体里；为拆分后的头方块补充出站别名。
     * <p>
     * Older palettes have a single skull block carrying the head type in its block entity; add outgoing aliases for split heads.
     * Aliases only fill forward lookups; native states keep their canonical reverse mappings.
     */
    private void registerLegacySkullAliases() {
        int[] heads = {BlockID.WITHER_SKELETON_SKULL, BlockID.ZOMBIE_HEAD,
                BlockID.PLAYER_HEAD, BlockID.CREEPER_HEAD, BlockID.DRAGON_HEAD,
                BlockID.PIGLIN_HEAD};
        for (int meta = 0; meta < 6; meta++) {
            int skull = BlockID.SKULL_BLOCK << Block.DATA_BITS | meta;
            int runtimeId = this.legacyToRuntimeId.get(skull);
            if (runtimeId == -1) {
                continue;
            }
            for (int head : heads) {
                int fullId = head << Block.DATA_BITS | meta;
                if (!this.legacyToRuntimeId.containsKey(fullId)) {
                    this.legacyToRuntimeId.put(fullId, runtimeId);
                    if (this.legacyToHashId.containsKey(skull)) {
                        this.legacyToHashId.putIfAbsent(fullId, this.legacyToHashId.get(skull));
                    }
                }
            }
        }
        this.legacyToRuntimeIdCache.invalidateAll();
    }

    public synchronized void lock() {
        this.networkMappingReady = false;
        if (!this.locked) {
            this.registerLegacySkullAliases();
        }
        this.locked = true;
        this.trim();
    }

    /**
     * Shrinks the internal fastutil maps after bulk registration to release unused capacity.
     */
    public synchronized void trim() {
        // Direct public trims also rehash live mappings. Publish only after every native map completes.
        this.networkMappingReady = false;
        this.legacyToRuntimeId.trim();
        this.runtimeIdToLegacy.trim();
        this.stateHashToLegacy.trim();
        this.legacyToHashId.trim();
        this.networkVersion++;
        this.networkMappingReady = true;
    }

    public int getRuntimeId(int id) {
        return this.getRuntimeId(id, 0);
    }

    public int getRuntimeId(int id, int meta) {
        int legacyId = protocol >= 388 ? ((id << Block.DATA_BITS) | meta) : ((id << 4) | meta);
        int runtimeId;
        runtimeId = legacyToRuntimeId.get(legacyId);
        if (runtimeId == -1) {
            runtimeId = getMaskedMeta(legacyToRuntimeId, id, meta);
        }
        if (runtimeId == -1) {
            runtimeId = legacyToRuntimeId.get(id << Block.DATA_BITS);
            if (runtimeId == -1) {
                Integer cache = legacyToRuntimeIdCache.getIfPresent(legacyId);
                if (cache == null) {
                    logMissingRuntimeIdMapping("({}) Missing block runtime id mappings for {}:{}", gameVersion, id, meta);
                    runtimeId = legacyToRuntimeId.get(BlockID.INFO_UPDATE << Block.DATA_BITS);
                    legacyToRuntimeIdCache.put(legacyId, runtimeId);
                } else {
                    runtimeId = cache;
                }
            }
        }
        return runtimeId;
    }

    public int getLegacyFullId(int runtimeId) {
        return runtimeIdToLegacy.get(runtimeId);
    }

    /**
     * 从哈希ID获取完整的旧方块ID
     * Get full legacy block ID from hash ID
     * <p>
     * 哈希ID是通过方块状态NBT计算得出的哈希值，用于新版本的方块网络传输
     * Hash ID is calculated from block state NBT and used for block network transmission in newer versions
     *
     * @param hashId 方块状态的哈希ID / hash ID of the block state
     * @return 完整的旧方块ID (blockId << Block.DATA_BITS | meta)，如果找不到则返回-1 / full legacy block ID, returns -1 if not found
     */
    public int getLegacyFullIdFromHashId(int hashId) {
        return stateHashToLegacy.get(hashId);
    }

    public int getLegacyFullId(CompoundTag blockState) {
        return stateHashToLegacy.get(Hash.hashBlock(blockState));
    }

    /**
     * 先按状态哈希精确匹配；新版本改过属性集时退回同名方块中状态最接近的一档。
     * <p>
     * Exact state-hash lookup first; when a newer/older version changed the state set,
     * fall back to the closest state of the same block name (vanilla-like upgrade).
     */
    public int getLegacyFullIdWithFallback(CompoundTag blockState) {
        int direct = this.getLegacyFullId(blockState);
        if (direct >= 0) {
            return direct;
        }
        String name = blockState.getString("name");
        if (name.isEmpty()) {
            return -1;
        }
        List<Map.Entry<Integer, CompoundTag>> candidates = this.statesByName().get(name);
        if (candidates == null) {
            return -1;
        }
        CompoundTag wanted = blockState.getCompound("states");
        int best = -1;
        int bestScore = -1;
        for (Map.Entry<Integer, CompoundTag> candidate : candidates) {
            int score = matchingStates(candidate.getValue().getCompound("states"), wanted);
            if (score > bestScore) {
                bestScore = score;
                best = candidate.getKey();
            }
        }
        return best;
    }

    private static int matchingStates(CompoundTag candidate, CompoundTag wanted) {
        int score = 0;
        for (Map.Entry<String, Tag> entry : candidate.getTags().entrySet()) {
            Tag wantedValue = wanted.get(entry.getKey());
            if (wantedValue != null && sameStateValue(entry.getValue(), wantedValue)) {
                score++;
            }
        }
        return score;
    }

    private static boolean sameStateValue(Tag first, Tag second) {
        if (first instanceof NumberTag<?> a && second instanceof NumberTag<?> b) {
            return a.getData().longValue() == b.getData().longValue();
        }
        if (first instanceof StringTag a && second instanceof StringTag b) {
            return a.data.equals(b.data);
        }
        return false;
    }

    private Map<String, List<Map.Entry<Integer, CompoundTag>>> statesByName() {
        Map<String, List<Map.Entry<Integer, CompoundTag>>> index = this.stateTagsByName;
        if (index == null) {
            synchronized (this) {
                index = this.stateTagsByName;
                if (index == null) {
                    index = new HashMap<>();
                    for (Map.Entry<Integer, CompoundTag> entry : this.legacyStateTags().entrySet()) {
                        String name = entry.getValue().getString("name");
                        index.computeIfAbsent(name, key -> new ArrayList<>()).add(entry);
                    }
                    this.stateTagsByName = index;
                }
            }
        }
        return index;
    }

    /**
     * 取方块状态对应的调色板原始 NBT（含 name/version/states），结构导出用。
     * <p>
     * Returns the raw palette state NBT for a block (name/version/states); used by structure export.
     * Falls back to {@code null} when the protocol palette has no such state.
     */
    public CompoundTag getBlockStateTag(int legacyFullId) {
        return this.legacyStateTags().get(legacyFullId);
    }

    private Map<Integer, CompoundTag> legacyStateTags() {
        Map<Integer, CompoundTag> tags = this.legacyStateTags;
        if (tags == null) {
            synchronized (this) {
                tags = this.legacyStateTags;
                if (tags == null) {
                    tags = this.loadLegacyStateTags();
                    this.legacyStateTags = tags;
                }
            }
        }
        return tags;
    }

    private Map<Integer, CompoundTag> loadLegacyStateTags() {
        Map<Integer, CompoundTag> tags = new HashMap<>();
        // 同一 legacy id 既有 canonical 又有 overload 条目时，canonical 优先
        // When a legacy id has both canonical and overload entries, keep the canonical one.
        Map<Integer, Boolean> canonical = new HashMap<>();
        for (CompoundTag state : paletteFor(this.protocol).getAll()) {
            int legacyId = state.getInt("id") << Block.DATA_BITS | state.getShort("data");
            boolean overload = state.getBoolean("stateOverload");
            Boolean existingCanonical = canonical.get(legacyId);
            if (existingCanonical != null && (existingCanonical || overload)) {
                continue;
            }
            CompoundTag copy = state.copy()
                    .remove("id")
                    .remove("data")
                    .remove("runtimeId")
                    .remove("stateOverload");
            tags.put(legacyId, copy);
            canonical.put(legacyId, !overload);
        }
        return tags;
    }

    /**
     * 获取方块的哈希ID (使用默认meta值0)
     * Get hash ID of a block (using default meta value 0)
     *
     * @param id 方块ID / block ID
     * @return 方块的哈希ID / hash ID of the block
     */
    public int getHashId(int id) {
        return this.getHashId(id, 0);
    }

    /**
     * 获取方块的哈希ID
     * Get hash ID of a block
     * <p>
     * 哈希ID用于新版本协议(1.19.80+)的方块网络传输，基于方块状态NBT的哈希值
     * Hash ID is used for block network transmission in newer protocols (1.19.80+), based on block state NBT hash
     *
     * @param id 方块ID / block ID
     * @param meta 方块元数据值 / block metadata value
     * @return 方块的哈希ID，如果找不到则返回INFO_UPDATE方块的哈希ID / hash ID of the block, returns INFO_UPDATE block's hash ID if not found
     */
    public int getHashId(int id, int meta) {
        int legacyId = protocol >= 388 ? ((id << Block.DATA_BITS) | meta) : ((id << 4) | meta);
        int hashId = legacyToHashId.get(legacyId);
        if (hashId == -1) {
            hashId = getMaskedMeta(legacyToHashId, id, meta);
        }
        if (hashId == -1) {
            hashId = legacyToHashId.get(id << Block.DATA_BITS);
            if (hashId == -1) {
                hashId = legacyToHashId.get(BlockID.INFO_UPDATE << Block.DATA_BITS);
            }
        }
        return hashId;
    }

    /**
     * 通过 fullId 直接查询 runtimeId，跳过 id/meta 的拆分与重组
     * Get runtimeId directly by fullId (blockId << DATA_BITS | meta), skipping id/meta split
     */
    public int getRuntimeIdByFullId(int legacyFullId) {
        int runtimeId = legacyToRuntimeId.get(legacyFullId);
        if (runtimeId == -1) {
            runtimeId = getMaskedMeta(legacyToRuntimeId, legacyFullId >> Block.DATA_BITS, legacyFullId & Block.DATA_MASK);
        }
        if (runtimeId == -1) {
            int id = legacyFullId >> Block.DATA_BITS;
            runtimeId = legacyToRuntimeId.get(id << Block.DATA_BITS);
            if (runtimeId == -1) {
                Integer cache = legacyToRuntimeIdCache.getIfPresent(legacyFullId);
                if (cache == null) {
                    logMissingRuntimeIdMapping("({}) Missing block runtime id mappings for fullId:{}", gameVersion, legacyFullId);
                    runtimeId = legacyToRuntimeId.get(BlockID.INFO_UPDATE << Block.DATA_BITS);
                    legacyToRuntimeIdCache.put(legacyFullId, runtimeId);
                } else {
                    runtimeId = cache;
                }
            }
        }
        return runtimeId;
    }

    /**
     * 通过 fullId 直接查询 hashId，跳过 id/meta 的拆分与重组
     * Get hashId directly by fullId (blockId << DATA_BITS | meta), skipping id/meta split
     */
    public int getHashIdByFullId(int legacyFullId) {
        int hashId = legacyToHashId.get(legacyFullId);
        if (hashId == -1) {
            hashId = getMaskedMeta(legacyToHashId, legacyFullId >> Block.DATA_BITS, legacyFullId & Block.DATA_MASK);
        }
        if (hashId == -1) {
            int id = legacyFullId >> Block.DATA_BITS;
            hashId = legacyToHashId.get(id << Block.DATA_BITS);
            if (hashId == -1) {
                hashId = legacyToHashId.get(BlockID.INFO_UPDATE << Block.DATA_BITS);
            }
        }
        return hashId;
    }

    /**
     * 按 1.26.50 连接/角落位的 meta 降级查询：完整 meta 未命中时依次尝试剥离高 4 位、高 3 位，
     * 让旧协议调色板（无 connection/corner 变体）与 LevelDB 存储仍能按基础 meta
     * （朝向/颜色/木种等）命中，而不是直接跌落到 data=0。
     * <p>
     * Degrades meta carrying 1.26.50 connection/corner bits: when the full meta misses, retry with
     * the upper bits (first 4, then 3) stripped so older-protocol palettes (without connection/corner
     * variants) and LevelDB storage still resolve the base meta (orientation/color/wood) instead of
     * falling straight through to data=0.
     */
    private int getMaskedMeta(Int2IntOpenHashMap map, int id, int meta) {
        int shift = protocol >= 388 ? Block.DATA_BITS : 4;
        if (meta > 0x07) {
            int value = map.get((id << shift) | (meta & 0x0f));
            if (value != -1) {
                return value;
            }
        }
        if (meta > 0x03) {
            int value = map.get((id << shift) | (meta & 0x07));
            if (value != -1) {
                return value;
            }
        }
        return -1;
    }

    private void logMissingRuntimeIdMapping(String message, Object... args) {
        if (this.gameVersion == GameVersion.getLastVersion() || this.gameVersion == GameVersion.getLastNetEaseVersion()) {
            log.info(message, args);
            return;
        }
        log.debug(message, args);
    }

}
