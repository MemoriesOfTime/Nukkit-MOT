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
import lombok.Getter;
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

import static cn.nukkit.level.format.leveldb.LevelDbConstants.CURRENT_LEVEL_PROTOCOL;

@Log4j2
public class BlockStateMapping {

    private static final BlockStateMapping instance;
    private static final CompoundTagUpdaterContext CONTEXT;
    private static final int LATEST_VERSION;

    @Getter
    private int protocol;
    private LegacyStateMapper legacyStateMapper;
    private BlockStateSnapshot unknownBlock;
    private int unknownBlockRuntimeId;

    private static final ExpiringMap<NbtMap, NbtMap> STATE_UPDATE_CACHE = ExpiringMap.builder()
            .maxSize(1024)
            .expiration(60L, TimeUnit.SECONDS)
            .expirationPolicy(ExpirationPolicy.ACCESSED)
            .build();
    private final Int2ObjectMap<BlockStateSnapshot> runtimeIdToSnapshot = new Int2ObjectOpenHashMap<>();
    private final Object2ObjectMap<NbtMap, BlockStateSnapshot> stateToSnapshot = new Object2ObjectOpenCustomHashMap<>(new Hash.Strategy<NbtMap>() {
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
        instance = new BlockStateMapping(CURRENT_LEVEL_PROTOCOL);
        instance.setLegacyStateMapper(new NukkitLegacyMapper());
        NukkitLegacyMapper.registerStates(instance);

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

        if (Boolean.parseBoolean(System.getProperty("Dleveldb-chunker"))) {
            blockStateUpdaters.add(BlockStateUpdaterChunker.INSTANCE);
            log.warn("Enabled chunker.app LevelDB updater. This may impact chunk loading performance!");
        }

        CompoundTagUpdaterContext context = new CompoundTagUpdaterContext();
        blockStateUpdaters.forEach(updater -> updater.registerUpdaters(context));
        CONTEXT = context;
        LATEST_VERSION = context.getLatestVersion();
    }

    public static BlockStateMapping get() {
        return instance;
    }

    public BlockStateMapping(int protocol) {
        this(protocol, null);
    }

    public BlockStateMapping(int protocol, LegacyStateMapper legacyStateMapper) {
        this.protocol = protocol;
        this.legacyStateMapper = legacyStateMapper;
    }

    public void clear() {
        this.runtimeIdToSnapshot.clear();
        this.stateToSnapshot.clear();
    }

    public void registerMapping(int runtimeId, NbtMap nbtMap) {
        Preconditions.checkArgument(!this.runtimeIdToSnapshot.containsKey(runtimeId), "Runtime ID " + runtimeId + " is already registered");
        Preconditions.checkArgument(!this.stateToSnapshot.containsKey(nbtMap), "Block state " + nbtMap + " is already registered");
        BlockStateSnapshot blockStateSnapshot = BlockStateSnapshot.builder().version(this.protocol).vanillaState(nbtMap).runtimeId(runtimeId).build();
        this.runtimeIdToSnapshot.put(runtimeId, blockStateSnapshot);
        this.stateToSnapshot.put(nbtMap, blockStateSnapshot);
    }

    public void setLegacyStateMapper(LegacyStateMapper legacyStateMapper) {
        this.legacyStateMapper = legacyStateMapper;
    }

    public LegacyStateMapper getLegacyStateMapper() {
        return legacyStateMapper;
    }

    public NbtMap updateBlockState(NbtMap tag) {
        NbtMap newTag = STATE_UPDATE_CACHE.get(tag);
        if (newTag == null) {
            int version = tag.getInt("version");
            newTag = CONTEXT.update(tag, LATEST_VERSION == version ? version - 1 : version);
            STATE_UPDATE_CACHE.put(tag, newTag);
        }
        return newTag;
    }

    public BlockStateSnapshot getBlockStateFromFullId(int fullId) {
        return getBlockState(fullId >> Block.DATA_BITS, fullId & Block.DATA_MASK);
    }

    public BlockStateSnapshot getBlockState(int id, int meta) {
        int runtimeId = this.legacyStateMapper.getRuntimeId(id, meta);
        if (runtimeId == -1) {
            log.warn("Unknown block: " + id + ":" + meta);
            return this.getUnknownBlockState();
        }
        return this.getBlockStateFromRuntimeId(runtimeId);
    }

    public BlockStateSnapshot getBlockStateFromRuntimeId(int runtimeId) {
        BlockStateSnapshot blockStateSnapshot = this.runtimeIdToSnapshot.get(runtimeId);
        if (blockStateSnapshot == null) {
            log.warn("Unknown runtime ID: " + runtimeId);
            return this.getUnknownBlockState();
        }
        return blockStateSnapshot;
    }

    public BlockStateSnapshot getBlockState(NbtMap nbtMap) {
        BlockStateSnapshot blockStateSnapshot = this.stateToSnapshot.get(nbtMap);
        if (blockStateSnapshot == null) {
            log.debug("Unknown block state: " + nbtMap);
            return BlockStateSnapshot.builder().vanillaState(nbtMap).runtimeId(this.getUnknownBlockState().getRuntimeId()).version(this.protocol).custom(true).build();
        }
        return blockStateSnapshot;
    }

    public BlockStateSnapshot getBlockStateOriginal(NbtMap nbtMap) {
        return this.stateToSnapshot.get(nbtMap);
    }

    public BlockStateSnapshot getBlockState(NbtMap tag, NbtMap newTag) {
        BlockStateSnapshot blockStateSnapshot = this.getBlockStateOriginal(newTag);
        if (blockStateSnapshot != null) {
            return blockStateSnapshot;
        }
        log.debug("Unknown block state: " + tag);
        return BlockStateSnapshot.builder().vanillaState(tag).runtimeId(this.getUnknownBlockState().getRuntimeId()).version(this.protocol).custom(true).build();
    }

    public BlockStateSnapshot getOrUpdateBlockState(NbtMap nbtMap) {
        BlockStateSnapshot stateSnapshot = this.getBlockStateOriginal(nbtMap);
        if (stateSnapshot == null || stateSnapshot.getLegacyId() == Block.INFO_UPDATE) {
            stateSnapshot = this.getBlockState(BlockStateMapping.get().updateBlockState(nbtMap));
        }
        return stateSnapshot;
    }

    public int getLegacyId(int runtimeId) {
        int blockId = this.legacyStateMapper.getBlockId(runtimeId);
        if (blockId == -1) {
            log.warn("Unknown runtime ID: " + runtimeId);
            blockId = this.legacyStateMapper.getBlockId(this.getUnknownBlockRuntimeId());
        }
        return blockId;
    }

    public int getLegacyData(int runtimeId) {
        int blockId = this.legacyStateMapper.getBlockData(runtimeId);
        if (blockId == -1) {
            log.warn("Unknown runtime ID: " + runtimeId);
            blockId = this.legacyStateMapper.getBlockData(this.getUnknownBlockRuntimeId());
        }
        return blockId;
    }

    public BlockStateSnapshot getUnknownBlockState() {
        if (this.unknownBlock == null) {
            this.createUnknownBlock(Block.INFO_UPDATE, 0);
        }
        return this.unknownBlock;
    }

    public void createUnknownBlock(int id, int meta) {
        int runtimeId = this.legacyStateMapper.getRuntimeId(id, meta);
        Preconditions.checkArgument(runtimeId != -1, "");
        this.unknownBlockRuntimeId = runtimeId;
        BlockStateSnapshot blockStateSnapshot = this.runtimeIdToSnapshot.get(runtimeId);
        Preconditions.checkNotNull(blockStateSnapshot, "");
        this.unknownBlock = blockStateSnapshot;
    }

    public int getUnknownBlockRuntimeId() {
        if (this.unknownBlockRuntimeId == -1) {
            this.createUnknownBlock(Block.INFO_UPDATE, 0);
        }
        return this.unknownBlockRuntimeId;
    }
}
