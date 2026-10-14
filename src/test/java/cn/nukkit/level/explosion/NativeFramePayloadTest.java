package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.*;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.*;
import cn.nukkit.event.block.*;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.*;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** A held item is source payload; the shell alone is subject to the event yield. */
@Isolated("native Server.instance and plugin handler lists")
public final class NativeFramePayloadTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
 @BeforeAll static void boot() throws Exception {NativeExplosionFixture.boot();}
 static final int[] IDS={Block.ITEM_FRAME_BLOCK,Block.GLOW_FRAME};
 static Item held(int count) {return Item.get(Item.DIAMOND,0,count).setNamedTag(new CompoundTag().putString("opaque","frame-held").putInt("arbitrary",42));}
 static NativeExplosionFixture.MemoryLevel world(int id) throws Exception {
  var w=NativeExplosionConservationTest.world(id,0);var tile=NativeExplosionFixture.allocated(BlockEntityItemFrame.class);
  tile.namedTag=new CompoundTag().putCompound("Item",NBTIO.putItemHelper(held(7))).putFloat("ItemDropChance",1).putByte("ItemRotation",0);
  NativeExplosionFixture.attach(w,tile);return w;
 }
 static void blast(NativeExplosionFixture.MemoryLevel w,double power) throws Exception {
  var e=new Explosion(new Position(0,64,0,w),power,Block.get(Block.TNT,0,w,4,64,0));
  NativeExplosionFixture.set(Explosion.class,e,"affectedBlocks",new LinkedHashSet<>(List.of(w.cell)));e.explodeB();
 }
 static RegisteredListener listen(HandlerList list,Consumer<Event> body) {
  var plugin=new PluginBase(){};plugin.setEnabled(true);
  var listener=new RegisteredListener(new Listener(){},(l,e)->body.accept(e),EventPriority.NORMAL,plugin,false);list.register(listener);return listener;
 }
 static int heldOutput(NativeExplosionFixture.MemoryLevel w) {return NativeExplosionFixture.matching(w.allDrops(),held(7));}
 @Test void zeroShellYieldStillPublishesHeldItemOnce() throws Exception {
  var listener=listen(BlockExplodeEvent.getHandlers(),e->((BlockExplodeEvent)e).setYield(0));
  try {for(int id:IDS) {
   var w=world(id);var tile=(BlockEntityItemFrame)w.tile;blast(w,4);
   assertTrue(w.cell.isAir());assertEquals(7,heldOutput(w));assertEquals(1,w.allDrops().size());assertTrue(tile.getItem().isNull());
   blast(w,4);assertEquals(7,heldOutput(w),"repeated affected cell must not duplicate goods");
  }} finally {BlockExplodeEvent.getHandlers().unregister(listener);}
 }
 @Test void ordinaryTntYield25RetainsEveryHeldItem() throws Exception {
  for(int id:IDS)for(int sample=0;sample<32;sample++) {
   var w=world(id);var tile=(BlockEntityItemFrame)w.tile;blast(w,4);
   assertTrue(w.cell.isAir());assertEquals(7,heldOutput(w));assertTrue(tile.getItem().isNull());
   assertTrue(w.allDrops().size()==1 || w.allDrops().size()==2,"only the shell uses the existing yield roll");
  }
 }
 @Test void capturedFrameItemAndStateMustStillMatchAfterActorPreparation() throws Exception {
  for(int id:IDS)for(boolean rotation:new boolean[]{false,true}) {
   var w=world(id);var tile=(BlockEntityItemFrame)w.tile;
   var listener=listen(ItemSpawnEvent.getHandlers(),event->{if(rotation)tile.namedTag.putByte("ItemRotation",3);else tile.setItem(held(8),false);});
   try {blast(w,1);}finally{ItemSpawnEvent.getHandlers().unregister(listener);}
   assertFalse(w.cell.isAir());assertFalse(tile.closed);assertTrue(w.allDrops().isEmpty());
   assertEquals(rotation?held(7):held(8),tile.getItem());
   if(rotation)assertEquals(3,tile.getItemRotation());
  }
 }
 @Test void refusedAndAcceptedSetterPhasesConserveTheHeldStack() throws Exception {
  for(int id:IDS)for(int mode=1;mode<=5;mode++) {
   var w=world(id);var tile=(BlockEntityItemFrame)w.tile;w.setterMode=mode;
   boolean threw=false;try {blast(w,1);}catch(IllegalStateException expected){threw=true;}
   boolean accepted=mode>=4;assertEquals(accepted,w.cell.isAir());assertEquals(mode==3 || mode==4,threw);
   assertEquals(accepted?7:0,heldOutput(w));assertEquals(accepted,tile.getItem().isNull());
   assertEquals(7,heldOutput(w)+(tile.getItem().equalsExact(held(7))?7:0));
  }
 }
 static final class FaultFrame extends BlockEntityItemFrame {
  boolean failBefore;
  public FaultFrame(cn.nukkit.level.format.FullChunk chunk,CompoundTag tag){super(chunk,tag);}
  @Override public void setItem(Item item,boolean changed){
   if(item.isNull() && failBefore)throw new IllegalStateException("frame before payload debit");
   super.setItem(item,changed);
   if(item.isNull())throw new IllegalStateException("frame after payload debit");
  }
 }
 @Test void payloadSetterFailureBeforeAndAfterDebitHasDifferentCommitOutcome() throws Exception {
  BlockEntity.registerBlockEntity("ExplosionFaultFrame",FaultFrame.class);
  for(int id:IDS)for(boolean before:new boolean[]{false,true}) {
   var w=world(id);var f=NativeExplosionFixture.allocated(FaultFrame.class);f.failBefore=before;f.namedTag=w.tile.namedTag.copy();NativeExplosionFixture.attach(w,f);
   assertThrows(IllegalStateException.class,()->blast(w,1));
   assertEquals(!before,w.cell.isAir());assertEquals(before?0:7,heldOutput(w));
   assertEquals(7,heldOutput(w)+(f.getItem().equalsExact(held(7))?7:0));
   assertEquals(!before,f.getItem().isNull());
  }
 }
 static NativeExplosionBlockProcessor processor(boolean refuse,boolean failAfter) {
  return new NativeExplosionBlockProcessor(){
   public Result process(Block b){return Result.NATIVE;}
   public boolean canRemove(List<Block> originals){return !refuse;}
   public void removed(List<Block> originals){if(failAfter)throw new IllegalStateException("metadata after acceptance");}
  };
 }
 @Test void trustedHostGoodsOverrideDoesNotWeakenTheOriginalFrameWitness() throws Exception {
  for(int id:IDS)for(boolean refuse:new boolean[]{false,true})for(boolean suppressed:new boolean[]{false,true}) {
   var w=world(id);var f=(BlockEntityItemFrame)w.tile;
   Item original=held(7).setNamedTag(held(7).getNamedTag().copy().putString("example:frame_owner","sample"));f.setItem(original,false);
   CompoundTag snapshot=f.namedTag.copy();Item stripped=held(7);var overrides=suppressed?List.<Item>of():List.of(stripped);
   Item shell=w.cell.toItem().setNamedTag(new CompoundTag().putString("example:shell","test"));
   boolean accepted=Explosion.removeSource(w.cell,Item.get(Item.DIAMOND_PICKAXE),true,List.of(shell),overrides,processor(refuse,false));
   assertEquals(!refuse,accepted);assertEquals(!refuse,w.cell.isAir());
   if(refuse){assertEquals(snapshot,f.namedTag);assertTrue(w.allDrops().isEmpty());}
   else {
    assertTrue(f.getItem().isNull());assertEquals(suppressed?0:7,heldOutput(w));
    assertEquals(1,NativeExplosionFixture.matching(w.allDrops(),shell));
    if(!suppressed){Item output=w.allDrops().stream().filter(i->i.getId()==Item.DIAMOND).findFirst().orElseThrow();assertTrue(output.equalsExact(stripped));assertFalse(output.getNamedTag().contains("example:shell"));assertFalse(output.getNamedTag().contains("example:frame_owner"));}
   }
  }
 }
 @Test void acceptedMetadataFailureStillClearsAndPublishesFundedHeldGoods() throws Exception {
  for(int id:IDS) {
   var w=world(id);var f=(BlockEntityItemFrame)w.tile;
   assertThrows(IllegalStateException.class,()->Explosion.removeSource(w.cell,Item.get(Item.AIR),false,List.of(),null,processor(false,true)));
   assertTrue(w.cell.isAir());assertTrue(f.getItem().isNull());assertEquals(7,heldOutput(w));assertEquals(1,w.allDrops().size());
  }
 }
 @Test void cloneBoundariesAndShellOnlyCanonicalLoot() throws Exception {
  for(int id:IDS){var w=world(id);var f=(BlockEntityItemFrame)w.tile;var snapshot=Explosion.captureStoredPayload(w.cell);
   assertNotNull(snapshot);assertEquals(List.of(held(7)),snapshot.goods());Item copy=snapshot.goods().get(0);copy.setCount(99);assertEquals(7,snapshot.goods().get(0).getCount());
   var shell=Explosion.canonicalOwnLoot(w.cell,Item.get(Item.AIR));assertEquals(1,shell.size());assertEquals(w.cell.toItem(),shell.get(0));assertTrue(snapshot.matches());assertEquals(held(7),f.getItem());
  }
 }
}
