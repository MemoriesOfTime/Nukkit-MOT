package cn.nukkit.inventory.transaction;

import cn.nukkit.*;
import cn.nukkit.event.server.ServerStopEvent;
import cn.nukkit.inventory.InventoryMutationGuard;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class NativeShutdownRequestTest {
    @BeforeAll static void initialize() { NativeWorkstationOverflowTest.initialize(); }
    @AfterEach void reset() { MockServer.reset(); }

    @Test void foreignForceShutdownCannotBeginTeardownWhileTheOwnerHasAnActiveNativeClaim() throws Exception {
        var f = NativePlayerProfileLifecycleTest.fixture();
        Server server = MockServer.get();
        AtomicBoolean running = new AtomicBoolean(true);
        inject(server, "isRunning", running); inject(server, "pluginManager", f.plugins); inject(server, "hasStopped", false);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            try (var claim = InventoryMutationGuard.tryClaim(List.of(f.player.getInventory(), f.player.getUIInventory()))) {
                if (claim == null) throw new AssertionError("native owner claim");
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("owner release deadline");
            } catch (Throwable failure) { ownerFailure.set(failure); }
        }, "native inventory owner");
        inject(server, "currentThread", owner); initializeShutdownFields(server);
        when(server.isPrimaryThread()).thenCallRealMethod(); when(server.getPrimaryThread()).thenCallRealMethod();
        doCallRealMethod().when(server).forceShutdown("native shutdown request");
        AtomicInteger stopEvents = new AtomicInteger();
        f.events(event -> {
            if (event instanceof ServerStopEvent) {
                stopEvents.incrementAndGet();
                throw new AssertionError("teardown entered before the native owner released");
            }
        });
        owner.start();
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            Throwable callerFailure = null;
            try { server.forceShutdown("native shutdown request"); }
            catch (Throwable failure) { callerFailure = failure; }
            assertNull(callerFailure, "foreign request returns without entering owner teardown");
            assertFalse(running.get());
            assertEquals(0, stopEvents.get());
            assertTrue(f.player.isConnected()); assertNotNull(f.player.getInventory());
        } finally { release.countDown(); owner.join(3000); }
        assertFalse(owner.isAlive()); assertNull(ownerFailure.get());
    }

    @Test void ownerShutdownWaitsForTheWholeNativeMutationThenPersistsBeforeDependencyRetirement() throws Exception {
        var f = NativePlayerProfileLifecycleTest.fixture();
        f.station.setItemForce(0, cn.nukkit.item.Item.AIR_ITEM);
        var store = new NativePlayerProfileLifecycleTest.ProfileStore(f);
        Server server = configureTeardown(f);
        var input = NativePlayerProfileLifecycleTest.marked(9);
        AtomicInteger disabled = new AtomicInteger();
        doAnswer(call -> {
            assertTrue(f.player.closed); assertNull(f.player.getInventory());
            assertNotNull(store.bytes, "sync profile completes before plugin/level retirement");
            assertEquals(3, store.read().getList("Inventory", cn.nukkit.nbt.tag.CompoundTag.class).getAll().stream()
                    .map(cn.nukkit.nbt.NBTIO::getItemHelper).filter(i -> i.equals(input, true, true))
                    .mapToInt(cn.nukkit.item.Item::getCount).sum());
            disabled.incrementAndGet(); return null;
        }).when(server).disablePlugins();
        try (var verifier = mockStatic(cn.nukkit.network.encryption.LoginChainVerifier.class);
             var handlers = mockStatic(cn.nukkit.event.HandlerList.class)) {
            verifier.when(cn.nukkit.network.encryption.LoginChainVerifier::shared)
                    .thenReturn(mock(cn.nukkit.network.encryption.LoginChainVerifier.class));
            try (var claim = InventoryMutationGuard.tryClaim(List.of(f.player.getInventory(), f.player.getUIInventory()))) {
                assertNotNull(claim);
                server.forceShutdown("owner request");
                assertEquals(0, disabled.get()); assertTrue(f.player.isConnected());
                try (var write = claim.expectWrite(f.player.getInventory(), 0, input)) {
                    assertTrue(f.player.getInventory().setItem(0, input));
                }
            }
        }
        assertEquals(1, disabled.get());
        assertTrue(completion(server).isDone()); assertFalse(completion(server).isCompletedExceptionally());
    }

    @Test void aClaimOpenedByTheStopEventAlsoDelaysDependenciesWithoutRepeatingTheEvent() throws Exception {
        var f = NativePlayerProfileLifecycleTest.fixture();
        f.station.setItemForce(0, cn.nukkit.item.Item.AIR_ITEM);
        var store = new NativePlayerProfileLifecycleTest.ProfileStore(f);
        Server server = configureTeardown(f);
        var held = new AtomicReference<InventoryMutationGuard.Lease>();
        AtomicInteger stops = new AtomicInteger(), disabled = new AtomicInteger();
        f.events(event -> {
            if (event instanceof ServerStopEvent) {
                stops.incrementAndGet(); held.set(InventoryMutationGuard.tryClaim(List.of(f.player.getInventory())));
                assertNotNull(held.get());
            }
        });
        doAnswer(call -> { disabled.incrementAndGet(); return null; }).when(server).disablePlugins();
        try (var verifier = mockStatic(cn.nukkit.network.encryption.LoginChainVerifier.class);
             var handlers = mockStatic(cn.nukkit.event.HandlerList.class)) {
            verifier.when(cn.nukkit.network.encryption.LoginChainVerifier::shared)
                    .thenReturn(mock(cn.nukkit.network.encryption.LoginChainVerifier.class));
            try {
                server.forceShutdown("event claim");
                assertEquals(0, disabled.get(), "an event-owned claim is still a live native mutation");
                assertTrue(f.player.isConnected()); assertNull(store.bytes);
            } finally { if (held.get() != null) held.get().close(); }
        }
        assertEquals(1, stops.get()); assertEquals(1, disabled.get()); assertNotNull(store.bytes);
    }

    @Test void terminatedOwnerStillAllowsOnlyOneConcurrentShutdownSequence() throws Exception {
        var f = NativePlayerProfileLifecycleTest.fixture(); f.station.setItemForce(0, cn.nukkit.item.Item.AIR_ITEM);
        new NativePlayerProfileLifecycleTest.ProfileStore(f);
        Server server = configureTeardown(f);
        Thread deadOwner = new Thread(() -> { }); deadOwner.start(); deadOwner.join(1000);
        assertEquals(Thread.State.TERMINATED, deadOwner.getState()); inject(server, "currentThread", deadOwner);
        CountDownLatch draining = new CountDownLatch(1), resume = new CountDownLatch(1);
        Player custom = mock(Player.class);
        doAnswer(call -> { draining.countDown(); assertTrue(resume.await(3, TimeUnit.SECONDS)); return null; }).when(custom).close();
        doCallRealMethod().when(server).deferPlayerClose(any(), any()); server.deferPlayerClose(custom, custom::close);
        AtomicInteger stops = new AtomicInteger();
        f.events(event -> { if (event instanceof ServerStopEvent) stops.incrementAndGet(); });
        var firstFailure = new AtomicReference<Throwable>(); var secondFailure = new AtomicReference<Throwable>();
        Thread first = new Thread(() -> runWithNativeShutdownDependencies(server, firstFailure));
        Thread second = new Thread(() -> runWithNativeShutdownDependencies(server, secondFailure));
        first.start();
        try {
            assertTrue(draining.await(2, TimeUnit.SECONDS)); second.start(); second.join(1500);
            assertFalse(second.isAlive(), "a second requester does not wait on the first callback");
            assertEquals(0, stops.get(), "the reserved first owner has not reached its stop event");
        } finally { resume.countDown(); first.join(3000); second.join(3000); }
        assertFalse(first.isAlive()); assertFalse(second.isAlive());
        assertNull(firstFailure.get()); assertNull(secondFailure.get()); assertEquals(1, stops.get());
    }

    @Test void throwingQueuedCloseCallbacksAreAllAttemptedAndCompleteShutdownExceptionally() throws Exception {
        var f = NativePlayerProfileLifecycleTest.fixture(); Server server = configureTeardown(f);
        doCallRealMethod().when(server).deferPlayerClose(any(), any());
        Error one = new AssertionError("first close extension"), two = new AssertionError("second close extension");
        AtomicInteger attempted = new AtomicInteger();
        Player first = mock(Player.class), second = mock(Player.class);
        doAnswer(call -> { attempted.incrementAndGet(); throw one; }).when(first).close();
        doAnswer(call -> { attempted.incrementAndGet(); throw two; }).when(second).close();
        server.deferPlayerClose(first, first::close); server.deferPlayerClose(second, second::close);
        Error actual = assertThrows(Error.class, () -> server.forceShutdown("queued close failure"));
        assertTrue(actual == one || actual == two); assertEquals(2, attempted.get());
        assertEquals(1, actual.getSuppressed().length);
        assertTrue(completion(server).isCompletedExceptionally(), "the hook must see the failed outcome without waiting for its deadline");
        server.forceShutdown("repeat failed request"); assertEquals(2, attempted.get());
    }

    static void runWithNativeShutdownDependencies(Server server, AtomicReference<Throwable> failure) {
        try (var verifier = mockStatic(cn.nukkit.network.encryption.LoginChainVerifier.class);
             var handlers = mockStatic(cn.nukkit.event.HandlerList.class)) {
            verifier.when(cn.nukkit.network.encryption.LoginChainVerifier::shared)
                    .thenReturn(mock(cn.nukkit.network.encryption.LoginChainVerifier.class));
            server.forceShutdown("concurrent terminated-owner request");
        } catch (Throwable thrown) { failure.set(thrown); }
    }

    @Test void aQuitListenerClaimKeepsThePlayerAndShutdownDependenciesAliveUntilRelease() throws Exception {
        var f = NativePlayerProfileLifecycleTest.fixture(); f.station.setItemForce(0, cn.nukkit.item.Item.AIR_ITEM);
        var store = new NativePlayerProfileLifecycleTest.ProfileStore(f); Server server = configureTeardown(f);
        var held = new AtomicReference<InventoryMutationGuard.Lease>();
        AtomicInteger quits = new AtomicInteger(), disabled = new AtomicInteger();
        var finalItem = NativePlayerProfileLifecycleTest.marked(12);
        f.events(event -> {
            if (event instanceof cn.nukkit.event.player.PlayerQuitEvent) {
                quits.incrementAndGet(); held.set(InventoryMutationGuard.tryClaim(List.of(f.player.getInventory(), f.player.getUIInventory())));
                assertNotNull(held.get());
                try (var write = held.get().expectWrite(f.player.getInventory(), 0, finalItem)) {
                    assertTrue(f.player.getInventory().setItem(0, finalItem));
                }
            }
        });
        doAnswer(call -> { assertTrue(f.player.closed); assertNotNull(store.bytes); disabled.incrementAndGet(); return null; })
                .when(server).disablePlugins();
        try (var verifier = mockStatic(cn.nukkit.network.encryption.LoginChainVerifier.class);
             var handlers = mockStatic(cn.nukkit.event.HandlerList.class)) {
            verifier.when(cn.nukkit.network.encryption.LoginChainVerifier::shared)
                    .thenReturn(mock(cn.nukkit.network.encryption.LoginChainVerifier.class));
            try {
                server.forceShutdown("quit listener claim");
                assertFalse(f.player.closed); assertNotNull(f.player.getInventory());
                assertNull(store.bytes); assertEquals(0, disabled.get());
            } finally { if (held.get() != null) held.get().close(); }
        }
        assertTrue(f.player.closed); assertNull(f.player.getInventory()); assertEquals(1, quits.get()); assertEquals(1, disabled.get());
        assertEquals(3, store.read().getList("Inventory", cn.nukkit.nbt.tag.CompoundTag.class).getAll().stream()
                .map(cn.nukkit.nbt.NBTIO::getItemHelper).filter(i -> i.equals(finalItem, true, true)).mapToInt(cn.nukkit.item.Item::getCount).sum());
    }

    @Test void realJvmHookDoesNotReenterRuntimeExitOnAClosedOwnerFailure(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        runExitProbe(directory, "hook-failure");
    }
    @Test void realJvmHookDoesNotWaitForTheOwnerAlreadyInsideRuntimeExit(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        runExitProbe(directory, "owner-exit");
    }
    @Test void realStartCompletesABriefRetainedMutationBeforeMainCanExit(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        runExitProbe(directory, "held-start");
    }
    @Test void terminatedOwnerHookStillSavesBeforeRetiringDependencies(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        runExitProbe(directory, "terminated-healthy");
    }
    @Test @Timeout(45) void terminatedOwnerHookHasAFiniteDeadlineEvenWhenANativeCallbackNeverReturns(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        runExitProbe(directory, "terminated-hang");
    }
    @Test @Timeout(45) void finiteHookKeepsTheLastCompleteStockProfileWhenAnAdmittedWriteIsInterrupted(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        runExitProbe(directory,"stock-writer-hang");
        assertTrue(java.nio.file.Files.exists(directory.resolve("stock-writer-entered.txt")),"the real stock writer must have started before the timeout");
        try(var input=java.nio.file.Files.newInputStream(directory.resolve("stock-data/players/nativecheckpoint.dat"))) {
            assertEquals("old-complete",cn.nukkit.nbt.NBTIO.readCompressed(input).getString("marker"));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"stop","quit","serialize"})
    void aLateNativeCallbackCannotStartFreshCaptureOrTeardownAfterTheHookDeadline(String phase) throws Exception {
        var f=NativePlayerProfileLifecycleTest.fixture();f.station.setItemForce(0,cn.nukkit.item.Item.AIR_ITEM);
        var store=new NativePlayerProfileLifecycleTest.ProfileStore(f);Server server=configureTeardown(f);
        CountDownLatch ownerDone=new CountDownLatch(1),entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread owner=new Thread(()->{try{ownerDone.await(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        owner.start();inject(server,"currentThread",owner);
        server.forceShutdown("native absolute deadline");ownerDone.countDown();owner.join(3000);assertFalse(owner.isAlive());
        // Shorten the already-created absolute request deadline; production uses thirty seconds.
        inject(server,"shutdownDeadlineNanos",System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(300));
        f.events(event->{
            boolean selected=phase.equals("stop")&&event instanceof ServerStopEvent
                    ||phase.equals("quit")&&event instanceof cn.nukkit.event.player.PlayerQuitEvent
                    ||phase.equals("serialize")&&event instanceof cn.nukkit.event.server.PlayerDataSerializeEvent;
            if(selected){entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("finite callback release");}
        });
        when(server.getPropertyString(anyString(),anyString())).thenAnswer(call->call.getArgument(1));
        doCallRealMethod().when(server).forceShutdown();
        var hook=Server.class.getDeclaredMethod("runShutdownHook");hook.setAccessible(true);
        doCallRealMethod().when(server);hook.invoke(server);
        var await=Server.class.getDeclaredMethod("awaitShutdownCompletion",long.class);await.setAccessible(true);
        doCallRealMethod().when(server);await.invoke(server,anyLong());
        Thread worker=null;
        try {
            hook.invoke(server);assertEquals(0,entered.getCount());assertTrue(server.isShutdownTimedOut());assertEquals(0,store.writes);
            Field field=Server.class.getDeclaredField("shutdownHookWorker");field.setAccessible(true);worker=(Thread)field.get(server);
        } finally {release.countDown();if(worker!=null)worker.join(3000);}
        assertTrue(worker==null||!worker.isAlive());assertEquals(0,store.writes);
        verify(server,never()).disablePlugins();verify(server,never()).unloadLevel(any(),anyBoolean());
        assertTrue(completion(server).isCompletedExceptionally());
    }
    static void runExitProbe(java.nio.file.Path directory, String mode) throws Exception {
        var marker = directory.resolve(mode + ".txt"); var output = directory.resolve(mode + ".log");
        String javaExecutable = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(javaExecutable, "--enable-native-access=ALL-UNNAMED", "-Xmx512m", "-XX:ActiveProcessorCount=2",
                "-cp", System.getProperty("java.class.path"), NativeShutdownHookExitProbe.class.getName(), mode, marker.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean exited;
        try { exited = process.waitFor((mode.equals("terminated-hang") || mode.equals("stock-writer-hang")) ? 38 : 8, TimeUnit.SECONDS); }
        finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS); } }
        assertTrue(exited, "the actual JVM shutdown hook must return finitely: " + java.nio.file.Files.readString(output));
        assertEquals(0, process.exitValue(), java.nio.file.Files.readString(output));
        var retained=java.nio.file.Path.of("target","native-shutdown-probes",mode);
        java.nio.file.Files.createDirectories(retained);
        try(var files=java.nio.file.Files.walk(directory)) {
            for(var source:files.filter(java.nio.file.Files::isRegularFile).toList()) {
                var destination=retained.resolve(directory.relativize(source));
                java.nio.file.Files.createDirectories(destination.getParent());
                java.nio.file.Files.copy(source,destination,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        java.nio.file.Files.writeString(retained.resolve("result.txt"),"exit_code="+process.exitValue()+"\nwait_finished="+exited+"\n");
        assertTrue(java.nio.file.Files.exists(marker), "actual hook did not return: " + java.nio.file.Files.readString(output));
        assertEquals(mode.equals("held-start") ? "profile saved before owner exit"
                : mode.equals("terminated-hang") ? "hook returned without a new profile" : "hook returned", java.nio.file.Files.readString(marker));
    }

    static Server configureTeardown(NativeWorkstationOverflowTest.Fixture f) throws Exception {
        Server server = MockServer.get(); initializeShutdownFields(server);
        inject(server, "currentThread", Thread.currentThread()); inject(server, "isRunning", new AtomicBoolean(true));
        inject(server, "hasStopped", false); inject(server, "pluginManager", f.plugins);
        var players = new java.util.HashMap<java.util.UUID, Player>(); players.put(f.player.getUniqueId(), f.player);
        inject(server, "players", players); inject(server, "levelArray", new cn.nukkit.level.Level[]{f.level});
        inject(server, "scheduler", server.getScheduler());
        Field console = Server.class.getDeclaredField("consoleThread"); console.setAccessible(true);
        console.set(server, mock(console.getType()));
        var network = mock(cn.nukkit.network.Network.class);
        when(network.getInterfaces()).thenReturn(java.util.Set.of()); inject(server, "network", network);
        inject(server, "baseLang", new cn.nukkit.lang.BaseLang("eng"));
        when(server.isPrimaryThread()).thenCallRealMethod(); when(server.getPrimaryThread()).thenCallRealMethod();
        doCallRealMethod().when(server).forceShutdown(anyString());
        return server;
    }
    static void initializeShutdownFields(Server server) throws Exception {
        inject(server, "requestedShutdownReason", null);
        inject(server, "shutdownAdvancing", false);
        inject(server, "shutdownAdvanceRequested", false);
        inject(server, "shutdownStopEventDispatched", false);
        inject(server, "shutdownPlayerClosingStarted", false);
        inject(server, "shutdownHookThread", null);
        inject(server, "shutdownHookWorker", null);
        inject(server, "shutdownDeadlineNanos", 0L);
        inject(server, "shutdownTimedOut", false);
        when(server.isShutdownTimedOut()).thenCallRealMethod();
        inject(server, "deferredShutdownIdentity", new Object());
        inject(server, "shutdownCompletion", new java.util.concurrent.CompletableFuture<Void>());
        inject(server, "pendingPlayerCloses", new java.util.concurrent.ConcurrentHashMap<Player,Runnable>());
    }
    @SuppressWarnings("unchecked") static java.util.concurrent.CompletableFuture<Void> completion(Server server) throws Exception {
        Field field=Server.class.getDeclaredField("shutdownCompletion"); field.setAccessible(true);
        return (java.util.concurrent.CompletableFuture<Void>) field.get(server);
    }

    static void inject(Object target, String name, Object value) throws Exception {
        Field field = Server.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
