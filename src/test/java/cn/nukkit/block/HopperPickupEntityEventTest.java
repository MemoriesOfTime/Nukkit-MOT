package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HopperPickupEntityEventTest {

    @Test
    void aPickupCarriesTheEntityItTakes() {
        PluginManager plugins = Server.getInstance().getPluginManager();
        clearInvocations(plugins);
        Inventory inventory = mock(Inventory.class);
        when(inventory.isFull()).thenReturn(false);
        when(inventory.canAddItem(any())).thenReturn(true);
        when(inventory.addItem(any())).thenReturn(new Item[0]);
        EntityItem drop = mock(EntityItem.class);
        when(drop.getItem()).thenReturn(Item.get(Item.DIAMOND, 0, 3));
        Level level = mock(Level.class);
        AxisAlignedBB area = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);
        when(level.getCollidingEntities(area)).thenReturn(new Entity[]{drop});
        Position at = new Position(0, 64, 0, level);

        boolean picked = hopper(inventory, at).pickupItems(area);

        assertTrue(picked);
        ArgumentCaptor<Event> fired = ArgumentCaptor.forClass(Event.class);
        verify(plugins, atLeastOnce()).callEvent(fired.capture());
        InventoryMoveItemEvent move = fired.getAllValues().stream()
                .filter(InventoryMoveItemEvent.class::isInstance)
                .map(InventoryMoveItemEvent.class::cast)
                .findFirst().orElseThrow();
        assertEquals(InventoryMoveItemEvent.Action.PICKUP, move.getAction());
        assertSame(drop, move.getPickedEntity());
        verify(drop).close();
    }

    @Test
    void otherMovesKeepANullEntity() {
        InventoryMoveItemEvent move = new InventoryMoveItemEvent(
                null, mock(Inventory.class), null, Item.get(Item.DIAMOND), InventoryMoveItemEvent.Action.SLOT_CHANGE);
        assertNull(move.getPickedEntity());
    }

    private static BlockHopper.IHopper hopper(Inventory inventory, Position at) {
        return new BlockHopper.IHopper() {
            @Override
            public Position getPosition() {
                return at;
            }

            @Override
            public boolean isOnTransferCooldown() {
                return false;
            }

            @Override
            public void setTransferCooldown(int transferCooldown) {
            }

            @Override
            public boolean pushItems() {
                return false;
            }

            @Override
            public Inventory getInventory() {
                return inventory;
            }
        };
    }
}
