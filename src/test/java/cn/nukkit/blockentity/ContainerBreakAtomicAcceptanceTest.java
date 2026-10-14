package cn.nukkit.blockentity;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.PlayerEnderChestInventory;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.inventory.transaction.action.CreativeInventoryAction;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.concurrent.CompletionException;
import cn.nukkit.inventory.transaction.action.DropItemAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.AddItemEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.RegisteredListener;
import cn.nukkit.nbt.tag.ListTag;
@org.junit.jupiter.api.parallel.Isolated
@SuppressWarnings("try")
public class ContainerBreakAtomicAcceptanceTest {
    private static Server originalServer;
    private cn.nukkit.lang.BaseLang previousLanguage;
    @org.junit.jupiter.api.BeforeAll static void rememberServer() { originalServer = Server.getInstance(); }
    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        var field = Server.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, originalServer);
    }

    private PluginManager previous;
    private PluginManager manager;
    private Fixture f;
    private BlockEntityChest chest;
    private byte[] opaque;
    private final List<RegisteredListener> listeners = new ArrayList<>();
    private final List<AutoCloseable> leases = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        MockServer.init();
        Server server = Server.getInstance(); previous = server.getPluginManager();
        previousLanguage = server.getLanguage();
        manager = new PluginManager(server, null); when(server.getPluginManager()).thenReturn(manager);
        cn.nukkit.lang.BaseLang language=mock(cn.nukkit.lang.BaseLang.class);
        when(language.translateString(anyString(),any(String[].class))).thenReturn("native listener control failure");
        when(server.getLanguage()).thenReturn(language);
        f = new Fixture(server);
        doCallRealMethod().when(f.level).dropItem(any(Vector3.class), any(Item.class));
        doCallRealMethod().when(f.level).dropItem(any(Vector3.class), any(Item.class), nullable(Vector3.class));
        chest = new BlockEntityChest(f.chunk, new CompoundTag().putString("id", BlockEntity.CHEST)
                .putInt("x",0).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));
        Item source=Item.get(Item.DIAMOND,0,5);
        source.setNamedTag(new CompoundTag().putString("opaque", "native-container-source")
                .putByteArray("bytes",new byte[]{-1,0,71}));
        opaque=source.getCompoundTag().clone();
        assertTrue(chest.getInventory().setItem(0,source));
    }
    @AfterEach void cleanup() throws Exception {
        for(int i=leases.size()-1;i>=0;i--) leases.get(i).close();
        listeners.forEach(ItemSpawnEvent.getHandlers()::unregister);
        when(Server.getInstance().getPluginManager()).thenReturn(previous);
        when(Server.getInstance().getLanguage()).thenReturn(previousLanguage);
    }
    private void spawn(java.util.function.Consumer<ItemSpawnEvent> callback) {
        Plugin plugin=mock(Plugin.class);when(plugin.isEnabled()).thenReturn(true);when(plugin.getDescription()).thenReturn(new cn.nukkit.plugin.PluginDescription(Map.of("name","native-container-control","version","1","main","test.Main","api",List.of("1.0.0"))));
        RegisteredListener listener=new RegisteredListener(new Listener(){},(ignored,event)->callback.accept((ItemSpawnEvent)event),EventPriority.NORMAL,plugin,false);
        ItemSpawnEvent.getHandlers().register(listener);listeners.add(listener);
    }
    private int total() {
        int source=chest.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum();
        int world=f.levelEntities.values().stream().filter(EntityItem.class::isInstance)
                .map(EntityItem.class::cast).filter(e->!e.closed && !e.isPublicationDeferred()).mapToInt(e->e.getItem().getCount()).sum();
        int moved=f.sourceInventory.getContents().values().stream().filter(i->i.getId()==Item.DIAMOND).mapToInt(Item::getCount).sum();
        List<Item> checked=new ArrayList<>(chest.getInventory().getContents().values());
        f.levelEntities.values().stream().filter(EntityItem.class::isInstance).map(EntityItem.class::cast)
                .filter(e->!e.closed && !e.isPublicationDeferred()).map(EntityItem::getItem).forEach(checked::add);
        f.sourceInventory.getContents().values().stream().filter(i->i.getId()==Item.DIAMOND).forEach(checked::add);
        for(Item item:checked){assertEquals(Item.DIAMOND,item.getId());assertArrayEquals(opaque,item.getCompoundTag());}
        System.out.println("BREAK_AUDIT inventory="+source+" world="+world+" moved="+moved+" actual="+(source+world+moved));
        return source+world+moved;
    }
    @Test void healthyBreakPublishesOneExactSource() {
        chest.onBreak();assertEquals(5,total());assertTrue(chest.getInventory().isEmpty());assertEquals(1,f.levelEntities.size());
    }
    @Test void nativeRefusalMustNotPublishBesideRetainedSource() {
        InventoryMutationGuard.Lease lease=InventoryMutationGuard.tryClaim(List.of(chest.getInventory()));
        assertNotNull(lease);leases.add(lease);leases.add(lease.blockWrites());
        chest.onBreak();assertEquals(5,total());
    }
    @Test void recursiveBreakDuringNativeItemSpawnMustNotPublishTwice() {
        AtomicInteger calls=new AtomicInteger();spawn(event->{if(calls.getAndIncrement()==0)chest.onBreak();});
        chest.onBreak();assertEquals(5,total());
    }
    @Test void eventAcceptedRelocationMustNotBeCopiedIntoWorld() {
        spawn(event->{Item original=chest.getInventory().getItem(0);assertTrue(chest.getInventory().clear(0));
            assertTrue(f.sourceInventory.setItem(1,original));});
        chest.onBreak();assertEquals(5,total());
    }
    @Test void sourceDirtyRuntimeExceptionMustNotRetainPublishedCopy() {
        AtomicInteger hooks=sourceWriteFailure(false);
        assertThrows(IllegalStateException.class,chest::onBreak);assertEquals(1,hooks.get());assertEquals(5,total());
    }
    @Test void sourceDirtyErrorMustConserveUnits() {
        AtomicInteger hooks=sourceWriteFailure(true);
        assertThrows(AssertionError.class,chest::onBreak);assertEquals(1,hooks.get());assertEquals(5,total());
    }
    private int published() {
        return f.levelEntities.values().stream().filter(EntityItem.class::isInstance).map(EntityItem.class::cast)
                .filter(e -> !e.closed && !e.isPublicationDeferred()).mapToInt(e -> e.getItem().getCount()).sum();
    }
    private BlockEntityBarrel barrel() {
        return new BlockEntityBarrel(f.chunk,new CompoundTag().putString("id",BlockEntity.BARREL)
                .putInt("x",1).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));
    }
    @Test void deferredBreakAndCloseWaitForAcceptedOwnerDecision() throws Exception {
        InventoryMutationGuard.Lease lease=InventoryMutationGuard.tryClaim(List.of(chest.getInventory()));
        assertNotNull(lease);
        try(lease;var blocked=lease.blockWrites()) {
            chest.onBreak();chest.close();chest.onBreak();
            assertFalse(chest.closed);assertEquals(0,published());assertTrue(f.levelEntities.isEmpty());
            Item accepted=chest.getRealInventory().getItem(0);accepted.setCount(4);
            try(var write=lease.expectWrite(chest.getRealInventory(),0,accepted)) {
                assertTrue(chest.getRealInventory().setItem(0,accepted,false));
            }
        }
        assertTrue(chest.closed);assertEquals(4,published());assertTrue(chest.getRealInventory().isEmpty());
        assertEquals(1,f.levelEntities.size());
    }
    @Test void recursiveCloseWaitsForFundedWorldRelease() {
        spawn(event->{assertTrue(event.getEntity().isPublicationDeferred());assertEquals(0,published());
            assertEquals(0,f.spawnPackets.get());chest.close();assertFalse(chest.closed);});
        chest.onBreak();assertTrue(chest.closed);assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());
    }
    @Test void invalidSecondTicketLeavesEveryRawSourceAndNoWorldCopy() {
        Item second=chest.getRealInventory().getItem(0);second.setCount(3);assertTrue(chest.getRealInventory().setItem(1,second));
        AtomicInteger calls=new AtomicInteger();spawn(event->{if(calls.incrementAndGet()==2)event.getEntity().close();});
        assertThrows(IllegalStateException.class,chest::onBreak);assertEquals(8,total());assertEquals(0,published());assertTrue(f.levelEntities.isEmpty());
        assertEquals(2,calls.get());assertEquals(5,chest.getRealInventory().getItem(0).getCount());assertEquals(3,chest.getRealInventory().getItem(1).getCount());
    }
    @Test void alteredQuantityCannotPublishUnfundedWorldVariant() {
        spawn(event->event.getEntity().getItem().setCount(9));assertThrows(IllegalStateException.class,chest::onBreak);
        assertEquals(5,total());assertEquals(0,published());assertTrue(f.levelEntities.isEmpty());
    }
    @Test void hiddenNegativeSlotsAndOpaqueNbtArePartOfTheWholeBreak() {
        Item hidden=chest.getRealInventory().getItem(0);hidden.setCount(3);
        chest.getRealInventory().slots.put(-2,hidden.clone());chest.getRealInventory().slots.put(90,hidden.clone());
        chest.onBreak();assertEquals(11,total());assertEquals(11,published());assertTrue(chest.getRealInventory().slots.isEmpty());
    }
    @Test void rawSnapshotSizeChangeDuringSpawnRefusesStaleWholeDebit() {
        spawn(event->chest.getRealInventory().setMaxStackSize(32));assertThrows(IllegalStateException.class,chest::onBreak);
        assertEquals(5,total());assertEquals(32,chest.getRealInventory().getMaxStackSize());assertTrue(f.levelEntities.isEmpty());
    }
    private void nativeLevelBreak() throws Exception {
        cn.nukkit.block.BlockChest block=new cn.nukkit.block.BlockChest();block.level=f.level;block.x=0;block.y=64;block.z=0;
        Field server=Level.class.getDeclaredField("server");server.setAccessible(true);server.set(f.level,Server.getInstance());
        when(f.level.getBlock(any(Vector3.class))).thenReturn(block);
        when(f.level.getChunk(anyInt(),anyInt())).thenReturn((cn.nukkit.level.format.generic.BaseFullChunk)f.chunk);
        when(f.level.getBlockEntity(any(Vector3.class))).thenAnswer(call->chest.closed?null:chest);
        Field rules=Level.class.getDeclaredField("gameRules");rules.setAccessible(true);
        var gameRules=cn.nukkit.level.GameRules.getDefault();gameRules.setGameRule(cn.nukkit.level.GameRule.DO_TILE_DROPS,false);rules.set(f.level,gameRules);
        Field vibration=Level.class.getDeclaredField("vibrationManager");vibration.setAccessible(true);vibration.set(f.level,mock(vibration.getType()));
        doCallRealMethod().when(f.level).useBreakOn(any(Vector3.class),any(cn.nukkit.math.BlockFace.class),any(Item.class),nullable(Player.class),anyBoolean());
    }
    @Test void actualLevelUseBreakOnCallsThePreparedContainerPath() throws Exception {
        nativeLevelBreak();
        assertNotNull(f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertTrue(chest.closed);assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());
        assertEquals(1,f.levelEntities.size());
    }
    @Test void actualSurvivalPlayerBlockBreakPublishesOneFundedContainerSource() throws Exception {
        nativeLevelBreak();
        Field effects=Entity.class.getDeclaredField("effects");effects.setAccessible(true);effects.set(f.source,new java.util.HashMap<Integer,cn.nukkit.potion.Effect>());
        Field settings=Player.class.getDeclaredField("adventureSettings");settings.setAccessible(true);settings.set(f.source,new cn.nukkit.AdventureSettings(f.source));
        Field vector=Entity.class.getDeclaredField("temporalVector");vector.setAccessible(true);vector.set(f.source,new Vector3());
        Field properties=Entity.class.getDeclaredField("dataProperties");properties.setAccessible(true);properties.set(f.source,new cn.nukkit.entity.data.EntityMetadata());
        f.source.gamemode=Player.SURVIVAL;f.source.lastBreak=-1;f.source.onGround=true;
        assertNotNull(f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),f.source,false));
        assertTrue(chest.closed);assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());assertEquals(1,f.levelEntities.size());
    }
    @Test void actualLevelRefusedBreakKeepsRetainedSourceReachableForRetry() throws Exception {
        nativeLevelBreak();AtomicInteger attempts=new AtomicInteger();
        spawn(event->{if(attempts.getAndIncrement()==0)chest.getRealInventory().setMaxStackSize(32);});
        assertThrows(IllegalStateException.class,()->f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertFalse(chest.closed);assertEquals(5,total());assertEquals(0,published());assertEquals(32,chest.getRealInventory().getMaxStackSize());
        verify(f.level,never()).setBlock(any(Vector3.class),any(cn.nukkit.block.Block.class),eq(true),eq(true));
        assertNotNull(f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertTrue(chest.closed);assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());assertEquals(1,f.levelEntities.size());
    }
    @Test void replacedNativeHolderCannotDebitItsGoodsIntoTheOldWorldActor() throws Exception {
        BlockEntityChest owner=new BlockEntityChest(f.chunk,new CompoundTag().putString("id",BlockEntity.CHEST)
                .putInt("x",1).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));
        Field holder=cn.nukkit.inventory.BaseInventory.class.getDeclaredField("holder");holder.setAccessible(true);holder.set(chest.getRealInventory(),owner);
        assertThrows(IllegalStateException.class,chest::onBreak);
        assertEquals(5,chest.getRealInventory().getItem(0).getCount());assertTrue(f.levelEntities.isEmpty());assertSame(owner,chest.getRealInventory().getHolder());
    }
    @Test void actualNativePickupRefusesAConstructingContainerDrop() {
        spawn(event->{EntityItem item=event.getEntity();assertTrue(item.isPublicationDeferred());item.setPickupDelay(0);
            assertFalse(f.picker.pickupEntity(item,true));assertFalse(item.canBeSavedWithChunk());assertFalse(item.isMergeable());
            item.spawnTo(f.picker);assertEquals(0,f.spawnPackets.get());assertEquals(0,published());});
        chest.onBreak();assertEquals(5,published());assertTrue(f.pickerInventory.isEmpty());assertTrue(chest.getRealInventory().isEmpty());
    }
    @Test void exactMetadataRollbackDiscardsEveryPreparedWorldUnit() {
        AtomicInteger changed=new AtomicInteger();
        doAnswer(call->{boolean write=java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(frame->frame.getClassName().equals("cn.nukkit.inventory.ContainerInventory")&&frame.getMethodName().equals("onSlotChange"));
            if(write && changed.compareAndSet(0,1))chest.getRealInventory().setMaxStackSize(1);return null;}).when(f.chunk).setChanged();
        assertThrows(IllegalStateException.class,chest::onBreak);assertEquals(1,changed.get());assertEquals(5,total());assertEquals(64,chest.getRealInventory().getMaxStackSize());
        assertTrue(f.levelEntities.isEmpty());assertEquals(0,f.spawnPackets.get());
    }
    @Test void preparationOfAnotherHolderDoesNotSuppressItsLegitimateBreak() {
        BlockEntityChest other=new BlockEntityChest(f.chunk,new CompoundTag().putString("id",BlockEntity.CHEST)
                .putInt("x",1).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));
        Item item=chest.getRealInventory().getItem(0);item.setCount(3);assertTrue(other.getRealInventory().setItem(0,item));
        AtomicInteger called=new AtomicInteger();spawn(event->{if(called.getAndIncrement()==0)other.onBreak();});
        chest.onBreak();assertEquals(8,published());assertTrue(chest.getRealInventory().isEmpty());assertTrue(other.getRealInventory().isEmpty());
        assertEquals(2,f.levelEntities.size());
    }
    @Test void pairedChestOwnerLeaseDefersBeforeUnpairingAndDropsOnlyBrokenHalf() {
        BlockEntityChest other=new BlockEntityChest(f.chunk,new CompoundTag().putString("id",BlockEntity.CHEST)
                .putInt("x",1).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));
        when(f.level.getBlockEntity(any(Vector3.class))).thenAnswer(call->((Vector3)call.getArgument(0)).getFloorX()==0?chest:other);
        when(f.level.getBlockEntityIfLoaded(any(Vector3.class))).thenAnswer(call->((Vector3)call.getArgument(0)).getFloorX()==0?chest:other);
        Item item=chest.getRealInventory().getItem(0);item.setCount(3);assertTrue(other.getRealInventory().setItem(0,item));
        when(f.level.getBlock(any(Vector3.class))).thenAnswer(call->{Vector3 position=call.getArgument(0);
            cn.nukkit.block.BlockChest block=new cn.nukkit.block.BlockChest();block.level=f.level;block.x=position.x;block.y=position.y;block.z=position.z;return block;});
        assertTrue(chest.pairWith(other));
        var doubleInventory=chest.getInventory();assertTrue(doubleInventory instanceof cn.nukkit.inventory.DoubleChestInventory);
        try(var lease=InventoryMutationGuard.tryClaim(List.of(doubleInventory));var blocked=lease.blockWrites()) {
            assertNotNull(lease);chest.onBreak();chest.close();
            assertTrue(chest.isPaired());assertFalse(chest.closed);assertEquals(0,published());
        }
        assertTrue(chest.closed);assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());
        assertEquals(3,other.getRealInventory().getItem(0).getCount());assertFalse(other.closed);assertFalse(other.isPaired());
    }
    @Test void fatalPublicationNotificationCannotRefundAcceptedSource() {
        doAnswer(call->{if(call.getArgument(0) instanceof AddItemEntityPacket)throw new AssertionError("native accepted notification");return true;})
                .when(f.picker).dataPacket(any(cn.nukkit.network.protocol.DataPacket.class));
        assertThrows(AssertionError.class,chest::onBreak);assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());
    }
    @Test void deferredCloseFatalErrorPropagatesAfterFundedWorldPublication() {
        spawn(event->chest.close());
        doAnswer(call->{assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());throw new AssertionError("native accepted close");})
                .when(f.chunk).removeBlockEntity(any(BlockEntity.class));
        assertThrows(AssertionError.class,chest::onBreak);assertEquals(5,published());assertTrue(chest.getRealInventory().isEmpty());
    }
    @Test void nativeMoveSourceHookBreakPublishesOnlyFinalAcceptedRemainder() {
        interop(false,false,false);
    }
    @Test void nativeMoveSourceHookRuntimeFailureBreakPublishesRestoredWholeSource() {
        interop(true,false,false);
    }
    @Test void nativeMoveSourceHookFatalFailureBreakPublishesRestoredWholeSource() {
        interop(true,true,false);
    }
    @Test void nativeMovePreparationCallbackBreakWaitsForAcceptedPair() {
        interop(false,false,true);
    }
    private java.util.concurrent.atomic.AtomicReference<cn.nukkit.block.Block> nativeBlockState() throws Exception {
        nativeLevelBreak();
        var state=new java.util.concurrent.atomic.AtomicReference<>(f.level.getBlock(new Vector3(0,64,0)));
        when(f.level.getBlock(any(Vector3.class))).thenAnswer(call->state.get());
        doAnswer(call->{state.set(call.getArgument(1));return true;}).when(f.level)
                .setBlock(any(Vector3.class),any(cn.nukkit.block.Block.class),eq(true),eq(true));
        return state;
    }
    @Test void nativeLevelOwnedSourceRefusesBeforeRemovalAndQueuesNoLateVeto() throws Exception {
        var state=nativeBlockState();AtomicInteger attempts=new AtomicInteger();
        spawn(event->{if(attempts.getAndIncrement()==0)chest.getRealInventory().setMaxStackSize(32);});
        var claim=InventoryMutationGuard.tryClaim(List.of(chest.getRealInventory()));assertNotNull(claim);
        Item answer;Throwable late=null;
        try {answer=f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false);}
        finally {try{claim.close();}catch(RuntimeException|Error failure){late=failure;}}
        System.out.println("OWNED_LEVEL_BREAK block="+state.get().getId()+" source="+chest.getRealInventory().getItem(0).getCount()+" world="+published()+" late="+(late==null?"none":late.getClass().getSimpleName()));
        assertNull(answer,"ordinary native break must refuse while another operation owns the source");
        assertNull(late,"refused native caller must not queue a later world decision");assertEquals(0,attempts.get());
        assertEquals(cn.nukkit.block.Block.CHEST,state.get().getId());assertFalse(chest.closed);assertEquals(5,total());assertEquals(0,published());
        assertThrows(IllegalStateException.class,()->f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertEquals(cn.nukkit.block.Block.CHEST,state.get().getId());assertFalse(chest.closed);assertEquals(5,total());assertEquals(0,published());
        assertNotNull(f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertEquals(cn.nukkit.block.Block.AIR,state.get().getId());assertTrue(chest.closed);assertEquals(5,published());assertEquals(5,total());assertEquals(1,f.levelEntities.size());
    }
    @Test void nativeLevelPreparedSourceRefusesBeforeRemovalAndQueuesNoLateVeto() throws Exception {
        var state=nativeBlockState();AtomicInteger attempts=new AtomicInteger();
        spawn(event->{if(attempts.getAndIncrement()==0)chest.getRealInventory().setMaxStackSize(32);});
        var preparing=cn.nukkit.inventory.NativeContainerTransaction.tryPrepare(chest.getRealInventory());assertNotNull(preparing);
        Item answer;Throwable late=null;
        try {answer=f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false);}
        finally {try{preparing.close();}catch(RuntimeException|Error failure){late=failure;}}
        System.out.println("PREPARED_LEVEL_BREAK block="+state.get().getId()+" source="+chest.getRealInventory().getItem(0).getCount()+" world="+published()+" late="+(late==null?"none":late.getClass().getSimpleName()));
        assertNull(answer,"ordinary native break must refuse during source preparation before the lease exists");
        assertNull(late);assertEquals(0,attempts.get());assertEquals(cn.nukkit.block.Block.CHEST,state.get().getId());
        assertFalse(chest.closed);assertEquals(5,total());assertEquals(0,published());
        assertThrows(IllegalStateException.class,()->f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertEquals(cn.nukkit.block.Block.CHEST,state.get().getId());assertEquals(5,total());assertEquals(0,published());
        assertNotNull(f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertEquals(cn.nukkit.block.Block.AIR,state.get().getId());assertEquals(5,published());assertEquals(5,total());assertEquals(1,f.levelEntities.size());
    }
    @Test void nativeItemSpawnRecursiveLevelBreakCannotDeleteBeforeOuterVeto() throws Exception {
        var state=nativeBlockState();AtomicInteger calls=new AtomicInteger();
        var nested=new java.util.concurrent.atomic.AtomicReference<Item>();
        spawn(event->{if(calls.getAndIncrement()==0){nested.set(f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));chest.getRealInventory().setMaxStackSize(32);}});
        assertThrows(IllegalStateException.class,()->f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        System.out.println("REENTRANT_LEVEL_BREAK block="+state.get().getId()+" source="+chest.getRealInventory().getItem(0).getCount()+" world="+published());
        assertNull(nested.get());assertEquals(cn.nukkit.block.Block.CHEST,state.get().getId());assertFalse(chest.closed);assertEquals(5,total());assertEquals(0,published());
        assertNotNull(f.level.useBreakOn(new Vector3(0,64,0),cn.nukkit.math.BlockFace.UP,Item.get(Item.DIAMOND_AXE),null,false));
        assertEquals(cn.nukkit.block.Block.AIR,state.get().getId());assertEquals(5,published());assertEquals(5,total());assertEquals(1,f.levelEntities.size());
    }

    private void interop(boolean fail,boolean fatal,boolean duringEvent) {
        BlockEntityBarrel target=barrel();AtomicInteger callbacks=new AtomicInteger();
        Runnable lifecycle=()->{chest.onBreak();chest.close();assertFalse(chest.closed);assertEquals(0,published());};
        RegisteredListener listener=null;
        if(duringEvent) {
            Plugin plugin=mock(Plugin.class);when(plugin.isEnabled()).thenReturn(true);when(plugin.getDescription()).thenReturn(new cn.nukkit.plugin.PluginDescription(Map.of("name","native-container-control","version","1","main","test.Main","api",List.of("1.0.0"))));
            listener=new RegisteredListener(new Listener(){},(ignored,event)->{callbacks.incrementAndGet();lifecycle.run();},EventPriority.NORMAL,plugin,false);
            cn.nukkit.event.inventory.InventoryMoveItemEvent.getHandlers().register(listener);
        } else {
            doAnswer(call->{boolean sourceWrite=java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                    .anyMatch(frame->frame.getClassName().equals("cn.nukkit.inventory.ContainerInventory")&&frame.getMethodName().equals("onSlotChange"));
                if(sourceWrite && chest.getRealInventory().getItem(0).getCount()==4 && callbacks.compareAndSet(0,1)) {
                    lifecycle.run();if(fail){if(fatal)throw new AssertionError("native pair debit dirty failure");throw new IllegalStateException("native pair debit dirty failure");}
                }return null;}).when(f.chunk).setChanged();
        }
        try {
            Runnable move=()->assertTrue(cn.nukkit.inventory.NativeContainerTransaction.move(chest,chest.getRealInventory(),0,target.getInventory(),-1,()->true));
            if(fail){if(fatal)assertThrows(AssertionError.class,move::run);else assertThrows(IllegalStateException.class,move::run);}else move.run();
        } finally {if(listener!=null)cn.nukkit.event.inventory.InventoryMoveItemEvent.getHandlers().unregister(listener);}
        assertEquals(1,callbacks.get());assertTrue(chest.closed);assertEquals(fail?5:4,published());
        assertEquals(fail?0:1,target.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum());
        assertEquals(5,published()+target.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum());
        assertTrue(chest.getRealInventory().isEmpty());
    }
    private AtomicInteger sourceWriteFailure(boolean fatal) {
        AtomicInteger calls=new AtomicInteger();
        doAnswer(call->{
            boolean nativeSlotWrite=java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                    .anyMatch(frame->frame.getClassName().equals("cn.nukkit.inventory.ContainerInventory")&&frame.getMethodName().equals("onSlotChange"));
            if(nativeSlotWrite) {
                calls.incrementAndGet();
                assertEquals(0, published(), "source failure precedes collectible world publication");
                assertEquals(0,f.spawnPackets.get());
                assertTrue(f.levelEntities.values().stream().filter(EntityItem.class::isInstance).allMatch(Entity::isPublicationDeferred));
                if(fatal)throw new AssertionError("native source dirty hook after world publication");
                throw new IllegalStateException("native source dirty hook after world publication");
            }
            return null;
        }).when(f.chunk).setChanged();
        return calls;
    }
    private static final class Fixture {
        final Level level = mock(Level.class);
        final Map<Long, Entity> chunkEntities = new LinkedHashMap<>();
        final Map<Long, Entity> levelEntities = new Long2ObjectNonBlockingMap<>();
        final FullChunk chunk;
        final Player source = mock(Player.class, CALLS_REAL_METHODS);
        final Player picker = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory sourceInventory = new PlayerInventory(source);
        final PlayerInventory pickerInventory = new PlayerInventory(picker);
        final PlayerOffhandInventory sourceOffhand = new PlayerOffhandInventory(source);
        final PlayerUIInventory sourceUI = new PlayerUIInventory(source);
        final PlayerEnderChestInventory sourceEnder = new PlayerEnderChestInventory(source);
        final AtomicInteger spawnPackets = new AtomicInteger();

        Fixture(Server server) throws Exception {
            when(level.getServer()).thenReturn(server);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of(1, picker));
            when(level.getEntity(anyLong())).thenAnswer(call -> levelEntities.get(call.getArgument(0)));
            when(level.getEntities()).thenAnswer(call -> levelEntities.values().toArray(Entity[]::new));
            field(Level.class, level, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            field(Level.class, level, "entities", levelEntities);
            doCallRealMethod().when(level).discardDeferredEntityRegistration(any(Entity.class));
            doCallRealMethod().when(level).prepareItemDrop(any(Vector3.class), any(Item.class), nullable(Vector3.class), anyInt());
            chunk = chunk(chunkEntities, 0);
            when(level.getChunk(eq(0), eq(0), eq(true))).thenReturn((cn.nukkit.level.format.generic.BaseFullChunk) null);
            doReturn(chunk).when(level).getChunk(0, 0, true);
            doAnswer(call -> { Entity entity = call.getArgument(0); levelEntities.put(entity.getId(), entity); return null; })
                    .when(level).addEntity(any(Entity.class));
            doAnswer(call -> { Entity entity = call.getArgument(0); levelEntities.remove(entity.getId(), entity); return null; })
                    .when(level).removeEntity(any(Entity.class));
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), nullable(Vector3.class), anyInt());
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), nullable(Vector3.class), anyBoolean(), anyInt());
            player(source, sourceInventory, server);
            field(EntityHumanType.class, source, "offhandInventory", sourceOffhand);
            field(EntityHumanType.class, source, "enderChestInventory", sourceEnder);
            field(Player.class, source, "playerUIInventory", sourceUI);
            player(picker, pickerInventory, server);
            doAnswer(call -> { if (call.getArgument(0) instanceof AddItemEntityPacket) spawnPackets.incrementAndGet(); return true; })
                    .when(picker).dataPacket(any(DataPacket.class));
            sourceInventory.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        }

        private void player(Player player, PlayerInventory inventory, Server server) throws Exception {
            field(Entity.class, player, "server", server);
            field(EntityHumanType.class, player, "inventory", inventory);
            field(Player.class, player, "usedChunks", new HashMap<>(Map.of(Level.chunkHash(0, 0), true)));
            player.level = level; player.spawned = true; player.y = 64;
            player.protocol = GameVersion.V1_20_0.getProtocol();
            doReturn(true).when(player).isAlive();
            doReturn(true).when(player).isOnline();
            doReturn(Map.of()).when(player).getViewers();
            doReturn(false).when(player).isSpectator();
            doReturn(false).when(player).isCreative();
            doReturn(GameVersion.V1_20_0).when(player).getGameVersion();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            doReturn(0).when(player).getWindowId(any());
            doReturn(new Vector3(0, 0, 1)).when(player).getDirectionVector();
            doNothing().when(player).setUsingItem(anyBoolean());
        }

        FullChunk chunk(Map<Long, Entity> entities, int x) {
            FullChunk value = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(value.getProvider()).thenReturn(provider);
            when(value.getX()).thenReturn(x);
            when(value.getEntities()).thenReturn(entities);
            doAnswer(call -> { Entity entity = call.getArgument(0); entities.put(entity.getId(), entity); return null; })
                    .when(value).addEntity(any(Entity.class));
            doAnswer(call -> { Entity entity = call.getArgument(0); entities.remove(entity.getId(), entity); return null; })
                    .when(value).removeEntity(any(Entity.class));
            return value;
        }

        InventoryTransaction drop(boolean dropFirst) {
            InventoryAction debit = new SlotChangeAction(sourceInventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR));
            InventoryAction drop = new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4));
            var tx = new InventoryTransaction(source, dropFirst ? List.of(drop, debit) : List.of(debit, drop));
            assertTrue(tx.canExecute());
            return tx;
        }

        InventoryTransaction twoDrops() {
            return new InventoryTransaction(source, List.of(
                    new SlotChangeAction(sourceInventory, 0, Item.get(Item.EMERALD, 0, 4), Item.get(Item.AIR)),
                    new DropItemAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 4)),
                    new SlotChangeAction(sourceInventory, 1, Item.get(Item.DIAMOND, 0, 3), Item.get(Item.AIR)),
                    new DropItemAction(Item.get(Item.AIR), Item.get(Item.DIAMOND, 0, 3))));
        }

        int total() {
            return sourceInventory.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + sourceOffhand.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + sourceUI.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + sourceEnder.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + pickerInventory.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + levelEntities.values().stream().filter(entity -> !entity.isClosed())
                    .mapToInt(entity -> ((EntityItem) entity).getItem().getCount()).sum();
        }

        private static void field(Class<?> owner, Object target, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
        }
    }
}
