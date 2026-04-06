package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.ClientToServerHandshakePacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.session.login.SessionLoginPhase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    }

    @Test
    void decodeInboundPrefixedBatchKeepsPrefixedSnappyPackets() throws Exception {
        byte[] legacyPacketBuffer = createLegacyHandshakeBatch();
        byte[] compressed = CompressionProvider.SNAPPY.compress(new cn.nukkit.utils.BinaryStream(legacyPacketBuffer), 7);
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
    void malformedPreLoginPacketsDoNotTriggerIpBlock() throws Exception {
        InetAddress address = InetAddress.getByName("1.2.3.4");
        assertFalse(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.CONNECTED, address));
        assertFalse(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.NETWORK_SETTINGS_NEGOTIATED, address));
        assertTrue(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.LOGIN_RECEIVED, address));
        assertTrue(RakNetPlayerSession.shouldBlockAddressAfterMalformed(SessionLoginPhase.LOGGED_IN, address));
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
        return new SessionFixture(session, channel, eventLoop, scheduledFuture);
    }

    private static void invokeNetworkTick(RakNetPlayerSession session) throws Exception {
        Method method = RakNetPlayerSession.class.getDeclaredMethod("networkTick");
        method.setAccessible(true);
        method.invoke(session);
    }

    private static RakNetInterface createRakNetInterface(cn.nukkit.Server server, Queue<RakNetPlayerSession> sessionCreationQueue,
                                                         Set<RakNetPlayerSession> pendingSessions) throws Exception {
        RakNetInterface interfaceUnderTest = mock(RakNetInterface.class, CALLS_REAL_METHODS);
        setField(interfaceUnderTest, "server", server);
        setField(interfaceUnderTest, "sessionCreationQueue", sessionCreationQueue);
        setField(interfaceUnderTest, "pendingSessions", pendingSessions);
        setField(interfaceUnderTest, "sessions", new HashMap<InetSocketAddress, RakNetPlayerSession>());
        return interfaceUnderTest;
    }

    private static void invokeExpireLoginSessions(RakNetInterface interfaceUnderTest) throws Exception {
        Method method = RakNetInterface.class.getDeclaredMethod("expireLoginSessions");
        method.setAccessible(true);
        method.invoke(interfaceUnderTest);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = RakNetInterface.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static byte[] createLegacyHandshakeBatch() {
        return new byte[]{
                0x01,
                ClientToServerHandshakePacket.NETWORK_ID
        };
    }

    private record SessionFixture(RakNetPlayerSession session, RakChildChannel channel, EventLoop eventLoop,
                                  ScheduledFuture<?> tickFuture) {
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
