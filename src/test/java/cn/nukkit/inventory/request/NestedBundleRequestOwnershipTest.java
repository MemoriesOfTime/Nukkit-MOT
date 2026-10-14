package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.event.inventory.ItemStackRequestActionEvent;
import cn.nukkit.inventory.BundleInventory;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.inventory.ShapelessRecipe;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TakeFromItemContainerAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TakeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.SwapAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ConsumeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.AutoCraftRecipeAction;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemDescriptorWithCount;
import cn.nukkit.network.protocol.types.inventory.descriptor.DefaultDescriptor;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/** End-to-end request controls: every diamond begins in the canonical nested carrier. */
@org.junit.jupiter.api.parallel.Isolated
class NestedBundleRequestOwnershipTest {
    private static cn.nukkit.Server previousServer;
    private PluginManager previousPlugins;
    private cn.nukkit.inventory.CraftingManager previousCraftingManager;

    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        var instance = cn.nukkit.Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }

    @org.junit.jupiter.api.AfterEach void restoreGlobalStubs() {
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(previousPlugins);
        Mockito.when(MockServer.get().getCraftingManager()).thenReturn(previousCraftingManager);
    }
    private static final int ROOT = 0;
    private static final int OUTSIDE = 1;
    private static final int MOVED_ROOT = 2;

    @BeforeAll
    static void init() {
        previousServer = cn.nukkit.Server.getInstance();
        MockServer.init();
        Item.initCreativeItems();
    }

    @BeforeEach
    void captureGlobalStubs() {
        previousPlugins = MockServer.get().getPluginManager();
        previousCraftingManager = MockServer.get().getCraftingManager();
    }

    @Test
    void successUpdatesCanonicalRootAndPreservesOpaquePayload() throws Exception {
        Fixture f = fixture();
        runRequest(f);
        assertEquals(ItemStackResponseStatus.OK, response(f));
        assertEquals(3, f.main.getItem(OUTSIDE).getCount());
        assertEquals(4, canonicalLeaf(f.main, ROOT).getItem(0).getCount());
        assertEquals(7, serializedDiamondCount(f.main));
        assertOpaque(f.main.getItem(OUTSIDE), f.payload);
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
        System.out.println("CONTROL success: canonical serialized root=4 outside=3 initial=7 opaqueNBT exact");
    }

    @Test
    void refusingNativeDestinationSetterRestoresExactCanonicalCarrierNbt() throws Exception {
        Fixture f = fixture();
        byte[] rootBefore = NBTIO.write(f.main.getItem(ROOT).getNamedTag());
        AtomicBoolean setterSeen = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getEntity() == f.player && change.getSlot() == OUTSIDE) {
                setterSeen.set(true);
                change.setCancelled(true);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runRequest(f);
        assertTrue(setterSeen.get(), "actual PlayerInventory native setter callback must run");
        assertEquals(ItemStackResponseStatus.ERROR, response(f));
        assertTrue(f.main.getItem(OUTSIDE).isNull());
        assertEquals(7, serializedDiamondCount(f.main));
        assertArrayEquals(rootBefore, NBTIO.write(f.main.getItem(ROOT).getNamedTag()),
                "rollback must preserve the entire actual canonical carrier NBT byte for byte");
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
        System.out.println("CONTROL setter refusal: canonical serialized root=7 outside=0 initial=7 carrierNBT byte exact");
    }

    @Test
    void relocatingPreexistingCanonicalRootInNativeCallbackConservesInitialDiamonds() throws Exception {
        Fixture f = fixture();
        AtomicBoolean callbackSeen = new AtomicBoolean();
        AtomicBoolean rootTransferAccepted = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getEntity() == f.player && change.getSlot() == OUTSIDE
                    && callbackSeen.compareAndSet(false, true)) {
                // Native move uses the existing carrier as its source. No direct slot-map
                // edits, force setters, item grants, or independently created payloads.
                Item source = f.main.getItem(ROOT);
                Item empty = f.main.getItem(MOVED_ROOT);
                InventoryTransaction move = new InventoryTransaction(f.player, List.of(
                        new SlotChangeAction(f.main, ROOT, source, Item.get(Item.AIR)),
                        new SlotChangeAction(f.main, MOVED_ROOT, empty, source)));
                rootTransferAccepted.set(move.execute());
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runRequest(f);
        assertTrue(callbackSeen.get(), "actual destination native setter callback must run");
        assertFalse(rootTransferAccepted.get(), "request must refuse overlapping native root move before acceptance");
        assertEquals(ItemStackResponseStatus.OK, response(f));
        assertTrue(f.main.getItem(MOVED_ROOT).isNull(), "rejected native root move must not publish a second carrier");
        int rootDiamonds = serializedDiamondCount(f.main.getItem(ROOT));
        int outsideDiamonds = f.main.getItem(OUTSIDE).getCount();
        System.out.println("REGRESSION root relocation: canonical serialized root=" + rootDiamonds
                + " outside=" + outsideDiamonds + " initial=7 acceptedNativeMove=false");
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
        assertOpaque(f.main.getItem(OUTSIDE), f.payload);
        assertEquals(7, serializedDiamondCount(f.main),
                "nested request must conserve diamonds in the entire real canonical ownership chain");
    }

    @Test
    void independentlyAcceptedCarrierMoveBeforeRequestIsNeverUndoneByStaleDynamicId() throws Exception {
        Fixture f = fixture();
        TakeFromItemContainerAction stale = leafTransfer(f, 3);
        byte[] rootBefore = NBTIO.write(f.main.getItem(ROOT).getNamedTag());
        assertTrue(move(f.player, f.main, ROOT, f.main, MOVED_ROOT),
                "independent native move before request ownership must be accepted");
        runActions(f, stale);
        assertEquals(ItemStackResponseStatus.ERROR, response(f));
        assertTrue(f.main.getItem(ROOT).isNull());
        assertTrue(f.main.getItem(OUTSIDE).isNull());
        assertEquals(7, serializedDiamondCount(f.main));
        assertArrayEquals(rootBefore, NBTIO.write(f.main.getItem(MOVED_ROOT).getNamedTag()));
        assertOpaque(canonicalLeaf(f.main, MOVED_ROOT).getItem(0), f.payload);
    }

    @Test
    void nestedCarrierInActualCursorMappingTransfersAndPersistsItsCanonicalNbt() throws Exception {
        Fixture f = fixture();
        Inventory cursor = f.player.getCursorInventory();
        assertTrue(move(f.player, f.main, ROOT, cursor, 0));
        ItemBundle root = (ItemBundle) cursor.getUnclonedItem(0);
        ItemBundle leaf = (ItemBundle) root.getInventory().getUnclonedItem(0);
        root.saveNBT();
        Fixture cursorFixture = new Fixture(f.player, f.main, leaf, f.plugins, f.packets, f.payload);
        assertSame(leaf.getInventory(), NetworkMapping.getInventory(f.player,
                ContainerSlotType.DYNAMIC_CONTAINER, leaf.getBundleId()));
        assertSame(cursor, NetworkMapping.findBundleHolder(f.player, leaf.getBundleId()).outer());
        runRequest(cursorFixture);
        assertEquals(ItemStackResponseStatus.OK, response(f));
        assertEquals(4, serializedDiamondCount(cursor.getItem(0)));
        assertEquals(3, f.main.getItem(OUTSIDE).getCount());
        assertEquals(7, serializedDiamondCount(cursor) + serializedDiamondCount(f.main));
        assertOpaque(((ItemBundle) ((ItemBundle) cursor.getItem(0)).getInventory().getItem(0))
                .getInventory().getItem(0), f.payload);
        assertOpaque(f.main.getItem(OUTSIDE), f.payload);
    }

    @Test
    void preexistingExternalDonorCannotTransferIntoClaimedNestedBackingDuringCallback() throws Exception {
        Fixture f = fixture();
        Player donor = Mockito.mock(Player.class);
        PlayerInventory donorInventory = new PlayerInventory(donor);
        Item donorSource = f.payload.clone();
        donorSource.setCount(2);
        assertTrue(donorInventory.setItem(0, donorSource, false));
        assertEquals(9, serializedDiamondCount(f.main) + serializedDiamondCount(donorInventory));
        AtomicBoolean callbackSeen = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change && change.getEntity() == f.player
                    && change.getSlot() == OUTSIDE && callbackSeen.compareAndSet(false, true)) {
                BundleInventory target = f.leaf.getInventory();
                Item before = target.getItem(0);
                Item after = before.clone(); after.setCount(9);
                accepted.set(new InventoryTransaction(f.player, List.of(
                        new SlotChangeAction(donorInventory, 0, donorInventory.getItem(0), Item.get(Item.AIR)),
                        new SlotChangeAction(target, 0, before, after))).execute());
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runRequest(f);
        assertTrue(callbackSeen.get()); assertFalse(accepted.get());
        assertEquals(ItemStackResponseStatus.OK, response(f));
        assertEquals(9, serializedDiamondCount(f.main) + serializedDiamondCount(donorInventory));
        assertEquals(2, donorInventory.getItem(0).getCount());
        assertOpaque(donorInventory.getItem(0), f.payload);
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
    }

    @Test
    void cancellationAfterSuccessfulPrefixRollsBackCanonicalRootByteExactly() throws Exception {
        Fixture f = fixture();
        byte[] before = NBTIO.write(f.main.getItem(ROOT).getNamedTag());
        int[] events = {0};
        AtomicBoolean rootMoveAccepted = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof InventoryTransactionEvent transaction && ++events[0] == 2) {
                rootMoveAccepted.set(move(f.player, f.main, ROOT, f.main, MOVED_ROOT));
                transaction.setCancelled(true);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runActions(f, leafTransfer(f, 3), outsideTransfer(3, 1));
        assertEquals(2, events[0]); assertFalse(rootMoveAccepted.get());
        assertRollback(f, before);
    }

    @Test
    void runtimeFailureAfterSuccessfulPrefixRollsBackCanonicalRootByteExactly() throws Exception {
        assertThrowingPrefixRollback(new IllegalStateException("ordinary-native-setter-failure"));
    }

    @Test
    void fatalNotificationFailureAfterSuccessfulPrefixRollsBackCanonicalRootByteExactly() throws Exception {
        assertThrowingPrefixRollback(new AssertionError("ordinary-native-setter-notification-error"));
    }

    private static void assertThrowingPrefixRollback(Throwable failure) throws Exception {
        Fixture f = fixture();
        byte[] before = NBTIO.write(f.main.getItem(ROOT).getNamedTag());
        AtomicBoolean setterSeen = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change && change.getEntity() == f.player
                    && change.getSlot() == 3) {
                setterSeen.set(true);
                throw failure;
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runActions(f, leafTransfer(f, 3), outsideTransfer(3, 1));
        assertTrue(setterSeen.get());
        assertRollback(f, before);
    }

    @Test
    void nestedResizeAfterSuccessfulPrefixRestoresSizeAndCarrierNbt() throws Exception {
        Fixture f = fixture();
        byte[] before = NBTIO.write(f.main.getItem(ROOT).getNamedTag());
        int originalSize = f.leaf.getInventory().getSize();
        AtomicBoolean resized = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof ItemStackRequestActionEvent action && action.getActionIndex() == 1) {
                f.leaf.getInventory().setSize(0);
                resized.set(true);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runActions(f, leafTransfer(f, 3), outsideTransfer(3, 1));
        assertTrue(resized.get()); assertEquals(originalSize, f.leaf.getInventory().getSize());
        assertRollback(f, before);
    }

    @Test
    void healthyMixedSwapAndNestedTransferRemainAccepted() throws Exception {
        Fixture f = fixture();
        Item stone = Item.get(Item.STONE, 0, 2); stone.autoAssignStackNetworkId();
        Item dirt = Item.get(Item.DIRT, 0, 3); dirt.autoAssignStackNetworkId();
        assertTrue(f.main.setItem(3, stone, false)); assertTrue(f.main.setItem(4, dirt, false));
        SwapAction swap = new SwapAction(slot(ContainerSlotType.HOTBAR, 3, stone.getStackNetId()),
                slot(ContainerSlotType.HOTBAR, 4, dirt.getStackNetId()));
        runActions(f, leafTransfer(f, 3), swap);
        assertEquals(ItemStackResponseStatus.OK, response(f));
        assertEquals(7, serializedDiamondCount(f.main));
        assertEquals(Item.DIRT, f.main.getItem(3).getId()); assertEquals(3, f.main.getItem(3).getCount());
        assertEquals(Item.STONE, f.main.getItem(4).getId()); assertEquals(2, f.main.getItem(4).getCount());
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
        assertOpaque(f.main.getItem(OUTSIDE), f.payload);
    }

    @Test
    void healthyMixedAutoCraftConsumeAndNestedTransferRemainAccepted() throws Exception {
        Fixture f = fixture();
        CraftingManager manager = Mockito.mock(CraftingManager.class);
        ShapelessRecipe recipe = new ShapelessRecipe("test:ordinary_log_to_planks", 10,
                Item.get(Item.PLANKS, 0, 4), List.of(Item.get(Item.LOG, 0, 1)));
        Mockito.when(f.player.getServer().getCraftingManager()).thenReturn(manager);
        Mockito.when(manager.getRecipeByNetworkId(recipe.getNetworkId())).thenReturn(recipe);
        Mockito.when(manager.matchRecipe(Mockito.anyList(), any(Item.class), Mockito.anyList())).thenReturn(recipe);
        Item log = Item.get(Item.LOG, 0, 1); log.autoAssignStackNetworkId();
        assertTrue(f.main.setItem(3, log, false));
        AutoCraftRecipeAction craft = new AutoCraftRecipeAction(recipe.getNetworkId(), 1, 1,
                List.of(new ItemDescriptorWithCount(new DefaultDescriptor(Item.LOG, 0), 1)));
        ConsumeAction consume = new ConsumeAction(1, slot(ContainerSlotType.HOTBAR, 3, log.getStackNetId()));
        TakeAction takeOutput = new TakeAction(4,
                slot(ContainerSlotType.CREATED_OUTPUT, PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0),
                slot(ContainerSlotType.HOTBAR, 4, 0));
        runActions(f, leafTransfer(f, 3), craft, consume, takeOutput);
        assertEquals(ItemStackResponseStatus.OK, response(f));
        assertEquals(7, serializedDiamondCount(f.main));
        assertTrue(f.main.getItem(3).isNull());
        assertEquals(Item.PLANKS, f.main.getItem(4).getId()); assertEquals(4, f.main.getItem(4).getCount());
        assertTrue(f.player.getUIInventory().getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
        assertOpaque(f.main.getItem(OUTSIDE), f.payload);
    }

    @Test
    void newlyCachedDescendantOfMovedCarrierRetainsRequestOwnership() throws Exception {
        Fixture f = fixture();
        byte[] before = NBTIO.write(f.main.getItem(ROOT).getNamedTag());
        Inventory cursor = f.player.getCursorInventory();
        PlayerInventory outside = new PlayerInventory(Mockito.mock(Player.class));
        AtomicBoolean attempted = new AtomicBoolean();
        AtomicBoolean accepted = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof ItemStackRequestActionEvent action && action.getActionIndex() == 1) {
                ItemBundle freshRoot = (ItemBundle) cursor.getUnclonedItem(0);
                ItemBundle freshLeaf = (ItemBundle) freshRoot.getInventory().getUnclonedItem(0);
                assertNotSame(f.leaf.getInventory(), freshLeaf.getInventory());
                attempted.set(true);
                accepted.set(move(f.player, freshLeaf.getInventory(), 0, outside, 0));
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        TakeAction rootToCursor = new TakeAction(1,
                slot(ContainerSlotType.HOTBAR, ROOT, f.main.getItem(ROOT).getStackNetId()),
                slot(ContainerSlotType.CURSOR, 0, 0));
        runActions(f, rootToCursor, leafTransfer(f, 3));
        assertTrue(attempted.get()); assertFalse(accepted.get());
        assertTrue(cursor.getItem(0).isNull()); assertTrue(outside.getItem(0).isNull());
        assertRollback(f, before);
    }

    @Test
    void reentrantActualDynamicRequestIsRefusedBeforeMutatingCanonicalRoot() throws Exception {
        Fixture f = fixture();
        AtomicBoolean attempted = new AtomicBoolean();
        AtomicBoolean refused = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change && change.getEntity() == f.player
                    && change.getSlot() == OUTSIDE && attempted.compareAndSet(false, true)) {
                TakeFromItemContainerAction nested = new TakeFromItemContainerAction(1,
                        new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0,
                                f.leaf.getInventory().getItem(0).getStackNetId(), f.leaf.getBundleId()),
                        slot(ContainerSlotType.HOTBAR, 4, 0));
                runActions(f, nested);
                refused.set(response(f) == ItemStackResponseStatus.ERROR);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runRequest(f);
        assertTrue(attempted.get()); assertTrue(refused.get());
        assertEquals(ItemStackResponseStatus.OK, response(f));
        assertTrue(f.main.getItem(4).isNull());
        assertEquals(7, serializedDiamondCount(f.main));
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
    }

    @Test
    void ordinaryReentrantRequestCannotAcceptProvisionalDynamicOutput() throws Exception {
        Fixture f = fixture();
        byte[] before = NBTIO.write(f.main.getItem(ROOT).getNamedTag());
        AtomicBoolean attempted = new AtomicBoolean();
        AtomicBoolean refused = new AtomicBoolean();
        cn.nukkit.blockentity.BlockEntityChest holder = Mockito.mock(cn.nukkit.blockentity.BlockEntityChest.class);
        holder.chunk = Mockito.mock(cn.nukkit.level.format.FullChunk.class);
        cn.nukkit.inventory.ChestInventory destination = new cn.nukkit.inventory.ChestInventory(holder);
        Mockito.when(holder.getInventory()).thenReturn(destination);
        Mockito.when(holder.getRealInventory()).thenReturn(destination);
        Mockito.when(f.player.getWindowId(destination)).thenReturn(2);
        Mockito.doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof ItemStackRequestActionEvent action && action.getActionIndex() == 1
                    && attempted.compareAndSet(false, true)) {
                assertEquals(3, f.main.getItem(OUTSIDE).getCount());
                Mockito.when(f.player.getTopWindow()).thenReturn(Optional.of(destination));
                runActions(f, new TakeAction(3, slot(ContainerSlotType.HOTBAR, OUTSIDE, 0),
                        slot(ContainerSlotType.LEVEL_ENTITY, 0, 0)));
                refused.set(response(f) == ItemStackResponseStatus.ERROR);
                action.setCancelled(true);
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));
        runActions(f, leafTransfer(f, 3), outsideTransfer(3, 1));
        assertTrue(attempted.get());
        assertEquals(7, serializedDiamondCount(f.main) + serializedDiamondCount(destination),
                "outer rollback must not retain a second accepted container grant");
        assertTrue(refused.get(), "an ordinary nested request must not acknowledge provisional bundle output");
        assertTrue(destination.getItem(0).isNull());
        assertTrue(f.main.getItem(4).isNull());
        assertRollback(f, before);
    }

    private static boolean move(Player actor, Inventory from, int fromSlot, Inventory to, int toSlot) {
        Item source = from.getItem(fromSlot);
        return new InventoryTransaction(actor, List.of(
                new SlotChangeAction(from, fromSlot, source, Item.get(Item.AIR)),
                new SlotChangeAction(to, toSlot, to.getItem(toSlot), source))).execute();
    }

    private static void assertRollback(Fixture f, byte[] rootBefore) throws Exception {
        assertEquals(ItemStackResponseStatus.ERROR, response(f));
        assertEquals(7, serializedDiamondCount(f.main));
        assertTrue(f.main.getItem(OUTSIDE).isNull());
        assertTrue(f.main.getItem(3).isNull());
        assertArrayEquals(rootBefore, NBTIO.write(f.main.getItem(ROOT).getNamedTag()));
        assertOpaque(canonicalLeaf(f.main, ROOT).getItem(0), f.payload);
    }

    private static ItemStackRequestSlotData slot(ContainerSlotType type, int slot, int stack) {
        return new ItemStackRequestSlotData(type, slot, stack, null);
    }

    private static TakeAction outsideTransfer(int destination, int count) {
        return new TakeAction(count, slot(ContainerSlotType.HOTBAR, OUTSIDE, 0),
                slot(ContainerSlotType.HOTBAR, destination, 0));
    }

    private static Fixture fixture() throws Exception {
        // Reuse the standard existing request fixture's actual mock-player factory.
        Method mockPlayer = ItemStackRequestProcessorTest.class.getDeclaredMethod("mockPlayer");
        mockPlayer.setAccessible(true);
        Player player = (Player) mockPlayer.invoke(null);
        Mockito.when(player.isCreative()).thenReturn(false);
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        player.spawned = true;
        PlayerInventory main = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager plugins = Mockito.mock(PluginManager.class);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(plugins);
        Mockito.when(player.getInventory()).thenReturn(main);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(main)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        List<DataPacket> packets = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            packets.add(invocation.getArgument(0));
            return false;
        }).when(player).dataPacket(any(DataPacket.class));

        Item payload = Item.get(Item.DIAMOND, 0, 7);
        payload.setNamedTag(new CompoundTag().putString("opaqueOwnership", "original-seven")
                .putCompound("opaqueNested", new CompoundTag().putLong("nonce", 0x1a2b3c4d5e6fL)
                        .putByteArray("bytes", new byte[]{0, 1, -1, 17, 0})));
        payload.autoAssignStackNetworkId();
        ItemBundle inner = (ItemBundle) Item.fromString("minecraft:bundle");
        inner.setNamedTag(new CompoundTag().putString("opaqueCarrier", "leaf"));
        assertTrue(inner.getInventory().setItem(0, payload, false));
        ItemBundle outer = (ItemBundle) Item.fromString("minecraft:bundle");
        outer.setNamedTag(new CompoundTag().putString("opaqueCarrier", "root"));
        assertTrue(outer.getInventory().setItem(0, inner, false));
        outer.saveNBT();
        assertTrue(main.setItem(ROOT, outer, false));
        ItemBundle canonicalRoot = (ItemBundle) main.getUnclonedItem(ROOT);
        ItemBundle canonicalLeaf = (ItemBundle) canonicalRoot.getInventory().getUnclonedItem(0);
        BundleInventory leaf = canonicalLeaf.getInventory();
        assertSame(leaf, NetworkMapping.getInventory(player, ContainerSlotType.DYNAMIC_CONTAINER,
                canonicalLeaf.getBundleId()));
        var ref = NetworkMapping.findBundleHolder(player, canonicalLeaf.getBundleId());
        assertNotNull(ref);
        assertSame(main, ref.outer());
        assertEquals(2, ref.chain().size());
        assertSame(canonicalRoot, ref.chain().get(0));
        assertSame(canonicalLeaf, ref.chain().get(1));
        assertEquals(7, serializedDiamondCount(main), "all initial diamonds must live in the actual root NBT");
        return new Fixture(player, main, canonicalLeaf, plugins, packets, payload);
    }

    private static void runRequest(Fixture f) {
        runActions(f, leafTransfer(f, 3));
    }

    private static TakeFromItemContainerAction leafTransfer(Fixture f, int count) {
        Item source = f.leaf.getInventory().getItem(0);
        return new TakeFromItemContainerAction(count,
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, source.getStackNetId(),
                        f.leaf.getBundleId()),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, OUTSIDE, 0, null));
    }

    private static void runActions(Fixture f, ItemStackRequestAction... actions) {
        ItemStackRequestHandler.handleRequests(f.player, List.of(new ItemStackRequest(61, actions, new String[0])));
    }

    private static ItemStackResponseStatus response(Fixture f) {
        return f.packets.stream().filter(ItemStackResponsePacket.class::isInstance)
                .map(ItemStackResponsePacket.class::cast).reduce((first, last) -> last)
                .orElseThrow().entries.get(0).getResult();
    }

    private static BundleInventory canonicalLeaf(PlayerInventory main, int slot) {
        // Deliberately reconstruct from canonical stored NBT rather than counting a
        // detached pre-request cached backing inventory as retained ownership.
        ItemBundle root = (ItemBundle) main.getItem(slot);
        return ((ItemBundle) root.getInventory().getItem(0)).getInventory();
    }

    private static int serializedDiamondCount(Inventory inventory) {
        return inventory.getContents().keySet().stream()
                .mapToInt(slot -> serializedDiamondCount(inventory.getItem(slot))).sum();
    }

    private static int serializedDiamondCount(Item item) {
        if (item instanceof ItemBundle bundle) return serializedDiamondCount(bundle.getInventory());
        return item.getId() == Item.DIAMOND ? item.getCount() : 0;
    }

    private static void assertOpaque(Item actual, Item initial) throws Exception {
        assertEquals(Item.DIAMOND, actual.getId());
        assertArrayEquals(NBTIO.write(initial.getNamedTag()), NBTIO.write(actual.getNamedTag()));
    }

    private record Fixture(Player player, PlayerInventory main, ItemBundle leaf, PluginManager plugins,
                           List<DataPacket> packets, Item payload) { }
}
