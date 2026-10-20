package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkReadTicket;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real LevelDB, chunk, chest inventory and item-entity lifecycle. Level storage bookkeeping
 * is real; unrelated world ticking, clients, comparator and hopper work are mocked.
 */
class LevelDBChunkLiveReloadTest {
    private static final int X = 9;
    private static final int Z = -4;
    private static final long HASH = Level.chunkHash(X, Z);
    private static final UUID ENTITY_UUID = UUID.fromString("ea4072f4-a56f-4b5e-85e2-78d4cb648723");
    private static final long UNIQUE_ID = 987654321L;

    @TempDir Path directory;
    private Server server;
    private PluginManager originalPlugins;
    private boolean previousAsync;
    private boolean previousCompletion;
    private LevelDBProvider provider;
    private ExecutorService readers;
    private Level level;
    private final Long2ObjectNonBlockingMap<Entity> liveEntities = new Long2ObjectNonBlockingMap<>();
    private final Long2ObjectNonBlockingMap<BlockEntity> liveBlockEntities = new Long2ObjectNonBlockingMap<>();
    private final List<Entity> spawned = new ArrayList<>();
    private Map<String, Class<? extends Entity>> entityRegistry;
    private Map<String, Class<? extends Entity>> originalEntityRegistry;
    private Map<String, String> entityNames;
    private Map<String, String> originalEntityNames;
    private Map<String, Class<? extends BlockEntity>> tileRegistry;
    private Map<String, Class<? extends BlockEntity>> originalTileRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        MockServer.init();
        this.server = Server.getInstance();
        this.previousAsync = this.server.asyncChunkSending;
        this.previousCompletion = this.server.asyncChunkLoadCompletion;
        this.server.asyncChunkSending = true;
        this.server.asyncChunkLoadCompletion = true;
        this.server.levelDbCache = 8;
        this.server.useNativeLevelDB = false;
        this.server.maxPendingChunkWrites = 128;
        this.originalPlugins = this.server.getPluginManager();
        PluginManager plugins = mock(PluginManager.class);
        when(this.server.getPluginManager()).thenReturn(plugins);
        Thread main = Thread.currentThread();
        doAnswer(call -> {
            assertSame(main, Thread.currentThread(), "entity lifecycle must never run on the disk reader");
            Event event = call.getArgument(0);
            if (event instanceof EntitySpawnEvent spawn) this.spawned.add(spawn.getEntity());
            return null;
        }).when(plugins).callEvent(any(Event.class));

        this.entityRegistry = (Map<String, Class<? extends Entity>>) field(Entity.class, null, "knownEntities");
        this.originalEntityRegistry = new HashMap<>(this.entityRegistry);
        this.entityNames = (Map<String, String>) field(Entity.class, null, "shortNames");
        this.originalEntityNames = new HashMap<>(this.entityNames);
        this.tileRegistry = (Map<String, Class<? extends BlockEntity>>) field(BlockEntity.class, null, "knownBlockEntities");
        this.originalTileRegistry = new HashMap<>(this.tileRegistry);
        assertTrue(Entity.registerEntity("Item", EntityItem.class));
        assertTrue(BlockEntity.registerBlockEntity(BlockEntity.CHEST, BlockEntityChest.class));

        this.level = mock(Level.class);
        when(this.level.getServer()).thenReturn(this.server);
        when(this.level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        when(this.level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        when(this.level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
        field(Level.class, this.level, "entities", this.liveEntities);
        field(Level.class, this.level, "blockEntities", this.liveBlockEntities);
        field(Level.class, this.level, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
        field(Level.class, this.level, "updateBlockEntities", new ConcurrentLinkedQueue<BlockEntity>());
        doCallRealMethod().when(this.level).addEntity(any(Entity.class));
        doCallRealMethod().when(this.level).removeEntity(any(Entity.class));
        doCallRealMethod().when(this.level).addBlockEntity(any(BlockEntity.class));
        doCallRealMethod().when(this.level).removeBlockEntity(any(BlockEntity.class));
        doCallRealMethod().when(this.level).scheduleBlockEntityUpdate(any(BlockEntity.class));
        LevelDBProvider.generate(this.directory.toString(), "live-reload", 404L, Flat.class);
        this.provider = new LevelDBProvider(this.level, this.directory.toString());
        when(this.level.getProvider()).thenReturn(this.provider);
        this.readers = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        try {
            if (this.readers != null) this.readers.shutdownNow();
            if (this.provider != null) this.provider.close();
        } finally {
            if (this.server != null) {
                this.server.asyncChunkSending = this.previousAsync;
                this.server.asyncChunkLoadCompletion = this.previousCompletion;
                when(this.server.getPluginManager()).thenReturn(this.originalPlugins);
            }
            if (this.originalEntityRegistry != null) {
                this.entityRegistry.clear();
                this.entityRegistry.putAll(this.originalEntityRegistry);
            }
            if (this.originalEntityNames != null) {
                this.entityNames.clear();
                this.entityNames.putAll(this.originalEntityNames);
            }
            if (this.originalTileRegistry != null) {
                this.tileRegistry.clear();
                this.tileRegistry.putAll(this.originalTileRegistry);
            }
        }
    }

    @Test
    void twoBlockedUnloadReloadCyclesPreserveLiveChestAndEntityWithoutDuplication() throws Exception {
        LevelDBChunk chunk = this.provider.getEmptyChunk(X, Z);
        chunk.setGenerated(true);
        chunk.setBlock(0, 64, 0, Block.CHEST);
        this.provider.setChunk(X, Z, chunk);
        chunk.initChunk();
        BlockEntityChest chest = new BlockEntityChest(chunk, new CompoundTag().putString("id", BlockEntity.CHEST)
                .putInt("x", X << 4).putInt("y", 64).putInt("z", Z << 4)
                .putList(new ListTag<CompoundTag>("Items")));
        Item diamonds = Item.get(Item.DIAMOND, 0, 7).setCustomName("live-diamonds");
        diamonds.setLore("retain item NBT");
        chest.getRealInventory().setItem(0, diamonds);
        chest.getRealInventory().setItem(13, Item.get(Item.IRON_SWORD, 12, 1).setCustomName("used-sword"));
        chest.getRealInventory().setItem(26, Item.get(Item.EMERALD, 0, 4));
        Item dropped = Item.get(Item.GOLD_INGOT, 0, 2).setCustomName("live-entity-stack");
        EntityItem entity = new EntityItem(chunk,
                Entity.getDefaultNBT(new Vector3((X << 4) + 2.5, 65, (Z << 4) + 2.5))
                        .putString("id", "Item").putString("uuid", ENTITY_UUID.toString()).putLong("UniqueID", UNIQUE_ID)
                        .putShort("Health", 5).putShort("PickupDelay", 200).putBoolean("Mergeable", false)
                        .putCompound("Item", NBTIO.putItemHelper(dropped)));
        assertEquals(1, this.spawned.size());
        assertLive(chunk, chest, entity);

        for (int cycle = 0; cycle < 2; cycle++) {
            if (cycle == 1) {
                chest.getRealInventory().setItem(0, Item.get(Item.DIAMOND, 0, 3).setCustomName("latest-snapshot"));
                chest.getRealInventory().clear(26);
                chest.getRealInventory().setItem(5, Item.get(Item.GOLD_INGOT, 0, 6));
            }
            Map<Integer, CompoundTag> beforeItems = inventorySnapshot(chest);
            CompoundTag beforeEntityItem = NBTIO.putItemHelper(entity.getItem());
            UUID beforeUuid = entity.getUniqueId();
            long beforeUniqueId = entity.namedTag.getLong("UniqueID");
            BlockEntityChest previousChest = chest;
            EntityItem previousEntity = entity;
            LevelDBChunk previousChunk = chunk;
            chunk = unloadAndReload(chunk, previousChest, previousEntity);
            chest = assertInstanceOf(BlockEntityChest.class, chunk.getBlockEntities().values().iterator().next());
            entity = assertInstanceOf(EntityItem.class, chunk.getEntities().values().iterator().next());
            assertNotSame(previousChunk, chunk);
            assertNotSame(previousChest, chest);
            assertNotSame(previousEntity, entity);
            assertNotEquals(previousEntity.getId(), entity.getId(), "runtime instance must be new while persistent identity remains");
            assertEquals(beforeItems, inventorySnapshot(chest));
            assertEquals(beforeEntityItem, NBTIO.putItemHelper(entity.getItem()));
            assertEquals(beforeUuid, entity.getUniqueId());
            assertEquals(beforeUniqueId, entity.namedTag.getLong("UniqueID"));
            assertFalse(entity.isMergeable());
            assertEquals(cycle + 2, this.spawned.size());
            assertLive(chunk, chest, entity);

            chunk.initChunk();
            assertEquals(cycle + 2, this.spawned.size(), "a second init must not create another live entity");
            assertLive(chunk, chest, entity);
            assertEquals(beforeItems, inventorySnapshot(chest));
        }
    }

    private LevelDBChunk unloadAndReload(LevelDBChunk old, BlockEntityChest oldChest, EntityItem oldEntity) throws Exception {
        DB real = this.provider.getDatabase();
        DB delayed = mock(DB.class, AdditionalAnswers.delegatesTo(real));
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            writing.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS), "test must release the owned write");
            real.write(call.getArgument(0, WriteBatch.class));
            return null;
        }).when(delayed).write(any(WriteBatch.class));
        field(LevelDBProvider.class, this.provider, "db", delayed);
        try {
            assertTrue(assertTimeout(Duration.ofMillis(500), () -> this.provider.unloadChunk(X, Z, false)));
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            assertTrue(oldChest.closed);
            assertTrue(oldChest.getRealInventory().destroyed);
            assertTrue(oldEntity.isClosed());
            assertNull(old.getProvider());
            assertTrue(old.getEntities().isEmpty());
            assertTrue(old.getBlockEntities().isEmpty());
            assertTrue(this.liveEntities.isEmpty());
            assertTrue(this.liveBlockEntities.isEmpty());
            assertTrue(this.level.updateEntities.isEmpty());
            assertNull(this.provider.getLoadedChunk(HASH));
            try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
                CountDownLatch reading = new CountDownLatch(1);
                CompletableFuture<BaseFullChunk> read = CompletableFuture.supplyAsync(() -> {
                    reading.countDown();
                    return ticket.read();
                }, this.readers);
                assertTrue(reading.await(5, TimeUnit.SECONDS));
                assertFalse(read.isDone(), "read must await the in-flight write ACK");
                release.countDown();
                LevelDBChunk decoded = assertInstanceOf(LevelDBChunk.class, read.get(10, TimeUnit.SECONDS));
                assertFalse(decoded.isInitialized());
                assertTrue(decoded.getEntities().isEmpty(), "disk decode must not instantiate live entities");
                assertTrue(decoded.getBlockEntities().isEmpty());
                assertTrue(this.liveEntities.isEmpty());
                assertTrue(this.liveBlockEntities.isEmpty());
                assertSame(decoded, ticket.tryMount(decoded));
                decoded.initChunk();
                assertTrue(decoded.isInitialized());
                return decoded;
            }
        } finally {
            release.countDown();
            ExecutorService writer = (ExecutorService) field(LevelDBProvider.class, this.provider, "executor");
            writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
            field(LevelDBProvider.class, this.provider, "db", real);
        }
    }

    private void assertLive(LevelDBChunk chunk, BlockEntityChest chest, EntityItem entity) {
        assertSame(chunk, this.provider.getLoadedChunk(HASH));
        assertEquals(1, this.provider.getLoadedChunks().size());
        assertEquals(1, chunk.getEntities().size());
        assertEquals(1, chunk.getBlockEntities().size());
        assertEquals(1, this.liveEntities.size());
        assertEquals(1, this.liveBlockEntities.size());
        assertEquals(1, this.level.updateEntities.size());
        assertSame(entity, chunk.getEntities().get(entity.getId()));
        assertSame(entity, this.liveEntities.get(entity.getId()));
        assertSame(entity, this.level.updateEntities.get(entity.getId()));
        assertSame(chest, chunk.getBlockEntities().get(chest.getId()));
        assertSame(chest, this.liveBlockEntities.get(chest.getId()));
        assertSame(chunk, entity.chunk);
        assertSame(chunk, chest.chunk);
        assertFalse(entity.isClosed());
        assertFalse(chest.closed);
        assertTrue(chunk.getPreservedEntityActors().isEmpty(), "the entity must be live, not retained raw NBT");
        assertEquals(ENTITY_UUID, entity.getUniqueId());
        assertEquals(UNIQUE_ID, entity.namedTag.getLong("UniqueID"));
    }

    private static Map<Integer, CompoundTag> inventorySnapshot(BlockEntityChest chest) {
        Map<Integer, CompoundTag> items = new HashMap<>();
        chest.getRealInventory().getContents().forEach((slot, item) -> items.put(slot, NBTIO.putItemHelper(item, slot)));
        return items;
    }

    private static Object field(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void field(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
