package cn.nukkit.entity.mob;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class WitherSpawnCountdownIdentityTest {
 static final class Wither extends EntityWither {
  Wither(FullChunk chunk,CompoundTag tag){super(chunk,tag);}
  @Override public Vector3 updateMove(int delta){return null;}
  void phase(int invulnerable,int lifetime){this.invul=invulnerable;this.age=lifetime;}
 }
 private void phase(int loadedInvul,int remaining,int age,int expectedPrimes) throws Exception{
  SparseMobConstructionWorld world=new SparseMobConstructionWorld();cn.nukkit.plugin.PluginManager plugins=world.server.getPluginManager();int[] prime={0};
  doAnswer(call->{if(call.getArgument(0) instanceof EntityExplosionPrimeEvent event){prime[0]++;assertEquals(7,event.getForce());event.setCancelled();}return null;}).when(plugins).callEvent(any(Event.class));
  Wither actor=new Wither(world.chunk,Entity.getDefaultNBT(new Vector3(.5,64,.5)).putInt("Invul",loadedInvul).putString("CustomName","countdown-test"));
  try{actor.phase(remaining,age);actor.entityBaseTick(1);actor.entityBaseTick(1);assertEquals(expectedPrimes,prime[0]);actor.saveNBT();assertEquals(remaining,actor.namedTag.getInt("Invul"));}
  finally{actor.close();}
 }
 @Test void matureLifetimeCrossing200CannotReplaySpawnBlast() throws Exception{phase(0,0,200,0);}
 @Test void activeSavedInvulnerabilityCannotBlastBeforeCompletion() throws Exception{phase(5,1,200,0);}
 @Test void restoredCountdownCompletionDoesNotRequireLifetime200() throws Exception{phase(5,0,105,1);}
 @Test void tickDiffOvershootConsumesPendingSpawnOnce() throws Exception{phase(1,-6,206,1);}
 @Test void alreadyCompletedSavedCountdownNeverCreatesPendingBlast() throws Exception{phase(-6,-6,200,0);}
}
