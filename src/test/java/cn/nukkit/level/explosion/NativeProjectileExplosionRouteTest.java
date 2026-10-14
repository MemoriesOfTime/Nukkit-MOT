package cn.nukkit.level.explosion;

import cn.nukkit.block.*;
import cn.nukkit.blockentity.*;
import cn.nukkit.entity.*;
import cn.nukkit.entity.item.*;
import cn.nukkit.entity.projectile.*;
import cn.nukkit.event.*;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.block.*;
import cn.nukkit.inventory.*;
import cn.nukkit.item.*;
import cn.nukkit.level.*;
import cn.nukkit.math.*;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.plugin.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Native legacy routes, canonical provider/actors, forced selection plus separate native-ray controls. */
@Isolated("native Server.instance, native registry flags and handler lists")
public final class NativeProjectileExplosionRouteTest {
 @BeforeAll static void boot() throws Exception {NativeExplosionFixture.boot();}
 @AfterAll static void restoreFixtureServer() throws Exception {NativeExplosionFixture.restoreServer();}
 enum Route {WEAK,STRONG}
 static final class RouteLevel extends NativeExplosionFixture.MemoryLevel {
  GameRules rules;Entity[] targets;
  RouteLevel(){super();}
  @Override public GameRules getGameRules(){return rules;}
  @Override public int getMinBlockY(){return 0;}@Override public int getMaxBlockY(){return 255;}
  @Override public void setBlockAt(int x,int y,int z,int id,int meta){setBlock(new Vector3(x,y,z),0,Block.get(id,meta));}
  @Override public Entity[] getNearbyEntities(AxisAlignedBB box,Entity ignored){return targets;}
  @Override public void antiXrayOnBlockChange(cn.nukkit.Player player,Vector3 block,int layer){}
 }
 static RouteLevel world(int id,int meta)throws Exception {
  var seed=NativeExplosionFixture.level(id);var w=NativeExplosionFixture.allocated(RouteLevel.class);
  for(Class<?> type:new Class<?>[]{Level.class,NativeExplosionFixture.MemoryLevel.class})for(Field f:type.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);f.set(w,f.get(seed));}
  NativeExplosionFixture.set(cn.nukkit.level.format.leveldb.LevelDBProvider.class,w.getProvider(),"level",w);
  NativeExplosionFixture.set(Level.class,w,"server",NativeExplosionFixture.server);
  w.cell=Block.get(id,meta,w,0,64,0);w.rules=GameRules.getDefault();w.targets=new Entity[0];return w;
 }
 static Item held(int n){return Item.get(Item.DIAMOND,0,n).setNamedTag(new CompoundTag().putString("opaque","projectile-goods"));}
 static BlockEntityChest chest(RouteLevel w)throws Exception{return NativeExplosionConservationTest.chest(w,0);}
 static BlockEntityShulkerBox shulker(RouteLevel w)throws Exception{return NativeExplosionConservationTest.shulker(w);}
 static BlockEntityItemFrame frame(RouteLevel w)throws Exception{
  var t=NativeExplosionFixture.allocated(BlockEntityItemFrame.class);t.namedTag=new CompoundTag().putCompound("Item",NBTIO.putItemHelper(held(7))).putFloat("ItemDropChance",1).putByte("ItemRotation",0);NativeExplosionFixture.attach(w,t);return t;
 }
 static Entity projectile(Route route,RouteLevel w)throws Exception {
  var nbt=Entity.getDefaultNBT(new Vector3(0,64,0));
  return route==Route.WEAK?new EntityGhastFireBall(w.chunk,nbt):new EntityBlueWitherSkull(w.chunk,nbt);
 }
 static Explosion explosion(Route route,RouteLevel w,Block...blocks)throws Exception {
  Entity actor=projectile(route,w);actor.close();
  Explosion e=route==Route.WEAK?new WeakExplosion(actor,1,(Entity)null):new StrongExplosion(actor,1,(Entity)null);
  NativeExplosionFixture.set(e.getClass(),e,"affectedBlocks",new ArrayList<>(List.of(blocks)));return e;
 }
 static RegisteredListener listen(HandlerList list,EventPriority priority,Consumer<Event> callback){
  return NativeExplosionDeferredPrimeTest.listen(list,priority,callback);
 }
 static int count(RouteLevel w,Item item){return NativeExplosionFixture.matching(w.allDrops(),item);}
 interface Case {void run(Route route)throws Exception;}
 @TestFactory List<DynamicTest> routes(){
  Map<String,Case> cases=new LinkedHashMap<>();
  cases.put("chest-own-and-physical-contents",this::chestLoot);
  cases.put("lectern-jukebox-pot-payload",this::storedPayload);
  cases.put("frame-held-at-zero-yield",this::frameLoot);
  cases.put("all17-filled-shulker-colors",this::boxes);
  cases.put("native-kit-creative-processors",this::processors);
  cases.put("paired-one-both-and-replay",this::pairs);
  cases.put("late-cancel-and-filter",this::finalEvent);
  cases.put("unselected-partner-pure-protection",this::partnerPolicy);
  cases.put("prepared-actor-frame-replacement",this::frameMutation);
  cases.put("refused-noop-and-pre-post-write",this::setterPhases);
  cases.put("active-inventory-source",this::activeInventory);
  cases.put("chain-prime-refusal",this::primeRefusal);
  cases.put("accepted-prime-replacement-not-reselected",this::primeReplacement);
  cases.put("non-TNT-harvest-tool-unchanged",this::toolPolicy);
  cases.put("false-gamerule-loot-contract",this::falseRule);
  cases.put("actual-null-shooter-projectile-protection",this::nullShooter);
  cases.put("native-rays-resistance-controls",this::rays);
  cases.put("legacy-damage-motion-attribution",this::damage);
  List<DynamicTest> result=new ArrayList<>();for(Route route:Route.values())for(var test:cases.entrySet())result.add(DynamicTest.dynamicTest(route+"/"+test.getKey(),()->test.getValue().run(route)));return result;
 }
 void chestLoot(Route route)throws Exception {
  var w=world(Block.CHEST,0);var c=chest(w);c.getRealInventory().slots.put(0,held(64));
  var e=explosion(route,w,w.cell);assertTrue(e.explodeB());assertTrue(w.cell.isAir());assertEquals(64,count(w,held(64)));assertTrue(c.getRealInventory().isEmpty());assertEquals(1,count(w,Block.get(Block.CHEST).toItem()));assertEquals(1,e.getRemovedBlocks().size());
 }
 void storedPayload(Route route)throws Exception {
  for(int id:new int[]{Block.LECTERN,Block.JUKEBOX,Block.DECORATED_POT}) {
   var w=world(id,0);BlockEntity tile;
   if(id==Block.LECTERN){var t=NativeExplosionFixture.allocated(BlockEntityLectern.class);t.namedTag=new CompoundTag().putCompound("book",NBTIO.putItemHelper(held(7)));tile=t;}
   else if(id==Block.JUKEBOX){var t=NativeExplosionFixture.allocated(BlockEntityJukebox.class);t.namedTag=new CompoundTag();NativeExplosionFixture.set(BlockEntityJukebox.class,t,"recordItem",held(7));tile=t;}
   else{var t=NativeExplosionFixture.allocated(BlockEntityDecoratedPot.class);var sherds=new ListTag<StringTag>("sherds");for(int i=0;i<4;i++)sherds.add(new StringTag("","minecraft:brick"));t.namedTag=new CompoundTag().putList(sherds);NativeExplosionFixture.set(BlockEntityDecoratedPot.class,t,"item",held(7));tile=t;}
   NativeExplosionFixture.attach(w,tile);var listener=listen(EntityExplodeEvent.getHandlers(),EventPriority.MONITOR,event->((EntityExplodeEvent)event).setYield(0));try{explosion(route,w,w.cell).explodeB();}finally{EntityExplodeEvent.getHandlers().unregister(listener);}assertTrue(w.cell.isAir());assertEquals(7,count(w,held(7)));assertEquals(1,w.allDrops().size());
  }
 }
 void frameLoot(Route route)throws Exception {
  for(int id:new int[]{Block.ITEM_FRAME_BLOCK,Block.GLOW_FRAME}){
   var w=world(id,0);var f=frame(w);var listener=listen(EntityExplodeEvent.getHandlers(),EventPriority.MONITOR,event->((EntityExplodeEvent)event).setYield(0));try{explosion(route,w,w.cell).explodeB();}finally{EntityExplodeEvent.getHandlers().unregister(listener);}assertTrue(w.cell.isAir());assertTrue(f.getItem().isNull());assertEquals(7,count(w,held(7)));assertEquals(1,w.allDrops().size());
  }
 }
 void boxes(Route route)throws Exception {
  for(int color=-1;color<16;color++){var w=world(color<0?Block.UNDYED_SHULKER_BOX:Block.SHULKER_BOX,Math.max(color,0));var box=shulker(w);box.getRealInventory().slots.put(0,held(64));CompoundTag expected=w.cell.toItem().getNamedTag().copy();explosion(route,w,w.cell).explodeB();assertTrue(w.cell.isAir());assertEquals(1,w.allDrops().size());assertEquals(expected,w.allDrops().get(0).getNamedTag());assertTrue(box.getRealInventory().isEmpty());}
 }
 void processors(Route route)throws Exception {
  for(boolean creative:new boolean[]{false,true}){
   var w=world(Block.CHEST,0);var c=chest(w);c.getRealInventory().slots.put(0,held(7));int[] processed={0};
   Item shell=w.cell.toItem().setNamedTag(new CompoundTag().putString("kit","shell-only"));
   var listener=listen(EntityExplodeEvent.getHandlers(),EventPriority.HIGH,event->((EntityExplodeEvent)event).addBlockProcessor(new NativeExplosionBlockProcessor(){
    public Result process(Block original){processed[0]++;return Explosion.removeSource(original,Item.get(Item.AIR),!creative,creative?List.of():List.of(shell),new NativeExplosionBlockProcessor(){public Result process(Block b){return Result.NATIVE;}})?Result.HANDLED:Result.REFUSED;}
   }));Explosion e=explosion(route,w,w.cell);try{e.explodeB();}finally{EntityExplodeEvent.getHandlers().unregister(listener);}assertEquals(1,processed[0]);assertTrue(w.cell.isAir());assertEquals(7,count(w,held(7)));assertEquals(creative?0:1,count(w,shell));assertEquals(creative?1:2,w.allDrops().size());assertEquals(1,e.getRemovedBlocks().size());
  }
 }
 void pairs(Route route)throws Exception {
  for(int id:new int[]{Block.WOODEN_DOOR_BLOCK,Block.BED_BLOCK,Block.DOUBLE_PLANT})for(boolean both:new boolean[]{false,true}){
   var w=world(id,0);Block first=w.cell;Block partner=id==Block.BED_BLOCK?Block.get(id,8,w,0,64,1):Block.get(id,8,w,0,65,0);w.cells.put(RouteLevel.key(partner.getFloorX(),partner.getFloorY(),partner.getFloorZ(),0),partner);
   var e=explosion(route,w,both?new Block[]{first,partner}:new Block[]{partner});e.explodeB();assertTrue(w.cell.isAir());assertTrue(w.getBlock(partner).isAir());assertEquals(1,w.allDrops().size());assertEquals(2,e.getRemovedBlocks().size());e.explodeB();assertEquals(1,w.allDrops().size());
  }
 }
 void finalEvent(Route route)throws Exception {
  for(boolean cancel:new boolean[]{false,true}){
   var w=world(Block.WOODEN_DOOR_BLOCK,0);Block partner=Block.get(Block.WOODEN_DOOR_BLOCK,8,w,0,65,0);w.cells.put("0,65,0,0",partner);int[] calls={0};
   var high=listen(EntityExplodeEvent.getHandlers(),EventPriority.HIGH,event->((EntityExplodeEvent)event).addBlockProcessor(new NativeExplosionBlockProcessor(){public Result process(Block block){calls[0]++;return Result.NATIVE;}}));
   var last=listen(EntityExplodeEvent.getHandlers(),EventPriority.MONITOR,event->{var e=(EntityExplodeEvent)event;if(cancel)e.setCancelled();else e.setBlockList(new ArrayList<>(List.of(w.cell)));});
   var e=explosion(route,w,w.cell,partner);try{assertEquals(!cancel,e.explodeB());}finally{EntityExplodeEvent.getHandlers().unregister(high);EntityExplodeEvent.getHandlers().unregister(last);}assertFalse(w.cell.isAir());assertFalse(w.getBlock(partner).isAir());assertTrue(w.allDrops().isEmpty());assertEquals(cancel?0:1,calls[0]);assertTrue(e.getRemovedBlocks().isEmpty());
  }
 }
 void partnerPolicy(Route route)throws Exception {
  var w=world(Block.WOODEN_DOOR_BLOCK,0);Block partner=Block.get(Block.WOODEN_DOOR_BLOCK,8,w,0,65,0);w.cells.put("0,65,0,0",partner);int[] validated={0};
  var listener=listen(EntityExplodeEvent.getHandlers(),EventPriority.HIGH,event->((EntityExplodeEvent)event).addBlockProcessor(new NativeExplosionBlockProcessor(){public Result process(Block b){return Result.NATIVE;}public boolean canRemove(List<Block> cells){validated[0]++;return cells.stream().noneMatch(cell->cell.getFloorY()==65);}}));
  var e=explosion(route,w,w.cell);try{e.explodeB();}finally{EntityExplodeEvent.getHandlers().unregister(listener);}assertTrue(validated[0]>0);assertFalse(w.cell.isAir());assertFalse(w.getBlock(partner).isAir());assertTrue(w.allDrops().isEmpty());assertTrue(e.getRemovedBlocks().isEmpty());
 }
 void frameMutation(Route route)throws Exception {
  for(int id:new int[]{Block.ITEM_FRAME_BLOCK,Block.GLOW_FRAME}){
   var w=world(id,0);var f=frame(w);var listener=listen(ItemSpawnEvent.getHandlers(),EventPriority.NORMAL,event->f.setItem(held(8),false));var e=explosion(route,w,w.cell);
   try{e.explodeB();}finally{ItemSpawnEvent.getHandlers().unregister(listener);}assertFalse(w.cell.isAir());assertFalse(f.closed);assertEquals(held(8),f.getItem());assertTrue(w.allDrops().isEmpty());assertTrue(e.getRemovedBlocks().isEmpty());
  }
 }
 void setterPhases(Route route)throws Exception {
  for(int mode=1;mode<=5;mode++)for(int id:new int[]{Block.PLANKS,Block.CHEST,Block.ITEM_FRAME_BLOCK}){
   var w=world(id,0);var c=id==Block.CHEST?chest(w):null;var f=id==Block.ITEM_FRAME_BLOCK?frame(w):null;if(c!=null)c.getRealInventory().slots.put(0,held(7));w.setterMode=mode;
   var e=explosion(route,w,w.cell);boolean thrown=false;try{e.explodeB();}catch(IllegalStateException expected){thrown=true;}
   boolean accepted=mode>=4;assertEquals(accepted,w.cell.isAir(),"mode="+mode+" id="+id);assertEquals(mode==3 || mode==4,thrown);
   if(c!=null)assertEquals(7,count(w,held(7))+(c.getRealInventory().getItem(0).equalsExact(held(7))?7:0));
   if(f!=null)assertEquals(7,count(w,held(7))+(f.getItem().equalsExact(held(7))?7:0));
   if(!accepted)assertTrue(w.allDrops().isEmpty());assertEquals(accepted?1:0,e.getRemovedBlocks().size());
  }
 }
 void activeInventory(Route route)throws Exception {
  var w=world(Block.CHEST,0);var c=chest(w);c.getRealInventory().slots.put(0,held(7));try(var p=NativeContainerTransaction.tryPrepare(c.getRealInventory())){explosion(route,w,w.cell).explodeB();}assertFalse(w.cell.isAir());assertTrue(w.allDrops().isEmpty());assertEquals(held(7),c.getRealInventory().getItem(0));
 }
 void primeRefusal(Route route)throws Exception {
  var w=world(Block.TNT,0);var listener=listen(EntitySpawnEvent.getHandlers(),EventPriority.NORMAL,event->{var spawn=(EntitySpawnEvent)event;if(spawn.getEntity() instanceof EntityPrimedTNT)spawn.getEntity().close();});var e=explosion(route,w,w.cell);try{e.explodeB();}finally{EntitySpawnEvent.getHandlers().unregister(listener);}assertEquals(Block.TNT,w.cell.getId());assertTrue(w.allDrops().isEmpty());assertTrue(e.getRemovedBlocks().isEmpty());assertTrue(w.actors.values().stream().noneMatch(actor->actor instanceof EntityPrimedTNT && !actor.closed));
 }
 void primeReplacement(Route route)throws Exception {
  var w=world(Block.TNT,0);Block original=w.cell;int[] accepted={0};
  var listener=listen(EntityPrimeAcceptedEvent.getHandlers(),EventPriority.NORMAL,event->{var prime=(EntityPrimeAcceptedEvent)event;if(prime.getSourceBlock().level==w){accepted[0]++;w.setBlock(new Vector3(0,64,0),Block.get(Block.TNT));}});
  var e=explosion(route,w,original,original.clone());try{e.explodeB();}finally{EntityPrimeAcceptedEvent.getHandlers().unregister(listener);}
  assertEquals(Block.TNT,w.cell.getId(),"post-accept replacement is not the old source");assertEquals(1,accepted[0]);assertEquals(1,e.getRemovedBlocks().size());assertEquals(1,w.actors.values().stream().filter(actor->actor instanceof EntityPrimedTNT && !actor.closed && !actor.isPublicationDeferred()).count());
 }
 void toolPolicy(Route route)throws Exception {var w=world(Block.DIAMOND_ORE,0);explosion(route,w,w.cell).explodeB();assertTrue(w.cell.isAir());assertTrue(w.allDrops().isEmpty(),"non-TNT route must retain AIR harvest policy");}
 void falseRule(Route route)throws Exception {
  for(int id:new int[]{Block.PLANKS,Block.CHEST,Block.SHULKER_BOX,Block.ITEM_FRAME_BLOCK,Block.LECTERN}){
   var w=world(id,0);w.rules.setGameRule(GameRule.DO_TILE_DROPS,false);Inventory inventory=null;
   if(id==Block.CHEST){var c=chest(w);inventory=c.getRealInventory();((BaseInventory)inventory).slots.put(0,held(7));}
   if(id==Block.SHULKER_BOX){var c=shulker(w);inventory=c.getRealInventory();((BaseInventory)inventory).slots.put(0,held(7));}
   if(id==Block.ITEM_FRAME_BLOCK)frame(w);
   if(id==Block.LECTERN){var t=NativeExplosionFixture.allocated(BlockEntityLectern.class);t.namedTag=new CompoundTag().putCompound("book",NBTIO.putItemHelper(held(7)));NativeExplosionFixture.attach(w,t);}
   explosion(route,w,w.cell).explodeB();assertTrue(w.cell.isAir());
   if(inventory!=null)assertTrue(w.allDrops().isEmpty(),"legacy false gamerule forbids packed shulker escape and container shell");
   else if(id==Block.PLANKS)assertEquals(1,w.allDrops().size(),"legacy plain block false-rule behavior preserved");
   else assertEquals(7,count(w,held(7)),"explicit accepted stored-payload policy, independent of legacy inventory gamerule");
  }
 }
 void nullShooter(Route route)throws Exception {
  var w=world(Block.PLANKS,0);Entity actor=projectile(route,w);assertTrue(actor instanceof EntityGhastFireBall || actor instanceof EntityBlueWitherSkull);int[] calls={0};
  var seed=listen(EntityExplodeEvent.getHandlers(),EventPriority.LOWEST,event->((EntityExplodeEvent)event).setBlockList(new ArrayList<>(List.of(w.cell))));
  var protection=listen(EntityExplodeEvent.getHandlers(),EventPriority.HIGH,event->{var e=(EntityExplodeEvent)event;if(e.getEntity()!=null){assertSame(actor,e.getEntity());calls[0]++;e.addBlockProcessor(new NativeExplosionBlockProcessor(){public Result process(Block block){return Result.REFUSED;}});}});
  try{if(actor instanceof EntityGhastFireBall fire)fire.explode();else((EntityBlueWitherSkull)actor).explode();}finally{EntityExplodeEvent.getHandlers().unregister(seed);EntityExplodeEvent.getHandlers().unregister(protection);}assertEquals(1,calls[0]);assertEquals(Block.PLANKS,w.cell.getId());assertTrue(w.allDrops().isEmpty());
 }
 void rays(Route route)throws Exception {
  boolean prior=NativeExplosionFixture.server.explosionBreakBlocks;NativeExplosionFixture.server.explosionBreakBlocks=true;
  try{for(int id:new int[]{Block.PLANKS,Block.OBSIDIAN,Block.BEDROCK}){var w=world(id,0);Explosion e=route==Route.WEAK?new WeakExplosion(new Position(0,64,0,w),2,(Entity)null):new StrongExplosion(new Position(0,64,0,w),2,(Entity)null);assertTrue(e.explodeA());Field selected=e.getClass().getDeclaredField("affectedBlocks");selected.setAccessible(true);var cells=(List<Block>)selected.get(e);assertEquals(id==Block.PLANKS || route==Route.STRONG && id==Block.OBSIDIAN,!cells.isEmpty(),"native resistance threshold id="+id);}}
  finally{NativeExplosionFixture.server.explosionBreakBlocks=prior;}
 }
 static final class Target extends Entity {
  EntityDamageEvent hit;Vector3 applied;
  Target(cn.nukkit.level.format.FullChunk chunk,CompoundTag nbt){super(chunk,nbt);}
  @Override public int getNetworkId(){return 0;}
  @Override public boolean attack(EntityDamageEvent event){hit=event;return true;}
  @Override public boolean setMotion(Vector3 motion){applied=motion.clone();return true;}
 }
 void damage(Route route)throws Exception {
  var w=world(Block.AIR,0);var target=NativeExplosionFixture.allocated(Target.class);target.x=.5;target.y=64;target.z=0;target.level=w;NativeExplosionFixture.set(Entity.class,target,"effects",new java.util.HashMap<>());w.targets=new Entity[]{target};var e=explosion(route,w);e.explodeB();assertEquals(route==Route.WEAK?7:11,target.hit.getDamage());assertTrue(target.hit instanceof EntityDamageByEntityEvent);assertNull(((EntityDamageByEntityEvent)target.hit).getDamager(),"event-emitter fallback must not change original null-shooter damage attribution");assertEquals(.75,target.applied.x);assertEquals(0,target.applied.y);assertEquals(0,target.applied.z);
 }
}
