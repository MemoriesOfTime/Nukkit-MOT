package cn.nukkit.item;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFirework;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ItemFireworkGroundShooterTest {
 @BeforeAll static void bootstrap(){MockServer.init();}
 static final class CapturingFirework extends ItemFirework {
  Entity actor;Vector3 position;Vector3 velocity;int launches;
  @Override public EntityFirework spawnFirework(Level level,Vector3 pos,Vector3 motion,Entity shootingEntity){
   actor=shootingEntity;position=pos;velocity=motion;launches++;return null;
  }
 }
 private void activate(boolean creative){
  Level level=mock(Level.class);Player player=mock(Player.class);PlayerInventory inventory=mock(PlayerInventory.class);
  when(player.isCreative()).thenReturn(creative);when(player.getInventory()).thenReturn(inventory);when(inventory.getHeldItemIndex()).thenReturn(4);
  Block block=mock(Block.class);when(block.canPassThrough()).thenReturn(true);CapturingFirework firework=new CapturingFirework();
  assertTrue(firework.onActivate(level,player,block,block,BlockFace.UP,0,0,0));
  assertSame(player,firework.actor,"The accepted ground launch retains the player for damage attribution and protection checks");
  assertSame(block,firework.position);assertNull(firework.velocity,"Ground launch is not a crossbow projectile");assertEquals(1,firework.launches);
  verify(inventory,creative?never():times(1)).decreaseCount(4);
 }
 @Test void survivalLaunchRetainsActorAndConsumesOnce(){activate(false);}
 @Test void creativeLaunchRetainsActorWithoutConsumption(){activate(true);}
 @Test void solidBlockRejectsLaunchAndConsumption(){
  Player player=mock(Player.class);Block block=mock(Block.class);CapturingFirework firework=new CapturingFirework();
  assertFalse(firework.onActivate(mock(Level.class),player,block,block,BlockFace.UP,0,0,0));assertEquals(0,firework.launches);verifyNoInteractions(player);
 }
 @Test void explicitShooterlessPublicLaunchStillWorks(){
  CapturingFirework firework=new CapturingFirework();Vector3 position=new Vector3(1,2,3);firework.spawnFirework(mock(Level.class),position);
  assertEquals(1,firework.launches);assertSame(position,firework.position);assertNull(firework.actor);assertNull(firework.velocity);
 }

 @Test void groundActivationKeepsTheTwoArgumentPluginHook() {
  ItemFirework item=new ItemFirework(){
   @Override public EntityFirework spawnFirework(Level level,Vector3 pos){twoArgumentCalls++;return null;}
   @Override public EntityFirework spawnFirework(Level level,Vector3 pos,Vector3 motion,Entity actor){return null;}
  };
  Player player=mock(Player.class);when(player.isCreative()).thenReturn(true);
  Block block=mock(Block.class);when(block.canPassThrough()).thenReturn(true);
  twoArgumentCalls=0;assertTrue(item.onActivate(mock(Level.class),player,block,block,BlockFace.UP,0,0,0));
  assertEquals(1,twoArgumentCalls,"accepted ground launches retain the original virtual hook");
 }
 static int twoArgumentCalls;
 @Test void groundActivationKeepsTheThreeArgumentPluginHook() {
  ItemFirework item=new ItemFirework(){
   @Override public EntityFirework spawnFirework(Level level,Vector3 pos,Vector3 motion){threeArgumentCalls++;return null;}
   @Override public EntityFirework spawnFirework(Level level,Vector3 pos,Vector3 motion,Entity actor){return null;}
  };
  Player player=mock(Player.class);when(player.isCreative()).thenReturn(true);
  Block block=mock(Block.class);when(block.canPassThrough()).thenReturn(true);
  threeArgumentCalls=0;assertTrue(item.onActivate(mock(Level.class),player,block,block,BlockFace.UP,0,0,0));
  assertEquals(1,threeArgumentCalls,"two-argument base dispatch retains the motion hook");
 }
 static int threeArgumentCalls;

 @Test void nestedLaunchesRestoreTheOuterActorAndOtherItemsStayShooterless(){
  Level level=mock(Level.class);Block launchBlock=mock(Block.class);when(launchBlock.canPassThrough()).thenReturn(true);
  Player outer=mock(Player.class),inner=mock(Player.class);when(outer.isCreative()).thenReturn(true);when(inner.isCreative()).thenReturn(true);
  java.util.List<Entity> actors=new java.util.ArrayList<>();CapturingFirework other=new CapturingFirework();
  ItemFirework item=new ItemFirework(){
   boolean nested;
   @Override public EntityFirework spawnFirework(Level world,Vector3 pos,Vector3 motion,Entity actor){
    actors.add(actor);
    if(actor==outer&&!nested){nested=true;other.spawnFirework(world,pos);
     assertTrue(this.onActivate(world,inner,launchBlock,launchBlock,BlockFace.UP,0,0,0));
     this.spawnFirework(world,pos);
    }
    return null;
   }
  };
  assertTrue(item.onActivate(level,outer,launchBlock,launchBlock,BlockFace.UP,0,0,0));item.spawnFirework(level,launchBlock);
  assertEquals(java.util.Arrays.asList(outer,inner,outer,null),actors);assertNull(other.actor);
 }
 @Test void exceptionalPluginLaunchCannotLeakItsActorToTheNextPublicLaunch(){
  Level level=mock(Level.class);Block launchBlock=mock(Block.class);when(launchBlock.canPassThrough()).thenReturn(true);
  Player player=mock(Player.class);java.util.List<Entity> actors=new java.util.ArrayList<>();
  ItemFirework item=new ItemFirework(){
   @Override public EntityFirework spawnFirework(Level world,Vector3 pos,Vector3 motion,Entity actor){
    actors.add(actor);if(actors.size()==1)throw new IllegalStateException("plugin refusal");return null;
   }
  };
  assertThrows(IllegalStateException.class,()->item.onActivate(level,player,launchBlock,launchBlock,BlockFace.UP,0,0,0));
  item.spawnFirework(level,launchBlock);assertEquals(java.util.Arrays.asList(player,null),actors);
 }
}
