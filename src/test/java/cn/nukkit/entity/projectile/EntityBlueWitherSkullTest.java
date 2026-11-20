package cn.nukkit.entity.projectile;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityBlueWitherSkullTest {

 @BeforeAll static void boot(){MockServer.init();Effect.init();}

 @Nested
 class EntityImpact {
  private void hit(boolean cancel,boolean accepted,boolean enabled,boolean blockRule) throws Exception{
   EntityBlueWitherSkull skull=mock(EntityBlueWitherSkull.class,CALLS_REAL_METHODS);Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);
   when(server.getPluginManager()).thenReturn(plugins);Field field=Entity.class.getDeclaredField("server");field.setAccessible(true);field.set(skull,server);
   Level level=mock(Level.class);GameRules rules=GameRules.getDefault();rules.setGameRule(GameRule.PROJECTILES_CAN_BREAK_BLOCKS,blockRule);when(level.getGameRules()).thenReturn(rules);skull.level=level;
   doReturn(5).when(skull).getResultDamage();doNothing().when(skull).explode();doNothing().when(skull).close();skull.setExplode(enabled);
   doAnswer(call->{if(cancel&&call.getArgument(0) instanceof ProjectileHitEvent hit)hit.setCancelled();return null;}).when(plugins).callEvent(any(Event.class));
   Entity victim=mock(Entity.class);when(victim.attack(any(EntityDamageEvent.class))).thenReturn(accepted);skull.onCollideWithEntity(victim);
   boolean shouldExplode=!cancel&&accepted&&enabled&&blockRule;verify(skull,shouldExplode?times(1):never()).explode();
   if(shouldExplode){var order=inOrder(skull);order.verify(skull).explode();order.verify(skull).close();}
  }
  @Test void acceptedEnabledImpactExplodesBeforeProjectileRetirement() throws Exception{hit(false,true,true,true);}
  @Test void cancelledHitCannotExplode() throws Exception{hit(true,true,true,true);}
  @Test void rejectedDamageCannotExplode() throws Exception{hit(false,false,true,true);}
  @Test void explosionAndGameRuleControlsRemainEffective() throws Exception{hit(false,true,false,true);hit(false,true,true,false);}
 }
 @Nested
 class SweptImpactPosition {
  @Test void acceptedSweptHitExplodesAtTheNearestIntersectionNotTheTickOrigin() throws Exception {
   Fixture f=new Fixture(false,true);f.skull.onUpdate(1);
   assertNotNull(f.explosion.get());assertEquals(4.7,f.explosion.get().x,1e-9);
   assertEquals(64,f.explosion.get().y,1e-9);assertEquals(0,f.explosion.get().z,1e-9);
   verify(f.near).attack(any(EntityDamageEvent.class));verify(f.far,never()).attack(any(EntityDamageEvent.class));
  }
  @Test void cancelledSweptHitDoesNotRepositionOrExplode() throws Exception {
   Fixture f=new Fixture(true,true);f.skull.onUpdate(1);
   assertNull(f.explosion.get());verify(f.skull,never()).setPosition(any(Vector3.class));
   verify(f.near,never()).attack(any(EntityDamageEvent.class));
  }
  @Test void rejectedDamageDoesNotRepositionOrExplode() throws Exception {
   Fixture f=new Fixture(false,false);f.skull.onUpdate(1);
   assertNull(f.explosion.get());verify(f.skull,never()).setPosition(any(Vector3.class));
  }
  @Test void aLaterManualHitDoesNotReuseTheCompletedSweptIntersection() throws Exception {
   Fixture f=new Fixture(true,true);f.skull.onUpdate(1);f.cancelHits=false;
   f.skull.onCollideWithEntity(f.near);
   assertNotNull(f.explosion.get());assertEquals(0,f.explosion.get().x,1e-9);
   verify(f.skull,never()).setPosition(any(Vector3.class));
  }
  private static final class Fixture {
   final EntityBlueWitherSkull skull=mock(EntityBlueWitherSkull.class,CALLS_REAL_METHODS);
   final Entity near=mock(Entity.class),far=mock(Entity.class);
   final AtomicReference<Vector3> explosion=new AtomicReference<>();
   boolean cancelHits;
   Fixture(boolean cancel,boolean accepted)throws Exception{
    this.cancelHits=cancel;
    Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);
    when(server.getPluginManager()).thenReturn(plugins);Field field=Entity.class.getDeclaredField("server");field.setAccessible(true);field.set(skull,server);
    Level level=mock(Level.class);GameRules rules=GameRules.getDefault();rules.setGameRule(GameRule.PROJECTILES_CAN_BREAK_BLOCKS,true);when(level.getGameRules()).thenReturn(rules);skull.level=level;
    skull.x=0;skull.y=64;skull.z=0;skull.age=1;skull.motionX=10;skull.boundingBox=new SimpleAxisAlignedBB(0,64,0,.3,64.3,.3);
    near.boundingBox=new SimpleAxisAlignedBB(5,63,-1,6,65,1);far.boundingBox=new SimpleAxisAlignedBB(9,63,-1,10,65,1);
    when(level.getCollidingEntities(any(),eq(skull))).thenReturn(new Entity[]{far,near});
    doReturn(false).when(skull).entityBaseTick(anyInt());doReturn(true).when(skull).isAlive();doReturn(5).when(skull).getResultDamage();
    doNothing().when(skull).updateMotion();doNothing().when(skull).updateRotation();doNothing().when(skull).updateMovement();doReturn(true).when(skull).move(anyDouble(),anyDouble(),anyDouble());
    doNothing().when(skull).close();
    doAnswer(call->{Vector3 p=call.getArgument(0);skull.x=p.x;skull.y=p.y;skull.z=p.z;return true;}).when(skull).setPosition(any(Vector3.class));
    doAnswer(call->{explosion.set(new Vector3(skull.x,skull.y,skull.z));skull.closed=true;return null;}).when(skull).explode();skull.setExplode(true);
    doAnswer(call->{if(cancelHits&&call.getArgument(0) instanceof ProjectileHitEvent hit)hit.setCancelled();return null;}).when(plugins).callEvent(any(Event.class));
    when(near.attack(any(EntityDamageEvent.class))).thenReturn(accepted);when(far.attack(any(EntityDamageEvent.class))).thenReturn(true);
   }
  }
 }
}
