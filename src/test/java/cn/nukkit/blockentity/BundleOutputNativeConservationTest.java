package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native item/caller-contract probe; world/network fixture is controlled, not a live client. */
@org.junit.jupiter.api.parallel.Isolated
public final class BundleOutputNativeConservationTest {
    private static Server previousServer;

    @org.junit.jupiter.api.BeforeAll static void captureServer() {
        previousServer = Server.getInstance();
        MockServer.init();
    }

    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }
    private PluginManager previous;
    private boolean previousPrimary;
    private Player player;
    private PlayerInventory held;
    private PlayerInventory destination;
    private Map<Long,Entity> entities;
    private byte[] opaque;
    private final List<RegisteredListener> listeners=new ArrayList<>();
    private static Object field(Object value,String name) throws Exception {
        Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        MockServer.init();Server server=Server.getInstance();previousPrimary=server.isPrimaryThread();when(server.isPrimaryThread()).thenReturn(true);previous=server.getPluginManager();
        when(server.getPluginManager()).thenReturn(new PluginManager(server,null));
        Class<?> type=Class.forName("cn.nukkit.inventory.transaction.InventoryTransactionPreparedWorldDropTest$Fixture");
        var constructor=type.getDeclaredConstructor(Server.class);constructor.setAccessible(true);Object f=constructor.newInstance(server);
        player=(Player)field(f,"source");held=(PlayerInventory)field(f,"sourceInventory");
        destination=(PlayerInventory)field(f,"pickerInventory");entities=(Map<Long,Entity>)field(f,"levelEntities");
        Item unit=Item.get(Item.DIAMOND,0,5);
        unit.setNamedTag(new CompoundTag().putString("opaque","bundle-native-source").putByteArray("bytes",new byte[]{-1,0,42}));opaque=unit.getCompoundTag().clone();
        ItemBundle carrier=new ItemBundle();assertTrue(carrier.getInventory().setItem(0,unit,false));held.setItemForce(0,carrier);
        assertEquals(5,total());
    }
    @AfterEach void cleanup(){
        listeners.forEach(ItemSpawnEvent.getHandlers()::unregister);
        listeners.forEach(EntityInventoryChangeEvent.getHandlers()::unregister);
        when(Server.getInstance().getPluginManager()).thenReturn(previous);
        when(Server.getInstance().isPrimaryThread()).thenReturn(previousPrimary);
    }
    private void itemSpawn(java.util.function.Consumer<ItemSpawnEvent> callback){
        Plugin plugin=mock(Plugin.class);when(plugin.isEnabled()).thenReturn(true);
        RegisteredListener listener=new RegisteredListener(new Listener(){},(l,event)->callback.accept((ItemSpawnEvent)event),EventPriority.NORMAL,plugin,false);
        ItemSpawnEvent.getHandlers().register(listener);listeners.add(listener);
    }
    private void slotChange(java.util.function.Consumer<EntityInventoryChangeEvent> callback){
        Plugin plugin=mock(Plugin.class);when(plugin.isEnabled()).thenReturn(true);
        RegisteredListener listener=new RegisteredListener(new Listener(){},(l,event)->callback.accept((EntityInventoryChangeEvent)event),EventPriority.NORMAL,plugin,false);
        EntityInventoryChangeEvent.getHandlers().register(listener);listeners.add(listener);
    }
    private boolean clickLikeNativeCaller(){
        ItemBundle copy=(ItemBundle)held.getItemInHand();boolean accepted=copy.onClickAir(player,new Vector3(0,0,1));
        if(accepted&&(copy.getId()==0||held.getItemInHandFast().getId()==copy.getId()))held.setItemInHand(copy);
        return accepted;
    }
    private int units(Item item){
        if(item instanceof ItemBundle bundle)return bundle.getInventory().getContents().values().stream().mapToInt(this::units).sum();
        if(item.getId()!=Item.DIAMOND)return 0;assertArrayEquals(opaque,item.getCompoundTag());return item.getCount();
    }
    private int total(){
        int count=held.getContents().values().stream().mapToInt(this::units).sum()+destination.getContents().values().stream().mapToInt(this::units).sum();
        for(Entity entity:entities.values())if(!entity.isClosed()&&entity instanceof EntityItem dropped)count+=units(dropped.getItem());
        System.out.println("BUNDLE_NATIVE_CONSERVATION total="+count);return count;
    }
    @Test void bundleOutputResetsNativeUsingStartAction() throws Exception {
        var metadata = new cn.nukkit.entity.data.EntityMetadata()
                .putLong(Entity.DATA_FLAGS, 1L << Entity.DATA_FLAG_ACTION);
        Field data = Entity.class.getDeclaredField("dataProperties");
        data.setAccessible(true); data.set(player, metadata);
        doNothing().when(player).setDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_ACTION, false);
        doCallRealMethod().when(player).setUsingItem(anyBoolean());
        player.setUsingItem(true);
        assertTrue(player.isUsingItem());
        assertTrue(clickLikeNativeCaller());
        assertFalse(player.isUsingItem(), "Accepted bundle output must reset the native use branch like Player.dropItem");
        assertEquals(5, total());
    }
    @Test void ordinaryBundleOutputConservesFiveOpaqueUnits(){assertTrue(clickLikeNativeCaller());assertEquals(5,total());assertEquals(1,entities.size());}
    @Test void abortedNativeWorldSpawnCannotLoseBundleContents(){AtomicInteger calls=new AtomicInteger();itemSpawn(e->{calls.incrementAndGet();e.getEntity().close();});clickLikeNativeCaller();assertEquals(1,calls.get());assertEquals(5,total());}
    @Test void nativeCarrierMoveDuringSpawnCannotFundSecondCopy(){
        AtomicInteger calls=new AtomicInteger();itemSpawn(e->{if(calls.getAndIncrement()!=0)return;
            Item original=held.getItemInHand();
            InventoryTransaction transfer=new InventoryTransaction(player,List.of(new SlotChangeAction(held,0,original,Item.get(Item.AIR)),new SlotChangeAction(destination,0,Item.get(Item.AIR),original)));
            assertTrue(transfer.execute(),"Independent native carrier transfer must reach acceptance");
        });
        clickLikeNativeCaller();assertEquals(1,calls.get());assertEquals(5,total());
    }
    @Test void cancelledCanonicalCarrierWritePreservesContents(){
        slotChange(e->{if(e.getEntity()==player)e.setCancelled(true);});
        assertFalse(clickLikeNativeCaller());assertEquals(5,total());assertTrue(entities.isEmpty());
        assertEquals(5,units(held.getItemInHand()));
    }
    @Test void throwingCanonicalCarrierWriteDiscardsPreparedOutput() throws Exception {
        held=spy(held);
        Field inventory=cn.nukkit.entity.EntityHumanType.class.getDeclaredField("inventory");
        inventory.setAccessible(true);inventory.set(player,held);
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("bundle-write-refused");})
                .when(held).setItem(anyInt(),any(Item.class),eq(false));
        assertThrows(IllegalStateException.class,this::clickLikeNativeCaller);
        assertEquals(5,total());assertTrue(entities.isEmpty());
    }
    @Test void replacementCanonicalCarrierWriteIsRefused(){
        slotChange(e->{if(e.getEntity()==player)e.setNewItem(Item.get(Item.DIRT,0,1));});
        assertFalse(clickLikeNativeCaller());assertEquals(5,total());assertTrue(entities.isEmpty());
    }
    @Test void recursivelyRequestedBundleOutputPublishesOnlyOnce(){
        AtomicInteger calls=new AtomicInteger();itemSpawn(e->{calls.incrementAndGet();assertFalse(clickLikeNativeCaller());});
        assertTrue(clickLikeNativeCaller());assertEquals(1,calls.get());assertEquals(5,total());assertEquals(1,entities.size());
    }
    @Test void changingHeldSlotDuringPreparationDoesNotRemoveContents(){
        itemSpawn(e->held.setHeldItemIndex(1,false));
        assertFalse(clickLikeNativeCaller());assertEquals(5,total());assertTrue(entities.isEmpty());
        assertEquals(5,units(held.getItem(0)));
    }
    @Test void callerCacheCannotRecreateAlreadyPublishedContents(){
        ItemBundle caller=(ItemBundle)held.getItemInHand();assertEquals(5,units(caller));
        assertTrue(caller.onClickAir(player,new Vector3(0,0,1)));assertEquals(0,units(caller));
        assertFalse(caller.onClickAir(player,new Vector3(0,0,1)));assertEquals(5,total());assertEquals(1,entities.size());
    }
}
