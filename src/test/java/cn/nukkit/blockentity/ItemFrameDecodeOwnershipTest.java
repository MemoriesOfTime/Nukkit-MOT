package cn.nukkit.blockentity;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ItemFrameDecodeOwnershipTest {
 @BeforeAll static void boot(){MockServer.init();}
 @Test void decodedItemCannotRenameOrMutatePersistedPayload(){
  FullChunk chunk=mock(FullChunk.class);LevelProvider provider=mock(LevelProvider.class);Level level=mock(Level.class);
  when(chunk.getProvider()).thenReturn(provider);when(provider.getLevel()).thenReturn(level);when(level.getServer()).thenReturn(MockServer.get());
  Item item=Item.get(Item.DIAMOND,0,7);item.setNamedTag(new CompoundTag().putString("opaque","frame-data").putCompound("nested",new CompoundTag().putInt("receipt",51)));
  CompoundTag source=new CompoundTag().putInt("x",2).putInt("y",64).putInt("z",3).putCompound("Item",NBTIO.putItemHelper(item));
  BlockEntityItemFrame frame=new BlockEntityItemFrame(chunk,source);CompoundTag before=source.copy();Item decoded=frame.getItem();
  assertEquals(item.getId(),decoded.getId());assertEquals(7,decoded.getCount());assertEquals("frame-data",decoded.getNamedTag().getString("opaque"));
  assertEquals(before,source,"Decoding must not rename the stored nested item tag");
  decoded.getNamedTag().getCompound("nested").putInt("receipt",99);assertEquals(before,source,"Decoded mutable NBT belongs to the item, not saved frame state");
  assertSame(decoded,frame.getItem(),"Existing item cache semantics remain unchanged");
 }
 @Test void emptyFrameRetainsAirAndExistingDefaults(){
  FullChunk chunk=mock(FullChunk.class);LevelProvider provider=mock(LevelProvider.class);Level level=mock(Level.class);
  when(chunk.getProvider()).thenReturn(provider);when(provider.getLevel()).thenReturn(level);when(level.getServer()).thenReturn(MockServer.get());
  BlockEntityItemFrame frame=new BlockEntityItemFrame(chunk,new CompoundTag());CompoundTag before=frame.namedTag.copy();
  assertTrue(frame.getItem().isNull());assertEquals(before,frame.namedTag);assertEquals(1,frame.getItemDropChance());assertEquals(0,frame.getItemRotation());
 }
}
