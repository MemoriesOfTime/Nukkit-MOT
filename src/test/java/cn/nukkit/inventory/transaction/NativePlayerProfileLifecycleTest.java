package cn.nukkit.inventory.transaction;

import cn.nukkit.*;
import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.lang.BaseLang;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native Player capture, close and fresh init; only the external profile writer is observed. */
class NativePlayerProfileLifecycleTest {
    @BeforeAll static void initialize() { NativeWorkstationOverflowTest.initialize(); }
    @AfterEach void reset() { MockServer.reset(); }

    @Test void directCaptureKeepsAuthoredUiInputsAndExcludesEveryOutputSlot() throws Exception {
        var f = fixture();
        for (int slot : new int[] {0, 1, 2, 3, 9, 16, 28, 32, 51, 52, 53}) {
            f.player.getUIInventory().setItemForce(slot, marked(slot));
        }
        for (int slot = 41; slot <= 50; slot++) f.player.getUIInventory().setItemForce(slot, marked(slot));
        f.player.saveNBT();
        var saved = f.player.namedTag.getList("PlayerUIItems", CompoundTag.class);
        assertEquals(11, saved.size());
        for (CompoundTag item : saved.getAll()) {
            int slot = item.getByte("Slot");
            assertFalse(slot >= 41 && slot <= 50);
            assertTrue(marked(slot).equalsExact(NBTIO.getItemHelper(item)));
        }
        assertEquals(36 * 64, f.player.namedTag.getList("Inventory", CompoundTag.class).getAll().stream()
                .map(NBTIO::getItemHelper).mapToInt(Item::getCount).sum());
    }

    @Test void provisionalDirectCaptureRefusesWithoutChangingAnEarlierSnapshot() throws Exception {
        var f = fixture(); f.player.saveNBT(); var before = f.player.namedTag.copy();
        try (var claim = InventoryMutationGuard.tryClaim(List.of(f.player.getUIInventory()))) {
            assertNotNull(claim);
            assertThrows(IllegalStateException.class, f.player::saveNBT);
            assertEquals(before, f.player.namedTag);
        }
    }

    @Test void freshNativeInitRestoresOnlyInputsAndConsumesTheStoredUiTagOnce() throws Exception {
        var f = fixture();
        var saved = new ListTag<CompoundTag>("PlayerUIItems")
                .add(NBTIO.putItemHelper(marked(1), 1))
                .add(NBTIO.putItemHelper(marked(51), 51))
                .add(NBTIO.putItemHelper(marked(50), 50))
                .add(NBTIO.putItemHelper(marked(41), 41));
        f.player.reload(new CompoundTag().putList(saved));
        assertTrue(marked(1).equalsExact(f.player.getUIInventory().getItem(1)));
        assertTrue(marked(51).equalsExact(f.player.getUIInventory().getItem(51)));
        assertTrue(f.player.getUIInventory().getItem(50).isNull());
        assertTrue(f.player.getUIInventory().getItem(41).isNull());
        assertFalse(f.player.namedTag.contains("PlayerUIItems"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void saveAndDisconnectDuringAClaimFinishAfterReleaseInEitherOrder(boolean closeFirst) throws Exception {
        var f = fixture(); var saved = new ArrayList<CompoundTag>(); var quits = new AtomicInteger();
        observeSaves(f, saved);
        f.events(event -> { if (event instanceof PlayerQuitEvent) quits.incrementAndGet(); });
        try (var claim = InventoryMutationGuard.tryClaim(List.of(f.player.getUIInventory(), f.player.getInventory()))) {
            assertNotNull(claim);
            if (closeFirst) { f.player.close(); assertTrue(f.player.isConnected(), "claimed close is deferred before a subsequent save"); f.player.save(); }
            else { f.player.save(); f.player.close(); }
            f.player.close();
            assertTrue(f.player.isConnected());
            assertNotNull(f.player.getInventory());
            assertEquals(0, quits.get());
            assertTrue(saved.isEmpty());
        }
        assertFalse(f.player.isConnected()); assertTrue(f.player.closed);
        assertNull(f.player.getInventory());
        assertEquals(1, quits.get());
        assertFalse(saved.isEmpty());
    }

    @Test void actualCloseWithRefusedWorldDropPersistsRetainedInputForFreshLoginAndRetry() throws Exception {
        var f = fixture(); var saved = new ArrayList<CompoundTag>(); observeSaves(f, saved);
        var closes = new AtomicInteger();
        f.events(event -> {
            if (event instanceof InventoryCloseEvent) closes.incrementAndGet();
            if (event instanceof cn.nukkit.event.entity.ItemSpawnEvent spawn) spawn.getEntity().close();
        });
        f.player.close();
        assertTrue(f.player.closed); assertNull(f.player.getInventory());
        assertEquals(1, closes.get()); assertTrue(f.world.isEmpty());
        assertFalse(saved.isEmpty());
        var latest = saved.get(saved.size() - 1);
        assertEquals(f.input.getCount(), latest.getList("PlayerUIItems", CompoundTag.class).getAll().stream()
                .map(NBTIO::getItemHelper).filter(item -> item.equals(f.input, true, true)).mapToInt(Item::getCount).sum());
        var fresh = fixture(); fresh.player.reload(latest);
        assertTrue(f.input.equalsExact(fresh.player.getUIInventory().getItem(1)));
        // A fresh ordinary native window can return the recovered input when capacity is available.
        fresh.player.getInventory().setItemForce(0, Item.AIR_ITEM);
        var window = new AnvilInventory(fresh.player.getUIInventory(), new cn.nukkit.level.Position(0,64,0,fresh.level));
        fresh.player.bind(window, Player.ANVIL_WINDOW_ID);
        fresh.player.removeWindow(window);
        assertTrue(fresh.player.getUIInventory().getItem(1).isNull());
        assertTrue(f.input.equalsExact(fresh.player.getInventory().getItem(0)));
        assertTrue(fresh.world.isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void serializerCloseAndRepeatedSaveFinishAfterAdmissionEvenWhenTheListenerThrows(boolean throwing) throws Exception {
        var f = fixture(); var store = new ProfileStore(f); var serializations = new AtomicInteger(); var quits = new AtomicInteger();
        var failure = new AssertionError("serialize callback after close request");
        Item finalInput = marked(1);
        f.events(event -> {
            if (event instanceof PlayerQuitEvent) quits.incrementAndGet();
            if (event instanceof cn.nukkit.event.entity.ItemSpawnEvent spawn) spawn.getEntity().close();
            if (event instanceof cn.nukkit.event.server.PlayerDataSerializeEvent && serializations.incrementAndGet() == 1) {
                f.player.close(); f.player.close();
                f.player.getUIInventory().setItemForce(1, finalInput);
                f.player.save(); f.player.save();
                if (throwing) throw failure;
            }
        });
        if (throwing) assertSame(failure, assertThrows(AssertionError.class, () -> f.player.save(true)));
        else assertDoesNotThrow(() -> f.player.save(true));
        assertTrue(f.player.closed); assertNull(f.player.getInventory());
        assertEquals(1, quits.get()); assertEquals(2, serializations.get());
        assertEquals(throwing ? 1 : 2, store.writes);
        for (var task : store.tasks) task.onRun(1);
        assertEquals(throwing ? 1 : 2, store.writes);
        assertTrue(store.pending.isEmpty()); assertTrue(f.world.isEmpty());
        var saved = store.read();
        assertEquals(3, saved.getList("PlayerUIItems", CompoundTag.class).getAll().stream().map(NBTIO::getItemHelper)
                .filter(item -> item.equals(finalInput, true, true)).mapToInt(Item::getCount).sum());
        var fresh = fixture(); fresh.player.reload(saved);
        assertTrue(finalInput.equalsExact(fresh.player.getUIInventory().getItem(1)));
    }

    static final class ProfileStore {
        final List<cn.nukkit.scheduler.Task> tasks = new ArrayList<>();
        final java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<cn.nukkit.scheduler.Task>> pending = new java.util.concurrent.ConcurrentHashMap<>();
        byte[] bytes; int writes;
        ProfileStore(NativeWorkstationOverflowTest.Fixture f) throws Exception {
            Server server = MockServer.get();
            inject(server, "playerDataLocks", new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>());
            inject(server, "pendingPlayerDataSaves", pending); inject(server, "pluginManager", f.plugins);
            inject(server, "playerDataSerializer", new cn.nukkit.utils.PlayerDataSerializer() {
                public Optional<java.io.InputStream> read(String name, UUID id) { return Optional.ofNullable(bytes).map(java.io.ByteArrayInputStream::new); }
                public java.io.OutputStream write(String name, UUID id) {
                    return new java.io.ByteArrayOutputStream() {
                        boolean closed;
                        public void close() throws java.io.IOException {
                            if (closed) return; closed = true; super.close(); bytes = toByteArray(); writes++;
                        }
                    };
                }
            });
            when(server.shouldSavePlayerData()).thenReturn(true);
            doCallRealMethod().when(server).saveOfflinePlayerData(anyString(), any(CompoundTag.class), anyBoolean());
            var scheduler = mock(cn.nukkit.scheduler.ServerScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.scheduleTask(any(cn.nukkit.plugin.Plugin.class), any(Runnable.class), eq(true))).thenAnswer(call -> {
                cn.nukkit.scheduler.Task task = call.getArgument(1);
                var handler = new cn.nukkit.scheduler.TaskHandler(call.getArgument(0), task, tasks.size()+1, true);
                task.setHandler(handler); tasks.add(task); return handler;
            });
        }
        CompoundTag read() throws java.io.IOException { return NBTIO.readCompressed(new java.io.ByteArrayInputStream(bytes)); }
        void inject(Server server, String name, Object value) throws Exception {
            Field field = Server.class.getDeclaredField(name); field.setAccessible(true); field.set(server, value);
        }
    }

    static Item marked(int slot) {
        Item item = Item.get(Item.EMERALD, 0, 3);
        item.setNamedTag(new CompoundTag().putString("example:input", "slot-" + slot));
        return item;
    }
    static NativeWorkstationOverflowTest.Fixture fixture() throws Exception {
        var f = new NativeWorkstationOverflowTest.Fixture(NativeWorkstationOverflowTest.Route.ANVIL_CLOSE);
        f.player.configureProfile();
        when(f.level.getFolderName()).thenReturn("world");
        Field list = Server.class.getDeclaredField("playerList"); list.setAccessible(true); list.set(MockServer.get(), new HashMap<>());
        when(MockServer.get().getLanguage()).thenReturn(new BaseLang("eng"));
        return f;
    }
    static void observeSaves(NativeWorkstationOverflowTest.Fixture f, List<CompoundTag> saved) {
        doAnswer(call -> { saved.add(((CompoundTag) call.getArgument(1)).copy()); return null; })
                .when(MockServer.get()).saveOfflinePlayerData(anyString(), any(CompoundTag.class), anyBoolean());
    }
}
