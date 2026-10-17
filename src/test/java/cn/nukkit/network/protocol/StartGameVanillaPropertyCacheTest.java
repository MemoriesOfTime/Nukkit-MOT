package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.custom.CustomBlockDefinition;
import cn.nukkit.level.GameRules;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.types.AuthoritativeMovementMode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StartGameVanillaPropertyCacheTest {
    @BeforeAll static void setup() { MockServer.init(); }
    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values()).filter(v -> !v.isNetEase()
                && v.getProtocol() >= ProtocolInfo.v1_20_0
                && v.getProtocol() <= ProtocolInfo.v1_26_50);
    }
    static StartGamePacket packet(GameVersion version) {
        StartGamePacket p = spy(new StartGamePacket());
        assertSame(StartGamePacket.class, p.getClass());
        p.gameVersion = version; p.protocol = version.getProtocol();
        p.gameRules = new GameRules(); p.worldName = "fixture";
        p.version = version.toString(); p.vanillaVersion = version.toString();
        p.blockDefinitions = List.of();
        p.authoritativeMovementMode = AuthoritativeMovementMode.SERVER;
        return p;
    }
    @ParameterizedTest @MethodSource("versions")
    void eachLoginCopiesVanillaPropertiesWithoutAnotherNbtWriter(GameVersion version) {
        for (int login = 0; login < 3; login++) {
            StartGamePacket p = packet(version); p.entityUniqueId = login + 21;
            p.encode();
            verify(p, never()).putNbtTag(any());
        }
    }
    @Test void packetBytesAndSessionFieldsCannotCorruptSharedPalette() {
        StartGamePacket first = packet(GameVersion.V1_26_50); first.entityUniqueId = 21;
        first.encode(); byte[] original = first.getBuffer().clone();
        Arrays.fill(first.getBufferUnsafe(), (byte) 0);
        StartGamePacket second = packet(GameVersion.V1_26_50); second.entityUniqueId = 22;
        second.encode(); assertFalse(Arrays.equals(original, second.getBuffer()));
        StartGamePacket third = packet(GameVersion.V1_26_50); third.entityUniqueId = 21;
        third.encode(); assertArrayEquals(original, third.getBuffer());
    }
    @Test void mutableCustomDefinitionsStayDynamic() {
        StartGamePacket p = packet(GameVersion.V1_26_50);
        CompoundTag nbt = new CompoundTag().putString("probe", "before");
        p.blockDefinitions = List.of(new CustomBlockDefinition("test:cache_probe", nbt, 10001, null, false));
        p.encode(); byte[] before = p.getBuffer().clone();
        nbt.putString("probe", "after"); p.encode();
        assertFalse(Arrays.equals(before, p.getBuffer()), "custom NBT must never enter the vanilla cache");
        verify(p, never()).putNbtTag(any());
    }

    @Test void customPacketWritersStillVisitVanillaDefinitions() {
        CustomWriterPacket p = new CustomWriterPacket();
        p.gameVersion = GameVersion.V1_26_50; p.protocol = p.gameVersion.getProtocol();
        p.gameRules = new GameRules(); p.worldName = "fixture";
        p.version = p.gameVersion.toString(); p.vanillaVersion = p.version;
        p.blockDefinitions = List.of(); p.authoritativeMovementMode = AuthoritativeMovementMode.SERVER;
        p.encode();
        assertEquals(98, p.nbtWrites, "subclasses retain the original virtual vanilla NBT writer");
        assertTrue(new String(p.getBuffer(), java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("test:minecraft:"), "subclass string transformations remain on wire");
    }
    static class CustomWriterPacket extends StartGamePacket {
        int nbtWrites;
        @Override public <T> void putNbtTag(T tag) { nbtWrites++; super.putNbtTag(tag); }
        @Override public void putString(String value) {
            super.putString(value.startsWith("minecraft:") ? "test:" + value : value);
        }
    }
}
