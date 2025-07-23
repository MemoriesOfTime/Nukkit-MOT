package cn.nukkit.block.custom;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.custom.comparator.AlphabetPaletteComparator;
import cn.nukkit.block.custom.comparator.HashedPaletteComparator;
import cn.nukkit.block.custom.container.BlockContainer;
import cn.nukkit.block.custom.container.BlockContainerFactory;
import cn.nukkit.block.custom.container.BlockStorageContainer;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.block.custom.properties.exception.InvalidBlockPropertyMetaException;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.LevelDBConstants;
import cn.nukkit.level.format.leveldb.NukkitLegacyMapper;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.*;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;

@Log4j2
public class CustomBlockManager {

    public static final Path BIN_PATH = Paths.get("bin/");
    public static final int LOWEST_CUSTOM_BLOCK_ID = 10000;

    private static CustomBlockManager instance;

    public static CustomBlockManager init(Server server) {
        if (instance == null) {
            return instance = new CustomBlockManager(server);
        }
        throw new IllegalStateException("CustomBlockManager was already initialized!");
    }

    public static CustomBlockManager get() {
        return instance;
    }

    private final Server server;

    private final Int2ObjectMap<CustomBlockDefinition> blockDefinitions = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<CustomBlockState> legacy2CustomState = new Int2ObjectOpenHashMap<>();

    private volatile boolean closed = false;

    private CustomBlockManager(Server server) {
        this.server = server;

        Path filesPath = this.getBinPath();
        if (!Files.isDirectory(filesPath)) {
            try {
                Files.createDirectories(filesPath);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create BIN_DIRECTORY", e);
            }
        }
    }

    public void registerCustomBlock(String identifier, int nukkitId, Supplier<BlockContainer> factory) {
        this.registerCustomBlock(identifier, nukkitId, CustomBlockDefinition.builder(factory.get()).build(), factory);
    }

    public void registerCustomBlock(String identifier, int nukkitId, CustomBlockDefinition blockDefinition, Supplier<BlockContainer> factory) {
        this.registerCustomBlock(identifier, nukkitId, null, blockDefinition, meta -> factory.get());
    }

    private static void variantGenerations(BlockProperties properties, String[] states, List<Map<String, Serializable>> variants, Map<String, Serializable> temp, int offset) {
        if (states.length - offset >= 0) {
            final String currentState = states[states.length - offset];

            properties.getBlockProperty(currentState).forEach((value) -> {
                temp.put(currentState, value);
                if(!variants.contains(temp))
                    variants.add(new HashMap<>(temp));
                variantGenerations(properties, states, variants, temp, offset + 1);
            });

            temp.put(currentState, 0);//def value
        }
    }

    private static List<Map<String, Serializable>> variantGenerations(BlockProperties properties, String[] states) {
        final Map<String, Serializable> temp = new HashMap<>();
        for(String state : states) {
            temp.put(state, 0);//def value
        }

        final List<Map<String, Serializable>> variants = new ArrayList<>();
        if(states.length == 0) {
            variants.add(temp);
        }

        variantGenerations(properties, states, variants, temp, 1);
        return variants;
    }

    public void registerCustomBlock(String identifier, int nukkitId, BlockProperties properties, CustomBlockDefinition blockDefinition, BlockContainerFactory factory) {
        if (this.closed) {
            throw new IllegalStateException("Block registry was already closed");
        }

        if (nukkitId < LOWEST_CUSTOM_BLOCK_ID) {
            throw new IllegalArgumentException("Block ID can not be lower than " + LOWEST_CUSTOM_BLOCK_ID);
        }

        BlockContainer blockSample = factory.create(0);
        if (blockSample instanceof BlockStorageContainer && properties == null) {
            properties = ((BlockStorageContainer) blockSample).getBlockProperties();
            log.warn("Custom block {} was registered using wrong method! Trying to use sample properties!", identifier);
        }

        if (properties != null && blockDefinition == null) {
            throw new IllegalArgumentException("Block network data can not be empty for block with more permutations: " + identifier);
        }

        CustomBlockState defaultState = this.createBlockState(identifier, nukkitId << Block.DATA_BITS, properties, factory);
        this.legacy2CustomState.put(defaultState.getLegacyId(), defaultState);

        // TODO: unsure if this is per state or not
        this.blockDefinitions.put(defaultState.getLegacyId(), blockDefinition);

        int itemId = 255 - nukkitId;
        for (RuntimeItemMapping mapping : RuntimeItems.VALUES) {
            mapping.registerItem(identifier, nukkitId, itemId, 0);
        }

        if (properties != null) {
            BlockProperties finalProperties = properties;
            variantGenerations(properties, properties.getNames().toArray(new String[0]))
                    .forEach(states -> {
                        final int[] meta = {0};
                        states.forEach((name, value) -> {
                            meta[0] = finalProperties.setValue(meta[0], name, value);
                        });

                        if(meta[0] != 0) {
                            CustomBlockState state;
                            try {
                                state = this.createBlockState(identifier, (nukkitId << Block.DATA_BITS) | meta[0], finalProperties, factory);
                            } catch (InvalidBlockPropertyMetaException e) {
                                log.error(e);
                                return; // Nukkit has more states than our block
                            }
                            this.legacy2CustomState.put(state.getLegacyId(), state);
                        }
                    });
        }
    }

    private CustomBlockState createBlockState(String identifier, int legacyId, BlockProperties properties, BlockContainerFactory factory) {
        int meta = legacyId & Block.DATA_MASK;

        NbtMapBuilder statesBuilder = NbtMap.builder();
        if (properties != null) {
            for (String propertyName : properties.getNames()) {
                BlockProperty<?> property = properties.getBlockProperty(propertyName);
                if (property instanceof EnumBlockProperty) {
                    statesBuilder.put(property.getPersistenceName(), properties.getPersistenceValue(meta, propertyName));
                } else {
                    statesBuilder.put(property.getPersistenceName(), properties.getValue(meta, propertyName));
                }
            }
        }

        NbtMap state = NbtMap.builder()
                .putString("name", identifier)
                .putCompound("states", statesBuilder.build())
                .putInt("version", LevelDBConstants.STATE_VERSION)
                .build();
        return new CustomBlockState(identifier, legacyId, state, factory);
    }

    public boolean closeRegistry() throws IOException {
        if (this.closed) {
            throw new IllegalStateException("Block registry was already closed");
        }

        this.closed = true;
        if (this.legacy2CustomState.isEmpty()) {
            return false;
        }

        long startTime = System.currentTimeMillis();

        BlockPalette storagePalette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.getFeatureVersion());
        boolean result = false;
        ObjectSet<BlockPalette> set = new ObjectArraySet<>();
        for (GameVersion gameVersion : GameVersion.values()) {
            int protocol = gameVersion.getProtocol();
            if (protocol < ProtocolInfo.v1_16_100 || protocol < this.server.minimumProtocol) {
                continue;
            }

            BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(gameVersion);
            if (set.contains(palette)) {
                continue;
            }
            set.add(palette);

            if (palette.getProtocol() == storagePalette.getProtocol()) {
                this.recreateBlockPalette(palette, new ObjectArrayList<>(NukkitLegacyMapper.loadBlockPalette()));
            } else {
                Path path = this.getVanillaPalettePath(palette.getProtocol());
                if (!Files.exists(path)) {
                    log.warn("No vanilla palette found for {}.", Utils.getVersionByProtocol(palette.getProtocol()));
                    continue;
                }
                this.recreateBlockPalette(palette);
            }
            result = true;
        }

        log.info("Custom block registry closed in {}ms", (System.currentTimeMillis() - startTime));
        return result;
    }

    private void recreateBlockPalette(BlockPalette palette) throws IOException {
        List<NbtMap> vanillaPalette = new ObjectArrayList<>(this.loadVanillaPalette(palette.getProtocol()));
        this.recreateBlockPalette(palette, vanillaPalette);
    }

    private void recreateBlockPalette(BlockPalette palette, List<NbtMap> vanillaPalette) {
        Map<String, List<NbtMap>> vanillaPaletteList;
        if (palette.getProtocol() >= ProtocolInfo.v1_18_30) {
            vanillaPaletteList = new Object2ObjectRBTreeMap<>(HashedPaletteComparator.INSTANCE);
        } else {
            vanillaPaletteList = new Object2ObjectRBTreeMap<>(AlphabetPaletteComparator.INSTANCE);
        }

        int paletteVersion = -1;
        String lastName = null;
        List<NbtMap> group = new ObjectArrayList<>();
        int runtimeId = 0;
        Int2ObjectMap<NbtMap> runtimeId2State = new Int2ObjectOpenHashMap<>();
        for (NbtMap state : vanillaPalette) {
            //删除不属于原版的内容
            if (state.containsKey("network_id") || state.containsKey("name_hash") || state.containsKey("block_id")) {
                NbtMapBuilder builder = NbtMapBuilder.from(state);
                builder.remove("network_id");
                builder.remove("name_hash");
                builder.remove("block_id");
                state = builder.build();
            }

            int version = state.getInt("version");
            if (version != paletteVersion) {
                paletteVersion = version;
            }

            String name = state.getString("name");
            if (lastName != null && !name.equals(lastName)) {
                vanillaPaletteList.put(lastName, group);
                group = new ObjectArrayList<>();
            }
            group.add(state);
            runtimeId2State.put(runtimeId++, state);
            lastName = name;
        }
        if (lastName != null) {
            vanillaPaletteList.put(lastName, group);
        }

        Object2ObjectMap<NbtMap, IntSet> state2Legacy = new Object2ObjectLinkedOpenHashMap<>();

        for (Int2IntMap.Entry entry : palette.getLegacyToRuntimeIdMap().int2IntEntrySet()) {
            int rid = entry.getIntValue();
            NbtMap state = runtimeId2State.get(rid);
            if (state == null) {
                log.info("Unknown runtime ID {}! protocol={}", rid, palette.getProtocol());
                continue;
            }
            IntSet legacyIds = state2Legacy.computeIfAbsent(state, s -> new IntOpenHashSet());
            legacyIds.add(entry.getIntKey());
        }

        lastName = null;
        group = new ObjectArrayList<>();
        for (CustomBlockState definition : this.legacy2CustomState.values()) {
            NbtMap state = definition.getBlockState();
            if (state.getInt("version") != paletteVersion) {
                state = state.toBuilder().putInt("version", paletteVersion).build();
            }
            state2Legacy.computeIfAbsent(state, s -> new IntOpenHashSet()).add(legacyToFullId(definition.getLegacyId()));

            String name = state.getString("name");
            if (lastName != null && !name.equals(lastName)) {
                vanillaPaletteList.put(lastName, group);
                group = new ObjectArrayList<>();
            }
            group.add(state);
            lastName = name;
        }
        if (lastName != null) {
            vanillaPaletteList.put(lastName, group);
        }

        palette.clearStates();
        boolean levelDb = palette.getProtocol() == GlobalBlockPalette.getPaletteByProtocol(GameVersion.getFeatureVersion()).getProtocol(); //防止小版本不相等问题
        if (levelDb) {
            BlockStateMapping.get().clearMapping();
        }

        runtimeId = 0;
        for (List<NbtMap> states : vanillaPaletteList.values()) {
            for (NbtMap state : states) {
                if(!levelDb || !BlockStateMapping.get().containsState(state)) {
                    if (levelDb) {
                        BlockStateMapping.get().registerState(runtimeId, state);
                    }

                    IntSet legacyIds = state2Legacy.get(state);
                    if (legacyIds != null) {
                        CompoundTag nukkitState = convertNbtMap(state);
                        for (Integer fullId : legacyIds) {
                            palette.registerState(fullId >> Block.DATA_BITS, (fullId & Block.DATA_MASK), runtimeId, nukkitState);
                        }
                    }
                }
                runtimeId++;
            }
        }
    }

    private List<NbtMap> loadVanillaPalette(int version) throws FileNotFoundException {
        Path path = this.getVanillaPalettePath(version);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Missing vanilla palette for version " + version);
        }

        try (InputStream stream = Files.newInputStream(path)) {
            return ((NbtMap) NbtUtils.createGZIPReader(stream).readTag()).getList("blocks", NbtType.COMPOUND);
        } catch (Exception e) {
            throw new AssertionError("Error while loading vanilla palette", e);
        }
    }

    private Path getVanillaPalettePath(int version) {
        return this.getBinPath().resolve("vanilla_palette_" + version + ".nbt");
    }

    public Block getBlock(int legacyId) {
        CustomBlockState state = this.legacy2CustomState.get(legacyId);
        if (state == null) {
            return Block.get(BlockID.INFO_UPDATE);
        }

        BlockContainer block = state.getFactory().create(legacyId & Block.DATA_MASK);
        if (block instanceof Block) {
            return (Block) block;
        }
        return null;
    }

    public Block getBlock(int[] fullState) {
        return getBlock(fullState[0], fullState[1]);
    }

    public Block getBlock(int id, int meta) {
        int legacyId = id << Block.DATA_BITS | meta;
        CustomBlockState state = this.legacy2CustomState.get(legacyId);
        if (state == null) {
            state = this.legacy2CustomState.get(id << Block.DATA_BITS);
            if (state == null) {
                return Block.get(BlockID.INFO_UPDATE);
            }
        }

        BlockContainer block = state.getFactory().create(meta);
        if (block instanceof Block) {
            return (Block) block;
        }
        return null;
    }

    public Class<?> getClassType(int blockId) {
        CustomBlockDefinition definition = this.blockDefinitions.get(blockId << Block.DATA_BITS);
        if (definition == null) {
            return null;
        }
        return definition.typeOf();
    }

    private Path getBinPath() {
        return Paths.get(this.server.getDataPath()).resolve(BIN_PATH);
    }

    public Collection<CustomBlockDefinition> getBlockDefinitions() {
        return Collections.unmodifiableCollection(this.blockDefinitions.values());
    }

    private static int legacyToFullId(int legacyId) {
        int blockId = legacyId >> Block.DATA_BITS;
        int meta = legacyId & Block.DATA_MASK;
        return (blockId << Block.DATA_BITS) | meta;
    }

    public static CompoundTag convertNbtMap(NbtMap nbt) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try (stream; NBTOutputStream nbtOutputStream = NbtUtils.createWriter(stream)) {
                nbtOutputStream.writeTag(nbt);
            }
            return NBTIO.read(stream.toByteArray(), ByteOrder.BIG_ENDIAN, false);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert NbtMap: " + nbt, e);
        }
    }
}
