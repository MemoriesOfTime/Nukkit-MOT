package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockStairs;
import cn.nukkit.block.BlockThin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 带 1.26.50 连接/角落位的 meta 在旧协议调色板（无 connection/corner 变体）上查询时，
 * 必须降级剥离高位后按基础 meta 命中，保留楼梯朝向、染色颜色、栅栏木种，而不是跌落到 data=0。
 * <p>
 * Meta carrying 1.26.50 connection/corner bits must degrade to the base meta on older-protocol
 * palettes (without such variants), preserving stair orientation, pane color and fence wood instead
 * of collapsing to data=0.
 */
class BlockPaletteLegacyMetaFallbackTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void stairsKeepOrientationOnOlderPalettes() {
        for (GameVersion version : new GameVersion[]{GameVersion.V1_26_40, GameVersion.V1_26_30, GameVersion.V1_26_10}) {
            BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(version);
            for (int base = 0; base < 8; base++) {
                int expected = palette.getRuntimeId(53, base);
                for (int corner = 1; corner <= 4; corner++) {
                    int withCorner = base | (corner << BlockStairs.CORNER_SHIFT);
                    Assertions.assertEquals(expected, palette.getRuntimeId(53, withCorner),
                            version + " stairs base=" + base + " corner=" + corner);
                    Assertions.assertEquals(expected, palette.getRuntimeIdByFullId((53 << 13) | withCorner),
                            version + " stairs byFullId base=" + base);
                }
            }
        }
    }

    @Test
    void thinBlocksKeepBaseMetaOnOlderPalettes() {
        for (GameVersion version : new GameVersion[]{GameVersion.V1_26_40, GameVersion.V1_26_30, GameVersion.V1_26_10}) {
            BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(version);
            // 楼梯在 2193 调色板必须精确命中膨胀变体，不能走降级
            int exact = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50)
                    .getRuntimeId(53, 2 | (BlockStairs.CORNER_OUTER_LEFT << BlockStairs.CORNER_SHIFT));
            Assertions.assertNotEquals(GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50).getRuntimeId(53, 2), exact);

            // 染色玻璃板：颜色在低 4 位，连接位剥离后颜色保留
            for (int color = 1; color <= 15; color++) {
                int expected = palette.getRuntimeId(160, color);
                Assertions.assertNotEquals(-1, expected, version + " stained pane color=" + color);
                int withConnections = color | BlockThin.CONNECTION_FLAGS;
                Assertions.assertEquals(expected, palette.getRuntimeId(160, withConnections),
                        version + " stained pane color=" + color);
                Assertions.assertEquals(expected, palette.getHashId(160, withConnections) != -1
                        ? palette.getRuntimeId(160, color) : -1, expected, version + " hash path color=" + color);
            }

            // 栅栏木种同理
            for (int wood = 1; wood <= 5; wood++) {
                int expected = palette.getRuntimeId(85, wood);
                int withConnections = wood | (BlockThin.FLAG_CONNECTION_NORTH | BlockThin.FLAG_CONNECTION_EAST);
                Assertions.assertEquals(expected, palette.getRuntimeId(85, withConnections),
                        version + " fence wood=" + wood);
            }
        }
    }

    @Test
    void expandedPaletteStillResolvesExactVariants() {
        BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50);
        // 2193 上带位的 meta 精确命中膨胀变体：不同 corner 得到不同 runtimeId
        int none = palette.getRuntimeId(53, 2);
        int innerLeft = palette.getRuntimeId(53, 2 | (BlockStairs.CORNER_INNER_LEFT << BlockStairs.CORNER_SHIFT));
        int outerRight = palette.getRuntimeId(53, 2 | (BlockStairs.CORNER_OUTER_RIGHT << BlockStairs.CORNER_SHIFT));
        Assertions.assertNotEquals(none, innerLeft);
        Assertions.assertNotEquals(none, outerRight);
        Assertions.assertNotEquals(innerLeft, outerRight);

        // 连接位同理
        int isolated = palette.getRuntimeId(102, 0);
        int both = palette.getRuntimeId(102, BlockThin.FLAG_CONNECTION_NORTH | BlockThin.FLAG_CONNECTION_SOUTH);
        Assertions.assertNotEquals(isolated, both);

        // 栅栏：四个方向的连接变体必须存在，且木种+连接组合不得坍缩回橡木
        // fences: connection variants per direction must exist, and wood+connection
        // combos must not collapse back to oak
        int fenceIsolated = palette.getRuntimeId(85, 0);
        for (int i = 0; i < 4; i++) {
            int connected = palette.getRuntimeId(85, BlockFence.FLAG_CONNECTION_NORTH << i);
            Assertions.assertNotEquals(-1, connected, "fence direction " + i);
            Assertions.assertNotEquals(fenceIsolated, connected, "fence direction " + i + " must be a distinct variant");
        }
        int spruceNorth = palette.getRuntimeId(85, 1 | BlockFence.FLAG_CONNECTION_NORTH);
        Assertions.assertNotEquals(palette.getRuntimeId(85, BlockFence.FLAG_CONNECTION_NORTH), spruceNorth,
                "spruce+north must not collapse to oak+north");
        Assertions.assertNotEquals(palette.getRuntimeId(85, 1), spruceNorth,
                "spruce+north must not lose the connection variant");
    }

    @Test
    void legacyProtocolTablesMaskConnectionBits() {
        // <419 硬编码查询表：带连接/角落位的 meta 必须按字段宽掩码后命中基础 meta，
        // 不得把高位溢出进 id 位解析成邻块（曾实测玻璃板+0x10 解析成西瓜方块）
        // <p>
        // Pre-419 hardcoded tables: meta carrying connection/corner bits must be masked to the
        // field width and resolve the base meta, never bleeding into the id bits (a pane +0x10
        // once resolved to a melon block).
        for (int protocol : new int[]{223, 313, 361, 388, 407}) {
            int paneBase = GlobalBlockPalette.getOrCreateRuntimeId(protocol, 102, 0);
            Assertions.assertNotEquals(-1, paneBase, "p" + protocol + " glass pane base");
            for (int connections : new int[]{0x10, 0x20, 0x40, 0x80, 0xF0}) {
                Assertions.assertEquals(paneBase, GlobalBlockPalette.getOrCreateRuntimeId(protocol, 102, connections),
                        "p" + protocol + " pane connections=0x" + Integer.toHexString(connections));
            }

            int stairsBase = GlobalBlockPalette.getOrCreateRuntimeId(protocol, 53, 2);
            for (int corner = 1; corner <= 4; corner++) {
                Assertions.assertEquals(stairsBase,
                        GlobalBlockPalette.getOrCreateRuntimeId(protocol, 53, 2 | (corner << BlockStairs.CORNER_SHIFT)),
                        "p" + protocol + " stairs corner=" + corner);
            }

            int fenceBase = GlobalBlockPalette.getOrCreateRuntimeId(protocol, 85, 3);
            Assertions.assertEquals(fenceBase,
                    GlobalBlockPalette.getOrCreateRuntimeId(protocol, 85, 3 | BlockFence.CONNECTION_FLAGS),
                    "p" + protocol + " fence all connections");
        }
    }
}
