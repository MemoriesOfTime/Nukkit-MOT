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
