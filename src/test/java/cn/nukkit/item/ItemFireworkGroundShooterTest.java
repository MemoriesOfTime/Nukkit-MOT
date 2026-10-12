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
}
