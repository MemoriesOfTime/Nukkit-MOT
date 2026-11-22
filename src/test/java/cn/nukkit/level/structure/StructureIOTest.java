package cn.nukkit.level.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * .mcstructure 读写与调色板反查的一致性。
 * <p>
 * .mcstructure round-trip and palette reverse-lookup consistency.
 */
class StructureIOTest {

    private static BlockPalette palette;

    @BeforeAll
    static void setUp() {
        palette = new BlockPalette(GameVersion.V1_26_60);
    }

    @Test
    void paletteReverseLookupMatchesHashMapping() {
        CompoundTag stone = palette.getBlockStateTag(BlockID.STONE << Block.DATA_BITS);
        assertNotNull(stone, "palette must expose a raw state tag for stone");
        assertEquals(BlockID.STONE << Block.DATA_BITS, palette.getLegacyFullId(stone));
        assertNotNull(palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS));
    }

    @Test
    void roundTripsBlocksBlockEntitiesAndEntities() {
        CompoundTag airTag = palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS);
        CompoundTag stoneTag = palette.getBlockStateTag(BlockID.STONE << Block.DATA_BITS);
        int airLegacy = BlockID.AIR << Block.DATA_BITS;
        int stoneLegacy = BlockID.STONE << Block.DATA_BITS;

        int volume = Structure.volume(2, 2, 2);
        int[] layer0 = new int[volume];
        int[] layer1 = new int[volume];
        for (int i = 0; i < volume; i++) {
            layer0[i] = i == 0 ? 0 : 1;
            layer1[i] = Structure.VOID;
        }
        CompoundTag chest = new CompoundTag()
                .putString("id", "Chest")
                .putInt("x", 1)
                .putInt("y", 2)
                .putInt("z", 3);
        Map<Integer, CompoundTag> blockEntities = Map.of(1,
                new CompoundTag().putCompound("block_entity_data", chest));
        CompoundTag entity = new CompoundTag()
                .putString("identifier", "minecraft:zombie")
                .putList("Pos", new ListTag<cn.nukkit.nbt.tag.FloatTag>()
                        .add(new cn.nukkit.nbt.tag.FloatTag(1.5f))
                        .add(new cn.nukkit.nbt.tag.FloatTag(2.5f))
                        .add(new cn.nukkit.nbt.tag.FloatTag(3.5f)));

        Structure structure = new Structure(2, 2, 2, List.of(airTag, stoneTag),
                new int[]{airLegacy, stoneLegacy}, layer0, layer1, blockEntities, List.of(entity),
                new BlockVector3(10, 20, 30));
        CompoundTag nbt = StructureIO.toNbt(structure);
        assertEquals(Structure.FORMAT_VERSION, nbt.getInt("format_version"));
        assertEquals(3, nbt.getList("structure_world_origin").size());

        Structure loaded = StructureIO.fromNbt(nbt, palette);
        assertEquals(2, loaded.getSizeX());
        assertEquals(2, loaded.getSizeY());
        assertEquals(2, loaded.getSizeZ());
        assertEquals(airLegacy, loaded.getLegacyId(loaded.getBlockIndex(0, 0)));
        assertEquals(stoneLegacy, loaded.getLegacyId(loaded.getBlockIndex(0, 1)));
        assertEquals(Structure.VOID, loaded.getBlockIndex(1, 0));
        assertFalse(loaded.hasSecondaryLayer());
        assertEquals(10, loaded.getOrigin().x);
        assertEquals(20, loaded.getOrigin().y);
        assertEquals(30, loaded.getOrigin().z);
        assertEquals("Chest", loaded.getBlockEntities().get(1).getCompound("block_entity_data").getString("id"));
        assertEquals(1, loaded.getEntities().size());
        assertEquals("minecraft:zombie", loaded.getEntities().get(0).getString("identifier"));
    }

    @Test
    void writesBothLayersEvenWhenSecondaryIsEmpty() {
        CompoundTag airTag = palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS);
        CompoundTag waterTag = palette.getBlockStateTag(BlockID.WATER << Block.DATA_BITS);
        int volume = Structure.volume(1, 1, 2);
        int[] layer0 = new int[volume];
        int[] layer1 = new int[]{Structure.VOID, 0};
        Structure structure = new Structure(1, 1, 2, List.of(airTag, waterTag),
                new int[]{BlockID.AIR << Block.DATA_BITS, BlockID.WATER << Block.DATA_BITS},
                layer0, layer1, Map.of(), List.of(), new BlockVector3(0, 0, 0));

        CompoundTag nbt = StructureIO.toNbt(structure);
        ListTag<? extends Tag> indices = nbt.getCompound("structure").getList("block_indices");
        assertEquals(2, indices.size());
        assertTrue(StructureIO.fromNbt(nbt, palette).hasSecondaryLayer());
    }

    @Test
    void wrapsLegacyPlainBlockEntityEntry() {
        CompoundTag airTag = palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS);
        CompoundTag plain = new CompoundTag().putString("id", "Chest");
        Structure structure = new Structure(1, 1, 1, List.of(airTag),
                new int[]{BlockID.AIR << Block.DATA_BITS}, new int[]{0}, new int[]{Structure.VOID},
                Map.of(0, new CompoundTag().putCompound("block_entity_data", plain)),
                List.of(), new BlockVector3(0, 0, 0));
        CompoundTag nbt = StructureIO.toNbt(structure);
        nbt.getCompound("structure").getCompound("palette").getCompound("default")
                .getCompound("block_position_data").putCompound("0", plain);

        Structure loaded = StructureIO.fromNbt(nbt, palette);
        assertEquals("Chest",
                loaded.getBlockEntities().get(0).getCompound("block_entity_data").getString("id"));
    }

    @Test
    void fallsBackToClosestStateWhenPropertiesChanged() {
        CompoundTag legacyWire = new CompoundTag()
                .putString("name", "minecraft:redstone_wire")
                .putCompound("states", new CompoundTag().putInt("redstone_signal", 7));
        int legacyId = palette.getLegacyFullIdWithFallback(legacyWire);
        assertTrue(legacyId >= 0, "changed state sets must fall back to the closest state");
        CompoundTag resolved = palette.getBlockStateTag(legacyId);
        assertNotNull(resolved);
        assertEquals("minecraft:redstone_wire", resolved.getString("name"));
        assertEquals(7, resolved.getCompound("states").getInt("redstone_signal"));
    }

    @Test
    void writesVersionOneIntListLayers() {
        CompoundTag airTag = palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS);
        CompoundTag stoneTag = palette.getBlockStateTag(BlockID.STONE << Block.DATA_BITS);
        Structure structure = new Structure(2, 1, 1, List.of(airTag, stoneTag),
                new int[]{BlockID.AIR << Block.DATA_BITS, BlockID.STONE << Block.DATA_BITS},
                new int[]{0, 1}, new int[]{Structure.VOID, Structure.VOID},
                Map.of(), List.of(), new BlockVector3(0, 0, 0));

        CompoundTag nbt = StructureIO.toNbt(structure);
        assertEquals(1, nbt.getInt("format_version"),
                "v2 int-array layers are only readable by 1.26.50+ clients; v1 works everywhere");
        ListTag<? extends Tag> indices = nbt.getCompound("structure").getList("block_indices");
        assertEquals(2, indices.size(), "v1 always writes both layers");
        for (Tag layer : indices.getAll()) {
            assertTrue(layer instanceof ListTag<?> list && list.type == Tag.TAG_Int,
                    "v1 stores every layer as TAG_List<TAG_Int>");
        }
    }

    @Test
    void readsVersionOneLayersStoredAsIntLists() {
        CompoundTag airTag = palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS);
        CompoundTag stoneTag = palette.getBlockStateTag(BlockID.STONE << Block.DATA_BITS);
        Structure structure = new Structure(2, 1, 1, List.of(airTag, stoneTag),
                new int[]{BlockID.AIR << Block.DATA_BITS, BlockID.STONE << Block.DATA_BITS},
                new int[]{0, 1}, new int[]{Structure.VOID, Structure.VOID},
                Map.of(), List.of(), new BlockVector3(0, 0, 0));
        CompoundTag nbt = StructureIO.toNbt(structure);
        nbt.putInt("format_version", 1);

        ListTag<IntTag> firstLayer = new ListTag<>();
        firstLayer.add(new IntTag(0));
        firstLayer.add(new IntTag(1));
        ListTag<IntTag> secondLayer = new ListTag<>();
        secondLayer.add(new IntTag(Structure.VOID));
        secondLayer.add(new IntTag(Structure.VOID));
        nbt.getCompound("structure").putList("block_indices",
                new ListTag<ListTag<IntTag>>(Tag.TAG_List, List.of(firstLayer, secondLayer)));

        Structure loaded = StructureIO.fromNbt(nbt, palette);
        assertEquals(BlockID.AIR << Block.DATA_BITS, loaded.getLegacyId(loaded.getBlockIndex(0, 0)));
        assertEquals(BlockID.STONE << Block.DATA_BITS, loaded.getLegacyId(loaded.getBlockIndex(0, 1)));
        assertEquals(Structure.VOID, loaded.getBlockIndex(1, 0));
    }

    @Test
    void readsCompressedVersionTwoStructure() throws Exception {
        CompoundTag airTag = palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS);
        Structure structure = new Structure(1, 1, 1, List.of(airTag),
                new int[]{BlockID.AIR << Block.DATA_BITS}, new int[]{0}, new int[]{Structure.VOID},
                Map.of(), List.of(), new BlockVector3(0, 0, 0));
        CompoundTag nbt = StructureIO.toNbt(structure);
        // BDS 1.26.60+ 导出的是 v2：int 数组层 + structure 标签压缩
        // BDS 1.26.60+ exports v2: int-array layers with the structure tag compressed.
        nbt.getCompound("structure").putList("block_indices", new ListTag<IntArrayTag>()
                .add(new IntArrayTag(new int[]{0}))
                .add(new IntArrayTag(new int[]{Structure.VOID})));

        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflaterOutputStream stream = new DeflaterOutputStream(deflated)) {
            stream.write(NBTIO.write(nbt.getCompound("structure"), ByteOrder.LITTLE_ENDIAN, false));
        }
        CompoundTag compressed = new CompoundTag();
        compressed.putInt("format_version", 2);
        compressed.putList("size", nbt.getList("size", IntTag.class));
        compressed.putInt("compression", 1);
        compressed.putByteArray("structure", deflated.toByteArray());

        Structure loaded = StructureIO.fromNbt(compressed, palette);
        assertEquals(BlockID.AIR << Block.DATA_BITS, loaded.getLegacyId(loaded.getBlockIndex(0, 0)));
        assertEquals(Structure.VOID, loaded.getBlockIndex(1, 0));
    }

    @Test
    void rejectsInvalidPaletteIndexAndOversizedDimensions() {
        CompoundTag airTag = palette.getBlockStateTag(BlockID.AIR << Block.DATA_BITS);
        CompoundTag nbt = StructureIO.toNbt(new Structure(1, 1, 1, List.of(airTag),
                new int[]{BlockID.AIR << Block.DATA_BITS}, new int[]{0}, new int[]{Structure.VOID},
                Map.of(), List.of(), new BlockVector3(0, 0, 0)));
        ListTag<IntTag> outOfRange = new ListTag<>();
        outOfRange.add(new IntTag(5));
        nbt.getCompound("structure").putList("block_indices",
                new ListTag<ListTag<IntTag>>(Tag.TAG_List, List.of(outOfRange)));
        assertThrows(IllegalArgumentException.class, () -> StructureIO.fromNbt(nbt, palette));

        CompoundTag oversized = nbt.copy();
        oversized.putList("size", new ListTag<cn.nukkit.nbt.tag.IntTag>()
                .add(new cn.nukkit.nbt.tag.IntTag(StructureIO.MAX_SIZE_X + 1))
                .add(new cn.nukkit.nbt.tag.IntTag(1))
                .add(new cn.nukkit.nbt.tag.IntTag(1)));
        assertThrows(IllegalArgumentException.class, () -> StructureIO.fromNbt(oversized, palette));
    }

    @Test
    void rejectsUnsafeNames() {
        assertTrue(StructureAPI.isValidName("demo:house"));
        assertTrue(StructureAPI.isValidName("house"));
        assertFalse(StructureAPI.isValidName("../etc/passwd"));
        assertFalse(StructureAPI.isValidName("demo:../x"));
        assertFalse(StructureAPI.isValidName("/abs"));
        assertFalse(StructureAPI.isValidName(""));
        assertNull(StructureAPI.load("../etc/passwd"));
    }
}
