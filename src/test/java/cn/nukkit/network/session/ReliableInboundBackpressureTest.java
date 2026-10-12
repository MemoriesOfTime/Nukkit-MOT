package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.NetherNetInterface;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReliableInboundBackpressureTest {
    private static final GameVersion VERSION = GameVersion.V1_21_130;
    private static Network network;

    @BeforeAll
    static void initialize() {
        MockServer.init();
        network = new Network(MockServer.get());
    }

    @BeforeEach
    void reset() {
        MockServer.reset();
    }

    @Test
    void normalResourcePackBurstKeepsCompletionAndCloseInOrder() throws Exception {
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = fixture(nether);
            receive(fixture, resourceRequests(1300));
            receive(fixture, completionAndClose());
            drainAll(fixture, 25);
            ArgumentCaptor<DataPacket> handled = ArgumentCaptor.forClass(DataPacket.class);
            verify(fixture.player, times(1302)).handleDataPacket(handled.capture());
            List<DataPacket> packets = handled.getAllValues();
            for (int i = 0; i < 1300; i++) {
                assertEquals(i, ((ResourcePackChunkRequestPacket) packets.get(i)).chunkIndex);
            }
            assertEquals(ResourcePackClientResponsePacket.STATUS_COMPLETED,
                    ((ResourcePackClientResponsePacket) packets.get(1300)).responseStatus);
            assertEquals(12, ((ContainerClosePacket) packets.get(1301)).windowId);
            assertNull(reason(fixture));
        }
    }

    @Test
    void decodedOverflowBatchIsRetainedBeforeNewerEncodedTraffic() throws Exception {
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = fixture(nether);
            receive(fixture, resourceRequests(1000));
            receive(fixture, resourceRequests(400));
            receive(fixture, completionAndClose());
            assertNotNull(backlog(fixture).pendingDecoded());
            assertEquals(400, backlog(fixture).pendingDecoded().size());
            assertEquals(1, backlog(fixture).batchCount());
            drainAll(fixture, 30);
            ArgumentCaptor<DataPacket> handled = ArgumentCaptor.forClass(DataPacket.class);
            verify(fixture.player, times(1402)).handleDataPacket(handled.capture());
            assertInstanceOf(ResourcePackClientResponsePacket.class, handled.getAllValues().get(1400));
            assertInstanceOf(ContainerClosePacket.class, handled.getAllValues().get(1401));
            assertNull(backlog(fixture).pendingDecoded());
            assertEquals(0, backlog(fixture).batchCount());
            assertEquals(0, backlog(fixture).byteCount());
        }
    }

    @Test
    void unavailableDecodeBudgetDefersCloseUntilBudgetRecovers() throws Exception {
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = fixture(nether);
            field(fixture.session, "ingressDecodeBytes", 0D);
            field(fixture.session, "lastIngressRefillNanos", Long.MAX_VALUE);
            receive(fixture, completionAndClose());
            tick(fixture);
            verify(fixture.player, never()).handleDataPacket(any());
            assertEquals(1, backlog(fixture).batchCount());
            field(fixture.session, "ingressDecodeBytes", RakNetPlayerSession.MAX_INGRESS_DECODE_BYTES);
            drainAll(fixture, 2);
            verify(fixture.player, times(2)).handleDataPacket(any());
            assertEquals(0, backlog(fixture).batchCount());
            assertNull(reason(fixture));
        }
    }

    @Test
    void nonPrefixedBacklogKeepsTheCompressionUsedAtReceipt() throws Exception {
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = fixture(nether);
            fixture.session.getState().getSecurity().setCompressionInitialized(false);
            field(fixture.session, "compressionInitialized", false);
            field(fixture.session, "ingressDecodeBytes", 0D);
            field(fixture.session, "lastIngressRefillNanos", Long.MAX_VALUE);
            byte[] prefixed = completionAndClose();
            receive(fixture, Arrays.copyOfRange(prefixed, 1, prefixed.length));
            if (fixture.session instanceof RakNetPlayerSession session) session.setCompression(CompressionProvider.ZLIB_RAW);
            else ((NetherNetPlayerSession) fixture.session).setCompression(CompressionProvider.ZLIB_RAW);
            field(fixture.session, "ingressDecodeBytes", RakNetPlayerSession.MAX_INGRESS_DECODE_BYTES);
            drainAll(fixture, 2);
            verify(fixture.player, times(2)).handleDataPacket(any());
            assertNull(reason(fixture));
        }
    }

    @Test
    void encryptedDeferredPayloadIsAuthenticatedOnceAndProcessedOnce() throws Exception {
        Fixture fixture = fixture(false);
        RakNetPlayerSession session = (RakNetPlayerSession) fixture.session;
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        Cipher clientCipher = EncryptionUtils.createCipher(true, true, key);
        session.setEncryption(key, EncryptionUtils.createCipher(true, true, key),
                EncryptionUtils.createCipher(true, false, key));
        field(session, "ingressDecodeBytes", 0D);
        field(session, "lastIngressRefillNanos", Long.MAX_VALUE);
        receive(fixture, encrypted(clientCipher, key, 0, completionAndClose()));
        receive(fixture, encrypted(clientCipher, key, 1, completionAndClose()));
        assertEquals(2, backlog(fixture).batchCount());
        field(session, "ingressDecodeBytes", RakNetPlayerSession.MAX_INGRESS_DECODE_BYTES);
        drainAll(fixture, 3);
        receive(fixture, encrypted(clientCipher, key, 2, completionAndClose()));
        drainAll(fixture, 2);
        verify(fixture.player, times(6)).handleDataPacket(any());
        verify(fixture.channel, never()).close();
        assertNull(reason(fixture));
    }

    @Test
    void fullEncodedBacklogDrainsBeforeRejectingNewTrafficWhenCreditsRecover() throws Exception {
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = fixture(nether);
            field(fixture.session, "ingressDecodeBytes", 0D);
            field(fixture.session, "lastIngressRefillNanos", Long.MAX_VALUE);
            byte[] request = resourceRequests(1);
            for (int i = 0; i < InboundBatchBacklog.MAX_BATCHES; i++) receive(fixture, request);
            assertEquals(InboundBatchBacklog.MAX_BATCHES, backlog(fixture).batchCount());
            // Advance the credit clock without sleeping or changing production limits.
            field(fixture.session, "lastIngressRefillNanos", System.nanoTime() - TimeUnit.SECONDS.toNanos(3));
            receive(fixture, completionAndClose());
            drainAll(fixture, 25);
            verify(fixture.player, times(InboundBatchBacklog.MAX_BATCHES + 2)).handleDataPacket(any());
            verify(fixture.channel, never()).close();
            assertNull(reason(fixture));
        }
    }

    @Test
    void hardBacklogOverflowClosesOnlyThatSessionAndReleasesRetention() throws Exception {
        for (boolean nether : new boolean[]{false, true}) {
            Fixture overloaded = fixture(nether);
            Fixture healthy = fixture(nether);
            InboundBatchBacklog retained = backlog(overloaded);
            // Simulate a stopped consumer without sending traffic to a live endpoint.
            retained.offer(new byte[(int) InboundBatchBacklog.MAX_BYTES], false, CompressionProvider.NONE);
            field(overloaded.session, "inboundThrottled", true);
            receive(overloaded, completionAndClose());
            verify(overloaded.channel).close();
            assertNotNull(reason(overloaded));
            assertEquals(0, retained.byteCount());
            assertEquals(0, retained.batchCount());
            receive(healthy, completionAndClose());
            drainAll(healthy, 2);
            verify(healthy.player, times(2)).handleDataPacket(any());
            verify(healthy.channel, never()).close();
        }
    }

    @Test
    void encodedBacklogHasIndependentByteAndBatchBounds() {
        InboundBatchBacklog retained = new InboundBatchBacklog();
        retained.offer(new byte[(int) InboundBatchBacklog.MAX_BYTES], false, CompressionProvider.NONE);
        assertFalse(retained.canOffer(1));
        assertEquals(InboundBatchBacklog.MAX_BYTES, retained.byteCount());
        assertNotNull(retained.poll());
        byte[] close = new byte[]{1};
        for (int i = 0; i < InboundBatchBacklog.MAX_BATCHES; i++) retained.offer(close, false, CompressionProvider.NONE);
        assertFalse(retained.canOffer(1));
        assertEquals(InboundBatchBacklog.MAX_BATCHES, retained.batchCount());
        retained.close();
        assertEquals(0, retained.byteCount());
        assertFalse(retained.canOffer(1));
    }

    private static Fixture fixture(boolean nether) {
        EventLoop loop = mock(EventLoop.class);
        io.netty.util.concurrent.ScheduledFuture<?> future = mock(io.netty.util.concurrent.ScheduledFuture.class);
        doReturn(future).when(loop).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        doReturn(future).when(loop).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        Channel channel;
        NetworkPlayerSession session;
        Player player = mock(Player.class);
        player.protocol = VERSION.getProtocol();
        when(player.getGameVersion()).thenReturn(VERSION);
        when(player.shouldLogin()).thenReturn(false);
        if (nether) {
            NetherNetChildChannel child = mock(NetherNetChildChannel.class, RETURNS_DEEP_STUBS);
            when(child.eventLoop()).thenReturn(loop);
            when(child.isActive()).thenReturn(true);
            when(child.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 20000));
            NetherNetInterface transport = mock(NetherNetInterface.class);
            when(transport.getNetwork()).thenReturn(network);
            NetherNetPlayerSession created = new NetherNetPlayerSession(transport, child);
            created.setPlayer(player);
            created.setCompression(CompressionProvider.NONE);
            session = created;
            channel = child;
        } else {
            RakChildChannel child = mock(RakChildChannel.class, RETURNS_DEEP_STUBS);
            when(child.eventLoop()).thenReturn(loop);
            when(child.isActive()).thenReturn(true);
            when(child.config().getProtocolVersion()).thenReturn(11);
            when(child.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 20000));
            RakNetInterface transport = mock(RakNetInterface.class);
            when(transport.getNetwork()).thenReturn(network);
            RakNetPlayerSession created = new RakNetPlayerSession(transport, child);
            created.setPlayer(player);
            created.setCompression(CompressionProvider.NONE);
            session = created;
            channel = child;
        }
        session.getState().getLogin().setPhase(SessionLoginPhase.LOGGED_IN);
        return new Fixture(session, channel, player);
    }

    private static void receive(Fixture fixture, byte[] payload) throws Exception {
        if (fixture.session instanceof RakNetPlayerSession session) {
            RakMessage message = new RakMessage(Unpooled.buffer().writeByte(0xfe).writeBytes(payload));
            try { session.channelRead0(mock(ChannelHandlerContext.class), message); }
            finally { message.release(); }
        } else {
            ByteBuf message = Unpooled.wrappedBuffer(payload);
            try { ((NetherNetPlayerSession) fixture.session).channelRead0(mock(ChannelHandlerContext.class), message); }
            finally { message.release(); }
        }
    }

    private static void tick(Fixture fixture) throws Exception {
        Method method = fixture.session.getClass().getDeclaredMethod("networkTick");
        method.setAccessible(true);
        method.invoke(fixture.session);
    }

    private static void drainAll(Fixture fixture, int ticks) throws Exception {
        for (int i = 0; i < ticks; i++) {
            tick(fixture);
            if (fixture.session instanceof RakNetPlayerSession session) {
                session.serverTick(120, 6L * 1024L * 1024L);
            } else {
                ((NetherNetPlayerSession) fixture.session).serverTick(120, 6L * 1024L * 1024L);
            }
        }
    }

    private static byte[] resourceRequests(int count) throws Exception {
        BinaryStream batch = new BinaryStream();
        for (int i = 0; i < count; i++) {
            ResourcePackChunkRequestPacket request = new ResourcePackChunkRequestPacket();
            request.packId = UUID.fromString("07f9efc2-1a83-4084-88b1-bb20af4a6eb4");
            request.chunkIndex = i;
            encode(batch, request);
        }
        byte[] compressed = CompressionProvider.ZLIB_RAW.compress(batch, 7);
        return ByteBuffer.allocate(compressed.length + 1).put(CompressionProvider.ZLIB_RAW.getPrefix()).put(compressed).array();
    }

    private static byte[] completionAndClose() {
        BinaryStream batch = new BinaryStream();
        ResourcePackClientResponsePacket completed = new ResourcePackClientResponsePacket();
        completed.responseStatus = ResourcePackClientResponsePacket.STATUS_COMPLETED;
        completed.packEntries = new ResourcePackClientResponsePacket.Entry[0];
        encode(batch, completed);
        ContainerClosePacket close = new ContainerClosePacket();
        close.windowId = 12;
        encode(batch, close);
        return ByteBuffer.allocate(batch.getCount() + 1).put(CompressionProvider.NONE.getPrefix()).put(batch.getBuffer()).array();
    }

    private static void encode(BinaryStream batch, DataPacket packet) {
        packet.protocol = VERSION.getProtocol();
        packet.gameVersion = VERSION;
        packet.encode();
        batch.putByteArray(packet.getBuffer());
    }

    private static byte[] encrypted(Cipher cipher, SecretKey key, long counter, byte[] payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array());
        digest.update(payload);
        digest.update(key.getEncoded());
        byte[] plaintext = Arrays.copyOf(payload, payload.length + 8);
        System.arraycopy(digest.digest(), 0, plaintext, payload.length, 8);
        return cipher.update(plaintext);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static InboundBatchBacklog backlog(Fixture fixture) throws Exception {
        Field field = fixture.session.getClass().getDeclaredField("inboundBacklog");
        field.setAccessible(true);
        return (InboundBatchBacklog) field.get(fixture.session);
    }

    private static String reason(Fixture fixture) {
        return fixture.session instanceof RakNetPlayerSession session ? session.getDisconnectReason()
                : ((NetherNetPlayerSession) fixture.session).getDisconnectReason();
    }

    private record Fixture(NetworkPlayerSession session, Channel channel, Player player) { }
}
