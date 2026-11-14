package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.inventory.InventoryPickupTridentEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NativeTridentPickupAcceptanceTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }
    @Test void favoredSetterTrueCannotAcceptATargetChangedByItsNonThrowingNativeObserver() throws Exception {
        Fixture f = new Fixture(Kind.TRIDENT,true);
        doAnswer(call -> { call.callRealMethod(); f.inventory.getItemFast(0).setCount(2); return null; })
                .when(f.inventory).onSlotChange(eq(0),any(Item.class),eq(true));
        assertFalse(f.player.pickupEntity(f.entity,true));
        assertFalse(f.entity.isClosed()); assertTrue(f.inventory.getContents().isEmpty()); assertEquals(1,f.total());
    }
    @Test void rawAliasGetterChangedByObserverCannotChangeTheFrozenAcceptedOneTrident() throws Exception {
        Fixture f = new Fixture(Kind.TRIDENT,true,true);
        Item rawAlias = ((EntityThrownTrident)f.entity).getItem();
        assertSame(rawAlias,((EntityThrownTrident)f.entity).getItem(),"Lawful public getter override deliberately exposes one retained item alias");
        doAnswer(call -> { call.callRealMethod(); rawAlias.setCount(2); return null; })
                .when(f.inventory).onSlotChange(eq(0),any(Item.class),eq(true));
        assertTrue(f.player.pickupEntity(f.entity,true));
        assertTrue(f.entity.isClosed()); assertEquals(1,f.inventory.getItem(0).getCount()); assertEquals(1,f.total());
    }
    /** Existing public EntityThrownTrident subclass extension; only getter ownership differs from stock clone. */
    private static final class RawAliasTrident extends EntityThrownTrident {
        private Item alias;
        RawAliasTrident(cn.nukkit.level.format.FullChunk chunk,cn.nukkit.nbt.tag.CompoundTag tag) {
            super(chunk,tag); this.alias=super.getItem();
        }
        @Override public Item getItem() { return alias==null?super.getItem():alias; }
    }
    private enum Kind { ITEM, ARROW, TRIDENT }
    private static final class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory;
        final Entity entity;

        Fixture(Kind kind) throws Exception {
            this(kind, false);
        }

        Fixture(Kind kind, boolean spyInventory) throws Exception { this(kind,spyInventory,false); }
        Fixture(Kind kind, boolean spyInventory, boolean rawAlias) throws Exception {
            inventory = spyInventory ? spy(new PlayerInventory(player)) : new PlayerInventory(player);
            set(Entity.class, player, "server", Server.getInstance());
            set(EntityHumanType.class, player, "inventory", inventory);
            player.spawned = true;
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(false).when(player).isCreative();
            doReturn(false).when(player).isSpectator();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            cn.nukkit.level.Level level = mock(cn.nukkit.level.Level.class);
            level.isBeingConverted = true;
            when(level.getServer()).thenReturn(Server.getInstance());
            when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Collections.emptyMap());
            when(level.getGameRules()).thenReturn(cn.nukkit.level.GameRules.getDefault());
            cn.nukkit.level.format.LevelProvider provider = mock(cn.nukkit.level.format.LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            cn.nukkit.level.format.generic.BaseFullChunk chunk = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(level.getChunk(anyInt(),anyInt())).thenReturn(chunk);
            player.level = level;
            var nbt = Entity.getDefaultNBT(new cn.nukkit.math.Vector3(1,64,1));
            if (kind == Kind.ITEM) {
                entity = new EntityItem(chunk, nbt.putShort("Health",5).putShort("PickupDelay",0)
                        .putCompound("Item",cn.nukkit.nbt.NBTIO.putItemHelper(Item.get(Item.EMERALD,0,1))));
            } else if (kind == Kind.ARROW) {
                var arrow = new EntityArrow(chunk,nbt);
                arrow.hadCollision = true;
                arrow.setPickupMode(EntityProjectile.PICKUP_ANY);
                entity = arrow;
            } else {
                var tridentNBT = nbt.putBoolean("player",true)
                        .putInt("favoredSlot",0).putByte("pickup",EntityProjectile.PICKUP_ANY)
                        .putCompound("Trident",cn.nukkit.nbt.NBTIO.putItemHelper(Item.get(Item.TRIDENT,0,1)));
                EntityThrownTrident trident = rawAlias ? new RawAliasTrident(chunk,tridentNBT)
                        : new EntityThrownTrident(chunk,tridentNBT);
                trident.hadCollision = true;
                entity = trident;
            }
            when(level.getEntity(entity.getId())).thenReturn(entity);
            assertFalse(entity.isClosed(), "Actual public native entity constructor publishes a live source");
        }

        int total() {
            int source = entity instanceof EntityItem item ? item.getItem().getCount() : 1;
            return inventory.getContents().values().stream().mapToInt(Item::getCount).sum() + (entity.isClosed() ? 0 : source);
        }
        private static void set(Class<?> owner, Object object, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, value);
        }
    }
}
