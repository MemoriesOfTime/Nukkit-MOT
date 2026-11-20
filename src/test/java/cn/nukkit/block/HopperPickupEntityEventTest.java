package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HopperPickupEntityEventTest {

    @org.junit.jupiter.api.BeforeAll
    static void initializeServer() { cn.nukkit.MockServer.init(); }

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
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.hasPickupEntities(true)).thenReturn(true);
        when(chunk.getEntities()).thenReturn(java.util.Map.of(1L, drop));
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        when(drop.getBoundingBox()).thenReturn(area);
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
    void cancelledPickupLeavesTheDropUntouched() {
        PluginManager plugins = Server.getInstance().getPluginManager();
        Inventory inventory = mock(Inventory.class);
        when(inventory.canAddItem(any())).thenReturn(true);
        EntityItem drop = mock(EntityItem.class);
        when(drop.getItem()).thenReturn(Item.get(Item.DIAMOND));
        Level level = mock(Level.class);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        AxisAlignedBB area = new SimpleAxisAlignedBB(0, 64, 0, 1, 65, 1);
        when(chunk.hasPickupEntities(true)).thenReturn(true);
        when(chunk.getEntities()).thenReturn(java.util.Map.of(1L, drop));
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        when(drop.getBoundingBox()).thenReturn(area);
        doAnswer(call -> {
            ((InventoryMoveItemEvent) call.getArgument(0)).setCancelled();
            return null;
        }).when(plugins).callEvent(any(InventoryMoveItemEvent.class));
        try {
            assertFalse(hopper(inventory, new Position(0, 64, 0, level)).pickupItems(area));
            verify(drop, never()).close();
            verify(inventory, never()).addItem(any(Item[].class));
        } finally { reset(plugins); }
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
