package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockComposter;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.ComposterEmptyEvent;
import cn.nukkit.event.block.ComposterFillEvent;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.item.Item;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.anvil.ChunkSection;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual inventories/events; acceptance-witness controls also run actual Level.setBlock and Anvil chunk/storage. */
@org.junit.jupiter.api.parallel.Isolated
@SuppressWarnings("try")
public class NativeComposterActorOwnershipTest {
    private static Server originalServer;
    @org.junit.jupiter.api.BeforeAll static void rememberServer() { originalServer = Server.getInstance(); }
    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        var field = Server.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, originalServer);
    }
    private Server server;
    private PluginManager previous;
    private BaseLang previousLanguage;
    private final List<Registration> registrations = new ArrayList<>();
    private record Registration(HandlerList handlers, RegisteredListener listener) { }
    @BeforeEach void setup() {
        MockServer.init(); server = Server.getInstance(); previous = server.getPluginManager(); previousLanguage = server.getLanguage();
        when(server.getPluginManager()).thenReturn(new PluginManager(server, null)); when(server.getLanguage()).thenReturn(new BaseLang("eng"));
    }
    @AfterEach void cleanup() {
        registrations.forEach(r -> r.handlers().unregister(r.listener()));
        when(server.getPluginManager()).thenReturn(previous); when(server.getLanguage()).thenReturn(previousLanguage);
    }
    private <T extends Event> void listen(HandlerList handlers, Class<T> type, Consumer<T> body) {
        Plugin plugin = mock(Plugin.class); when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDescription()).thenReturn(new PluginDescription(Map.of("name","composter-atomic-control","version","1","main","test.Main","api",List.of("1.0.0"))));
        RegisteredListener registration = new RegisteredListener(new Listener() {}, (l,e) -> body.accept(type.cast(e)), EventPriority.NORMAL, plugin, false);
        handlers.register(registration); registrations.add(new Registration(handlers, registration));
    }
    private void fill(Consumer<ComposterFillEvent> body) { listen(ComposterFillEvent.getHandlers(), ComposterFillEvent.class, body); }
    private void empty(Consumer<ComposterEmptyEvent> body) { listen(ComposterEmptyEvent.getHandlers(), ComposterEmptyEvent.class, body); }
    private void move(Consumer<InventoryMoveItemEvent> body) { listen(InventoryMoveItemEvent.getHandlers(), InventoryMoveItemEvent.class, body); }

    private void canonical(Fixture f) {
        doReturn(f.actual).when(f.level).getChunk(0,0,false);
        doCallRealMethod().when(f.level).getBlockEntity(any(Vector3.class));
        doCallRealMethod().when(f.level).getBlockEntity(nullable(FullChunk.class),any(Vector3.class));
        assertSame(f.destination,f.level.getBlockEntity(f.destination));
    }
    @Test void detachedReadyDestinationWithoutReplacementIsRefused() throws Exception {
        Fixture f=new Fixture(true);canonical(f);f.ready();
        empty(event->{f.actual.removeBlockEntity(f.destination);assertFalse(f.destination.closed);assertNull(f.level.getBlockEntityIfLoaded(f.destination));});
        assertFalse(f.destination.pullItems());assertEquals(8,f.damage());assertEquals(0,f.meals());
    }
    @Test void destinationDetachedInsideMoveEventDoesNotConsumeReadySource() throws Exception {
        Fixture f=new Fixture(true);canonical(f);f.ready();
        move(event->{f.actual.removeBlockEntity(f.destination);assertFalse(f.destination.closed);});
        assertFalse(f.destination.pullItems());assertEquals(8,f.damage());assertEquals(0,f.meals());
    }
    @Test void detachedFillSourceCannotAdvanceComposter() throws Exception {
        Fixture f=new Fixture(true);canonical(f);
        fill(event->{f.actual.removeBlockEntity(f.source);assertFalse(f.source.closed);assertNull(f.level.getBlockEntityIfLoaded(f.source));});
        assertFalse(f.source.pushItems());assertEquals(7,f.damage());assertEquals(5,f.pies());f.sourceExact();
    }
    @Test void canonicalMinecartReadyConversionUsesNativeWorldAndEntityRegistries() throws Exception {
        Fixture f=new Fixture(true);canonical(f);f.ready();var cart=cart(f);
        assertTrue(cart.pullItems(null,f.composter(8)));assertEquals(0,f.damage());assertEquals(1,cart.getInventory().getItem(0).getCount());
    }
    @Test void detachedMinecartDuringEmptyEventCannotConsumeReadySource() throws Exception {
        Fixture f=new Fixture(true);canonical(f);f.ready();var cart=cart(f);
        empty(event->{f.actual.removeEntity(cart);assertFalse(cart.closed);assertSame(cart,f.level.getEntity(cart.getId()));});
        assertFalse(cart.pullItems(null,f.composter(8)));assertEquals(8,f.damage());assertTrue(cart.getInventory().getContents().isEmpty());
    }
    @Test void unregisteredLiveMinecartDuringEmptyEventCannotConsumeReadySource() throws Exception {
        Fixture f=new Fixture(true);canonical(f);f.ready();var cart=cart(f);
        empty(event->{try{java.lang.reflect.Field field=Level.class.getDeclaredField("entities");field.setAccessible(true);((cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<?>)field.get(f.level)).remove(cart.getId());}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}assertFalse(cart.closed);assertSame(cart,f.actual.getEntities().get(cart.getId()));});
        assertFalse(cart.pullItems(null,f.composter(8)));assertEquals(8,f.damage());assertTrue(cart.getInventory().getContents().isEmpty());
    }
    private cn.nukkit.entity.item.EntityMinecartHopper cart(Fixture f) throws Exception {
        field(Level.class,f.level,"entities",new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<cn.nukkit.entity.Entity>());
        field(Level.class,f.level,"updateEntities",new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<cn.nukkit.entity.Entity>());
        when(f.level.getGameRules()).thenReturn(cn.nukkit.level.GameRules.getDefault());
        doCallRealMethod().when(f.level).addEntity(any(cn.nukkit.entity.Entity.class));
        doCallRealMethod().when(f.level).getEntity(anyLong());
        doReturn(f.actual).when(f.level).getChunk(0,0);
        var cart=new cn.nukkit.entity.item.EntityMinecartHopper(f.actual,cn.nukkit.entity.Entity.getDefaultNBT(new Vector3(0,62,0)));
        assertSame(cart,f.actual.getEntities().get(cart.getId()));assertSame(cart,f.level.getEntity(cart.getId()));assertTrue(cart.isAlive());return cart;
    }
    private final class Fixture {
        final Level level=mock(Level.class);final FullChunk chunk;final Chunk actual;
        final AtomicReference<BlockComposter> world=new AtomicReference<>();final BlockEntityHopper source,destination;final byte[] opaque;
        Fixture(boolean nativeWorld) throws Exception {
            LevelProvider provider=mock(LevelProvider.class);when(provider.getLevel()).thenReturn(level);when(provider.getMaximumLayer()).thenReturn(1);when(provider.getMinBlockY()).thenReturn(0);
            when(level.getServer()).thenReturn(server);when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(Map.of());
            if(nativeWorld){
                actual=new Chunk(provider);ChunkSection section=new ChunkSection(3);section.setBlock(0,15,0,Block.COMPOSTER,7);actual.setSection(3,section);chunk=actual;
                when(level.requireProvider()).thenReturn(provider);when(level.isYInRange(anyInt())).thenReturn(true);doReturn(actual).when(level).getChunk(0,0,true);
                when(level.getChunkLoaders(anyInt(),anyInt())).thenReturn(new ChunkLoader[0]);
                when(level.getNearbyEntities(any(cn.nukkit.math.AxisAlignedBB.class))).thenReturn(new cn.nukkit.entity.Entity[0]);
                when(level.getBlock(anyInt(),anyInt(),anyInt(),eq(1))).thenReturn(Block.get(Block.AIR));
                field(Level.class,level,"server",server);field(Level.class,level,"callbackBlockSet",new Int2ObjectOpenHashMap<BiConsumer<Block,Block>>());
                doCallRealMethod().when(level).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean());
                doCallRealMethod().when(level).setBlock(anyInt(),anyInt(),anyInt(),anyInt(),any(Block.class),anyBoolean(),anyBoolean());
                doCallRealMethod().when(level).setBlock(any(Vector3.class),eq(0),any(Block.class),anyBoolean(),anyBoolean());
                doCallRealMethod().when(level).setBlock(any(Vector3.class),any(Block.class),anyBoolean(),anyBoolean(),any(Runnable.class));
            }else{
                actual=null;chunk=mock(cn.nukkit.level.format.generic.BaseFullChunk.class);when(chunk.getProvider()).thenReturn(provider);world.set(composter(7));
                doAnswer(call->{world.set((BlockComposter)((BlockComposter)call.getArgument(1)).clone());return true;}).when(level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true));
                doAnswer(call->{boolean accepted=level.setBlock(call.getArgument(0),call.getArgument(1),true,true);if(accepted)((Runnable)call.getArgument(4)).run();return accepted;}).when(level).setBlock(any(Vector3.class),any(Block.class),eq(true),eq(true),any(Runnable.class));
            }
            doReturn(chunk).when(level).getChunkIfLoaded(0,0);
            when(level.isYInRange(anyInt())).thenReturn(true);
            doCallRealMethod().when(level).getBlockEntityIfLoaded(any(Vector3.class));
            doCallRealMethod().when(level).getBlockEntityIfLoaded(nullable(FullChunk.class),any(Vector3.class));
            source=new BlockEntityHopper(chunk,tag(BlockEntity.HOPPER,64));destination=new BlockEntityHopper(chunk,tag(BlockEntity.HOPPER,62));
            when(level.getBlockDataAt(0,64,0)).thenReturn(0);
            when(level.getBlock(any(Vector3.class))).thenAnswer(call->{Vector3 at=call.getArgument(0);return at.y==63?composter(damage()):Block.get(Block.HOPPER_BLOCK);});
            when(level.getBlock(any(Vector3.class),eq(1))).thenReturn(Block.get(Block.AIR));
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call->{Vector3 at=call.getArgument(0);return at.y==64?source:at.y==62?destination:null;});
            Item pies=Item.get(Item.PUMPKIN_PIE,0,5);pies.setNamedTag(new CompoundTag().putString("opaque","native-atomic-composter").putByteArray("bytes",new byte[]{-1,0,49}));opaque=pies.getCompoundTag().clone();assertTrue(source.getInventory().setItem(0,pies));
        }
        BlockComposter composter(int damage){BlockComposter b=new BlockComposter(damage);b.level=level;b.x=0;b.y=63;b.z=0;return b;}
        void ready(){if(actual==null)world.set(composter(8));else actual.getAndSetBlock(0,63,0,composter(8));}
        int damage(){return actual==null?world.get().getDamage():actual.getBlockData(0,63,0);}
        int pies(){return source.getInventory().getContents().values().stream().filter(i->i.getId()==Item.PUMPKIN_PIE).mapToInt(Item::getCount).sum();}
        int meals(){return destination.getInventory().getContents().values().stream().filter(i->i.getId()==Item.DYE&&i.getDamage()==15).mapToInt(Item::getCount).sum();}
        void sourceExact(){for(Item i:source.getInventory().getContents().values())if(i.getId()==Item.PUMPKIN_PIE)assertArrayEquals(opaque,i.getCompoundTag());}
        @SuppressWarnings("unchecked") Map<Integer,Item> raw(BaseInventory inv)throws Exception{Field slots=BaseInventory.class.getDeclaredField("slots");slots.setAccessible(true);return(Map<Integer,Item>)slots.get(inv);}
        void hidden(BaseInventory inv)throws Exception{Item i=Item.get(Item.EMERALD,0,3);i.setNamedTag(new CompoundTag().putString("opaque","hidden"));raw(inv).put(-7,i.clone());raw(inv).put(29,i.clone());}
        Map<Integer,Item> captureRaw(BaseInventory inventory)throws Exception{Map<Integer,Item> result=new java.util.HashMap<>();raw(inventory).forEach((slot,item)->result.put(slot,item.clone()));return result;}
        void rawEquals(Map<Integer,Item> before,BaseInventory inventory)throws Exception{Map<Integer,Item> after=raw(inventory);assertEquals(before.keySet(),after.keySet());before.forEach((slot,item)->assertTrue(item.equalsExact(after.get(slot))));}
    }
    private static CompoundTag tag(String type,int y){return new CompoundTag().putString("id",type).putInt("x",0).putInt("y",y).putInt("z",0).putList(new ListTag<CompoundTag>("Items"));}
    private static void field(Class<?> owner,Object target,String name,Object value)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
