package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.PlayerEnderChestInventory;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.inventory.transaction.action.CreativeInventoryAction;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.inventory.transaction.action.DropItemAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.AddItemEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.parallel.Isolated
class InventoryTransactionPreparedWorldDropTest {
    private static Server previousServer;
    private boolean originalPrimary;

    @org.junit.jupiter.api.BeforeAll static void captureServer() {
        previousServer = Server.getInstance();
        MockServer.init();
    }

    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }
    private PluginManager originalPlugins;
    private PluginManager plugins;
    private Fixture f;

    @BeforeEach void setup() throws Exception {
        MockServer.init();
        Server server = Server.getInstance();
        originalPrimary = server.isPrimaryThread();
        originalPlugins = server.getPluginManager();
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        f = new Fixture(server);
    }
    @AfterEach void cleanup() {
        when(Server.getInstance().getPluginManager()).thenReturn(originalPlugins);
        when(Server.getInstance().isPrimaryThread()).thenReturn(originalPrimary);
    }

    @Test void cancelledNativeSourceCannotLeaveARealConstructedWorldItem() { cancelled(false); }
    @Test void earlierWorldActionCannotSurviveACancelledNativeSource() { cancelled(true); }
    private void cancelled(boolean dropFirst) {
        var tx = f.drop(dropFirst);
        events(event -> { if (event instanceof EntityInventoryChangeEvent change) change.setCancelled(); });
        tx.execute();
        assertEquals(4, f.total(), "real constructor publication cannot coexist with a refused native debit");
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.chunkEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
        assertFalse(tx.hasExecuted());
    }

    @Test void itemSpawnEventCannotPickUpSaveOrMergeTheConstructingItem() {
        AtomicInteger observed = new AtomicInteger();
        var tx = f.drop(true);
        events(event -> {
            if (event instanceof ItemSpawnEvent spawn) {
                EntityItem item = spawn.getEntity();
                assertFalse(f.chunkEntities.containsValue(item));
                assertFalse(f.levelEntities.containsValue(item));
                item.setPickupDelay(0);
                assertFalse(f.picker.pickupEntity(item, true), "real pickup must refuse the constructing source");
                assertFalse(item.canBeSavedWithChunk());
                assertFalse(item.isMergeable());
                item.setMergeable(true);
                item.setCanBeSavedWithChunk(true);
                assertFalse(item.isMergeable());
                assertFalse(item.canBeSavedWithChunk());
                assertFalse(item.onUpdate(100));
                assertFalse(item.entityBaseTick(1));
                item.scheduleUpdate();
                assertFalse(f.level.updateEntities.containsKey(item.getId()));
                item.spawnTo(f.picker);
                assertEquals(0, f.spawnPackets.get());
                observed.incrementAndGet();
            }
        });
        assertTrue(tx.execute());
        assertEquals(1, observed.get());
        assertEquals(4, f.total());
    }

    @Test void entitySpawnEventAlsoKeepsTheRegisteredConstructingItemSealed() {
        AtomicInteger observed = new AtomicInteger();
        List<String> order = new ArrayList<>();
        var tx = f.drop(true);
        events(event -> {
            if (event instanceof ItemSpawnEvent) order.add("item-before-registration");
            if (event instanceof EntitySpawnEvent spawn) {
                EntityItem item = (EntityItem) spawn.getEntity();
                assertSame(item, f.chunkEntities.get(item.getId()));
                assertSame(item, f.levelEntities.get(item.getId()));
                item.setPickupDelay(0);
                assertFalse(f.picker.pickupEntity(item, true), "registration alone must not publish a source");
                assertFalse(item.canBeSavedWithChunk());
                assertFalse(item.isMergeable());
                item.scheduleUpdate();
                assertFalse(f.level.updateEntities.containsKey(item.getId()));
                item.spawnTo(f.picker);
                assertEquals(0, f.spawnPackets.get());
                assertEquals(4, f.sourceInventory.getItem(0).getCount(), "constructor listeners precede native writes");
                order.add("entity-after-hidden-registration");
                observed.incrementAndGet();
            }
        });
        assertTrue(tx.execute());
        assertEquals(1, observed.get());
        assertEquals(List.of("item-before-registration", "entity-after-hidden-registration"), order);
        assertEquals(4, f.total());
    }

    @Test void throwingItemSpawnListenerCannotConsumeNativeItems() { constructorThrow(false); }
    @Test void throwingEntitySpawnListenerCannotConsumeNativeItems() { constructorThrow(true); }
    private void constructorThrow(boolean registered) {
        var tx = f.drop(false);
        events(event -> {
            if (registered ? event instanceof EntitySpawnEvent : event instanceof ItemSpawnEvent)
                throw new IllegalStateException("constructor listener failed");
        });
        assertThrows(IllegalStateException.class, tx::execute);
        assertEquals(4, f.total());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.chunkEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
        assertFalse(tx.hasExecuted());
    }

    @Test void itemSpawnCloseVetoRestoresTheNativeSource() { constructorClose(false); }
    @Test void entitySpawnCloseVetoRestoresTheNativeSource() { constructorClose(true); }
    private void constructorClose(boolean registered) {
        var tx = f.drop(false);
        events(event -> {
            if (registered && event instanceof EntitySpawnEvent spawn) spawn.getEntity().close();
            if (!registered && event instanceof ItemSpawnEvent spawn) spawn.getEntity().close();
        });
        assertFalse(tx.execute());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.chunkEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void constructorKillVetoCannotPublishADeadDropAndConsumeItsSource() {
        var tx = f.drop(false);
        events(event -> { if (event instanceof EntitySpawnEvent spawn) spawn.getEntity().kill(); });
        assertFalse(tx.execute());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.chunkEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void constructorTeleportCannotPublishAnUnexpectedPlacement() {
        var tx = f.drop(false);
        events(event -> { if (event instanceof ItemSpawnEvent spawn) spawn.getEntity().teleport(new Vector3(2, 64, 2)); });
        assertFalse(tx.execute());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.chunkEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void throwingConstructorAfterChunkReplacementRemovesTheOriginalRegistration() throws Exception {
        Map<Long, Entity> otherEntities = new HashMap<>();
        FullChunk other = f.chunk(otherEntities, 1);
        var tx = f.drop(true);
        events(event -> {
            if (event instanceof EntitySpawnEvent spawn) {
                spawn.getEntity().chunk = other;
                throw new IllegalStateException("failure after registered chunk replacement");
            }
        });
        assertThrows(IllegalStateException.class, tx::execute);
        assertTrue(f.chunkEntities.isEmpty(), "remove the registered identity, not only the callback's replacement chunk");
        assertTrue(otherEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
    }

    @Test void nativeCallbackCannotInflateAnAlreadyPreparedWorldItem() {
        AtomicReference<EntityItem> prepared = new AtomicReference<>();
        var tx = f.drop(true);
        events(event -> {
            if (event instanceof ItemSpawnEvent spawn) prepared.set(spawn.getEntity());
            if (event instanceof EntityInventoryChangeEvent) prepared.get().getItem().setCount(8);
        });
        assertFalse(tx.execute());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.chunkEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
        assertEquals(4, f.total());
    }

    @Test void bothConstructorCallbacksRefuseNativeHopperPickupBeforePublication() {
        var hopper = mock(cn.nukkit.block.BlockHopper.IHopper.class, CALLS_REAL_METHODS);
        var inventory = new cn.nukkit.inventory.HopperInventory(mock(cn.nukkit.blockentity.BlockEntityHopper.class));
        when(hopper.getInventory()).thenReturn(inventory);
        when(hopper.getPosition()).thenReturn(new cn.nukkit.level.Position(0, 64, 0, f.level));
        var area = new cn.nukkit.math.SimpleAxisAlignedBB(-1, 64, -1, 1, 67, 1);
        AtomicInteger calls = new AtomicInteger();
        events(event -> {
            Entity entity = event instanceof ItemSpawnEvent spawn ? spawn.getEntity()
                    : event instanceof EntitySpawnEvent spawn ? spawn.getEntity() : null;
            if (entity == null) return;
            try (var query = mockStatic(cn.nukkit.utils.CollisionHelper.class)) {
                query.when(() -> cn.nukkit.utils.CollisionHelper.getPickupEntities(f.level, area, true))
                        .thenReturn(List.of(entity));
                assertFalse(hopper.pickupItems(area));
                assertTrue(inventory.isEmpty());
                assertFalse(entity.isClosed());
                calls.incrementAndGet();
            }
        });
        assertTrue(f.drop(false).execute());
        assertEquals(2, calls.get());
        assertEquals(4, f.total());
    }

    @Test void legalOrdinaryDropStillPublishesOneWorldSource() {
        var tx = f.drop(false);
        assertTrue(tx.execute());
        assertTrue(f.sourceInventory.getItem(0).isNull());
        assertEquals(1, f.levelEntities.size());
        assertEquals(4, f.total());
        EntityItem item = (EntityItem) f.levelEntities.values().iterator().next();
        assertTrue(item.canBeSavedWithChunk());
        assertTrue(item.isMergeable());
    }

    @Test void ordinaryPublicLevelDropKeepsPublicConstructorBehavior() {
        AtomicInteger count = new AtomicInteger();
        events(event -> {
            if (event instanceof ItemSpawnEvent spawn) {
                assertFalse(spawn.getEntity().isPublicationDeferred());
                assertTrue(spawn.getEntity().canBeSavedWithChunk());
                assertTrue(spawn.getEntity().isMergeable());
                count.incrementAndGet();
            }
        });
        f.level.dropItem(new Vector3(0, 65, 0), Item.get(Item.EMERALD, 0, 2), new Vector3(0, 0, 0.4), 40);
        assertEquals(1, count.get());
        assertEquals(2, ((EntityItem) f.levelEntities.values().iterator().next()).getItem().getCount());
    }

    @Test void constructorCallbacksCannotWriteClaimedNativeBackingsDirectly() {
        AtomicInteger refused = new AtomicInteger();
        events(event -> {
            if (event instanceof ItemSpawnEvent || event instanceof EntitySpawnEvent) {
                assertFalse(f.sourceInventory.setItem(0, Item.get(Item.EMERALD, 0, 8)));
                assertEquals(4, f.sourceInventory.getItem(0).getCount());
                refused.incrementAndGet();
            }
        });
        assertTrue(f.drop(false).execute());
        assertEquals(2, refused.get());
        assertEquals(4, f.total());
    }

    @Test void bothConstructorEventsAndNativeCallbacksKeepCanonicalBackingClaimed() {
        AtomicInteger rejected = new AtomicInteger();
        doReturn(true).when(Server.getInstance()).isPrimaryThread();
        events(event -> {
            if (event instanceof ItemSpawnEvent || event instanceof EntitySpawnEvent || event instanceof EntityInventoryChangeEvent) {
                assertFalse(InventoryMutationGuard.canCaptureProfile(List.of(f.sourceInventory)));
                assertNull(InventoryMutationGuard.tryClaim(List.of(f.sourceInventory)));
                rejected.incrementAndGet();
            }
        });
        assertTrue(f.drop(false).execute());
        assertEquals(3, rejected.get());
        assertTrue(InventoryMutationGuard.canCaptureProfile(List.of(f.sourceInventory)));
    }

    @Test void cancelledPlayerDropEventPrecedesAndPreventsAnyEntityConstruction() {
        AtomicInteger constructions = new AtomicInteger();
        events(event -> {
            if (event instanceof PlayerDropItemEvent drop) drop.setCancelled();
            if (event instanceof ItemSpawnEvent || event instanceof EntitySpawnEvent) constructions.incrementAndGet();
        });
        assertFalse(f.drop(false).execute());
        assertEquals(0, constructions.get());
        assertEquals(4, f.total());
    }

    @Test void throwingLaterNativeSetterDiscardsEveryHiddenOutputAndRestoresBothSlots() {
        f.sourceInventory.setItemForce(1, Item.get(Item.DIAMOND, 0, 3));
        InventoryTransaction tx = f.twoDrops();
        events(event -> {
            if (event instanceof EntityInventoryChangeEvent change && change.getSlot() == 1)
                throw new IllegalStateException("second native setter failed");
        });
        assertThrows(IllegalStateException.class, tx::execute);
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertEquals(3, f.sourceInventory.getItem(1).getCount());
        assertTrue(f.levelEntities.isEmpty());
        assertTrue(f.chunkEntities.isEmpty());
        assertFalse(tx.hasExecuted());
    }

    @Test void laterConstructorThrowDiscardsEarlierPreparedOutput() {
        f.sourceInventory.setItemForce(1, Item.get(Item.DIAMOND, 0, 3));
        AtomicInteger events = new AtomicInteger();
        InventoryTransaction tx = f.twoDrops();
        events(event -> {
            if (event instanceof EntitySpawnEvent && events.incrementAndGet() == 2)
                throw new IllegalStateException("second constructor failed");
        });
        assertThrows(IllegalStateException.class, tx::execute);
        assertEquals(7, f.total());
        assertTrue(f.chunkEntities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
        assertFalse(tx.hasExecuted());
    }

    @Test void packetFailureAfterAllTicketsAreReleasedNeverRefundsNativeSlots() {
        f.sourceInventory.setItemForce(1, Item.get(Item.DIAMOND, 0, 3));
        InventoryTransaction tx = f.twoDrops();
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof AddItemEntityPacket) {
                assertTrue(tx.hasExecuted());
                assertTrue(f.sourceInventory.getItem(0).isNull());
                assertTrue(f.sourceInventory.getItem(1).isNull());
                for (Entity entity : f.levelEntities.values()) assertFalse(entity.isPublicationDeferred());
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("first spawn packet failed");
            }
            return true;
        }).when(f.picker).dataPacket(any(DataPacket.class));
        assertThrows(IllegalStateException.class, tx::execute);
        assertEquals(2, attempts.get());
        assertTrue(tx.hasExecuted());
        assertEquals(7, f.total());
        assertEquals(2, f.levelEntities.size());
        assertTrue(f.sourceInventory.getContents().isEmpty());
    }

    @Test void recipientTransferDuringNotificationThenNetworkFailureCannotRefundTheSource() {
        InventoryTransaction tx = f.drop(false);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof AddItemEntityPacket) {
                EntityItem world = (EntityItem) f.levelEntities.values().iterator().next();
                world.setPickupDelay(0);
                assertTrue(f.picker.pickupEntity(world, true));
                throw new IllegalStateException("network failure after recipient accepted");
            }
            return true;
        }).when(f.picker).dataPacket(any(DataPacket.class));
        assertThrows(IllegalStateException.class, tx::execute);
        assertTrue(tx.hasExecuted());
        assertTrue(f.sourceInventory.getItem(0).isNull());
        assertEquals(4, f.pickerInventory.getItem(0).getCount());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void lateNativeCallbackCloseOrModeChangeRestoresPhysicalSources() {
        AtomicReference<EntityItem> prepared = new AtomicReference<>();
        events(event -> {
            if (event instanceof EntitySpawnEvent spawn) prepared.set((EntityItem) spawn.getEntity());
            if (event instanceof EntityInventoryChangeEvent) prepared.get().close();
        });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
        events(event -> { if (event instanceof EntityInventoryChangeEvent) doReturn(1).when(f.source).getGamemode(); });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void callbackActionRemovalOrTargetChangeFailsWithoutWorldPublication() {
        InventoryTransaction tx = f.drop(false);
        events(event -> { if (event instanceof EntitySpawnEvent) tx.actions.remove(1); });
        assertFalse(tx.execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
        InventoryTransaction other = f.drop(false);
        events(event -> {
            if (event instanceof EntityInventoryChangeEvent)
                other.actions.get(1).setTargetItem(Item.get(Item.EMERALD, 0, 8));
        });
        assertFalse(other.execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void constructorMetadataSanitationPreservesQuantityAndNormalOwnerDrops() {
        Item marked = Item.get(Item.EMERALD, 0, 4);
        marked.setNamedTag(new CompoundTag().putBoolean("example_policy", true).putString("owner", "existing-owner"));
        f.sourceInventory.setItemForce(0, marked);
        InventoryTransaction tx = new InventoryTransaction(f.source, List.of(
                new SlotChangeAction(f.sourceInventory, 0, marked, Item.get(Item.AIR)),
                new DropItemAction(Item.get(Item.AIR), marked)));
        events(event -> {
            if (event instanceof ItemSpawnEvent spawn) {
                Item item = spawn.getEntity().getItem();
                CompoundTag clean = item.getNamedTag().clone();
                clean.remove("example_policy");
                item.setNamedTag(clean);
                spawn.getEntity().setMergeable(false);
            }
        });
        assertTrue(tx.execute());
        EntityItem result = (EntityItem) f.levelEntities.values().iterator().next();
        assertEquals(4, result.getItem().getCount());
        assertEquals("existing-owner", result.getItem().getNamedTag().getString("owner"));
        assertFalse(result.getItem().getNamedTag().contains("example_policy"));
        assertFalse(result.isMergeable());
        assertEquals(4, f.total());
    }

    @Test void lateMetadataMutationAfterApprovedConstructorRejectsPublication() {
        AtomicReference<EntityItem> prepared = new AtomicReference<>();
        events(event -> {
            if (event instanceof ItemSpawnEvent spawn) prepared.set(spawn.getEntity());
            if (event instanceof EntityInventoryChangeEvent)
                prepared.get().getItem().setNamedTag(new CompoundTag().putString("late", "unapproved"));
        });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void preparedMergeParticipantCannotBeAbsorbedByAnOrdinaryNeighbour() throws Exception {
        var method = EntityItem.class.getDeclaredMethod("mergeableWith", EntityItem.class);
        method.setAccessible(true);
        EntityItem neighbour = new EntityItem(f.chunk,
                Entity.getDefaultNBT(new Vector3(0, 65.3, 0)).putShort("Health", 5)
                        .putCompound("Item", cn.nukkit.nbt.NBTIO.putItemHelper(Item.get(Item.EMERALD, 0, 1))));
        events(event -> {
            if (event instanceof EntitySpawnEvent spawn) {
                EntityItem prepared = (EntityItem) spawn.getEntity();
                assertFalse((Boolean) method.invoke(neighbour, prepared));
                assertFalse((Boolean) method.invoke(prepared, neighbour));
            }
        });
        assertTrue(f.drop(false).execute());
        assertEquals(5, f.total());
    }

    @Test void serializationAndMetadataMovementPublicationAreSuppressedWhilePrepared() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(call -> { attempts.incrementAndGet(); return true; }).when(f.picker).dataPacket(any(DataPacket.class));
        events(event -> {
            if (event instanceof ItemSpawnEvent spawn) {
                EntityItem prepared = spawn.getEntity();
                CompoundTag before = prepared.namedTag.clone();
                prepared.saveNBT();
                assertEquals(before, prepared.namedTag);
                prepared.sendData(f.picker);
                prepared.sendData(new Player[] {f.picker});
                prepared.updateMovement();
                prepared.addMovement(2, 65, 2, 0, 0, 0);
                prepared.spawnToAll();
                assertEquals(0, attempts.get());
                verify(f.level, never()).addEntityMovement(any(Entity.class), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }
        });
        assertTrue(f.drop(false).execute());
        assertEquals(1, attempts.get());
    }

    @Test void unsupportedCustomAndCreativeWorldCombinationsFailClosed() {
        InventoryAction custom = mock(InventoryAction.class);
        InventoryTransaction tx = new InventoryTransaction(f.source, List.of(
                new SlotChangeAction(f.sourceInventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4)), custom));
        assertFalse(tx.execute());
        verify(custom, never()).execute(any(Player.class));
        InventoryTransaction creative = new InventoryTransaction(f.source, List.of(
                new CreativeInventoryAction(Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR), CreativeInventoryAction.TYPE_CREATE_ITEM),
                new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4))));
        assertFalse(creative.execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void callbacksChangingSessionVersionRejectTheWorldCommit() {
        events(event -> { if (event instanceof EntitySpawnEvent) f.source.protocol = GameVersion.V1_26_50.getProtocol(); });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void exactNativeDropUsesExistingSessionAwareSpawnAcrossEverySupportedVersion() throws Exception {
        for (GameVersion version : GameVersion.values()) {
            if (version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                    || version.getProtocol() > GameVersion.V1_26_50.getProtocol()) continue;
            f = new Fixture(Server.getInstance());
            f.source.protocol = version.getProtocol(); f.picker.protocol = version.getProtocol();
            doReturn(version).when(f.source).getGameVersion();
            doReturn(version).when(f.picker).getGameVersion();
            doAnswer(call -> {
                if (call.getArgument(0) instanceof AddItemEntityPacket packet) {
                    assertEquals(4, packet.item.getCount());
                    packet.protocol = version.getProtocol();
                    packet.encode();
                    assertTrue(packet.getBuffer().length > 0);
                    f.spawnPackets.incrementAndGet();
                }
                return true;
            }).when(f.picker).dataPacket(any(DataPacket.class));
            assertTrue(f.drop(false).execute(), version.toString());
            assertEquals(1, f.spawnPackets.get(), version.toString());
            assertEquals(4, f.total(), version.toString());
        }
    }

    @Test void serverMetadataCanBeSanitizedOnThePreparedWorldCopy() {
        Item item = Item.get(Item.EMERALD, 0, 4);
        item.setNamedTag(new CompoundTag().putString("example_source_policy", "server-only"));
        f.sourceInventory.setItemForce(0, item);
        InventoryTransaction tx = new InventoryTransaction(f.source, List.of(
                new SlotChangeAction(f.sourceInventory, 0, item, Item.get(Item.AIR)),
                new DropItemAction(Item.get(Item.AIR), item)));
        events(event -> {
            if (event instanceof ItemSpawnEvent spawn) {
                spawn.getEntity().getItem().setNamedTag(new CompoundTag());
            }
        });
        assertTrue(tx.execute());
        EntityItem world = (EntityItem) f.levelEntities.values().iterator().next();
        assertFalse(world.getItem().hasCompoundTag());
        assertEquals(4, world.getItem().getCount());
        assertEquals(4, f.total());
    }

    @Test void constructorQuantityOrDamageRewriteCannotPublishAnUnfundedVariant() {
        events(event -> { if (event instanceof ItemSpawnEvent spawn) spawn.getEntity().getItem().setCount(8); });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
        events(event -> { if (event instanceof EntitySpawnEvent spawn) ((EntityItem) spawn.getEntity()).getItem().setDamage(1); });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void cancelledSecondNativeWriteRestoresEveryAuthoredDebitAndDiscardsAllOutputs() {
        f.sourceInventory.setItemForce(1, Item.get(Item.DIAMOND, 0, 3));
        events(event -> {
            if (event instanceof EntityInventoryChangeEvent change && change.getSlot() == 1) change.setCancelled();
        });
        InventoryTransaction tx = f.twoDrops();
        assertFalse(tx.execute());
        assertFalse(tx.hasExecuted());
        assertEquals(7, f.total());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertEquals(3, f.sourceInventory.getItem(1).getCount());
        assertTrue(f.levelEntities.isEmpty());
        assertTrue(f.chunkEntities.isEmpty());
    }

    @Test void unexpectedConstructorLevelReplacementIsCleanedFromItsExactRegistration() throws Exception {
        Level other = mock(Level.class);
        Long2ObjectNonBlockingMap<Entity> entities = new Long2ObjectNonBlockingMap<>();
        doAnswer(call -> { Entity item = call.getArgument(0); entities.put(item.getId(), item); return null; })
                .when(other).addEntity(any(Entity.class));
        Fixture.field(Level.class, other, "entities", entities);
        Fixture.field(Level.class, other, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
        doCallRealMethod().when(other).discardDeferredEntityRegistration(any(Entity.class));
        events(event -> { if (event instanceof ItemSpawnEvent spawn) spawn.getEntity().level = other; });
        assertFalse(f.drop(false).execute());
        assertTrue(entities.isEmpty());
        assertTrue(f.levelEntities.isEmpty());
        assertTrue(f.chunkEntities.isEmpty());
        assertEquals(4, f.total());
    }

    @Test void killingThenRevivingAHiddenDropDoesNotEraseThePreparationVeto() {
        events(event -> {
            if (event instanceof EntitySpawnEvent spawn) {
                spawn.getEntity().kill();
                spawn.getEntity().setHealth(5);
            }
        });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void callbackMovingTheActorCannotCommitTheCapturedDropContext() {
        events(event -> { if (event instanceof EntitySpawnEvent) f.source.y += 5; });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void cancelledNativeResizeMustRestoreCapacityAlongWithHighSlotContents() {
        int originalSize = f.sourceInventory.getSize();
        f.sourceInventory.setItemForce(35, Item.get(Item.DIAMOND, 0, 3));
        events(event -> {
            if (event instanceof EntityInventoryChangeEvent change) {
                f.sourceInventory.setSize(1);
                change.setCancelled();
            }
        });
        assertFalse(f.drop(false).execute());
        assertEquals(originalSize, f.sourceInventory.getSize(), "rollback must not strand an existing high slot outside the inventory");
        assertEquals(3, f.sourceInventory.getItem(35).getCount());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void acceptedNativeResizeCannotPublishBeforeTheCapturedCapacityIsStable() {
        int originalSize = f.sourceInventory.getSize();
        f.sourceInventory.setItemForce(35, Item.get(Item.DIAMOND, 0, 3));
        events(event -> { if (event instanceof EntityInventoryChangeEvent) f.sourceInventory.setSize(1); });
        assertFalse(f.drop(false).execute());
        assertEquals(originalSize, f.sourceInventory.getSize());
        assertEquals(3, f.sourceInventory.getItem(35).getCount());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void constructorSourceTransferIntoOffhandCannotSurviveAnAuthoredMainSlotRollback() {
        events(event -> {
            if (event instanceof ItemSpawnEvent) {
                Item transferred = f.sourceInventory.getItem(0);
                f.sourceOffhand.setItemForce(0, transferred);
                f.sourceInventory.setItemForce(0, Item.get(Item.AIR));
                assertEquals(4, f.total(), "the callback transferred the existing source without creating extra items");
            }
        });
        assertFalse(f.drop(false).execute());
        assertEquals(4, f.total(), "native rollback must include the player's untouched destination view");
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertTrue(f.sourceOffhand.getItem(0).isNull());
        assertTrue(f.levelEntities.isEmpty());
        assertTrue(f.chunkEntities.isEmpty());
    }

    @Test void constructorAndNativeTransfersIntoEveryCanonicalDestinationViewAreRolledBack() throws Exception {
        for (boolean nativeCallback : List.of(false, true)) {
            for (int view = 0; view < 4; view++) {
                f = new Fixture(Server.getInstance());
                final BaseInventory destination = switch (view) {
                    case 0 -> f.sourceOffhand;
                    case 1 -> f.sourceEnder;
                    case 2 -> f.sourceUI.getCursorInventory();
                    default -> f.sourceInventory;
                };
                final int slot = view == 3 ? 36 : 0;
                events(event -> {
                    if (nativeCallback ? event instanceof EntityInventoryChangeEvent : event instanceof ItemSpawnEvent) {
                        Item source = f.sourceInventory.getItem(0);
                        destination.setItemForce(slot, source);
                        f.sourceInventory.setItemForce(0, Item.get(Item.AIR));
                    }
                });
                assertFalse(f.drop(false).execute(), "view=" + view + " native=" + nativeCallback);
                assertEquals(4, f.total());
                assertTrue(destination.getItem(slot).isNull());
                assertEquals(4, f.sourceInventory.getItem(0).getCount());
                assertTrue(f.levelEntities.isEmpty());
                assertTrue(f.chunkEntities.isEmpty());
            }
        }
    }

    @Test void untouchedCanonicalContentsAndArmorRemainAvailableOnASuccessfulDrop() {
        f.sourceInventory.setItemForce(36, Item.get(Item.DIAMOND_HELMET, 0, 1));
        f.sourceOffhand.setItemForce(0, Item.get(Item.ARROW, 0, 3));
        f.sourceEnder.setItemForce(0, Item.get(Item.DIAMOND, 0, 2));
        f.sourceUI.getCursorInventory().setItemForce(0, Item.get(Item.GOLD_INGOT, 0, 5));
        assertTrue(f.drop(false).execute());
        assertEquals(1, f.sourceInventory.getItem(36).getCount());
        assertEquals(3, f.sourceOffhand.getItem(0).getCount());
        assertEquals(2, f.sourceEnder.getItem(0).getCount());
        assertEquals(5, f.sourceUI.getCursorInventory().getItem(0).getCount());
        assertEquals(15, f.total());
    }

    @Test void callbackStackLimitMutationCannotOutliveWorldPreparationRollback() {
        int limit = f.sourceInventory.getMaxStackSize();
        events(event -> { if (event instanceof EntityInventoryChangeEvent) f.sourceInventory.setMaxStackSize(2); });
        assertFalse(f.drop(false).execute());
        assertEquals(limit, f.sourceInventory.getMaxStackSize());
        assertEquals(4, f.total());
        assertTrue(f.levelEntities.isEmpty());
    }

    @Test void exactAuthoredCursorWriteUsesTheSharedUiBackingWithoutLosingUntouchedSlots() {
        f.sourceInventory.setItemForce(0, Item.get(Item.AIR));
        f.sourceUI.getCursorInventory().setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        f.sourceUI.setItemForce(5, Item.get(Item.DIAMOND, 0, 2));
        var tx = new InventoryTransaction(f.source, List.of(
                new SlotChangeAction(f.sourceUI.getCursorInventory(), 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4))));
        assertTrue(tx.execute());
        assertTrue(f.sourceUI.getCursorInventory().getItem(0).isNull());
        assertEquals(2, f.sourceUI.getItem(5).getCount());
        assertEquals(6, f.total());
    }

    @Test void exactDoubleChestDropClaimsAndChecksBothPhysicalHalves() {
        BlockEntityChest leftHolder = mock(BlockEntityChest.class), rightHolder = mock(BlockEntityChest.class);
        ChestInventory left = new ChestInventory(leftHolder), right = new ChestInventory(rightHolder);
        when(leftHolder.getRealInventory()).thenReturn(left); when(rightHolder.getRealInventory()).thenReturn(right);
        left.setItemForce(0, Item.get(Item.DIAMOND, 0, 3));
        right.setItemForce(1, Item.get(Item.EMERALD, 0, 4));
        DoubleChestInventory chest = new DoubleChestInventory(leftHolder, rightHolder);
        var tx = new InventoryTransaction(f.source, List.of(
                new SlotChangeAction(chest, left.getSize() + 1, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4))));
        assertTrue(tx.execute());
        assertTrue(right.getItem(1).isNull());
        assertEquals(3, left.getItem(0).getCount());
        assertEquals(4, f.sourceInventory.getItem(0).getCount());
        assertEquals(8, f.total());
    }

    @Test void levelDbSaveAdapterRefusesHiddenRegistrationAndAcceptsReleasedItem() throws Exception {
        var adapter = Class.forName("cn.nukkit.level.format.leveldb.serializer.EntityNbtAdapter")
                .getDeclaredMethod("isSavable", Entity.class);
        adapter.setAccessible(true);
        events(event -> {
            if (event instanceof EntitySpawnEvent spawn) assertFalse((Boolean) adapter.invoke(null, spawn.getEntity()));
        });
        assertTrue(f.drop(false).execute());
        assertTrue((Boolean) adapter.invoke(null, f.levelEntities.values().iterator().next()));
    }

    @FunctionalInterface private interface Callback { void apply(Event event) throws Exception; }
    private void events(Callback callback) {
        doAnswer(call -> { callback.apply(call.getArgument(0)); return null; })
                .when(plugins).callEvent(any(Event.class));
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Map<Long, Entity> chunkEntities = new LinkedHashMap<>();
        final Map<Long, Entity> levelEntities = new Long2ObjectNonBlockingMap<>();
        final FullChunk chunk;
        final Player source = mock(Player.class, CALLS_REAL_METHODS);
        final Player picker = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory sourceInventory = new PlayerInventory(source);
        final PlayerInventory pickerInventory = new PlayerInventory(picker);
        final PlayerOffhandInventory sourceOffhand = new PlayerOffhandInventory(source);
        final PlayerUIInventory sourceUI = new PlayerUIInventory(source);
        final PlayerEnderChestInventory sourceEnder = new PlayerEnderChestInventory(source);
        final AtomicInteger spawnPackets = new AtomicInteger();

        Fixture(Server server) throws Exception {
            when(level.getServer()).thenReturn(server);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of(1, picker));
            when(level.getEntity(anyLong())).thenAnswer(call -> levelEntities.get(call.getArgument(0)));
            when(level.getEntities()).thenAnswer(call -> levelEntities.values().toArray(Entity[]::new));
            field(Level.class, level, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            field(Level.class, level, "entities", levelEntities);
            doCallRealMethod().when(level).discardDeferredEntityRegistration(any(Entity.class));
            doCallRealMethod().when(level).prepareItemDrop(any(Vector3.class), any(Item.class), any(Vector3.class), anyInt());
            chunk = chunk(chunkEntities, 0);
            when(level.getChunk(eq(0), eq(0), eq(true))).thenReturn((cn.nukkit.level.format.generic.BaseFullChunk) null);
            doReturn(chunk).when(level).getChunk(0, 0, true);
            doAnswer(call -> { Entity entity = call.getArgument(0); levelEntities.put(entity.getId(), entity); return null; })
                    .when(level).addEntity(any(Entity.class));
            doAnswer(call -> { Entity entity = call.getArgument(0); levelEntities.remove(entity.getId(), entity); return null; })
                    .when(level).removeEntity(any(Entity.class));
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), any(Vector3.class), anyInt());
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), any(Vector3.class), anyBoolean(), anyInt());
            player(source, sourceInventory, server);
            field(EntityHumanType.class, source, "offhandInventory", sourceOffhand);
            field(EntityHumanType.class, source, "enderChestInventory", sourceEnder);
            field(Player.class, source, "playerUIInventory", sourceUI);
            player(picker, pickerInventory, server);
            doAnswer(call -> { if (call.getArgument(0) instanceof AddItemEntityPacket) spawnPackets.incrementAndGet(); return true; })
                    .when(picker).dataPacket(any(DataPacket.class));
            sourceInventory.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        }

        private void player(Player player, PlayerInventory inventory, Server server) throws Exception {
            field(Entity.class, player, "server", server);
            field(EntityHumanType.class, player, "inventory", inventory);
            field(Player.class, player, "usedChunks", new HashMap<>(Map.of(Level.chunkHash(0, 0), true)));
            player.level = level; player.spawned = true; player.y = 64;
            player.protocol = GameVersion.V1_20_0.getProtocol();
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(Map.of()).when(player).getViewers();
            doReturn(false).when(player).isSpectator();
            doReturn(false).when(player).isCreative();
            doReturn(GameVersion.V1_20_0).when(player).getGameVersion();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            doReturn(0).when(player).getWindowId(any());
            doReturn(new Vector3(0, 0, 1)).when(player).getDirectionVector();
            doNothing().when(player).setUsingItem(anyBoolean());
        }

        FullChunk chunk(Map<Long, Entity> entities, int x) {
            FullChunk value = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(value.getProvider()).thenReturn(provider);
            when(value.getX()).thenReturn(x);
            when(value.getEntities()).thenReturn(entities);
            doAnswer(call -> { Entity entity = call.getArgument(0); entities.put(entity.getId(), entity); return null; })
                    .when(value).addEntity(any(Entity.class));
            doAnswer(call -> { Entity entity = call.getArgument(0); entities.remove(entity.getId(), entity); return null; })
                    .when(value).removeEntity(any(Entity.class));
            return value;
        }

        InventoryTransaction drop(boolean dropFirst) {
            InventoryAction debit = new SlotChangeAction(sourceInventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR));
            InventoryAction drop = new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4));
            var tx = new InventoryTransaction(source, dropFirst ? List.of(drop, debit) : List.of(debit, drop));
            assertTrue(tx.canExecute());
            return tx;
        }

        InventoryTransaction twoDrops() {
            return new InventoryTransaction(source, List.of(
                    new SlotChangeAction(sourceInventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                    new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4)),
                    new SlotChangeAction(sourceInventory, 1, Item.get(Item.DIAMOND, 0, 3), Item.get(Item.AIR)),
                    new DropItemAction(Item.get(Item.AIR), Item.get(Item.DIAMOND, 0, 3))));
        }

        int total() {
            return sourceInventory.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + sourceOffhand.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + sourceUI.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + sourceEnder.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + pickerInventory.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + levelEntities.values().stream().filter(entity -> !entity.isClosed())
                    .mapToInt(entity -> ((EntityItem) entity).getItem().getCount()).sum();
        }

        private static void field(Class<?> owner, Object target, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
        }
    }
}
