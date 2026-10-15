package cn.nukkit.entity.projectile;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.*;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class BlueSkullEntityImpactTest {
 @BeforeAll static void boot(){MockServer.init();Effect.init();}
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
