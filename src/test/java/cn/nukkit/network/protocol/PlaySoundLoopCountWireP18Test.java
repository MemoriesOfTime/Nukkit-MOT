package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Independent wire oracle from Mojang 2193, Cloudburst v2193 and gophertunnel 2193. */
class PlaySoundLoopCountWireP18Test {
    private static Stream<GameVersion> windowVersions() {
        return Arrays.stream(GameVersion.values()).filter(v -> !v.isNetEase()
                && v.getProtocol() >= ProtocolInfo.v1_20_0
                && v.getProtocol() <= ProtocolInfo.v1_26_50);
    }

    private static Stream<GameVersion> legacyVersions() {
        return windowVersions().filter(v -> v.getProtocol() < ProtocolInfo.v1_26_50);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 2, Integer.MAX_VALUE})
    void released2193UsesSignedLoopCount(int loops) {
        PlaySoundPacket packet = packet(GameVersion.V1_26_50, loops, 0);
        assertArrayEquals(expected(packet), packet.getBuffer());
    }

    @ParameterizedTest
    @MethodSource("legacyVersions")
    void preservesEveryOlderLayoutIncluding2192(GameVersion version) {
        for (int loops : new int[]{-1, 0, 1, Integer.MAX_VALUE}) {
            for (int fields = 0; fields < 4; fields++) {
                PlaySoundPacket packet = packet(version, loops, fields);
                assertArrayEquals(expected(packet), packet.getBuffer(),
                        version + " loops=" + loops + " fields=" + fields);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("windowVersions")
    void hostDefaultZeroLoopsRetainsWholePacket(GameVersion version) {
        PlaySoundPacket packet = packet(version, 0, 0);
        assertArrayEquals(expected(packet), packet.getBuffer());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void keepsListenerHandleAndPlaybackFieldsInOrder(int fields) {
        PlaySoundPacket packet = packet(GameVersion.V1_26_50, 7, fields);
        assertArrayEquals(expected(packet), packet.getBuffer());
    }

    private static PlaySoundPacket packet(GameVersion version, int loops, int fields) {
        PlaySoundPacket packet = new PlaySoundPacket();
        packet.protocol = version.getProtocol();
        packet.gameVersion = version;
        packet.name = "random.click";
        packet.x = 12;
        packet.y = -17;
        packet.z = -9;
        packet.volume = 0.75f;
        packet.pitch = 1.25f;
        packet.loopCount = loops;
        packet.bypassListenerRangeCheck = (fields & 1) != 0;
        packet.serverSoundHandle = (fields & 1) != 0 ? 0x0102030405060708L : null;
        packet.playbackPositionSeconds = (fields & 2) != 0 ? 3.5f : null;
        packet.encode();
        return packet;
    }

    private static byte[] expected(PlaySoundPacket packet) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        unsigned(out, 86); // PlaySound packet header.
        string(out, packet.name);
        signed(out, packet.x << 3);
        if (packet.protocol >= ProtocolInfo.v1_26_10) signed(out, packet.y << 3);
        else unsigned(out, Integer.toUnsignedLong(packet.y << 3));
        signed(out, packet.z << 3);
        little(out, Integer.toUnsignedLong(Float.floatToRawIntBits(packet.volume)), 4);
        little(out, Integer.toUnsignedLong(Float.floatToRawIntBits(packet.pitch)), 4);
        if (packet.protocol >= ProtocolInfo.v1_26_50) signed(out, (int) packet.loopCount);
        else if (packet.protocol >= ProtocolInfo.v1_26_40) {
            // Preserve 2168, 2169 and preview 2192; this patch changes only released 2193.
            unsigned(out, Integer.toUnsignedLong((int) packet.loopCount));
        }
        if (packet.protocol >= ProtocolInfo.v1_26_50_27) {
            out.write(packet.bypassListenerRangeCheck ? 1 : 0);
        }
        if (packet.protocol >= ProtocolInfo.v1_26_20_26) {
            out.write(packet.serverSoundHandle != null ? 1 : 0);
            if (packet.serverSoundHandle != null) little(out, packet.serverSoundHandle, 8);
        }
        if (packet.protocol >= ProtocolInfo.v1_26_50_27) {
            out.write(packet.playbackPositionSeconds != null ? 1 : 0);
            if (packet.playbackPositionSeconds != null) {
                little(out, Integer.toUnsignedLong(Float.floatToRawIntBits(packet.playbackPositionSeconds)), 4);
            }
        }
        return out.toByteArray();
    }

    private static void string(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        unsigned(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void signed(ByteArrayOutputStream out, int value) {
        unsigned(out, Integer.toUnsignedLong((value << 1) ^ (value >> 31)));
    }

    private static void unsigned(ByteArrayOutputStream out, long value) {
        do {
            int bits = (int) (value & 0x7f);
            value >>>= 7;
            out.write(bits | (value != 0 ? 0x80 : 0));
        } while (value != 0);
    }

    private static void little(ByteArrayOutputStream out, long value, int length) {
        for (int i = 0; i < length; i++) out.write((int) (value >>> (i * 8)) & 0xff);
    }
}
