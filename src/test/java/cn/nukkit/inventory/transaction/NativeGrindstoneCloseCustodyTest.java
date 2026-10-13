package cn.nukkit.inventory.transaction;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.plugin.PluginManager;
import com.google.common.collect.HashBiMap;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** Native removeWindow/direct grinder close, stock returnUiItems, strict slot transaction and UI backing. World overflow is an explicit accepted mock sink, not BDS entity proof. */
class NativeGrindstoneCloseCustodyTest {
 @BeforeAll static void init(){MockServer.init();}
 @AfterEach void resetListeners(){reset(Server.getInstance().getPluginManager());}
 private static void field(Class<?> type,Object target,String name,Object value) throws Exception {
  Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
 }
 private static Item stack(int count){return Item.get(Item.EMERALD,0,count);}
 private static int count(Inventory inventory){return inventory.getContents().values().stream().filter(i->i.getId()==Item.EMERALD).mapToInt(Item::getCount).sum();}
 private static final class Fixture {
  final PluginManager plugins=Server.getInstance().getPluginManager();
  final Player player=mock(Player.class,CALLS_REAL_METHODS);
  final PlayerInventory inventory=new PlayerInventory(player);
  final PlayerUIInventory ui=new PlayerUIInventory(player);
  final Level level=mock(Level.class);
  final GrindstoneInventory grindstone=new GrindstoneInventory(ui,new Position(1,64,1,level));
  final AtomicInteger dropped=new AtomicInteger();
  final List<Integer> closeWindowIds=new ArrayList<>();
  final com.google.common.collect.BiMap<Inventory,Integer> windows=HashBiMap.create();
  Fixture() throws Exception {
   field(Entity.class,player,"server",Server.getInstance());field(EntityHumanType.class,player,"inventory",inventory);
   field(Player.class,player,"windows",windows);field(Player.class,player,"windowIndex",windows.inverse());field(Player.class,player,"permanentWindows",new HashSet<Integer>());
   field(Player.class,player,"closingWindowId",Integer.MIN_VALUE);
   field(Player.class,player,"playerUIInventory",ui);field(Player.class,player,"craftingGrid",ui.getCraftingGrid());windows.put(grindstone,Player.GRINDSTONE_WINDOW_ID);
   player.level=level;player.spawned=true;player.protocol=589;player.craftingType=Player.GRINDSTONE_WINDOW_ID;
   doReturn(true).when(player).isAlive();doReturn(true).when(player).isOnline();doReturn(false).when(player).isSpectator();doReturn(false).when(player).isCreative();
   doAnswer(call->{if(call.getArgument(0) instanceof cn.nukkit.network.protocol.ContainerClosePacket close)closeWindowIds.add(close.windowId);return true;}).when(player).dataPacket(any(DataPacket.class));
   doReturn(Collections.emptyMap()).when(player).getViewers();
   doReturn(false).when(player).dropItem(any(Item.class));
   doAnswer(call->{Item item=call.getArgument(1);if(item.getId()==Item.EMERALD)dropped.addAndGet(item.getCount());return null;}).when(level).dropItem(any(Vector3.class),any(Item.class));
  }
  int owned(){return count(inventory)+count(ui)+dropped.get();}
  void input(int amount){grindstone.setItemForce(0,stack(amount));}
 }
 private void transfer(boolean vetoSource,boolean throwSource,boolean reenterClose) throws Exception {
  Fixture f=new Fixture();f.input(4);AtomicBoolean requested=new AtomicBoolean(),inDestination=new AtomicBoolean(),sourceRefused=new AtomicBoolean(),reentered=new AtomicBoolean();
  AssertionError original=new AssertionError("later source callback failure");
  doAnswer(call->{Event raw=call.getArgument(0);
   if(raw instanceof InventoryCloseEvent && reenterClose && reentered.compareAndSet(false,true))f.player.removeWindow(f.grindstone);
   if(raw instanceof EntityInventoryChangeEvent event){
    if(event.getSlot()==4 && event.getNewItem().getId()==Item.EMERALD && requested.compareAndSet(false,true)){
     inDestination.set(true);try{f.player.removeWindow(f.grindstone);}finally{inDestination.set(false);}
    }
    if(!inDestination.get() && event.getSlot()==GrindstoneInventory.GRINDSTONE_EQUIPMENT_UI_SLOT && event.getNewItem().isNull() && (vetoSource||throwSource) && sourceRefused.compareAndSet(false,true)){
     if(throwSource)throw original;event.setCancelled();
    }
   }
   return null;
  }).when(f.plugins).callEvent(any(Event.class));
  InventoryTransaction transaction=new InventoryTransaction(f.player,List.of(
   new SlotChangeAction(f.inventory,4,Item.AIR_ITEM.clone(),stack(4)),
   new SlotChangeAction(f.grindstone,0,stack(4),Item.AIR_ITEM.clone())));
  assertTrue(transaction.canExecute());
  if(throwSource)assertSame(original,assertThrows(AssertionError.class,transaction::execute));else assertEquals(!vetoSource,transaction.execute());
  assertTrue(requested.get());assertFalse(f.windows.containsKey(f.grindstone));
  assertFalse(f.closeWindowIds.isEmpty());assertTrue(f.closeWindowIds.stream().allMatch(id->id==Player.GRINDSTONE_WINDOW_ID),"Close packet keeps window identity before deferred map removal");assertEquals(4,f.owned(),"destination + all UI sources + world must keep the original four");
  assertTrue(f.grindstone.getEquipment().isNull(),"Closing consumes the final accepted or rolled-back inputs once");
  if(!vetoSource&&!throwSource){assertEquals(4,f.inventory.getItem(4).getCount());assertEquals(0,f.dropped.get());}
  if(!reenterClose)verify(f.player,times(1)).resetCraftingGridType();
 }
 @Test void callbackCloseCannotDuplicateAcceptedDestination() throws Exception{transfer(false,false,false);}
 @Test void callbackCloseCannotDuplicateLaterRefusedSource() throws Exception{transfer(true,false,false);}
 @Test void callbackCloseCannotDuplicateSourceAfterErrorRollback() throws Exception{transfer(false,true,false);}
 @Test void reenteredNativeCloseStillConservesTheSource() throws Exception{transfer(false,false,true);}
 @Test void cursorClaimAlsoProtectsAliasedAnvilBacking() throws Exception {
  Fixture f=new Fixture();f.input(3);f.ui.getCursorInventory().setItemForce(0,stack(4));AtomicBoolean closed=new AtomicBoolean();
  doAnswer(call->{if(call.getArgument(0) instanceof EntityInventoryChangeEvent e && e.getSlot()==4 && closed.compareAndSet(false,true))f.player.removeWindow(f.grindstone);return null;})
   .when(f.plugins).callEvent(any(Event.class));
  InventoryTransaction tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.inventory,4,Item.AIR_ITEM.clone(),stack(4)),new SlotChangeAction(f.ui.getCursorInventory(),0,stack(4),Item.AIR_ITEM.clone())));
  assertTrue(tx.execute());assertTrue(closed.get());assertEquals(7,f.owned(),"Cursor and grindstone share UI ownership despite different wrapper identities");assertTrue(f.ui.getCursorInventory().getItem(0).isNull());assertTrue(f.grindstone.getEquipment().isNull());
 }
 @Test void unrelatedInventoryLeaseDoesNotDelayAnvilClose() throws Exception {
  Fixture f=new Fixture();f.input(3);BaseInventory source=new BaseInventory(f.player,InventoryType.CHEST){};BaseInventory destination=new BaseInventory(f.player,InventoryType.CHEST){};source.setItemForce(0,stack(4));AtomicBoolean observedClose=new AtomicBoolean();
  doAnswer(call->{if(call.getArgument(0) instanceof EntityInventoryChangeEvent e && e.getSlot()==4 && e.getNewItem().getId()==Item.EMERALD && observedClose.compareAndSet(false,true)){
   f.player.removeWindow(f.grindstone);assertTrue(f.grindstone.getEquipment().isNull(),"Unclaimed UI close runs immediately inside an unrelated native transfer");
  }return null;}).when(f.plugins).callEvent(any(Event.class));
  InventoryTransaction tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(destination,4,Item.AIR_ITEM.clone(),stack(4)),new SlotChangeAction(source,0,stack(4),Item.AIR_ITEM.clone())));
  assertTrue(tx.execute());assertTrue(observedClose.get());assertEquals(7,count(source)+count(destination)+f.owned());
 }
 private void directClose(boolean directOnClose,boolean vetoSource) throws Exception {
  Fixture f=new Fixture();f.input(4);AtomicBoolean requested=new AtomicBoolean(),inClose=new AtomicBoolean(),vetoed=new AtomicBoolean();
  doAnswer(call->{if(call.getArgument(0) instanceof EntityInventoryChangeEvent e){
   if(e.getSlot()==4&&e.getNewItem().getId()==Item.EMERALD&&requested.compareAndSet(false,true)){
    inClose.set(true);try{if(directOnClose)f.grindstone.onClose(f.player);else f.grindstone.close(f.player);}finally{inClose.set(false);}
   }
   if(vetoSource&&!inClose.get()&&e.getSlot()==GrindstoneInventory.GRINDSTONE_EQUIPMENT_UI_SLOT&&e.getNewItem().isNull()&&vetoed.compareAndSet(false,true))e.setCancelled();
  }return null;}).when(f.plugins).callEvent(any(Event.class));
  InventoryTransaction tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.inventory,4,Item.AIR_ITEM.clone(),stack(4)),new SlotChangeAction(f.grindstone,0,stack(4),Item.AIR_ITEM.clone())));
  assertEquals(!vetoSource,tx.execute());assertTrue(requested.get());
  assertEquals(4,f.owned(),"Public grindstone close entrypoints must not copy provisional destination or restored source into the world");
  assertTrue(f.grindstone.getEquipment().isNull());
  assertFalse(f.closeWindowIds.isEmpty());assertTrue(f.closeWindowIds.stream().allMatch(id->id==Player.GRINDSTONE_WINDOW_ID),"Direct close retains the native window identity");
  if(!vetoSource){assertEquals(4,f.inventory.getItem(4).getCount());assertEquals(0,f.dropped.get());}
 }
 @Test void directInventoryCloseCannotDuplicateAcceptedDestination() throws Exception{directClose(false,false);}
 @Test void directInventoryCloseCannotDuplicateLaterRefusedSource() throws Exception{directClose(false,true);}
 @Test void directOnCloseCannotDuplicateAcceptedDestination() throws Exception{directClose(true,false);}
 private void closeAndRemoveOrdering(boolean directFirst) throws Exception {
  Fixture f=new Fixture();f.input(4);AtomicBoolean requested=new AtomicBoolean();
  doAnswer(call->{if(call.getArgument(0) instanceof EntityInventoryChangeEvent e&&e.getSlot()==4&&e.getNewItem().getId()==Item.EMERALD&&requested.compareAndSet(false,true)){
   if(directFirst){f.grindstone.close(f.player);f.player.removeWindow(f.grindstone);}else{f.player.removeWindow(f.grindstone);f.grindstone.close(f.player);}
  }return null;}).when(f.plugins).callEvent(any(Event.class));
  InventoryTransaction tx=new InventoryTransaction(f.player,List.of(new SlotChangeAction(f.inventory,4,Item.AIR_ITEM.clone(),stack(4)),new SlotChangeAction(f.grindstone,0,stack(4),Item.AIR_ITEM.clone())));
  assertTrue(tx.execute());assertTrue(requested.get());assertFalse(f.windows.containsKey(f.grindstone),"A direct cleanup request must not swallow a later window removal");
  assertEquals(4,f.owned());assertTrue(f.grindstone.getEquipment().isNull());assertEquals(4,f.inventory.getItem(4).getCount());assertEquals(0,f.dropped.get());
  assertFalse(f.closeWindowIds.isEmpty());assertTrue(f.closeWindowIds.stream().allMatch(id->id==Player.GRINDSTONE_WINDOW_ID));verify(f.player,times(1)).resetCraftingGridType();
 }
 @Test void directCloseThenWindowRemovalKeepsBothOperations() throws Exception{closeAndRemoveOrdering(true);}
 @Test void windowRemovalThenDirectCloseCleansFinalContentsOnce() throws Exception{closeAndRemoveOrdering(false);}
 @Test void ordinaryNativeCloseReturnsOrDropsAndClearsInputsOnce() throws Exception {
  Fixture f=new Fixture();f.input(4);f.player.removeWindow(f.grindstone);assertEquals(4,f.owned());assertTrue(f.grindstone.getEquipment().isNull());assertFalse(f.windows.containsKey(f.grindstone));verify(f.player).resetCraftingGridType();
 }
}
