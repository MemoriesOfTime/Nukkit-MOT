package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockStairs;
import cn.nukkit.block.BlockThin;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * meta 连接/角落位与调色板状态字符串的方向对齐：corner 位值必须映射到对应 minecraft:corner
 * 枚举、连接位必须映射到对应方向的 connection_* 键且互不串扰——错位会导致 1.26.50 客户端
 * 全员旋转/镜像渲染。
 * <p>
 * Directional alignment between meta connection/corner bits and palette state strings: corner
 * bit values must map to the matching minecraft:corner enum and connection bits to their own
 * direction keys without crosstalk — a misalignment would rotate/mirror rendering for all
 * 1.26.50 clients.
 */
class BlockStateMetaAlignmentTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void cornerBitsMapToCornerEnum() {
        String[] cornerNames = {null, "inner_left", "inner_right", "outer_left", "outer_right"};
        for (int corner = 1; corner <= 4; corner++) {
            NbtMap states = BlockStateMapping.get()
                    .getState(53, 2 | (corner << BlockStairs.CORNER_SHIFT))
                    .getVanillaState().getCompound("states");
            Assertions.assertEquals(cornerNames[corner], states.get("minecraft:corner"), "corner " + corner);
            // 朝向与半层基础位不受 corner 影响 / base orientation and half are unaffected by corner bits
            Assertions.assertEquals(2, states.getInt("weirdo_direction"));
            Assertions.assertEquals(false, states.getBoolean("upside_down_bit"));
        }
    }

    @Test
    void connectionBitsMapToDirectionKeys() {
        String[] keys = {"minecraft:connection_north", "minecraft:connection_east",
                "minecraft:connection_south", "minecraft:connection_west"};
        for (int i = 0; i < 4; i++) {
            NbtMap states = BlockStateMapping.get().getState(102, 0x10 << i).getVanillaState().getCompound("states");
            Assertions.assertEquals((byte) 1, states.get(keys[i]), "bit 0x" + Integer.toHexString(0x10 << i));
            for (int j = 1; j < 4; j++) {
                Assertions.assertEquals((byte) 0, states.get(keys[(i + j) % 4]),
                        "bit 0x" + Integer.toHexString(0x10 << i) + " must not leak into " + keys[(i + j) % 4]);
            }
        }

        // 组合位：N+S 同时连接、E+W 保持 0 / combos set both directions, others stay 0
        NbtMap both = BlockStateMapping.get()
                .getState(102, BlockThin.FLAG_CONNECTION_NORTH | BlockThin.FLAG_CONNECTION_SOUTH)
                .getVanillaState().getCompound("states");
        Assertions.assertEquals((byte) 1, both.get("minecraft:connection_north"));
        Assertions.assertEquals((byte) 1, both.get("minecraft:connection_south"));
        Assertions.assertEquals((byte) 0, both.get("minecraft:connection_east"));
        Assertions.assertEquals((byte) 0, both.get("minecraft:connection_west"));
    }

    @Test
    void fenceConnectionBitsKeepWoodType() {
        // 栅栏：连接位在 meta 高 4 位，低 3 位木种必须保留 / fences carry connections in the
        // upper meta nibble; the wood type in the low bits must survive
        for (int i = 0; i < 4; i++) {
            cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot state = BlockStateMapping.get().getState(85, 0x10 << i);
            Assertions.assertEquals("minecraft:oak_fence", state.getVanillaState().getString("name"));
            Assertions.assertEquals((byte) 1, state.getVanillaState().getCompound("states")
                    .get("minecraft:connection_" + new String[]{"north", "east", "south", "west"}[i]),
                    "bit 0x" + Integer.toHexString(0x10 << i));
        }

        cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot spruce = BlockStateMapping.get().getState(85, 0x11);
        Assertions.assertEquals("minecraft:spruce_fence", spruce.getVanillaState().getString("name"));
        Assertions.assertEquals((byte) 1, spruce.getVanillaState().getCompound("states").get("minecraft:connection_north"));
    }
}
