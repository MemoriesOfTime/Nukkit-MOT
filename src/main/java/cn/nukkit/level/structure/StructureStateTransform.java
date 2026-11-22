package cn.nukkit.level.structure;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.NumberTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;

import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * 结构加载时的方块状态旋转/镜像，语义对齐原版（JE {@code BlockState.mirror().rotate()}）。
 * <p>
 * Rotates and mirrors block states when a structure is loaded, matching vanilla
 * ({@code BlockState.mirror(mirror).rotate(rotation)}): mirror first, then rotation.
 * Rotation is clockwise for {@code ROTATE_90} and state tag types are preserved.
 */
final class StructureStateTransform {

    private static final int CLOCKWISE = 0;
    private static final int MIRROR_X = 1;
    private static final int MIRROR_Z = 2;
    private static final String CARDINAL = "minecraft:cardinal_direction";
    private static final String FACING = "minecraft:facing_direction";
    private static final String BLOCK_FACE = "minecraft:block_face";
    private static final String LEVER = "lever_direction";
    private static final String CORNER = "minecraft:corner";
    private static final int[] RAIL_CW = {1, 0, 5, 4, 2, 3, 7, 8, 9, 6};
    private static final int[] RAIL_MIRROR_X = {0, 1, 3, 2, 4, 5, 7, 6, 9, 8};
    private static final int[] RAIL_MIRROR_Z = {0, 1, 2, 3, 5, 4, 9, 8, 7, 6};
    private static final int[] WEIRDO_CW = {2, 3, 1, 0};
    private static final int[] WEIRDO_MIRROR_X = {1, 0, 2, 3};
    private static final int[] WEIRDO_MIRROR_Z = {0, 1, 3, 2};
    private static final int[] TRAPDOOR_CW = {2, 3, 1, 0};
    private static final int[] TRAPDOOR_MIRROR_X = {1, 0, 2, 3};
    private static final int[] TRAPDOOR_MIRROR_Z = {0, 1, 3, 2};
    private static final int[] DIRECTION_MIRROR_X = {0, 3, 2, 1};
    private static final int[] DIRECTION_MIRROR_Z = {2, 1, 0, 3};
    private static final int[] FACING_CW = {0, 1, 5, 4, 2, 3};
    private static final int[] FACING_MIRROR_X = {0, 1, 2, 3, 5, 4};
    private static final int[] FACING_MIRROR_Z = {0, 1, 3, 2, 4, 5};
    private static final Map<String, String> FACE_CW = Map.of(
            "north", "east", "east", "south", "south", "west", "west", "north");
    private static final Map<String, String> FACE_MIRROR_X = Map.of("east", "west", "west", "east");
    private static final Map<String, String> FACE_MIRROR_Z = Map.of("north", "south", "south", "north");
    private static final Map<String, String> AXIS_CW = Map.of("x", "z", "z", "x");
    private static final Map<String, String> LEVER_CW = Map.ofEntries(
            Map.entry("east", "south"), Map.entry("south", "west"),
            Map.entry("west", "north"), Map.entry("north", "east"),
            Map.entry("down_east_west", "down_north_south"), Map.entry("down_north_south", "down_east_west"),
            Map.entry("up_east_west", "up_north_south"), Map.entry("up_north_south", "up_east_west"));
    /**
     * JE 的楼梯镜像不对称：沿 X 镜像只翻 outer，沿 Z 镜像翻全部左右形状。
     * <p>
     * JE's stair mirroring is asymmetric: the X mirror only swaps outer shapes, the
     * Z mirror swaps every left/right shape.
     */
    private static final Map<String, String> CORNER_MIRROR_X = Map.of(
            "outer_left", "outer_right", "outer_right", "outer_left");
    private static final Map<String, String> CORNER_MIRROR_Z = Map.of(
            "inner_left", "inner_right", "inner_right", "inner_left",
            "outer_left", "outer_right", "outer_right", "outer_left");

    private StructureStateTransform() {
    }

    /**
     * 返回状态已变换的调色板条目副本；无需变换时返回 {@code null}。
     * <p>
     * Returns a copy of the palette entry with transformed states, or {@code null}
     * when nothing can be transformed.
     */
    static CompoundTag transform(CompoundTag entry, int rotation, int mirror) {
        int rot = rotation & 3;
        int mir = mirror & 3;
        if (rot == 0 && mir == 0) {
            return null;
        }
        if (!entry.containsCompound("states")) {
            return null;
        }
        CompoundTag result = entry.copy();
        CompoundTag states = result.getCompound("states");
        if (states.isEmpty()) {
            return null;
        }
        switch (mir) {
            case 1 -> mirrorX(states);
            case 2 -> mirrorZ(states);
            case 3 -> {
                rotate90(states);
                rotate90(states);
            }
            default -> {
            }
        }
        for (int i = 0; i < rot; i++) {
            rotate90(states);
        }
        return result;
    }

    private static void rotate90(CompoundTag states) {
        rotateString(states, "torch_facing_direction", FACE_CW);
        rotateIntTable(states, "rail_direction", RAIL_CW);
        rotateIntTable(states, "weirdo_direction", WEIRDO_CW);
        transformDirection(states, CLOCKWISE, TRAPDOOR_CW, value -> (value + 1) & 3);
        rotateInt(states, "ground_sign_direction", value -> (value + 4) & 15);
        rotateFacingDirection(states, FACING_CW);
        rotateString(states, CARDINAL, FACE_CW);
        rotateString(states, FACING, FACE_CW);
        rotateString(states, BLOCK_FACE, FACE_CW);
        rotateString(states, "pillar_axis", AXIS_CW);
        rotateString(states, "portal_axis", AXIS_CW);
        rotateString(states, LEVER, LEVER_CW);
        rotateInt(states, "vine_direction_bits", value -> ((value << 1) | (value >>> 3)) & 0xf);
        rotateInt(states, "multi_face_direction_bits", StructureStateTransform::rotateMultiFace);
        transformConnections(states, CLOCKWISE);
        transformWallConnections(states, CLOCKWISE);
        rotateInt(states, "rotation", value -> (value + 1) & 3);
    }

    private static void mirrorX(CompoundTag states) {
        rotateString(states, "torch_facing_direction", FACE_MIRROR_X);
        rotateIntTable(states, "rail_direction", RAIL_MIRROR_X);
        mirrorWeirdo(states, WEIRDO_MIRROR_X, true);
        transformDirection(states, MIRROR_X, TRAPDOOR_MIRROR_X, null);
        rotateInt(states, "ground_sign_direction", value -> (16 - value) & 15);
        rotateFacingDirection(states, FACING_MIRROR_X);
        rotateString(states, CARDINAL, FACE_MIRROR_X);
        rotateString(states, FACING, FACE_MIRROR_X);
        rotateString(states, BLOCK_FACE, FACE_MIRROR_X);
        rotateString(states, LEVER, FACE_MIRROR_X);
        rotateInt(states, "vine_direction_bits", value -> (value & 0x5) | ((value & 0x2) << 2) | ((value & 0x8) >> 2));
        rotateInt(states, "multi_face_direction_bits",
                value -> (value & 0x0f) | ((value & 0x10) << 1) | ((value & 0x20) >> 1));
        transformConnections(states, MIRROR_X);
        transformWallConnections(states, MIRROR_X);
        flipHinge(states);
        rotateInt(states, "rotation", value -> (4 - (value & 3)) & 3);
    }

    private static void mirrorZ(CompoundTag states) {
        rotateString(states, "torch_facing_direction", FACE_MIRROR_Z);
        rotateIntTable(states, "rail_direction", RAIL_MIRROR_Z);
        mirrorWeirdo(states, WEIRDO_MIRROR_Z, false);
        transformDirection(states, MIRROR_Z, TRAPDOOR_MIRROR_Z, null);
        rotateInt(states, "ground_sign_direction", value -> (8 - value) & 15);
        rotateFacingDirection(states, FACING_MIRROR_Z);
        rotateString(states, CARDINAL, FACE_MIRROR_Z);
        rotateString(states, FACING, FACE_MIRROR_Z);
        rotateString(states, BLOCK_FACE, FACE_MIRROR_Z);
        rotateString(states, LEVER, FACE_MIRROR_Z);
        rotateInt(states, "vine_direction_bits", value -> (value & 0xa) | ((value & 0x1) << 2) | ((value & 0x4) >> 2));
        rotateInt(states, "multi_face_direction_bits",
                value -> (value & 0x33) | ((value & 0x4) << 1) | ((value & 0x8) >> 1));
        transformConnections(states, MIRROR_Z);
        transformWallConnections(states, MIRROR_Z);
        flipHinge(states);
        rotateInt(states, "rotation", value -> (2 - (value & 3)) & 3);
    }

    /**
     * trapdoor 的 {@code direction} 是 E/W/S/N 序，其余方块是水平索引（S/W/N/E）序。
     * <p>
     * Trapdoor {@code direction} uses the E/W/S/N order; other blocks use the
     * horizontal index order (S/W/N/E).
     */
    private static void transformDirection(CompoundTag states, int mode, int[] trapdoorTable,
                                           IntUnaryOperator horizontalOperation) {
        Tag tag = states.get("direction");
        if (!(tag instanceof NumberTag<?> number)) {
            return;
        }
        int value = number.getData().intValue() & 3;
        boolean trapdoor = states.contains("upside_down_bit") && states.contains("open_bit");
        int result;
        if (trapdoor) {
            result = trapdoorTable[value];
        } else if (horizontalOperation != null) {
            result = horizontalOperation.applyAsInt(value);
        } else {
            result = mode == MIRROR_X ? DIRECTION_MIRROR_X[value] : DIRECTION_MIRROR_Z[value];
        }
        putNumber(states, "direction", tag.getId(), result);
    }

    private static void mirrorWeirdo(CompoundTag states, int[] table, boolean axisX) {
        Tag tag = states.get("weirdo_direction");
        if (!(tag instanceof NumberTag<?> number)) {
            return;
        }
        int value = number.getData().intValue();
        if (value >= 0 && value < table.length) {
            putNumber(states, "weirdo_direction", tag.getId(), table[value]);
        }
        boolean facingAxisMatches = axisX ? value <= 1 : value >= 2;
        if (facingAxisMatches) {
            rotateString(states, CORNER, axisX ? CORNER_MIRROR_X : CORNER_MIRROR_Z);
        }
    }

    private static void rotateFacingDirection(CompoundTag states, int[] table) {
        Tag tag = states.get("facing_direction");
        if (!(tag instanceof NumberTag<?> number)) {
            return;
        }
        int value = number.getData().intValue();
        int face = value & 0x7;
        int extra = value & ~0x7;
        putNumber(states, "facing_direction", tag.getId(), (face < 6 ? table[face] : face) | extra);
    }

    private static void transformConnections(CompoundTag states, int mode) {
        if (!states.contains("minecraft:connection_north")) {
            return;
        }
        int north = byteValue(states, "minecraft:connection_north");
        int east = byteValue(states, "minecraft:connection_east");
        int south = byteValue(states, "minecraft:connection_south");
        int west = byteValue(states, "minecraft:connection_west");
        if (mode == CLOCKWISE) {
            states.putByte("minecraft:connection_north", west);
            states.putByte("minecraft:connection_east", north);
            states.putByte("minecraft:connection_south", east);
            states.putByte("minecraft:connection_west", south);
        } else if (mode == MIRROR_X) {
            states.putByte("minecraft:connection_east", west);
            states.putByte("minecraft:connection_west", east);
        } else {
            states.putByte("minecraft:connection_north", south);
            states.putByte("minecraft:connection_south", north);
        }
    }

    private static void transformWallConnections(CompoundTag states, int mode) {
        if (!states.contains("wall_connection_type_north")) {
            return;
        }
        String north = stringValue(states, "wall_connection_type_north");
        String east = stringValue(states, "wall_connection_type_east");
        String south = stringValue(states, "wall_connection_type_south");
        String west = stringValue(states, "wall_connection_type_west");
        if (mode == CLOCKWISE) {
            states.putString("wall_connection_type_north", west);
            states.putString("wall_connection_type_east", north);
            states.putString("wall_connection_type_south", east);
            states.putString("wall_connection_type_west", south);
        } else if (mode == MIRROR_X) {
            states.putString("wall_connection_type_east", west);
            states.putString("wall_connection_type_west", east);
        } else {
            states.putString("wall_connection_type_north", south);
            states.putString("wall_connection_type_south", north);
        }
    }

    /**
     * {@code multi_face_direction_bits} 位序：0=down 1=up 2=north 3=south 4=west 5=east。
     * <p>
     * Bit order of {@code multi_face_direction_bits}: down, up, north, south, west, east.
     */
    private static int rotateMultiFace(int value) {
        int result = value & 0x3;
        result |= (value & 1 << 2) != 0 ? 1 << 5 : 0;
        result |= (value & 1 << 5) != 0 ? 1 << 3 : 0;
        result |= (value & 1 << 3) != 0 ? 1 << 4 : 0;
        result |= (value & 1 << 4) != 0 ? 1 << 2 : 0;
        return result;
    }

    private static void flipHinge(CompoundTag states) {
        Tag tag = states.get("door_hinge_bit");
        if (tag instanceof NumberTag<?> number) {
            putNumber(states, "door_hinge_bit", tag.getId(), number.getData().intValue() == 0 ? 1 : 0);
        }
    }

    private static void rotateInt(CompoundTag states, String key, IntUnaryOperator op) {
        Tag tag = states.get(key);
        if (!(tag instanceof NumberTag<?> number)) {
            return;
        }
        putNumber(states, key, tag.getId(), op.applyAsInt(number.getData().intValue()));
    }

    private static void rotateString(CompoundTag states, String key, Map<String, String> mapping) {
        Tag tag = states.get(key);
        if (!(tag instanceof StringTag stringTag)) {
            return;
        }
        String mapped = mapping.get(stringTag.data);
        if (mapped != null) {
            states.putString(key, mapped);
        }
    }

    private static void rotateIntTable(CompoundTag states, String key, int[] table) {
        Tag tag = states.get(key);
        if (!(tag instanceof NumberTag<?> number)) {
            return;
        }
        int value = number.getData().intValue();
        if (value >= 0 && value < table.length) {
            putNumber(states, key, tag.getId(), table[value]);
        }
    }

    private static void putNumber(CompoundTag states, String key, byte type, int value) {
        if (type == Tag.TAG_Byte) {
            states.putByte(key, value);
        } else {
            states.putInt(key, value);
        }
    }

    private static int byteValue(CompoundTag states, String key) {
        Tag tag = states.get(key);
        return tag instanceof NumberTag<?> number ? number.getData().intValue() : 0;
    }

    private static String stringValue(CompoundTag states, String key) {
        Tag tag = states.get(key);
        return tag instanceof StringTag stringTag ? stringTag.data : "none";
    }
}
