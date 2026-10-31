package cn.nukkit.network.session;

import cn.nukkit.MockServer;
import cn.nukkit.network.RakNetInterface;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class RakNetSessionCloseLifecycleTest {

    private Fixture fixture;

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @BeforeEach
    void resetServerState() {
        MockServer.reset();
        MockServer.get().networkLoginTimeoutMilliseconds = 0;
        MockServer.get().netEaseMode = false;
        fixture = new Fixture();
    }

    @AfterEach
    void stopEventLoopEvenWhenTheBaselineLeavesItsTickScheduled() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void closeBeforeActivationCancelsTickWithoutAChannelInactiveEvent() throws Exception {
        fixture.open(false);
        assertTrue(fixture.child.isOpen());
        assertFalse(fixture.child.isActive());
        assertFalse(fixture.tick.isDone());

        fixture.closeChannel();

        assertEquals(0, fixture.inactiveEvents.get(), "The real channel never became active");
        assertTrue(fixture.child.closeFuture().isSuccess());
        assertTrue(fixture.tick.isCancelled(), "A closed pre-handshake session must leave the scheduler");
        assertEquals(0, fixture.loop.delayedTasks.get(), "Closing an already closed channel needs no new timer");
    }

    @Test
    void inactiveButOpenChannelKeepsItsTickScheduled() throws Exception {
        fixture.open(false);
        fixture.barrier();

        assertTrue(fixture.child.isOpen());
        assertFalse(fixture.child.isActive());
        assertFalse(fixture.child.closeFuture().isDone());
        assertFalse(fixture.tick.isCancelled());
        assertFalse(fixture.tick.isDone(), "Waiting for the handshake is not terminal cleanup");
        assertEquals(0, fixture.inactiveEvents.get());
    }

    @Test
    void alreadyClosedChannelCancelsTickWhenTheSessionIsConstructed() throws Exception {
        fixture.open(true);
        fixture.barrier();

        assertTrue(fixture.child.closeFuture().isSuccess());
        assertFalse(fixture.child.isOpen());
        assertEquals(0, fixture.inactiveEvents.get());
        assertTrue(fixture.tick.isCancelled(), "Registration must also observe an already completed close future");
        assertEquals(0, fixture.loop.delayedTasks.get());
    }

    @Test
    void activeCloseCancelsTickThroughTheRealChannelLifecycle() throws Exception {
        fixture.open(false);
        fixture.activate();
        assertTrue(fixture.child.isActive());

        fixture.closeChannel();

        assertEquals(1, fixture.inactiveEvents.get());
        assertTrue(fixture.tick.isCancelled());
        assertEquals("transport:unknown", fixture.session.getDisconnectReason());
        assertEquals(1, fixture.loop.delayedTasks.get(), "Keep the existing disconnect close timer unchanged");
    }

    @Test
    void explicitDisconnectPreservesItsReasonAndDoesNotScheduleAnotherClose() throws Exception {
        fixture.open(false);
        fixture.activate();

        fixture.onLoop(() -> {
            fixture.session.disconnect("Intentional test disconnect");
            assertTrue(fixture.tick.isCancelled());
            assertEquals(1, fixture.loop.delayedTasks.get());
            return null;
        });
        fixture.child.closeFuture().get(5, TimeUnit.SECONDS);
        assertTrue(fixture.inactiveProcessed.await(5, TimeUnit.SECONDS),
                "The active close must finish the session handler before assertions");
        fixture.barrier();

        assertEquals("Intentional test disconnect", fixture.session.getDisconnectReason());
        assertEquals(1, fixture.inactiveEvents.get());
        assertTrue(fixture.tick.isCancelled());
        assertEquals(1, fixture.loop.delayedTasks.get(), "The close future must not invoke disconnect again");
    }

    @Test
    void repeatedCloseIsIdempotentForANeverActiveSession() throws Exception {
        fixture.open(false);

        fixture.closeChannel();
        fixture.closeChannel();

        assertTrue(fixture.child.closeFuture().isSuccess());
        assertTrue(fixture.tick.isCancelled());
        assertEquals(0, fixture.inactiveEvents.get());
        assertEquals(0, fixture.loop.delayedTasks.get());
    }

    private static final class Fixture implements AutoCloseable {
        private final CountingEventLoop loop = new CountingEventLoop();
        private final AtomicInteger inactiveEvents = new AtomicInteger();
        private final CountDownLatch inactiveProcessed = new CountDownLatch(1);
        private RakChildChannel child;
        private RakNetPlayerSession session;
        private ScheduledFuture<?> tick;

        private void open(boolean closeBeforeSessionConstruction) throws Exception {
            Constructor<RakChildChannel> constructor = RakChildChannel.class.getDeclaredConstructor(
                    InetSocketAddress.class, InetSocketAddress.class, InetSocketAddress.class,
                    RakServerChannel.class, long.class, int.class, Consumer.class);
            constructor.setAccessible(true);
            // Only the parent is mocked: registration, closeFuture, doClose and event delivery are real.
            RakServerChannel parent = mock(RakServerChannel.class, RETURNS_DEEP_STUBS);
            InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 19132);
            InetSocketAddress local = new InetSocketAddress("127.0.0.1", 19133);
            child = constructor.newInstance(remote, local, remote, parent, 1L, 1400,
                    (Consumer<RakChannel>) channel -> channel.config().setProtocolVersion(11));
            loop.register(child).get(5, TimeUnit.SECONDS);

            onLoop(() -> {
                child.pipeline().addLast("inactive-counter", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext context) throws Exception {
                        inactiveEvents.incrementAndGet();
                        super.channelInactive(context);
                        inactiveProcessed.countDown();
                    }
                });
                return null;
            });
            if (closeBeforeSessionConstruction) {
                closeChannel();
            }
            onLoop(() -> {
                session = new RakNetPlayerSession(mock(RakNetInterface.class), child);
                Field field = RakNetPlayerSession.class.getDeclaredField("tickFuture");
                field.setAccessible(true);
                tick = (ScheduledFuture<?>) field.get(session);
                if (!closeBeforeSessionConstruction) {
                    child.pipeline().addLast("nukkit-session", session);
                }
                return null;
            });
        }

        private void activate() throws Exception {
            onLoop(() -> {
                child.setActive(true);
                child.pipeline().fireChannelActive();
                return null;
            });
        }

        private <T> T onLoop(Callable<T> task) throws Exception {
            return loop.submit(task).get(5, TimeUnit.SECONDS);
        }

        private void barrier() throws Exception {
            onLoop(() -> null);
        }

        private void closeChannel() throws Exception {
            onLoop(child::close).get(5, TimeUnit.SECONDS);
            barrier();
        }

        @Override
        public void close() throws Exception {
            try {
                if (tick != null) {
                    tick.cancel(false);
                }
            } finally {
                try {
                    if (child != null) {
                        child.close().get(5, TimeUnit.SECONDS);
                    }
                } finally {
                    loop.shutdownGracefully(0, 5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static final class CountingEventLoop extends DefaultEventLoop {
        private final AtomicInteger delayedTasks = new AtomicInteger();

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            delayedTasks.incrementAndGet();
            return super.schedule(command, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
            delayedTasks.incrementAndGet();
            return super.schedule(command, delay, unit);
        }
    }
}
