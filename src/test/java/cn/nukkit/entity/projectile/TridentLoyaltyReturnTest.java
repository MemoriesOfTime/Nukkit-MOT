package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.MockServer;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.block.Block;
import cn.nukkit.item.ItemTrident;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.DataPacket;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.inventory.InventoryPickupTridentEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Ported from EaseCation/Nukkit 4ae1f9b2 (GPL-3.0); native MOT pickup remains under test. */
class TridentLoyaltyReturnTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetServer() { MockServer.reset(); }

    @Test
    void followsVanillaAccelerationAndDoesNotTeleportAtTwentyTicks() {
        TestTrident trident = returningTrident(2, 100);
        trident.onUpdate(1);
        assertEquals(99.9, trident.x, 1e-9);
        assertEquals(-0.099, trident.motionX, 1e-9);
        trident.onUpdate(2);
        assertEquals(99.70595, trident.x, 1e-9);
        assertEquals(-0.1921095, trident.motionX, 1e-9);
        for (int tick = 3; tick <= 20; tick++) {
            trident.onUpdate(tick);
        }
        assertFalse(trident.closed);
        assertTrue(trident.x > 70);
        verify(trident.shootingEntity, never()).attack(any());
        assertFalse(trident.canCollideWith(mock(Entity.class)));
    }

    @Test
    void higherLoyaltyAndShorterDistanceReturnSooner() {
        assertTrue(ticksUntilReturned(3, 30) < ticksUntilReturned(2, 30));
        assertTrue(ticksUntilReturned(2, 30) < ticksUntilReturned(1, 30));
        assertTrue(ticksUntilReturned(2, 10) < ticksUntilReturned(2, 50));
    }

    @Test
    void checksSweptPickupAndReturnsOnlyOnce() {
        TestTrident trident = returningTrident(2, 2);
        trident.motionX = -6;
        trident.onUpdate(1);
        assertTrue(trident.closed);
        assertTrue(trident.x < -0.8);
        trident.onUpdate(2);
        verify(((Player) trident.shootingEntity).getInventory(), times(1)).setItem(eq(0), any(Item.class));
    }

    @Test
    void picksUpAGlancingReturnAtTheOwnersCurrentPosition() {
        TestTrident trident = returningTrident(2, 2);
        trident.z = 2.9;
        trident.motionX = -6;
        trident.shootingEntity.z = 4;
        trident.onUpdate(1);
        assertTrue(trident.closed);
        verify(((Player) trident.shootingEntity).getInventory(), times(1)).setItem(eq(0), any(Item.class));
    }

    @Test
    void doesNotPickUpOutsideTheExpandedRange() {
        TestTrident trident = returningTrident(2, 2);
        trident.z = 2.4;
        trident.motionX = -6;
        trident.shootingEntity.z = 4;
        trident.onUpdate(1);
        assertFalse(trident.closed);
        verify(((Player) trident.shootingEntity).getInventory(), never()).setItem(anyInt(), any(Item.class));
    }

    @Test
    void retargetsMovingOwnerEveryTick() {
        TestTrident trident = returningTrident(2, 30);
        trident.onUpdate(1);
        assertEquals(0, trident.motionZ, 1e-9);
        trident.shootingEntity.z = 10;
        trident.onUpdate(2);
        assertTrue(trident.motionZ > 0);
        trident.shootingEntity.z = -10;
        trident.onUpdate(3);
        assertTrue(trident.motionZ < 0);
    }

    @Test
    void fullInventoryKeepsReturningUntilSpaceIsAvailable() {
        TestTrident trident = returningTrident(2, 0.1);
        PlayerInventory inventory = ((Player) trident.shootingEntity).getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, Item.get(Item.STONE, 0, 64));
        }
        clearInvocations(inventory);
        trident.onUpdate(1);
        assertFalse(trident.closed);
        verify(inventory, never()).addItem(any(Item.class));
        inventory.clear(0);
        trident.onUpdate(2);
        assertTrue(trident.closed);
    }

    @Test
    void cancelledPickupDoesNotConsumeTheProjectile() {
        TestTrident trident = returningTrident(2, 0.1);
        PluginManager plugins = trident.getServer().getPluginManager();
        doAnswer(invocation -> {
            ((InventoryPickupTridentEvent) invocation.getArgument(0)).setCancelled();
            return null;
        }).when(plugins).callEvent(any(InventoryPickupTridentEvent.class));
        trident.onUpdate(1);
        assertFalse(trident.closed);
        verify(((Player) trident.shootingEntity).getInventory(), never()).setItem(anyInt(), any(Item.class));
    }

    @Test
    void waitsFiveTicksAfterHittingABlock() {
        TestTrident trident = returningTrident(2, 30);
        trident.hitBlock();
        for (int tick = 1; tick < 5; tick++) {
            trident.onUpdate(tick);
            assertEquals(30, trident.x);
        }
        trident.onUpdate(5);
        assertTrue(trident.x < 30);
    }

    @Test
    void ownerInvalidatedByPickupEventCannotReceiveTheItem() {
        TestTrident trident = returningTrident(2, 0.1);
        PluginManager plugins = trident.getServer().getPluginManager();
        doAnswer(invocation -> {
            when(trident.shootingEntity.isAlive()).thenReturn(false);
            return null;
        }).when(plugins).callEvent(any(InventoryPickupTridentEvent.class));
        trident.onUpdate(1);
        verify(((Player) trident.shootingEntity).getInventory(), never()).setItem(anyInt(), any(Item.class));
    }

    @Test
    void returnOwnerReplacedDuringPickupEventRetainsTheWorldSource() {
        TestTrident trident = returningTrident(2, 0.1);
        Player originalOwner = (Player) trident.shootingEntity;
        Player replacement = mock(Player.class);
        PluginManager plugins = trident.getServer().getPluginManager();
        doAnswer(call -> { trident.shootingEntity = replacement; return null; })
                .when(plugins).callEvent(any(InventoryPickupTridentEvent.class));
        trident.onUpdate(1);
        assertFalse(trident.closed);
        assertTrue(originalOwner.getInventory().getItem(0).isNull());
        assertSame(replacement, trident.shootingEntity);
        verify(trident.level, never()).dropItem(any(Vector3.class), any(Item.class));
    }

    @Test
    void returnOwnerReplacedDuringSlotCallbackRollsBackTheGrant() {
        TestTrident trident = returningTrident(2, 0.1);
        Player originalOwner = (Player) trident.shootingEntity;
        Player replacement = mock(Player.class);
        PluginManager plugins = trident.getServer().getPluginManager();
        doAnswer(call -> { trident.shootingEntity = replacement; return null; })
                .when(plugins).callEvent(
                        any(cn.nukkit.event.entity.EntityInventoryChangeEvent.class));
        trident.onUpdate(1);
        assertFalse(trident.closed);
        assertTrue(originalOwner.getInventory().getItem(0).isNull());
        assertSame(replacement, trident.shootingEntity);
        verify(trident.level, never()).dropItem(any(Vector3.class), any(Item.class));
    }

    @Test
    void groundedTridentPickupDoesNotRequireItsHistoricalShooter() {
        TestTrident trident = returningTrident(2, 0.1);
        Player player = (Player) trident.shootingEntity;
        trident.noClip = false;
        trident.hadCollision = true;
        PluginManager plugins = trident.getServer().getPluginManager();
        doAnswer(call -> { trident.shootingEntity = mock(Player.class); return null; })
                .when(plugins).callEvent(any(InventoryPickupTridentEvent.class));
        assertTrue(player.pickupEntity(trident, true));
        assertTrue(trident.closed);
        assertEquals(Item.TRIDENT, player.getInventory().getItem(0).getId());
    }

    @Test
    void deadOwnerDropsOrdinaryTridentAndStopsReturning() {
        TestTrident trident = returningTrident(2, 30);
        when(trident.shootingEntity.isAlive()).thenReturn(false);
        trident.onUpdate(1);
        assertTrue(trident.closed);
        verify(trident.level).dropItem(trident, trident.trident);
    }

    @Test
    void voidReturnIgnoresVoidDamageAndDuplicateTicks() {
        TestTrident trident = returningTrident(2, 30);
        trident.y = -20;
        trident.onUpdate(1);
        assertFalse(trident.attack(new EntityDamageEvent(trident, EntityDamageEvent.DamageCause.VOID, 4)));
        Vector3 position = new Vector3(trident.x, trident.y, trident.z);
        trident.onUpdate(1);
        assertEquals(position, new Vector3(trident.x, trident.y, trident.z));
        assertFalse(trident.closed);
    }

    @Test
    void restingProjectileStaysScheduledUntilLoyaltyStarts() {
        TestTrident trident = returningTrident(2, 30);
        trident.hitBlock();
        doReturn(false).when(trident).entityBaseTick(anyInt());
        doReturn(true).when(trident).isAlive();
        doReturn(false).when(trident).isInsideOfWater();
        doReturn(false).when(trident).isInsideOfLava();
        doNothing().when(trident).updateMotion();
        doAnswer(call -> {
            Vector3 motion = call.getArgument(0);
            trident.motionX = motion.x; trident.motionY = motion.y; trident.motionZ = motion.z;
            return true;
        }).when(trident).setMotion(any(Vector3.class));
        trident.isCollided = true;
        trident.hadCollision = true;
        trident.boundingBox = new SimpleAxisAlignedBB(29.8, 2, -0.2, 30.2, 2.4, 0.2);
        when(trident.level.getCollidingEntities(any(), eq(trident))).thenReturn(new Entity[0]);
        for (int tick = 1; tick < 5; tick++) {
            assertTrue(trident.onUpdate(tick), "Loyalty must remain in the level update queue");
            assertEquals(30, trident.x);
        }
        assertTrue(trident.onUpdate(5));
        assertTrue(trident.x < 30);
    }

    @Test
    void fallingIntoVoidStartsReturnEvenWhenImpactDelayHasNotExpired() {
        TestTrident trident = returningTrident(2, 30);
        trident.hitBlock();
        trident.y = -20;
        for (int tick = 1; tick <= 200 && !trident.closed; tick++) {
            assertTrue(trident.onUpdate(tick) || trident.closed);
            assertFalse(trident.attack(new EntityDamageEvent(trident, EntityDamageEvent.DamageCause.VOID, 4)));
        }
        assertTrue(trident.closed);
        verify(((Player) trident.shootingEntity).getInventory(), times(1)).setItem(eq(0), any(Item.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {EntityProjectile.PICKUP_NONE, EntityProjectile.PICKUP_CREATIVE})
    void ownerLossMustNotConvertUnrecoverableTridentsToItems(int mode) {
        TestTrident trident = returningTrident(2, 30);
        trident.setPickupMode(mode);
        when(trident.shootingEntity.isAlive()).thenReturn(false);
        trident.onUpdate(1);
        trident.onUpdate(2);
        assertTrue(trident.closed);
        verify(trident.level, never()).dropItem(any(Vector3.class), any(Item.class));
    }

    @Test
    void ownerInAnotherWorldPausesReturnWithoutCreatingAnItem() {
        TestTrident trident = returningTrident(2, 30);
        trident.shootingEntity.level = mock(Level.class);
        trident.onUpdate(1);
        assertEquals(30, trident.x);
        assertFalse(trident.closed);
        verify(trident.level, never()).dropItem(any(Vector3.class), any(Item.class));
        trident.shootingEntity.level = trident.level;
        trident.onUpdate(2);
        assertTrue(trident.x < 30);
    }

    @Test
    void rejectedPositionMustNotPickUpAlongAnUntravelledSegment() {
        TestTrident trident = returningTrident(2, 2);
        trident.motionX = -6;
        doReturn(false).when(trident).setPosition(any(Vector3.class));
        trident.onUpdate(1);
        assertFalse(trident.closed);
        assertEquals(2, trident.x);
        verify(((Player) trident.shootingEntity).getInventory(), never()).setItem(anyInt(), any(Item.class));
    }

    @Test
    void ownerWorldChangedByMovementCallbackCannotReceiveTheTrident() {
        TestTrident trident = returningTrident(2, 0.1);
        Entity owner = trident.shootingEntity;
        doAnswer(call -> { owner.level = mock(Level.class); return null; }).when(trident).updateMovement();
        trident.onUpdate(1);
        assertFalse(trident.closed);
        verify(((Player) owner).getInventory(), never()).setItem(anyInt(), any(Item.class));
        verify(trident.level, never()).dropItem(any(Vector3.class), any(Item.class));
    }

    @Test
    void deadOwnerDuringBlockWaitDropsExactlyOnceAndStopsScheduling() {
        TestTrident trident = returningTrident(2, 30);
        trident.hitBlock();
        when(trident.shootingEntity.isAlive()).thenReturn(false);
        assertFalse(trident.onUpdate(1));
        assertTrue(trident.closed);
        assertFalse(trident.onUpdate(2));
        verify(trident.level, times(1)).dropItem(eq(trident), any(Item.class));
    }

    @Test
    void offlineCreativeOwnerDuringBlockWaitCannotCreateAnItem() {
        TestTrident trident = returningTrident(2, 30);
        trident.hitBlock();
        trident.setPickupMode(EntityProjectile.PICKUP_CREATIVE);
        when(((Player) trident.shootingEntity).isOnline()).thenReturn(false);
        assertFalse(trident.onUpdate(1));
        assertTrue(trident.closed);
        verify(trident.level, never()).dropItem(any(Vector3.class), any(Item.class));
    }

    @Test
    void removingLoyaltyDuringBlockWaitStopsExtraScheduling() {
        TestTrident trident = returningTrident(2, 30);
        trident.hitBlock();
        trident.setItem(new ItemTrident());
        doReturn(false).when(trident).entityBaseTick(anyInt());
        assertFalse(trident.onUpdate(1));
        assertFalse(trident.closed);
    }

    private int ticksUntilReturned(int loyalty, double distance) {
        TestTrident trident = returningTrident(loyalty, distance);
        for (int tick = 1; tick <= 200; tick++) {
            trident.onUpdate(tick);
            if (trident.closed) {
                return tick;
            }
        }
        fail("Trident never returned");
        return -1;
    }

    private TestTrident returningTrident(int loyalty, double distance) {
        TestTrident trident = mock(TestTrident.class, CALLS_REAL_METHODS);
        Player owner = mock(Player.class, CALLS_REAL_METHODS);
        Level level = mock(Level.class);
        Server server = MockServer.get();
        when(level.getServer()).thenReturn(server);
        when(level.getVibrationManager()).thenReturn(mock(cn.nukkit.level.vibration.VibrationManager.class));
        when(level.getGameRules()).thenReturn(cn.nukkit.level.GameRules.getDefault());
        when(level.getBlock(any(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(mock(Block.class));
        // The mocked chunk is null in this constructor-free fixture.
        when(level.getBlock(isNull(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(mock(Block.class));
        owner.level = level;
        owner.spawned = true;
        doReturn(true).when(owner).isOnline();
        doReturn(true).when(owner).isAlive();
        doReturn(false).when(owner).isCreative();
        doReturn(false).when(owner).isSpectator();
        doReturn(true).when(owner).dataPacket(any(DataPacket.class));
        doReturn(2f).when(owner).getEyeHeight();
        doAnswer(invocation -> new Vector3(owner.x, owner.y + 2, owner.z)).when(owner).getEyePosition();
        doAnswer(invocation -> new SimpleAxisAlignedBB(owner.x - 0.3, owner.y, owner.z - 0.3,
            owner.x + 0.3, owner.y + 2, owner.z + 0.3)).when(owner).getBoundingBox();
        PlayerInventory inventory = spy(new PlayerInventory(owner));
        try {
            Field serverField = Entity.class.getDeclaredField("server");
            serverField.setAccessible(true); serverField.set(owner, server);
            Field inventoryField = EntityHumanType.class.getDeclaredField("inventory");
            inventoryField.setAccessible(true); inventoryField.set(owner, inventory);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        Item item = new ItemTrident();
        item.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_TRIDENT_LOYALTY).setLevel(loyalty));
        trident.configure(server, level, owner, item, distance);
        doReturn(true).when(trident).entityBaseTick(anyInt());
        doNothing().when(trident).setDataFlag(anyInt(), anyInt(), anyBoolean());
        doReturn(Map.of()).when(trident).getViewers();
        doReturn(true).when(trident).isAlive();
        doReturn(false).when(trident).isInsideOfWater();
        doReturn(false).when(trident).isInsideOfLava();
        doNothing().when(trident).updateMotion();
        doAnswer(call -> {
            Vector3 motion = call.getArgument(0);
            trident.motionX = motion.x; trident.motionY = motion.y; trident.motionZ = motion.z;
            return true;
        }).when(trident).setMotion(any(Vector3.class));
        doReturn(false).when(trident).hasEffect(anyInt());
        doReturn(1f).when(trident).getHealth();
        doNothing().when(trident).setHealth(anyFloat());
        doReturn(false).when(trident).isCritical();
        doNothing().when(trident).setTridentRope(anyBoolean());
        doNothing().when(trident).updateMovement();
        doNothing().when(trident).updateRotation();
        doReturn(true).when(trident).move(anyDouble(), anyDouble(), anyDouble());
        doAnswer(invocation -> {
            Vector3 position = invocation.getArgument(0);
            trident.x = position.x;
            trident.y = position.y;
            trident.z = position.z;
            return true;
        }).when(trident).setPosition(any(Vector3.class));
        doAnswer(invocation -> {
            trident.closed = true;
            return null;
        }).when(trident).close();
        trident.finishEntityHit();
        return trident;
    }

    /** Fixture only: native impact, return, and guarded Player pickup execute unchanged. */
    static class TestTrident extends EntityThrownTrident {
        TestTrident(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }

        void finishEntityHit() { onCollideWithEntity(mock(Entity.class)); }
        void hitBlock() {
            noClip = false;
            isCollided = true;
            hadCollision = true;
            onHitGround(new Vector3(x, y, z));
        }
        void configure(Server server, Level level, Player owner, Item item, double distance) {
            this.server = server;
            this.level = level;
            this.chunk = mock(FullChunk.class);
            long entityId = getId();
            Map<Long, Entity> entities = Map.of(entityId, this);
            when(level.getEntity(entityId)).thenReturn(this);
            when(chunk.getEntities()).thenReturn(entities);
            shootingEntity = owner;
            trident = item;
            pickupMode = PICKUP_ANY;
            setFavoredSlot(0);
            setPlayer(true);
            setItem(item);
            namedTag = new CompoundTag();
            setCollisionPos(new Vector3());
            setStuckToBlockPos(new BlockVector3());
            boundingBox = new SimpleAxisAlignedBB(distance - 0.1, 2, -0.1, distance + 0.1, 2.1, 0.1);
            x = distance;
            y = 2;
        }
    }
}
