package cn.nukkit.entity;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.Network;
import cn.nukkit.network.PacketPool;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerEntitySkinSender;
import cn.nukkit.network.protocol.PlayerListPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.ProtocolInfo_v113;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the frozen native fixture; reflection is test setup only, never a timed loop. */
class PlaceholderSkinContractTest {
    private EntityHumanSkinLifecycleTest fixture;
    private static final UUID UUID_FIXED = new UUID(0x123456789abcdef0L, 0x123456789abcdef0L);
    private static final String PIXELS_SHA = "2de9f1efb4cf85f020c68465acf41f8211e2402e7425ffc902537b0e7046e4f0";

    @BeforeEach
    void setUp() throws Exception {
        fixture = new EntityHumanSkinLifecycleTest();
        fixture.setUp();
    }

    private static Skin oracle() {
        Skin skin = new Skin();
        skin.setSkinId("nukkit.empty-player-entity-skin");
        skin.setSkinData(Base64.getDecoder().decode(Skin.STEVE_SKIN));
        skin.setGeometryName("geometry.humanoid.custom");
        assertTrue(skin.isValid());
        return skin;
    }

    private PlayerListPacket register(GameVersion version, boolean initial) throws Exception {
        Method factory = EntityHumanSkinLifecycleTest.class.getDeclaredMethod("newViewer", GameVersion.class);
        factory.setAccessible(true);
        Player viewer = (Player) factory.invoke(fixture, version);
        Skin real = oracle();
        real.setSkinId("probe-real-npc");
        boolean accepted = initial
                ? PlayerEntitySkinSender.sendInitialSkinIfAbsent(viewer, UUID_FIXED, 1007L,
                    "Probe NPC", real, "fixed-xuid", () -> real)
                : PlayerEntitySkinSender.registerPostSpawnSkinIfAbsent(viewer, UUID_FIXED, 1007L,
                    "Probe NPC", "fixed-xuid", () -> real);
        assertTrue(accepted);
        Field packetsField = viewer.getClass().getDeclaredField("sentPackets");
        packetsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<DataPacket> packets = (List<DataPacket>) packetsField.get(viewer);
        PlayerListPacket add = packets.stream().filter(PlayerListPacket.class::isInstance)
                .map(PlayerListPacket.class::cast).findFirst().orElseThrow();
        assertEquals(PlayerListPacket.TYPE_ADD, add.type);
        assertEquals(1, add.entries.length);
        assertEquals(UUID_FIXED, add.entries[0].uuid);
        assertEquals(1007L, add.entries[0].entityId);
        assertEquals("Probe NPC", add.entries[0].name);
        assertEquals("fixed-xuid", add.entries[0].xboxUserId);
        return add;
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void assertEquivalent(Skin actual) throws Exception {
        Skin expected = oracle();
        assertNotSame(Skin.NO_PERSONA_SKIN, actual);
        assertEquals(64, actual.getSkinData().width);
        assertEquals(64, actual.getSkinData().height);
        assertEquals(16384, actual.getSkinData().data.length);
        assertEquals(PIXELS_SHA, digest(actual.getSkinData().data));
        assertArrayEquals(expected.getSkinData().data, actual.getSkinData().data);
        assertEquals(expected.getSkinId(), actual.getSkinId());
        assertEquals(expected.getGeometryData(), actual.getGeometryData());
        assertEquals(expected.getSkinResourcePatch(), actual.getSkinResourcePatch());
        assertTrue(actual.isValid());
        assertFalse(actual.isFullyTransparent());
        assertFalse(actual.isPremium());
        assertFalse(actual.isPersona());
        assertFalse(actual.isCapeOnClassic());
        assertFalse(actual.isLegacySlim);
        assertTrue(actual.isPrimaryUser());
        assertTrue(actual.isTrusted());
        assertTrue(actual.isOverridingPlayerAppearance());
        assertEquals("", actual.getProfileHash());
        assertEquals("#0", actual.getSkinColor());
        assertEquals("wide", actual.getArmSize());
        assertEquals("0.0.0", actual.getGeometryDataEngineVersion());
        assertTrue(actual.getAnimations().isEmpty());
        assertTrue(actual.getPersonaPieces().isEmpty());
        assertTrue(actual.getTintColors().isEmpty());
        assertEquals(0, actual.getCapeData().data.length);
        // Do not call randomized/lazy identity getters before this characterization.
        Field fullId = Skin.class.getDeclaredField("fullSkinId");
        fullId.setAccessible(true);
        assertNull(fullId.get(actual));
    }

    @Test
    void postSpawnRegistrationPreservesPlaceholderFields() throws Exception {
        assertEquivalent(register(GameVersion.V1_26_50, false).entries[0].skin);
    }

    @Test
    void initialRegistrationPreservesPlaceholderFields() throws Exception {
        assertEquivalent(register(GameVersion.V1_21_124_NETEASE, true).entries[0].skin);
    }

    @Test
    void allVersionAddPacketsEqualFrozenOracle() throws Exception {
        // Only v113 reset() consults the registry. Use its exact native packet ID;
        // a full Network constructor would inspect host interfaces unnecessarily.
        Network network = mock(Network.class);
        PacketPool pool = PacketPool.builder().protocolVersion(ProtocolInfo.v1_1_0)
                .minecraftVersion("1.1.0")
                .registerPacket(ProtocolInfo_v113.PLAYER_LIST_PACKET, PlayerListPacket.class).build();
        when(network.getPacketPool(GameVersion.V1_1_0)).thenReturn(pool);
        when(MockServer.get().getNetwork()).thenReturn(network);
        int count = 0;
        for (GameVersion version : GameVersion.values()) {
            PlayerListPacket actual = register(version, false);
            PlayerListPacket expected = new PlayerListPacket();
            expected.type = PlayerListPacket.TYPE_ADD;
            expected.entries = new PlayerListPacket.Entry[]{
                    new PlayerListPacket.Entry(UUID_FIXED, 1007L, "Probe NPC", oracle(), "fixed-xuid")};
            for (PlayerListPacket packet : new PlayerListPacket[]{actual, expected}) {
                packet.gameVersion = version;
                packet.protocol = version.getProtocol();
                // Native fullSkinId is randomized lazily; compare payloads under
                // identical explicit identity inputs, without changing production.
                packet.entries[0].skin.setFullSkinId("probe-placeholder-full-id");
                packet.entries[0].skin.setPlayFabId("probe-playfab");
                packet.encode();
            }
            assertArrayEquals(expected.getBuffer(), actual.getBuffer(), version.name());
            System.out.println("PACKET\t" + version.name() + "\t" + version.getProtocol()
                    + "\t" + actual.getBuffer().length + "\t" + digest(actual.getBuffer()));
            count++;
        }
        assertEquals(GameVersion.values().length, count);
        System.out.println("VERSIONS\t" + count);
    }

    @Test
    void pixelsAndFieldsNeverAliasOtherResultsOrPublicDefault() throws Exception {
        Skin first = register(GameVersion.V1_26_50, false).entries[0].skin;
        Skin second = register(GameVersion.V1_21_124_NETEASE, true).entries[0].skin;
        assertNotSame(first, second);
        assertNotSame(first.getSkinData(), second.getSkinData());
        assertNotSame(first.getSkinData().data, second.getSkinData().data);
        Skin savedDefault = Skin.NO_PERSONA_SKIN;
        byte[] savedPixels = savedDefault.getSkinData().data.clone();
        String savedId = savedDefault.getSkinId();
        String savedPatch = savedDefault.getSkinResourcePatch();
        boolean savedSlim = savedDefault.isLegacySlim;
        Field fingerprint = Skin.class.getDeclaredField("cachedFingerprint");
        fingerprint.setAccessible(true);
        Object savedFingerprint = fingerprint.get(savedDefault);
        try {
            first.getSkinData().data[3] ^= (byte) 0xff;
            first.setSkinId("mutated-first");
            first.setGeometryName("geometry.humanoid.customSlim");
            savedDefault.getSkinData().data[7] ^= (byte) 0xff;
            savedDefault.setSkinId("mutated-public-default");
            savedDefault.setGeometryName("geometry.humanoid.customSlim");
            assertEquivalent(second);
            Skin third = register(GameVersion.V1_26_50, false).entries[0].skin;
            assertEquivalent(third);
            assertNotSame(first.getSkinData().data, third.getSkinData().data);
            assertNotSame(second.getSkinData().data, third.getSkinData().data);
            Skin.NO_PERSONA_SKIN = first;
            Skin fourth = register(GameVersion.V1_21_124_NETEASE, true).entries[0].skin;
            assertEquivalent(fourth);
            assertNotSame(first.getSkinData().data, fourth.getSkinData().data);
        } finally {
            Skin.NO_PERSONA_SKIN = savedDefault;
            System.arraycopy(savedPixels, 0, savedDefault.getSkinData().data, 0, savedPixels.length);
            savedDefault.setSkinId(savedId);
            savedDefault.setSkinResourcePatch(savedPatch);
            savedDefault.isLegacySlim = savedSlim;
            fingerprint.set(savedDefault, savedFingerprint);
        }
    }
}
