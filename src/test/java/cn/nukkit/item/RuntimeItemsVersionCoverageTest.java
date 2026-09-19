package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 RuntimeItems 的版本覆盖完整性。
 * <p>
 * 当添加新的协议版本时，需要在 RuntimeItems 的映射选择链和 values() 中加入对应锚点。
 * 此测试确保所有可达映射都包含在 values() 中，防止遗漏。
 */
public class RuntimeItemsVersionCoverageTest {

    @BeforeAll
    static void setup() {
        MockServer.init();
    }

    /**
     * 验证 getMapping 对每个 GameVersion 返回的映射都包含在 values() 数组中。
     * 如果在映射选择链中添加了新锚点但忘记加入 values()，此测试会失败。
     */
    @Test
    public void testValuesCoverAllAnchors() {
        RuntimeItemMapping[] values = RuntimeItems.values();
        assertTrue(values.length > 0, "RuntimeItems.values() should not be empty");

        Set<RuntimeItemMapping> valueSet = new HashSet<>(Arrays.asList(values));
        List<String> missing = new ArrayList<>();

        for (GameVersion version : GameVersion.getValues()) {
            RuntimeItemMapping mapping = RuntimeItems.getMapping(version);
            if (mapping == null || !valueSet.contains(mapping)) {
                missing.add(version.toString());
            }
        }

        assertTrue(missing.isEmpty(),
                "以下 GameVersion 的映射未包含在 RuntimeItems.values() 中: " + missing +
                "\n请将对应锚点添加到 RuntimeItems.values()");
    }

    /**
     * 验证 GameVersion.getLastVersion() 对应的映射存在且可正确获取。
     */
    @Test
    public void testLastVersionMappingExists() {
        GameVersion lastVersion = GameVersion.getLastVersion();
        RuntimeItemMapping mapping = RuntimeItems.getMapping(lastVersion);
        assertNotNull(mapping,
                "RuntimeItems.getMapping() 应该为最新版本 " + lastVersion + " 返回有效映射");
    }

    /**
     * 验证所有需要独立 RuntimeItemMapping 的 GameVersion 都能获取到映射。
     */
    @Test
    public void testAllGameVersionsMapped() {
        List<String> unmapped = new ArrayList<>();
        for (GameVersion version : GameVersion.getValues()) {
            try {
                RuntimeItemMapping mapping = RuntimeItems.getMapping(version);
                if (mapping == null) {
                    unmapped.add(version.toString());
                }
            } catch (Exception e) {
                unmapped.add(version + " (exception: " + e.getMessage() + ")");
            }
        }
        assertTrue(unmapped.isEmpty(),
                "以下 GameVersion 无法获取 RuntimeItemMapping: " + unmapped);
    }

    /**
     * 验证 values() 数组中没有 null 或重复元素。
     */
    @Test
    public void testNoNullOrDuplicateInValues() {
        RuntimeItemMapping[] values = RuntimeItems.values();
        Set<RuntimeItemMapping> seen = new HashSet<>();
        for (int i = 0; i < values.length; i++) {
            assertNotNull(values[i], "RuntimeItems.values()[" + i + "] should not be null");
            assertTrue(seen.add(values[i]), "RuntimeItems.values()[" + i + "] is a duplicate");
        }
    }
}
