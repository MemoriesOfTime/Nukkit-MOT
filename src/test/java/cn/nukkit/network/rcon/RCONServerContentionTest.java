package cn.nukkit.network.rcon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class RCONServerContentionTest {
    @Test
    void socketWriteDoesNotBlockTickReplyToAnotherClient() throws Exception {
        assertReplyWhileWriterPaused(false, false);
    }

    @Test
    void packetEncodingDoesNotBlockTickReplyToSameClient() throws Exception {
        assertReplyWhileWriterPaused(true, true);
    }

    private void assertReplyWhileWriterPaused(boolean pauseEncoding, boolean sameClient) throws Exception {
        try (Fixture fixture = new Fixture()) {
            SocketChannel writing = fixture.channel();
            SocketChannel replying = sameClient ? writing : fixture.channel();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch admitted = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            List<byte[]> written = new ArrayList<>();
            when(writing.write(any(ByteBuffer.class))).thenAnswer(call -> {
                if (!pauseEncoding) pause(entered, release);
                ByteBuffer buffer = call.getArgument(0);
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                written.add(bytes);
                return bytes.length;
            });
            RCONPacket first = new RCONPacket(11, 0, "первый".getBytes(StandardCharsets.UTF_8)) {
                @Override
                public ByteBuffer toBuffer() {
                    if (pauseEncoding) pause(entered, release);
                    return super.toBuffer();
                }
            };
            fixture.queue(writing).add(first);
            Thread writer = new Thread(() -> {
                try {
                    fixture.write(writing);
                } catch (Throwable problem) {
                    failure.compareAndSet(null, problem);
                }
            }, "p08-rcon");
            Thread tick = new Thread(() -> {
                try {
                    fixture.server.respond(replying, 12, "второй");
                    admitted.countDown();
                } catch (Throwable problem) {
                    failure.compareAndSet(null, problem);
                }
            }, "p08-tick");
            writer.start();
            boolean prompt;
            long elapsed;
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS), "writer did not reach paused work");
                long started = System.nanoTime();
                tick.start();
                prompt = admitted.await(1, TimeUnit.SECONDS);
                elapsed = System.nanoTime() - started;
            } finally {
                release.countDown();
                writer.join(3000);
                tick.join(3000);
                if (writer.isAlive()) writer.interrupt();
                if (tick.isAlive()) tick.interrupt();
            }
            assertFalse(writer.isAlive(), "writer outlived fixture deadline");
            assertFalse(tick.isAlive(), "tick outlived fixture deadline");
            assertNull(failure.get());
            assertArrayEquals(bytes(first), written.get(0));
            assertEquals(12, fixture.queue(replying).get(0).getId());
            if (sameClient) {
                fixture.write(writing);
                assertArrayEquals(bytes(new RCONPacket(12, 0,
                        "второй".getBytes(StandardCharsets.UTF_8))), written.get(1));
                assertFalse(fixture.queues().containsKey(writing));
            }
            System.out.printf(Locale.ROOT, "p08 pause=%s admissionMs=%.3f prompt=%s%n",
                    pauseEncoding ? "encoding" : "socket", elapsed / 1_000_000.0, prompt);
            assertTrue(prompt, "tick reply waited for unrelated RCON encoding/socket work");
        }
    }

    @Test
    void responsesRetainFifoAndExactUtf8WireBytes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SocketChannel channel = fixture.channel();
            List<byte[]> written = new ArrayList<>();
            when(channel.write(any(ByteBuffer.class))).thenAnswer(call -> {
                ByteBuffer buffer = call.getArgument(0);
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                written.add(bytes);
                return bytes.length;
            });
            fixture.server.respond(channel, 31, "ответ один");
            fixture.server.respond(channel, 32, "ответ два");
            fixture.write(channel);
            fixture.write(channel);
            assertEquals(2, written.size());
            assertArrayEquals(bytes(new RCONPacket(31, 0,
                    "ответ один".getBytes(StandardCharsets.UTF_8))), written.get(0));
            assertArrayEquals(bytes(new RCONPacket(32, 0,
                    "ответ два".getBytes(StandardCharsets.UTF_8))), written.get(1));
            assertFalse(fixture.queues().containsKey(channel));
        }
    }

    @Test
    void socketFailureCleansOnlyFailingClient() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SocketChannel failed = fixture.channel();
            SocketChannel healthy = fixture.channel();
            fixture.server.respond(failed, 41, "failed");
            fixture.server.respond(healthy, 42, "healthy");
            when(failed.write(any(ByteBuffer.class))).thenThrow(new IOException("test socket failure"));
            fixture.write(failed);
            assertFalse(fixture.queues().containsKey(failed));
            assertEquals(42, fixture.queue(healthy).get(0).getId());
            verify(fixture.keys.get(failed)).cancel();
            verify(failed).close();
        }
    }

    @Test
    void disconnectedClientDoesNotAcquireQueueMonitor() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SocketChannel absent = mock(SocketChannel.class);
            fixture.server.respond(absent, 51, "gone");
            assertTrue(fixture.queues().isEmpty());
        }
    }

    private static byte[] bytes(RCONPacket packet) {
        ByteBuffer buffer = packet.toBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static void pause(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("pause deadline exceeded");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final RCONServer server = new RCONServer("127.0.0.1", 0, "test-only");
        private final Map<SocketChannel, SelectionKey> keys = new HashMap<>();

        private Fixture() throws IOException {
        }

        private SocketChannel channel() throws Exception {
            SocketChannel channel = mock(SocketChannel.class);
            SelectionKey key = mock(SelectionKey.class);
            when(key.channel()).thenReturn(channel);
            when(key.isValid()).thenReturn(true);
            when(key.interestOps(anyInt())).thenReturn(key);
            when(channel.keyFor((Selector) field("selector"))).thenReturn(key);
            keys.put(channel, key);
            return channel;
        }

        @SuppressWarnings("unchecked")
        private Map<SocketChannel, List<RCONPacket>> queues() throws Exception {
            return (Map<SocketChannel, List<RCONPacket>>) field("sendQueues");
        }

        private List<RCONPacket> queue(SocketChannel channel) throws Exception {
            return queues().computeIfAbsent(channel, ignored -> new ArrayList<>());
        }

        private Object field(String name) throws Exception {
            Field field = RCONServer.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(server);
        }

        private void write(SocketChannel channel) throws Exception {
            Method write = RCONServer.class.getDeclaredMethod("write", SelectionKey.class);
            write.setAccessible(true);
            try {
                write.invoke(server, keys.get(channel));
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Exception exception) throw exception;
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            ((ServerSocketChannel) field("serverChannel")).close();
            ((Selector) field("selector")).close();
        }
    }
}
