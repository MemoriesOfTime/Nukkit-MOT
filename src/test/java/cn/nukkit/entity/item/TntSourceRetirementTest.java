package cn.nukkit.entity.item;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class TntSourceRetirementTest {
 @BeforeAll static void boot(){MockServer.init();}
 private void detonate(Entity actor){if(actor instanceof EntityPrimedTNT t)t.explode();else((EntityMinecartTNT)actor).explode(0);}
 private void run(boolean cart,boolean cancel,boolean ruleAfter,boolean throwingBlast,boolean closeInCallback) throws Exception{
  Entity actor=cart?mock(EntityMinecartTNT.class,CALLS_REAL_METHODS):mock(EntityPrimedTNT.class,CALLS_REAL_METHODS);
  Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);when(server.getPluginManager()).thenReturn(plugins);
  Field field=Entity.class.getDeclaredField("server");field.setAccessible(true);field.set(actor,server);doReturn(true).when(actor).isAlive();
  Level level=mock(Level.class);GameRules rules=GameRules.getDefault();when(level.getGameRules()).thenReturn(rules);actor.level=level;
  doAnswer(call->{actor.closed=true;return null;}).when(actor).close();AtomicInteger prime=new AtomicInteger();
  doAnswer(call->{if(call.getArgument(0) instanceof EntityExplosionPrimeEvent event){prime.incrementAndGet();event.setBlockBreaking(false);rules.setGameRule(GameRule.TNT_EXPLODES,ruleAfter);if(cancel)event.setCancelled();if(closeInCallback)actor.close();}return null;}).when(plugins).callEvent(any(Event.class));
  RuntimeException failure=new IllegalStateException("effect failure");AtomicBoolean retried=new AtomicBoolean();
  try(var construction=mockConstruction(Explosion.class,(blast,context)->{
   doAnswer(call->{assertTrue(actor.closed,"Charge must retire before fallible blast callbacks");if(retried.compareAndSet(false,true))detonate(actor);if(throwingBlast)throw failure;return true;}).when(blast).explodeB();
  })){
   if(throwingBlast)assertSame(failure,assertThrows(RuntimeException.class,()->detonate(actor)));else detonate(actor);
   if(!cancel&&!closeInCallback)assertTrue(actor.closed);boolean accepted=!cancel&&ruleAfter&&!closeInCallback;
   assertEquals(accepted?1:0,construction.constructed().size());assertEquals(1,prime.get());
   if(accepted){detonate(actor);assertEquals(1,prime.get());verify(construction.constructed().get(0)).explodeB();verify(construction.constructed().get(0),never()).explodeA();}
   if(cancel&&ruleAfter&&!closeInCallback)assertFalse(actor.closed,"A cancelled prime retains the existing source");
  }
 }
 @Test void bothSourcesRetireBeforeEffectsAndReentrantRetry() throws Exception{run(false,false,true,false,false);run(true,false,true,false,false);}
 @Test void bothSourcesStayRetiredAfterEffectFailure() throws Exception{run(false,false,true,true,false);run(true,false,true,true,false);}
 @Test void finalGameRuleVetoRetiresWithoutBlast() throws Exception{run(false,false,false,false,false);run(true,false,false,false,false);}
 @Test void cancellationPreservesCharge() throws Exception{run(false,true,true,false,false);run(true,true,true,false,false);}
 @Test void callbackClosedSourceCannotBlast() throws Exception{run(false,false,true,false,true);run(true,false,true,false,true);} 
 private void retirementNotificationFailure(boolean cart,boolean effectFailure) throws Exception {
  Entity actor=cart?mock(EntityMinecartTNT.class,CALLS_REAL_METHODS):mock(EntityPrimedTNT.class,CALLS_REAL_METHODS);
  Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);when(server.getPluginManager()).thenReturn(plugins);
  Field field=Entity.class.getDeclaredField("server");field.setAccessible(true);field.set(actor,server);doReturn(true).when(actor).isAlive();
  Level level=mock(Level.class);when(level.getGameRules()).thenReturn(GameRules.getDefault());actor.level=level;
  RuntimeException closeFailure=new IllegalStateException("despawn callback failure"),blastFailure=new IllegalArgumentException("blast callback failure");
  doAnswer(call->{actor.closed=true;throw closeFailure;}).when(actor).close();
  doAnswer(call->{if(call.getArgument(0) instanceof EntityExplosionPrimeEvent prime)prime.setBlockBreaking(false);return null;}).when(plugins).callEvent(any(Event.class));
  try(var construction=mockConstruction(Explosion.class,(blast,context)->{
   doAnswer(call->{assertTrue(actor.closed,"Native lifetime commits before despawn notification throws");if(effectFailure)throw blastFailure;return true;}).when(blast).explodeB();
  })){
   assertSame(closeFailure,assertThrows(RuntimeException.class,()->detonate(actor)));assertTrue(actor.closed);assertEquals(1,construction.constructed().size());verify(construction.constructed().get(0)).explodeB();
   assertEquals(effectFailure?1:0,closeFailure.getSuppressed().length);if(effectFailure)assertSame(blastFailure,closeFailure.getSuppressed()[0]);
   detonate(actor);assertEquals(1,construction.constructed().size());
  }
 }
 @Test void bothRetiredSourcesAttemptEffectsDespiteDespawnFailure() throws Exception{retirementNotificationFailure(false,false);retirementNotificationFailure(true,false);}
 @Test void originalDespawnFailureContainsEffectFailureWithoutRetry() throws Exception{retirementNotificationFailure(false,true);retirementNotificationFailure(true,true);}
 
 @Test void closedOrDeadCartCannotAcceptFlamingArrowIgnition() throws Exception {
  for(boolean closed:new boolean[]{false,true}){
   EntityMinecartTNT cart=mock(EntityMinecartTNT.class,CALLS_REAL_METHODS);Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);
   when(server.getPluginManager()).thenReturn(plugins);Field field=Entity.class.getDeclaredField("server");field.setAccessible(true);field.set(cart,server);
   cart.closed=closed;doReturn(closed).when(cart).isAlive();doReturn(false).when(cart).hasEffect(anyInt());doReturn(10f).when(cart).getHealth();doNothing().when(cart).explode(anyDouble());
   Level level=mock(Level.class);when(level.getGameRules()).thenReturn(GameRules.getDefault());cart.level=level;
   cn.nukkit.entity.projectile.EntityArrow arrow=mock(cn.nukkit.entity.projectile.EntityArrow.class);when(arrow.isOnFire()).thenReturn(true);
   var hit=new cn.nukkit.event.entity.EntityDamageByEntityEvent(arrow,cart,cn.nukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE,0);
   assertFalse(cart.attack(hit));verifyNoInteractions(plugins);verify(cart,never()).explode(anyDouble());
  }
 }

}
