package cn.nukkit.level;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeBlockStatesNewBlocksTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void testRuntimeBlockStatesContainNewerVanillaBlocks() throws IOException {
        try (var files = Files.list(RESOURCES)) {
            for (Path file : files.filter(RuntimeBlockStatesNewBlocksTest::isRuntimeBlockStatesFile).toList()) {
                String fileName = file.getFileName().toString();
                boolean netease = fileName.startsWith("runtime_block_states_netease_");
                int version = parseVersion(fileName);

                Map<String, Integer> expected = expectedCounts(version, netease);
                if (expected.isEmpty()) {
                    continue;
                }

                Map<String, Integer> actual = countBlockStates(file);
                for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                    assertEquals(entry.getValue(), actual.getOrDefault(entry.getKey(), 0), fileName + " " + entry.getKey());
                }
            }
        }
    }

    private static boolean isRuntimeBlockStatesFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("runtime_block_states_") && fileName.endsWith(".dat");
    }

    private static int parseVersion(String fileName) {
        return Integer.parseInt(fileName
                .replace("runtime_block_states_netease_", "")
                .replace("runtime_block_states_", "")
                .replace(".dat", ""));
    }

    private static Map<String, Integer> expectedCounts(int version, boolean netease) {
        Map<String, Integer> expected = new HashMap<>();
        if (netease || version >= 575) {
            expected.put("minecraft:torchflower_crop", 8);
            expected.put("minecraft:torchflower", 1);
        }
        if (netease || version >= 589) {
            expected.put("minecraft:pitcher_crop", 16);
            expected.put("minecraft:pitcher_plant", 2);
        }
        if (netease || version >= 560) {
            expected.put("minecraft:bamboo_door", 32);
        }
        if (netease || version >= 567) {
            expected.put("minecraft:chiseled_bookshelf", 256);
        } else if (version == 560) {
            expected.put("minecraft:chiseled_bookshelf", 196);
        }
        if (netease || version >= 630) {
            expected.put("minecraft:crafter", 48);
        }
        if (!netease && version >= 844) {
            expected.put("minecraft:copper_lantern", 2);
            expected.put("minecraft:exposed_copper_lantern", 2);
            expected.put("minecraft:weathered_copper_lantern", 2);
            expected.put("minecraft:oxidized_copper_lantern", 2);
            expected.put("minecraft:waxed_copper_lantern", 2);
            expected.put("minecraft:waxed_exposed_copper_lantern", 2);
            expected.put("minecraft:waxed_weathered_copper_lantern", 2);
            expected.put("minecraft:waxed_oxidized_copper_lantern", 2);
            expected.put("minecraft:lightning_rod", 12);
            expected.put("minecraft:exposed_lightning_rod", 12);
            expected.put("minecraft:weathered_lightning_rod", 12);
            expected.put("minecraft:oxidized_lightning_rod", 12);
            expected.put("minecraft:waxed_lightning_rod", 12);
            expected.put("minecraft:waxed_exposed_lightning_rod", 12);
            expected.put("minecraft:waxed_weathered_lightning_rod", 12);
            expected.put("minecraft:waxed_oxidized_lightning_rod", 12);
        }
        if (!netease && version >= 2168) {
            // 1.26.40 白杨系列；楼梯/栅栏按版本差异补高位 meta 别名（40 / 16 条）
            expected.put("minecraft:poplar_button", 12);
            expected.put("minecraft:poplar_door", 32);
            expected.put("minecraft:poplar_fence", 16);
            expected.put("minecraft:poplar_fence_gate", 16);
            expected.put("minecraft:poplar_hanging_sign", 384);
            expected.put("minecraft:stripped_poplar_log", 3);
            expected.put("minecraft:poplar_log", 3);
            expected.put("minecraft:poplar_planks", 1);
            expected.put("minecraft:poplar_pressure_plate", 16);
            expected.put("minecraft:poplar_slab", 2);
            expected.put("minecraft:poplar_double_slab", 2);
            expected.put("minecraft:poplar_stairs", 40);
            expected.put("minecraft:poplar_standing_sign", 16);
            expected.put("minecraft:poplar_trapdoor", 16);
            expected.put("minecraft:poplar_wall_sign", 6);
            expected.put("minecraft:stripped_poplar_wood", 3);
            expected.put("minecraft:poplar_wood", 3);
            expected.put("minecraft:poplar_sapling", 2);
            expected.put("minecraft:orange_poplar_leaves", 4);
            expected.put("minecraft:red_poplar_leaves", 4);
            expected.put("minecraft:yellow_poplar_leaves", 4);
            expected.put("minecraft:poplar_shelf", 32);
        }
        if (!netease && version >= 2193) {
            // 1.26.50 羊毛台阶 / 楼梯 / 双台阶（16 色）
            String[] coloredFamilies = {
                    "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
                    "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"
            };
            for (String color : coloredFamilies) {
                expected.put("minecraft:" + color + "_wool_slab", 2);
                expected.put("minecraft:" + color + "_wool_double_slab", 2);
                expected.put("minecraft:" + color + "_wool_stairs", 40);
                expected.put("minecraft:" + color + "_concrete_slab", 2);
                expected.put("minecraft:" + color + "_concrete_double_slab", 2);
                expected.put("minecraft:" + color + "_concrete_stairs", 40);
            }
        }
        return expected;
    }

    private static Map<String, Integer> countBlockStates(Path file) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        for (CompoundTag tag : readBlockStates(file).getAll()) {
            counts.merge(tag.getString("name"), 1, Integer::sum);
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private static ListTag<CompoundTag> readBlockStates(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file);
             GZIPInputStream gzipInputStream = new GZIPInputStream(stream);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(gzipInputStream)) {
            return (ListTag<CompoundTag>) NBTIO.readTag(bufferedInputStream, ByteOrder.BIG_ENDIAN, false);
        }
    }
}
