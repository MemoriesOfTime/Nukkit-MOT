package cn.nukkit.network;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.scheduler.AsyncPool;
import cn.nukkit.scheduler.ServerScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import oshi.hardware.NetworkIF;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(10)
class NetworkStatisticsTest {
    private Network network;
    private NetworkIF nic;
    private AsyncPool pool;
    private ServerScheduler scheduler;
    private Server server;
    private Queue<Runnable> pending;

    @BeforeAll
    static void initialize() { MockServer.init(); }

    @BeforeEach
    void setup() throws Exception {
        server = mock(Server.class);
        scheduler = mock(ServerScheduler.class);
        pool = mock(AsyncPool.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.getAsyncPool()).thenReturn(pool);
        pending = new ArrayDeque<>();
        doAnswer(call -> { pending.add(call.getArgument(0)); return null; })
                .when(pool).execute(any(Runnable.class));
        network = new Network(server);
        nic = mock(NetworkIF.class);
        setInterfaces(List.of(nic));
    }

    private void setInterfaces(List<NetworkIF> interfaces) throws Exception {
        Field field = Network.class.getDeclaredField("hardWareNetworkInterfaces");
        field.setAccessible(true);
        field.set(network, interfaces);
    }

    private void sample(long sent, long received) {
        when(nic.getBytesSent()).thenReturn(sent);
        when(nic.getBytesRecv()).thenReturn(received);
        network.resetStatistics();
        assertEquals(1, pending.size());
        pending.remove().run();
    }

    @Test
    void tickOnlyDispatchesAndFirstSampleHasNoDelta() {
        network.resetStatistics();
        verifyNoInteractions(nic);
        assertEquals(0, network.getUpload());
        assertEquals(0, network.getDownload());
        assertEquals(1, pending.size());
        pending.remove().run();
        verify(nic).updateAttributes();
        assertEquals(0, network.getUpload());
        assertEquals(0, network.getDownload());
    }

    @Test
    void preservesHostTotalsAndConsecutiveByteDeltas() throws Exception {
        NetworkIF second = mock(NetworkIF.class);
        List<NetworkIF> interfaces = List.of(nic, second);
        setInterfaces(interfaces);
        when(second.getBytesSent()).thenReturn(500L);
        when(second.getBytesRecv()).thenReturn(800L);
        sample(100, 200);
        when(second.getBytesSent()).thenReturn(750L);
        when(second.getBytesRecv()).thenReturn(900L);
        sample(130, 270);
        assertEquals(280, network.getUpload());
        assertEquals(170, network.getDownload());
        assertSame(interfaces, network.getHardWareNetworkInterfaces());
        sample(150, 300);
        assertEquals(20, network.getUpload());
        assertEquals(30, network.getDownload());
    }

    @Test
    void repeatedRequestsDoNotBuildBacklog() {
        for (int i = 0; i < 1000; i++) network.resetStatistics();
        assertEquals(1, pending.size());
        verify(pool, times(1)).execute(any(Runnable.class));
        pending.remove().run();
        network.resetStatistics();
        assertEquals(1, pending.size());
    }

    @Test
    void rejectionDoesNotWedgeFutureSamples() {
        doThrow(new RejectedExecutionException("test shutdown")).doAnswer(call -> {
            pending.add(call.getArgument(0)); return null;
        }).when(pool).execute(any(Runnable.class));
        assertDoesNotThrow(network::resetStatistics);
        assertTrue(pending.isEmpty());
        sample(100, 200);
        sample(110, 225);
        assertEquals(10, network.getUpload());
        assertEquals(25, network.getDownload());
    }

    @Test
    void readFailurePreservesSnapshotAndAllowsRecovery() {
        sample(100, 200);
        sample(120, 250);
        when(nic.updateAttributes()).thenThrow(new IllegalStateException("test I/O"));
        network.resetStatistics();
        assertDoesNotThrow(() -> pending.remove().run());
        assertEquals(20, network.getUpload());
        assertEquals(50, network.getDownload());
        doReturn(true).when(nic).updateAttributes();
        sample(140, 280);
        assertEquals(20, network.getUpload());
        assertEquals(30, network.getDownload());
    }

    @Test
    void partialInterfaceFailureDoesNotPublishPartialTotals() throws Exception {
        sample(100, 200);
        sample(120, 250);
        NetworkIF second = mock(NetworkIF.class);
        setInterfaces(List.of(nic, second));
        when(second.updateAttributes()).thenThrow(new IllegalStateException("second NIC"));
        sample(1000, 2000);
        assertEquals(20, network.getUpload());
        assertEquals(50, network.getDownload());
        setInterfaces(List.of(nic));
        sample(140, 280);
        assertEquals(20, network.getUpload());
        assertEquals(30, network.getDownload());
    }

    @Test
    void missingInterfacesDoNotScheduleWork() throws Exception {
        setInterfaces(null);
        network.resetStatistics();
        setInterfaces(List.of());
        network.resetStatistics();
        verifyNoInteractions(pool);
        assertEquals(0, network.getUpload());
        assertEquals(0, network.getDownload());
    }

    @Test
    void failedAttributeRefreshAndCounterResetKeepExistingApiSemantics() {
        when(nic.updateAttributes()).thenReturn(false);
        sample(100, 200);
        sample(100, 200);
        assertEquals(0, network.getUpload());
        assertEquals(0, network.getDownload());
        sample(10, 20);
        assertEquals(-90, network.getUpload());
        assertEquals(-180, network.getDownload());
    }

    @Test
    void managedPoolShutdownRejectsFurtherReads() {
        AsyncPool realPool = new AsyncPool(server, 1);
        when(scheduler.getAsyncPool()).thenReturn(realPool);
        try {
            network.resetStatistics();
        } finally {
            realPool.shutdownGracefully(2);
        }
        assertTrue(realPool.isTerminated());
        verify(nic).updateAttributes();
        clearInvocations(nic);
        for (int i = 0; i < 10; i++) assertDoesNotThrow(network::resetStatistics);
        verifyNoInteractions(nic);
    }

    @Test
    void concurrentRequestsStillAllowOnlyOneReader() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] callers = new Thread[16];
        for (int i = 0; i < callers.length; i++) {
            callers[i] = new Thread(() -> {
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    for (int j = 0; j < 100; j++) network.resetStatistics();
                } catch (Throwable error) { failure.compareAndSet(null, error); }
            });
            callers[i].start();
        }
        start.countDown();
        for (Thread caller : callers) {
            caller.join(5000);
            assertFalse(caller.isAlive());
        }
        assertNull(failure.get());
        assertEquals(1, pending.size());
        verify(pool, times(1)).execute(any(Runnable.class));
        pending.remove().run();
        verify(nic).updateAttributes();
    }

    @Test
    void fatalReaderErrorReleasesSingleFlightWithoutPublishing() {
        sample(100, 200);
        sample(120, 250);
        doThrow(new AssertionError("fatal reader")).when(nic).updateAttributes();
        network.resetStatistics();
        assertThrows(AssertionError.class, () -> pending.remove().run());
        assertEquals(20, network.getUpload());
        doReturn(true).when(nic).updateAttributes();
        sample(140, 280);
        assertEquals(20, network.getUpload());
        assertEquals(30, network.getDownload());
    }

    @Test
    void blockedReadNeverBlocksTickOrPublishesHalfSample() throws Exception {
        sample(100, 200);
        sample(120, 250);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        when(nic.getBytesSent()).thenReturn(170L);
        when(nic.getBytesRecv()).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker timeout");
            return 340L;
        });
        network.resetStatistics();
        Runnable task = pending.remove();
        Thread worker = new Thread(() -> {
            try { task.run(); } catch (Throwable error) { failure.set(error); }
        });
        worker.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 1000; i++) {
                network.resetStatistics();
                assertEquals(20, network.getUpload());
                assertEquals(50, network.getDownload());
            }
            assertTrue(pending.isEmpty());
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertEquals(50, network.getUpload());
        assertEquals(90, network.getDownload());
    }
}
