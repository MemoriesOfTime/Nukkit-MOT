package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 校验 1.26.4x 系 SetScore REMOVE 条目的 optional 层数差异：
 * 1.26.44 为 Double optional（wire 协议号未提升，仍 2168），1.26.40/1.26.45 为单 optional。
 * <p>
 * Verifies the optional-layer difference of SetScore REMOVE entries across the 1.26.4x line:
 * double-optional on 1.26.44 (wire protocol not bumped, still 2168), single on 1.26.40/1.26.45.
 */
class SetScorePacketTest {

    private static byte[] encodeRemoveEntry(GameVersion gameVersion, String objectiveId) {
        SetScorePacket pk = new SetScorePacket();
        pk.protocol = gameVersion.getProtocol();
        pk.gameVersion = gameVersion;
        pk.action = SetScorePacket.Action.REMOVE;
        pk.infos.add(new SetScorePacket.ScoreInfo(7L, objectiveId, 0));
        pk.encode();
        return pk.getBuffer();
    }

    private static void assertEndsWith(byte[] buffer, byte[] tail) {
        assertTrue(buffer.length >= tail.length,
                () -> "buffer shorter than expected tail: " + buffer.length + " < " + tail.length);
        byte[] actual = java.util.Arrays.copyOfRange(buffer, buffer.length - tail.length, buffer.length);
        assertArrayEquals(tail, actual);
    }

    private static byte[] tail(String objectiveId, boolean doubleOptional) {
        // present(true) [+ extra boolean, 1.26.44 only] + stringLength(3) + "obj"
        byte[] name = objectiveId.getBytes(StandardCharsets.US_ASCII);
        byte[] tail = new byte[doubleOptional ? 3 + name.length : 2 + name.length];
        int i = 0;
        tail[i++] = 1; // present
        if (doubleOptional) {
            tail[i++] = 1; // 1.26.44 Double optional 内层标记
        }
        tail[i++] = (byte) name.length;
        System.arraycopy(name, 0, tail, i, name.length);
        return tail;
    }

    @Test
    void v1_26_40WritesSingleOptionalObjectiveName() {
        assertEndsWith(encodeRemoveEntry(GameVersion.V1_26_40, "obj"), tail("obj", false));
    }

    @Test
    void v1_26_44WritesDoubleOptionalObjectiveName() {
        assertEndsWith(encodeRemoveEntry(GameVersion.V1_26_44, "obj"), tail("obj", true));
    }

    @Test
    void v1_26_45RevertsToSingleOptionalObjectiveName() {
        assertEndsWith(encodeRemoveEntry(GameVersion.V1_26_45, "obj"), tail("obj", false));
    }

    @Test
    void v1_26_45EncodingIsByteIdenticalToV1_26_40() {
        // 1.26.45 除协议号外 wire 层与 1.26.40 完全一致（含 SetScore 回滚）
        // 1.26.45 is wire-identical to 1.26.40 apart from the protocol number
        assertArrayEquals(encodeRemoveEntry(GameVersion.V1_26_40, "obj"), encodeRemoveEntry(GameVersion.V1_26_45, "obj"));
    }

    @Test
    void v1_26_44AddsExactlyOneExtraByte() {
        assertEquals(encodeRemoveEntry(GameVersion.V1_26_40, "obj").length + 1,
                encodeRemoveEntry(GameVersion.V1_26_44, "obj").length);
    }

    @Test
    void absentObjectiveNameHasNoOptionalLayers() {
        for (GameVersion version : new GameVersion[]{GameVersion.V1_26_40, GameVersion.V1_26_44, GameVersion.V1_26_45}) {
            assertEndsWith(encodeRemoveEntry(version, null), new byte[]{0});
        }
    }
}
