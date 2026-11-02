package cn.nukkit;

import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁住 1.26.4x/1.26.5x/1.26.60 系的协议号映射不变量：1.26.44 与 1.26.40 同为 2168 且不进入 BY_PROTOCOL，
 * 1.26.45 独占 2169；2192 是 1.26.50.27 预览版，1.26.50 正式版独占 2193，1.26.60 独占 2225。
 * <p>
 * Locks the 1.26.4x/1.26.5x/1.26.60 protocol-mapping invariants: 1.26.44 shares 2168 with 1.26.40 and stays
 * out of BY_PROTOCOL, 1.26.45 owns 2169; 2192 is the 1.26.50.27 preview, the 1.26.50 stable owns 2193,
 * and 1.26.60 owns 2225.
 */
class GameVersionTest {

    @Test
    void v1_26_44SharesProtocolWithV1_26_40() {
        assertEquals(ProtocolInfo.v1_26_40, GameVersion.V1_26_44.getProtocol());
    }

    @Test
    void byProtocolPrefersFirstDeclaredOnDuplicateProtocol() {
        // Login 前无法区分 1.26.40 与 1.26.44，byProtocol(2168) 必须返回 V1_26_40；
        // V1_26_44 仅可经直接引用或 byName 到达
        // 1.26.40 vs 1.26.44 is indistinguishable pre-login; byProtocol(2168) must return V1_26_40
        assertEquals(GameVersion.V1_26_40, GameVersion.byProtocol(ProtocolInfo.v1_26_40, false));
        assertEquals(GameVersion.V1_26_44, GameVersion.byName("1.26.44"));
    }

    @Test
    void byProtocolResolvesV1_26_45() {
        assertEquals(GameVersion.V1_26_45, GameVersion.byProtocol(ProtocolInfo.v1_26_45, false));
    }

    @Test
    void byProtocolResolvesV1_26_50PreviewAndStable() {
        // 2192=预览版、2193=正式版，两个协议号必须各自独立解析
        // 2192 is the preview and 2193 the stable release; both must resolve distinctly
        assertEquals(GameVersion.V1_26_50_27, GameVersion.byProtocol(ProtocolInfo.v1_26_50_27, false));
        assertEquals(GameVersion.V1_26_50, GameVersion.byProtocol(ProtocolInfo.v1_26_50, false));
    }

    @Test
    void byProtocolResolvesV1_26_60() {
        assertEquals(GameVersion.V1_26_60, GameVersion.byProtocol(ProtocolInfo.v1_26_60, false));
    }

    @Test
    void lastVersionIsV1_26_60() {
        assertEquals(GameVersion.V1_26_60, GameVersion.getLastVersion());
    }

    @Test
    void featureVersionHoldsAtV1_26_50() {
        // 存储基线暂停在 1.26.50，存储升级后再与 LAST_VERSION 一起前进
        // Storage baseline holds at 1.26.50; advance with LAST_VERSION once storage upgrades
        assertEquals(GameVersion.V1_26_50, GameVersion.getFeatureVersion());
    }

    @Test
    void supportedProtocolsContainWireNumbersOnly() {
        assertTrue(ProtocolInfo.SUPPORTED_PROTOCOLS.contains(ProtocolInfo.v1_26_40));
        assertTrue(ProtocolInfo.SUPPORTED_PROTOCOLS.contains(ProtocolInfo.v1_26_45));
        assertTrue(ProtocolInfo.SUPPORTED_PROTOCOLS.contains(ProtocolInfo.v1_26_50_27));
        assertTrue(ProtocolInfo.SUPPORTED_PROTOCOLS.contains(ProtocolInfo.v1_26_50));
        assertTrue(ProtocolInfo.SUPPORTED_PROTOCOLS.contains(ProtocolInfo.v1_26_60));
    }

    @Test
    void currentProtocolIsV1_26_60() {
        // Utils.dynamic 允许测试属性覆盖；默认应解析到 1.26.60（2225）的版本字符串
        // Utils.dynamic allows a test-property override; default must resolve to the 1.26.60 (2225) string
        assertEquals("1.26.60", cn.nukkit.utils.Utils.getVersionByProtocol(ProtocolInfo.CURRENT_PROTOCOL));
        assertEquals("1.26.50.27", cn.nukkit.utils.Utils.getVersionByProtocol(ProtocolInfo.v1_26_50_27));
    }
}
