package cn.nukkit.entity.projectile;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.*;
import cn.nukkit.plugin.PluginManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class FireballCombustionEventTest {
 @BeforeAll static void boot(){MockServer.init();}
 private void collide(boolean ghast,boolean cancelHit,boolean acceptDamage,boolean cancelCombust,int duration) throws Exception {
  EntityProjectile projectile=ghast?mock(EntityGhastFireBall.class,CALLS_REAL_METHODS):mock(EntitySmallFireBall.class,CALLS_REAL_METHODS);
  Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);when(server.getPluginManager()).thenReturn(plugins);
  Field field=Entity.class.getDeclaredField("server");field.setAccessible(true);field.set(projectile,server);
  doReturn(5).when(projectile).getResultDamage();doNothing().when(projectile).close();Entity victim=mock(Entity.class);when(victim.attack(any(EntityDamageEvent.class))).thenReturn(acceptDamage);
  int[] combustion={0};doAnswer(call->{Event event=call.getArgument(0);
   if(event instanceof ProjectileHitEvent hit&&cancelHit)hit.setCancelled();
   if(event instanceof EntityCombustByEntityEvent combust){combustion[0]++;assertSame(projectile,combust.getCombuster());assertSame(victim,combust.getEntity());combust.setDuration(duration);if(cancelCombust)combust.setCancelled();}
   return null;
  }).when(plugins).callEvent(any(Event.class));
  projectile.onCollideWithEntity(victim);boolean accepted=!cancelHit&&(ghast||acceptDamage);
  assertEquals(accepted?1:0,combustion[0]);verify(victim,accepted&&!cancelCombust?times(1):never()).setOnFire(duration);
  if(cancelHit){verify(victim,never()).attack(any(EntityDamageEvent.class));verify(victim,never()).setOnFire(anyInt());}
  if(cancelCombust)verify(victim,never()).setOnFire(anyInt());
 }
 @Test void smallFireballVetoIsHonored() throws Exception{collide(false,false,true,true,5);}
 @Test void ghastFireballVetoIsHonored() throws Exception{collide(true,false,true,true,5);}
 @Test void bothUseListenerDuration() throws Exception{collide(false,false,true,false,9);collide(true,false,true,false,9);}
 @Test void cancelledProjectileHitDoesNotIgnite() throws Exception{collide(false,true,true,false,5);collide(true,true,true,false,5);}
 @Test void rejectedSmallProjectileDamageDoesNotIgnite() throws Exception{collide(false,false,false,false,5);}
}
