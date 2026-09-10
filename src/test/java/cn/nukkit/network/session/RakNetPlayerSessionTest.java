package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.ClientToServerHandshakePacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.RequestNetworkSettingsPacket;
import cn.nukkit.network.protocol.ResourcePackChunkRequestPacket;
import cn.nukkit.network.proxy.ProxyProtocolHandler;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.concurrent.ScheduledFuture;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.packet.RakMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static cn.nukkit.network.session.NetworkPlayerSession.ImmediatePacketMode.DIRECT_WRITE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RakNetPlayerSessionTest {

    private static final int RAKNET_PROTOCOL = 11;
    private static Network network;

    @BeforeAll
    static void setUp() {
        MockServer.init();
        network = new Network(MockServer.get());
    }

    @BeforeEach
    void resetServerState() {
        MockServer.reset();
        MockServer.get().networkLoginTimeoutMilliseconds = 0;
        MockServer.get().netEaseMode = false;
    }

    @Test
    void decodeInboundPrefixedBatchFallsBackToLegacyWhenPrefixCollides() {
        byte[] legacyPacketBuffer = createLegacyHandshakeBatch();

        RakNetPlayerSession.InboundBatchDecodeResult result = RakNetPlayerSession.decodeInboundPrefixedBatch(
                network,
                legacyPacketBuffer,
                CompressionProvider.NONE,
                true,
                RAKNET_PROTOCOL,
                null
        );

        assertTrue(result.success());
        assertFalse(result.prefixed());
        assertSame(CompressionProvider.NONE, result.compression());
        assertEquals(1, result.packets().size());
        assertInstanceOf(ClientToServerHandshakePacket.class, result.packets().get(0));
        assertTrue(result.workBytes() > legacyPacketBuffer.length,
                "the failed prefixed attempt must also consume the decode budget");
        assertTrue(result.framedPackets() > result.packets().size());
    }

    @Test
    void decodeInboundPrefixedBatchKeepsPrefixedSnappyPackets() throws Exception {
        byte[] legacyPacketBuffer = createLegacyHandshakeBatch();
        byte[] compressed = CompressionProvider.SNAPPY.compress(new BinaryStream(legacyPacketBuffer), 7);
        byte[] prefixedPacketBuffer = new byte[compressed.length + 1];
        prefixedPacketBuffer[0] = CompressionProvider.SNAPPY.getPrefix();
        System.arraycopy(compressed, 0, prefixedPacketBuffer, 1, compressed.length);

        RakNetPlayerSession.InboundBatchDecodeResult result = RakNetPlayerSession.decodeInboundPrefixedBatch(
                network,
                prefixedPacketBuffer,
                CompressionProvider.NONE,
                true,
                RAKNET_PROTOCOL,
                null
        );

        assertTrue(result.success());
        assertTrue(result.prefixed());
        assertSame(CompressionProvider.SNAPPY, result.compression());
        assertEquals(1, result.packets().size());
        assertInstanceOf(ClientToServerHandshakePacket.class, result.packets().get(0));
    }

    @Test
    void decodeInboundPrefixedBatchAcceptsResourcePackRequestBurst() throws Exception {
        Player player = mock(Player.class);
        player.protocol = GameVersion.V1_21_130.getProtocol();
        when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);

        RakNetPlayerSession.InboundBatchDecodeResult result = RakNetPlayerSession.decodeInboundPrefixedBatch(
                network,
                createPrefixedResourcePackRequestBatch(1300),
                CompressionProvider.NONE,
                true,
                RAKNET_PROTOCOL,
                player
        );

        assertTrue(result.success());
        assertTrue(result.prefixed());
        assertEquals(1300, result.packets().size());
        assertTrue(result.packets().stream().allMatch(ResourcePackChunkRequestPacket.class::isInstance));
    }

    @Test
    void firstLoginBatchUsesPlayerProtocolWhenGameVersionIsNotNegotiated() {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        player.protocol = GameVersion.V1_21_130.getProtocol();
        assertNull(player.getGameVersion());

        List<DataPacket> packets = new ArrayList<>();
        boolean success = network.processBatchQuietly(
                createRequestNetworkSettingsBatch(GameVersion.V1_21_130.getProtocol()),
                packets,
                CompressionProvider.NONE,
                RAKNET_PROTOCOL,
                player
        );

        assertTrue(success);
        assertEquals(1, packets.size());
        RequestNetworkSettingsPacket packet = assertInstanceOf(RequestNetworkSettingsPacket.class, packets.get(0));
        assertEquals(GameVersion.V1_21_130.getProtocol(), packet.protocol);
        assertEquals(GameVersion.V1_21_130, packet.gameVersion);
        assertEquals(GameVersion.V1_21_130.getProtocol(), packet.protocolVersion);
        assertEquals(GameVersion.V1_21_130, player.getGameVersion());
    }

    @Test
    void firstLoginBatchUsesLastVersionWhenPlayerProtocolIsNotNegotiated() {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        player.protocol = Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, player.protocol);
        assertNull(player.getGameVersion());

        List<DataPacket> packets = new ArrayList<>();
        boolean success = network.processBatchQuietly(
                createRequestNetworkSettingsBatch(GameVersion.V1_21_130.getProtocol()),
                packets,
                CompressionProvider.NONE,
                RAKNET_PROTOCOL,
                player
        );

        assertTrue(success);
        assertEquals(1, packets.size());
        RequestNetworkSettingsPacket packet = assertInstanceOf(RequestNetworkSettingsPacket.class, packets.get(0));
        assertEquals(GameVersion.getLastVersion().getProtocol(), packet.protocol);
        assertEquals(GameVersion.getLastVersion(), packet.gameVersion);
        assertEquals(GameVersion.V1_21_130.getProtocol(), packet.protocolVersion);
        assertEquals(GameVersion.getLastVersion(), player.getGameVersion());
    }

    @Test
    void loginTimeoutOnlyAppliesBeforeLoggedIn() {
        long now = System.nanoTime();
        assertTrue(RakNetPlayerSession.isLoginPhaseTimedOut(SessionLoginPhase.CONNECTED,
                now - TimeUnit.MILLISECONDS.toNanos(200), now, 100));
        assertFalse(RakNetPlayerSession.isLoginPhaseTimedOut(SessionLoginPhase.LOGGED_IN,
                now - TimeUnit.MILLISECONDS.toNanos(200), now, 100));
    }

    @Test
    void pendingLoginTimeoutUsesAcceptedTimeBeforeChannelActivation() {
        long now = System.nanoTime();

        assertTrue(RakNetPlayerSession.isPendingLoginTimedOut(
                false,
                SessionLoginPhase.CONNECTED,
                now,
                now - TimeUnit.MILLISECONDS.toNanos(200),
                now,
                100
        ));
    }

    @Test
    void pendingLoginTimeoutUsesLoginActivityAfterChannelActivation() {
        long now = System.nanoTime();

        assertFalse(RakNetPlayerSession.isPendingLoginTimedOut(
                true,
                SessionLoginPhase.CONNECTED,
                now - TimeUnit.MILLISECONDS.toNanos(50),
                now - TimeUnit.MILLISECONDS.toNanos(200),
                now,
                100
        ));
    }

    @Test
    void expireLoginSessionsDisconnectsTimedOutInactivePendingSession() throws Exception {
        MockServer.get().networkLoginTimeoutMilliseconds = 100;

        Queue<RakNetPlayerSession> sessionCreationQueue = new ArrayDeque<>();
        Set<RakNetPlayerSession> pendingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
        RakNetInterface interfaceUnderTest = createRakNetInterface(MockServer.get(), sessionCreationQueue, pendingSessions);

        SessionFixture fixture = createSession(mock(RakNetInterface.class, RETURNS_DEEP_STUBS), false, false, false);
        fixture.session.getState().getConnection()
                .setChildChannelAcceptedNanos(System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(200));
        pendingSessions.add(fixture.session);

        invokeExpireLoginSessions(interfaceUnderTest);

        assertEquals("disconnectionScreen.timeout", fixture.session.getDisconnectReason());
        assertFalse(pendingSessions.contains(fixture.session));
        verify(fixture.tickFuture).cancel(false);
    }

    @Test
    void expireLoginSessionsClearsProxyProtocolMappingForTimedOutPendingSession() throws Exception {
        MockServer.get().networkLoginTimeoutMilliseconds = 100;

        Queue<RakNetPlayerSession> sessionCreationQueue = new ArrayDeque<>();
        Set<RakNetPlayerSession> pendingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
        RakNetInterface interfaceUnderTest = createRakNetInterface(MockServer.get(), sessionCreationQueue, pendingSessions);

        Channel parentChannel = mock(Channel.class);
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        ProxyProtocolHandler proxyProtocolHandler = mock(ProxyProtocolHandler.class);
        when(parentChannel.pipeline()).thenReturn(pipeline);
        when(pipeline.get(ProxyProtocolHandler.class)).thenReturn(proxyProtocolHandler);
        setField(interfaceUnderTest, "channel", parentChannel);

        InetSocketAddress realAddress = new InetSocketAddress("127.0.0.1", 19132);
        SessionFixture fixture = createSession(interfaceUnderTest, false, false, false);
        fixture.session.getState().getConnection()
                .setChildChannelAcceptedNanos(System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(200));
        pendingSessions.add(fixture.session);

        invokeExpireLoginSessions(interfaceUnderTest);

        assertFalse(pendingSessions.contains(fixture.session));
        verify(proxyProtocolHandler).clearMappingByRealAddress(realAddress);
    }

    @Test
    void channelActiveQueuesPlayerCreationOnlyOnce() throws Exception {
        Queue<RakNetPlayerSession> sessionCreationQueue = new ArrayDeque<>();
        Set<RakNetPlayerSession> pendingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
        RakNetInterface interfaceUnderTest = createRakNetInterface(MockServer.get(), sessionCreationQueue, pendingSessions);

        SessionFixture fixture = createSession(interfaceUnderTest, false, false, false);
        pendingSessions.add(fixture.session);

        ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        when(context.fireChannelActive()).thenReturn(context);

        fixture.session.channelActive(context);
        fixture.session.channelActive(context);

        assertTrue(fixture.session.getState().getConnection().isQueuedForPlayerCreation());
        assertEquals(1, sessionCreationQueue.size());
        assertSame(fixture.session, sessionCreationQueue.peek());
        assertEquals(1, pendingSessions.size());
    }

    @Test
    void processClearsProxyProtocolMappingWhenDisconnectedSessionIsRemoved() throws Exception {
        Queue<RakNetPlayerSession> sessionCreationQueue = new ArrayDeque<>();
        Set<RakNetPlayerSession> pendingSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Map<InetSocketAddress, RakNetPlayerSession> sessions = new HashMap<>();
        RakNetInterface interfaceUnderTest = createRakNetInterface(MockServer.get(), sessionCreationQueue, pendingSessions, sessions);

        Channel parentChannel = mock(Channel.class);
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        ProxyProtocolHandler proxyProtocolHandler = mock(ProxyProtocolHandler.class);
        when(parentChannel.pipeline()).thenReturn(pipeline);
        when(pipeline.get(ProxyProtocolHandler.class)).thenReturn(proxyProtocolHandler);
        setField(interfaceUnderTest, "channel", parentChannel);

        InetSocketAddress realAddress = new InetSocketAddress("127.0.0.1", 19132);
        SessionFixture fixture = createSession(interfaceUnderTest, false, true, true);
        sessions.put(realAddress, fixture.session);
        fixture.session.disconnect("Closed");

        interfaceUnderTest.process();

        assertTrue(sessions.isEmpty());
        verify(proxyProtocolHandler).clearMappingByRealAddress(realAddress);
    }

    @Test
    void malformedPreLoginPacketsDoNotTriggerIpBlock() throws Exception {
        InetAddress address = InetAddress.getByName("1.2.3.4");
        assertFalse(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.CONNECTED, address));
        assertFalse(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.NETWORK_SETTINGS_NEGOTIATED, address));
        assertTrue(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.LOGIN_RECEIVED, address));
        assertTrue(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.RESOURCE_PACK, address));
        assertFalse(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.LOGGED_IN, address));
        assertFalse(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.DISCONNECTED, address));
    }

    @Test
    void serverTickDefersInboundTailWithoutDroppingPackets() {
        SessionFixture fixture = createSession(false);
        int burst = RakNetPlayerSession.MAX_INBOUND_PACKETS_PER_SERVER_TICK + 44;
        assertTrue(fixture.session.enqueueDecodedBatch(testPackets(burst)));

        drainServerTick(fixture.session);

        verify(fixture.player, times(RakNetPlayerSession.MAX_INBOUND_PACKETS_PER_SERVER_TICK))
                .handleDataPacket(any(DataPacket.class));
        assertEquals(44, fixture.session.queuedInboundPacketCount());

        drainServerTick(fixture.session);

        verify(fixture.player, times(burst)).handleDataPacket(any(DataPacket.class));
        assertEquals(0, fixture.session.queuedInboundPacketCount());
    }

    @Test
    void inboundQueueHasAHardPerSessionBound() {
        SessionFixture fixture = createSession(false);

        assertTrue(fixture.session.enqueueDecodedBatch(
                testPackets(RakNetPlayerSession.MAX_QUEUED_INBOUND_PACKETS)));
        assertFalse(fixture.session.enqueueDecodedBatch(List.of(new TestPacket())));

        assertEquals(RakNetPlayerSession.MAX_QUEUED_INBOUND_PACKETS,
                fixture.session.queuedInboundPacketCount());
        assertTrue(fixture.session.isInboundThrottled());
        assertNull(fixture.session.getDisconnectReason());
    }

    @Test
    void inboundBackpressureClearsAtLowWater() {
        SessionFixture fixture = createSession(false);
        assertTrue(fixture.session.enqueueDecodedBatch(
                testPackets(RakNetPlayerSession.MAX_QUEUED_INBOUND_PACKETS)));

        while (fixture.session.queuedInboundPacketCount() > RakNetPlayerSession.MAX_QUEUED_INBOUND_PACKETS / 2) {
            drainServerTick(fixture.session);
        }

        assertFalse(fixture.session.isInboundThrottled());
        assertTrue(fixture.session.enqueueDecodedBatch(List.of(new TestPacket())));
        assertNull(fixture.session.getDisconnectReason());
    }

    @Test
    void inboundQueueAlsoHasAByteBound() {
        SessionFixture fixture = createSession(false);
        TestPacket fullBatch = new TestPacket();
        fullBatch.setBuffer(new byte[(int) RakNetPlayerSession.MAX_QUEUED_INBOUND_BYTES]);

        assertTrue(fixture.session.enqueueDecodedBatch(List.of(fullBatch)));
        assertFalse(fixture.session.enqueueDecodedBatch(List.of(new TestPacket())));

        assertEquals(RakNetPlayerSession.MAX_QUEUED_INBOUND_BYTES,
                fixture.session.queuedInboundByteCount());
        assertTrue(fixture.session.isInboundThrottled());
        assertNull(fixture.session.getDisconnectReason());
    }

    @Test
    void ingressBudgetChargesDecodeWorkEvenWhenNothingCanBeQueued() {
        SessionFixture fixture = createSession(false);
        long now = System.nanoTime();
        int worstDecode = 6 * 1024 * 1024;

        for (int i = 0; i < 3; i++) {
            assertTrue(fixture.session.reserveWireIngressBudget(64, now));
            assertTrue(fixture.session.reserveDecodeIngressBudget(now));
            fixture.session.settleIngressBudget(worstDecode, 1300, now);
        }

        assertTrue(fixture.session.reserveWireIngressBudget(64, now));
        assertFalse(fixture.session.reserveDecodeIngressBudget(now));
        assertNull(fixture.session.getDisconnectReason());
    }

    @Test
    void ingressBudgetAllowsNormalSmallBatchesButBoundsSameInstantBurst() {
        SessionFixture fixture = createSession(false);
        long now = System.nanoTime();

        for (int i = 0; i < 1200; i++) {
            assertTrue(fixture.session.reserveWireIngressBudget(1, now));
            assertTrue(fixture.session.reserveDecodeIngressBudget(now));
            fixture.session.settleIngressBudget(1, 1, now);
        }

        assertFalse(fixture.session.reserveWireIngressBudget(1, now));
    }

    @Test
    void measuredDecodeCountsUnknownFrames() {
        BinaryStream batch = new BinaryStream();
        BinaryStream unknownPacket = new BinaryStream();
        unknownPacket.putUnsignedVarInt(0x3ff);
        for (int i = 0; i < 1300; i++) {
            batch.putByteArray(unknownPacket.getBuffer());
        }
        List<DataPacket> decoded = new ArrayList<>();

        Network.BatchProcessResult result = network.processBatchMeasured(
                batch.getBuffer(), decoded, CompressionProvider.NONE, RAKNET_PROTOCOL, null, false);

        assertTrue(result.success());
        assertEquals(1300, result.framedPackets());
        assertTrue(decoded.isEmpty());
    }

    @Test
    void interfaceBudgetRotatesPastSessionsThatUsedThePreviousTick() throws Exception {
        Queue<RakNetPlayerSession> creationQueue = new ArrayDeque<>();
        Set<RakNetPlayerSession> pending = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Map<InetSocketAddress, RakNetPlayerSession> sessions = new LinkedHashMap<>();
        RakNetInterface interfaceUnderTest = createRakNetInterface(
                MockServer.get(), creationQueue, pending, sessions);
        List<SessionFixture> fixtures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            SessionFixture fixture = createSession(interfaceUnderTest, false, true, true);
            fixture.session.enqueueDecodedBatch(testPackets(1300));
            fixtures.add(fixture);
            sessions.put(new InetSocketAddress("127.0.0.1", 20000 + i), fixture.session);
        }

        interfaceUnderTest.process();

        int queuedAfterFirstTick = fixtures.stream()
                .mapToInt(fixture -> fixture.session.queuedInboundPacketCount())
                .sum();
        assertEquals(20 * 1300 - 2048, queuedAfterFirstTick);
        assertEquals(1300, fixtures.get(19).session.queuedInboundPacketCount());

        interfaceUnderTest.process();

        assertTrue(fixtures.get(19).session.queuedInboundPacketCount() < 1300,
                "the round-robin cursor must serve a session skipped by the previous global budget");
    }

    @Test
    void loggedInMalformedBudgetIsSlidingAndSuccessfulTrafficDoesNotClearIt() {
        SessionFixture fixture = createSession(false);
        fixture.session.getState().getLogin().setPhase(SessionLoginPhase.LOGGED_IN);
        long now = TimeUnit.SECONDS.toNanos(100);

        for (int i = 0; i < 32; i++) {
            assertTrue(fixture.session.keepPlayingSessionAfterMalformedBatch(now));
        }
        assertTrue(fixture.session.enqueueDecodedBatch(List.of(new TestPacket())));

        assertFalse(fixture.session.keepPlayingSessionAfterMalformedBatch(now));
    }

    @Test
    void expiredMalformedBatchesFreeTheBudget() {
        SessionFixture fixture = createSession(false);
        fixture.session.getState().getLogin().setPhase(SessionLoginPhase.LOGGED_IN);
        long now = TimeUnit.SECONDS.toNanos(100);
        for (int i = 0; i < 32; i++) {
            assertTrue(fixture.session.keepPlayingSessionAfterMalformedBatch(now));
        }

        assertTrue(fixture.session.keepPlayingSessionAfterMalformedBatch(
                now + RakNetPlayerSession.MALFORMED_BATCH_WINDOW_NANOS + 1));
    }

    @Test
    void malformedBatchBeforeLoginStillFailsClosed() {
        SessionFixture fixture = createSession(false);
        fixture.session.getState().getLogin().setPhase(SessionLoginPhase.RESOURCE_PACK);

        assertFalse(fixture.session.keepPlayingSessionAfterMalformedBatch(1));
    }

    @Test
    void delayedMalformedLoginCloseUsesOriginalPhaseAndAddress() throws Exception {
        assertMalformedLoginPolicy(SessionLoginPhase.RESOURCE_PACK, true);
    }

    @Test
    void delayedMalformedNegotiationCloseDoesNotBlockAnAddress() throws Exception {
        assertMalformedLoginPolicy(SessionLoginPhase.NETWORK_SETTINGS_NEGOTIATED, false);
    }

    private static void assertMalformedLoginPolicy(SessionLoginPhase phase, boolean expectedBlock) throws Exception {
        RakNetInterface networkInterface = mock(RakNetInterface.class, RETURNS_DEEP_STUBS);
        when(networkInterface.getNetwork()).thenReturn(network);
        SessionFixture fixture = createSession(networkInterface, false, true, true);
        fixture.session.getState().getLogin().setPhase(phase);
        InetAddress originalAddress = InetAddress.getByName("192.0.2.23");
        when(fixture.channel.remoteAddress()).thenReturn(new InetSocketAddress(originalAddress, 20000));
        ServerScheduler scheduler = mock(ServerScheduler.class);
        when(MockServer.get().getScheduler()).thenReturn(scheduler);

        receiveBatch(fixture.session, new byte[]{(byte) 0x80});

        assertEquals(SessionLoginPhase.DISCONNECTED, fixture.session.getState().getLogin().getPhase());
        assertEquals("Sent malformed packet", fixture.session.getDisconnectReason());
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), callback.capture(), eq(10));
        when(fixture.channel.remoteAddress()).thenThrow(new IllegalStateException("channel already closed"));

        callback.getValue().run();

        verify(fixture.channel).close();
        verify(networkInterface, times(expectedBlock ? 1 : 0)).blockAddress(originalAddress, 60);
        verify(fixture.channel, never()).unsafe();
    }

    @Test
    void encryptedSoftThrottleKeepsTheNextPacketDecryptable() throws Exception {
        for (boolean queueThrottle : new boolean[]{true, false}) {
            RakNetInterface networkInterface = mock(RakNetInterface.class, RETURNS_DEEP_STUBS);
            when(networkInterface.getNetwork()).thenReturn(network);
            SessionFixture fixture = createSession(networkInterface, false, true, true);
            fixture.session.getState().getLogin().setPhase(SessionLoginPhase.LOGGED_IN);
            fixture.session.setCompression(CompressionProvider.NONE);
            SecretKey key = new SecretKeySpec(new byte[32], "AES");
            Cipher clientCipher = EncryptionUtils.createCipher(true, true, key);
            fixture.session.setEncryption(key, EncryptionUtils.createCipher(true, true, key),
                    EncryptionUtils.createCipher(true, false, key));
            if (queueThrottle) {
                setField(fixture.session, "inboundThrottled", true);
            } else {
                setField(fixture.session, "ingressDecodeBytes", 0D);
                setField(fixture.session, "lastIngressRefillNanos", Long.MAX_VALUE);
            }
            byte[] payload = {(byte) 0xff, 0x01, ClientToServerHandshakePacket.NETWORK_ID};

            receiveBatch(fixture.session, encryptBatch(clientCipher, key, 0, payload));

            assertEquals(0, fixture.session.queuedInboundPacketCount());
            assertNull(fixture.session.getDisconnectReason());
            setField(fixture.session, "inboundThrottled", false);
            setField(fixture.session, "ingressDecodeBytes", 24D * 1024 * 1024);

            receiveBatch(fixture.session, encryptBatch(clientCipher, key, 1, payload));

            assertEquals(1, fixture.session.queuedInboundPacketCount(),
                    "soft backpressure must advance both the cipher stream and checksum counter");
            assertNull(fixture.session.getDisconnectReason());
            verify(fixture.channel, never()).close();
        }
    }

    @Test
    void wireThrottleClosesBeforeDecrypting() throws Exception {
        SessionFixture fixture = createSession(false);
        Cipher cipher = mock(Cipher.class);
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        fixture.session.setEncryption(key, cipher, cipher);
        setField(fixture.session, "ingressBatchTokens", 0D);
        setField(fixture.session, "lastIngressRefillNanos", Long.MAX_VALUE);

        receiveBatch(fixture.session, new byte[]{1, 2, 3});

        assertEquals("Too much inbound data", fixture.session.getDisconnectReason());
        verify(fixture.channel).close();
        verifyNoInteractions(cipher);
    }

    @Test
    void oversizedWirePacketClosesWithSizeCapReason() throws Exception {
        SessionFixture fixture = createSession(false);

        receiveBatch(fixture.session, new byte[RakNetPlayerSession.MAX_INBOUND_WIRE_BYTES + 1]);

        assertEquals("Too big packet", fixture.session.getDisconnectReason());
        verify(fixture.channel).close();
    }

    @Test
    void malformedBatchesAboveWindowDisconnectPlayingSession() throws Exception {
        RakNetInterface networkInterface = mock(RakNetInterface.class, RETURNS_DEEP_STUBS);
        when(networkInterface.getNetwork()).thenReturn(network);
        SessionFixture fixture = createSession(networkInterface, false, true, true);
        fixture.session.getState().getLogin().setPhase(SessionLoginPhase.LOGGED_IN);

        for (int i = 0; i < RakNetPlayerSession.MAX_MALFORMED_BATCHES_WHILE_PLAYING; i++) {
            receiveBatch(fixture.session, new byte[]{(byte) 0x80});
            assertNull(fixture.session.getDisconnectReason());
        }

        receiveBatch(fixture.session, new byte[]{(byte) 0x80});

        assertEquals("Sent malformed packet", fixture.session.getDisconnectReason());
    }

    @Test
    void malformedSecondFrameDoesNotEnqueueTheValidFirstFrame() throws Exception {
        RakNetInterface networkInterface = mock(RakNetInterface.class, RETURNS_DEEP_STUBS);
        when(networkInterface.getNetwork()).thenReturn(network);
        SessionFixture fixture = createSession(networkInterface, false, true, true);
        fixture.session.getState().getLogin().setPhase(SessionLoginPhase.LOGGED_IN);
        BinaryStream batch = new BinaryStream();
        batch.putByteArray(new byte[]{ClientToServerHandshakePacket.NETWORK_ID});
        batch.putByteArray(new byte[]{(byte) 0x80});
        List<DataPacket> prefix = new ArrayList<>();
        Network.BatchProcessResult result = network.processBatchMeasured(
                batch.getBuffer(), prefix, CompressionProvider.NONE, RAKNET_PROTOCOL, fixture.player, false);
        assertFalse(result.success());
        assertEquals(2, result.framedPackets());
        assertEquals(batch.getCount(), result.decompressedBytes());
        assertEquals(1, prefix.size(), "the first frame really did decode before the failure");

        receiveBatch(fixture.session, batch.getBuffer());

        assertEquals(0, fixture.session.queuedInboundPacketCount());
        assertEquals(0, fixture.session.queuedInboundByteCount());
        assertNull(fixture.session.getDisconnectReason());
    }

    @Test
    void uncompressedBatchesCannotBypassTheDecodedByteLimit() {
        byte[] oversized = new byte[3 * 1024 * 1024 + 1];
        List<DataPacket> decoded = new ArrayList<>();

        Network.BatchProcessResult result = network.processBatchMeasured(
                oversized, decoded, CompressionProvider.NONE, RAKNET_PROTOCOL, null, false);

        assertFalse(result.success());
        assertEquals(oversized.length, result.decompressedBytes());
        assertEquals(0, result.framedPackets());
        assertTrue(decoded.isEmpty());
    }

    @Test
    void globalByteBudgetEventuallyServesALargeHeadPacket() throws Exception {
        Map<InetSocketAddress, RakNetPlayerSession> sessions = new LinkedHashMap<>();
        RakNetInterface networkInterface = createRakNetInterface(MockServer.get(), new ArrayDeque<>(),
                Collections.newSetFromMap(new ConcurrentHashMap<>()), sessions);
        List<SessionFixture> fixtures = new ArrayList<>();
        int[] sizes = {3 * 1024 * 1024, 4 * 1024 * 1024, 1024 * 1024};
        for (int i = 0; i < sizes.length; i++) {
            SessionFixture fixture = createSession(networkInterface, false, true, true);
            TestPacket packet = new TestPacket();
            packet.setBuffer(new byte[sizes[i]]);
            assertTrue(fixture.session.enqueueDecodedBatch(List.of(packet)));
            fixtures.add(fixture);
            sessions.put(new InetSocketAddress("127.0.0.1", 20000 + i), fixture.session);
        }

        networkInterface.process();

        assertEquals(0, fixtures.get(0).session.queuedInboundPacketCount());
        assertEquals(1, fixtures.get(1).session.queuedInboundPacketCount());
        assertEquals(0, fixtures.get(2).session.queuedInboundPacketCount());
        networkInterface.process();
        assertEquals(0, fixtures.get(1).session.queuedInboundPacketCount(),
                "the next round must give a large head packet the full global byte budget");
    }

    private static void drainServerTick(RakNetPlayerSession session) {
        session.serverTick(RakNetPlayerSession.MAX_INBOUND_PACKETS_PER_SERVER_TICK,
                RakNetPlayerSession.MAX_INBOUND_BYTES_PER_SERVER_TICK);
    }

    private static void receiveBatch(RakNetPlayerSession session, byte[] payload) throws Exception {
        RakMessage message = new RakMessage(Unpooled.buffer(payload.length + 1).writeByte(0xfe).writeBytes(payload));
        try {
            session.channelRead0(mock(ChannelHandlerContext.class), message);
        } finally {
            message.release();
        }
    }

    private static byte[] encryptBatch(Cipher cipher, SecretKey key, long counter, byte[] payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array());
        digest.update(payload);
        digest.update(key.getEncoded());
        byte[] plaintext = Arrays.copyOf(payload, payload.length + 8);
        System.arraycopy(digest.digest(), 0, plaintext, payload.length, 8);
        return cipher.update(plaintext);
    }

    @Test
    void queuedPacketIsEncodedDuringNetworkTick() throws Exception {
        SessionFixture fixture = createSession(false);

        TestPacket packet = new TestPacket();
        packet.protocol = GameVersion.V1_21_130.getProtocol();
        packet.gameVersion = GameVersion.V1_21_130;

        fixture.session.sendPacket(packet);

        assertFalse(packet.isEncoded, "queued packets should not be encoded on the caller thread");
        verify(fixture.channel, never()).writeAndFlush(any(ByteBuf.class));

        invokeNetworkTick(fixture.session);

        assertTrue(packet.isEncoded, "queued packets should be encoded during networkTick");
        verify(fixture.channel).writeAndFlush(any(ByteBuf.class));
    }

    @Test
    void directWriteStillEncodesImmediatelyOnEventLoop() {
        SessionFixture fixture = createSession(true);

        TestPacket packet = new TestPacket();
        packet.protocol = GameVersion.V1_21_130.getProtocol();
        packet.gameVersion = GameVersion.V1_21_130;

        fixture.session.sendImmediatePacket(packet, () -> {
        }, DIRECT_WRITE);

        assertTrue(packet.isEncoded, "direct-write packets should still be encoded before the immediate write");
        verify(fixture.channel).writeAndFlush(any(ByteBuf.class));
    }

    private static SessionFixture createSession(boolean executeImmediately) {
        return createSession(mock(RakNetInterface.class, RETURNS_DEEP_STUBS), executeImmediately, true, true);
    }

    private static SessionFixture createSession(RakNetInterface server, boolean executeImmediately, boolean channelActive) {
        return createSession(server, executeImmediately, channelActive, true);
    }

    private static SessionFixture createSession(RakNetInterface server, boolean executeImmediately, boolean channelActive, boolean bindPlayer) {
        EventLoop eventLoop = mock(EventLoop.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(eventLoop)
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        doReturn(scheduledFuture).when(eventLoop)
                .schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        if (executeImmediately) {
            doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(eventLoop).execute(any(Runnable.class));
        }

        ChannelFuture channelFuture = mock(ChannelFuture.class);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);
        RakChildChannel channel = mock(RakChildChannel.class, RETURNS_DEEP_STUBS);
        when(channel.eventLoop()).thenReturn(eventLoop);
        when(channel.config().getProtocolVersion()).thenReturn(RAKNET_PROTOCOL);
        when(channel.isActive()).thenReturn(channelActive);
        when(channel.isOpen()).thenReturn(true);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 19132));
        when(channel.writeAndFlush(any(ByteBuf.class))).thenReturn(channelFuture);

        RakNetPlayerSession session = new RakNetPlayerSession(server, channel);

        if (bindPlayer) {
            Player player = mock(Player.class);
            player.protocol = GameVersion.V1_21_130.getProtocol();
            when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
            when(player.shouldLogin()).thenReturn(false);
            session.setPlayer(player);
        }
        return new SessionFixture(session, channel, eventLoop, scheduledFuture, session.getPlayer());
    }

    private static void invokeNetworkTick(RakNetPlayerSession session) throws Exception {
        Method method = RakNetPlayerSession.class.getDeclaredMethod("networkTick");
        method.setAccessible(true);
        method.invoke(session);
    }

    private static RakNetInterface createRakNetInterface(cn.nukkit.Server server, Queue<RakNetPlayerSession> sessionCreationQueue,
                                                         Set<RakNetPlayerSession> pendingSessions) throws Exception {
        return createRakNetInterface(server, sessionCreationQueue, pendingSessions, new HashMap<InetSocketAddress, RakNetPlayerSession>());
    }

    private static RakNetInterface createRakNetInterface(cn.nukkit.Server server, Queue<RakNetPlayerSession> sessionCreationQueue,
                                                         Set<RakNetPlayerSession> pendingSessions,
                                                         Map<InetSocketAddress, RakNetPlayerSession> sessions) throws Exception {
        RakNetInterface interfaceUnderTest = mock(RakNetInterface.class, CALLS_REAL_METHODS);
        setField(interfaceUnderTest, "server", server);
        setField(interfaceUnderTest, "sessionCreationQueue", sessionCreationQueue);
        setField(interfaceUnderTest, "pendingSessions", pendingSessions);
        setField(interfaceUnderTest, "sessions", sessions);
        return interfaceUnderTest;
    }

    private static void invokeExpireLoginSessions(RakNetInterface interfaceUnderTest) throws Exception {
        Method method = RakNetInterface.class.getDeclaredMethod("expireLoginSessions");
        method.setAccessible(true);
        method.invoke(interfaceUnderTest);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> owner = target instanceof RakNetPlayerSession ? RakNetPlayerSession.class : RakNetInterface.class;
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static byte[] createLegacyHandshakeBatch() {
        return new byte[]{
                0x01,
                ClientToServerHandshakePacket.NETWORK_ID
        };
    }

    private static byte[] createRequestNetworkSettingsBatch(int protocol) {
        BinaryStream packet = new BinaryStream();
        packet.putUnsignedVarInt(RequestNetworkSettingsPacket.NETWORK_ID & 0xff);
        packet.putInt(protocol);

        BinaryStream batch = new BinaryStream();
        batch.putByteArray(packet.getBuffer());
        return batch.getBuffer();
    }

    private static byte[] createPrefixedResourcePackRequestBatch(int packetCount) throws Exception {
        UUID packId = UUID.fromString("07f9efc2-1a83-4084-88b1-bb20af4a6eb4");
        BinaryStream batch = new BinaryStream();
        for (int i = 0; i < packetCount; i++) {
            ResourcePackChunkRequestPacket request = new ResourcePackChunkRequestPacket();
            request.protocol = GameVersion.V1_21_130.getProtocol();
            request.gameVersion = GameVersion.V1_21_130;
            request.packId = packId;
            request.chunkIndex = i;
            request.encode();
            batch.putByteArray(request.getBuffer());
        }

        byte[] compressed = CompressionProvider.ZLIB_RAW.compress(batch, 7);
        byte[] prefixed = new byte[compressed.length + 1];
        prefixed[0] = CompressionProvider.ZLIB_RAW.getPrefix();
        System.arraycopy(compressed, 0, prefixed, 1, compressed.length);
        return prefixed;
    }

    private static List<DataPacket> testPackets(int count) {
        List<DataPacket> packets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            packets.add(new TestPacket());
        }
        return packets;
    }

    private record SessionFixture(RakNetPlayerSession session, RakChildChannel channel, EventLoop eventLoop,
                                  ScheduledFuture<?> tickFuture, Player player) {
    }

    private static final class TestPacket extends DataPacket {
        @Override
        public byte pid() {
            return 0;
        }

        @Override
        public void decode() {
        }

        @Override
        public void encode() {
            this.reset();
            this.putByte((byte) 0x7f);
        }
    }
}
