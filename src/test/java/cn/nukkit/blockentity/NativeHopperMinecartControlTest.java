package cn.nukkit.blockentity;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityMinecartHopper;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class NativeHopperMinecartControlTest {
 @BeforeAll static void init(){MockServer.init();}
 record Context(Level level,FullChunk chunk,BlockEntityHopper hopper,EntityMinecartHopper cart){}
 Context make() throws ReflectiveOperationException {MockServer.reset();Server server=Server.getInstance();Level level=mock(Level.class);var update=Level.class.getDeclaredField("updateEntities");update.setAccessible(true);update.set(level,new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<cn.nukkit.entity.Entity>());when(level.getServer()).thenReturn(server);when(level.getChunkPlayers(anyInt(),anyInt())).thenReturn(java.util.Collections.emptyMap());when(level.getGameRules()).thenReturn(GameRules.getDefault());cn.nukkit.level.format.generic.BaseFullChunk chunk=mock(cn.nukkit.level.format.generic.BaseFullChunk.class);var provider=mock(LevelProvider.class);when(chunk.getProvider()).thenReturn(provider);when(provider.getLevel()).thenReturn(level);when(level.getChunk(anyInt(),anyInt())).thenReturn(chunk);var hopper=new BlockEntityHopper(chunk,new CompoundTag().putString("id",BlockEntity.HOPPER).putInt("x",0).putInt("y",64).putInt("z",0).putList(new ListTag<CompoundTag>("Items")));var cart=new EntityMinecartHopper(chunk,Entity.getDefaultNBT(new Vector3(0,65,0)));return new Context(level,chunk,hopper,cart);}
 @Test void actualMinecartConstructorAndHopperPushControl() throws ReflectiveOperationException {var c=make();assertTrue(c.cart.isAlive());assertTrue(c.hopper.getInventory().setItem(0,Item.get(Item.DIAMOND,0,5)));c.hopper.setMinecartInvPushTo(c.cart);assertTrue(c.hopper.pushItemsIntoMinecart());assertEquals(4,c.hopper.getInventory().getItem(0).getCount());assertEquals(1,c.cart.getInventory().getItem(0).getCount());}
 @Test void actualMinecartHopperPullControl() throws ReflectiveOperationException {var c=make();assertTrue(c.cart.getInventory().setItem(0,Item.get(Item.DIAMOND,0,5)));c.hopper.setMinecartInvPickupFrom(c.cart);assertTrue(c.hopper.pullItemsFromMinecart());assertEquals(4,c.cart.getInventory().getItem(0).getCount());assertEquals(1,c.hopper.getInventory().getItem(0).getCount());}
}
