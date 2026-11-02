package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.DisconnectPacket;
import cn.nukkit.utils.BinaryStream;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoop;
import io.netty.util.ReferenceCountUtil;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/** Exercise the native tick and channel; mocks never enter the measured idle path. */
@Timeout(60)
class RakNetIdleBatchAllocationTest {
    private static final MethodHandle TICK;

    static {
        try {
            TICK = MethodHandles.privateLookupIn(RakNetPlayerSession.class, MethodHandles.lookup())
                    .findVirtual(RakNetPlayerSession.class, "networkTick", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
        MockServer.get().netEaseMode = false;
    }

    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values())
                .filter(version -> version.getProtocol() >= GameVersion.V1_20_0.getProtocol());
    }

    @Test
    void idleTicksAfterOrdinaryTrafficDoNotAllocatePerTickScratch() throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        try (Fixture fixture = new Fixture()) {
            fixture.run(() -> {
                DataPacket warmPacket = packet(GameVersion.V1_20_0, 42);
                // Pure idle warmup lets C2 discard the unobserved send branch and hides this regression.
                for (int round = 0; round < 4; round++) {
                    for (int i = 0; i < 500_000; i++) {
                        if ((i & 63) == 0) fixture.outbound.offer(warmPacket);
                        tick(fixture.session);
                    }
                    idleLoop(fixture, false, 500_000);
                    idleLoop(fixture, true, 500_000);
                }
                long actual = Long.MAX_VALUE;
                long control = Long.MAX_VALUE;
                for (int i = 0; i < 5; i++) {
                    actual = Math.min(actual, allocated(allocations, fixture, false, 500_000));
                    control = Math.min(control, allocated(allocations, fixture, true, 500_000));
                }
                assertTrue(fixture.channel.isActive());
                assertTrue(fixture.outbound.isEmpty());
                assertTrue(fixture.writes > 0, "warmup must exercise ordinary packet batching");
                assertTrue(actual - control < 500_000L,
                        "idle tick allocates " + (actual - control) / 500_000.0 + " excess bytes per call");
            });
        }
    }

    @ParameterizedTest
    @MethodSource("versions")
    void preservesBatchBoundariesAndDisconnectCutoff(GameVersion version) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.run(() -> {
                fixture.session.getState().getProtocol().setGameVersion(version);
                fixture.prefixed = version.getProtocol() >= GameVersion.V1_20_60.getProtocol();
                fixture.capture = true;
                tick(fixture.session);
                assertTrue(fixture.sent.isEmpty());
                byte[] first = {11, 12}, second = {21, 22};
                fixture.outbound.offer(batch(first));
                fixture.outbound.offer(batch(second));
                tick(fixture.session);
                assertFrames(fixture, first, second);

                DataPacket a = packet(version, 1), b = packet(version, 2), c = packet(version, 3);
                fixture.outbound.offer(a);
                fixture.outbound.offer(b);
                fixture.outbound.offer(batch(first));
                fixture.outbound.offer(c);
                fixture.outbound.offer(batch(second));
                tick(fixture.session);
                assertFrames(fixture, encoded(a, b), first, encoded(c), second);

                // Preserve the existing terminal policy: no flush of staged ordinary packets,
                // and packets after DisconnectPacket stay queued for connection shutdown.
                DisconnectPacket disconnect = new DisconnectPacket();
                disconnect.protocol = version.getProtocol();
                disconnect.gameVersion = version;
                disconnect.message = "test";
                fixture.outbound.offer(a);
                fixture.outbound.offer(disconnect);
                fixture.outbound.offer(b);
                tick(fixture.session);
                assertFrames(fixture, encoded(disconnect));
                assertSame(b, fixture.outbound.poll());
                assertTrue(fixture.outbound.isEmpty());
            });
        }
    }

    private static void tick(RakNetPlayerSession session) throws Throwable {
        TICK.invokeExact(session);
    }

    private static void idleLoop(Fixture fixture, boolean control, int count) throws Throwable {
        for (int i = 0; i < count; i++) {
            if (control) {
                if (!fixture.channel.isActive()) throw new AssertionError("inactive channel");
                fixture.session.drainDeferredInbound();
                if (fixture.outbound.poll() != null) throw new AssertionError("nonempty queue");
            } else {
                tick(fixture.session);
            }
        }
    }

    private static long allocated(ThreadMXBean allocations, Fixture fixture, boolean control, int count)
            throws Throwable {
        long thread = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(thread);
        idleLoop(fixture, control, count);
        return allocations.getThreadAllocatedBytes(thread) - before;
    }

    private static DataPacket packet(GameVersion version, int value) {
        DataPacket packet = new DataPacket() {
            @Override public byte pid() { return 9; }
            @Override public void decode() { }
            @Override public void encode() { reset(); putByte((byte) value); }
        };
        packet.protocol = version.getProtocol();
        packet.gameVersion = version;
        return packet;
    }

    private static BatchPacket batch(byte[] payload) {
        BatchPacket packet = new BatchPacket();
        packet.payload = payload;
        return packet;
    }

    private static byte[] encoded(DataPacket... packets) {
        BinaryStream stream = new BinaryStream();
        for (DataPacket packet : packets) {
            packet.tryEncode();
            byte[] bytes = packet.getBuffer();
            stream.putUnsignedVarInt(bytes.length);
            stream.put(bytes);
        }
        return stream.getBuffer();
    }

    private static void assertFrames(Fixture fixture, byte[]... payloads) {
        assertEquals(payloads.length, fixture.sent.size());
        for (int i = 0; i < payloads.length; i++) {
            byte[] frame = fixture.sent.get(i);
            assertEquals((byte) 0xfe, frame[0]);
            if (fixture.prefixed) assertEquals(CompressionProvider.NONE.getPrefix(), frame[1]);
            assertArrayEquals(payloads[i], Arrays.copyOfRange(frame, fixture.prefixed ? 2 : 1, frame.length));
        }
        fixture.sent.clear();
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Throwable;
    }

    private static final class Fixture implements AutoCloseable {
        private final DefaultEventLoop eventLoop = new DefaultEventLoop();
        private RakChildChannel channel;
        private final RakNetPlayerSession session;
        private final Queue<DataPacket> outbound;
        private final List<byte[]> sent = new ArrayList<>();
        private boolean capture;
        private boolean prefixed;
        private int writes;

        @SuppressWarnings("unchecked")
        private Fixture() throws Exception {
            try {
                Constructor<RakChildChannel> constructor = RakChildChannel.class.getDeclaredConstructor(
                        InetSocketAddress.class, InetSocketAddress.class, InetSocketAddress.class,
                        RakServerChannel.class, long.class, int.class, Consumer.class);
                constructor.setAccessible(true);
                InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 20000);
                channel = constructor.newInstance(remote, new InetSocketAddress("127.0.0.1", 19000), remote,
                        mock(RakServerChannel.class, RETURNS_DEEP_STUBS), 100L, 1400,
                        (Consumer<RakChannel>) value -> value.config().setProtocolVersion(11));
                eventLoop.register(channel).get(5, TimeUnit.SECONDS);
                session = eventLoop.submit(() -> {
                    RakNetPlayerSession created = new RakNetPlayerSession(mock(RakNetInterface.class), channel);
                    created.setCompression(CompressionProvider.NONE);
                    Field timer = RakNetPlayerSession.class.getDeclaredField("tickFuture");
                    timer.setAccessible(true);
                    ((Future<?>) timer.get(created)).cancel(false);
                    channel.setActive(true);
                    channel.pipeline().addLast("test-output-sink", new ChannelOutboundHandlerAdapter() {
                        @Override
                        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                            try {
                                writes++;
                                if (capture) {
                                    ByteBuf bytes = (ByteBuf) message;
                                    byte[] copy = new byte[bytes.readableBytes()];
                                    bytes.getBytes(bytes.readerIndex(), copy);
                                    sent.add(copy);
                                }
                            } finally {
                                ReferenceCountUtil.release(message);
                            }
                            promise.setSuccess();
                        }
                    });
                    return created;
                }).get(5, TimeUnit.SECONDS);
                Field queue = RakNetPlayerSession.class.getDeclaredField("outbound");
                queue.setAccessible(true);
                outbound = (Queue<DataPacket>) queue.get(session);
            } catch (Exception | Error failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        private void run(CheckedAction action) throws Exception {
            try {
                eventLoop.submit(() -> {
                    try {
                        action.run();
                    } catch (Error failure) {
                        throw failure;
                    } catch (Throwable failure) {
                        throw new RuntimeException(failure);
                    }
                }).get(45, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof Error error) throw error;
                throw failure;
            }
        }

        @Override
        public void close() {
            boolean channelClosed = true;
            try {
                if (channel != null) {
                    channelClosed = channel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
                }
            } finally {
                assertTrue(eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS)
                        .awaitUninterruptibly(5, TimeUnit.SECONDS), "event loop shutdown timed out");
            }
            assertTrue(channelClosed, "channel close timed out");
        }
    }
}
