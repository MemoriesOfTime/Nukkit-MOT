package cn.nukkit.blockentity;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDespawnEvent;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.block.BlockHopper;
import cn.nukkit.inventory.HopperInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.TakeItemEntityPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.InventoryContentPacket;
import org.mockito.AdditionalAnswers;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual published entity constructors/registration/close/merge, native pickup and registered event dispatch.
 * Level/chunk/network boundaries are controlled fixtures; no sealed APIs, no BDS/crash-durability claim. */
@org.junit.jupiter.api.Timeout(15)
class NativePublishedPickupAdjacentControlTest {
    private PluginManager previous;
    private boolean previousPacketEvents;
    private Fixture f;
    private final List<BoundListener> listeners=new ArrayList<>();
    private record BoundListener(HandlerList handlers,RegisteredListener listener) { }
    private static final AxisAlignedBB AREA=new SimpleAxisAlignedBB(0,64,0,2,66,2);
    @BeforeEach void setup() throws Exception {
        MockServer.init(); Server server=Server.getInstance(); previous=server.getPluginManager();
        PluginManager manager=new PluginManager(server,null); when(server.getPluginManager()).thenReturn(manager);
        previousPacketEvents=server.callDataPkSendEv; f=new Fixture(server);
    }
    @AfterEach void cleanup() {
        for(BoundListener bound:listeners)bound.handlers().unregister(bound.listener());
        Server server=Server.getInstance();server.callDataPkSendEv=previousPacketEvents;
        when(server.getPluginManager()).thenReturn(previous);
    }
    private void listen(HandlerList handlers,Consumer<Event> callback) {
        Plugin plugin=mock(Plugin.class);when(plugin.isEnabled()).thenReturn(true);
        RegisteredListener listener=new RegisteredListener(new Listener(){},(ignored,event)->callback.accept(event),EventPriority.NORMAL,plugin,false);
        handlers.register(listener);listeners.add(new BoundListener(handlers,listener));
    }
    @Test void aMergeDespawnCallbackCannotConsumeItsReceiverBeforeTheDonorGrant() throws Exception {
        EntityItem receiver=f.item(5),donor=f.item(3);int tick=f.mergeTick(receiver,donor);
        AtomicBoolean entered=new AtomicBoolean(),nested=new AtomicBoolean(true);
        listen(EntityDespawnEvent.getHandlers(),raw->{
            if(((EntityDespawnEvent)raw).getEntity()==donor&&entered.compareAndSet(false,true))
                nested.set(f.player.pickupEntity(receiver,true));
        });
        assertDoesNotThrow(()->receiver.onUpdate(tick));assertTrue(entered.get());assertFalse(nested.get());
        assertTrue(donor.isClosed());assertFalse(receiver.isClosed());assertEquals(8,receiver.getItem().getCount());
        assertTrue(f.main.getContents().isEmpty());assertEquals(8,f.total());
    }
    @Test void aPublicCustomIHopperInventoryKeepsItsExistingPickupContract() {
        EntityItem source=f.item(5);Inventory facade=mock(Inventory.class,AdditionalAnswers.delegatesTo(f.first.getInventory()));
        assertFalse(facade instanceof cn.nukkit.inventory.BaseInventory);
        BlockHopper.IHopper custom=new BlockHopper.IHopper(){
            public cn.nukkit.level.Position getPosition(){return f.first;}
            public Inventory getInventory(){return facade;}
            public boolean isOnTransferCooldown(){return f.first.isOnTransferCooldown();}
            public void setTransferCooldown(int value){f.first.setTransferCooldown(value);}
            public boolean pushItems(){return false;}
        };
        assertTrue(custom.pickupItems(AREA));assertTrue(source.isClosed());
        assertEquals(5,f.hopperCount(f.first));assertEquals(5,f.total());
    }
    @Test void rejectedHopperLimitChangeResendsTheRolledBackNativeViewerContents() {
        EntityItem source=f.item(5);HopperInventory inventory=spy(f.first.getInventory());f.first.inventory=inventory;
        inventory.getViewers().add(f.player);doReturn(1).when(f.player).getWindowId(inventory);
        AtomicBoolean changed=new AtomicBoolean();AtomicInteger viewedCount=new AtomicInteger(0),provisional=new AtomicInteger(0),resyncs=new AtomicInteger(0);
        doAnswer(call->{DataPacket packet=call.getArgument(0);
            if(packet instanceof InventorySlotPacket slot&&slot.inventoryId==1&&slot.slot==0){
                viewedCount.set(slot.item.isNull()?0:slot.item.getCount());provisional.incrementAndGet();
            } else if(packet instanceof InventoryContentPacket contents&&contents.inventoryId==1){
                viewedCount.set(contents.slots.length==0||contents.slots[0].isNull()?0:contents.slots[0].getCount());resyncs.incrementAndGet();
            }
            return true;
        }).when(f.player).dataPacket(any(DataPacket.class));
        doAnswer(call->{call.callRealMethod();if(changed.compareAndSet(false,true))inventory.setMaxStackSize(1);return null;})
                .when(inventory).onSlotChange(eq(0),any(Item.class),eq(true));
        assertFalse(f.first.pickupItems(AREA));assertTrue(changed.get());assertTrue(provisional.get()>0);
        assertFalse(source.isClosed());assertEquals(5,f.total());assertTrue(inventory.getContents().isEmpty());
        assertEquals(64,inventory.getMaxStackSize());assertTrue(resyncs.get()>0);
        assertEquals(0,viewedCount.get(),"Actual native packet stream must agree with restored physical slots");
    }
    private static void field(Class<?> owner,Object target,String name,Object value) throws Exception {
        Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private static final class Fixture {
        final Level level=mock(Level.class);
        final Map<Long,Entity> chunkEntities=new LinkedHashMap<>();
        final Map<Long,Entity> levelEntities=new Long2ObjectNonBlockingMap<>();
        final BaseFullChunk chunk=mock(BaseFullChunk.class);
        final Player player=mock(Player.class,CALLS_REAL_METHODS);
        final PlayerInventory main=new PlayerInventory(player);
        final BlockEntityHopper first,second;
        final byte[] opaque;
        Fixture(Server server) throws Exception {
            Item authored=Item.get(Item.DIAMOND,0,1);authored.setNamedTag(new CompoundTag().putString("opaque","published-native-pickup").putByteArray("bytes",new byte[]{-1,0,71}));opaque=authored.getCompoundTag().clone();
            LevelProvider provider=mock(LevelProvider.class);when(provider.getLevel()).thenReturn(level);
            when(level.getServer()).thenReturn(server);when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
            when(level.getEntity(anyLong())).thenAnswer(call->levelEntities.get(call.getArgument(0)));
            when(level.getEntities()).thenAnswer(call->levelEntities.values().toArray(Entity[]::new));
            when(level.getChunk(0,0,true)).thenReturn(chunk);when(level.getChunk(0,0)).thenReturn(chunk);
            when(level.getChunkIfLoaded(0,0)).thenReturn(chunk);when(chunk.getProvider()).thenReturn(provider);
            when(chunk.getEntities()).thenReturn(chunkEntities);when(chunk.hasPickupEntities(anyBoolean())).thenReturn(true);
            field(Level.class,level,"entities",levelEntities);field(Level.class,level,"updateEntities",new Long2ObjectNonBlockingMap<Entity>());
            doAnswer(call->{Entity entity=call.getArgument(0);levelEntities.put(entity.getId(),entity);return null;}).when(level).addEntity(any(Entity.class));
            doAnswer(call->{Entity entity=call.getArgument(0);levelEntities.remove(entity.getId(),entity);return null;}).when(level).removeEntity(any(Entity.class));
            doAnswer(call->{Entity entity=call.getArgument(0);chunkEntities.put(entity.getId(),entity);return null;}).when(chunk).addEntity(any(Entity.class));
            doAnswer(call->{Entity entity=call.getArgument(0);chunkEntities.remove(entity.getId(),entity);return null;}).when(chunk).removeEntity(any(Entity.class));
            field(Entity.class,player,"server",server);field(EntityHumanType.class,player,"inventory",main);
            field(Player.class,player,"usedChunks",new HashMap<>(Map.of(Level.chunkHash(0,0),true)));
            player.level=level;player.spawned=true;player.protocol=GameVersion.V1_20_0.getProtocol();
            doReturn(true).when(player).isAlive();doReturn(true).when(player).isOnline();doReturn(false).when(player).isCreative();doReturn(false).when(player).isSpectator();
            doReturn(GameVersion.V1_20_0).when(player).getGameVersion();doReturn(Map.of()).when(player).getViewers();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));doReturn(false).when(player).awardAchievement(anyString());
            first=new BlockEntityHopper(chunk,new CompoundTag().putString("id",BlockEntity.HOPPER).putInt("x",0).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));
            second=new BlockEntityHopper(chunk,new CompoundTag().putString("id",BlockEntity.HOPPER).putInt("x",1).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));
        }
        Item stack(int amount) {return Item.get(Item.DIAMOND,0,amount,opaque.clone());}
        EntityItem item(int amount) {
            EntityItem value=new EntityItem(chunk,Entity.getDefaultNBT(new Vector3(.2,64.2,.2)).putShort("Health",5).putShort("PickupDelay",0).putCompound("Item",NBTIO.putItemHelper(stack(amount))));
            assertFalse(value.isClosed());assertSame(value,levelEntities.get(value.getId()));assertSame(value,chunkEntities.get(value.getId()));return value;
        }
        int hopperCount(BlockEntityHopper hopper) {return hopper.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum();}
        int total() {
            List<Item> items=new ArrayList<>(main.getContents().values());items.addAll(first.getInventory().getContents().values());items.addAll(second.getInventory().getContents().values());
            levelEntities.values().stream().filter(e->!e.isClosed()).filter(EntityItem.class::isInstance).map(EntityItem.class::cast).map(EntityItem::getItem).forEach(items::add);
            for(Item item:items){assertEquals(Item.DIAMOND,item.getId());assertArrayEquals(opaque,item.getCompoundTag());}
            return items.stream().mapToInt(Item::getCount).sum();
        }
        int mergeTick(EntityItem source,EntityItem other) throws Exception {
            Block air=Block.get(Item.AIR);air.level=level;
            when(level.getBlock(anyInt(),anyInt(),anyInt())).thenReturn(air);when(level.getBlock(any(Vector3.class))).thenReturn(air);
            when(level.getBlock(any(Vector3.class),anyInt())).thenReturn(air);
            when(level.getBlock(any(FullChunk.class),anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean())).thenReturn(air);
            when(level.getNearbyEntities(any(AxisAlignedBB.class),same(source),eq(false))).thenReturn(new Entity[]{other});
            source.age=199;source.onGround=true;other.onGround=true;
            Field field=Entity.class.getDeclaredField("lastUpdate");field.setAccessible(true);return field.getInt(source)+1;
        }
    }
}
