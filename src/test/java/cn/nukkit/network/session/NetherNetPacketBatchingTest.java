package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.utils.BinaryStream;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** NetherNet batches go through the same reusable scratch as RakNet and keep identical wire bytes. */
class NetherNetPacketBatchingTest {
    private static final Method SEND_PACKETS;
    private static final Method SEND_LEGACY;

    static {
        try {
            SEND_PACKETS = NetherNetPlayerSession.class.getDeclaredMethod("sendPackets", Collection.class);
            SEND_PACKETS.setAccessible(true);
            SEND_LEGACY = NetherNetPlayerSession.class.getDeclaredMethod("sendPackets", BinaryStream.class);
            SEND_LEGACY.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    static Stream<CompressionProvider> providers() {
        return Stream.of(CompressionProvider.NONE, CompressionProvider.ZLIB, CompressionProvider.ZLIB_RAW,
                CompressionProvider.SNAPPY);
    }

    /**
     * 只有能写前缀的编码才覆盖 1.20.60+；ZLIB 带 zlib 头，没有前缀形态，只能停在无前缀的老版本上。
     * <p>Only prefix-capable codecs cover 1.20.60+; ZLIB is header-bearing and has no prefixed form, so it
     * stays on the legacy versions that need no prefix.
     */
    private static GameVersion[] versionsFor(CompressionProvider provider) {
        return provider.supportsPrefix()
                ? new GameVersion[]{GameVersion.V1_20_0, GameVersion.V1_20_60, GameVersion.V1_26_50}
                : new GameVersion[]{GameVersion.V1_20_0};
    }

    private static final class Fixture {
        final NetherNetPlayerSession session;
        final List<byte[]> frames = new ArrayList<>();

        Fixture(GameVersion version, CompressionProvider compression) {
            EventLoop loop = mock(EventLoop.class);
            io.netty.util.concurrent.ScheduledFuture<?> timer = mock(io.netty.util.concurrent.ScheduledFuture.class);
            doReturn(timer).when(loop).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            ChannelFuture future = mock(ChannelFuture.class);
            when(future.addListener(any())).thenReturn(future);
            NetherNetChildChannel channel = mock(NetherNetChildChannel.class);
            when(channel.eventLoop()).thenReturn(loop);
            when(channel.closeFuture()).thenReturn(future);
            doAnswer(call -> {
                ByteBuf payload = call.getArgument(0);
                try {
                    frames.add(ByteBufUtil.getBytes(payload));
                } finally {
                    payload.release();
                }
                return future;
            }).when(channel).writeAndFlush(any(ByteBuf.class));
            session = new NetherNetPlayerSession(null, channel);
            session.getState().getProtocol().setGameVersion(version);
            session.setCompression(compression);
        }

        List<byte[]> send(List<DataPacket> packets) throws Exception {
            frames.clear();
            SEND_PACKETS.invoke(session, packets);
            return new ArrayList<>(frames);
        }

        /** The bytes the pre-batcher loop produced: a fresh stream per batch, split before 3 MiB. */
        List<byte[]> legacy(List<DataPacket> packets) throws Exception {
            frames.clear();
            BinaryStream batched = new BinaryStream();
            for (DataPacket packet : packets) {
                packet.tryEncode();
                byte[] buf = packet.getBuffer();
                if (batched.getCount() + buf.length > 3145728) {
                    SEND_LEGACY.invoke(session, batched);
                    batched = new BinaryStream();
                }
                batched.putUnsignedVarInt(buf.length);
                batched.put(buf);
            }
            SEND_LEGACY.invoke(session, batched);
            return new ArrayList<>(frames);
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    void keepsLegacyWireBytesAndSplitBoundaries(CompressionProvider provider) throws Exception {
        for (GameVersion version : versionsFor(provider)) {
            Fixture fixture = new Fixture(version, provider);
            List<List<DataPacket>> cases = List.of(
                    List.of(packet(version, 1, 1)),
                    List.of(packet(version, 2, 1), packet(version, 3, 300), packet(version, 4, 70_000)),
                    // Crosses the 3 MiB split twice and leaves a small tail.
                    List.of(packet(version, 5, 2_000_000), packet(version, 6, 2_000_000),
                            packet(version, 7, 2_000_000), packet(version, 8, 17)));
            for (List<DataPacket> packets : cases) {
                List<byte[]> expected = fixture.legacy(packets);
                // 空帧对比是 0 == 0 的假通过：两条路径都必须真的出帧
                // Comparing empty lists passes on 0 == 0, so both paths must really produce frames
                assertFalse(expected.isEmpty(), provider + " " + version + " produced no frames");
                List<byte[]> actual = fixture.send(packets);
                assertEquals(expected.size(), actual.size(), provider + " " + version + " frame count");
                for (int i = 0; i < expected.size(); i++) {
                    assertArrayEquals(expected.get(i), actual.get(i), provider + " " + version + " frame " + i);
                }
                // A second send through the retained scratch must not leak bytes from the first.
                List<byte[]> again = fixture.send(packets);
                for (int i = 0; i < expected.size(); i++) {
                    assertArrayEquals(expected.get(i), again.get(i), provider + " " + version + " repeat " + i);
                }
            }
        }
    }

    @Test
    void prefixIncapableCompressionClosesOnceInsteadOfDroppingEveryBatch() throws Exception {
        Fixture fixture = new Fixture(GameVersion.V1_26_50, CompressionProvider.ZLIB);
        List<DataPacket> packets = List.of(packet(GameVersion.V1_26_50, 1, 64));

        assertTrue(fixture.send(packets).isEmpty(), "a codec without a prefix cannot frame anything");
        assertTrue(fixture.send(packets).isEmpty(), "later batches stay silent");
        assertEquals(SessionLoginPhase.DISCONNECTED, fixture.session.getState().getLogin().getPhase());
        assertEquals("Your client does not support this game version over this connection",
                fixture.session.getState().getLogin().getDisconnectCauseHint(), "the prefix guard must close the session");

        Fixture prefixed = new Fixture(GameVersion.V1_26_50, CompressionProvider.ZLIB_RAW);
        assertEquals(1, prefixed.send(packets).size(), "the legal combination still frames");
    }

    @Test
    void legacyCustomCompressionProviderStillFramesBatches() throws Exception {
        // 只实现旧接口（compress/decompress/getPrefix）的插件编码器不能因新增的能力方法被断开
        // A plugin codec implementing only the old surface must not be disconnected by the new capability method
        CompressionProvider legacy = new CompressionProvider() {
            @Override
            public byte[] compress(BinaryStream packet, int level) throws Exception {
                return CompressionProvider.ZLIB_RAW.compress(packet, level);
            }

            @Override
            public byte[] decompress(byte[] compressed) throws Exception {
                return CompressionProvider.ZLIB_RAW.decompress(compressed);
            }

            @Override
            public byte getPrefix() {
                return CompressionProvider.ZLIB_RAW.getPrefix();
            }
        };

        Fixture fixture = new Fixture(GameVersion.V1_26_50, legacy);
        assertEquals(1, fixture.send(List.of(packet(GameVersion.V1_26_50, 1, 64))).size(),
                "a legacy plugin codec must keep framing on prefixed transports");
        assertNotEquals(SessionLoginPhase.DISCONNECTED, fixture.session.getState().getLogin().getPhase());
    }

    @Test
    void directWritePrefixFailureDoesNotRunSuccessCallback() throws Exception {
        // 同 RakNet：前缀不可用时批次未写出，DIRECT_WRITE 不得按成功执行回调
        // Same as RakNet: the batch is never written when the prefix is unavailable, so DIRECT_WRITE
        // must not run success callbacks
        EventLoop loop = mock(EventLoop.class);
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(loop).execute(any(Runnable.class));
        NetherNetChildChannel channel = mock(NetherNetChildChannel.class);
        when(channel.eventLoop()).thenReturn(loop);
        when(channel.isActive()).thenReturn(true);
        ChannelFuture closeFuture = mock(ChannelFuture.class);
        when(closeFuture.addListener(any())).thenReturn(closeFuture);
        when(channel.closeFuture()).thenReturn(closeFuture);
        stubCompletedFutures(channel);

        NetherNetPlayerSession session = new NetherNetPlayerSession(null, channel);
        session.getState().getProtocol().setGameVersion(GameVersion.V1_26_50);
        session.setCompression(CompressionProvider.ZLIB);

        AtomicBoolean callbackRan = new AtomicBoolean();
        session.sendImmediatePacket(packet(GameVersion.V1_26_50, 1, 64), () -> callbackRan.set(true),
                NetworkPlayerSession.ImmediatePacketMode.DIRECT_WRITE);

        assertFalse(callbackRan.get(), "an unwritten packet must not report success to DIRECT_WRITE");
        verify(channel, never()).writeAndFlush(any(ByteBuf.class));
        assertEquals(SessionLoginPhase.DISCONNECTED, session.getState().getLogin().getPhase(),
                "the phase set by the prefix close must survive the failure branch");
        assertEquals("Your client does not support this game version over this connection",
                session.getState().getLogin().getDisconnectCauseHint(),
                "the failure branch's disconnect must stay a no-op against the prefix close reason");
    }

    /**
     * 用真实 Netty future 替换 mock 的已完成 future：监听器同步触发，防止 mock 吞掉回调掩盖回归。
     * <p>Replaces the mock's completed futures with real Netty ones so listeners fire synchronously
     * and a mock cannot swallow the callback and hide a regression.
     */
    private static void stubCompletedFutures(Channel channel) {
        when(channel.newSucceededFuture()).thenAnswer(call ->
                completedPromise(channel, null));
        when(channel.newFailedFuture(any(Throwable.class))).thenAnswer(call ->
                completedPromise(channel, call.getArgument(0)));
    }

    private static DefaultChannelPromise completedPromise(Channel channel, Throwable cause) {
        DefaultChannelPromise promise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        if (cause == null) {
            promise.setSuccess();
        } else {
            promise.setFailure(cause);
        }
        return promise;
    }

    @Test
    void steadyBatchesDoNotRegrowAFreshStream() throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);

        GameVersion version = GameVersion.V1_26_50;
        Fixture fixture = new Fixture(version, CompressionProvider.ZLIB_RAW);
        int payload = 256 * 1024;
        List<DataPacket> packets = new ArrayList<>();
        for (int i = 0; i < 8; i++) packets.add(packet(version, i, payload / 8));
        for (DataPacket packet : packets) packet.tryEncode();

        int rounds = 200;
        for (int i = 0; i < rounds; i++) fixture.send(packets);
        long batcher = Long.MAX_VALUE, legacy = Long.MAX_VALUE;
        for (int attempt = 0; attempt < 5; attempt++) {
            long start = allocations.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < rounds; i++) fixture.send(packets);
            batcher = Math.min(batcher, allocations.getCurrentThreadAllocatedBytes() - start);
            start = allocations.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < rounds; i++) fixture.legacy(packets);
            legacy = Math.min(legacy, allocations.getCurrentThreadAllocatedBytes() - start);
        }
        // Both paths copy each packet out of DataPacket.getBuffer(). On top of that the legacy loop regrows
        // a fresh stream to the batch size and copies it once more for the codec; the batcher reuses scratch.
        assertTrue(batcher / rounds < payload * 3L / 2,
                "batched send allocates " + batcher / rounds + " bytes per " + payload + "-byte batch");
        assertTrue(batcher * 2 < legacy, "batcher " + batcher / rounds + " vs legacy " + legacy / rounds + " bytes per batch");
    }

    private static DataPacket packet(GameVersion version, int value, int size) {
        DataPacket packet = new DataPacket() {
            @Override public byte pid() { return 9; }
            @Override public void decode() { }
            @Override public void encode() {
                reset();
                byte[] body = new byte[size];
                for (int i = 0; i < size; i++) body[i] = (byte) (value * 31 + i);
                put(body);
            }
        };
        packet.protocol = version.getProtocol();
        packet.gameVersion = version;
        return packet;
    }
}
