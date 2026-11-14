package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockComposter;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.ComposterEmptyEvent;
import cn.nukkit.event.block.ComposterFillEvent;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.item.Item;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual native block/inventory/event paths; Server/Level/Chunk implement deterministic test infrastructure. */
@org.junit.jupiter.api.parallel.Isolated
@SuppressWarnings("try")
public class NativeComposterConservationTest {
    private static Server originalServer;
    @org.junit.jupiter.api.BeforeAll static void rememberServer() { originalServer = Server.getInstance(); }
    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        var field = Server.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, originalServer);
    }
    private Server server;
    private PluginManager previous;
    private BaseLang previousLanguage;
    private final List<Registration> registrations = new ArrayList<>();
    private Fixture f;
    private String testName;
    private record Registration(HandlerList handlers, RegisteredListener listener) { }

    @BeforeEach void setup(org.junit.jupiter.api.TestInfo test) {
        testName = test.getTestMethod().orElseThrow().getName();
        MockServer.init(); server = Server.getInstance(); previous = server.getPluginManager();
        previousLanguage = server.getLanguage();
        when(server.getPluginManager()).thenReturn(new PluginManager(server, null));
        when(server.getLanguage()).thenReturn(new BaseLang("eng"));
        f = new Fixture();
        trace("before");
    }
    @AfterEach void cleanup() {
        registrations.forEach(r -> r.handlers().unregister(r.listener()));
        when(server.getPluginManager()).thenReturn(previous);
        when(server.getLanguage()).thenReturn(previousLanguage);
    }
    private <T extends Event> void listen(HandlerList handlers, Class<T> type, Consumer<T> body) {
        Plugin plugin = mock(Plugin.class); when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDescription()).thenReturn(new PluginDescription(Map.of("name", "composter-native-control", "version", "1", "main", "test.Main", "api", List.of("1.0.0"))));
        RegisteredListener registered = new RegisteredListener(new Listener() {}, (listener, event) -> body.accept(type.cast(event)), EventPriority.NORMAL, plugin, false);
        handlers.register(registered); registrations.add(new Registration(handlers, registered));
    }
    private void fill(Consumer<ComposterFillEvent> body) { listen(ComposterFillEvent.getHandlers(), ComposterFillEvent.class, body); }
    private void empty(Consumer<ComposterEmptyEvent> body) { listen(ComposterEmptyEvent.getHandlers(), ComposterEmptyEvent.class, body); }
    private void move(Consumer<InventoryMoveItemEvent> body) { listen(InventoryMoveItemEvent.getHandlers(), InventoryMoveItemEvent.class, body); }
    private void trace(String name) {
        String row = "{\"test\":\"" + testName + "\",\"phase\":\"" + name
                + "\",\"ingredient_count\":" + f.pies() + ",\"compost_level\":" + f.damage()
                + ",\"bone_meal_count\":" + f.meals() + ",\"world_writes\":" + f.writes.get() + "}\n";
        String output = System.getProperty("km.composter.observations");
        if (output != null) {
            try { java.nio.file.Files.writeString(java.nio.file.Path.of(output), row,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        System.out.println("COMPOSTER " + name + " pie=" + f.pies() + " world=" + f.damage() + " meal=" + f.meals() + " writes=" + f.writes.get());
    }
    private void sourceExact() { for (Item item : f.source.getInventory().getContents().values()) if (item.getId() == Item.PUMPKIN_PIE) assertArrayEquals(f.opaque, item.getCompoundTag()); }
    private int readyAndMeals() { return (f.damage() == 8 ? 1 : 0) + f.meals(); }

    @Test void ordinaryLastIngredientAndExtractionPreserveExactPaidUnit() {
        assertEquals(5, f.pies()); assertEquals(7, f.damage()); assertEquals(0, f.meals());
        assertTrue(f.source.pushItems()); trace("healthy-fill");
        assertEquals(4, f.pies()); assertEquals(8, f.damage()); sourceExact();
        assertTrue(f.destination.pullItems()); trace("healthy-pull");
        assertEquals(0, f.damage()); assertEquals(1, f.meals()); assertEquals(5, f.pies() + f.meals()); sourceExact();
    }
    @Test void ordinaryCompleteEightIngredientCycleProducesOneBoneMeal() {
        Item batch = f.source.getInventory().getItem(0); batch.setCount(8);
        assertTrue(f.source.getInventory().setItem(0, batch)); f.world.set(f.composter(0)); trace("before-eight-ingredients");
        for (int step = 0; step < 8; step++) {
            assertTrue(f.source.pushItems()); assertEquals(7 - step, f.pies()); assertEquals(step + 1, f.damage()); sourceExact();
        }
        trace("after-eight-ingredients"); assertTrue(f.destination.pullItems()); trace("after-complete-cycle");
        assertEquals(0, f.pies()); assertEquals(0, f.damage()); assertEquals(1, f.meals());
    }
    @Test void cancellingFillKeepsIngredientAndOriginalWorldState() {
        fill(event -> event.setCancelled(true)); assertFalse(f.source.pushItems()); trace("cancel-fill");
        assertEquals(5, f.pies()); assertEquals(7, f.damage()); assertEquals(0, f.meals()); sourceExact();
    }
    @Test void cancellingEmptyKeepsReadyComposterAndNoOutput() {
        f.ready(); empty(event -> event.setCancelled(true)); assertFalse(f.destination.pullItems()); trace("cancel-empty");
        assertEquals(8, f.damage()); assertEquals(0, f.meals()); assertEquals(1, readyAndMeals());
    }
    @Test void nativeRuntimeExceptionInFillHandlerIsLoggedAndOrdinaryFillRemainsFunded() {
        AtomicInteger calls = new AtomicInteger(); fill(event -> { calls.incrementAndGet(); throw new IllegalStateException("controlled fill handler"); });
        assertTrue(f.source.pushItems()); trace("runtime-fill-handler");
        assertEquals(1, calls.get()); assertEquals(4, f.pies()); assertEquals(8, f.damage()); sourceExact();
    }
    @Test void nativeRuntimeExceptionInEmptyHandlerIsLoggedAndOrdinaryExtractionRemainsFunded() {
        f.ready(); AtomicInteger calls = new AtomicInteger(); empty(event -> { calls.incrementAndGet(); throw new IllegalStateException("controlled empty handler"); });
        assertTrue(f.destination.pullItems()); trace("runtime-empty-handler");
        assertEquals(1, calls.get()); assertEquals(0, f.damage()); assertEquals(1, f.meals());
    }
    @Test void nativeRuntimeExceptionInMoveHandlerIsLoggedAndOrdinaryExtractionRemainsFunded() {
        f.ready(); AtomicInteger calls = new AtomicInteger(); move(event -> { calls.incrementAndGet(); throw new IllegalStateException("controlled move handler"); });
        assertTrue(f.destination.pullItems()); trace("runtime-move-handler");
        assertEquals(1, calls.get()); assertEquals(1, readyAndMeals());
    }
    @Test void nativeFatalFillHandlerPropagatesIdentityWithoutChangingGoods() {
        AssertionError failure = new AssertionError("controlled fatal fill handler"); fill(event -> { throw failure; });
        assertSame(failure, assertThrows(AssertionError.class, f.source::pushItems)); trace("fatal-fill-handler");
        assertEquals(5, f.pies()); assertEquals(7, f.damage()); sourceExact();
    }
    @Test void nativeFatalEmptyHandlerPropagatesIdentityWithoutChangingGoods() {
        f.ready(); AssertionError failure = new AssertionError("controlled fatal empty handler"); empty(event -> { throw failure; });
        assertSame(failure, assertThrows(AssertionError.class, f.destination::pullItems)); trace("fatal-empty-handler");
        assertEquals(8, f.damage()); assertEquals(0, f.meals());
    }
    @Test void legalUnsuccessfulCompostAttemptConsumesOneWithoutCreatingLayer() {
        fill(event -> event.setSuccess(false)); assertTrue(f.source.pushItems()); trace("accepted-no-layer");
        assertEquals(4, f.pies()); assertEquals(7, f.damage()); assertEquals(0, f.meals()); sourceExact();
    }
    @Test void cancellingMoveCannotDiscardReadyBoneMeal() {
        f.ready(); move(event -> event.setCancelled(true)); assertFalse(f.destination.pullItems()); trace("cancel-move");
        assertEquals(1, readyAndMeals(), "cancelled destination move must preserve its ready source");
        assertEquals(8, f.damage()); assertEquals(0, f.meals());
    }
    @Test void realDestinationWriteGuardCannotDiscardReadyBoneMeal() {
        f.ready(); try (var claim = InventoryMutationGuard.tryClaim(List.of(f.destination.getInventory())); var blocked = claim.blockWrites()) {
            assertNotNull(claim); assertFalse(f.destination.pullItems()); trace("leased-destination");
            assertEquals(1, readyAndMeals()); assertEquals(8, f.damage()); assertEquals(0, f.meals());
        }
    }
    @Test void incompatibleDestinationRoomCannotDiscardReadyBoneMeal() {
        f.ready(); for (int slot = 0; slot < 5; slot++) assertTrue(f.destination.getInventory().setItem(slot, Item.get(Item.EMERALD, 0, slot == 4 ? 63 : 64)));
        assertFalse(f.destination.getInventory().isFull()); assertFalse(f.destination.getInventory().canAddItem(Item.get(Item.DYE, 15, 1)));
        assertFalse(f.destination.pullItems()); trace("incompatible-room"); assertEquals(1, readyAndMeals());
    }
    @Test void realSourceWriteGuardCannotCreateUnfundedCompostLayer() {
        try (var claim = InventoryMutationGuard.tryClaim(List.of(f.source.getInventory())); var blocked = claim.blockWrites()) {
            assertNotNull(claim); boolean answer = f.source.pushItems(); trace("leased-source answer=" + answer);
            assertEquals(7, f.damage()); assertEquals(5, f.pies()); assertFalse(answer); sourceExact();
        }
    }
    @Test void runtimeSoundFailureCannotLeaveAcceptedWorldLayerBesideOriginalIngredient() {
        IllegalStateException failure = new IllegalStateException("controlled native sound callback");
        doThrow(failure).when(f.level).addSound(any(Vector3.class), any(Sound.class));
        assertSame(failure, assertThrows(IllegalStateException.class, f.source::pushItems)); trace("runtime-fill-sound");
        assertEquals(5, f.pies() + f.damage() - 7, "accepted or restored conversion must remain funded"); sourceExact();
    }
    @Test void fatalSoundFailureCannotLeaveAcceptedWorldLayerBesideOriginalIngredient() {
        AssertionError failure = new AssertionError("controlled fatal native sound callback");
        doThrow(failure).when(f.level).addSound(any(Vector3.class), any(Sound.class));
        assertSame(failure, assertThrows(AssertionError.class, f.source::pushItems)); trace("fatal-fill-sound");
        assertEquals(5, f.pies() + f.damage() - 7); sourceExact();
    }
    @Test void fatalMoveHandlerCannotLoseReadySourceBeforeNativeDestinationAck() {
        f.ready(); AssertionError failure = new AssertionError("controlled fatal move handler"); move(event -> { throw failure; });
        assertSame(failure, assertThrows(AssertionError.class, f.destination::pullItems)); trace("fatal-move-handler");
        assertEquals(1, readyAndMeals());
    }
    @Test void nativeEmptyEventReentryCannotExtractSameReadySourceTwice() {
        f.ready(); AtomicInteger calls = new AtomicInteger(); empty(event -> { if (calls.getAndIncrement() == 0) assertTrue(f.destination.pullItems()); });
        f.destination.pullItems(); trace("empty-reentry"); assertEquals(1, readyAndMeals());
    }
    @Test void acceptedIndependentFillCallbackTransferCannotBeResurrectedByOuterDebit() {
        var target = new BlockEntityBarrel(f.chunk, Fixture.tag(BlockEntity.BARREL, 66));
        fill(event -> { Item moved = f.source.getInventory().getItem(0); assertTrue(f.source.getInventory().clear(0)); assertTrue(target.getInventory().setItem(0, moved)); });
        f.source.pushItems(); int external = target.getInventory().getItem(0).getCount(); trace("independent-transfer external=" + external);
        assertEquals(5, f.pies() + external + f.damage() - 7); assertEquals(5, external);
    }
    private final class Fixture {
        final Level level = mock(Level.class);
        final FullChunk chunk = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
        final AtomicReference<BlockComposter> world = new AtomicReference<>();
        final AtomicInteger writes = new AtomicInteger();
        final BlockEntityHopper source, destination;
        final byte[] opaque;
        Fixture() {
            when(level.getServer()).thenReturn(server); when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
            var provider = mock(LevelProvider.class); when(provider.getLevel()).thenReturn(level); when(chunk.getProvider()).thenReturn(provider);
            doReturn(chunk).when(level).getChunkIfLoaded(0, 0);
            when(level.isYInRange(anyInt())).thenReturn(true);
            doCallRealMethod().when(level).getBlockEntityIfLoaded(any(Vector3.class));
            doCallRealMethod().when(level).getBlockEntityIfLoaded(nullable(FullChunk.class),any(Vector3.class));
            Map<Long,BlockEntity> canonicalActors = new java.util.HashMap<>();
            when(chunk.getBlockEntities()).thenReturn(canonicalActors);
            doAnswer(call->{BlockEntity tile=call.getArgument(0);canonicalActors.put(tile.getId(),tile);return null;}).when(chunk).addBlockEntity(any(BlockEntity.class));
            doAnswer(call->{BlockEntity tile=call.getArgument(0);canonicalActors.remove(tile.getId(),tile);return null;}).when(chunk).removeBlockEntity(any(BlockEntity.class));
            when(chunk.getTile(anyInt(),anyInt(),anyInt())).thenAnswer(call->canonicalActors.values().stream().filter(tile->tile.getFloorX()==(int)call.getArgument(0)&&tile.getFloorY()==(int)call.getArgument(1)&&tile.getFloorZ()==(int)call.getArgument(2)).findFirst().orElse(null));
            source = new BlockEntityHopper(chunk, tag(BlockEntity.HOPPER, 64)); destination = new BlockEntityHopper(chunk, tag(BlockEntity.HOPPER, 62));
            world.set(composter(7));
            when(level.getBlockDataAt(0, 64, 0)).thenReturn(0);
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> { Vector3 at = call.getArgument(0); return at.y == 63 ? world.get().clone() : Block.get(Block.HOPPER_BLOCK); });
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call -> { Vector3 at = call.getArgument(0); return at.y == 64 ? source : at.y == 62 ? destination : null; });
            doAnswer(call -> { Block block = call.getArgument(1); assertEquals(Block.COMPOSTER, block.getId()); world.set((BlockComposter) block.clone()); writes.incrementAndGet(); return true; })
                    .when(level).setBlock(any(Vector3.class), any(Block.class), eq(true), eq(true));
            doAnswer(call -> { boolean written = level.setBlock(call.getArgument(0), call.getArgument(1), true, true);
                if (written) ((Runnable) call.getArgument(4)).run(); return written; })
                    .when(level).setBlock(any(Vector3.class), any(Block.class), eq(true), eq(true), any(Runnable.class));
            Item pies = Item.get(Item.PUMPKIN_PIE, 0, 5); pies.setNamedTag(new CompoundTag().putString("opaque", "native-composter-source").putByteArray("payload", new byte[]{-1, 0, 71})); opaque = pies.getCompoundTag().clone();
            assertEquals(100, BlockComposter.getChance(pies)); assertTrue(source.getInventory().setItem(0, pies));
        }
        private BlockComposter composter(int damage) { BlockComposter block = new BlockComposter(damage); block.level = level; block.x = 0; block.y = 63; block.z = 0; return block; }
        void ready() { world.set(composter(8)); trace("before-ready-pull"); }
        int damage() { return world.get().getDamage(); }
        int pies() { return source.getInventory().getContents().values().stream().filter(item -> item.getId() == Item.PUMPKIN_PIE).mapToInt(Item::getCount).sum(); }
        int meals() { return destination.getInventory().getContents().values().stream().filter(item -> item.getId() == Item.DYE && item.getDamage() == 15).mapToInt(Item::getCount).sum(); }
        static CompoundTag tag(String type, int y) { return new CompoundTag().putString("id", type).putInt("x", 0).putInt("y", y).putInt("z", 0).putList(new ListTag<CompoundTag>("Items")); }
    }
}
