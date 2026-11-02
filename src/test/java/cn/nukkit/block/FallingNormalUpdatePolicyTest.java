package cn.nukkit.block;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.event.block.BlockFallEvent;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class FallingNormalUpdatePolicyTest {
 @BeforeAll static void bootstrap(){MockServer.init();}
 static final class Gravel extends BlockGravel {
  Block support;EntityFallingBlock spawned;
  @Override public Block down(){return support;}
  @Override protected EntityFallingBlock createFallingEntity(CompoundTag tag){return spawned;}
 }
 static final class Sand extends BlockSand {
  Block support;EntityFallingBlock spawned;
  @Override public Block down(){return support;}
  @Override protected EntityFallingBlock createFallingEntity(CompoundTag tag){return spawned;}
 }
 private void update(boolean metadata,boolean randomTicks,boolean cancel,int type){
  Level level=mock(Level.class);Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);
  when(level.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(plugins);when(level.randomTickingEnabled()).thenReturn(randomTicks);
  doAnswer(call->{if(cancel)((BlockFallEvent)call.getArgument(0)).setCancelled(true);return null;}).when(plugins).callEvent(any(BlockFallEvent.class));
  EntityFallingBlock falling=mock(EntityFallingBlock.class);Block source;
  if(metadata){Sand b=new Sand();b.support=Block.get(Block.AIR);b.spawned=falling;source=b;}
  else {Gravel b=new Gravel();b.support=Block.get(Block.AIR);b.spawned=falling;source=b;}
  source.level=level;assertEquals(type,source.onUpdate(type));boolean normal=type==Level.BLOCK_UPDATE_NORMAL;
  verify(plugins,normal?times(1):never()).callEvent(any(BlockFallEvent.class));
  verify(level,normal&&!cancel?times(1):never()).setBlock(eq(source),any(Block.class),eq(true),eq(true));
  verify(falling,normal&&!cancel?times(1):never()).spawnToAll();
 }
 @Test void normalGravelGravityWithRandomTicksDisabled(){update(false,false,false,Level.BLOCK_UPDATE_NORMAL);}
 @Test void normalSandGravityWithRandomTicksDisabled(){update(true,false,false,Level.BLOCK_UPDATE_NORMAL);}
 @Test void normalGravityRetainsEnabledBehavior(){update(false,true,false,Level.BLOCK_UPDATE_NORMAL);update(true,true,false,Level.BLOCK_UPDATE_NORMAL);}
 @Test void cancellationRetainsBothSourceBlocks(){update(false,false,true,Level.BLOCK_UPDATE_NORMAL);update(true,false,true,Level.BLOCK_UPDATE_NORMAL);}
 @Test void randomUpdateDoesNotBecomeGravity(){update(false,false,false,Level.BLOCK_UPDATE_RANDOM);update(true,true,false,Level.BLOCK_UPDATE_RANDOM);}
}
