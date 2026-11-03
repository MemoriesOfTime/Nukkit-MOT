package cn.nukkit;

import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.network.encryption.LoginChainVerifier;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.network.session.login.NetworkSessionState;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.permission.BanList;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ClientChainData;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerLoginTest {
    @Test
    void onlyLiveUnchangedLoginReceivesValidatedIdentityOnMain() throws Exception {
        MockServer.init();
        for (String state : new String[]{"live", "closed", "timeout", "phase-changed", "expired"}) {
            ClientChainData verified = mock(ClientChainData.class);
            when(verified.isAuthenticationCurrent()).thenReturn(!state.equals("expired"));
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger validations = new AtomicInteger();
            LoginChainVerifier verifier = verifier(8, packet -> {
                validations.incrementAndGet();
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                return verified;
            });
            Probe player = player();
            try {
                LoginPacket packet = new LoginPacket();
                packet.setBuffer(new byte[]{1});
                player.beginLoginVerification(packet, verifier);
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                player.beginLoginVerification(packet, verifier);
                assertEquals(1, validations.get(), "duplicate login cannot create another verification");
                assertFalse(Player.isPreLoginVerifiedPacketAllowed(SessionLoginPhase.LOGIN_RECEIVED,
                        ProtocolInfo.toNewProtocolID(ProtocolInfo.LOGIN_PACKET)));
                assertNull(player.accepted);
                assertNull(player.getLoginChainData());
                assertNull(player.getUniqueId());
                assertFalse(player.loginVerified);
                if (state.equals("closed")) player.closed = true;
                if (state.equals("timeout")) player.close("", "timeout");
                if (state.equals("phase-changed")) player.getNetworkSession().getState().getLogin()
                        .setPhase(SessionLoginPhase.DISCONNECTED);
                release.countDown();
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                if (!state.equals("timeout")) {
                    while (AsyncTask.FINISHED_LIST.isEmpty() && System.nanoTime() < until) Thread.sleep(1);
                    assertFalse(AsyncTask.FINISHED_LIST.isEmpty());
                }
                while (player.accepted == null && player.reason == null && System.nanoTime() < until) {
                    AsyncTask.collectTask();
                    Thread.sleep(1);
                    if (!state.equals("live") && !state.equals("expired") && AsyncTask.FINISHED_LIST.isEmpty()) break;
                }
                if (state.equals("live")) {
                    assertSame(verified, player.accepted);
                    assertSame(Thread.currentThread(), player.continuedOn);
                } else {
                    assertNull(player.accepted, state);
                }
                if (state.equals("expired")) assertNotNull(player.reason);
            } finally {
                release.countDown();
                verifier.shutdown();
            }
        }
    }

    @Test
    void overloadSendsProtocolFailureAndNeverStartsVerificationOrAssignsIdentity() throws Exception {
        MockServer.init();
        LoginChainVerifier verifier = verifier(1, packet -> { fail("must not decode over-budget input"); return null; });
        Probe player = player();
        LoginPacket packet = new LoginPacket();
        packet.setBuffer(new byte[]{1, 2});
        try {
            player.beginLoginVerification(packet, verifier);
            assertEquals(PlayStatusPacket.LOGIN_FAILED_SERVER_FULL, player.status);
            assertNotNull(player.reason);
            assertNull(player.accepted);
            assertNull(player.getLoginChainData());
            assertNull(player.getUniqueId());
        } finally {
            verifier.shutdown();
        }
    }

    private static LoginChainVerifier verifier(long byteCapacity, Function<byte[], ClientChainData> decoder) throws Exception {
        Constructor<LoginChainVerifier> constructor = LoginChainVerifier.class
                .getDeclaredConstructor(int.class, int.class, long.class, Function.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, 1, byteCapacity, decoder);
    }

    private static Probe player() throws Exception {
        Probe player = mock(Probe.class, CALLS_REAL_METHODS);
        player.connected = true;
        NetworkSessionState state = new NetworkSessionState();
        state.getLogin().setPhase(SessionLoginPhase.LOGIN_RECEIVED);
        NetworkPlayerSession session = mock(NetworkPlayerSession.class);
        when(session.getState()).thenReturn(state);
        Field field = Player.class.getDeclaredField("networkSession");
        field.setAccessible(true);
        field.set(player, session);
        field = cn.nukkit.entity.Entity.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(player, MockServer.get());
        return player;
    }

    private static class Probe extends Player {
        ClientChainData accepted;
        Thread continuedOn;
        String reason;
        int status;

        private Probe() { super(null, null, null); }
        @Override void continueVerifiedLogin(Skin skin, ClientChainData validated) {
            accepted = validated;
            continuedOn = Thread.currentThread();
        }
        @Override protected void sendPlayStatus(int status, boolean immediate) {
            assertTrue(immediate, "overload status must precede disconnect");
            this.status = status;
        }
        @Override public String getAddress() { return "127.0.0.1"; }
        @Override public void close(String message, String reason) {
            this.reason = reason;
            connected = false;
            closed = true;
            super.close(new TextContainer(message), reason, false); // Exercises pending-job cancellation.
        }
    }

    /*
     * keep-existing-session-on-duplicate-login: the player who is already in the world keeps playing
     * and the newcomer is the one that is refused, for an equal name and for an equal identity alike.
     */
    private static final String REFUSED = "disconnectionScreen.loggedinOtherLocation";

    @Test
    void anEqualNameIsRefusedWhileTheServerKeepsTheExistingSession() throws Exception {
        UUID playing = UUID.randomUUID();
        Server server = server(true, existing("Steve", playing));
        AtomicReference<String> reason = new AtomicReference<>();
        Player newcomer = newcomer(server, "steve", UUID.randomUUID(), reason);

        newcomer.processLogin();

        assertEquals(REFUSED, reason.get(), "the newcomer is refused");
        verify(onlyPlayer(server), never()).close(anyString(), anyString());
    }

    @Test
    void anEqualIdentityIsRefusedWhileTheServerKeepsTheExistingSession() throws Exception {
        UUID identity = UUID.randomUUID();
        Server server = server(true, existing("Alex", identity));
        AtomicReference<String> reason = new AtomicReference<>();
        Player newcomer = newcomer(server, "Steve", identity, reason);

        newcomer.processLogin();

        assertEquals(REFUSED, reason.get(), "the newcomer is refused");
        verify(onlyPlayer(server), never()).close(anyString(), anyString());
        verify(server.getLogger()).warning(contains("Refused the login of Steve"));
    }

    @Test
    void theHistoricalBehaviourStillClosesTheOlderSessionFirst() throws Exception {
        UUID playing = UUID.randomUUID();
        Server server = server(false, existing("Steve", playing));
        AtomicReference<String> reason = new AtomicReference<>();
        Player newcomer = newcomer(server, "steve", UUID.randomUUID(), reason);

        // The login goes on to load player data after the check; only the eviction matters here.
        try {
            newcomer.processLogin();
        } catch (RuntimeException ignored) {
        }

        verify(onlyPlayer(server)).close("", REFUSED);
        assertNotEquals(REFUSED, reason.get(), "the newcomer is not refused by default");
    }

    private static Player existing(String name, UUID identity) {
        Player player = mock(Player.class);
        player.username = name;
        when(player.getUniqueId()).thenReturn(identity);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static Server server(boolean keepExisting, Player existing) throws Exception {
        Server server = mock(Server.class);
        when(server.isDuplicateLoginKeepingExistingSession()).thenReturn(keepExisting);
        when(server.isWhitelisted(anyString())).thenReturn(true);
        BanList bans = mock(BanList.class);
        when(server.getNameBans()).thenReturn(bans);
        when(server.getIPBans()).thenReturn(bans);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        Map<UUID, Player> players = new HashMap<>();
        players.put(existing.getUniqueId(), existing);
        Field field = Server.class.getDeclaredField("playerList");
        field.setAccessible(true);
        field.set(server, players);
        return server;
    }

    private static Player onlyPlayer(Server server) {
        return server.playerList.values().iterator().next();
    }

    private static Player newcomer(Server server, String name, UUID identity, AtomicReference<String> reason) throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        player.username = name;
        Field uuid = EntityHuman.class.getDeclaredField("uuid");
        uuid.setAccessible(true);
        uuid.set(player, identity);
        Field serverField = cn.nukkit.entity.Entity.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(player, server);
        doReturn("127.0.0.1").when(player).getAddress();
        doAnswer(invocation -> {
            reason.set(invocation.getArgument(1));
            return null;
        }).when(player).close(anyString(), anyString());
        return player;
    }

    @Test
    void resourcePackValidationNotificationIsAllowedDuringResourcePackLogin() {
        int packetId = ProtocolInfo.RESOURCE_PACKS_READY_FOR_VALIDATION_PACKET;

        assertTrue(Player.isPreLoginVerifiedPacketAllowed(SessionLoginPhase.RESOURCE_PACK, packetId));
        assertTrue(Player.isPreLoginPacketAllowed(SessionLoginPhase.RESOURCE_PACK, packetId));
    }

    @Test
    void tenMiBTransferUsesBdsSizedChunksInsteadOfARequestBurst() {
        int packSize = 10 * 1024 * 1024;
        int chunkCount = (packSize + Player.RESOURCE_PACK_CHUNK_SIZE - 1) / Player.RESOURCE_PACK_CHUNK_SIZE;

        assertEquals(103, chunkCount);
    }

    @Test
    void resourcePackIdentitySuffixStartsAtProtocol281() {
        UUID packId = UUID.fromString("12345678-1234-1234-1234-123456789abc");

        assertPackIdentityBoundary(createChunkRequest(packId));
        assertPackIdentityBoundary(createChunkData(packId));
        assertPackIdentityBoundary(createDataInfo(packId));
    }

    @Test
    void pendingResourcePackAllowsOutOfOrderAndRepeatedRequests() {
        Player.PendingResourcePack pending = new Player.PendingResourcePack(mock(ResourcePack.class));

        pending.request(2);
        assertEquals(2, pending.pollChunkIndex());
        assertTrue(pending.isEmpty());

        pending.request(2);
        pending.request(0);
        assertEquals(0, pending.pollChunkIndex());
        assertEquals(2, pending.pollChunkIndex());
        assertTrue(pending.isEmpty());
    }

    private static void assertPackIdentityBoundary(cn.nukkit.network.protocol.DataPacket packet) {
        String uuid = "12345678-1234-1234-1234-123456789abc";

        packet.protocol = ProtocolInfo.v1_5_0;
        packet.encode();
        assertEquals(uuid, readPackIdentity(packet));

        packet.protocol = ProtocolInfo.v1_6_0_5;
        packet.encode();
        assertEquals(uuid + "_1.2.3", readPackIdentity(packet));
    }

    private static String readPackIdentity(cn.nukkit.network.protocol.DataPacket packet) {
        BinaryStream stream = new BinaryStream(packet.getBuffer());
        if (packet.protocol <= 274) {
            stream.getByte();
            stream.getShort();
        } else {
            stream.getUnsignedVarInt();
        }
        return stream.getString();
    }

    private static ResourcePackChunkRequestPacket createChunkRequest(UUID packId) {
        ResourcePackChunkRequestPacket packet = new ResourcePackChunkRequestPacket();
        packet.packId = packId;
        packet.packVersion = "1.2.3";
        return packet;
    }

    private static ResourcePackChunkDataPacket createChunkData(UUID packId) {
        ResourcePackChunkDataPacket packet = new ResourcePackChunkDataPacket();
        packet.packId = packId;
        packet.packVersion = "1.2.3";
        packet.data = new byte[0];
        return packet;
    }

    private static ResourcePackDataInfoPacket createDataInfo(UUID packId) {
        ResourcePackDataInfoPacket packet = new ResourcePackDataInfoPacket();
        packet.packId = packId;
        packet.packVersion = "1.2.3";
        packet.sha256 = new byte[0];
        return packet;
    }
}
