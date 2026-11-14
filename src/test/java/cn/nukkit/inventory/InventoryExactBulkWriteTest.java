package cn.nukkit.inventory;

import cn.nukkit.*;
import cn.nukkit.entity.*;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.*;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SuppressWarnings("try")
class InventoryExactBulkWriteTest {
 @BeforeAll static void init(){MockServer.init();}
 @AfterEach void clearEvents(){reset(Server.getInstance().getPluginManager());}
 @Test void rewrittenOverstackIsRejectedBeforeWorldOverflow() throws Exception {
  Fixture f=new Fixture(false);var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{((EntityInventoryChangeEvent)c.getArgument(0)).setNewItem(stack(200));return null;})
   .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(4,total(f.inventory.addItem(stack(4))));assertEquals(0,total(f.inventory));assertEquals(0,f.dropped.get());
 }
 @Test void rewrittenVariantDoesNotConsumeTheRequestedUnits() throws Exception {
  Fixture f=new Fixture(false);var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{((EntityInventoryChangeEvent)c.getArgument(0)).setNewItem(Item.get(Item.DIAMOND,0,4));return null;})
   .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(4,total(f.inventory.addItem(stack(4))));assertEquals(0,total(f.inventory));
 }
 @Test void trueSetterWithoutTheTargetDoesNotClaimAcceptance() throws Exception {
  Fixture f=new Fixture(true);doReturn(true).when(f.inventory).setItem(anyInt(),any(Item.class),anyBoolean());
  assertEquals(4,total(f.inventory.addItem(stack(4))));assertEquals(0,total(f.inventory));
 }
 @Test void addExceptionRestoresOnlyItsSlotAndKeepsEarlierAcceptedUnits() throws Exception {
  Fixture f=new Fixture(true);
  doThrow(new IllegalStateException("later notification")).when(f.inventory).onSlotChange(eq(1),any(Item.class),anyBoolean());
  assertThrows(IllegalStateException.class,()->f.inventory.addItem(stack(100)));
  assertEquals(64,total(f.inventory));assertTrue(f.inventory.getItem(1).isNull());assertEquals(0,f.dropped.get());
  doCallRealMethod().when(f.inventory).onSlotChange(eq(1),any(Item.class),anyBoolean());
  assertEquals(0,f.inventory.addItem(stack(36)).length);assertEquals(100,total(f.inventory));
 }
 @Test void removeExceptionRestoresOnlyItsSlotAndKeepsEarlierDebit() throws Exception {
  Fixture f=new Fixture(true);f.inventory.setItemForce(0,stack(64));f.inventory.setItemForce(1,stack(36));
  doThrow(new IllegalStateException("later notification")).when(f.inventory).onSlotChange(eq(1),any(Item.class),anyBoolean());
  assertThrows(IllegalStateException.class,()->f.inventory.removeItem(stack(100)));
  assertTrue(f.inventory.getItem(0).isNull());assertEquals(36,total(f.inventory));
  doCallRealMethod().when(f.inventory).onSlotChange(eq(1),any(Item.class),anyBoolean());
  assertEquals(0,f.inventory.removeItem(stack(36)).length);assertEquals(0,total(f.inventory));
 }
 @Test void refusedMergeCanAcceptTheExactRemainderInAnotherSlot() throws Exception {
  Fixture f=new Fixture(false);f.inventory.setItemForce(0,stack(8));var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{EntityInventoryChangeEvent e=c.getArgument(0);if(e.getSlot()==0)e.setNewItem(stack(9));return null;})
   .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,f.inventory.addItem(stack(4)).length);assertEquals(8,f.inventory.getItem(0).getCount());assertEquals(4,f.inventory.getItem(1).getCount());
 }
 @Test void cancelledRemoveReportsOnlyItsUnacceptedRemainder() throws Exception {
  Fixture f=new Fixture(false);f.inventory.setItemForce(0,stack(8));f.inventory.setItemForce(1,stack(8));var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{EntityInventoryChangeEvent e=c.getArgument(0);if(e.getSlot()==1)e.setCancelled();return null;})
   .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(4,total(f.inventory.removeItem(stack(12))));assertEquals(8,total(f.inventory));
 }
 @Test void bulkWriteCanRunInsideTheOuterPhysicalPickupClaim() throws Exception {
  Fixture f=new Fixture(false);
  try(var lease=InventoryMutationGuard.tryClaim(List.of(f.inventory))){assertNotNull(lease);assertEquals(0,f.inventory.addItem(stack(4)).length);}
  assertEquals(4,total(f.inventory));
 }
 @Test void nestedBulkWriteRefusesBeforeCallingItsEvents() throws Exception {
  Fixture f=new Fixture(false);var plugins=Server.getInstance().getPluginManager();AtomicInteger events=new AtomicInteger();
  AtomicInteger nestedRemaining=new AtomicInteger();AtomicBoolean entered=new AtomicBoolean();
  doAnswer(c->{events.incrementAndGet();if(entered.compareAndSet(false,true))nestedRemaining.set(total(f.inventory.addItem(stack(1))));return null;})
   .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,f.inventory.addItem(stack(4)).length);assertEquals(1,events.get());assertEquals(1,nestedRemaining.get());assertEquals(4,total(f.inventory));
 }
 @Test void nestedTransactionCannotConsumeAClaimedBulkSlot() throws Exception {
  Fixture f=new Fixture(false);f.inventory.setItemForce(0,stack(8));var plugins=Server.getInstance().getPluginManager();
  AtomicBoolean accepted=new AtomicBoolean(true),entered=new AtomicBoolean();
  doAnswer(c->{if(!entered.compareAndSet(false,true))return null;var tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.inventory,0,stack(8),Item.AIR_ITEM.clone()),
    new SlotChangeAction(f.inventory,1,Item.AIR_ITEM.clone(),stack(8))));accepted.set(tx.execute());return null;})
   .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,f.inventory.addItem(stack(4)).length);assertFalse(accepted.get());assertEquals(12,total(f.inventory));assertTrue(f.inventory.getItem(1).isNull());
 }
 @Test void independentInventoryStillAcceptsANestedBulkGrant() throws Exception {
  Fixture first=new Fixture(false),second=new Fixture(false);var plugins=Server.getInstance().getPluginManager();AtomicBoolean entered=new AtomicBoolean();
  doAnswer(c->{if(entered.compareAndSet(false,true))assertEquals(0,second.inventory.addItem(stack(1)).length);return null;})
   .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,first.inventory.addItem(stack(4)).length);assertEquals(4,total(first.inventory));assertEquals(1,total(second.inventory));
 }
 @Test void cursorBulkWriteUsesItsPhysicalBacking() throws Exception {
  Fixture f=new Fixture(false);PlayerUIInventory ui=new PlayerUIInventory(f.player);var cursor=ui.getCursorInventory();
  var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{((EntityInventoryChangeEvent)c.getArgument(0)).setNewItem(stack(2));return null;}).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(4,total(cursor.addItem(stack(4))));assertTrue(cursor.getItem(0).isNull());
  reset(plugins);assertEquals(0,cursor.addItem(stack(4)).length);assertEquals(4,ui.getItem(0).getCount());
  assertEquals(0,cursor.removeItem(stack(4)).length);assertTrue(ui.getItem(0).isNull());
 }
 @Test void siblingUiViewCannotNestIntoTheSameBacking() throws Exception {
  Fixture f=new Fixture(false);PlayerUIInventory ui=new PlayerUIInventory(f.player);var cursor=ui.getCursorInventory();var grid=ui.getCraftingGrid();
  var plugins=Server.getInstance().getPluginManager();AtomicInteger calls=new AtomicInteger();AtomicBoolean entered=new AtomicBoolean();
  doAnswer(c->{calls.incrementAndGet();if(entered.compareAndSet(false,true))assertEquals(1,total(grid.addItem(stack(1))));return null;}).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,cursor.addItem(stack(4)).length);assertEquals(1,calls.get());assertTrue(grid.getItem(0).isNull());
 }
 @Test void doubleChestBulkWritesPreserveNativeSides() {
  BlockEntityChest left=mock(BlockEntityChest.class),right=mock(BlockEntityChest.class);ChestInventory l=new ChestInventory(left),r=new ChestInventory(right);
  when(left.getRealInventory()).thenReturn(l);when(right.getRealInventory()).thenReturn(r);DoubleChestInventory chest=new DoubleChestInventory(left,right);
  for(int i=0;i<l.getSize();i++)l.slots.put(i,Item.get(Item.STONE,0,64));
  assertEquals(0,chest.addItem(stack(4)).length);assertEquals(4,r.getItem(0).getCount());
  assertEquals(0,chest.removeItem(stack(4)).length);assertTrue(r.getItem(0).isNull());
 }
 @Test void bundleIngressRetainsUniqueIdsAndExactContents() throws Exception {
  Fixture f=new Fixture(false);ItemBundle first=new ItemBundle();first.getInventory().addItem(stack(4));ItemBundle second=first.clone();
  assertEquals(0,f.inventory.addItem(first,second).length);
  ItemBundle acceptedFirst=(ItemBundle)f.inventory.getItem(0),acceptedSecond=(ItemBundle)f.inventory.getItem(1);
  assertNotEquals(acceptedFirst.getBundleId(),acceptedSecond.getBundleId());assertEquals(4,total(acceptedFirst.getInventory()));assertEquals(4,total(acceptedSecond.getInventory()));
 }
 @Test void callbackCapacityChangeCannotRouteBulkOverflow() throws Exception {
  Fixture f=new Fixture(false);var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{f.inventory.setMaxStackSize(1);return null;}).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,f.inventory.addItem(stack(4)).length);assertEquals(4,total(f.inventory));assertEquals(0,f.dropped.get());
  assertTrue(f.inventory.getItem(0).isNull());assertTrue(f.inventory.getContents().values().stream().allMatch(item->item.getCount()<=1));
 }
 @Test void nestedPublicPickupLeavesItsSourceUntilTheBulkCallbackCompletes() throws Exception {
  Fixture f=new Fixture(false);EntityItem source=NativePublishedPickupSourceFixture.create(f.player,stack(1));
  java.util.function.Supplier<Boolean> closed=source::isClosed;AtomicBoolean entered=new AtomicBoolean(),accepted=new AtomicBoolean(true);
  var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{if(entered.compareAndSet(false,true))accepted.set(f.player.pickupEntity(source,true));return null;}).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,f.inventory.addItem(stack(4)).length);assertFalse(accepted.get());assertFalse(closed.get());assertEquals(5,total(f.inventory)+source.getItem().getCount());
  reset(plugins);assertTrue(f.player.pickupEntity(source,true));assertTrue(closed.get());assertEquals(5,total(f.inventory));
 }
 @Test void cursorNotificationExceptionRestoresOnlyThePhysicalSlot() throws Exception {
  Fixture f=new Fixture(false);PlayerUIInventory ui=new PlayerUIInventory(f.player);var cursor=spy(ui.getCursorInventory());
  ui.setItemForce(4,stack(8));
  doThrow(new IllegalStateException("cursor observer failed")).when(cursor).onSlotChange(eq(0),any(Item.class),eq(false));
  assertThrows(IllegalStateException.class,()->cursor.addItem(stack(4)));
  assertTrue(ui.getItem(0).isNull());assertEquals(8,ui.getItem(4).getCount());assertEquals(0,f.dropped.get());
 }
 @Test void nestedTransactionThroughUiParentCannotConsumeTheBulkCursor() throws Exception {
  Fixture f=new Fixture(false);PlayerUIInventory ui=new PlayerUIInventory(f.player);var cursor=ui.getCursorInventory();cursor.setItemForce(0,stack(8));
  AtomicBoolean accepted=new AtomicBoolean(true),entered=new AtomicBoolean();var plugins=Server.getInstance().getPluginManager();
  doAnswer(c->{if(entered.compareAndSet(false,true)){var tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(ui,0,stack(8),Item.AIR_ITEM.clone()),
   new SlotChangeAction(ui,1,Item.AIR_ITEM.clone(),stack(8))));accepted.set(tx.execute());}return null;}).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
  assertEquals(0,cursor.addItem(stack(4)).length);assertFalse(accepted.get());assertEquals(12,total(ui));assertTrue(ui.getItem(1).isNull());
 }
 @Test void doubleChestSideCannotNestIntoItsBulkParent() {
  BlockEntityChest left=mock(BlockEntityChest.class),right=mock(BlockEntityChest.class);ChestInventory l=new ChestInventory(left),r=spy(new ChestInventory(right));
  when(left.getRealInventory()).thenReturn(l);when(right.getRealInventory()).thenReturn(r);DoubleChestInventory chest=new DoubleChestInventory(left,right);
  for(int i=0;i<l.getSize();i++)l.slots.put(i,Item.get(Item.STONE,0,64));
  AtomicInteger remaining=new AtomicInteger(),calls=new AtomicInteger();AtomicBoolean entered=new AtomicBoolean();
  doAnswer(c->{calls.incrementAndGet();if(entered.compareAndSet(false,true))remaining.set(total(r.addItem(stack(1))));return null;}).when(r).onSlotChange(anyInt(),any(Item.class),anyBoolean());
  assertEquals(0,chest.addItem(stack(4)).length);assertEquals(1,remaining.get());assertEquals(1,calls.get());assertEquals(4,total(r));
 }
 @Test void nonpositiveEmptySlotCapacityNeverIncreasesTheRemainder() throws Exception {
  for(int capacity:new int[]{0,-1,Integer.MIN_VALUE}) {
   Fixture f=new Fixture(false);f.inventory.setMaxStackSize(capacity);
   assertEquals(4,total(f.inventory.addItem(stack(4))));assertEquals(0,total(f.inventory));assertEquals(0,f.dropped.get());
  }
 }
 static Item stack(int n){return Item.get(Item.EMERALD,0,n);}
 static int total(Item[] items){return Arrays.stream(items).mapToInt(Item::getCount).sum();}
 static int total(Inventory inventory){return inventory.getContents().values().stream().mapToInt(Item::getCount).sum();}
 static class Fixture {
  final Player player=mock(Player.class,CALLS_REAL_METHODS);final PlayerInventory inventory;final AtomicInteger dropped=new AtomicInteger();
  Fixture(boolean spying)throws Exception{
   inventory=spying?spy(new PlayerInventory(player)):new PlayerInventory(player);
   Field srv=Entity.class.getDeclaredField("server");srv.setAccessible(true);srv.set(player,Server.getInstance());
   Field inv=EntityHumanType.class.getDeclaredField("inventory");inv.setAccessible(true);inv.set(player,inventory);
   player.spawned=true;doReturn(true).when(player).isAlive();doReturn(true).when(player).isOnline();doReturn(false).when(player).isSpectator();doReturn(false).when(player).isCreative();
   doReturn(true).when(player).dataPacket(any(DataPacket.class));doReturn(0).when(player).getWindowId(any());doReturn(Collections.emptyMap()).when(player).getViewers();
   Level level=mock(Level.class);player.level=level;doAnswer(c->{dropped.addAndGet(((Item)c.getArgument(1)).getCount());return null;}).when(level).dropItem(any(Vector3.class),any(Item.class));
  }
 }
}
