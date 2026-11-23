package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.NetherNetInterface;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.DisconnectPacket;
import cn.nukkit.utils.BinaryStream;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real session methods; the scheduled event-loop callback provides access without reflection. */
public class SessionIdleDrainTest {
    private static volatile Object sink;

    @Test
    void emptyDrainsKeepCountsAndBudgetsForBothTransports() {
        MockServer.init();
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(nether, GameVersion.V1_26_60);
            for (int budget : new int[]{Integer.MIN_VALUE, -1, 0, 1, 120, Integer.MAX_VALUE}) {
                RakNetPlayerSession.InboundDrain result = fixture.drain(budget, Long.MAX_VALUE);
                assertEquals(new RakNetPlayerSession.InboundDrain(0, 0L), result);
                assertSame(result, fixture.drain(budget, Long.MIN_VALUE));
            }
            DataPacket packet = packet(GameVersion.V1_26_60, 42);
            assertTrue(fixture.enqueue(packet));
            assertEquals(new RakNetPlayerSession.InboundDrain(0, 0L), fixture.drain(0, 0));
            assertEquals(new RakNetPlayerSession.InboundDrain(1, packet.getCount()), fixture.drain(120, 1024));
            assertEquals(new RakNetPlayerSession.InboundDrain(0, 0L), fixture.drain(120, 1024));
            verify(fixture.player).handleDataPacket(packet);
        }
    }

    @Test
    void netherBatchBoundariesAndDisconnectCutoffKeepWireBytesAcrossVersions() {
        MockServer.init();
        for (GameVersion version : GameVersion.values()) {
            if (version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                    || version.getProtocol() > GameVersion.V1_26_60.getProtocol()) continue;
            Fixture fixture = new Fixture(true, version);
            fixture.tick.run();
            assertTrue(fixture.frames.isEmpty());
            BatchPacket batch = new BatchPacket();
            batch.payload = new byte[]{5, 6, 7};
            DataPacket a = packet(version, 11), b = packet(version, 12), c = packet(version, 13);
            fixture.session.sendPacket(a);
            fixture.session.sendPacket(b);
            fixture.session.sendPacket(batch);
            fixture.session.sendPacket(c);
            fixture.tick.run();
            assertEquals(3, fixture.frames.size(), version.toString());
            assertArrayEquals(frame(version, encoded(a, b)), fixture.frames.get(0), version.toString());
            assertArrayEquals(frame(version, batch.payload), fixture.frames.get(1), version.toString());
            assertArrayEquals(frame(version, encoded(c)), fixture.frames.get(2), version.toString());
            fixture.frames.clear();
            DisconnectPacket disconnect = new DisconnectPacket();
            disconnect.gameVersion = version;
            disconnect.protocol = version.getProtocol();
            disconnect.message = "test";
            fixture.session.sendPacket(a);
            fixture.session.sendPacket(disconnect);
            fixture.session.sendPacket(b);
            fixture.tick.run();
            assertEquals(1, fixture.frames.size(), version.toString());
            assertArrayEquals(frame(version, encoded(disconnect)), fixture.frames.get(0), version.toString());
            fixture.frames.clear();
            fixture.tick.run();
            assertEquals(1, fixture.frames.size(), version.toString());
            assertArrayEquals(frame(version, encoded(b)), fixture.frames.get(0), version.toString());
        }
    }

    @Test
    void idleServerDrainsDoNotAllocateResults() throws Exception {
        MockServer.init();
        Fixture raknet = new Fixture(false, GameVersion.V1_26_60);
        Fixture nether = new Fixture(true, GameVersion.V1_26_60);
        measure(raknet, 200_000);
        measure(nether, 200_000);
        if (measure(raknet, 100_000) > 1024 || measure(nether, 100_000) > 1024) {
            // Another test may have instrumented the session class with inline Mockito.
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-javaagent:" + Path.of(org.mockito.Mockito.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
                    "-cp", System.getProperty("java.class.path"), getClass().getName(), "check")
                    .inheritIO().start();
            try {
                assertTrue(child.waitFor(60, TimeUnit.SECONDS), "Clean allocation probe timed out");
                assertEquals(0, child.exitValue());
            } finally {
                if (child.isAlive()) {
                    child.destroyForcibly();
                    assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Clean allocation probe did not stop");
                }
            }
        } else {
            checkAllocations();
        }
    }

    public static void main(String[] args) {
        MockServer.init();
        if (args.length > 0 && args[0].equals("check")) {
            checkAllocations();
            return;
        }
        if (args.length > 0 && args[0].equals("network")) {
            Fixture fixture = new Fixture(true, GameVersion.V1_26_60);
            DataPacket ordinary = packet(GameVersion.V1_26_60, 42);
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            bean.setThreadAllocatedMemoryEnabled(true);
            // Exercise the ordinary-packet branch before measuring only empty outbound ticks.
            for (int i = 0; i < 20_000; i++) {
                if ((i & 63) == 0) fixture.session.sendPacket(ordinary);
                fixture.tick.run();
                fixture.frames.clear();
            }
            System.out.printf(Locale.ROOT, "network-runtime,%s,%s,%s%n", System.getProperty("java.version"),
                    System.getProperty("os.arch"),
                    NetherNetPlayerSession.class.getProtectionDomain().getCodeSource().getLocation());
            for (int sample = 0; sample < 5; sample++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                for (int i = 0; i < 20_000; i++) fixture.tick.run();
                long bytes = bean.getCurrentThreadAllocatedBytes() - before;
                System.out.printf(Locale.ROOT, "network-idle,sample=%d,B/op=%.6f%n", sample, bytes / 20_000.0);
            }
            assertTrue(fixture.frames.isEmpty(), "Idle network ticks sent unexpected frames");
            return;
        }
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(nether, GameVersion.V1_26_60);
            measure(fixture, 1_000_000);
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                long bytes = measure(fixture, 1_000_000);
                System.out.printf(Locale.ROOT, "%s idle ns/op=%.4f B/op=%.6f%n",
                        nether ? "nether" : "raknet", (System.nanoTime() - start) / 1_000_000.0, bytes / 1_000_000.0);
            }
        }
    }

    private static void checkAllocations() {
        for (boolean nether : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(nether, GameVersion.V1_26_60);
            measure(fixture, 200_000);
            assertTrue(measure(fixture, 200_000) <= 1024, "Idle drain allocated results");
        }
    }

    private static long measure(Fixture fixture, int calls) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < calls; i++) sink = fixture.drain(120, 1024);
        return bean.getCurrentThreadAllocatedBytes() - before;
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

    private static byte[] frame(GameVersion version, byte[] bytes) {
        if (version.getProtocol() < GameVersion.V1_20_60.getProtocol()) return bytes;
        byte[] frame = new byte[bytes.length + 1];
        frame[0] = (byte) 0xff;
        System.arraycopy(bytes, 0, frame, 1, bytes.length);
        return frame;
    }

    private static DataPacket packet(GameVersion version, int value) {
        DataPacket packet = new DataPacket() {
            @Override public byte pid() { return 4; }
            @Override public void decode() { }
            @Override public void encode() { reset(); putByte((byte) value); }
        };
        packet.gameVersion = version;
        packet.protocol = version.getProtocol();
        packet.tryEncode();
        return packet;
    }

    private static final class Fixture {
        final NetworkPlayerSession session;
        final Player player = mock(Player.class);
        final List<byte[]> frames = new ArrayList<>();
        Runnable tick;

        Fixture(boolean nether, GameVersion version) {
            EventLoop loop = mock(EventLoop.class);
            ScheduledFuture<?> timer = mock(ScheduledFuture.class);
            doAnswer(call -> { tick = call.getArgument(0); return timer; }).when(loop)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            ChannelFuture close = mock(ChannelFuture.class);
            when(close.addListener(any())).thenReturn(close);
            when(player.getGameVersion()).thenReturn(version);
            player.protocol = version.getProtocol();
            if (nether) {
                NetherNetChildChannel channel = mock(NetherNetChildChannel.class, RETURNS_DEEP_STUBS);
                when(channel.eventLoop()).thenReturn(loop);
                when(channel.closeFuture()).thenReturn(close);
                when(channel.isActive()).thenReturn(true);
                doAnswer(call -> {
                    ByteBuf buf = call.getArgument(0);
                    try { frames.add(ByteBufUtil.getBytes(buf)); } finally { buf.release(); }
                    return close;
                }).when(channel).writeAndFlush(any(ByteBuf.class));
                NetherNetPlayerSession created = new NetherNetPlayerSession(mock(NetherNetInterface.class), channel);
                created.setPlayer(player);
                session = created;
            } else {
                RakChildChannel channel = mock(RakChildChannel.class, RETURNS_DEEP_STUBS);
                when(channel.eventLoop()).thenReturn(loop);
                when(channel.closeFuture()).thenReturn(close);
                RakNetPlayerSession created = new RakNetPlayerSession(mock(RakNetInterface.class), channel);
                created.setPlayer(player);
                session = created;
            }
            session.setCompression(CompressionProvider.NONE);
        }

        boolean enqueue(DataPacket packet) {
            return session instanceof RakNetPlayerSession raknet ? raknet.enqueueDecodedBatch(List.of(packet))
                    : ((NetherNetPlayerSession) session).enqueueDecodedBatch(List.of(packet));
        }

        RakNetPlayerSession.InboundDrain drain(int packets, long bytes) {
            return session instanceof RakNetPlayerSession raknet ? raknet.serverTick(packets, bytes)
                    : ((NetherNetPlayerSession) session).serverTick(packets, bytes);
        }
    }
}
