package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

/**
 * 无专属资源版本的调色板/物品映射回退回归测试（网易与标准 1.21.130 均无专属 dat，借用标准 898/844 数据）。
 * <p>
 * Regression tests for palette/item-mapping fallback of versions without dedicated resources.
 */
class BlockPaletteNetEaseFallbackTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void netEase130BorrowsStandardPalette() {
        BlockPalette palette = Assertions.assertDoesNotThrow(() -> new BlockPalette(GameVersion.V1_21_130_NETEASE));
        BlockPalette palette844 = new BlockPalette(GameVersion.V1_21_111);

        int stoneRuntimeId = palette.getRuntimeId(BlockID.STONE, 0);
        Assertions.assertNotEquals(palette.getRuntimeId(BlockID.INFO_UPDATE, 0), stoneRuntimeId,
                "stone must resolve to a real state, not the INFO_UPDATE fallback");
        Assertions.assertEquals(palette844.getRuntimeId(BlockID.STONE, 0), stoneRuntimeId,
                "borrowed palette must match the standard 844 palette");
        Assertions.assertEquals(palette844.getHashId(BlockID.STONE, 0), palette.getHashId(BlockID.STONE, 0),
                "hash ids are state-derived and must be identical");

        // production routing: NetEase 898 shares the palette instance with standard clients
        // (threshold map points at V1_21_111)
        Assertions.assertSame(GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_21_130),
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_21_130_NETEASE));
    }

    @Test
    void standard130FallsBackTo111Palette() {
        BlockPalette palette = Assertions.assertDoesNotThrow(() -> new BlockPalette(GameVersion.V1_21_130));
        BlockPalette palette844 = new BlockPalette(GameVersion.V1_21_111);

        Assertions.assertEquals(palette844.getRuntimeId(BlockID.STONE, 0), palette.getRuntimeId(BlockID.STONE, 0));
    }

    @Test
    void netEase130ItemMappingReusesStandard898() {
        RuntimeItemMapping mapping = RuntimeItems.getMapping(GameVersion.V1_21_130_NETEASE);

        Assertions.assertEquals(GameVersion.V1_21_130.getProtocol(), mapping.getProtocolId(),
                "898 NetEase must ride the standard 898 mapping until netease_898 data lands");
        OptionalInt rawCopper = mapping.getNetworkIdByNamespaceId("minecraft:raw_copper");
        Assertions.assertTrue(rawCopper.isPresent());
        Assertions.assertEquals(547, rawCopper.getAsInt(), "standard 898 item id");
        Assertions.assertTrue(mapping.getNetworkIdByNamespaceId("minecraft:diamond_spear").isPresent(),
                "1.21.130 spears must resolve for NetEase clients via the standard table");
    }
}
