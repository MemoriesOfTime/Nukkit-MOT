package cn.nukkit.level.explosion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import cn.nukkit.Server;import cn.nukkit.block.*;import cn.nukkit.blockentity.*;import cn.nukkit.entity.*;import cn.nukkit.entity.item.EntityItem;import cn.nukkit.inventory.*;import cn.nukkit.item.Item;import cn.nukkit.level.*;import cn.nukkit.level.format.generic.BaseFullChunk;import cn.nukkit.level.format.leveldb.*;import cn.nukkit.level.format.leveldb.structure.*;import cn.nukkit.level.particle.Particle;import cn.nukkit.level.vibration.*;import cn.nukkit.math.*;import cn.nukkit.nbt.tag.CompoundTag;import cn.nukkit.utils.*;import java.lang.reflect.*;import java.util.*;
final class BeeExplosionFixture {

 static final class FaultChunk extends LevelDBChunk {
  boolean refusePartner;String sourceFailure="";
  FaultChunk(LevelDBProvider provider){super(provider,0,0);}
  @Override public Block getAndSetBlock(int x,int y,int z,int layer,Block block){if(y==64 && block.isAir()){if(sourceFailure.equals("before"))throw new IllegalStateException("source before raw");if(sourceFailure.equals("refuse"))return Block.fromFullId(getFullBlock(x,y,z,layer));if(sourceFailure.equals("after-raw")){super.getAndSetBlock(x,y,z,layer,block);throw new IllegalStateException("source after raw");}}if(refusePartner && y==65 && block.isAir())return Block.fromFullId(getFullBlock(x,y,z,layer));return super.getAndSetBlock(x,y,z,layer,block);}
 }
 static final class RealLevel extends Level {
  FaultChunk chunk;BlockEntity tile;Map<Long,Entity> actors;List<String> trace;int all(){return(int)actors.values().stream().filter(e->e instanceof EntityItem && !e.closed && !e.isPublicationDeferred()).count();}
  RealLevel()throws Exception{super(null,"","/",null);}
  @Override public Server getServer(){return NativeExplosionFixture.server;}
  @Override public GameRules getGameRules(){return GameRules.getDefault();}
  @Override public int getMinBlockY(){return 0;}@Override public int getMaxBlockY(){return 255;}
  @Override public DimensionData getDimensionData(){return DimensionData.LEGACY_DIMENSION;}
  @Override public BaseFullChunk getChunk(int x,int z){return chunk;}@Override public BaseFullChunk getChunk(int x,int z,boolean create){return chunk;}@Override public BaseFullChunk getChunkIfLoaded(int x,int z){return x==0 && z==0?chunk:null;}
  @Override public Block getBlock(int x,int y,int z,int layer){return y<0 || y>255 || x<0 || x>15 || z<0 || z>15?Block.get(Block.AIR,0,this,x,y,z,layer):Block.get(chunk.getBlockId(x,y,z,layer),chunk.getBlockData(x,y,z,layer),this,x,y,z,layer);}
  @Override public Block getBlock(int x,int y,int z){return getBlock(x,y,z,0);}@Override public Block getBlock(int x,int y,int z,boolean load){return getBlock(x,y,z,0);}@Override public Block getBlock(int x,int y,int z,int layer,boolean load){return getBlock(x,y,z,layer);}
  @Override public Block getBlock(Vector3 p){return getBlock(p,0);}@Override public Block getBlock(Vector3 p,boolean load){return getBlock(p,0);}@Override public Block getBlock(Vector3 p,int layer){return getBlock(p.getFloorX(),p.getFloorY(),p.getFloorZ(),layer);}@Override public Block getBlock(Vector3 p,int layer,boolean load){return getBlock(p,layer);}
  @Override public BlockEntity getBlockEntity(Vector3 p){return super.getBlockEntityIfLoaded(p);}
  @Override public BlockEntity getBlockEntityIfLoaded(Vector3 p){return super.getBlockEntityIfLoaded(p);}
  Map<Integer,cn.nukkit.Player> observers = Map.of();
  @Override public Map<Integer,cn.nukkit.Player> getChunkPlayers(int x,int z){return observers;}@Override public cn.nukkit.level.ChunkLoader[] getChunkLoaders(int x,int z){return new cn.nukkit.level.ChunkLoader[0];}
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
  w.observers=Map.of();w.actors=seed.actors;w.trace=new ArrayList<>();
  NativeExplosionFixture.set(Level.class,w,"changedBlocks",new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
  NativeExplosionFixture.set(Level.class,w,"callbackBlockSet",new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<java.util.function.BiConsumer<Block,Block>>());
  w.chunk.setBlock(0,64,0,id,0);if(w.chunk.getProvider().getLevel()!=w)throw new AssertionError("canonical provider");return w;
 }
}
