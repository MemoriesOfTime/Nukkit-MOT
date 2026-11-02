package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionAllocationTest {
    @BeforeAll static void init() { MockServer.init(); }
    static final class CountingItem extends Item {
        static int clones;
        CountingItem(int id) { super(id, 0, 1); }
        @Override public Item clone() { clones++; return super.clone(); }
    }
    @Test void differentSlotsDoNotCloneExistingItems() {
        Player player=mock(Player.class);
        when(player.isCreative()).thenReturn(true);
        PlayerInventory inventory=new PlayerInventory(player);
        var a=new SlotChangeAction(inventory,0,new CountingItem(Item.STONE),new CountingItem(Item.DIRT));
        var b=new SlotChangeAction(inventory,1,new CountingItem(Item.STONE),new CountingItem(Item.DIRT));
        CountingItem.clones=0;
        var transaction=new InventoryTransaction(player,List.of(a,b));
        assertEquals(2,transaction.getActionList().size());
        assertEquals(4,CountingItem.clones,"Only the incoming source/target snapshots are needed");
    }
    @Test void sameSlotStillChainsWithoutChangingInputItems() {
        Player player=mock(Player.class);when(player.isCreative()).thenReturn(true);
        PlayerInventory inventory=new PlayerInventory(player);
        Item stone=Item.get(Item.STONE),dirt=Item.get(Item.DIRT),sand=Item.get(Item.SAND);
        var transaction=new InventoryTransaction(player,List.of(
                new SlotChangeAction(inventory,0,stone,dirt),new SlotChangeAction(inventory,0,dirt,sand)));
        assertEquals(1,transaction.getActionList().size());
        assertTrue(transaction.getActionList().get(0).getSourceItem().equalsExact(stone));
        assertTrue(transaction.getActionList().get(0).getTargetItem().equalsExact(sand));
        assertEquals(1,stone.getCount());assertEquals(1,dirt.getCount());assertEquals(1,sand.getCount());
    }
    @Test void trimPatternIsSharedButProtectedFieldKeepsInstanceAbi() throws Exception {
        var field=InventoryTransaction.class.getDeclaredField("TRIM_PATTERN");field.setAccessible(true);
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isProtected(field.getModifiers()));
        var a=new InventoryTransaction(null,List.of());var b=new InventoryTransaction(null,List.of());
        assertSame(field.get(a),field.get(b));
        var pattern=(java.util.regex.Pattern)field.get(a);
        assertTrue(pattern.matcher("minecraft:coast_smithing_template").matches());
        assertFalse(pattern.matcher("minecraft:stone").matches());
    }
}
