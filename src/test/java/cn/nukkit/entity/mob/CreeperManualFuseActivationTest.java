package cn.nukkit.entity.mob;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class CreeperManualFuseActivationTest {
 static final class Creeper extends EntityCreeper {
  int blasts,updates;
  Creeper(FullChunk chunk,CompoundTag tag){super(chunk,tag);}
  @Override protected boolean isInTickingRange(double range){return false;}
  @Override public boolean entityBaseTick(int delta){updates++;return false;}
  @Override public Vector3 updateMove(int delta){return null;}
  @Override public void explode(){blasts++;close();}
 }
 @Test void acceptedManualIgnitionWakesSleepingFuseForEveryUpdate() throws Exception{
  for(boolean charged:new boolean[]{false,true}){
   SparseMobConstructionWorld w=new SparseMobConstructionWorld();double previous=w.server.entityActivationRangeSquared;w.server.entityActivationRangeSquared=64;
   Creeper actor=new Creeper(w.chunk,Entity.getDefaultNBT(new Vector3(.5,64,.5)).putBoolean("powered",charged));
   try{
    int phase=1020-Math.floorMod((int)actor.getId(),20);assertFalse(actor.isActivationThrottled(phase));assertTrue(actor.isActivationAsleep());
    assertTrue(actor.onInteract(mock(Player.class),Item.get(Item.FLINT_AND_STEEL),new Vector3()));
    for(int i=1;i<=31;i++){int tick=phase+i;if(!actor.isActivationThrottled(tick))actor.onUpdate(tick);}
    assertEquals(1,actor.blasts,"Manual fuse must detonate after 31 updates despite empty activation range");assertTrue(actor.closed);assertFalse(actor.onUpdate(phase+32));assertEquals(1,actor.blasts);
   }finally{actor.close();w.server.entityActivationRangeSquared=previous;}
  }
 }
 @Test void unlitCreeperRetainsSleepingCadence() throws Exception{
  SparseMobConstructionWorld w=new SparseMobConstructionWorld();double previous=w.server.entityActivationRangeSquared;w.server.entityActivationRangeSquared=64;
  Creeper actor=new Creeper(w.chunk,Entity.getDefaultNBT(new Vector3(.5,64,.5)));
  try{int phase=1020-Math.floorMod((int)actor.getId(),20);actor.isActivationThrottled(phase);for(int i=1;i<=31;i++)if(!actor.isActivationThrottled(phase+i))actor.onUpdate(phase+i);assertEquals(0,actor.blasts);assertTrue(actor.updates<5,"Unlit native mob keeps activation optimization");}
  finally{actor.close();w.server.entityActivationRangeSquared=previous;}
 }
}
