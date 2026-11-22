package cn.nukkit.level.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.nbt.tag.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方块状态旋转/镜像语义（顺时针、先镜像后旋转、类型保真）。
 * <p>
 * Block state rotation/mirror semantics: clockwise rotation, mirror-then-rotate order
 * and NBT type preservation.
 */
class StructureStateTransformTest {

    private static BlockPalette palette;

    @BeforeAll
    static void setUp() {
        palette = new BlockPalette(GameVersion.V1_26_60);
    }

    private static CompoundTag entry(String name, CompoundTag states) {
        return new CompoundTag()
                .putString("name", name)
                .putCompound("states", states)
                .putInt("version", 1);
    }

    private static CompoundTag states(CompoundTag entry) {
        return entry.getCompound("states");
    }

    @Test
    void stairsRotateClockwiseAndKeepCorner() {
        CompoundTag source = entry("minecraft:oak_stairs", new CompoundTag()
                .putInt("weirdo_direction", 0)
                .putByte("upside_down_bit", 0)
                .putString("minecraft:corner", "inner_left"));
        CompoundTag rotated = StructureStateTransform.transform(source, 1, 0);
        assertNotNull(rotated);
        assertEquals(2, states(rotated).getInt("weirdo_direction"));
        assertEquals("inner_left", states(rotated).getString("minecraft:corner"));
        assertEquals(Tag.TAG_Byte, states(rotated).get("upside_down_bit").getId());
    }

    @Test
    void stairsMirrorUsesVanillaAsymmetry() {
        CompoundTag east = entry("minecraft:oak_stairs", new CompoundTag()
                .putInt("weirdo_direction", 0)
                .putString("minecraft:corner", "inner_left"));
        CompoundTag mirroredEast = StructureStateTransform.transform(east, 0, 1);
        assertEquals(1, states(mirroredEast).getInt("weirdo_direction"));
        assertEquals("inner_left", states(mirroredEast).getString("minecraft:corner"),
                "JE's X mirror keeps inner stair corners");

        CompoundTag eastOuter = entry("minecraft:oak_stairs", new CompoundTag()
                .putInt("weirdo_direction", 0)
                .putString("minecraft:corner", "outer_left"));
        assertEquals("outer_right",
                states(StructureStateTransform.transform(eastOuter, 0, 1)).getString("minecraft:corner"));

        CompoundTag north = entry("minecraft:oak_stairs", new CompoundTag()
                .putInt("weirdo_direction", 3)
                .putString("minecraft:corner", "inner_left"));
        CompoundTag mirroredNorth = StructureStateTransform.transform(north, 0, 1);
        assertEquals(3, states(mirroredNorth).getInt("weirdo_direction"));
        assertEquals("inner_left", states(mirroredNorth).getString("minecraft:corner"));

        CompoundTag northZ = entry("minecraft:oak_stairs", new CompoundTag()
                .putInt("weirdo_direction", 3)
                .putString("minecraft:corner", "inner_left"));
        CompoundTag mirroredNorthZ = StructureStateTransform.transform(northZ, 0, 2);
        assertEquals(2, states(mirroredNorthZ).getInt("weirdo_direction"));
        assertEquals("inner_right", states(mirroredNorthZ).getString("minecraft:corner"),
                "JE's Z mirror swaps all left/right stair corners");
    }

    @Test
    void doorsMirrorFlipsCardinalAndHinge() {
        CompoundTag source = entry("minecraft:oak_door", new CompoundTag()
                .putString("minecraft:cardinal_direction", "east")
                .putByte("door_hinge_bit", 0)
                .putByte("open_bit", 0)
                .putByte("upper_block_bit", 0));
        CompoundTag mirrored = StructureStateTransform.transform(source, 0, 1);
        assertEquals("west", states(mirrored).getString("minecraft:cardinal_direction"));
        assertEquals(1, states(mirrored).getByte("door_hinge_bit"));

        CompoundTag rotated = StructureStateTransform.transform(source, 1, 0);
        assertEquals("south", states(rotated).getString("minecraft:cardinal_direction"));
        assertEquals(0, states(rotated).getByte("door_hinge_bit"));
    }

    @Test
    void trapdoorDirectionUsesEwsnOrder() {
        CompoundTag source = entry("minecraft:oak_trapdoor", new CompoundTag()
                .putInt("direction", 0)
                .putByte("upside_down_bit", 0)
                .putByte("open_bit", 0));
        assertEquals(2, states(StructureStateTransform.transform(source, 1, 0)).getInt("direction"));
        assertEquals(1, states(StructureStateTransform.transform(source, 0, 1)).getInt("direction"));
    }

    @Test
    void torchAndPillarFollowHorizontalRotation() {
        CompoundTag source = entry("minecraft:torch", new CompoundTag()
                .putString("torch_facing_direction", "north")
                .putString("pillar_axis", "x"));
        CompoundTag rotated = StructureStateTransform.transform(source, 1, 0);
        assertEquals("east", states(rotated).getString("torch_facing_direction"));
        assertEquals("z", states(rotated).getString("pillar_axis"));
        CompoundTag mirrored = StructureStateTransform.transform(source, 0, 1);
        assertEquals("north", states(mirrored).getString("torch_facing_direction"));
        assertEquals("x", states(mirrored).getString("pillar_axis"));
    }

    @Test
    void connectionsRotateAndMirror() {
        CompoundTag fence = new CompoundTag()
                .putByte("minecraft:connection_north", 1)
                .putByte("minecraft:connection_east", 0)
                .putByte("minecraft:connection_south", 0)
                .putByte("minecraft:connection_west", 0);
        CompoundTag rotated = StructureStateTransform.transform(entry("minecraft:oak_fence", fence), 1, 0);
        assertEquals(1, states(rotated).getByte("minecraft:connection_east"));
        assertEquals(0, states(rotated).getByte("minecraft:connection_north"));

        CompoundTag wall = new CompoundTag()
                .putString("wall_connection_type_north", "tall")
                .putString("wall_connection_type_east", "none")
                .putString("wall_connection_type_south", "none")
                .putString("wall_connection_type_west", "none");
        CompoundTag mirrored = StructureStateTransform.transform(entry("minecraft:cobblestone_wall", wall), 0, 2);
        assertEquals("tall", states(mirrored).getString("wall_connection_type_south"));
        assertEquals("none", states(mirrored).getString("wall_connection_type_north"));
    }

    @Test
    void returnsNullWhenNothingToDo() {
        CompoundTag source = entry("minecraft:stone", new CompoundTag());
        assertNull(StructureStateTransform.transform(source, 0, 0));
        assertNull(StructureStateTransform.transform(source, 1, 0));
    }

    @Test
    void transformedStateExistsInThePalette() {
        CompoundTag stairs = palette.getBlockStateTag(BlockID.WOOD_STAIRS << Block.DATA_BITS);
        assertNotNull(stairs);
        assertEquals(0, stairs.getCompound("states").getInt("weirdo_direction"));
        CompoundTag rotated = StructureStateTransform.transform(stairs, 1, 0);
        assertNotNull(rotated);
        assertTrue(states(rotated).get("minecraft:corner") instanceof StringTag);
        assertEquals(2, states(rotated).getInt("weirdo_direction"));
        int legacyId = palette.getLegacyFullId(rotated);
        assertTrue(legacyId >= 0, "rotated state must exist in the server palette");
        assertEquals(2, legacyId & 0x03);
        assertTrue(legacyId != (BlockID.WOOD_STAIRS << Block.DATA_BITS));
    }

    @Test
    void typesArePreserved() {
        CompoundTag source = entry("minecraft:lever", new CompoundTag()
                .putString("lever_direction", "east")
                .putByte("open_bit", 0));
        CompoundTag rotated = StructureStateTransform.transform(source, 1, 0);
        assertTrue(states(rotated).get("lever_direction") instanceof StringTag);
        assertTrue(states(rotated).get("open_bit") instanceof ByteTag);
        CompoundTag weirdo = entry("minecraft:oak_stairs", new CompoundTag().putInt("weirdo_direction", 0));
        assertTrue(states(StructureStateTransform.transform(weirdo, 1, 0)).get("weirdo_direction") instanceof IntTag);
    }
}
