package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacketV1;
import cn.nukkit.network.protocol.LevelSoundEventPacketV2;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelSoundFinitePositionTest {
    private enum Layout { MODERN, V1, V2 }

    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values())
                .filter(version -> !version.isNetEase())
                .filter(version -> version.getProtocol() >= ProtocolInfo.v1_20_0
                        && version.getProtocol() <= ProtocolInfo.v1_26_50);
    }

    static Stream<Arguments> layouts() {
        return versions().flatMap(version -> Arrays.stream(Layout.values())
                .map(layout -> Arguments.of(version, layout)));
    }

    static Stream<Arguments> invalidPositions() {
        return versions().flatMap(version -> Arrays.stream(Layout.values())
                .flatMap(layout -> Stream.of(0, 1, 2).flatMap(axis ->
                        Stream.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
                                .map(value -> Arguments.of(version, layout, axis, value)))));
    }

    static Stream<GameVersion> fireVersions() {
        return versions().filter(version -> version.getProtocol() >= ProtocolInfo.v1_26_20_26);
    }

    static Stream<Arguments> invalidFirePositions() {
        return fireVersions().flatMap(version -> Stream.of(0, 1, 2).flatMap(axis ->
                Stream.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
                        .map(value -> Arguments.of(version, axis, value))));
    }

    static Stream<Arguments> ignoredFireLayouts() {
        return versions().flatMap(version -> Arrays.stream(Layout.values())
                .filter(layout -> layout != Layout.MODERN
                        || version.getProtocol() < ProtocolInfo.v1_26_20_26)
                .map(layout -> Arguments.of(version, layout)));
    }

    @ParameterizedTest(name = "{0} {1}: finite payload unchanged")
    @MethodSource("layouts")
    void finitePayloadIsForwardedUnchanged(GameVersion version, Layout layout) {
        for (float[] position : new float[][] {
                {-0.0f, 64.0f, 2.0f}, {16.0f, 64.0f, 0.0f},
                {17.0f, 64.0f, 0.0f}, {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE}}) {
            for (boolean global : new boolean[] {false, true}) {
                LevelSoundEventPacket input = packet(version, layout, position, global);
                if (layout == Layout.MODERN) input.fireAtPosition = new Vector3f(1, 2, 3);
                LevelSoundEventPacket decoded = roundTrip(input, layout);
                if (layout != Layout.MODERN) {
                    // These parent fields do not belong to the legacy wire layout.
                    decoded.x = decoded.y = decoded.z = Float.NaN;
                }
                assertForwardedUnchanged(decoded, layout);
            }
        }
    }

    @ParameterizedTest(name = "{0} {1}: axis {2} rejects {3}")
    @MethodSource("invalidPositions")
    void nonFiniteSoundCoordinatesNeverReachObservers(GameVersion version, Layout layout,
                                                       int axis, float value) {
        float[] position = {1, 64, 2};
        position[axis] = value;
        LevelSoundEventPacket decoded = roundTrip(packet(version, layout, position, false), layout);
        Fixture fixture = new Fixture(false);
        handle(fixture.player, decoded, layout);
        verify(fixture.level, never()).addChunkPacket(anyInt(), anyInt(), any());
    }

    @ParameterizedTest(name = "{0}: optional fire axis {1} rejects {2}")
    @MethodSource("invalidFirePositions")
    void effectiveOptionalFireVectorNeverRelaysNonFiniteCoordinates(GameVersion version,
                                                                    int axis, float value) {
        LevelSoundEventPacket input = packet(version, Layout.MODERN, new float[] {1, 64, 2}, false);
        float[] fire = {3, 4, 5};
        fire[axis] = value;
        input.fireAtPosition = new Vector3f(fire[0], fire[1], fire[2]);
        LevelSoundEventPacket decoded = roundTrip(input, Layout.MODERN);
        assertNotNull(decoded.fireAtPosition);
        Fixture fixture = new Fixture(false);
        handle(fixture.player, decoded, Layout.MODERN);
        verify(fixture.level, never()).addChunkPacket(anyInt(), anyInt(), any());
    }

    @ParameterizedTest(name = "{0} {1}: ignored optional field remains ignored")
    @MethodSource("ignoredFireLayouts")
    void unusedOptionalFieldDoesNotChangeOlderOrLegacyPayloads(GameVersion version, Layout layout) {
        LevelSoundEventPacket decoded = roundTrip(packet(version, layout, new float[] {1, 64, 2}, true), layout);
        decoded.fireAtPosition = new Vector3f(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
        assertForwardedUnchanged(decoded, layout);
    }

    @ParameterizedTest(name = "{0} {1}: spectator remains silent")
    @MethodSource("layouts")
    void spectatorsStillCannotRelaySound(GameVersion version, Layout layout) {
        LevelSoundEventPacket decoded = roundTrip(packet(version, layout, new float[] {1, 64, 2}, false), layout);
        Fixture fixture = new Fixture(true);
        handle(fixture.player, decoded, layout);
        verify(fixture.level, never()).addChunkPacket(anyInt(), anyInt(), any());
    }

    private static void assertForwardedUnchanged(LevelSoundEventPacket packet, Layout layout) {
        packet.encode();
        byte[] before = packet.getBuffer();
        Fixture fixture = new Fixture(false);
        handle(fixture.player, packet, layout);
        var captured = org.mockito.ArgumentCaptor.forClass(cn.nukkit.network.protocol.DataPacket.class);
        verify(fixture.level).addChunkPacket(eq(7), eq(-3), captured.capture());
        LevelSoundEventPacket forwarded = (LevelSoundEventPacket) captured.getValue();
        assertEquals(packet.getClass(), forwarded.getClass());
        LevelSoundEventPacket expected = (LevelSoundEventPacket) packet.clone();
        expected.isEncoded = false; expected.reuse();
        expected.isGlobal = false; expected.entityUniqueId = fixture.player.getId();
        if (expected instanceof LevelSoundEventPacketV1 legacy) legacy.isGlobal = false;
        if (expected instanceof LevelSoundEventPacketV2 legacy) legacy.isGlobal = false;
        expected.encode(); forwarded.encode();
        assertArrayEquals(expected.getBuffer(), forwarded.getBuffer(), "only actor authority may change");
        packet.encode();
        assertArrayEquals(before, packet.getBuffer(), "caller-owned input is unchanged");
    }

    private static LevelSoundEventPacket roundTrip(LevelSoundEventPacket input, Layout layout) {
        input.encode();
        BinaryStream header = new BinaryStream(input.getBuffer());
        header.getUnsignedVarInt();
        LevelSoundEventPacket decoded = create(layout);
        decoded.protocol = input.protocol;
        decoded.gameVersion = input.gameVersion;
        decoded.setBuffer(input.getBuffer(), header.getOffset());
        decoded.decode();
        assertEquals(input.getCount(), decoded.getOffset(), "complete native payload consumed");
        return decoded;
    }

    private static LevelSoundEventPacket packet(GameVersion version, Layout layout,
                                                 float[] position, boolean global) {
        LevelSoundEventPacket packet = create(layout);
        packet.protocol = version.getProtocol();
        packet.gameVersion = version;
        if (packet instanceof LevelSoundEventPacketV1 legacy) {
            legacy.sound = LevelSoundEventPacket.SOUND_HIT;
            legacy.x = position[0]; legacy.y = position[1]; legacy.z = position[2];
            legacy.isGlobal = global;
        } else if (packet instanceof LevelSoundEventPacketV2 legacy) {
            legacy.sound = LevelSoundEventPacket.SOUND_HIT;
            legacy.x = position[0]; legacy.y = position[1]; legacy.z = position[2];
            legacy.entityIdentifier = "minecraft:player";
            legacy.isGlobal = global;
        } else {
            packet.sound = LevelSoundEventPacket.SOUND_HIT;
            packet.x = position[0]; packet.y = position[1]; packet.z = position[2];
            packet.entityIdentifier = "minecraft:player";
            packet.isGlobal = global;
            packet.entityUniqueId = 123;
        }
        return packet;
    }

    private static LevelSoundEventPacket create(Layout layout) {
        return switch (layout) {
            case MODERN -> new LevelSoundEventPacket();
            case V1 -> new LevelSoundEventPacketV1();
            case V2 -> new LevelSoundEventPacketV2();
        };
    }

    private static void handle(Player player, LevelSoundEventPacket packet, Layout layout) {
        PlayerHandle handle = new PlayerHandle(player);
        switch (layout) {
            case MODERN -> LevelSoundEventProcessor.INSTANCE.handle(handle, packet);
            case V1 -> LevelSoundEventProcessorV1.INSTANCE.handle(handle, packet);
            case V2 -> LevelSoundEventProcessorV2.INSTANCE.handle(handle, packet);
        }
    }

    private static final class Fixture {
        final Player player = mock(Player.class);
        final Level level = mock(Level.class);

        Fixture(boolean spectator) {
            player.level = level;
            when(player.isSpectator()).thenReturn(spectator);
            when(player.getChunkX()).thenReturn(7);
            when(player.getChunkZ()).thenReturn(-3);
        }
    }
}
