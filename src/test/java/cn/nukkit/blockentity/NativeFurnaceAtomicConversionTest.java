package cn.nukkit.blockentity;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.FurnaceBurnEvent;
import cn.nukkit.event.inventory.FurnaceSmeltEvent;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.item.Item;
import cn.nukkit.MockServer;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import cn.nukkit.utils.MainLogger;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
final class NativeFurnaceAtomicConversionTest {
 static Server server; static Object previous;static CraftingManager recipes;
 final List<RegisteredListener> listeners=new ArrayList<>(); final List<AutoCloseable> scopes=new ArrayList<>();
 @BeforeAll static void init() throws Exception {
  MockServer.init();Block.init();server=mock(Server.class);when(server.getLogger()).thenReturn(mock(MainLogger.class));
  var manager=new PluginManager(server,null);when(server.getPluginManager()).thenReturn(manager);
  Field f=Server.class.getDeclaredField("instance");f.setAccessible(true);previous=f.get(null);f.set(null,server);
  recipes=new CraftingManager();when(server.getCraftingManager()).thenReturn(recipes);
 }
 @AfterAll static void restore() throws Exception {Field f=Server.class.getDeclaredField("instance");f.setAccessible(true);f.set(null,previous);}
 @AfterEach void clean() throws Exception {for(int i=scopes.size()-1;i>=0;i--)scopes.get(i).close();for(var l:listeners){FurnaceBurnEvent.getHandlers().unregister(l);FurnaceSmeltEvent.getHandlers().unregister(l);}}
 <T extends Event> void listen(HandlerList handlers,Class<T> type,Consumer<T> action){Plugin plugin=mock(Plugin.class);when(plugin.isEnabled()).thenReturn(true);var l=new RegisteredListener(new Listener(){},(listener,event)->action.accept(type.cast(event)),EventPriority.NORMAL,plugin,false);handlers.register(l);listeners.add(l);}
 BlockEntityFurnace make(boolean burning){Level level=mock(Level.class);when(level.getServer()).thenReturn(server);when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Collections.emptyMap());when(level.getBlock(any(Vector3.class))).thenReturn(Block.get(Block.LIT_FURNACE));FullChunk chunk=mock(FullChunk.class);LevelProvider provider=mock(LevelProvider.class);when(chunk.getProvider()).thenReturn(provider);when(provider.getLevel()).thenReturn(level);
  var nbt=new CompoundTag().putString("id",BlockEntity.FURNACE).putInt("x",0).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items"));var furnace=new BlockEntityFurnace(chunk,nbt);
  assertTrue(furnace.getInventory().setSmelting(Item.get(Item.IRON_ORE,0,5)));assertTrue(furnace.getInventory().setFuel(Item.get(Item.COAL,0,5)));assertNotNull(recipes.matchFurnaceRecipe(furnace.getInventory().getSmelting()));
  if(burning){furnace.burnTime=10;furnace.maxTime=10;furnace.cookTime=199;furnace.crackledTime=10;}return furnace;
 }
 void refuse(BlockEntityFurnace f){var claim=InventoryMutationGuard.tryClaim(List.of(f.getInventory()));assertNotNull(claim);scopes.add(claim);scopes.add(claim.expectWrite(f.getInventory(),-1,Item.AIR_ITEM));}
 @Test void validSmeltControl(){var f=make(true);assertTrue(f.onUpdate());assertEquals(4,f.getInventory().getSmelting().getCount());assertEquals(1,f.getInventory().getResult().getCount());assertEquals(Item.IRON_INGOT,f.getInventory().getResult().getId());}
 @Test void cancelledSmeltControl(){var f=make(true);listen(FurnaceSmeltEvent.getHandlers(),FurnaceSmeltEvent.class,e->e.setCancelled());f.onUpdate();assertEquals(5,f.getInventory().getSmelting().getCount());assertTrue(f.getInventory().getResult().isNull());}
 @Test void pluginAuthoredSmeltResultXpControl(){var f=make(true);listen(FurnaceSmeltEvent.getHandlers(),FurnaceSmeltEvent.class,e->{Item output=Item.get(Item.DIAMOND,0,3);output.setNamedTag(new CompoundTag().putString("opaque","plugin-authored-conversion"));e.setResult(output);e.setXp(7);});f.onUpdate();assertEquals(4,f.getInventory().getSmelting().getCount());assertEquals(3,f.getInventory().getResult().getCount());assertEquals(Item.DIAMOND,f.getInventory().getResult().getId());assertEquals(7,f.getExperience());}
 @Test void refusedSmeltWritesMustNotPublishXp(){var f=make(true);listen(FurnaceSmeltEvent.getHandlers(),FurnaceSmeltEvent.class,e->refuse(f));f.onUpdate();System.out.println("FURNACE refused-smelt raw="+f.getInventory().getSmelting().getCount()+" product="+f.getInventory().getResult().getCount()+" xp="+f.getExperience());assertEquals(0,f.getExperience());}
 @Test void validBurnControl(){var f=make(false);f.checkFuel(f.getInventory().getFuel());assertEquals(4,f.getInventory().getFuel().getCount());assertTrue(f.burnTime>0);}
 @Test void cancelledBurnControl(){var f=make(false);listen(FurnaceBurnEvent.getHandlers(),FurnaceBurnEvent.class,e->e.setCancelled());f.checkFuel(f.getInventory().getFuel());assertEquals(5,f.getInventory().getFuel().getCount());assertEquals(0,f.burnTime);}
 @Test void pluginNonConsumingBurnControl(){var f=make(false);listen(FurnaceBurnEvent.getHandlers(),FurnaceBurnEvent.class,e->{e.setBurning(false);e.setBurnTime((short)30);});f.checkFuel(f.getInventory().getFuel());assertEquals(5,f.getInventory().getFuel().getCount());assertEquals(30,f.burnTime);}
 @Test void refusedFuelDebitMustNotCreateBurnTime(){var f=make(false);listen(FurnaceBurnEvent.getHandlers(),FurnaceBurnEvent.class,e->refuse(f));f.checkFuel(f.getInventory().getFuel());System.out.println("FURNACE refused-burn fuel="+f.getInventory().getFuel().getCount()+" burn="+f.burnTime);assertEquals(0,f.burnTime);}
 @Test void sourceRelocatedDuringSmeltMustNotReplayRaw(){var f=make(true);listen(FurnaceSmeltEvent.getHandlers(),FurnaceSmeltEvent.class,e->{Item raw=f.getInventory().getSmelting();assertTrue(f.getInventory().setSmelting(Item.AIR_ITEM));assertTrue(f.getInventory().setFuel(raw));});f.onUpdate();int units=f.getInventory().getSmelting().getCount()+f.getInventory().getFuel().getCount()+f.getInventory().getResult().getCount();System.out.println("FURNACE relocated-smelt units="+units);assertEquals(5,units);}
 @Test void smeltDebitRuntimeExceptionMustRestoreProductXp(){smeltFailure(false);}
 @Test void smeltDebitErrorMustRestoreProductXp(){smeltFailure(true);}
 void smeltFailure(boolean error){var f=make(true);var calls=new java.util.concurrent.atomic.AtomicInteger();doAnswer(c->{if(calls.incrementAndGet()==3){if(error)throw new AssertionError("raw debit dirty hook");throw new IllegalStateException("raw debit dirty hook");}return null;}).when(f.chunk).setChanged();if(error)assertThrows(AssertionError.class,f::onUpdate);else assertThrows(IllegalStateException.class,f::onUpdate);System.out.println("FURNACE failed-smelt raw="+f.getInventory().getSmelting().getCount()+" product="+f.getInventory().getResult().getCount()+" xp="+f.getExperience());assertEquals(5,f.getInventory().getSmelting().getCount()+f.getInventory().getResult().getCount());assertEquals(0,f.getExperience());}
 @Test void burnDebitRuntimeExceptionMustRestoreTimer(){burnFailure(false);}
 @Test void burnDebitErrorMustRestoreTimer(){burnFailure(true);}
 void burnFailure(boolean error){var f=make(false);doAnswer(c->{if(error)throw new AssertionError("fuel debit dirty hook");throw new IllegalStateException("fuel debit dirty hook");}).when(f.chunk).setChanged();if(error)assertThrows(AssertionError.class,()->f.checkFuel(f.getInventory().getFuel()));else assertThrows(IllegalStateException.class,()->f.checkFuel(f.getInventory().getFuel()));assertEquals(5,f.getInventory().getFuel().getCount());assertEquals(0,f.burnTime);}

 @Test void blastFurnaceNativeRecipeControl(){variant(false);}
 @Test void smokerNativeRecipeControl(){variant(true);}
 void variant(boolean smoker){var old=make(true);var tag=new CompoundTag().putString("id",smoker?BlockEntity.SMOKER:BlockEntity.BLAST_FURNACE).putInt("x",0).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items"));BlockEntityFurnace f=smoker?new BlockEntitySmoker(old.chunk,tag):new BlockEntityBlastFurnace(old.chunk,tag);Item input=Item.get(smoker?Item.RAW_BEEF:Item.IRON_ORE,0,5);assertTrue(f.getInventory().setSmelting(input));assertTrue(f.getInventory().setFuel(Item.get(Item.COAL,0,5)));assertNotNull(f.matchRecipe(f.getInventory().getSmelting()));f.burnTime=10;f.maxTime=10;f.cookTime=99;f.crackledTime=10;assertTrue(f.onUpdate());assertEquals(4,f.getInventory().getSmelting().getCount());assertEquals(1,f.getInventory().getResult().getCount());assertEquals(smoker?Item.COOKED_BEEF:Item.IRON_INGOT,f.getInventory().getResult().getId());}
 @Test void furnaceHiddenRawAndOpaqueInputSurviveSetterRollback(){var f=make(true);Item hidden=Item.get(Item.DIAMOND,0,2);hidden.setNamedTag(new CompoundTag().putString("opaque","hidden-furnace"));f.getInventory().slots.put(77,hidden.clone());Item before=f.getInventory().getSmelting();before.setNamedTag(new CompoundTag().putByteArray("opaque",new byte[]{-1,0,4}));assertTrue(f.getInventory().setSmelting(before));assertNotNull(f.matchRecipe(before));var calls=new java.util.concurrent.atomic.AtomicInteger();doAnswer(c->{if(calls.incrementAndGet()==3){f.getInventory().slots.put(-1,Item.get(Item.DIAMOND,0,9));f.getInventory().setMaxStackSize(1);throw new AssertionError("raw debit callback");}return null;}).when(f.chunk).setChanged();assertThrows(AssertionError.class,f::onUpdate);assertTrue(f.getInventory().getSmelting().equalsExact(before));assertTrue(f.getInventory().getResult().isNull());assertTrue(f.getInventory().slots.get(77).equalsExact(hidden));assertFalse(f.getInventory().slots.containsKey(-1));assertEquals(64,f.getInventory().getMaxStackSize());assertEquals(0,f.getExperience());}
 @Test void reentrantFurnaceUpdateIsRefusedBeforeTimersOrInventoryChange(){var f=make(true);var once=new java.util.concurrent.atomic.AtomicBoolean();listen(FurnaceSmeltEvent.getHandlers(),FurnaceSmeltEvent.class,e->{if(once.compareAndSet(false,true))assertFalse(f.onUpdate());});f.onUpdate();assertEquals(4,f.getInventory().getSmelting().getCount());assertEquals(1,f.getInventory().getResult().getCount());assertEquals(0,f.cookTime);}
 @Test void acceptedFurnaceNotificationErrorKeepsInputDebitAndXp(){var f=make(true);cn.nukkit.Player player=mock(cn.nukkit.Player.class);player.protocol=2193;when(player.getWindowId(f.getInventory())).thenReturn(1);f.getInventory().getViewers().add(player);doAnswer(c->{throw new AssertionError("viewer after accepted conversion");}).when(player).dataPacket(any(cn.nukkit.network.protocol.DataPacket.class));assertThrows(AssertionError.class,f::onUpdate);assertEquals(4,f.getInventory().getSmelting().getCount());assertEquals(1,f.getInventory().getResult().getCount());assertEquals(0.7f,f.getExperience());}

}
