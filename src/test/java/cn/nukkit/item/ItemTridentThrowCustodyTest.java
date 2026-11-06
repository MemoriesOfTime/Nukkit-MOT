package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native release, projectile constructor and inventory writes. World/network only mocked. */
class ItemTridentThrowCustodyTest {
    private Player player;
    private PlayerInventory inventory;
    private PluginManager plugins;
    private Level level;
    private final List<EntityThrownTrident> created = new ArrayList<>();

    @BeforeAll static void boot() { MockServer.init(); }
    @BeforeEach void setup() throws Exception {
        MockServer.init(); MockServer.reset();
        Server server = MockServer.get();
        plugins = server.getPluginManager();
        level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        Field converting = Level.class.getDeclaredField("isBeingConverted");
        converting.setAccessible(true); converting.setBoolean(level, true);
        doAnswer(call -> {
            Entity entity = call.getArgument(0);
            if (entity instanceof EntityThrownTrident trident) created.add(trident);
            return null;
        }).when(level).addEntity(any(Entity.class));
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        player = mock(Player.class);
        player.level = level; player.chunk = chunk;
        player.x = 0.5; player.y = 65; player.z = 0.5; player.yaw = 90;
        when(player.getLevel()).thenReturn(level);
        when(player.getServer()).thenReturn(server);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
        when(player.getGamemode()).thenReturn(Player.SURVIVAL);
        when(player.getViewers()).thenReturn(Map.of());
        when(player.isAlive()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.isConnected()).thenReturn(true);
        inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(inventory);
        inventory.setItemForce(0, new ItemTrident());
    }

    @Test void successfulSurvivalThrowTransfersExactlyOneItem() {
        release();
        assertEquals(0, inventoryTridents());
        assertEquals(1, recoverableProjectileUnits());
        assertEquals(1, created.size());
    }

    @Test void cancelledShotKeepsSourceAndClosesProjectile() {
        listen(event -> { if (event instanceof EntityShootBowEvent shot) shot.setCancelled(); });
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
    }

    @Test void cancelledLaunchKeepsSourceAndClosesProjectile() {
        listen(event -> { if (event instanceof ProjectileLaunchEvent launch) launch.setCancelled(); });
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
    }

    @Test void cancelledHandDebitMustNotLeaveRecoverableProjectile() {
        listen(event -> { if (event instanceof EntityInventoryChangeEvent change
                && change.getOldItem().getId() == Item.TRIDENT && change.getNewItem().isNull()) {
            change.setCancelled();
        }});
        release();
        assertEquals(1, inventoryTridents(), "Rejected debit retains source trident");
        assertEquals(0, recoverableProjectileUnits(), "Rejected debit must close uncharged projectile");
    }

    @Test void rewrittenDebitMustNotLeaveRecoverableProjectile() {
        listen(event -> { if (event instanceof EntityInventoryChangeEvent change
                && change.getOldItem().getId() == Item.TRIDENT && change.getNewItem().isNull()) {
            change.setNewItem(change.getOldItem().clone());
        }});
        release();
        assertEquals(1, inventoryTridents(), "Rewritten debit retains source trident");
        assertEquals(0, recoverableProjectileUnits(), "Rewrite cannot charge projectile from retained source");
    }

    @Test void sourceMovedDuringLaunchMustNotAlsoBecomeProjectile() {
        listen(event -> { if (event instanceof ProjectileLaunchEvent) {
            Item moved = inventory.getItemInHand();
            inventory.setItemForce(0, Item.AIR_ITEM.clone());
            inventory.setItemForce(1, moved);
        }});
        release();
        assertEquals(1, inventoryTridents(), "Plugin moved physical source to another slot");
        assertEquals(0, recoverableProjectileUnits(), "Stale release snapshot is no debit proof");
    }

    @Test void switchingHandDuringLaunchCannotEraseOtherHeldItem() {
        inventory.setItemForce(1, Item.get(Item.DIAMOND));
        listen(event -> { if (event instanceof ProjectileLaunchEvent) inventory.setHeldItemIndex(1, false); });
        release();
        assertEquals(Item.DIAMOND, inventory.getItem(1).getId(), "Release cannot overwrite newly selected hand");
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
    }

    @Test void malformedStackStillThrowsOnlyOneRecoverableItem() {
        // Model a persisted legacy stack directly: current setters split stacks at ingress.
        inventory.slots.put(0, new ItemTrident(0, 2));
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(1, recoverableProjectileUnits(), "One thrown entity must hold one consumed trident");
    }

    @Test void creativeThrowKeepsSourceAndPublishesNoSurvivalPickup() {
        when(player.isCreative()).thenReturn(true);
        when(player.getGamemode()).thenReturn(Player.CREATIVE);
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
        assertEquals(1, created.stream().filter(p -> !p.isClosed()).count());
        assertEquals(EntityThrownTrident.PICKUP_CREATIVE, created.get(0).getPickupMode());
    }

    @Test void launchInAnotherWorldCannotConsumeOldHand() {
        Level nextWorld = mock(Level.class);
        listen(event -> { if (event instanceof ProjectileLaunchEvent) when(player.getLevel()).thenReturn(nextWorld); });
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
    }

    @Test void closedSessionDuringLaunchCannotConsumeOldHand() {
        listen(event -> { if (event instanceof ProjectileLaunchEvent) player.closed = true; });
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
    }

    @Test void debitListenerExceptionKeepsSourceWithoutRecoveryCopy() {
        listen(event -> { if (event instanceof EntityInventoryChangeEvent change
                && change.getOldItem().getId() == Item.TRIDENT && change.getNewItem().isNull()) {
            throw new IllegalStateException("debit observer failed");
        }});
        assertThrows(IllegalStateException.class, this::release);
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
    }

    @Test void postDebitNotificationExceptionRetainsChargedProjectile() {
        player.spawned = true; player.protocol = ProtocolInfo.v1_26_50;
        inventory.getViewers().add(player);
        doThrow(new IllegalStateException("slot notification failed")).when(player).dataPacket(any(DataPacket.class));
        assertThrows(IllegalStateException.class, this::release);
        assertEquals(0, inventoryTridents(), "Native debit committed before failed notification");
        assertEquals(1, recoverableProjectileUnits(), "A committed debit retains its original charged projectile");
    }

    @Test void postDebitWorldChangeRetainsChargedProjectileInOriginalWorld() {
        Level originalWorld = level;
        Level nextWorld = mock(Level.class);
        player.spawned = true; player.protocol = ProtocolInfo.v1_26_50;
        inventory.getViewers().add(player);
        doAnswer(call -> { when(player.getLevel()).thenReturn(nextWorld); return false; })
                .when(player).dataPacket(any(DataPacket.class));
        release();
        assertEquals(0, inventoryTridents());
        assertEquals(1, recoverableProjectileUnits(), "Committed throw must survive owner world change");
        assertSame(originalWorld, created.get(0).getLevel());
    }

    @Test void staleCallerSnapshotCannotThrowCurrentHand() {
        ItemTrident stale = (ItemTrident) inventory.getItemInHand();
        Item changed = new ItemTrident().setNamedTag(new cn.nukkit.nbt.tag.CompoundTag().putString("custody", "changed"));
        inventory.setItemForce(0, changed);
        stale.onRelease(player, 20);
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
        assertTrue(created.isEmpty());
    }

    @Test void replacedProjectileCannotOutliveRejectedSourceDebit() {
        listen(event -> { if (event instanceof EntityShootBowEvent shot) {
            EntityThrownTrident replacement = new EntityThrownTrident(player.chunk,
                    Entity.getDefaultNBT(new cn.nukkit.math.Vector3(0.5, 65, 0.5)), player);
            replacement.setItem(new ItemTrident());
            shot.setProjectile(replacement);
        }});
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits(), "Rejected replacement still belongs to native cancellation cleanup");
        assertEquals(2, created.size());
    }

    @Test void replacedProjectileThenDispatcherErrorCannotLeaveARecoveryCopy() {
        listen(event -> { if (event instanceof EntityShootBowEvent shot) {
            EntityThrownTrident replacement = new EntityThrownTrident(player.chunk,
                    Entity.getDefaultNBT(new cn.nukkit.math.Vector3(0.5, 65, 0.5)), player);
            replacement.setItem(new ItemTrident());
            shot.setProjectile(replacement);
            throw new AssertionError("shot dispatcher failed after replacement");
        }});
        AssertionError failure = assertThrows(AssertionError.class, this::release);
        assertEquals("shot dispatcher failed after replacement", failure.getMessage());
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits(), "Propagating dispatcher Error still owns replacement cleanup");
        assertEquals(2, created.size());
    }

    @Test void cancelledReplacementClosesBothPreparedSources() {
        listen(event -> { if (event instanceof EntityShootBowEvent shot) {
            EntityThrownTrident replacement = new EntityThrownTrident(player.chunk,
                    Entity.getDefaultNBT(new cn.nukkit.math.Vector3(0.5, 65, 0.5)), player);
            replacement.setItem(new ItemTrident());
            shot.setProjectile(replacement);
            shot.setCancelled();
        }});
        release();
        assertEquals(1, inventoryTridents());
        assertEquals(0, recoverableProjectileUnits());
        assertEquals(2, created.size());
    }

    @Test void nestedReleaseDuringLaunchCannotPublishSecondSource() {
        AtomicBoolean entered = new AtomicBoolean();
        listen(event -> { if (event instanceof ProjectileLaunchEvent && entered.compareAndSet(false, true)) {
            ((ItemTrident) inventory.getItemInHand()).onRelease(player, 20);
        }});
        release();
        assertEquals(0, inventoryTridents());
        assertEquals(1, recoverableProjectileUnits());
        assertEquals(1, created.size());
    }

    @Test void shootAndLaunchCallbacksCannotSeeRecoverableUnchargedProjectile() {
        listen(event -> { if (event instanceof EntityShootBowEvent || event instanceof ProjectileLaunchEvent
                || event instanceof EntityInventoryChangeEvent) {
            assertEquals(0, recoverableProjectileUnits(), "Preparation must not be pickable before debit");
            assertEquals(1, inventoryTridents());
        }});
        release();
        assertEquals(0, inventoryTridents());
        assertEquals(1, recoverableProjectileUnits());
    }

    @Test void rejectedDebitPreservesExactSourceDamageCountAndNbt() {
        Item source = new ItemTrident(11).setNamedTag(new cn.nukkit.nbt.tag.CompoundTag()
                .putString("custody", "original"));
        inventory.setItemForce(0, source);
        listen(event -> { if (event instanceof EntityInventoryChangeEvent change) change.setCancelled(); });
        release();
        assertTrue(source.equalsExact(inventory.getItem(0)));
        assertEquals(0, recoverableProjectileUnits());
    }

    @Test void stackedThrowDamagesOnlyTheSinglePublishedPayload() {
        Item source = new ItemTrident(11, 2).setNamedTag(new cn.nukkit.nbt.tag.CompoundTag()
                .putString("custody", "original"));
        inventory.slots.put(0, source.clone());
        release();
        Item remainder = source.clone();
        remainder.setCount(1);
        assertTrue(remainder.equalsExact(inventory.getItem(0)), "Retained source keeps original damage and NBT");
        Item payload = remainder.clone();
        payload.setDamage(12);
        assertTrue(payload.equalsExact(created.get(0).getItem()), "Exactly one payload receives throw durability");
        assertEquals(1, recoverableProjectileUnits());
    }

    @Test void sourceDebitPrecedesTheFirstProjectileSpawnPacket() throws Exception {
        Player viewer = mock(Player.class);
        when(viewer.isOnline()).thenReturn(true);
        Field usedChunks = Player.class.getDeclaredField("usedChunks");
        usedChunks.setAccessible(true);
        usedChunks.set(viewer, new java.util.HashMap<Long, Boolean>());
        viewer.usedChunks.put(Level.chunkHash(0, 0), true);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of(0, viewer));
        AtomicBoolean published = new AtomicBoolean();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof AddEntityPacket packet
                    && packet.type == EntityThrownTrident.NETWORK_ID) {
                assertEquals(0, inventoryTridents(), "World publication must follow the native source debit");
                assertEquals(1, recoverableProjectileUnits());
                published.set(true);
            }
            return true;
        }).when(viewer).dataPacket(any(DataPacket.class));
        release();
        assertTrue(published.get(), "Native spawnToAll must emit the trident packet");
    }

    @Test void inventoryTransactionDuringThrowCannotMoveTheClaimedSource() {
        InventoryTransaction move = sourceMove();
        listen(event -> { if (event instanceof ProjectileLaunchEvent) {
            assertTrue(move.canExecute());
            assertFalse(move.execute());
        }});
        release();
        assertEquals(0, inventoryTridents());
        assertEquals(1, recoverableProjectileUnits());
    }

    @Test void throwDuringInventoryTransactionCannotEscapeItsRollback() {
        InventoryTransaction move = sourceMove();
        AtomicBoolean entered = new AtomicBoolean();
        listen(event -> { if (event instanceof EntityInventoryChangeEvent change
                && entered.compareAndSet(false, true)) {
            release();
            change.setCancelled();
        }});
        assertFalse(move.execute());
        assertTrue(entered.get());
        assertEquals(1, inventoryTridents());
        assertTrue(created.isEmpty(), "An outer inventory transfer owns the source until it completes");
    }

    private InventoryTransaction sourceMove() {
        Item source = inventory.getItemInHand();
        return new InventoryTransaction(player, List.of(
                new SlotChangeAction(inventory, 0, source, Item.AIR_ITEM.clone()),
                new SlotChangeAction(inventory, 1, Item.AIR_ITEM.clone(), source)));
    }

    private void listen(Consumer<Event> handler) {
        doAnswer(call -> { handler.accept(call.getArgument(0)); return null; })
                .when(plugins).callEvent(any(Event.class));
    }
    private void release() { ((ItemTrident) inventory.getItemInHand()).onRelease(player, 20); }
    private int inventoryTridents() {
        return inventory.getContents().values().stream().filter(i -> i.getId() == Item.TRIDENT)
                .mapToInt(Item::getCount).sum();
    }
    private int recoverableProjectileUnits() {
        return created.stream().filter(p -> !p.isClosed())
                .filter(p -> p.getPickupMode() == EntityThrownTrident.PICKUP_ANY)
                .mapToInt(p -> p.getItem().getCount()).sum();
    }
}
