package cn.nukkit.level.explosion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import cn.nukkit.Server;import cn.nukkit.block.*;import cn.nukkit.blockentity.*;import cn.nukkit.entity.*;import cn.nukkit.entity.item.EntityItem;import cn.nukkit.inventory.*;import cn.nukkit.item.Item;import cn.nukkit.level.*;import cn.nukkit.level.format.generic.BaseFullChunk;import cn.nukkit.level.format.leveldb.*;import cn.nukkit.level.format.leveldb.structure.*;import cn.nukkit.level.particle.Particle;import cn.nukkit.level.vibration.*;import cn.nukkit.math.*;import cn.nukkit.nbt.tag.CompoundTag;import cn.nukkit.utils.*;import java.lang.reflect.*;import java.util.*;
@Isolated("native Server.instance, item registries and plugin handler lists")
public final class NativeActualLevelSetterTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
 @BeforeAll static void nativeBootstrap() throws Exception {NativeExplosionFixture.boot();}

 static int passed;
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
 static final class FaultChunk extends LevelDBChunk {
  boolean refusePartner;
  Runnable afterSourceRawAir;
  boolean refuseTntRestoration;
  Block restoredBlockOverride;
  int restoredTntMetadata = -1;
  FaultChunk(LevelDBProvider provider){super(provider,0,0);}
  @Override public Block getAndSetBlock(int x,int y,int z,int layer,Block block){if(restoredTntMetadata>=0 && block.getId()==Block.TNT){Block previous=Block.fromFullId(getFullBlock(x,y,z,layer));setBlock(x,y,z,Block.TNT,restoredTntMetadata);return previous;}if(restoredBlockOverride!=null && block.getId()==Block.TNT)return super.getAndSetBlock(x,y,z,layer,restoredBlockOverride);if(refuseTntRestoration && block.getId()==Block.TNT)return Block.fromFullId(getFullBlock(x,y,z,layer));if(refusePartner && y==65 && block.isAir())return Block.fromFullId(getFullBlock(x,y,z,layer));Block previous=super.getAndSetBlock(x,y,z,layer,block);if(x==0 && y==64 && z==0 && layer==0 && block.isAir() && afterSourceRawAir!=null)afterSourceRawAir.run();return previous;}
 }
 static final class RealLevel extends Level {
  FaultChunk chunk;BlockEntity tile;Map<Long,Entity> actors;List<String> trace;int all(){return(int)actors.values().stream().filter(e->e instanceof EntityItem && !e.closed && !e.isPublicationDeferred()).count();}
  RealLevel()throws Exception{super(null,"","/",null);}
  @Override public Server getServer(){return NativeExplosionFixture.server;}
  @Override public int getMinBlockY(){return 0;}@Override public int getMaxBlockY(){return 255;}
  @Override public DimensionData getDimensionData(){return DimensionData.LEGACY_DIMENSION;}
  @Override public BaseFullChunk getChunk(int x,int z){return chunk;}@Override public BaseFullChunk getChunk(int x,int z,boolean create){return chunk;}@Override public BaseFullChunk getChunkIfLoaded(int x,int z){return x==0 && z==0?chunk:null;}
  @Override public Block getBlock(int x,int y,int z,int layer){return y<0 || y>255 || x<0 || x>15 || z<0 || z>15?Block.get(Block.AIR,0,this,x,y,z,layer):Block.get(chunk.getBlockId(x,y,z,layer),chunk.getBlockData(x,y,z,layer),this,x,y,z,layer);}
  @Override public Block getBlock(int x,int y,int z){return getBlock(x,y,z,0);}@Override public Block getBlock(int x,int y,int z,boolean load){return getBlock(x,y,z,0);}@Override public Block getBlock(int x,int y,int z,int layer,boolean load){return getBlock(x,y,z,layer);}
  @Override public Block getBlock(Vector3 p){return getBlock(p,0);}@Override public Block getBlock(Vector3 p,boolean load){return getBlock(p,0);}@Override public Block getBlock(Vector3 p,int layer){return getBlock(p.getFloorX(),p.getFloorY(),p.getFloorZ(),layer);}@Override public Block getBlock(Vector3 p,int layer,boolean load){return getBlock(p,layer);}
  @Override public BlockEntity getBlockEntity(Vector3 p){return super.getBlockEntityIfLoaded(p);}
  @Override public BlockEntity getBlockEntityIfLoaded(Vector3 p){return super.getBlockEntityIfLoaded(p);}
  @Override public Map<Integer,cn.nukkit.Player> getChunkPlayers(int x,int z){return Map.of();}@Override public cn.nukkit.level.ChunkLoader[] getChunkLoaders(int x,int z){return new cn.nukkit.level.ChunkLoader[0];}
  @Override public Map<Long,cn.nukkit.Player> getPlayers(){return Map.of();}
  @Override public Entity[] getNearbyEntities(AxisAlignedBB b,Entity ignored){return new Entity[0];}
  @Override public Entity getEntity(long id){return actors.get(id);}
  @Override public void addEntity(Entity entity){actors.put(entity.getId(),entity);}@Override public void removeEntity(Entity entity){actors.remove(entity.getId(),entity);}
  @Override public void removeBlockEntity(BlockEntity entity){super.removeBlockEntity(entity);if(tile==entity)tile=null;}
  @Override public void addParticle(Particle p){}@Override public void addSound(Vector3 p,Sound s){}@Override public void addLevelSoundEvent(Vector3 p,int event){}@Override public VibrationManager getVibrationManager(){return new SimpleVibrationManager(this);}
 }
 static RealLevel world(int id)throws Exception{
  var seed=NativeExplosionFixture.level(id);var w=NativeExplosionFixture.allocated(RealLevel.class);
  for(Field field:Level.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(w,field.get(seed));}
  NativeExplosionFixture.set(Level.class,w,"server",NativeExplosionFixture.server);
  LevelDBProvider provider=(LevelDBProvider)w.getProvider();NativeExplosionFixture.set(LevelDBProvider.class,provider,"level",w);
  w.chunk=new FaultChunk(provider);NativeExplosionFixture.set(BaseFullChunk.class,w.chunk,"entities",new Long2ObjectNonBlockingMap<Entity>());
  var chunks=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BaseFullChunk>();chunks.put(Level.chunkHash(0,0),w.chunk);NativeExplosionFixture.set(LevelDBProvider.class,provider,"chunks",chunks);
  w.actors=seed.actors;w.trace=new ArrayList<>();
  NativeExplosionFixture.set(Level.class,w,"lightQueue",new java.util.concurrent.ConcurrentHashMap<>());
  NativeExplosionFixture.set(Level.class,w,"normalUpdateQueue",new java.util.concurrent.ConcurrentLinkedDeque<>());
  NativeExplosionFixture.set(Level.class,w,"normalUpdateQueueSize",new java.util.concurrent.atomic.AtomicInteger());
  NativeExplosionFixture.set(Level.class,w,"changedBlocks",new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
  NativeExplosionFixture.set(Level.class,w,"callbackBlockSet",new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<java.util.function.BiConsumer<Block,Block>>());
  w.chunk.setBlock(0,64,0,id,0);check(w.chunk.getProvider().getClass()==LevelDBProvider.class && w.chunk.getProvider().getLevel()==w,"canonical real setter provider");return w;
 }
 static void explode(RealLevel w,Block...blocks)throws Exception{var e=new Explosion(new Position(0,64,0,w),1,(Entity)null);NativeExplosionFixture.set(Explosion.class,e,"affectedBlocks",new LinkedHashSet<>(List.of(blocks)));e.explodeB();}
 @Test void acceptedRawWritesBeforeActualNativeNotifications()throws Exception{
  NativeExplosionFixture.boot();
  for(boolean refusal:List.of(false,true)){
   var w=world(Block.WOODEN_DOOR_BLOCK);w.chunk.setBlock(0,65,0,Block.WOODEN_DOOR_BLOCK,8);w.chunk.refusePartner=refusal;
   Field f=Level.class.getDeclaredField("callbackBlockSet");f.setAccessible(true);var callbacks=(it.unimi.dsi.fastutil.ints.Int2ObjectMap<java.util.function.BiConsumer<Block,Block>>)f.get(w);
   callbacks.put(1,(before,after)->{w.trace.add("notify:"+after.y+":"+after.getId());if(after.isAir())check(w.getBlock(0,64,0).isAir() && w.getBlock(0,65,0).isAir() && w.all()==1,"both raw cells and goods accepted before first real notification");});
   explode(w,w.getBlock(0,64,0));check(w.getBlock(0,64,0).isAir()==!refusal && w.getBlock(0,65,0).isAir()==!refusal && w.all()==(refusal?0:1),"real pair refusal="+refusal+" trace="+w.trace);System.out.println("REAL_PAIR refusal="+refusal+" trace="+w.trace);
  }
  var w=world(Block.CHEST);var tile=NativeExplosionFixture.allocated(BlockEntityChest.class);tile.level=w;tile.chunk=w.chunk;tile.namedTag=new CompoundTag();tile.x=0;tile.y=64;tile.z=0;w.tile=tile;var inv=new ChestInventory(tile);NativeExplosionFixture.set(BlockEntitySpawnableContainer.class,tile,"inventory",inv);inv.slots.put(0,Item.get(Item.DIAMOND,0,6));NativeExplosionFixture.register(w,tile);
  Field f=Level.class.getDeclaredField("callbackBlockSet");f.setAccessible(true);var callbacks=(it.unimi.dsi.fastutil.ints.Int2ObjectMap<java.util.function.BiConsumer<Block,Block>>)f.get(w);
  callbacks.put(1,(before,after)->{if(after.isAir()){check(inv.slots.isEmpty() && w.all()==2,"real chest callback sees funded actors before close");tile.close();}});
  explode(w,w.getBlock(0,64,0));check(tile.closed && inv.slots.isEmpty() && w.all()==2,"real BE close callback acceptedexactonce");
  System.out.println("NATIVE_REAL_SETTER passed="+passed+" actualLevelMethod=true nativeBEclose=true");
 }
}
