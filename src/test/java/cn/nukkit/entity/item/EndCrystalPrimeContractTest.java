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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class EndCrystalPrimeContractTest {
 @BeforeAll static void boot(){MockServer.init();}
 private void explode(boolean cancel,boolean breakBlocks,boolean grief) throws Exception{
  EntityEndCrystal crystal=mock(EntityEndCrystal.class,CALLS_REAL_METHODS);Server server=mock(Server.class);PluginManager plugins=mock(PluginManager.class);
  when(server.getPluginManager()).thenReturn(plugins);Field field=Entity.class.getDeclaredField("server");field.setAccessible(true);field.set(crystal,server);
  Level level=mock(Level.class);GameRules rules=GameRules.getDefault();rules.setGameRule(GameRule.MOB_GRIEFING,grief);when(level.getGameRules()).thenReturn(rules);crystal.level=level;
  doAnswer(call->{crystal.closed=true;return null;}).when(crystal).close();AtomicInteger prime=new AtomicInteger();
  doAnswer(call->{if(call.getArgument(0) instanceof EntityExplosionPrimeEvent event){prime.incrementAndGet();assertFalse(crystal.closed,"Listeners must observe the source before native retirement");event.setForce(2.5);event.setBlockBreaking(breakBlocks);if(cancel)event.setCancelled();}return null;}).when(plugins).callEvent(any(Event.class));
  try(var construction=mockConstruction(Explosion.class,(blast,context)->{assertEquals(2.5,((Number)context.arguments().get(1)).doubleValue());assertTrue(crystal.closed,"Source retires before effects");})){
   crystal.explode();crystal.explode();assertEquals(1,prime.get());assertTrue(crystal.closed);assertEquals(cancel?0:1,construction.constructed().size());
   if(!cancel){Explosion blast=construction.constructed().get(0);verify(blast,breakBlocks&&grief?times(1):never()).explodeA();verify(blast).explodeB();}
  }
 }
 @Test void nativeDamageCancellationCannotFundPrimeOrRetireSource() throws Exception {
  MockServer.reset();Server server=MockServer.get();PluginManager plugins=server.getPluginManager();Level level=mock(Level.class);
  cn.nukkit.level.format.generic.BaseFullChunk chunk=mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
  cn.nukkit.level.format.LevelProvider provider=mock(cn.nukkit.level.format.LevelProvider.class);
  when(level.getServer()).thenReturn(server);when(chunk.getProvider()).thenReturn(provider);when(provider.getLevel()).thenReturn(level);
  when(level.getChunk(anyInt(),anyInt(),anyBoolean())).thenReturn(chunk);when(level.getChunkIfLoaded(anyInt(),anyInt())).thenReturn(chunk);
  when(level.getGameRules()).thenReturn(GameRules.getDefault());when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
  Field updates=Level.class.getDeclaredField("updateEntities");updates.setAccessible(true);updates.set(level,new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<>());
  EntityEndCrystal crystal=new EntityEndCrystal(chunk,Entity.getDefaultNBT(new cn.nukkit.math.Vector3(.5,64,.5)));int[] primes={0};
  doAnswer(call->{if(call.getArgument(0) instanceof cn.nukkit.event.entity.EntityDamageEvent event)event.setCancelled();
   if(call.getArgument(0) instanceof EntityExplosionPrimeEvent event){primes[0]++;event.setCancelled();}return null;
  }).when(plugins).callEvent(any(Event.class));
  try {assertFalse(crystal.attack(new cn.nukkit.event.entity.EntityDamageEvent(crystal,cn.nukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,1)));
   assertEquals(0,primes[0]);assertFalse(crystal.closed,"Refused accepted-damage path retains the crystal");}
  finally {
   try {crystal.close();}
   finally {MockServer.reset();}
  }
 }
 @Test void changedForceAndBlockBreakingVetoAreHonored() throws Exception{explode(false,false,true);}
 @Test void acceptedBlockBreakingRetainsMobGriefingGate() throws Exception{explode(false,true,true);explode(false,true,false);}
 @Test void cancelledPrimeRetiresOnceWithoutBlast() throws Exception{explode(true,true,true);}
}
