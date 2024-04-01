package cn.nukkit.level.format.leveldb;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.updater.BlockStateUpdaterChunker;
import cn.nukkit.level.format.leveldb.updater.BlockStateUpdaterVanilla;
import com.nukkitx.network.util.Preconditions;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import lombok.extern.log4j.Log4j2;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.cloudburstmc.blockstateupdater.*;
import org.cloudburstmc.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;
import org.cloudburstmc.nbt.NbtMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.PALETTE_VERSION;

@Log4j2
public class BlockStateMapping {

    private static final BlockStateMapping INSTANCE = new BlockStateMapping(PALETTE_VERSION);
    private static final CompoundTagUpdaterContext CONTEXT;
    private static final int LATEST_UPDATER_VERSION;

    private final int version;

    private LegacyStateMapper legacyMapper;

    private int defaultRuntimeId = -1;
    private BlockStateSnapshot defaultState;

    private static final ExpiringMap<NbtMap, NbtMap> BLOCK_UPDATE_CACHE = ExpiringMap.builder()
            .maxSize(1024)
            .expiration(60L, TimeUnit.SECONDS)
            .expirationPolicy(ExpirationPolicy.ACCESSED)
            .build();
    private final Int2ObjectMap<BlockStateSnapshot> runtime2State = new Int2ObjectOpenHashMap<>();
    private final Object2ObjectMap<NbtMap, BlockStateSnapshot> paletteMap = new Object2ObjectOpenCustomHashMap<>(new Hash.Strategy<>() {
        @Override
        public int hashCode(NbtMap nbtMap) {
            return nbtMap.hashCode();
        }

        @Override
        public boolean equals(NbtMap nbtMap, NbtMap nbtMap2) {
            return Objects.equals(nbtMap, nbtMap2);
        }
    });

    static {
        INSTANCE.setLegacyMapper(new NukkitLegacyMapper());
        NukkitLegacyMapper.registerStates(INSTANCE);

        List<BlockStateUpdater> blockStateUpdaters = new ArrayList<>();
        blockStateUpdaters.add(BlockStateUpdaterBase.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_10_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_12_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_13_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_14_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_15_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_16_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_16_210.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_17_30.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_17_40.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_18_10.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_18_30.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_19_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_19_20.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_19_70.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_19_80.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_20_0.INSTANCE);
        blockStateUpdaters.add(BlockStateUpdater_1_20_10.INSTANCE);

        blockStateUpdaters.add(BlockStateUpdaterVanilla.INSTANCE);

        if (Boolean.parseBoolean(System.getProperty("leveldb-chunker"))) {
            blockStateUpdaters.add(BlockStateUpdaterChunker.INSTANCE);
            log.warn("Enabled chunker.app LevelDB updater. This may impact chunk loading performance!");
        }

        CompoundTagUpdaterContext context = new CompoundTagUpdaterContext();
        blockStateUpdaters.forEach(updater -> updater.registerUpdaters(context));
        CONTEXT = context;
        LATEST_UPDATER_VERSION = context.getLatestVersion();
    }

    public static BlockStateMapping get() {
        return INSTANCE;
    }

    public BlockStateMapping(int version) {
        this(version, null);
    }

    public BlockStateMapping(int version, LegacyStateMapper legacyStateMapper) {
        this.version = version;
        this.legacyMapper = legacyStateMapper;
    }

    public void registerState(int runtimeId, NbtMap state) {
        Preconditions.checkArgument(!this.runtime2State.containsKey(runtimeId),
                "Mapping for runtimeId " + runtimeId + " is already created!");
        Preconditions.checkArgument(!this.paletteMap.containsKey(state),
                "Mapping for state is already created: " + state);

        BlockStateSnapshot blockState = BlockStateSnapshot.builder()
                .version(this.version)
                .vanillaState(state)
                .runtimeId(runtimeId)
                .build();
        this.runtime2State.put(runtimeId, blockState);
        this.paletteMap.put(state, blockState);
    }

    public void clearMapping() {
        this.runtime2State.clear();
        this.paletteMap.clear();
    }

    public void setLegacyMapper(LegacyStateMapper legacyStateMapper) {
        this.legacyMapper = legacyStateMapper;
    }

    public LegacyStateMapper getLegacyMapper() {
        return this.legacyMapper;
    }

    public int getVersion() {
        return this.version;
    }

    public BlockStateSnapshot getBlockStateFromFullId(int fullId) {
        return getState(fullId >> Block.DATA_BITS, fullId & Block.DATA_MASK);
    }

    public BlockStateSnapshot getState(int legacyId, int data) {
        int runtimeId = this.legacyMapper.legacyToRuntime(legacyId, data);
        if (runtimeId == -1) {
            log.warn("Can not find state! No legacy2runtime mapping for " + legacyId + ":" + data);
            return this.getDefaultState();
        }
        return this.getState(runtimeId);
    }

    public BlockStateSnapshot getState(int runtimeId) {
        BlockStateSnapshot blockStateSnapshot = this.runtime2State.get(runtimeId);
        if (blockStateSnapshot == null) {
            log.warn("Can not find state! No runtime2State mapping for " + runtimeId);
            return this.getDefaultState();
        }
        return blockStateSnapshot;
    }

    public BlockStateSnapshot getState(NbtMap vanillaState) {
        BlockStateSnapshot blockStateSnapshot = this.paletteMap.get(vanillaState);
        if (blockStateSnapshot == null) {
            log.warn("Can not find block state! " + vanillaState);
            return this.getDefaultState();
        }
        return blockStateSnapshot;
    }

    public BlockStateSnapshot getStateUnsafe(NbtMap vanillaState) {
        return this.paletteMap.get(vanillaState);
    }

    public BlockStateSnapshot getBlockState(NbtMap tag, NbtMap newTag) {
        BlockStateSnapshot blockStateSnapshot = this.getStateUnsafe(newTag);
        if (blockStateSnapshot != null) {
            return blockStateSnapshot;
        }
        log.debug("Unknown block state: " + tag);
        return BlockStateSnapshot.builder().vanillaState(tag).runtimeId(this.getDefaultState().getRuntimeId()).version(this.version).custom(true).build();
    }

    public int getRuntimeId(int legacyId, int data) {
        int runtimeId = this.legacyMapper.legacyToRuntime(legacyId, data);
        if (runtimeId == -1) {
            log.warn("Can not find runtimeId! No legacy2runtime mapping for " + legacyId + ":" + data);
            return this.getDefaultRuntimeId();
        }
        return runtimeId;
    }

    public int getFullId(int runtimeId) {
        int fullId = this.legacyMapper.runtimeToFullId(runtimeId);
        if (fullId == -1) {
            log.warn("Can not find legacyId! No runtime2FullId mapping for " + runtimeId);
            fullId = this.legacyMapper.runtimeToFullId(this.getDefaultRuntimeId());
            Preconditions.checkArgument(fullId != -1, "Can not find fullId for default runtimeId: " + this.getDefaultRuntimeId());
        }
        return fullId;
    }

    public int getLegacyId(int runtimeId) {
        int legacyId = this.legacyMapper.runtimeToLegacyId(runtimeId);
        if (legacyId == -1) {
            log.warn("Can not find legacyId! No runtime2legacy mapping for " + runtimeId);
            legacyId = this.legacyMapper.runtimeToLegacyId(this.getDefaultRuntimeId());
            Preconditions.checkArgument(legacyId != -1, "Can not find legacyId for default runtimeId: " + this.getDefaultRuntimeId());
        }
        return legacyId;
    }

    public int getLegacyData(int runtimeId) {
        int data = this.legacyMapper.runtimeToLegacyData(runtimeId);
        if (data == -1) {
            log.warn("Can not find legacyId! No runtime2legacy mapping for " + runtimeId);
            data = this.legacyMapper.runtimeToLegacyData(this.getDefaultRuntimeId());
            Preconditions.checkArgument(data != -1, "Can not find legacyData for default runtimeId: " + this.getDefaultRuntimeId());        }
        return data;
    }

    public void setDefaultBlock(int legacyId, int legacyData) {
        int runtimeId = this.legacyMapper.legacyToRuntime(legacyId, legacyData);
        Preconditions.checkArgument(runtimeId != -1, "Can not find runtimeId mapping for default block: " + legacyId + ":" + legacyData);
        this.defaultRuntimeId = runtimeId;

        BlockStateSnapshot state = this.runtime2State.get(runtimeId);
        Preconditions.checkNotNull(state, "Can not find state for default block: " + legacyId + ":" + legacyData);
        this.defaultState = state;
    }

    public int getDefaultRuntimeId() {
        if (this.defaultRuntimeId == -1) {
            this.setDefaultBlock(Block.INFO_UPDATE, 0);
        }
        return this.defaultRuntimeId;
    }

    public BlockStateSnapshot getDefaultState() {
        if (this.defaultState == null) {
            this.setDefaultBlock(Block.INFO_UPDATE, 0);
        }
        return this.defaultState;
    }

    public BlockStateSnapshot updateState(NbtMap state) {
        BlockStateSnapshot blockState = this.paletteMap.get(state);
        if (blockState == null) {
            blockState = this.updateStateUnsafe(state);
        }
        return blockState;
    }

    public BlockStateSnapshot updateStateUnsafe(NbtMap state) {
        return this.getState(this.updateVanillaState(state));
    }

    public BlockStateSnapshot getUpdatedState(NbtMap state) {
        if (this.paletteMap.get(state) == null) {
            return this.getState(this.updateVanillaState(state));
        }
        return null;
    }

    public NbtMap updateVanillaState(NbtMap state) {
        NbtMap cached = BLOCK_UPDATE_CACHE.get(state);
        if (cached == null) {
            int version = state.getInt("version"); // TODO: validate this when updating next time
            cached = CONTEXT.update(state, LATEST_UPDATER_VERSION == version ? version - 1 : version);
            BLOCK_UPDATE_CACHE.put(state, cached);
        }
        return cached;
    }

    public BlockStateSnapshot getUpdatedOrCustom(NbtMap state) {
        return this.getUpdatedOrCustom(state, this.updateVanillaState(state));
    }

    public BlockStateSnapshot getUpdatedOrCustom(NbtMap state, NbtMap updated) {
        BlockStateSnapshot blockState = this.getStateUnsafe(updated);
        if (blockState != null) {
            return blockState;
        }

        return BlockStateSnapshot.builder()
                .vanillaState(state)
                .runtimeId(this.getDefaultState().getRuntimeId())
                .version(this.version)
                .custom(true)
                .build();
    }
}
