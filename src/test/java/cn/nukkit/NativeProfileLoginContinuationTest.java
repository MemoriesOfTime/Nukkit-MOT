package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.event.player.PlayerExperienceChangeEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.event.server.PlayerDataSerializeEvent;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.PlayStatusPacket;
import cn.nukkit.network.protocol.ResourcePacksInfoPacket;
import cn.nukkit.network.protocol.types.DisconnectFailReason;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.network.session.login.NetworkSessionState;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.*;
import org.iq80.leveldb.DB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(20)
class NativeProfileLoginContinuationTest {
    @TempDir Path directory;

    @Test void actualExperienceListenerClosingPlayerStopsInitialSaveAndEntityInitialization() throws Exception {
        try (var f = new Fixture(directory)) {
            doAnswer(call -> { f.experiences.incrementAndGet(); f.player.close("", "experience listener"); return null; })
                    .when(f.plugins).callEvent(any(PlayerExperienceChangeEvent.class));
            f.player.processLogin(); f.pump(6);
            assertEquals(1, f.experiences.get()); assertEquals("experience listener", f.player.reason);
            assertFalse(f.player.loginDataProcessed); assertEquals(1, f.readDisk().getInt("EXP"));
            verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
            verify(f.session, never()).sendPacket(any(PlayStatusPacket.class));
        }
    }

    @Test void actualSerializeListenerCloseCannotAdmitTheInitialOuterSnapshot() throws Exception {
        try (var f = new Fixture(directory)) {
            doAnswer(call -> {
                if (f.experiences.get() > 0) f.player.close("", "serialize listener"); return null;
            }).when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
            f.player.processLogin(); f.pump(6);
            assertEquals(1, f.experiences.get()); assertEquals("serialize listener", f.player.reason);
            assertTrue(f.writes.isEmpty()); assertEquals(1, f.readDisk().getInt("EXP"));
            verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        }
    }

    @Test void actualLoginSuccessPacketListenerCloseStopsSuperInitAndResourcePacketsAfterInitialAck() throws Exception {
        try (var f = new Fixture(directory)) {
            f.server.callDataPkSendEv = true;
            doAnswer(call -> {
                DataPacketSendEvent event = call.getArgument(0);
                if (event.getPacket() instanceof PlayStatusPacket) f.player.close("", "packet listener"); return null;
            }).when(f.plugins).callEvent(any(DataPacketSendEvent.class));
            f.player.processLogin(); f.pump(8);
            assertEquals("packet listener", f.player.reason); assertEquals(1, f.experiences.get());
            verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
            verify(f.session, never()).sendPacket(any(ResourcePacksInfoPacket.class));
        }
    }

    @Test void queuedAndCompletedSupersededObservationsRetryInsteadOfApplyingOrKicking() throws Exception {
        for (boolean completed : List.of(false, true)) {
            try (var f = new Fixture(directory.resolve(Boolean.toString(completed)))) {
                f.stopAtExperience(); f.player.processLogin();
                if (completed) f.runRead(); // inspect completed but not consumed
                f.server.saveOfflinePlayerDataAsync(f.id, Fixture.profile(9));
                if (!completed) f.runRead(); // old queued inspect sees invalid generation
                f.runWrite(); f.pump(8);
                assertEquals(9, f.player.getExperience()); assertEquals("experience boundary", f.player.reason);
                assertEquals(1, f.experiences.get());
            }
        }
    }

    @Test void failedCheckpointNeverLoadsOldDiskBytesOrCreatesFreshProfile() throws Exception {
        try (var f = new Fixture(directory)) {
            f.queue.submit(f.id.toString(), () -> { throw new IOException("disk failure"); }); f.runWrite();
            f.player.processLogin(); f.pump(3);
            assertNotNull(f.player.reason); assertEquals(0, f.experiences.get());
            assertEquals(1, f.readDisk().getInt("EXP"));
        }
    }

    @Test void legacyMainReaderReentryInvalidatesItsResultAndPreservesTheVirtualWriteHook() throws Exception {
        try (var f = new Fixture(directory)) {
            AtomicInteger reads = new AtomicInteger(), writes = new AtomicInteger();
            byte[][] bytes = {Files.readAllBytes(f.file)};
            PlayerDataSerializer custom = new PlayerDataSerializer() {
                public Optional<InputStream> read(String key, UUID uuid) throws IOException {
                    assertSame(f.main, Thread.currentThread());
                    byte[] observed = bytes[0];
                    if (reads.getAndIncrement() == 0) f.server.saveOfflinePlayerDataAsync(f.id, Fixture.profile(7));
                    return Optional.of(new ByteArrayInputStream(observed));
                }
                public OutputStream write(String key, UUID uuid) {
                    writes.incrementAndGet();
                    return new ByteArrayOutputStream() { public void close() throws IOException { super.close(); bytes[0] = toByteArray(); } };
                }
            };
            field(Server.class, f.server, "playerDataSerializer", custom);
            f.stopAtExperience(); f.player.processLogin(); f.pump(10);
            assertEquals(7, f.player.getExperience()); assertEquals(1, f.experiences.get()); assertEquals(1, writes.get());
            assertTrue(reads.get() >= 2); assertEquals(1, f.readDisk().getInt("EXP"));
        }
    }

    @Test void eligibleCorruptPreviousSourceFailsClosedButProtectedSourceIsNeverRead() throws Exception {
        for (boolean protectedIdentity : List.of(false, true)) {
            try (var f = new Fixture(directory.resolve("eligible-" + protectedIdentity))) {
                Files.delete(f.file);
                UUID previous = protectedIdentity ? UUID.fromString("3c14031c-69c0-30bb-8339-5ee69712b3e5") : UUID.randomUUID();
                Path source = f.file.getParent().resolve(previous + ".dat"); Files.writeString(source, "corrupt source");
                var migration = f.server.migratePlayerDataForLogin(previous, f.id, "tester", () -> f.player.connected);
                f.runWrite();
                if (protectedIdentity) assertEquals(PlayerDataMigrator.Result.SKIPPED, migration.toCompletableFuture().join());
                else {
                    assertThrows(CompletionException.class, () -> migration.toCompletableFuture().join());
                    f.player.processLogin(); f.pump(3); assertNotNull(f.player.reason); assertEquals(0, f.experiences.get());
                }
                assertEquals("corrupt source", Files.readString(source)); assertFalse(Files.exists(f.file));
            }
        }
    }

    @Test void eligibleMissingPreviousSourceKeepsTheExistingSafeSkippedResult() throws Exception {
        try (var f = new Fixture(directory)) {
            Files.delete(f.file); UUID previous = UUID.randomUUID();
            var migration = f.server.migratePlayerDataForLogin(previous, f.id, "tester", () -> f.player.connected);
            f.runWrite(); assertEquals(PlayerDataMigrator.Result.SKIPPED, migration.toCompletableFuture().join());
            assertFalse(Files.exists(f.file));
        }
    }

    @Test void renamedCanonicalTargetIsRepublishedOnExactUnknownMigrationRecovery() throws Exception {
        try (var f = new Fixture(directory)) {
            UUID previous = UUID.randomUUID(); String source = previous.toString();
            var failed = f.queue.migrateVerified(source, Set.of(source, f.id.toString(), "tester"), recovering -> {
                throw new IOException("prior rename published; directory ACK unknown");
            }, null);
            f.runWrite(); assertThrows(CompletionException.class, () -> failed.completion().toCompletableFuture().join());
            var selected = spy(new DefaultPlayerDataSerializer(f.file.getParent().getParent().toString()));
            field(Server.class, f.server, "playerDataSerializer", selected);
            var retry = f.server.migratePlayerDataForLogin(previous, f.id, "tester", () -> f.player.connected);
            f.runWrite(); assertEquals(PlayerDataMigrator.Result.MIGRATED, retry.toCompletableFuture().join());
            verify(selected).writeAtomicallyAcknowledged(eq(f.id.toString()), eq(f.id), any(CompoundTag.class));
            assertEquals(1, f.readDisk().getInt("EXP"));
        }
    }

    @Test void firstReadSelectionListenerCloseRegistersNoLeakedObservation() throws Exception {
        try (var f = new Fixture(directory)) {
            doAnswer(call -> { f.player.close("", "reader selection"); return null; })
                    .when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
            f.player.processLogin(); f.runRead(); f.tick();
            assertEquals("reader selection", f.player.reason); assertEquals(0, f.experiences.get());
            List<PlayerDataSaveQueue.ReadHandle<Integer>> handles = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                var handle = f.queue.read(Set.of("quota-" + i), current -> 1);
                assertFalse(handle.completion().toCompletableFuture().isCompletedExceptionally()); handles.add(handle);
            }
            handles.forEach(PlayerDataSaveQueue.ReadHandle::close);
        }
    }

    @Test void eachMigrationSelectionCallbackIsFencedBeforeAnyMoveOrDelete() throws Exception {
        for (boolean legacy : List.of(false, true)) for (int callback : List.of(1, 2)) {
            try (var f = new Fixture(directory.resolve(legacy + "-" + callback))) {
                Files.delete(f.file); UUID previous = UUID.randomUUID();
                String sourceKey = legacy ? "tester" : previous.toString();
                Path source = f.file.getParent().resolve(sourceKey + ".dat");
                try (OutputStream output = Files.newOutputStream(source)) { NBTIO.writeGZIPCompressed(Fixture.profile(8), output); }
                byte[] original = Files.readAllBytes(source); AtomicInteger selections = new AtomicInteger();
                doAnswer(call -> { if (selections.incrementAndGet() == callback) f.player.close("", "migration selection"); return null; })
                        .when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
                assertThrows(CancellationException.class, () -> {
                    if (legacy) f.server.migrateLegacyPlayerDataForLogin("tester", f.id, () -> f.player.connected);
                    else f.server.migratePlayerDataForLogin(previous, f.id, "tester", () -> f.player.connected);
                });
                assertEquals(callback, selections.get()); assertTrue(f.writes.isEmpty());
                assertArrayEquals(original, Files.readAllBytes(source)); assertFalse(Files.exists(f.file));
            }
        }
    }

    @Test void synchronousWorkerReentryRefusesBeforeSchedulingAMainSideEffect() throws Exception {
        try (var f = new Fixture(directory)) {
            var job = f.queue.submit("outer", () -> {
                assertThrows(IllegalStateException.class, () -> f.server.saveOfflinePlayerData(f.id, Fixture.profile(9), false)); return true;
            });
            f.runWrite(); assertTrue(job.toCompletableFuture().join()); assertTrue(f.writes.isEmpty());
            verify(f.server.getScheduler(), never()).scheduleTask(any(Plugin.class), any(Runnable.class));
            assertEquals(1, f.readDisk().getInt("EXP"));
        }
    }

    @Test void mainHandoffCapacityRejectsBeforeCopyAndCancellationCompletesWithoutIo() throws Exception {
        try (var f = new Fixture(directory)) {
            doReturn(false).when(f.server).isPrimaryThread();
            AtomicInteger credits = new AtomicInteger(256), copies = new AtomicInteger();
            field(Server.class, f.server, "profileCaptureHandoffs", credits);
            CompoundTag profile = new CompoundTag() { @Override public CompoundTag copy() { copies.incrementAndGet(); return super.copy(); } };
            assertThrows(RejectedExecutionException.class, () -> f.server.saveOfflinePlayerData(f.id, profile, true));
            assertEquals(0, copies.get()); assertEquals(256, credits.get());
            credits.set(0); List<cn.nukkit.scheduler.Task> captured = new ArrayList<>();
            var scheduler = f.server.getScheduler();
            doAnswer(call -> {
                cn.nukkit.scheduler.Task task = call.getArgument(1); captured.add(task);
                return new TaskHandler(null, task, 1, false);
            }).when(scheduler).scheduleTask(any(Plugin.class), any(Runnable.class));
            f.server.saveOfflinePlayerData(f.id, profile, true);
            assertEquals(1, copies.get()); assertEquals(1, credits.get());
            captured.get(0).onCancel(); captured.get(0).onRun(1); captured.get(0).onCancel();
            assertEquals(0, credits.get()); assertTrue(f.writes.isEmpty());
            verify(f.plugins, never()).callEvent(any(PlayerDataSerializeEvent.class));
        }
    }

    @Test void twoPendingLoginsRecheckKeepExistingAfterFirstBecomesOnline() throws Exception {
        try (var f = new Fixture(directory)) {
            doReturn(true).when(f.server).isDuplicateLoginKeepingExistingSession();
            Probe newcomer = f.player("Tester", f.id);
            f.player.processLogin(); newcomer.processLogin();
            // Finish the first profile phase, then the canonical online registration takes over the claim.
            Object flow = fieldValue(Player.class, f.player, "nativeProfileLogin");
            Method complete = flow.getClass().getDeclaredMethod("complete"); complete.setAccessible(true); complete.invoke(flow);
            f.player.loginDataProcessed = true; f.player.loggedIn = true;
            f.server.playerList.put(f.id, f.player); f.server.releaseLoginProfile(f.player);
            f.tick();
            assertEquals("disconnectionScreen.loggedinOtherLocation", newcomer.reason);
            assertNull(f.player.reason); assertTrue(f.player.connected);
        }
    }

    @Test void repeatedRejectedFinalCloseRetainsOwnerAndQuitListenerIsSingleShot() throws Exception {
        try (var f = new Fixture(directory)) {
            Player old = mock(Player.class, CALLS_REAL_METHODS);
            field(Entity.class, old, "server", f.server); field(EntityHuman.class, old, "uuid", f.id);
            old.username = "Tester"; old.connected = true; old.loggedIn = true;
            field(Player.class, old, "hiddenPlayers", new HashMap<>());
            doNothing().when(old).removeAllWindows(true); doNothing().when(old).resetCraftingGridType();
            doThrow(new RejectedExecutionException("bounded queue full")).when(old).save(true);
            AtomicInteger quits = new AtomicInteger();
            doAnswer(call -> { quits.incrementAndGet(); return null; }).when(f.plugins).callEvent(any(PlayerQuitEvent.class));
            old.close(new TextContainer("leave"), "left", false); old.close();
            assertEquals(1, quits.get()); verify(old, times(2)).save(true);
            assertFalse(old.connected); assertFalse(old.isClosed()); assertTrue(old.loggedIn);
            assertNotNull(fieldValue(Player.class, old, "pendingPlayerCloseContinuation"));
            verify(f.server, atLeastOnce()).deferPlayerClose(eq(old), any(Runnable.class));
        }
    }

    @Test void onlyDesignatedTerminatedMainShutdownOwnerMayAdmitTheFinalCheckpoint() throws Exception {
        try (var f = new Fixture(directory)) {
            Thread terminated = new Thread(() -> { }); terminated.start(); terminated.join(1_000);
            assertEquals(Thread.State.TERMINATED, terminated.getState());
            field(Server.class, f.server, "currentThread", terminated);
            field(Server.class, f.server, "shutdownHookWorker", Thread.currentThread());
            doReturn(false).when(f.server).isPrimaryThread();
            assertTrue(f.server.isProfileOwnerThread());
            assertFalse(f.io.submit(f.server::isProfileOwnerThread).get(3, TimeUnit.SECONDS));
            var checkpoint = f.server.saveOfflinePlayerDataAsync(f.id, Fixture.profile(5));
            f.runWrite(); assertNotNull(checkpoint.toCompletableFuture().join());
            assertEquals(5, f.readDisk().getInt("EXP"));
        }
    }

    @Test void retiredPlayerCannotRecreateItsOldProfilePath() throws Exception {
        try (var f = new Fixture(directory)) {
            f.player.closed = true;
            assertThrows(IllegalStateException.class, () -> f.player.save(true));
            assertTrue(f.writes.isEmpty());
        }
    }

    static final class Fixture implements AutoCloseable {
        final Thread main = Thread.currentThread();
        final Server server = mock(Server.class, CALLS_REAL_METHODS);
        final PluginManager plugins = mock(PluginManager.class);
        final Level level = mock(Level.class);
        final UUID id = UUID.randomUUID();
        final Queue<Runnable> mainTasks = new ArrayDeque<>(), reads = new ArrayDeque<>(), writes = new ArrayDeque<>();
        final PlayerDataSaveQueue queue = ManualPlayerDataQueue.create(writes::add, reads::add, 16);
        final ExecutorService io = Executors.newSingleThreadExecutor();
        final AtomicInteger experiences = new AtomicInteger();
        final Path file;
        final Probe player;
        final NetworkPlayerSession session;
        Fixture(Path directory) throws Exception {
            Files.createDirectories(directory.resolve("players")); file = directory.resolve("players/" + id + ".dat");
            try (OutputStream output = Files.newOutputStream(file)) { NBTIO.writeGZIPCompressed(profile(1), output); }
            field(Server.class, server, "playerDataLocks", Server.createPlayerDataLocks());
            field(Server.class, server, "playerDataSaves", queue); field(Server.class, server, "playerDataSerializer", new DefaultPlayerDataSerializer(directory.toString()));
            field(Server.class, server, "pluginManager", plugins); field(Server.class, server, "nameLookup", mock(DB.class));
            field(Server.class, server, "playerList", new LinkedHashMap<UUID, Player>());
            field(Server.class, server, "players", new HashMap<>());
            field(Server.class, server, "pendingPlayerCloses", new ConcurrentHashMap<>());
            doReturn(directory.toString()).when(server).getDataPath(); doReturn(true).when(server).isWhitelisted(anyString());
            doReturn(true).when(server).getAutoSave(); doReturn(true).when(server).shouldSavePlayerData();
            doReturn(level).when(server).getLevelByName("world"); doReturn(level).when(server).getDefaultLevel();
            doReturn(mock(MainLogger.class)).when(server).getLogger();
            doAnswer(call -> Thread.currentThread() == main).when(server).isPrimaryThread();
            doAnswer(call -> { assertSame(main, Thread.currentThread()); experiences.incrementAndGet(); return null; })
                    .when(plugins).callEvent(any(PlayerExperienceChangeEvent.class));
            doAnswer(call -> { assertSame(main, Thread.currentThread()); return null; })
                    .when(plugins).callEvent(any(PlayerDataSerializeEvent.class));
            server.strongIPBans = true; server.savePlayerDataByUuid = true; server.networkLoginTimeoutMilliseconds = 25_000;
            var scheduler = mock(ServerScheduler.class); doReturn(scheduler).when(server).getScheduler();
            doAnswer(call -> { Runnable task = call.getArgument(1); mainTasks.add(task); return new TaskHandler(null, task, 1, false); })
                    .when(scheduler).scheduleDelayedTask(any(Plugin.class), any(Runnable.class), anyInt());
            player = player("Tester", id); session = player.networkSession;
        }
        Probe player(String name, UUID uuid) throws Exception {
            Probe result = mock(Probe.class, CALLS_REAL_METHODS); result.connected = true; result.username = name;
            field(Entity.class, result, "server", server); field(EntityHuman.class, result, "uuid", uuid);
            field(Player.class, result, "achievements", new HashSet<>()); result.fogStack = new ArrayList<>();
            result.loginChainData = mock(LoginChainData.class);
            NetworkPlayerSession session = mock(NetworkPlayerSession.class); var state = new NetworkSessionState();
            state.getLogin().setPhase(SessionLoginPhase.PRE_LOGIN); when(session.getState()).thenReturn(state);
            field(Player.class, result, "networkSession", session); doReturn(false).when(result).isBanned();
            return result;
        }
        void stopAtExperience() {
            doAnswer(call -> { experiences.incrementAndGet(); player.close("", "experience boundary"); return null; })
                    .when(plugins).callEvent(any(PlayerExperienceChangeEvent.class));
        }
        void tick() { int count = mainTasks.size(); for (int i = 0; i < count; i++) mainTasks.remove().run(); }
        void runRead() throws Exception { Runnable task = reads.poll(); assertNotNull(task); io.submit(task).get(3, TimeUnit.SECONDS); }
        void runWrite() throws Exception { Runnable task = writes.poll(); assertNotNull(task); io.submit(task).get(3, TimeUnit.SECONDS); }
        void pump(int ticks) throws Exception {
            for (int i = 0; i < ticks; i++) {
                while (!reads.isEmpty()) runRead(); while (!writes.isEmpty()) runWrite(); tick();
            }
        }
        CompoundTag readDisk() throws IOException { try (InputStream input = Files.newInputStream(file)) { return NBTIO.readCompressed(input); } }
        static CompoundTag profile(int exp) {
            return new CompoundTag().putLong("firstPlayed", 1).putLong("lastPlayed", 3).putInt("EXP", exp)
                    .putInt("expLevel", 1).putInt("playerGameType", 0).putShort("Health", 20).putString("Level", "world")
                    .putCompound("Achievements", new CompoundTag())
                    .putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", 0)).add(new DoubleTag("", 64)).add(new DoubleTag("", 0)))
                    .putList(new ListTag<StringTag>("fogIdentifiers")).putList(new ListTag<StringTag>("userProvidedFogIds"));
        }
        @Override public void close() throws Exception {
            Object flow = fieldValue(Player.class, player, "nativeProfileLogin");
            if (flow != null) { Method cancel = flow.getClass().getDeclaredMethod("cancel"); cancel.setAccessible(true); cancel.invoke(flow); }
            while (!writes.isEmpty()) runWrite(); while (!reads.isEmpty()) runRead();
            queue.close(Duration.ZERO); io.shutdown(); assertTrue(io.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    static class Probe extends Player {
        String reason;
        Probe() { super(null, null, null); }
        @Override public void close(TextContainer message, String reason, boolean notify, DisconnectFailReason failure) {
            this.reason = reason; connected = false; closed = true;
            super.close(message, reason, false, failure); // Exercise real native cancellation prefix without world retirement fixtures.
        }
    }
    static void field(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    static Object fieldValue(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
}
