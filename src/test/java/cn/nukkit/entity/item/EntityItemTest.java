package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockLiquid;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.entity.ItemBurnEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityItemTest {

 @BeforeAll static void initialize() { MockServer.init(); }

 @Nested
 class BurnEvent {

  private static EntityItem entity(PluginManager plugins) {
   EntityItem entity = mock(EntityItem.class);
   when(entity.onUpdate(anyInt())).thenCallRealMethod();
   Server server = mock(Server.class);
   when(server.getPluginManager()).thenReturn(plugins);
   setServer(entity, server);
   entity.lastUpdate = 10;
   return entity;
  }

  private static void setServer(EntityItem entity, Server server) {
   try {
    Field field = Entity.class.getDeclaredField("server");
    field.setAccessible(true);
    field.set(entity, server);
   } catch (ReflectiveOperationException failure) {
    throw new AssertionError(failure);
   }
  }

  @Test
  void aStackInFireIsReportedBeforeItIsRemoved() {
   PluginManager plugins = mock(PluginManager.class);
   EntityItem entity = entity(plugins);
   when(entity.isInsideOfFire()).thenReturn(true);

   assertTrue(entity.onUpdate(11));

   ArgumentCaptor<Event> fired = ArgumentCaptor.forClass(Event.class);
   var order = inOrder(plugins, entity);
   order.verify(plugins).callEvent(fired.capture());
   order.verify(entity).close();
   ItemBurnEvent event = assertInstanceOf(ItemBurnEvent.class, fired.getValue());
   assertSame(entity, event.getEntity());
  }

  @Test
  void aFireproofStackStaysSilent() {
   PluginManager plugins = mock(PluginManager.class);
   EntityItem entity = entity(plugins);
   entity.fireProof = true;
   when(entity.isInsideOfFire()).thenReturn(true);
   when(entity.entityBaseTick(anyInt())).thenReturn(false);
   when(entity.isAlive()).thenReturn(false);

   entity.onUpdate(11);

   verify(plugins, never()).callEvent(any(ItemBurnEvent.class));
  }
 }

 @Nested
 class FrictionLookup {
  private static EntityItem item(Level level) {
   EntityItem item = mock(EntityItem.class);
   when(item.onUpdate(anyInt())).thenCallRealMethod();
   when(item.isAlive()).thenReturn(true);
   when(item.getLevel()).thenReturn(level);
   when(item.getFloorX()).thenReturn(5);
   when(item.getFloorZ()).thenReturn(5);
   when(item.getDrag()).thenReturn(0.02f);
   item.level = level;
   item.x = 5;
   item.y = 10;
   item.z = 5;
   item.boundingBox = new SimpleAxisAlignedBB(5, 10, 5, 5.25, 10.25, 5.25);
   item.onGround = true;
   Block air = mock(Block.class);
   when(level.getBlock(5, 10, 5)).thenReturn(air);
   when(item.getLevelBlock()).thenReturn(air);
   when(level.getBlock(any(Vector3.class), eq(1))).thenReturn(air);
   return item;
  }

  @ParameterizedTest
  @CsvSource({"0,0", "0.00001,-0.00001", "-0.00001,0.00001", "NaN,0"})
  void restingOrSubthresholdStackDoesNotLookUpFloor(double x, double z) {
   Level level = mock(Level.class);
   EntityItem item = item(level);
   item.motionX = x;
   item.motionZ = z;
   item.motionY = 0.125;
   item.onUpdate(1);
   verify(level, never()).getBlock(5, 9, 5);
   verify(item).move(x, 0.125, z);
   verify(item).updateMovement();
   verify(item).entityBaseTick(1);
  }

  @ParameterizedTest
  @CsvSource({"true,false,0.6", "false,false,1.0", "true,true,0.8", "false,true,0.8"})
  void preservesGroundAirAndLiquidFriction(boolean grounded, boolean liquid, double expectedFactor) {
   Level level = mock(Level.class);
   EntityItem item = item(level);
   Block floor = liquid ? mock(BlockLiquid.class) : mock(Block.class);
   when(floor.getFrictionFactor()).thenReturn(0.6);
   when(level.getBlock(5, 9, 5)).thenReturn(floor);
   item.onGround = grounded;
   item.motionX = 0.25;
   item.motionZ = -0.5;
   item.onUpdate(1);
   double drag = 1 - 0.02f;
   assertEquals(0.25 * drag * expectedFactor, item.motionX, 1e-12);
   assertEquals(-0.5 * drag * expectedFactor, item.motionZ, 1e-12);
   verify(level).getBlock(5, 9, 5);
  }

  @ParameterizedTest
  @ValueSource(ints = {50, 100, 500})
  void noFrictionReadsForDenseStationaryDrops(int count) {
   Level level = mock(Level.class);
   for (int i = 0; i < count; i++) {
    EntityItem item = item(level);
    for (int tick = 1; tick <= 20; tick++) item.onUpdate(tick);
   }
   verify(level, never()).getBlock(5, 9, 5);
  }

 }

 @Nested
 class OptOut {

  private static EntityItem entity(boolean mergeable) {
   EntityItem entity = mock(EntityItem.class, CALLS_REAL_METHODS);
   entity.namedTag = new CompoundTag();
   entity.mergeItems = mergeable;
   return entity;
  }

  @Test
  void bothSidesMustAllowMerging() {
   assertTrue(entity(true).mergeableWith(entity(true)));
   assertFalse(entity(false).mergeableWith(entity(true)));
   assertFalse(entity(true).mergeableWith(entity(false)));
   assertFalse(entity(true).mergeableWith(null));
  }

  @Test
  void optOutIsReadFromTheSavedEntity() {
   assertTrue(EntityItem.mergeableFromNbt(new CompoundTag()));
   assertTrue(EntityItem.mergeableFromNbt(null));
   assertFalse(EntityItem.mergeableFromNbt(new CompoundTag().putBoolean("Mergeable", false)));
   assertTrue(EntityItem.mergeableFromNbt(new CompoundTag().putBoolean("Mergeable", true)));
  }

  @Test
  void setterWritesTheFlagOnlyWhileOptedOut() {
   EntityItem entity = entity(true);
   entity.setMergeable(false);
   assertFalse(entity.isMergeable());
   assertTrue(entity.namedTag.contains("Mergeable"));
   assertFalse(entity.namedTag.getBoolean("Mergeable"));

   entity.setMergeable(true);
   assertTrue(entity.isMergeable());
   assertFalse(entity.namedTag.contains("Mergeable"));
  }
 }

 @Nested
 class Construction {
  private Server server;
  private PluginManager originalPlugins;
  private PluginManager plugins;
  private FullChunk chunk;

  @BeforeEach
  void setUp() {
   MockServer.init();
   server = MockServer.get();
   originalPlugins = server.getPluginManager();
   plugins = mock(PluginManager.class);
   when(server.getPluginManager()).thenReturn(plugins);
   Level level = mock(Level.class);
   level.isBeingConverted = true;
   when(level.getServer()).thenReturn(server);
   when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
   LevelProvider provider = mock(LevelProvider.class);
   when(provider.getLevel()).thenReturn(level);
   chunk = mock(FullChunk.class);
   when(chunk.getProvider()).thenReturn(provider);
  }

  @AfterEach
  void restorePlugins() {
   when(server.getPluginManager()).thenReturn(originalPlugins);
  }

  private CompoundTag itemNbt() {
   return Entity.getDefaultNBT(new Vector3(1, 64, 1))
    .putShort("Health", 5)
    .putCompound("Item", NBTIO.putItemHelper(Item.get(Item.DIAMOND)));
  }

  @Test
  void savedOptOutSurvivesTheRealConstructorAndSave() {
   EntityItem entity = new EntityItem(chunk, itemNbt().putBoolean("Mergeable", false));
   assertFalse(entity.isClosed());
   assertFalse(entity.isMergeable());
   entity.saveNBT();
   assertTrue(entity.namedTag.contains("Mergeable"));
   assertFalse(entity.namedTag.getBoolean("Mergeable"));
  }

  @Test
  void missingOrExplicitTrueFlagKeepsOrdinaryDropsMergeable() {
   assertTrue(new EntityItem(chunk, itemNbt()).isMergeable());
   assertTrue(new EntityItem(chunk, itemNbt().putBoolean("Mergeable", true)).isMergeable());
  }

  @Test
  void itemSpawnListenerOptOutSurvivesConstruction() {
   assertSpawnListenerOptOut(ItemSpawnEvent.class);
  }

  @Test
  void entitySpawnListenerOptOutSurvivesConstruction() {
   assertSpawnListenerOptOut(EntitySpawnEvent.class);
  }

  private void assertSpawnListenerOptOut(Class<? extends EntityEvent> eventType) {
   AtomicBoolean called = new AtomicBoolean();
   doAnswer(call -> {
    Event event = call.getArgument(0);
    if (event.getClass() == eventType) {
     EntityItem entity = (EntityItem) ((EntityEvent) event).getEntity();
     assertTrue(entity.isMergeable(), "initEntity must establish the default before events");
     entity.setMergeable(false);
     called.set(true);
    }
    return null;
   }).when(plugins).callEvent(any(Event.class));
   EntityItem entity = new EntityItem(chunk, itemNbt());
   assertTrue(called.get());
   assertFalse(entity.isMergeable());
   assertFalse(entity.namedTag.getBoolean("Mergeable"));
  }
 }

 /** Native merge, item conservation and saved Age; terrain/network are a finite test registry. */
 @Nested
 @Timeout(10)
 @Execution(ExecutionMode.SAME_THREAD)
 class YoungestMerge {
  @BeforeEach void prepare() { MockServer.reset(); }

  @Test void freshDonorRetainsAllUnitsAndYoungestAgeInOlderReceiverAndSaveReload() throws Exception {
   World world = new World();
   EntityItem old = world.drop(0, 1_199);
   EntityItem fresh = world.drop(0, 0);
   world.merge(old, fresh);

   assertEquals(2, old.getItem().getCount());
   assertTrue(fresh.isClosed());
   assertFalse(old.isClosed());
   assertEquals(0, old.age);
   EntityItem loaded = world.reload(old);
   assertNotEquals(old.getId(), loaded.getId());
   assertTrue(old.isClosed());
   assertFalse(loaded.isClosed());
   assertEquals(2, loaded.getItem().getCount());
   assertEquals(0, loaded.age);
  }

  @Test void olderDonorKeepsYoungerReceiverAgeAndAllUnits() {
   World world = new World();
   EntityItem young = world.drop(0, 199);
   EntityItem old = world.drop(0, 1_199);
   world.merge(young, old);

   assertEquals(2, young.getItem().getCount());
   assertTrue(old.isClosed());
   assertFalse(young.isClosed());
   assertEquals(200, young.age);
  }

  /** Core item code stays real; the fixture supplies only a finite world registry. */
  private static final class World {
   final Level level = mock(Level.class);
   final LevelProvider provider = mock(LevelProvider.class);
   final Map<Long, FullChunk> chunks = new LinkedHashMap<>();
   final Map<Long, Map<Long, Entity>> registries = new LinkedHashMap<>();
   final Server server = MockServer.get();

   World() {
    level.isBeingConverted = true;
    when(level.getServer()).thenReturn(server);
    when(level.getChunkPlayers(org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt())).thenReturn(Map.of());
    when(level.getFolderName()).thenReturn("age34");
    when(level.getMinBlockY()).thenReturn(-64);
    when(level.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(new BlockAir());
    when(level.getBlock(any(Vector3.class), anyInt())).thenReturn(new BlockAir());
    doReturn(chunks).when(level).getChunks();
    when(level.getEntities()).thenAnswer(call -> registries.values().stream()
            .flatMap(map -> map.values().stream()).toArray(Entity[]::new));
    when(provider.getLevel()).thenReturn(level);
    when(server.getLevels()).thenReturn(Map.of(1, level));
    when(server.getLevelByName("age34")).thenReturn(level);
   }

   EntityItem drop(int x, int age) {
    return load(Entity.getDefaultNBT(new Vector3(x, 64, 0))
            .putShort("Health", 5)
            .putShort("Age", age)
            .putCompound("Item", NBTIO.putItemHelper(Item.get(Item.DIAMOND))));
   }

   EntityItem reload(EntityItem original) throws Exception {
    original.saveNBT();
    CompoundTag saved = NBTIO.read(NBTIO.write(original.namedTag, ByteOrder.LITTLE_ENDIAN),
            ByteOrder.LITTLE_ENDIAN);
    original.close();
    return load(saved);
   }

   void merge(EntityItem receiver, EntityItem donor) {
    receiver.onGround = true;
    donor.onGround = true;
    when(level.getNearbyEntities(any(cn.nukkit.math.AxisAlignedBB.class),
            eq(receiver), eq(false))).thenReturn(new Entity[] {donor});
    receiver.onUpdate(1);
   }

   private EntityItem load(CompoundTag tag) {
    int x = (int) Math.floor(tag.getList("Pos", cn.nukkit.nbt.tag.DoubleTag.class)
            .get(0).data);
    long hash = Level.chunkHash(x >> 4, 0);
    FullChunk chunk = chunks.computeIfAbsent(hash, ignored -> {
     FullChunk result = mock(FullChunk.class);
     Map<Long, Entity> entities = new LinkedHashMap<>();
     registries.put(hash, entities);
     when(result.getProvider()).thenReturn(provider);
     when(result.getX()).thenReturn(x >> 4);
     when(result.getZ()).thenReturn(0);
     when(result.getEntities()).thenReturn(entities);
     return result;
    });
    EntityItem item = new FiniteItem(chunk, tag);
    registries.get(hash).put(item.getId(), item);
    return item;
   }
  }

  private static final class FiniteItem extends EntityItem {
   FiniteItem(FullChunk chunk, CompoundTag tag) {
    super(chunk, tag);
   }

   @Override
   public void checkBlockCollision() {
    // No terrain in this registry: native item age/tick/save/close stay unchanged.
   }

   @Override
   public boolean isInsideOfFire() {
    return false;
   }

   @Override
   protected boolean checkObstruction(double x, double y, double z) {
    return false;
   }

   @Override
   public boolean move(double x, double y, double z) {
    return false;
   }

   @Override
   public void updateMovement() {}

   @Override
   public Block getLevelBlock() {
    return new BlockAir();
   }
  }
 }
}
