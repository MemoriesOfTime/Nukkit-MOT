package cn.nukkit.dispenser;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockDispenser;
import cn.nukkit.block.BlockRail;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TntMinecartDispenserPayloadTest {
 @BeforeAll static void bootstrap(){MockServer.init();}
 @Test void taggedTntPlacementStoresOneDetachedItemWithoutConsumingItsSource(){
  Item source=Item.get(Item.MINECART_WITH_TNT,0,3);
  source.setCustomName("Retained cart");source.setNamedTag(source.getOrCreateNamedTag().putString("custom_owner","Alice"));
  CompoundTag spawned=dispense(source,"MinecartTnt");
  assertTrue(spawned.contains("MinecartItem"),"dispenser-created TNT carts need the same durable item snapshot as hand placement");
  Item retained=NBTIO.getItemHelper(spawned.getCompound("MinecartItem"));
  assertEquals(1,retained.getCount());assertEquals(source.getId(),retained.getId());
  assertEquals("Retained cart",retained.getCustomName());assertEquals("Alice",retained.getNamedTag().getString("custom_owner"));
  assertEquals(3,source.getCount());source.setCustomName("changed later");
  assertEquals("Retained cart",NBTIO.getItemHelper(spawned.getCompound("MinecartItem")).getCustomName());
  assertEquals(3.5,spawned.getList("Pos",cn.nukkit.nbt.tag.DoubleTag.class).get(0).data);
 }
 @Test void otherMinecartKindsKeepTheirExistingPayload(){
  for(int id:new int[]{Item.MINECART,Item.MINECART_WITH_CHEST,Item.MINECART_WITH_HOPPER}){
   String kind=id==Item.MINECART?"MinecartRideable":id==Item.MINECART_WITH_CHEST?"MinecartChest":"MinecartHopper";
   assertFalse(dispense(Item.get(id),kind).contains("MinecartItem"));
  }
 }
 private CompoundTag dispense(Item item,String kind){
  BlockDispenser dispenser=mock(BlockDispenser.class);Level level=mock(Level.class);dispenser.level=level;
  BlockRail rail=new BlockRail();rail.x=3;rail.y=64;rail.z=5;
  when(dispenser.getSide(BlockFace.EAST)).thenReturn(rail);BaseFullChunk chunk=mock(BaseFullChunk.class);when(level.getChunk(0,0)).thenReturn(chunk);
  Entity cart=mock(Entity.class);AtomicReference<CompoundTag> payload=new AtomicReference<>();
  try(MockedStatic<Entity> factory=mockStatic(Entity.class)){
   factory.when(()->Entity.createEntity(eq(kind),eq(chunk),any(CompoundTag.class))).thenAnswer(call->{payload.set(call.getArgument(2));return cart;});
   assertNull(new MinecartDispenseBehavior().dispense(dispenser,BlockFace.EAST,item));verify(cart).spawnToAll();
  }
  return payload.get();
 }
}
