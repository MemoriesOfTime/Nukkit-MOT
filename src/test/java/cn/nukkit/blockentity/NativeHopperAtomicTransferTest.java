package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDiamond;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exact native JAR paths; only Server/Level/Chunk infrastructure is mocked. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public final class NativeHopperAtomicTransferTest {
    private static Server server;
    private static PluginManager manager;
    private static Object previousServer;
    private final List<RegisteredListener> registrations = new ArrayList<>();
    private final List<AutoCloseable> openedScopes = new ArrayList<>();

    @BeforeAll
    static void setupServer() throws ReflectiveOperationException {
        cn.nukkit.MockServer.init();
        Block.init();
        server = mock(Server.class);
        when(server.getLogger()).thenReturn(mock(cn.nukkit.utils.MainLogger.class));
        manager = new PluginManager(server, null);
        when(server.getPluginManager()).thenReturn(manager);
        Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousServer = instance.get(null);
        instance.set(null, server);
    }

    @AfterAll
    static void restoreServer() throws ReflectiveOperationException {
        Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }

    @AfterEach
    void cleanup() throws Exception {
        for (int i = openedScopes.size() - 1; i >= 0; i--) openedScopes.get(i).close();
        registrations.forEach(InventoryMoveItemEvent.getHandlers()::unregister);
    }

    private void listen(Consumer<InventoryMoveItemEvent> action) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        RegisteredListener registered = new RegisteredListener(new Listener() {},
                (listener, event) -> action.accept((InventoryMoveItemEvent) event),
                EventPriority.NORMAL, plugin, false);
        InventoryMoveItemEvent.getHandlers().register(registered);
        registrations.add(registered);
    }

    private static FullChunk makeChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static CompoundTag nbt(String type, int y) {
        return new CompoundTag().putString("id", type).putInt("x", 0).putInt("y", y)
                .putInt("z", 0).putList(new ListTag<CompoundTag>("Items"));
    }

    private static Context makeContext(boolean pull) {
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        FullChunk hopperChunk = makeChunk(level);
        FullChunk chestChunk = makeChunk(level);
        BlockEntityHopper hopper = new BlockEntityHopper(hopperChunk, nbt(BlockEntity.HOPPER, 64));
        BlockEntityChest chest = new BlockEntityChest(chestChunk, nbt(BlockEntity.CHEST, pull ? 65 : 63));
        when(level.getBlockDataAt(0, 64, 0)).thenReturn(0);
        when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.CHEST));
        when(level.getBlockEntity(any(Vector3.class))).thenReturn(chest);
        Inventory source = pull ? chest.getInventory() : hopper.getInventory();
        Inventory target = pull ? hopper.getInventory() : chest.getInventory();
        Item diamonds = new ItemDiamond(0, 5);
        diamonds.setNamedTag(new CompoundTag().putString("auditIdentity", "ordinary-native-diamond"));
        assertTrue(source.setItem(0, diamonds), "fixture writes use real native setters");
        return new Context(hopper, chest, level, pull ? chestChunk : hopperChunk,
                source, target, pull, HexFormat.of().formatHex(diamonds.getCompoundTag()));
    }

    private record Context(BlockEntityHopper hopper, BlockEntityChest chest, Level level,
                           FullChunk sourceChunk, Inventory source, Inventory target,
                           boolean pull, String nbtHex) {
        boolean transfer() { return pull ? hopper.pullItems(chest, null) : hopper.pushItems(); }
        BlockEntity targetHolder() { return pull ? hopper : chest; }
    }

    private static String contents(Inventory inventory) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < inventory.getSize(); i++) {
            Item item = inventory.getItem(i);
            if (!item.isNull()) result.append(i).append('=').append(item.getId()).append('x')
                    .append(item.getCount()).append(':')
                    .append(HexFormat.of().formatHex(item.getCompoundTag())).append(';');
        }
        return result.append(']').toString();
    }

    private static void conserved(String name, Context context) {
        int count = 0;
        for (Inventory inventory : List.of(context.source, context.target)) {
            for (Item item : inventory.getContents().values()) {
                if (item.isNull()) continue;
                assertEquals(264, item.getId(), "ordinary diamond identity");
                assertEquals(context.nbtHex, HexFormat.of().formatHex(item.getCompoundTag()), "exact NBT");
                count += item.getCount();
            }
        }
        System.out.println("AUDIT case=" + name + " before=5 actual=" + count + " source="
                + contents(context.source) + " target=" + contents(context.target));
        assertEquals(5, count, name + " must conserve the original ordinary diamonds");
    }

    @Test @Order(1)
    void successfulNativePush() {
        Context c = makeContext(false);
        AtomicInteger calls = new AtomicInteger();
        listen(event -> { assertEquals(c.source, event.getInventory()); assertEquals(c.target, event.getTargetInventory()); calls.incrementAndGet(); });
        assertTrue(c.transfer());
        assertEquals(1, calls.get());
        assertEquals(4, c.source.getItem(0).getCount());
        assertEquals(1, c.target.getItem(0).getCount());
        conserved("push-success", c);
    }

    @Test @Order(2)
    void successfulNativePull() {
        Context c = makeContext(true);
        assertTrue(c.transfer());
        assertEquals(4, c.source.getItem(0).getCount());
        assertEquals(1, c.target.getItem(0).getCount());
        conserved("pull-success", c);
    }

    @Test @Order(3)
    void cancelledNativePush() { cancelled(false); }
    @Test @Order(4)
    void cancelledNativePull() { cancelled(true); }
    private void cancelled(boolean pull) {
        Context c = makeContext(pull);
        listen(event -> event.setCancelled(true));
        assertFalse(c.transfer());
        assertEquals(5, c.source.getItem(0).getCount());
        assertTrue(c.target.isEmpty());
        conserved(pull ? "pull-event-cancelled" : "push-event-cancelled", c);
    }

    @Test @Order(5)
    void rejectedNativeDestinationPush() { rejected(false, false); }
    @Test @Order(6)
    void rejectedNativeDestinationPull() { rejected(true, false); }
    @Test @Order(7)
    void rejectedNativeSourcePush() { rejected(false, true); }
    @Test @Order(8)
    void rejectedNativeSourcePull() { rejected(true, true); }
    private void rejected(boolean pull, boolean source) {
        Context c = makeContext(pull);
        listen(event -> {
            Inventory inventory = source ? c.source : c.target;
            InventoryMutationGuard.Lease lease = InventoryMutationGuard.tryClaim(List.of(inventory));
            assertTrue(lease != null, "native guard acquired");
            openedScopes.add(lease);
            openedScopes.add(lease.expectWrite(inventory, -1, Item.AIR_ITEM));
        });
        boolean moved = c.transfer();
        System.out.println("AUDIT transfer-return=" + moved);
        conserved((pull ? "pull" : "push") + (source ? "-source-write-refused" : "-destination-write-refused"), c);
    }

    @Test @Order(9)
    void sourceMovedDuringNativePushEvent() { movedSource(false); }
    @Test @Order(10)
    void sourceMovedDuringNativePullEvent() { movedSource(true); }
    private void movedSource(boolean pull) {
        Context c = makeContext(pull);
        AtomicInteger calls = new AtomicInteger();
        listen(event -> {
            if (calls.getAndIncrement() == 0) {
                Item original = c.source.getItem(0);
                assertTrue(c.source.clear(0));
                assertTrue(c.source.setItem(1, original));
                assertEquals(5, c.source.getItem(1).getCount());
            }
        });
        assertTrue(c.transfer(), "a later slot may transfer only after a fresh source read and event");
        assertEquals(4, c.source.getItem(1).getCount());
        assertEquals(1, c.target.getItem(0).getCount());
        conserved((pull ? "pull" : "push") + "-source-relocated-by-event", c);
    }

    @Test @Order(11)
    void sourceDirtyRuntimeExceptionNativePush() { sourceDirtyFailure(false, false); }
    @Test @Order(12)
    void sourceDirtyRuntimeExceptionNativePull() { sourceDirtyFailure(true, false); }
    @Test @Order(13)
    void sourceDirtyErrorNativePush() { sourceDirtyFailure(false, true); }
    @Test @Order(14)
    void sourceDirtyErrorNativePull() { sourceDirtyFailure(true, true); }
    private void sourceDirtyFailure(boolean pull, boolean error) {
        Context c = makeContext(pull);
        doAnswer(invocation -> {
            if (error) throw new AssertionError("source dirty hook after destination write");
            throw new IllegalStateException("source dirty hook after destination write");
        }).when(c.sourceChunk).setChanged();
        if (error) assertThrows(AssertionError.class, c::transfer);
        else assertThrows(IllegalStateException.class, c::transfer);
        conserved((pull ? "pull" : "push") + "-source-dirty-" + (error ? "error" : "runtime-exception"), c);
    }

    @Test @Order(15)
    void destinationComparatorRuntimeExceptionNativePush() { destinationComparatorFailure(false, false); }
    @Test @Order(16)
    void destinationComparatorRuntimeExceptionNativePull() { destinationComparatorFailure(true, false); }
    @Test @Order(17)
    void destinationComparatorErrorNativePush() { destinationComparatorFailure(false, true); }
    @Test @Order(18)
    void destinationComparatorErrorNativePull() { destinationComparatorFailure(true, true); }
    private void destinationComparatorFailure(boolean pull, boolean error) {
        Context c = makeContext(pull);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == c.targetHolder()) {
                if (error) throw new AssertionError("destination comparator after native slots put");
                throw new IllegalStateException("destination comparator after native slots put");
            }
            return null;
        }).when(c.level).updateComparatorOutputLevel(any(Vector3.class));
        if (error) assertThrows(AssertionError.class, c::transfer);
        else assertThrows(IllegalStateException.class, c::transfer);
        conserved((pull ? "pull" : "push") + "-destination-comparator-" + (error ? "error" : "runtime-exception"), c);
    }
    private static cn.nukkit.inventory.BaseInventory nativeInventory(Inventory inventory) {
        return (cn.nukkit.inventory.BaseInventory) inventory;
    }
    private static Item opaque(int count, String tag) {
        Item item = new ItemDiamond(0, count);
        item.setNamedTag(new CompoundTag().putString("opaque", tag).putByteArray("bytes", new byte[] {0, -1, 4}));
        return item;
    }
    @Test void hiddenRawSlotsSurviveAcceptedPush() {
        Context c = makeContext(false);
        Item hidden = opaque(3, "hidden-before");
        nativeInventory(c.source).slots.put(-1, hidden.clone());
        nativeInventory(c.target).slots.put(91, hidden.clone());
        assertTrue(c.transfer());
        assertTrue(nativeInventory(c.source).slots.get(-1).equalsExact(hidden));
        assertTrue(nativeInventory(c.target).slots.get(91).equalsExact(hidden));
        assertEquals(4, c.source.getItem(0).getCount());
        assertEquals(1, c.target.getItem(0).getCount());
    }
    @Test void rawSetterCallbackMutationRollsBackWholePair() {
        Context c = makeContext(false);
        Item hidden = opaque(3, "pre-existing-hidden");
        nativeInventory(c.source).slots.put(-1, hidden.clone());
        doAnswer(call -> {
            if (call.getArgument(0) == c.chest) {
                nativeInventory(c.source).slots.put(99, opaque(7, "callback-added"));
                nativeInventory(c.source).slots.put(0, opaque(9, "callback-replaced-debit"));
            }
            return null;
        }).when(c.level).updateComparatorOutputLevel(any(Vector3.class));
        assertFalse(c.transfer());
        assertTrue(nativeInventory(c.source).slots.get(-1).equalsExact(hidden));
        assertFalse(nativeInventory(c.source).slots.containsKey(99));
        assertEquals(5, c.source.getItem(0).getCount());
        assertTrue(c.target.isEmpty());
    }
    @Test void setterCallbackMetadataChangeRestoresNativeLimit() {
        Context c = makeContext(false);
        doAnswer(call -> { if (call.getArgument(0) == c.chest) c.source.setMaxStackSize(2); return null; })
                .when(c.level).updateComparatorOutputLevel(any(Vector3.class));
        assertFalse(c.transfer()); assertEquals(64, c.source.getMaxStackSize());
        assertEquals(5, c.source.getItem(0).getCount()); assertTrue(c.target.isEmpty());
    }
    @Test void eventReentryCannotTransferTheSameSourceTwice() {
        Context c = makeContext(false); AtomicInteger calls = new AtomicInteger();
        listen(event -> {if (calls.incrementAndGet() == 1) assertFalse(c.transfer());});
        assertTrue(c.transfer()); assertEquals(1, calls.get()); conserved("push-event-reentry", c);
    }
    @Test void movedSourceRequiresFreshSecondEvent() {
        Context c = makeContext(false); AtomicInteger calls = new AtomicInteger();
        listen(event -> {
            if (calls.incrementAndGet() == 1) { Item item=c.source.getItem(0); assertTrue(c.source.clear(0)); assertTrue(c.source.setItem(1,item)); }
            else { assertTrue(c.source.getItem(0).isNull()); assertEquals(5,c.source.getItem(1).getCount()); assertTrue(c.target.isEmpty()); }
        });
        assertTrue(c.transfer()); assertEquals(2,calls.get()); assertEquals(4,c.source.getItem(1).getCount()); conserved("push-fresh-second-event",c);
    }
    @Test void independentEventRawEditIsRetainedAndStaleTransferRefused() {
        Context c=makeContext(false); Item independent=opaque(2,"independent-event-edit");
        listen(event -> nativeInventory(c.target).slots.put(97, independent.clone()));
        assertFalse(c.transfer()); assertEquals(5,c.source.getItem(0).getCount());
        assertTrue(nativeInventory(c.target).slots.get(97).equalsExact(independent)); assertTrue(c.target.getItem(0).isNull());
    }
    @Test void closedHolderDuringEventRefusesWithoutResurrectingIt() {
        Context c=makeContext(false);listen(event -> c.chest.closed=true);
        assertFalse(c.transfer());assertTrue(c.chest.closed);assertEquals(5,c.source.getItem(0).getCount());assertTrue(c.target.isEmpty());
    }
    @Test void pluginAuthoredMoveConversionIsAcceptedWithOnePredecessorDebit() {
        Context c=makeContext(false);listen(event -> event.setItem(opaque(3,"approved-move-conversion")));
        assertTrue(c.transfer());assertEquals(4,c.source.getItem(0).getCount());
        assertTrue(c.target.getItem(0).equalsExact(opaque(3,"approved-move-conversion")));
    }
    @Test void differentOpaqueNbtDoesNotMerge() {
        Context c=makeContext(false);Item other=opaque(2,"unrelated-target");assertTrue(c.target.setItem(0,other));
        assertTrue(c.transfer());assertTrue(c.target.getItem(0).equalsExact(other));assertEquals(1,c.target.getItem(1).getCount());assertEquals(4,c.source.getItem(0).getCount());
    }
    @Test void notificationErrorAfterAcceptanceDoesNotRefund() {
        Context c=makeContext(false);cn.nukkit.Player viewer=mock(cn.nukkit.Player.class);viewer.protocol=2193;
        when(viewer.getWindowId(c.target)).thenReturn(1);nativeInventory(c.target).getViewers().add(viewer);
        doAnswer(call -> {throw new AssertionError("viewer notification after accepted pair");}).when(viewer).dataPacket(any(cn.nukkit.network.protocol.DataPacket.class));
        assertThrows(AssertionError.class,c::transfer);assertEquals(4,c.source.getItem(0).getCount());assertEquals(1,c.target.getItem(0).getCount());conserved("push-notification-error",c);
    }
    @Test void doubleChestRightHalfUsesPhysicalCanonicalClaimAndRawSnapshot() {
        Context base=makeContext(false);BlockEntityChest right=new BlockEntityChest(makeChunk(base.level),nbt(BlockEntity.CHEST,63).putInt("x",1));
        var pair=new cn.nukkit.inventory.DoubleChestInventory(base.chest,right);base.chest.doubleInventory=pair;right.doubleInventory=pair;
        for(int i=0;i<27;i++)assertTrue(pair.setItem(i,new cn.nukkit.item.ItemCoal(0,64)));
        Item hidden=opaque(2,"right-half-hidden");right.getRealInventory().slots.put(92,hidden.clone());
        assertTrue(base.hopper.pushItems());assertEquals(4,base.source.getItem(0).getCount());assertEquals(1,right.getRealInventory().getItem(0).getCount());assertTrue(right.getRealInventory().slots.get(92).equalsExact(hidden));
    }
    @Test void doubleChestRightHalfErrorRestoresBothPhysicalBackings() {
        Context base=makeContext(false);BlockEntityChest right=new BlockEntityChest(makeChunk(base.level),nbt(BlockEntity.CHEST,63).putInt("x",1));
        var pair=new cn.nukkit.inventory.DoubleChestInventory(base.chest,right);base.chest.doubleInventory=pair;right.doubleInventory=pair;
        for(int i=0;i<27;i++)assertTrue(pair.setItem(i,new cn.nukkit.item.ItemCoal(0,64)));
        Item hidden=opaque(2,"right-half-hidden");right.getRealInventory().slots.put(92,hidden.clone());
        doAnswer(call->{if(call.getArgument(0)==right){base.chest.getRealInventory().slots.put(-3,opaque(9,"callback-left"));throw new AssertionError("right comparator after native write");}return null;}).when(base.level).updateComparatorOutputLevel(any(Vector3.class));
        assertThrows(AssertionError.class,base.hopper::pushItems);assertEquals(5,base.source.getItem(0).getCount());assertTrue(right.getRealInventory().getItem(0).isNull());assertTrue(right.getRealInventory().slots.get(92).equalsExact(hidden));assertFalse(base.chest.getRealInventory().slots.containsKey(-3));assertEquals(64,base.chest.getRealInventory().getItem(0).getCount());
    }

    @Test void nativeFurnaceInputAndResultTransfersUseAcceptedPair() {
        Context c=makeContext(false);BlockEntityFurnace furnace=new BlockEntityFurnace(makeChunk(c.level),nbt(BlockEntity.FURNACE,63));when(c.level.getBlockEntity(any(Vector3.class))).thenReturn(furnace);
        assertTrue(c.hopper.pushItems());assertEquals(4,c.source.getItem(0).getCount());assertEquals(1,furnace.getInventory().getSmelting().getCount());assertEquals(c.nbtHex,HexFormat.of().formatHex(furnace.getInventory().getSmelting().getCompoundTag()));
        assertTrue(furnace.getInventory().setResult(opaque(5,"furnace-result")));assertTrue(c.hopper.pullItems(furnace,null));assertEquals(4,furnace.getInventory().getResult().getCount());assertEquals(1,c.hopper.getInventory().getItem(1).getCount());assertTrue(c.hopper.getInventory().getItem(1).equalsExact(opaque(1,"furnace-result")));
    }
    @Test void nativeFurnaceFuelInsertionKeepsFuelTags() {
        Context c=makeContext(false);BlockEntityFurnace furnace=new BlockEntityFurnace(makeChunk(c.level),nbt(BlockEntity.FURNACE,63));when(c.level.getBlockEntity(any(Vector3.class))).thenReturn(furnace);when(c.level.getBlockDataAt(0,64,0)).thenReturn(2);
        Item coal=new cn.nukkit.item.ItemCoal(0,5);coal.setNamedTag(new CompoundTag().putString("opaque","fuel-tags"));assertTrue(c.hopper.getInventory().setItem(0,coal));assertTrue(c.hopper.pushItems());assertEquals(4,c.hopper.getInventory().getItem(0).getCount());coal.setCount(1);assertTrue(furnace.getInventory().getFuel().equalsExact(coal));
    }

}
