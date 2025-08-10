package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.Utils;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

@Log4j2
public class BlockPalette {

    private final int protocol;
    private final GameVersion gameVersion;
    private final Int2IntMap legacyToRuntimeId = new Int2IntOpenHashMap();
    private final Int2IntMap runtimeIdToLegacy = new Int2IntOpenHashMap();
    private final Int2IntMap stateHashToLegacy = new Int2IntOpenHashMap();

    private final Cache<Integer, Integer> legacyToRuntimeIdCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    private volatile boolean locked;

    @Deprecated
    public BlockPalette(int protocol) {
        this(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode));
    }

    public BlockPalette(GameVersion gameVersion) {
        this.protocol = gameVersion.getProtocol();
        this.gameVersion = gameVersion;

        legacyToRuntimeId.defaultReturnValue(-1);
        runtimeIdToLegacy.defaultReturnValue(-1);

        loadBlockStates(paletteFor(protocol));
        loadBlockStatesExtras();
    }

    private ListTag<CompoundTag> paletteFor(int protocol) {
        ListTag<CompoundTag> tag;
        String name = "runtime_block_states_" + protocol + ".dat";
        if (gameVersion.isNetEase()) {
            name = "runtime_block_states_netease_" + protocol + ".dat";
        }
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new AssertionError("Unable to locate block state nbt " + protocol);
            }
            //noinspection unchecked
            tag = (ListTag<CompoundTag>) NBTIO.readTag(new BufferedInputStream(new GZIPInputStream(stream)), ByteOrder.BIG_ENDIAN, false);
        } catch (IOException e) {
            throw new AssertionError("Unable to load block palette " + protocol, e);
        }
        return tag;
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

    /**
     * 加载扩展数据，用于在不修改runtime_block_states.dat文件的情况下额外增加一些内容
     */
    private void loadBlockStatesExtras() {
        try (InputStream resourceAsStream = Server.class.getClassLoader().getResourceAsStream("RuntimeBlockStatesExtras/" + protocol + ".json")) {
            if (resourceAsStream == null) {
                return;
            }
            List<Map> extras = new Config().loadFromStream(resourceAsStream).getMapList("extras");
            //noinspection unchecked
            for (Map<String, Object> map : extras) {
                int id = Utils.toInt(map.get("id"));
                int data = Utils.toInt(map.getOrDefault("data", 0));
                int runtimeId = Utils.toInt(map.get("runtimeId"));
                int legacyId = id << Block.DATA_BITS | data;
                legacyToRuntimeId.put(legacyId, runtimeId);
                if (!runtimeIdToLegacy.containsKey(runtimeId)) {
                    runtimeIdToLegacy.put(runtimeId, legacyId);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public void clearStates() {
        this.locked = false;
        this.legacyToRuntimeId.clear();
        this.runtimeIdToLegacy.clear();
        this.stateHashToLegacy.clear();
    }

    public void registerState(int blockId, int data, int runtimeId, CompoundTag blockState) {
        if (this.locked) {
            throw new IllegalStateException("Block palette is already locked!");
        }

        int legacyId = blockId << Block.DATA_BITS | data;
        this.legacyToRuntimeId.put(legacyId, runtimeId);
        this.runtimeIdToLegacy.putIfAbsent(runtimeId, legacyId);
        this.stateHashToLegacy.putIfAbsent(blockState.hashCode(), legacyId);

        // Hack: Map IDs for item frame up & down states
        if (blockId == BlockID.ITEM_FRAME_BLOCK || blockId == BlockID.GLOW_FRAME) {
            if (data == 7) {
                int offset = 5;

                runtimeId = runtimeId + offset;
                legacyId = blockId << Block.DATA_BITS | 5; // Up
                this.legacyToRuntimeId.put(legacyId, runtimeId);
                this.runtimeIdToLegacy.putIfAbsent(runtimeId, legacyId);

                int offset2 = 0;

                runtimeId = runtimeId + offset + offset2;
                legacyId = blockId << Block.DATA_BITS | 4; // Down
                this.legacyToRuntimeId.put(legacyId, runtimeId);
                this.runtimeIdToLegacy.putIfAbsent(runtimeId, legacyId);
            }
        }
    }

    public void lock() {
        this.locked = true;
    }

    public int getRuntimeId(int id) {
        return this.getRuntimeId(id, 0);
    }

    public int getRuntimeId(int id, int meta) {
        int legacyId = protocol >= 388 ? ((id << Block.DATA_BITS) | meta) : ((id << 4) | meta);
        int runtimeId;
        runtimeId = legacyToRuntimeId.get(legacyId);
        if (runtimeId == -1) {
            runtimeId = legacyToRuntimeId.get(id << Block.DATA_BITS);
            if (runtimeId == -1) {
                Integer cache = legacyToRuntimeIdCache.getIfPresent(legacyId);
                if (cache == null) {
                    log.info("({}) Missing block runtime id mappings for {}:{}", gameVersion, id, meta);
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

    public int getLegacyFullId(CompoundTag compoundTag) {
        return stateHashToLegacy.getOrDefault(compoundTag.hashCode(), -1);
    }

}
