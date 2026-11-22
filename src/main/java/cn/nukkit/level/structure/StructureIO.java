package cn.nukkit.level.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**
 * Bedrock .mcstructure 读写。
 * <p>
 * Reads and writes the Bedrock .mcstructure layout (format_version 1/2, two block
 * layers, block palette with name/version/states, block_position_data and entities).
 */
@Log4j2
public final class StructureIO {

    /**
     * 与原版一致的单轴尺寸上限（Y 轴覆盖整个世界高度）。
     * <p>
     * Per-axis limits matching vanilla structure blocks (Y covers the full world height).
     */
    public static final int MAX_SIZE_X = 64;
    public static final int MAX_SIZE_Y = 384;
    public static final int MAX_SIZE_Z = 64;

    private StructureIO() {
    }

    /**
     * 结构 state 标签统一使用服务端支持的最新调色板，避免 feature 版本落后导致新方块被写成 info_update。
     * <p>
     * Structure state tags always use the newest palette the server supports, so blocks added
     * after the feature version are not degraded to {@code minecraft:info_update}.
     */
    static BlockPalette statePalette() {
        return GlobalBlockPalette.getPaletteByProtocol(GameVersion.getLastVersion());
    }

    public static Structure fromNbt(CompoundTag root, BlockPalette palette) {
        int formatVersion = root.getInt("format_version");
        if (formatVersion != 1 && formatVersion != 2) {
            throw new IllegalArgumentException("Unsupported structure format_version " + formatVersion);
        }
        int[] size = readSize(root);
        int volume = Structure.volume(size[0], size[1], size[2]);
        CompoundTag structureTag = readStructureTag(root);
        ListTag<? extends Tag> indices = readIndices(structureTag);
        int[] layer0 = readLayer(indices, 0, volume);
        int[] layer1 = readLayer(indices, 1, volume);

        CompoundTag defaultPalette = structureTag.getCompound("palette").getCompound("default");
        ListTag<CompoundTag> blockPalette = defaultPalette.getList("block_palette", CompoundTag.class);
        validateIndices(layer0, blockPalette.size());
        validateIndices(layer1, blockPalette.size());
        Palette resolved = readPalette(blockPalette, palette);

        List<CompoundTag> entities = new ArrayList<>(structureTag.getList("entities", CompoundTag.class).getAll());
        return new Structure(size[0], size[1], size[2], resolved.tags(), resolved.legacyIds(), layer0, layer1,
                readBlockEntities(defaultPalette, volume), entities, readOrigin(root));
    }

    public static CompoundTag toNbt(Structure structure) {
        CompoundTag root = new CompoundTag();
        root.putInt("format_version", Structure.FORMAT_VERSION);
        root.putList("size", intList(structure.getSizeX(), structure.getSizeY(), structure.getSizeZ()));
        root.putCompound("structure", writeStructure(structure));
        BlockVector3 origin = structure.getOrigin();
        root.putList("structure_world_origin", intList(origin.x, origin.y, origin.z));
        return root;
    }

    private static CompoundTag writeStructure(Structure structure) {
        CompoundTag structureTag = new CompoundTag();
        structureTag.putList("block_indices", writeIndices(structure));
        CompoundTag defaultPalette = new CompoundTag();
        defaultPalette.putList("block_palette", copyPalette(structure));
        defaultPalette.putCompound("block_position_data", copyBlockEntities(structure));
        structureTag.putCompound("palette", new CompoundTag().putCompound("default", defaultPalette));
        ListTag<CompoundTag> entities = new ListTag<>();
        for (CompoundTag entity : structure.getEntities()) {
            entities.add(entity.copy());
        }
        structureTag.putList("entities", entities);
        return structureTag;
    }

    /**
     * v1 要求两层都是 int 列表，空副层也要写 {@code -1}；v2 的 int 数组层只读不写。
     * <p>
     * v1 requires both layers as int lists — an empty secondary layer is written as {@code -1}s;
     * v2 int-array layers are read but never written.
     */
    private static ListTag<ListTag<IntTag>> writeIndices(Structure structure) {
        int volume = structure.getVolume();
        ListTag<IntTag> layer0 = new ListTag<>();
        ListTag<IntTag> layer1 = new ListTag<>();
        for (int i = 0; i < volume; i++) {
            layer0.add(new IntTag(structure.getBlockIndex(0, i)));
            layer1.add(new IntTag(structure.getBlockIndex(1, i)));
        }
        return new ListTag<ListTag<IntTag>>().add(layer0).add(layer1);
    }

    private static ListTag<CompoundTag> copyPalette(Structure structure) {
        ListTag<CompoundTag> palette = new ListTag<>();
        for (CompoundTag entry : structure.getPalette()) {
            palette.add(entry.copy());
        }
        return palette;
    }

    private static CompoundTag copyBlockEntities(Structure structure) {
        CompoundTag positionData = new CompoundTag();
        for (Map.Entry<Integer, CompoundTag> entry : structure.getBlockEntities().entrySet()) {
            positionData.putCompound(String.valueOf(entry.getKey()), entry.getValue().copy());
        }
        return positionData;
    }

    private static ListTag<IntTag> intList(int first, int second, int third) {
        return new ListTag<IntTag>()
                .add(new IntTag(first))
                .add(new IntTag(second))
                .add(new IntTag(third));
    }

    private static int[] readSize(CompoundTag root) {
        ListTag<IntTag> sizeTag = root.getList("size", IntTag.class);
        if (sizeTag.size() < 3) {
            throw new IllegalArgumentException("Missing structure size");
        }
        int sizeX = sizeTag.get(0).getData();
        int sizeY = sizeTag.get(1).getData();
        int sizeZ = sizeTag.get(2).getData();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Invalid structure size " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (sizeX > MAX_SIZE_X || sizeY > MAX_SIZE_Y || sizeZ > MAX_SIZE_Z) {
            throw new IllegalArgumentException("Structure exceeds the maximum size " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        return new int[]{sizeX, sizeY, sizeZ};
    }

    /**
     * v2 起可只压缩 {@code structure} 标签（1.26.60+ 导出默认压缩），解压后与未压缩文件同构。
     * <p>
     * Since v2 the {@code structure} tag alone may be zlib-compressed (default export since 1.26.60);
     * the inflated payload is the same compound as in an uncompressed file.
     */
    private static CompoundTag readStructureTag(CompoundTag root) {
        if (root.containsCompound("structure")) {
            return root.getCompound("structure");
        }
        if (root.getInt("compression") != 1 || !(root.get("structure") instanceof ByteArrayTag compressed)) {
            throw new IllegalArgumentException("Missing structure");
        }
        try (InputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed.data))) {
            if (NBTIO.readTag(input, ByteOrder.LITTLE_ENDIAN, false) instanceof CompoundTag tag) {
                return tag;
            }
            throw new IllegalArgumentException("Compressed structure is not a compound tag");
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to decompress structure", e);
        }
    }

    private static ListTag<? extends Tag> readIndices(CompoundTag structureTag) {
        ListTag<? extends Tag> indices = structureTag.getList("block_indices");
        if (indices.isEmpty()) {
            throw new IllegalArgumentException("Missing block_indices");
        }
        return indices;
    }

    private static int[] readLayer(ListTag<? extends Tag> indices, int layer, int volume) {
        if (layer >= indices.size()) {
            int[] empty = new int[volume];
            Arrays.fill(empty, Structure.VOID);
            return empty;
        }
        int[] data = readLayerData(indices.get(layer));
        if (data.length != volume) {
            throw new IllegalArgumentException("block_indices length " + data.length + " does not match volume " + volume);
        }
        return data;
    }

    /**
     * v1 每层是 TAG_List&lt;TAG_Int&gt;，v2 是 TAG_Int_Array，两种都要能读。
     * <p>
     * v1 stores each layer as a list of ints, v2 as an int array; accept both.
     */
    private static int[] readLayerData(Tag layer) {
        if (layer instanceof IntArrayTag array) {
            return array.getData().clone();
        }
        if (layer instanceof ListTag<?> list && list.type == Tag.TAG_Int) {
            int[] data = new int[list.size()];
            for (int i = 0; i < data.length; i++) {
                if (!(list.get(i) instanceof IntTag entry)) {
                    throw new IllegalArgumentException("Unsupported block_indices entry at " + i);
                }
                data[i] = entry.getData();
            }
            return data;
        }
        throw new IllegalArgumentException("Unsupported block_indices layer type");
    }

    private static Palette readPalette(ListTag<CompoundTag> blockPalette, BlockPalette palette) {
        List<CompoundTag> tags = new ArrayList<>(blockPalette.size());
        int[] legacyIds = new int[blockPalette.size()];
        for (int i = 0; i < blockPalette.size(); i++) {
            CompoundTag entry = normalizePaletteEntry(blockPalette.get(i));
            tags.add(entry);
            legacyIds[i] = palette.getLegacyFullIdWithFallback(entry);
            if (legacyIds[i] < 0) {
                log.warn("Structure load: palette entry {} has no matching state, using minecraft:info_update",
                        entry.getString("name"));
            }
        }
        return new Palette(tags, legacyIds);
    }

    private static Map<Integer, CompoundTag> readBlockEntities(CompoundTag defaultPalette, int volume) {
        Map<Integer, CompoundTag> blockEntities = new HashMap<>();
        for (Map.Entry<String, Tag> entry : defaultPalette.getCompound("block_position_data").getTags().entrySet()) {
            if (!(entry.getValue() instanceof CompoundTag data)) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (index >= 0 && index < volume) {
                blockEntities.put(index, normalizeBlockEntityEntry(data));
            }
        }
        return blockEntities;
    }

    private static BlockVector3 readOrigin(CompoundTag root) {
        ListTag<IntTag> originTag = root.getList("structure_world_origin", IntTag.class);
        return originTag.size() >= 3
                ? new BlockVector3(originTag.get(0).getData(), originTag.get(1).getData(), originTag.get(2).getData())
                : new BlockVector3(0, 0, 0);
    }

    private static void validateIndices(int[] layer, int paletteSize) {
        for (int index : layer) {
            if (index < Structure.VOID || index >= paletteSize) {
                throw new IllegalArgumentException("Palette index " + index + " is out of range");
            }
        }
    }

    /**
     * 文件里的调色板条目可能缺少 states，补齐后再参与哈希与导出。
     * <p>
     * Some files omit the states compound; add it so hashing and re-export stay consistent.
     */
    private static CompoundTag normalizePaletteEntry(CompoundTag entry) {
        CompoundTag copy = entry.copy();
        if (!copy.containsCompound("states")) {
            copy.putCompound("states", new CompoundTag());
        }
        if (!copy.containsString("name") || "minecraft:unknown".equals(copy.getString("name"))) {
            copy.putString("name", "minecraft:unknown");
        }
        return copy;
    }

    private static CompoundTag normalizeBlockEntityEntry(CompoundTag entry) {
        if (entry.containsCompound("block_entity_data")) {
            return entry;
        }
        return new CompoundTag().putCompound("block_entity_data", entry);
    }

    private record Palette(List<CompoundTag> tags, int[] legacyIds) {
    }
}
