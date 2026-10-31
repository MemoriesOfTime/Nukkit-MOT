package cn.nukkit.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.nukkit.Server;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.MainLogger;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Uses the native scheduler: directly calling a sleeping hopper would hide the regression. */
public class NativeHopperPolicyRetry28Test {
    private static Server server;
    private static Object previousServer;

    @BeforeAll
    static void initialize() throws Exception {
        MockServer.init();
        Block.init();
        server = mock(Server.class);
        Field singleton = Server.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        previousServer = singleton.get(null);
        singleton.set(null, server);
    }

    @AfterAll
    static void restoreServer() throws Exception {
        Field singleton = Server.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        singleton.set(null, previousServer);
    }

    @Test
    void allowedAfterEarlierCancellationMustResumeWithoutSlotWrites() throws Exception {
        try (Fixture f = fixture()) {
            f.tick(1);
            assertEquals(5, f.chest.getInventory().getItem(0).getCount());
            assertTrue(f.hopper.getInventory().isEmpty());
            f.deny.set(false);
            f.tick(32);
            assertFalse(f.hopper.getInventory().isEmpty(),
                    "Allowed transfer must resume without inventory edits after an earlier refusal");
            assertEquals(5, units(f.chest.getInventory()) + units(f.hopper.getInventory()));
            assertEquals("resume28", f.hopper.getInventory().getItem(0).getNamedTag().getString("identity"));
        }
    }

    @Test
    void permanentCancellationPreservesOriginalSource() throws Exception {
        try (Fixture f = fixture()) {
            f.tick(32);
            assertEquals(5, f.chest.getInventory().getItem(0).getCount());
            assertTrue(f.hopper.getInventory().isEmpty());
            assertEquals(4, f.attempts.get(), "Permanent veto must retry once per eight ticks, not every tick");
        }
    }

    @Test
    void realSourceSetterKeepsOrdinaryWakeAndTransfer() throws Exception {
        try (Fixture f = fixture()) {
            f.tick(1);
            f.deny.set(false);
            Item six = f.chest.getInventory().getItem(0);
            six.setCount(6);
            assertTrue(f.chest.getInventory().setItem(0, six));
            assertTrue(f.queue.contains(f.hopper));
            f.tick(8);
            assertEquals(5, f.chest.getInventory().getItem(0).getCount());
            assertEquals(1, f.hopper.getInventory().getItem(0).getCount());
        }
    }


    private static int units(Inventory inventory) {
        return inventory.getContents().values().stream().filter(item -> !item.isNull()).mapToInt(Item::getCount).sum();
    }

    @Test
    void deniedUpperSourceDoesNotBlockAllowedPushOfOwnedItems() throws Exception {
        try (Fixture f = fixture()) {
            BlockEntityChest destination = new BlockEntityChest(f.hopper.chunk, tag(BlockEntity.CHEST, 63));
            when(f.level.getBlockEntity(any(Vector3.class))).thenAnswer(call -> {
                int y = ((Vector3) call.getArgument(0)).getFloorY();
                return y == 65 ? f.chest : y == 63 ? destination : null;
            });
            when(f.level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.CHEST));
            f.close();
            Plugin plugin = mock(Plugin.class);
            when(plugin.isEnabled()).thenReturn(true);
            RegisteredListener listener = new RegisteredListener(new Listener() {}, (ignored, event) -> {
                InventoryMoveItemEvent move = (InventoryMoveItemEvent) event;
                Inventory other = move.getInventory().getHolder() == f.hopper
                        ? move.getTargetInventory() : move.getInventory();
                if (other.getHolder() == f.chest) move.setCancelled();
            }, EventPriority.HIGH, plugin, false);
            InventoryMoveItemEvent.getHandlers().register(listener);
            try {
                assertTrue(f.hopper.getInventory().setItem(0, Item.get(Item.EMERALD, 0, 2)));
                f.tick(1);
                assertEquals(5, units(f.chest.getInventory()));
                assertEquals(1, units(f.hopper.getInventory()));
                assertEquals(1, units(destination.getInventory()));
                assertEquals(Item.EMERALD, destination.getInventory().getItem(0).getId());
            } finally {
                InventoryMoveItemEvent.getHandlers().unregister(listener);
            }
        }
    }

    @Test
    void nonemptyHopperResumesPullAfterCancellationWithoutSlotWrites() throws Exception {
        try (Fixture f = fixture()) {
            assertTrue(f.hopper.getInventory().setItem(0, Item.get(Item.EMERALD, 0, 2)));
            f.tick(1);
            assertEquals(5, units(f.chest.getInventory()));
            assertEquals(2, units(f.hopper.getInventory()));
            f.deny.set(false);
            f.tick(32);
            assertTrue(f.hopper.getInventory().getContents().values().stream()
                    .anyMatch(item -> item.getId() == Item.DIAMOND), "Nonempty hopper must resume denied pull");
            assertEquals(7, units(f.chest.getInventory()) + units(f.hopper.getInventory()));
            assertTrue(f.hopper.getInventory().getContents().values().stream()
                    .filter(item -> item.getId() == Item.DIAMOND)
                    .allMatch(item -> item.getNamedTag().getString("identity").equals("resume28")));
        }
    }

    @Test
    void cancelledPushResumesAfterPermissionReleaseWithoutSlotWrites() throws Exception {
        try (Fixture f = fixture()) {
            f.chest.getInventory().clearAll();
            BlockEntityChest destination = new BlockEntityChest(f.hopper.chunk, tag(BlockEntity.CHEST, 63));
            when(f.level.getBlockEntity(any(Vector3.class))).thenAnswer(call -> {
                int y = ((Vector3) call.getArgument(0)).getFloorY();
                return y == 65 ? f.chest : y == 63 ? destination : null;
            });
            when(f.level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.CHEST));
            assertTrue(f.hopper.getInventory().setItem(0, Item.get(Item.EMERALD, 0, 2)));
            f.tick(1);
            assertEquals(2, units(f.hopper.getInventory()));
            assertTrue(destination.getInventory().isEmpty());
            f.deny.set(false);
            f.tick(16);
            assertFalse(destination.getInventory().isEmpty(), "Cancelled push must resume after release");
            assertEquals(2, units(f.hopper.getInventory()) + units(destination.getInventory()));
        }
    }


    @Test
    void cancellingCallbackMayCloseHopperWithoutThrowOrItemWrites() throws Exception {
        try (Fixture f = fixture()) {
            Plugin plugin = mock(Plugin.class);
            when(plugin.isEnabled()).thenReturn(true);
            RegisteredListener listener = new RegisteredListener(new Listener() {}, (ignored, event) -> {
                ((InventoryMoveItemEvent) event).setCancelled();
                f.hopper.close();
            }, EventPriority.HIGHEST, plugin, false);
            InventoryMoveItemEvent.getHandlers().register(listener);
            try {
                assertFalse(f.hopper.pullItems(f.chest, null));
                assertTrue(f.hopper.closed);
                assertEquals(5, units(f.chest.getInventory()));
                assertTrue(f.hopper.getInventory().isEmpty());
            } finally {
                InventoryMoveItemEvent.getHandlers().unregister(listener);
            }
        }
    }

    @Test
    void cancellingCallbackMayDetachHolderWithoutRetryOnMissingLevel() throws Exception {
        try (Fixture f = fixture()) {
            Plugin plugin = mock(Plugin.class);
            when(plugin.isEnabled()).thenReturn(true);
            RegisteredListener listener = new RegisteredListener(new Listener() {}, (ignored, event) -> {
                ((InventoryMoveItemEvent) event).setCancelled();
                f.hopper.setLevel(null);
            }, EventPriority.HIGHEST, plugin, false);
            InventoryMoveItemEvent.getHandlers().register(listener);
            try {
                assertFalse(f.hopper.pullItems(f.chest, null));
                assertNull(f.hopper.getLevel());
                assertEquals(5, units(f.chest.getInventory()));
                assertTrue(f.hopper.getInventory().isEmpty());
            } finally {
                InventoryMoveItemEvent.getHandlers().unregister(listener);
            }
        }
    }

    private static Fixture fixture() throws Exception {
        when(server.getPluginManager()).thenReturn(new PluginManager(server, null));
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getFolderName()).thenReturn("world");
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        ConcurrentLinkedQueue<BlockEntity> queue = new ConcurrentLinkedQueue<>();
        field(level, "server", server);
        field(level, "updateBlockEntities", queue);
        // Upstream ticks block entities inline in doTick; initialize its unrelated queues empty.
        field(level, "normalUpdateQueue", new ConcurrentLinkedQueue<>());
        field(level, "normalUpdateQueueSize", new AtomicInteger());
        field(level, "updateQueue", new BlockUpdateScheduler(level, 0));
        field(level, "chunkTickList", new Long2IntOpenHashMap());
        field(level, "changedBlocks", new Long2ObjectOpenHashMap<>());
        field(level, "pendingChunkRequests", new Object2ObjectOpenHashMap<>());
        field(level, "asyncChunkRequestFailedQueue", new ConcurrentLinkedQueue<>());
        field(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        field(level, "chunkPackets", new ConcurrentHashMap<>());
        level.gameRules = GameRules.getDefault();
        level.gameRules.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        level.gameRules.refresh();
        doCallRealMethod().when(level).scheduleBlockEntityUpdate(any(BlockEntity.class));
        doCallRealMethod().when(level).doTick(anyInt());
        Method scheduler = Level.class.getDeclaredMethod("doTick", int.class);

        BaseFullChunk chunk = mock(BaseFullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
        when(level.getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> (int) call.getArgument(2) == 64 ? Block.HOPPER_BLOCK : Block.CHEST);
        BlockEntityHopper hopper = new BlockEntityHopper(chunk, tag(BlockEntity.HOPPER, 64));
        BlockEntityChest chest = new BlockEntityChest(chunk, tag(BlockEntity.CHEST, 65));
        when(level.getBlockEntity(any(Vector3.class)))
                .thenAnswer(call -> ((Vector3) call.getArgument(0)).getFloorY() == 65 ? chest : null);
        when(level.getBlockEntityIfLoaded(any(FullChunk.class), any(Vector3.class)))
                .thenAnswer(call -> ((Vector3) call.getArgument(1)).getFloorY() == 64 ? hopper : null);
        Item diamonds = Item.get(Item.DIAMOND, 0, 5);
        diamonds.setNamedTag(new CompoundTag().putString("identity", "resume28"));
        assertTrue(chest.getInventory().setItem(0, diamonds));
        hopper.setTransferCooldown(0);

        AtomicBoolean deny = new AtomicBoolean(true);
        AtomicInteger attempts = new AtomicInteger();
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        RegisteredListener listener = new RegisteredListener(new Listener() {}, (ignored, event) -> {
            InventoryMoveItemEvent move = (InventoryMoveItemEvent) event;
            attempts.incrementAndGet();
            if (deny.get()) move.setCancelled();
        }, EventPriority.HIGH, plugin, false);
        InventoryMoveItemEvent.getHandlers().register(listener);
        assertTrue(queue.contains(hopper));
        return new Fixture(level, hopper, chest, deny, attempts, queue, listener, scheduler);
    }

    private static CompoundTag tag(String type, int y) {
        return new CompoundTag().putString("id", type).putInt("x", 8).putInt("y", y).putInt("z", 8)
                .putList(new ListTag<CompoundTag>("Items"));
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Fixture(Level level, BlockEntityHopper hopper, BlockEntityChest chest,
            AtomicBoolean deny, AtomicInteger attempts, ConcurrentLinkedQueue<BlockEntity> queue, RegisteredListener listener,
            Method scheduler) implements AutoCloseable {
        void tick(int count) throws Exception {
            for (int i = 0; i < count; i++) scheduler.invoke(level, i + 1);
        }

        @Override
        public void close() {
            InventoryMoveItemEvent.getHandlers().unregister(listener);
        }
    }
}
