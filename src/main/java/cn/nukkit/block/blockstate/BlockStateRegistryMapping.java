package cn.nukkit.block.blockstate;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockUnknown;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.HumanStringComparator;
import cn.nukkit.utils.MinecraftNamespaceComparator;
import cn.nukkit.utils.exception.BlockPropertyNotFoundException;
import cn.nukkit.utils.exception.InvalidBlockStateException;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 保存着从{@link BlockState} -> runtimeid 的注册表
 */
@ParametersAreNonnullByDefault
@Log4j2
public class BlockStateRegistryMapping {

    private final int protocolId;

    public final AtomicInteger blockPaletteVersion = new AtomicInteger(0);

    private static final Pattern BLOCK_ID_NAME_PATTERN = Pattern.compile("^blockid:(\\d+)$");
    private final Registration updateBlockRegistration;
    private final Map<BlockState, Registration> blockStateRegistration = new ConcurrentHashMap<>();
    private final Map<String, Registration> stateIdRegistration = new ConcurrentHashMap<>();
    private final Int2ObjectMap<Registration> runtimeIdRegistration = new Int2ObjectOpenHashMap<>();

    private final Int2ObjectMap<Registration> blockStateHashRegistration = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<String> blockIdToPersistenceName;
    private final Map<String, Integer> persistenceNameToBlockId;
    private byte[] blockPaletteBytes;
    private final List<String> knownStateIds;

    public BlockStateRegistryMapping(Int2ObjectMap<String> blockIdToPersistenceName, Map<String, Integer> persistenceNameToBlockId, int protocolId) {
        this.blockIdToPersistenceName = blockIdToPersistenceName;
        this.persistenceNameToBlockId = persistenceNameToBlockId;
        this.protocolId = protocolId;

        //<editor-fold desc="Loading canonical_block_states.nbt" defaultstate="collapsed">
        List<CompoundTag> tags = new ArrayList<>();
        knownStateIds = new ArrayList<>();
        try (InputStream stream = Server.class.getClassLoader().getResourceAsStream("RuntimeBlockStates/v" + protocolId + "/canonical_block_states.nbt")) {
            if (stream == null) {
                throw new AssertionError("Unable to locate block state nbt");
            }

            try (BufferedInputStream bis = new BufferedInputStream(stream)) {
                int runtimeId = 0;
                while (bis.available() > 0) {
                    CompoundTag tag = NBTIO.readNoClose(bis, ByteOrder.BIG_ENDIAN, true);
                    tag.putInt("runtimeId", runtimeId++);
                    tag.putInt("blockId", persistenceNameToBlockId.getOrDefault(tag.getString("name").toLowerCase(Locale.ENGLISH), -1));
                    tags.add(tag);
                }
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        //</editor-fold>
        Integer infoUpdateRuntimeId = null;

        Set<String> warned = new HashSet<>();

        for (CompoundTag state : tags) {
            int blockId = state.getInt("blockId");
            int runtimeId = state.getInt("runtimeId");
            String name = state.getString("name").toLowerCase();
            if (name.equals("minecraft:unknown")) {
                infoUpdateRuntimeId = runtimeId;
            }

            // Special condition: minecraft:wood maps 3 blocks, minecraft:wood, minecraft:log and minecraft:log2
            // All other cases, register the name normally
            if (isNameOwnerOfId(name, blockId)) {
                registerPersistenceName(blockId, name);
                registerStateId(state, runtimeId);
            } else if (blockId == -1) {
                if (RuntimeItems.getMapping(protocolId).fromIdentifier(name) == null) {
                    if (warned.add(name)) {
                        log.warn("Unknown block id for the block named {}", name);
                    }
                }
                registerStateId(state, runtimeId);
            }
        }

        blockPaletteVersion.set(tags.get(0).getInt("version"));

        if (infoUpdateRuntimeId == null) {
            throw new IllegalStateException("Could not find the minecraft:info_update runtime id!");
        }

        updateBlockRegistration = findRegistrationByRuntimeId(infoUpdateRuntimeId);

        try {
            blockPaletteBytes = NBTIO.write(tags, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SneakyThrows
    private void registerStateId(CompoundTag block, int runtimeId) {
        String stateId = getStateId(block);
        CompoundTag pureTag = block
                .clone()
                .remove("blockId")
                .remove("version")
                .remove("runtimeId");
        if (!pureTag.contains("states"))
            pureTag.putCompound("states", new CompoundTag());
        else pureTag.putCompound("states", new CompoundTag(new TreeMap<>(pureTag.getCompound("states").getTags())));
        Registration registration = new Registration(null, runtimeId, MinecraftNamespaceComparator.fnv1a_32(NBTIO.write(pureTag, ByteOrder.LITTLE_ENDIAN)), block);

        Registration old = stateIdRegistration.putIfAbsent(stateId, registration);
        if (old != null && !old.equals(registration)) {
            throw new UnsupportedOperationException("The persistence NBT registration tried to replaced a runtime id. Old:" + old + ", New:" + runtimeId + ", State:" + stateId);
        }
        knownStateIds.add(stateId);
        runtimeIdRegistration.put(runtimeId, registration);
        blockStateHashRegistration.put(registration.blockStateHash, registration);
    }

    private void registerState(int blockId, int meta, CompoundTag originalState, int runtimeId) {
        BlockState state = BlockState.of(blockId, meta);
        Registration registration = new Registration(state, runtimeId, state.getBlock().computeBlockStateHash(), null);

        Registration old = blockStateRegistration.putIfAbsent(state, registration);
        if (old != null && !registration.equals(old)) {
            throw new UnsupportedOperationException("The persistence NBT registration tried to replaced a runtime id. Old:" + old + ", New:" + runtimeId + ", State:" + state);
        }
        runtimeIdRegistration.put(runtimeId, registration);
        blockStateHashRegistration.put(registration.blockStateHash, registration);

        stateIdRegistration.remove(getStateId(originalState));
        stateIdRegistration.remove(state.getLegacyStateId());
    }

    private boolean isNameOwnerOfId(String name, int blockId) {
        return blockId != -1 && !name.equals("minecraft:wood") || blockId == BlockID.WOOD_BARK;
    }

    @NotNull
    private String getStateId(CompoundTag block) {
        Map<String, String> propertyMap = new TreeMap<>(HumanStringComparator.getInstance());
        for (Tag tag : block.getCompound("states").getAllTags()) {
            propertyMap.put(tag.getName(), tag.parseValue().toString());
        }

        String blockName = block.getString("name").toLowerCase(Locale.ENGLISH);
        Preconditions.checkArgument(!blockName.isEmpty(), "Couldn't find the block name!");
        StringBuilder stateId = new StringBuilder(blockName);
        propertyMap.forEach((name, value) -> stateId.append(';').append(name).append('=').append(value));
        return stateId.toString();
    }

    @Nullable
    private Registration findRegistrationByRuntimeId(int runtimeId) {
        return runtimeIdRegistration.get(runtimeId);
    }

    @Nullable
    private BlockState buildStateFromCompound(CompoundTag block) {
        String name = block.getString("name").toLowerCase(Locale.ENGLISH);
        Integer id = getBlockId(name);
        if (id == null) {
            //处理在调用getBlockStateByRuntimeId时，遇到在block_mappings.json中方块的情况
            String stateId = getStateId(block);
            String fullId = BlockStateRegistry.getBlockMappings().inverse().get(stateId);
            if (fullId != null) {
                String[] sId = fullId.split(":");
                int blockId = Integer.parseInt(sId[0]);
                int blockData = Integer.parseInt(sId[1]);
                return BlockState.of(blockId, blockData);
            }
            return null;
        }

        BlockState state = BlockState.of(id);
        CompoundTag properties = block.getCompound("states");
        for (Tag tag : properties.getAllTags()) {
            state = state.withProperty(tag.getName(), tag.parseValue().toString());
        }

        return state;
    }

    private static NoSuchElementException runtimeIdNotRegistered(int runtimeId) {
        return new NoSuchElementException("The block id for the runtime id " + runtimeId + " is not registered");
    }

    private Registration getRegistration(BlockState state) {
        return blockStateRegistration.computeIfAbsent(state, this::findRegistration);
    }

    private Registration findRegistration(final BlockState state) {
        // Special case for PN-96 PowerNukkit#210 where the world contains blocks like 0:13, 0:7, etc
        if (state.getBlockId() == BlockID.AIR) {
            Registration airRegistration = blockStateRegistration.get(BlockState.AIR);
            if (airRegistration != null) {
                return new Registration(state, airRegistration.runtimeId, airRegistration.blockStateHash, null);
            }
        }

        Registration registration = findRegistrationByStateId(state);
        removeStateIdsAsync(registration);
        return registration;
    }

    private Registration findRegistrationByStateId(BlockState state) {
        Registration registration;
        try {
            registration = stateIdRegistration.remove(state.getStateId());
            if (registration != null) {
                registration.state = state;
                registration.originalBlock = null;
                return registration;
            }
        } catch (Exception e) {
            try {
                log.fatal("An error has occurred while trying to get the stateId of state: "
                                + "{}:{}"
                                + " - {}"
                                + " - {}",
                        state.getBlockId(),
                        state.getDataStorage(),
                        state.getProperties(),
                        blockIdToPersistenceName.get(state.getBlockId()),
                        e);
            } catch (Exception e2) {
                e.addSuppressed(e2);
                log.fatal("An error has occurred while trying to get the stateId of state: {}:{}",
                        state.getBlockId(), state.getDataStorage(), e);
            }
        }

        try {
            registration = stateIdRegistration.remove(state.getLegacyStateId());
            if (registration != null) {
                registration.state = state;
                registration.originalBlock = null;
                return registration;
            }
        } catch (Exception e) {
            log.fatal("An error has occurred while trying to parse the legacyStateId of {}:{}", state.getBlockId(), state.getDataStorage(), e);
        }
        return logDiscoveryError(state);
    }

    private void removeStateIdsAsync(@Nullable Registration registration) {
        if (registration != null && registration != updateBlockRegistration) {
            BlockStateRegistry.asyncStateRemover.submit(() -> stateIdRegistration.values().removeIf(r -> r.runtimeId == registration.runtimeId));
        }
    }

    private Registration logDiscoveryError(BlockState state) {
        log.error("Found an unknown BlockId:Meta combination: {}:{}"
                        + " - {}"
                        + " - {}"
                        + " - {}"
                        + ", trying to repair or replacing with an \"UPDATE!\" block.",
                state.getBlockId(), state.getDataStorage(), state.getStateId(), state.getProperties(),
                blockIdToPersistenceName.get(state.getBlockId())
        );
        return updateBlockRegistration;
    }

    public int getBlockIdByRuntimeId(int runtimeId) {
        Registration registration = findRegistrationByRuntimeId(runtimeId);
        if (registration == null) {
            throw runtimeIdNotRegistered(runtimeId);
        }
        BlockState state = registration.state;
        if (state != null) {
            return state.getBlockId();
        }
        CompoundTag originalBlock = registration.originalBlock;
        if (originalBlock == null) {
            throw runtimeIdNotRegistered(runtimeId);
        }
        try {
            state = buildStateFromCompound(originalBlock);
        } catch (BlockPropertyNotFoundException e) {
            String name = originalBlock.getString("name").toLowerCase(Locale.ENGLISH);
            Integer id = getBlockId(name);
            if (id == null) {
                throw runtimeIdNotRegistered(runtimeId);
            }
            return id;
        }
        if (state != null) {
            registration.state = state;
            registration.originalBlock = null;
        } else {
            throw runtimeIdNotRegistered(runtimeId);
        }
        return state.getBlockId();
    }

    public int getRuntimeId(BlockState state) {
        String blockMapping = BlockStateRegistry.getBlockMappings().getOrDefault(state.getBlockId() + ":" + state.getDataStorage().intValue(), null);
        if (blockMapping != null) {
            Registration registration = stateIdRegistration.get(blockMapping);
            if (registration != null) {
                return registration.runtimeId;
            }
        }
        return getRegistration(state).runtimeId;
    }

    public int getRuntimeId(int blockId) {
        return getRuntimeId(BlockState.of(blockId));
    }

    public int getRuntimeId(int blockId, int meta) {
        return getRuntimeId(BlockState.of(blockId, meta));
    }

    @Nullable
    public String getKnownBlockStateIdByRuntimeId(int runtimeId) {
        if (runtimeId >= 0 && runtimeId < knownStateIds.size()) {
            return knownStateIds.get(runtimeId);
        }
        return null;
    }

    public int getKnownRuntimeIdByBlockStateId(String stateId) {
        int result = knownStateIds.indexOf(stateId);
        if (result != -1) {
            return result;
        }
        BlockState state;
        try {
            state = BlockState.of(stateId);
        } catch (NoSuchElementException | IllegalStateException | IllegalArgumentException ignored) {
            return -1;
        }
        String fullStateId = state.getStateId();
        return knownStateIds.indexOf(fullStateId);
    }

    public int getRuntimeIdByBlockStateHash(int blockStateHash) {
        var reg = blockStateHashRegistration.get(blockStateHash);
        if (reg != null) return reg.runtimeId;
        else return -1;
    }

    /**
     * @return {@code null} if the runtime id does not matches any known block state.
     */
    @Nullable
    public BlockState getBlockStateByRuntimeId(int runtimeId) {
        Registration registration = findRegistrationByRuntimeId(runtimeId);
        if (registration == null) {
            return null;
        }
        BlockState state = registration.state;
        if (state != null) {
            return state;
        }
        CompoundTag originalBlock = registration.originalBlock;
        if (originalBlock != null) {
            state = buildStateFromCompound(originalBlock);
            if (state != null) {
                registration.state = state;
                registration.originalBlock = null;
            }
        }
        return state;
    }

    public List<String> getPersistenceNames() {
        return new ArrayList<>(persistenceNameToBlockId.keySet());
    }

    @NotNull
    public String getPersistenceName(int blockId) {
        String persistenceName = blockIdToPersistenceName.get(blockId);
        if (persistenceName == null) {
            String fallback = "blockid:" + blockId;
            log.warn("The persistence name of the block id {} is unknown! Using {} as an alternative!", blockId, fallback);
            registerPersistenceName(blockId, fallback);
            return fallback;
        }
        return persistenceName;
    }

    public void registerPersistenceName(int blockId, String persistenceName) {
        synchronized (blockIdToPersistenceName) {
            String newName = persistenceName.toLowerCase();
            String oldName = blockIdToPersistenceName.putIfAbsent(blockId, newName);
            if (oldName != null && !persistenceName.equalsIgnoreCase(oldName)) {
                throw new UnsupportedOperationException("The persistence name registration tried to replaced a name. Name:" + persistenceName + ", Old:" + oldName + ", Id:" + blockId);
            }
            Integer oldId = persistenceNameToBlockId.putIfAbsent(newName, blockId);
            if (oldId != null && blockId != oldId) {
                blockIdToPersistenceName.remove(blockId);
                throw new UnsupportedOperationException("The persistence name registration tried to replaced an id. Name:" + persistenceName + ", OldId:" + oldId + ", Id:" + blockId);
            }
        }
    }

    public int getBlockPaletteDataVersion() {
        @SuppressWarnings("UnnecessaryLocalVariable")
        Object obj = blockPaletteBytes;
        return obj.hashCode();
    }

    @NotNull
    public byte[] getBlockPaletteBytes() {
        return blockPaletteBytes.clone();
    }

    public void putBlockPaletteBytes(BinaryStream stream) {
        stream.put(blockPaletteBytes);
    }

    public int getBlockPaletteLength() {
        return blockPaletteBytes.length;
    }

    public void copyBlockPaletteBytes(byte[] target, int targetIndex) {
        System.arraycopy(blockPaletteBytes, 0, target, targetIndex, blockPaletteBytes.length);
    }

    @SuppressWarnings({"deprecation", "squid:CallToDepreca"})
    @NotNull
    public BlockProperties getProperties(int blockId) {
        int fullId = blockId << Block.DATA_BITS;
        Block block;
        if (fullId >= Block.fullList.length || fullId < 0 || (block = Block.fullList[fullId]) == null) {
            return BlockUnknown.PROPERTIES;
        }
        return block.getProperties();
    }

    @NotNull
    public MutableBlockState createMutableState(int blockId) {
        return getProperties(blockId).createMutableState(blockId);
    }

    @NotNull
    public MutableBlockState createMutableState(int blockId, int bigMeta) {
        MutableBlockState blockState = createMutableState(blockId);
        blockState.setDataStorageFromInt(bigMeta);
        return blockState;
    }

    /**
     * @throws InvalidBlockStateException
     */
    @NotNull
    public MutableBlockState createMutableState(int blockId, Number storage) {
        MutableBlockState blockState = createMutableState(blockId);
        blockState.setDataStorage(storage);
        return blockState;
    }

    public int getUpdateBlockRegistration() {
        return updateBlockRegistration.runtimeId;
    }

    @Nullable
    public Integer getBlockId(String persistenceName) {
        Integer blockId = persistenceNameToBlockId.get(persistenceName);
        if (blockId != null) {
            return blockId;
        }

        Matcher matcher = BLOCK_ID_NAME_PATTERN.matcher(persistenceName);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public int getFallbackRuntimeId() {
        return updateBlockRegistration.runtimeId;
    }

    public BlockState getFallbackBlockState() {
        return updateBlockRegistration.state;
    }

    @AllArgsConstructor
    @ToString
    @EqualsAndHashCode
    private static class Registration {
        @Nullable
        private BlockState state;
        private final int runtimeId;
        private final int blockStateHash;
        @Nullable
        private CompoundTag originalBlock;
    }

    @Data
    public static class MappingEntry {
        private final int legacyName;
        private final int damage;
    }
}
