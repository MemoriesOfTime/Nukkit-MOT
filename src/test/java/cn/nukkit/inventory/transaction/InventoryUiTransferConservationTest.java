package cn.nukkit.inventory.transaction;
import cn.nukkit.*;
import cn.nukkit.entity.*;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SuppressWarnings("try")
class InventoryUiTransferConservationTest {
 @BeforeAll static void init(){MockServer.init();}
 @AfterEach void reset(){org.mockito.Mockito.reset(Server.getInstance().getPluginManager());}
 @Test void rejectedDestinationRestoresCursorSourceBacking() throws Exception {
  Fixture f=new Fixture(); f.ui.setItemForce(0,stack(4)); var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{EntityInventoryChangeEvent e=c.getArgument(0);if(e.getSlot()==1)e.setCancelled();return null;})
     .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  var tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.cursor,0,stack(4),Item.AIR_ITEM.clone()),
       new SlotChangeAction(f.inventory,1,Item.AIR_ITEM.clone(),stack(4))));
  assertTrue(tx.canExecute()); assertFalse(tx.execute());
  assertEquals(4,f.cursor.getItem(0).getCount(),"rollback must restore the real UI backing, not wrapper.slots");
 }
 @Test void rewrittenCursorDestinationCannotOverflowBeforeRollback() throws Exception {
  Fixture f=new Fixture(); f.inventory.slots.put(0,stack(4)); var rewritten=new java.util.concurrent.atomic.AtomicBoolean(); var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{EntityInventoryChangeEvent e=c.getArgument(0);if(e.getNewItem().getId()==Item.EMERALD && rewritten.compareAndSet(false,true))e.setNewItem(stack(200));return null;})
     .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  var tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.inventory,0,stack(4),Item.AIR_ITEM.clone()),
       new SlotChangeAction(f.cursor,0,Item.AIR_ITEM.clone(),stack(4))));
  assertTrue(tx.canExecute()); assertFalse(tx.execute());
  assertEquals(4,f.inventory.getItem(0).getCount()+f.cursor.getItem(0).getCount()+f.dropped.get(),"source+cursor+world must stay four");
 }
 @Test void callbackLoweringEffectiveLimitCannotDropBeforeRollback() throws Exception {
  Fixture f=new Fixture(); f.inventory.slots.put(0,stack(4)); var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{EntityInventoryChangeEvent e=c.getArgument(0);if(e.getSlot()==1)f.inventory.setMaxStackSize(1);return null;})
     .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  var tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.inventory,0,stack(4),Item.AIR_ITEM.clone()),
       new SlotChangeAction(f.inventory,1,Item.AIR_ITEM.clone(),stack(4))));
  assertTrue(tx.canExecute()); assertFalse(tx.execute());
  assertEquals(4,f.inventory.getItem(0).getCount()+f.inventory.getItem(1).getCount()+f.dropped.get(),"new effective cap must reject before splitting overflow");
 }
 @Test void successfulCursorTransferCommitsTheRealBacking() throws Exception {
  Fixture f=new Fixture(); f.ui.setItemForce(0,stack(4));
  var tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.cursor,0,stack(4),Item.AIR_ITEM.clone()),
       new SlotChangeAction(f.inventory,1,Item.AIR_ITEM.clone(),stack(4))));
  assertTrue(tx.execute());assertTrue(f.cursor.getItem(0).isNull());assertEquals(4,f.inventory.getItem(1).getCount());
 }
 @Test void rejectedDestinationRestoresCraftingOffsetBacking() throws Exception {
  Fixture f=new Fixture();var grid=f.ui.getCraftingGrid();grid.setItemForce(2,stack(4));var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{EntityInventoryChangeEvent e=c.getArgument(0);if(e.getSlot()==1)e.setCancelled();return null;})
     .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  var tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(grid,2,stack(4),Item.AIR_ITEM.clone()),
       new SlotChangeAction(f.inventory,1,Item.AIR_ITEM.clone(),stack(4))));
  assertTrue(tx.canExecute());assertFalse(tx.execute());assertEquals(4,grid.getItem(2).getCount());
 }
 @Test void differentViewOfClaimedUiBackingCannotCommitNestedTransfer() throws Exception {
  Fixture f=new Fixture();f.ui.setItemForce(0,stack(4));var grid=f.ui.getCraftingGrid();grid.setItemForce(0,stack(1));
  var inner=new InventoryTransaction(f.player,List.of(new SlotChangeAction(grid,0,stack(1),Item.AIR_ITEM.clone()),
       new SlotChangeAction(grid,1,Item.AIR_ITEM.clone(),stack(1))));
  var outer=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.cursor,0,stack(4),Item.AIR_ITEM.clone()),
       new SlotChangeAction(f.inventory,1,Item.AIR_ITEM.clone(),stack(4))));
  var plugins=Server.getInstance().getPluginManager();var nested=new java.util.concurrent.atomic.AtomicBoolean(true);
  doAnswer(c->{nested.set(inner.execute());return null;}).when(plugins).callEvent(any(cn.nukkit.event.inventory.InventoryTransactionEvent.class));
  assertTrue(outer.execute());assertFalse(nested.get());assertEquals(1,grid.getItem(0).getCount());assertTrue(grid.getItem(1).isNull());
 }
 @Test void strictNativeGrantScopeRejectsCallbackCursorAliasWrite() throws Exception {
  Fixture f=new Fixture();var plugins=Server.getInstance().getPluginManager();var changed=new java.util.concurrent.atomic.AtomicBoolean(true);
  doAnswer(c->{EntityInventoryChangeEvent e=c.getArgument(0);if(e.getSlot()==1)changed.set(f.cursor.setItem(0,stack(200),false));return null;})
     .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  try(var lease=InventoryMutationGuard.tryClaim(List.of(f.inventory,f.cursor))){
   assertNotNull(lease);
   try(var write=lease.expectWrite(f.inventory,1,stack(4))){assertTrue(f.inventory.setItem(1,stack(4),false));}
  }
  assertFalse(changed.get());assertTrue(f.cursor.getItem(0).isNull());assertEquals(0,f.dropped.get());
 }
 static Item stack(int n){return Item.get(Item.EMERALD,0,n);}
 static class Fixture {
  final Player player=mock(Player.class,CALLS_REAL_METHODS);
  final PlayerInventory inventory=new PlayerInventory(player);
  final PlayerUIInventory ui=new PlayerUIInventory(player);
  final PlayerCursorInventory cursor=ui.getCursorInventory();
  final AtomicInteger dropped=new AtomicInteger();
  Fixture()throws Exception {
   Field srv=Entity.class.getDeclaredField("server");srv.setAccessible(true);srv.set(player,Server.getInstance());
   Field inv=EntityHumanType.class.getDeclaredField("inventory");inv.setAccessible(true);inv.set(player,inventory);
   player.spawned=true;doReturn(true).when(player).isAlive();doReturn(true).when(player).isOnline();
   doReturn(false).when(player).isSpectator();doReturn(false).when(player).isCreative();
   doReturn(true).when(player).dataPacket(any(DataPacket.class));doReturn(0).when(player).getWindowId(any());
   doReturn(Collections.emptyMap()).when(player).getViewers();Level level=mock(Level.class);player.level=level;
   doAnswer(c->{dropped.addAndGet(((Item)c.getArgument(1)).getCount());return null;}).when(level).dropItem(any(Vector3.class),any(Item.class));
  }
 }
}
