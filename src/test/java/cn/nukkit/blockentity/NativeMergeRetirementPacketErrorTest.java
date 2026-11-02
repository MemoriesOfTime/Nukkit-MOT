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
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.inventory.HopperInventory;
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
class NativeMergeRetirementPacketErrorTest {
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
    @Test void aNativeDonorRemovePacketErrorDoesNotLoseAcceptedMergeQuantityOrLeaveClaimsHeld() throws Exception {
        EntityItem receiver=f.item(5),donor=f.item(3);int tick=f.mergeTick(receiver,donor);
        donor.getViewers().put(f.player.getLoaderId(),f.player);
        AssertionError failure=new AssertionError("native donor remove packet sink");AtomicInteger removePackets=new AtomicInteger();
        doAnswer(call->{if(call.getArgument(0) instanceof cn.nukkit.network.protocol.RemoveEntityPacket){removePackets.incrementAndGet();throw failure;}return true;})
                .when(f.player).dataPacket(any(DataPacket.class));
        assertSame(failure,assertThrows(AssertionError.class,()->receiver.onUpdate(tick)));assertEquals(1,removePackets.get());
        assertTrue(donor.isClosed());assertFalse(receiver.isClosed());assertNull(f.levelEntities.get(donor.getId()));
        assertSame(receiver,f.levelEntities.get(receiver.getId()));assertEquals(8,receiver.getItem().getCount());assertEquals(8,f.total());
        assertTrue(f.player.pickupEntity(receiver,true),"Both source claims release after native retirement packet failure");
        assertEquals(8,f.main.getItem(0).getCount());assertEquals(8,f.total());
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
