package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.BinaryStream;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
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
        for (GameVersion version : new GameVersion[]{GameVersion.V1_20_0, GameVersion.V1_20_60, GameVersion.V1_26_50}) {
            Fixture fixture = new Fixture(version, provider);
            List<List<DataPacket>> cases = List.of(
                    List.of(packet(version, 1, 1)),
                    List.of(packet(version, 2, 1), packet(version, 3, 300), packet(version, 4, 70_000)),
                    // Crosses the 3 MiB split twice and leaves a small tail.
                    List.of(packet(version, 5, 2_000_000), packet(version, 6, 2_000_000),
                            packet(version, 7, 2_000_000), packet(version, 8, 17)));
            for (List<DataPacket> packets : cases) {
                List<byte[]> expected = fixture.legacy(packets);
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
    void steadyBatchesDoNotRegrowAFreshStream() throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);

        GameVersion version = GameVersion.V1_26_50;
        Fixture fixture = new Fixture(version, CompressionProvider.ZLIB);
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
