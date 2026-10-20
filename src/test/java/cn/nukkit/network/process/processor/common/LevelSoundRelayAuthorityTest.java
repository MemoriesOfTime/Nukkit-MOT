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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Arrays;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelSoundRelayAuthorityTest {
    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values()).filter(v -> !v.isNetEase())
            .filter(v -> v.getProtocol() >= ProtocolInfo.v1_20_0 && v.getProtocol() <= ProtocolInfo.v1_26_50);
    }
    @ParameterizedTest @MethodSource("versions")
    void relayBindsAuthorityAndRetainsHonestPayloadAcrossClientWindow(GameVersion version) {
        Fixture f = new Fixture();
        LevelSoundEventPacket client = packet();
        client.protocol = version.getProtocol();
        client.isGlobal = true;
        client.entityUniqueId = 9002;
        client.fireAtPosition = new Vector3f(3, 66, 4);
        client.tryEncode();
        LevelSoundEventProcessor.INSTANCE.handle(f.handle, client);
        LevelSoundEventPacket sent = f.sent;
        assertNotNull(sent);
        assertNotSame(client, sent, "broadcast must not retain client authority or encoded buffer");
        assertFalse(sent.isGlobal);
        assertEquals(1234, sent.entityUniqueId);
        assertFalse(sent.isEncoded);
        assertEquals(client.sound, sent.sound);
        assertEquals(client.x, sent.x); assertEquals(client.y, sent.y); assertEquals(client.z, sent.z);
        assertEquals(client.extraData, sent.extraData);
        assertEquals(client.entityIdentifier, sent.entityIdentifier);
        assertTrue(sent.isBabyMob);
        assertEquals(client.fireAtPosition, sent.fireAtPosition);
        assertTrue(client.isGlobal, "input belongs to receive/event pipeline");
        assertEquals(9002, client.entityUniqueId);
        sent.tryEncode();
        LevelSoundEventPacket decoded = new LevelSoundEventPacket();
        decoded.protocol = version.getProtocol();
        decoded.setBuffer(sent.getBuffer(), 1);
        decoded.decode();
        assertFalse(decoded.isGlobal);
        assertEquals(sent.x, decoded.x); assertEquals(sent.extraData, decoded.extraData);
        if (version.getProtocol() >= ProtocolInfo.v1_21_70_24) assertEquals(1234, decoded.entityUniqueId);
        else assertEquals(-1, decoded.entityUniqueId);
        if (version.getProtocol() >= ProtocolInfo.v1_26_20_26) assertEquals(sent.fireAtPosition, decoded.fireAtPosition);
        else assertNull(decoded.fireAtPosition);
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void nonfinitePositionIsNeverRelayed(int axis) {
        Fixture f = new Fixture();
        LevelSoundEventPacket client = packet();
        if (axis == 0) client.x = Float.NaN;
        if (axis == 1) client.y = Float.POSITIVE_INFINITY;
        if (axis == 2) client.z = Float.NEGATIVE_INFINITY;
        if (axis >= 3) client.fireAtPosition = new Vector3f(axis == 3 ? Float.NaN : 1,
                axis == 4 ? Float.POSITIVE_INFINITY : 2, axis == 5 ? Float.NEGATIVE_INFINITY : 3);
        LevelSoundEventProcessor.INSTANCE.handle(f.handle, client);
        assertNull(f.sent);
    }
    @Test void spectatorRemainsSilent() {
        Fixture f = new Fixture(); when(f.player.isSpectator()).thenReturn(true);
        LevelSoundEventProcessor.INSTANCE.handle(f.handle, packet()); assertNull(f.sent);
    }
    @Test void legacyPacketClassesArePreserved() {
        for (LevelSoundEventPacket client : new LevelSoundEventPacket[]{new LevelSoundEventPacketV1(), new LevelSoundEventPacketV2()}) {
            Fixture f = new Fixture(); client.isGlobal = true;
            LevelSoundEventProcessor.INSTANCE.handle(f.handle, client);
            assertNotNull(f.sent); assertEquals(client.getClass(), f.sent.getClass()); assertFalse(f.sent.isGlobal);
        }
    }
    private static LevelSoundEventPacket packet() {
        LevelSoundEventPacket pk = new LevelSoundEventPacket();
        pk.sound = LevelSoundEventPacket.SOUND_BUCKET_FILL_WATER;
        pk.x = 1.5f; pk.y = 64.5f; pk.z = -2.5f; pk.extraData = 13;
        pk.entityIdentifier = "minecraft:player"; pk.isBabyMob = true;
        return pk;
    }
    private static final class Fixture {
        final Player player = mock(Player.class);
        final Level level = mock(Level.class);
        final PlayerHandle handle = new PlayerHandle(player);
        LevelSoundEventPacket sent;
        Fixture() {
            player.level = level; when(player.getId()).thenReturn(1234L);
            when(player.getChunkX()).thenReturn(5); when(player.getChunkZ()).thenReturn(-7);
            doAnswer(a -> { sent = a.getArgument(2); return null; })
                    .when(level).addChunkPacket(eq(5), eq(-7), any(LevelSoundEventPacket.class));
        }
    }
}
