package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.level.GameRules;
import cn.nukkit.network.protocol.types.AuthoritativeMovementMode;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2192 登录期新增数据的冒烟测试：vanilla 方块属性随 StartGame 下发、体素形状缓存、拼图结构缓存。
 * <p>
 * Smoke tests for v2192 login-time data: vanilla block properties on the StartGame wire, voxel shapes cache, jigsaw structure cache.
 */
class V2192LoginDataTest {

    @BeforeAll
    static void setup() {
        MockServer.init();
    }

    @Test
    void vanillaBlockPropertiesReachLoginPacket() throws IOException {
        StartGamePacket packet = new StartGamePacket();
        packet.gameVersion = GameVersion.V1_26_50_27;
        packet.protocol = GameVersion.V1_26_50_27.getProtocol();
        packet.gameRules = new GameRules();
        packet.worldName = "fixture";
        packet.version = GameVersion.V1_26_50_27.toString();
        packet.vanillaVersion = packet.version;
        packet.blockDefinitions = List.of();
        packet.authoritativeMovementMode = AuthoritativeMovementMode.SERVER;
        packet.encode();

        String wire = new String(packet.getBuffer(), StandardCharsets.ISO_8859_1);
        for (String name : vanillaPropertyNames()) {
            assertTrue(wire.contains(name), "登录包缺少方块属性 / login packet is missing block property: " + name);
        }
    }

    private static List<String> vanillaPropertyNames() throws IOException {
        try (InputStream stream = V2192LoginDataTest.class.getClassLoader()
                .getResourceAsStream("data_driven_blocks_2193.nbt");
             NBTInputStream nbt = NbtUtils.createGZIPReader(stream)) {
            NbtMap properties = (NbtMap) nbt.readTag();
            assertFalse(properties.isEmpty(), "data_driven_blocks_2193.nbt 应非空 / should not be empty");
            return List.copyOf(properties.keySet());
        }
    }

    @Test
    void voxelShapesCachedPacketsSplit() {
        // 静态初始化会加载 voxel_shapes_2193.bin，缺失即抛错 / static init loads the bin and fails loudly if missing
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
