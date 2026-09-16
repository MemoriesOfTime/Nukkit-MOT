package cn.nukkit.network.protocol;

import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2192 登录期新增数据的加载冒烟测试：vanilla 方块属性、体素形状缓存、拼图结构缓存。
 * <p>
 * Smoke tests for v2192 login-time data: vanilla block properties, voxel shapes cache, jigsaw structure cache.
 */
class V2192LoginDataTest {

    @Test
    void vanillaBlockPropertiesLoad() throws Exception {
        Field field = StartGamePacket.class.getDeclaredField("vanillaBlockProperties2192");
        field.setAccessible(true);
        List<?> properties = (List<?>) field.get(null);

        assertFalse(properties.isEmpty(), "block_properties_2192.json 应非空 / should not be empty");
        for (Object o : properties) {
            String name = (String) o.getClass().getDeclaredMethod("getName").invoke(o);
            NbtMap nbt = (NbtMap) o.getClass().getDeclaredMethod("getProperties").invoke(o);
            assertNotNull(name, "条目 name 为 null / entry name is null");
            assertTrue(name.startsWith("minecraft:"), "非 vanilla 命名空间 / not a vanilla namespace: " + name);
            assertNotNull(nbt, "条目 properties 为 null / entry properties is null");
            assertFalse(nbt.isEmpty(), "条目 properties 为空 / entry properties is empty: " + name);
        }
    }

    @Test
    void voxelShapesCachedPacketsSplit() {
        // 静态初始化会加载 voxel_shapes_2192.bin，缺失即抛错 / static init loads the bin and fails loudly if missing
        assertNotNull(VoxelShapesPacket.getCachedPacket(ProtocolInfo.v1_26_50_27));
        assertNotNull(VoxelShapesPacket.getCachedPacket(ProtocolInfo.v1_26_50));
        assertNotNull(VoxelShapesPacket.getCachedPacket(ProtocolInfo.v1_26_20_26));
        assertEquals(VoxelShapesPacket.getCachedPacket(ProtocolInfo.v1_26_50_27),
                VoxelShapesPacket.getCachedPacket(ProtocolInfo.v1_26_50), "2192/2193 应共用同一缓存 / 2192 and 2193 should share one cache");
    }

    @Test
    void jigsawStructureCachedPacketAvailable() {
        assertNotNull(JigsawStructureDataPacket.getCachedPacket());
    }
}
