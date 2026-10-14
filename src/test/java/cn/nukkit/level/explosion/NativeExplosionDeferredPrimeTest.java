package cn.nukkit.level.explosion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import cn.nukkit.block.*;
import cn.nukkit.entity.*;
import cn.nukkit.entity.item.*;
import cn.nukkit.event.*;
import cn.nukkit.event.block.*;
import cn.nukkit.event.entity.*;
import cn.nukkit.level.*;
import cn.nukkit.math.*;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.plugin.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
@Isolated("native Server.instance, item registries and plugin handler lists")
public final class NativeExplosionDeferredPrimeTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
 @BeforeAll static void nativeBootstrap() throws Exception {NativeExplosionFixture.boot();}

 static int passed;
 static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);passed++;}
 static RegisteredListener listen(HandlerList handlers,EventPriority priority,Consumer<Event> callback){
  var plugin=new PluginBase(){};plugin.init(null,cn.nukkit.Server.getInstance(),new PluginDescription(Map.of("name","native-receipt-probe","version","1","main","test.Main","api",List.of("1.0.0"))),new java.io.File("/tmp"),new java.io.File("/tmp/probe.jar"));plugin.setEnabled(true);
  var r=new RegisteredListener(new Listener(){},(listener,event)->callback.accept(event),priority,plugin,false);handlers.register(r);return r;
 }
 static CompoundTag nbt(int fuse){return new CompoundTag().putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("",0.5)).add(new DoubleTag("",64)).add(new DoubleTag("",0.5))).putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("",0)).add(new DoubleTag("",0.2)).add(new DoubleTag("",0))).putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("",0)).add(new FloatTag("",0))).putShort("Fuse",fuse);}
 static Entity actor(NativeExplosionFixture.MemoryLevel w){var ticket=EntityPrimedTNT.preparePrime(w.chunk,nbt(80),null);if(ticket==null)throw new AssertionError("source actor preparation");ticket.release();return w.actors.values().stream().filter(a->a instanceof EntityPrimedTNT && !a.isPublicationDeferred()).findFirst().orElseThrow();}
 static Explosion explosion(NativeExplosionFixture.MemoryLevel w,boolean entity)throws Exception{
  Explosion e=entity?new Explosion(new Position(0,64,0,w),1,actor(w)):new Explosion(new Position(0,64,0,w),1,Block.get(Block.TNT,0,w,4,64,0));
  NativeExplosionFixture.set(Explosion.class,e,"affectedBlocks",new LinkedHashSet<>(List.of(w.cell)));return e;
 }
 static ExplosionBlockProcessing processing(Event event){return event instanceof EntityExplodeEvent e?e.getBlockProcessing():((BlockExplodeEvent)event).getBlockProcessing();}
 @Test void events()throws Exception{
  for(boolean entity:List.of(false,true))for(int mode=0;mode<7;mode++){
   var w=NativeExplosionConservationTest.world(Block.PLANKS,0);var e=explosion(w,entity);var handlers=entity?EntityExplodeEvent.getHandlers():BlockExplodeEvent.getHandlers();int currentMode=mode;
   AtomicInteger planned=new AtomicInteger(),processed=new AtomicInteger(),removed=new AtomicInteger();AtomicReference<Event> captured=new AtomicReference<>();
   var high=listen(handlers,EventPriority.HIGH,event->{captured.set(event);planned.incrementAndGet();processing(event).addBlockProcessor(new NativeExplosionBlockProcessor(){
    public Result process(Block original){processed.incrementAndGet();if(currentMode==3)return Result.REFUSED;if(currentMode==4)throw new IllegalStateException("processor refused");if(currentMode==5){w.setBlock(original,Block.get(Block.AIR));return Result.HANDLED;}return Result.NATIVE;}
    public boolean canRemove(List<Block> originals){if(currentMode==6)throw new IllegalStateException("metadata precommit refusal");return true;}
    public void removed(Block original){check(w.cell.isAir() && w.allDrops().isEmpty(),"hook before loot publication");removed.incrementAndGet();}
   });});
   var late=listen(handlers,EventPriority.MONITOR,event->{if(currentMode==1)((Cancellable)event).setCancelled(true);if(currentMode==2){if(event instanceof EntityExplodeEvent x)x.getBlockList().clear();else((BlockExplodeEvent)event).getAffectedBlocks().clear();}});
   try{try{e.explodeB();}catch(IllegalStateException expected){if(mode!=6)throw expected;}}
   finally{handlers.unregister(high);handlers.unregister(late);}
   boolean accepted=mode==0||mode==5;
   check(planned.get()==1 && processed.get()==(mode==1||mode==2?0:1) && w.cell.isAir()==accepted,"late state entity="+entity+" mode="+mode);
   var records=processing(captured.get()).getRemovedBlocks();
   check(records.size()==(accepted?1:0) && removed.get()==(mode==0?1:0),"removed outcome entity="+entity+" mode="+mode);
   check(w.allDrops().size()==(mode==0?1:0),"native publication entity="+entity+" mode="+mode);
   try{records.add(w.cell);throw new AssertionError("mutable removed list");}catch(UnsupportedOperationException expected){passed++;}
  }
 }
 static int primed(NativeExplosionFixture.MemoryLevel w){return(int)w.actors.values().stream().filter(a->a instanceof EntityPrimedTNT && !a.closed && !a.isPublicationDeferred()).count();}
 @Test void prime()throws Exception{
  for(int mode=0;mode<=5;mode++){
   var w=NativeExplosionConservationTest.world(Block.TNT,0);BlockTNT t=(BlockTNT)w.cell;w.setterMode=mode;AtomicInteger accepted=new AtomicInteger();boolean result=false,threw=false;
   try{result=t.primeAccepted(20,null,accepted::incrementAndGet);}catch(IllegalStateException expected){threw=true;}
   boolean expected=mode==0||mode==4||mode==5;
   check(w.cell.isAir()==expected && primed(w)==(expected?1:0) && accepted.get()==(expected?1:0),"prime setter mode="+mode+" standing="+!w.cell.isAir()+" actors="+primed(w));
   check(threw==(mode==3||mode==4) && result==(mode==0||mode==5),"prime result mode="+mode);
   if(expected)check(!t.primeAccepted(20,null,accepted::incrementAndGet) && primed(w)==1,"prime replay mode="+mode);
  }
  for(int mode=0;mode<5;mode++){
   var w=NativeExplosionConservationTest.world(Block.TNT,0);BlockTNT t=(BlockTNT)w.cell;AtomicInteger callbacks=new AtomicInteger();int m=mode;
   var listener=listen(EntitySpawnEvent.getHandlers(),EventPriority.NORMAL,event->{Entity a=((EntitySpawnEvent)event).getEntity();if(!(a instanceof EntityPrimedTNT))return;
    check(!w.cell.isAir() && a.isPublicationDeferred() && primed(w)==0,"spawn sees retained TNT");
    check(!a.onUpdate(1),"prepared TNT cannot tick");
    if(m==0)a.close();if(m==1)a.motionX+=1;if(m==2){a.close();throw new IllegalStateException("factory event failure");}
    if(m==3)w.setBlock(t,Block.get(Block.EMERALD_BLOCK));
    if(m==4)check(!t.primeAccepted(10,null,callbacks::incrementAndGet),"reentrant prime refused");
   });
   boolean result=false;try{try{result=t.primeAccepted(20,null,callbacks::incrementAndGet);}catch(IllegalStateException expected){if(m!=2)throw expected;}}finally{EntitySpawnEvent.getHandlers().unregister(listener);}
   check(primed(w)==(m==4?1:0) && callbacks.get()==(m==4?1:0) && result==(m==4),"spawn result mode="+m);
   check(w.cell.getId()==(m==3?Block.EMERALD_BLOCK:m==4?Block.AIR:Block.TNT),"spawn source mode="+m);
  }
  var w=NativeExplosionConservationTest.world(Block.TNT,0);BlockTNT t=(BlockTNT)w.cell;
  try{t.primeAccepted(20,null,()->{throw new IllegalStateException("metadata refusal");},()->{});throw new AssertionError("callback not invoked");}catch(IllegalStateException expected){}
  check(w.cell.getId()==Block.TNT && primed(w)==0,"prime callback refuses without source loss");
  var chain=NativeExplosionConservationTest.world(Block.TNT,0);var e=explosion(chain,false);e.explodeB();
  check(chain.cell.isAir() && primed(chain)==1 && e.getRemovedBlocks().size()==1,"actual TNT explosion branch source accepted");
 }

 @Test void acceptedActorHook()throws Exception {
  for(int route=0;route<3;route++)for(boolean refused:List.of(false,true)) {
   var w=NativeExplosionConservationTest.world(Block.TNT,0);w.rejectRemoval=refused;BlockTNT t=(BlockTNT)w.cell;
   AtomicInteger spawns=new AtomicInteger(),accepted=new AtomicInteger(),arm=new AtomicInteger();
   var early=listen(EntitySpawnEvent.getHandlers(),EventPriority.NORMAL,event->{if(((EntitySpawnEvent)event).getEntity() instanceof EntityPrimedTNT){spawns.incrementAndGet();check(!w.cell.isAir(),"spawn retained source route");}});
   var late=listen(EntityPrimeAcceptedEvent.getHandlers(),EventPriority.NORMAL,event->{var e=(EntityPrimeAcceptedEvent)event;accepted.incrementAndGet();check(w.cell.isAir() && e.getEntity().isPublicationDeferred() && e.getSourceBlock().getId()==Block.TNT,"post-source arm boundary");e.getEntity().namedTag.putString("chargeOwner","receipt-owner");arm.incrementAndGet();});
   try {if(route==0)t.prime(80,null);else if(route==1)t.onActivate(cn.nukkit.item.Item.get(cn.nukkit.item.Item.FLINT_STEEL),null);else explosion(w,false).explodeB();}
   finally {EntitySpawnEvent.getHandlers().unregister(early);EntityPrimeAcceptedEvent.getHandlers().unregister(late);}
   check(spawns.get()==1 && accepted.get()==(refused?0:1) && arm.get()==(refused?0:1) && primed(w)==(refused?0:1),"accepted hook exactonce route="+route+" refused="+refused);
   if(!refused){check(w.actors.values().stream().filter(a->a instanceof EntityPrimedTNT).allMatch(a->"receipt-owner".equals(a.namedTag.getString("chargeOwner"))),"actor source ownership retained");t.prime(80,null);check(accepted.get()==1 && primed(w)==1,"accepted hook replay absent");}
  }
 }
}
