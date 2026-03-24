package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 RuntimeItems 的版本覆盖完整性。
 * <p>
 * 当添加新的协议版本时，需要在 RuntimeItems 中添加对应的映射字段并加入 VALUES 数组。
 * 此测试确保所有 RuntimeItemMapping 字段都被包含在 VALUES 中，防止遗漏。
 */
public class RuntimeItemsVersionCoverageTest {

    @BeforeAll
    static void setup() {
        MockServer.init();
    }

    /**
     * 验证 RuntimeItems 中所有 RuntimeItemMapping 类型的静态字段都被包含在 VALUES 数组中。
     * 如果添加了新的 mapping 字段但忘记加入 VALUES，此测试会失败。
     */
    @Test
    public void testAllMappingFieldsInValues() throws Exception {
        RuntimeItemMapping[] values = RuntimeItems.VALUES;
        assertNotNull(values, "RuntimeItems.VALUES should not be null");
        assertTrue(values.length > 0, "RuntimeItems.VALUES should not be empty");

        List<String> missingFields = new ArrayList<>();

        for (Field field : RuntimeItems.class.getDeclaredFields()) {
            if (field.getType() == RuntimeItemMapping.class) {
                field.setAccessible(true);
                RuntimeItemMapping mapping = (RuntimeItemMapping) field.get(null);
                if (mapping == null) {
                    continue;
                }
                boolean found = false;
                for (RuntimeItemMapping value : values) {
                    if (value == mapping) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    missingFields.add(field.getName());
                }
            }
        }

        assertTrue(missingFields.isEmpty(),
                "以下 RuntimeItemMapping 字段未包含在 RuntimeItems.VALUES 中: " + missingFields +
                "\n请将它们添加到 RuntimeItems.init() 的 VALUES 数组中");
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
     * 验证 VALUES 数组中没有 null 元素。
     */
    @Test
    public void testNoNullInValues() {
        RuntimeItemMapping[] values = RuntimeItems.VALUES;
        for (int i = 0; i < values.length; i++) {
            assertNotNull(values[i], "RuntimeItems.VALUES[" + i + "] should not be null");
        }
    }
}
