package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.StonecutterItemAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Public window close/stonecutter execute, real inventories and real native item constructors. */
class NativeWorkstationOverflowTest {
    enum Route { ANVIL_CLOSE, STONECUTTER_CLOSE, STONECUTTER_RESULT }
    private static CraftingManager recipes;
    @BeforeAll static void initialize() { MockServer.init(); recipes = new CraftingManager(); }
    @AfterEach void resetServer() { MockServer.reset(); }

    @ParameterizedTest @EnumSource(Route.class)
    void fullBagPublishesOnlyAfterTheNativeInputIsRemovedAndDoesNotReplay(Route route) throws Exception {
        Fixture f = new Fixture(route);
        assertTrue(f.run());
        assertTrue(f.station.getItem(0).isNull());
        assertEquals(f.expected.getCount(), f.publishedCount());
        assertEquals(1, f.world.size());
        assertTrue(f.world.values().stream().allMatch(Entity::canBeSavedWithChunk));
        f.run();
        assertEquals(f.expected.getCount(), f.publishedCount());
        assertEquals(1, f.world.size());
    }

    @ParameterizedTest @EnumSource(Route.class)
    void refusedSourceWriteRetainsExactInputAndDiscardsEveryWorldRegistration(Route route) throws Exception {
        Fixture f = new Fixture(route);
        f.events(event -> {
            if (event instanceof EntityInventoryChangeEvent change && change.getNewItem().isNull()) change.setCancelled();
        });
        f.run();
        f.assertRetained();
    }

    @ParameterizedTest @EnumSource(Route.class)
    void constructorVetoKeepsTheInputReachableForAHealthyRetry(Route route) throws Exception {
        Fixture f = new Fixture(route);
        f.events(event -> { if (event instanceof ItemSpawnEvent spawn) spawn.getEntity().close(); });
        f.run();
        f.assertRetained();
        f.events(event -> { });
        assertTrue(f.run());
        assertTrue(f.station.getItem(0).isNull());
        assertEquals(f.expected.getCount(), f.publishedCount());
    }

    @ParameterizedTest @EnumSource(Route.class)
    void constructorErrorPreservesItsIdentityAndLeavesTheSameWindowRetriable(Route route) throws Exception {
        Fixture f = new Fixture(route);
        Error failure = new AssertionError("native world constructor");
        f.events(event -> { if (event instanceof EntitySpawnEvent) throw failure; });
        assertSame(failure, assertThrows(Error.class, f::run));
        f.assertRetained();
        f.events(event -> { });
        assertTrue(f.run());
        assertEquals(f.expected.getCount(), f.publishedCount());
    }

    @ParameterizedTest @EnumSource(Route.class)
    void sourceWriteErrorRestoresInputAndDiscardsSealedOutput(Route route) throws Exception {
        Fixture f = new Fixture(route);
        Error failure = new AssertionError("native source setter");
        f.events(event -> {
            if (event instanceof EntityInventoryChangeEvent change && change.getNewItem().isNull()) throw failure;
        });
        assertSame(failure, assertThrows(Error.class, f::run));
        f.assertRetained();
    }

    @ParameterizedTest @EnumSource(Route.class)
    void reentrantCloseOrResultCannotPublishAnotherCopy(Route route) throws Exception {
        Fixture f = new Fixture(route);
        AtomicInteger callbacks = new AtomicInteger();
        f.events(event -> {
            if (event instanceof ItemSpawnEvent && callbacks.getAndIncrement() == 0) f.run();
        });
        assertTrue(f.run());
        assertEquals(1, callbacks.get());
        assertTrue(f.station.getItem(0).isNull());
        assertEquals(f.expected.getCount(), f.publishedCount());
        assertEquals(1, f.world.size());
    }

    @ParameterizedTest @EnumSource(Route.class)
    void preparedConstructorCannotExposeAnInputAndConsumableOutputAtTheSameTime(Route route) throws Exception {
        Fixture f = new Fixture(route);
        AtomicInteger checked = new AtomicInteger();
        f.events(event -> {
            if (event instanceof EntitySpawnEvent spawn) {
                EntityItem item = (EntityItem) spawn.getEntity();
                assertTrue(f.input.equalsExact(f.station.getItem(0)));
                assertTrue(item.isPublicationDeferred());
                assertFalse(item.canBeSavedWithChunk());
                assertFalse(item.isMergeable());
                assertFalse(f.player.pickupEntity(item, true));
                checked.incrementAndGet();
            }
        });
        assertTrue(f.run());
        assertEquals(1, checked.get());
        assertEquals(f.expected.getCount(), f.publishedCount());
    }

    @FunctionalInterface interface EventConsumer { void accept(Event event) throws Exception; }

    static final class Fixture {
        final Route route;
        final Level level = mock(Level.class);
        final Map<Long, Entity> world = new Long2ObjectNonBlockingMap<>();
        final Map<Long, Entity> chunkEntities = new LinkedHashMap<>();
        final PluginManager plugins = mock(PluginManager.class);
        final NativePlayer player;
        final PlayerUIComponent station;
        final int windowId;
        final Item input;
        final Item expected;
        final StonecutterTransaction transaction;

        Fixture(Route route) throws Exception {
            this.route = route;
            MockServer.reset();
            Server server = MockServer.get();
            when(server.isPrimaryThread()).thenReturn(true);
            when(server.getCraftingManager()).thenReturn(recipes);
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getDefaultLevel()).thenReturn(level);
            when(level.getServer()).thenReturn(server);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
            when(level.getEntity(anyLong())).thenAnswer(call -> world.get(call.getArgument(0)));
            Field entities = Level.class.getDeclaredField("entities"); entities.setAccessible(true); entities.set(level, world);
            Field updates = Level.class.getDeclaredField("updateEntities"); updates.setAccessible(true);
            updates.set(level, new Long2ObjectNonBlockingMap<Entity>());
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level); when(chunk.getProvider()).thenReturn(provider);
            when(chunk.getEntities()).thenReturn(chunkEntities);
            doReturn(chunk).when(level).getChunk(0, 0, true);
            doReturn(chunk).when(level).getChunkIfLoaded(0, 0);
            doAnswer(call -> { Entity entity = call.getArgument(0); chunkEntities.put(entity.getId(), entity); return null; }).when(chunk).addEntity(any());
            doAnswer(call -> { Entity entity = call.getArgument(0); chunkEntities.remove(entity.getId(), entity); return null; }).when(chunk).removeEntity(any());
            doAnswer(call -> { Entity entity = call.getArgument(0); world.put(entity.getId(), entity); return null; }).when(level).addEntity(any());
            doAnswer(call -> { Entity entity = call.getArgument(0); world.remove(entity.getId(), entity); return null; }).when(level).removeEntity(any());
            doCallRealMethod().when(level).discardDeferredEntityRegistration(any());
            doCallRealMethod().when(level).prepareItemDrop(any(Vector3.class), any(Item.class), any(), anyInt());
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class));
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), any());
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), any(), anyInt());
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), any(), anyBoolean(), anyInt());
            SourceInterface network = mock(SourceInterface.class);
            when(network.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
            player = new NativePlayer(network, level, chunk);
            if (route == Route.ANVIL_CLOSE) {
                station = new AnvilInventory(player.getUIInventory(), new Position(0,64,0,level));
                windowId = Player.ANVIL_WINDOW_ID;
                input = Item.get(Item.EMERALD, 0, 3); expected = input.clone(); transaction = null;
            } else {
                station = new StonecutterInventory(player.getUIInventory(), new Position(0,64,0,level));
                windowId = Player.STONECUTTER_WINDOW_ID;
                StonecutterRecipe recipe = recipes.getStonecutterRecipes().stream()
                        .filter(candidate -> candidate.getResult().getCount() == 1).findFirst().orElseThrow();
                input = recipe.getIngredient().clone();
                expected = route == Route.STONECUTTER_RESULT ? recipe.getResult().clone() : input.clone();
                transaction = route == Route.STONECUTTER_RESULT ? new StonecutterTransaction(player, List.of(
                        new StonecutterItemAction(expected.clone(), Item.get(Item.AIR), NetworkInventoryAction.SOURCE_TYPE_CRAFTING_RESULT))) : null;
            }
            player.bind(station, windowId);
            station.setItemForce(0, input.clone());
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) player.getInventory().setItemForce(slot, Item.get(Item.DIAMOND, 0, 64));
            if (transaction != null) assertTrue(transaction.canExecute());
        }

        void events(EventConsumer callback) {
            doAnswer(call -> { callback.accept(call.getArgument(0)); return null; }).when(plugins).callEvent(any(Event.class));
        }
        boolean run() {
            if (transaction != null) return transaction.execute();
            player.removeWindow(station);
            return station.getItem(0).isNull();
        }
        int publishedCount() { return world.values().stream().filter(entity -> !entity.closed)
                .mapToInt(entity -> ((EntityItem) entity).getItem().getCount()).sum(); }
        void assertRetained() {
            assertTrue(input.equalsExact(station.getItem(0)), "exact native UI input remains owned");
            assertTrue(world.isEmpty(), "no registered world credit may coexist with retained source");
            assertTrue(chunkEntities.isEmpty());
            assertSame(station, player.getWindowById(windowId), "the real close caller retains the same reachable window");
            assertTrue(InventoryMutationGuard.canCaptureProfile(List.of(player.getInventory(), player.getUIInventory())));
            if (transaction != null) assertFalse(transaction.hasExecuted());
        }
    }

    static class NativePlayer extends Player {
        NativePlayer(SourceInterface source, Level level, FullChunk chunk) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.protocol = 2193; this.gameVersion = GameVersion.V1_26_50;
            this.level = level; this.chunk = chunk; this.y = 64;
            this.temporalVector = new Vector3();
            this.spawned = this.loggedIn = this.loginVerified = true;
            this.inventory = new PlayerInventory(this); this.offhandInventory = new PlayerOffhandInventory(this);
            this.enderChestInventory = new PlayerEnderChestInventory(this);
            this.playerUIInventory = new PlayerUIInventory(this); this.playerUIInventory.getViewers().add(this);
            this.craftingGrid = this.playerUIInventory.getCraftingGrid();
            this.windows.put(this.inventory, ContainerIds.INVENTORY); this.permanentWindows.add(ContainerIds.INVENTORY);
            this.windows.put(this.playerUIInventory, ContainerIds.UI); this.permanentWindows.add(ContainerIds.UI);
            this.windows.put(this.craftingGrid, ContainerIds.NONE);
        }
        void configureModeChange() {this.configureProfile();this.adventureSettings=new cn.nukkit.AdventureSettings(this);}
        void setSessionVersion(GameVersion version) {this.gameVersion=version;this.protocol=version.getProtocol();}
        void configureProfile() {
            this.username = "NativeCheckpoint";
            this.uuid = UUID.fromString("6a1b7ac6-2f0f-4a6a-9f43-2b6c4a1d0e11");
            this.namedTag = new cn.nukkit.nbt.tag.CompoundTag();
            this.foodData = new cn.nukkit.PlayerFood(this, 20, 20);
        }
        void reload(cn.nukkit.nbt.tag.CompoundTag tag) {
            this.spawned = this.loggedIn = false;
            this.windows.clear(); this.permanentWindows.clear();
            this.namedTag = tag.copy();
            this.initEntity();
        }
        void bind(Inventory inventory, int id) {
            this.windows.put(inventory, id); inventory.getViewers().add(this); this.craftingType = id;
        }
    }
}
