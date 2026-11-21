package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockAir;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockEntitySpawnerLocalScanTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @ParameterizedTest
    @CsvSource({"16,64,0,false,1", "16.001,64,0,false,0", "0,80,0,false,1",
            "0,80.001,0,false,0", "12,64,12,false,0", "0,64,0,true,0"})
    void activationKeepsTheInclusiveSphereAndSpectatorRule(double x, double y, double z,
                                                         boolean spectator, int attempts) {
        Fixture f = new Fixture(0, 0, 1);
        Player player = f.add(Player.class, x, y, z);
        when(player.isSpectator()).thenReturn(spectator);
        assertTrue(f.spawner.onUpdate());
        assertEquals(attempts, f.attempts);
        f.verifyLocalScan();
    }

    @ParameterizedTest
    @CsvSource({"16,64,0,0", "16.001,64,0,1", "0,80,0,0", "0,80.001,0,1",
            "12,64,12,1", "0,64,-16,0", "0,64,-16.001,1"})
    void countKeepsTheSphereInsteadOfCountingTheWholeChunkOrCube(double x, double y, double z,
                                                               int attempts) {
        Fixture f = new Fixture(0, 0, 1);
        f.add(Player.class, 0, 64, 0);
        f.add(BaseEntity.class, x, y, z);
        f.spawner.setMaxNearbyEntities(0);
        f.spawner.onUpdate();
        assertEquals(attempts, f.attempts);
        f.verifyLocalScan();
    }

    @Test
    void countsEveryBaseEntityWithoutNewTypeAliveOrClosedFilters() {
        Fixture f = new Fixture(0, 0, 1);
        f.add(Player.class, 0, 64, 0);
        BaseEntity mob = f.add(BaseEntity.class, 1, 64, 0);
        when(mob.getNetworkId()).thenReturn(999);
        when(mob.isAlive()).thenReturn(false);
        mob.closed = true;
        f.add(Entity.class, 0, 64, 0);
        f.spawner.setMaxNearbyEntities(1);
        f.spawner.onUpdate();
        assertEquals(1, f.attempts, "equality still permits a spawn; non-BaseEntity does not count");
        f.spawner.setMaxNearbyEntities(0);
        f.spawner.onUpdate();
        assertEquals(1, f.attempts, "another entity type, dead or closed, still counts");
        f.verifyLocalScan();
    }

    @Test
    void successfulSpawnsIncrementTheCountWithinTheBatch() {
        Fixture f = new Fixture(0, 0, 4);
        f.add(Player.class, 0, 64, 0);
        f.spawner.setMaxNearbyEntities(0);
        f.cancelEvents = false;
        Entity spawned = mock(Entity.class);
        try (MockedStatic<Entity> factory = mockStatic(Entity.class)) {
            factory.when(() -> Entity.createEntity(anyInt(), any(Position.class))).thenReturn(spawned);
            f.spawner.onUpdate();
        }
        assertEquals(1, f.attempts);
        verify(spawned).spawnToAll();
        f.verifyLocalScan();
    }

    @Test
    void cancelledSpawnsDoNotConsumeTheNearbyLimit() {
        Fixture f = new Fixture(0, 0, 4);
        f.add(Player.class, 0, 64, 0);
        f.spawner.setMaxNearbyEntities(0);
        f.spawner.onUpdate();
        assertEquals(4, f.attempts);
        f.verifyLocalScan();
    }

    @ParameterizedTest
    @CsvSource({"0,0,16", "-16,-16,16", "15,-1,16", "-17,15,-16", "0,0,0"})
    void visitsOnlyLoadedChunksCoveringTheCountRadius(int x, int z, int radius) {
        Fixture f = new Fixture(x, z, 1);
        f.spawner.setRequiredPlayerRange(radius);
        f.add(Player.class, x, 64, z);
        f.add(BaseEntity.class, x + 10000, 64, z + 10000);
        // A normal range probes every coordinate of its rectangle, including empty cells.
        f.spawner.onUpdate();
        Set<Long> expected = new HashSet<>();
        int r = Math.abs(radius);
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                expected.add(Level.chunkHash(cx, cz));
            }
        }
        assertEquals(expected, f.visited);
        assertEquals(1, f.attempts);
        f.verifyLocalScan();
    }

    @ParameterizedTest
    @ValueSource(ints = {32767, -32768, Integer.MAX_VALUE})
    void hugeRangesVisitOnlyLoadedChunksAndKeepTheClampedSquaredRadius(int radius) {
        Fixture f = new Fixture(0, 0, 1);
        f.spawner.setRequiredPlayerRange(radius);
        double effectiveRadius = Math.sqrt((int) Math.pow(radius, 2));
        f.add(Player.class, 0, 64, 0);
        f.add(BaseEntity.class, effectiveRadius - 0.01, 64, 0);
        f.add(BaseEntity.class, effectiveRadius + 0.01, 64, 0);
        f.add(BaseEntity.class, 100000, 64, 100000);
        f.spawner.setMaxNearbyEntities(1);
        f.spawner.onUpdate();
        assertEquals(1, f.attempts, "only the mob inside the original squared radius counts");
        Set<Long> expected = new HashSet<>();
        expected.add(Level.chunkHash(0, 0));
        expected.add(Level.chunkHash((int) (effectiveRadius - 0.01) >> 4, 0));
        expected.add(Level.chunkHash((int) effectiveRadius >> 4, 0));
        assertEquals(expected, f.visited, "empty coordinates and remote chunks must not be queried");
        f.verifyLocalScan();
    }

    @Test
    void negativeFractionalBoundaryCoversFloorAndTruncatedChunkIndices() {
        Fixture f = new Fixture(-17, -17, 1);
        f.spawner.x = -16.5;
        f.spawner.z = -16.5;
        f.add(Player.class, -16.5, 64, -16.5);
        f.add(BaseEntity.class, -0.5, 64, -16.5); // checkChunks() puts this in chunk x=0.
        BaseEntity floorIndexed = f.add(BaseEntity.class, -32.5, 64, -16.5);
        f.chunks.get(Level.chunkHash(-2, -1)).getEntities().values().remove(floorIndexed);
        f.chunk(-3, -2).getEntities().put(100L, floorIndexed);
        f.spawner.setMaxNearbyEntities(1);
        f.spawner.onUpdate();
        assertEquals(0, f.attempts, "both inclusive ends must be counted");
        f.verifyLocalScan();
    }

    @Test
    void matchesTheOldWorldScanAcrossDeterministicMixedWorlds() {
        Random random = new Random(0x5A7A);
        for (int sample = 0; sample < 40; sample++) {
            Fixture f = new Fixture(random.nextInt(65) - 32, random.nextInt(65) - 32, 1);
            int radius = new int[]{0, 4, 16, -16, 32}[sample % 5];
            f.spawner.setRequiredPlayerRange(radius);
            f.spawner.setMaxNearbyEntities(sample % 4);
            for (int i = 0; i < 20; i++) {
                Class<? extends Entity> type = i % 7 == 0 ? Player.class
                        : i % 3 == 0 ? Entity.class : BaseEntity.class;
                Entity entity = f.add(type, f.spawner.x + random.nextDouble() * 64 - 32,
                        64 + random.nextDouble() * 40 - 20, f.spawner.z + random.nextDouble() * 64 - 32);
                if (entity instanceof Player player) when(player.isSpectator()).thenReturn(i % 2 == 0);
            }
            if (sample % 2 == 0) f.add(Player.class, f.spawner.x, 64, f.spawner.z);
            boolean active = false;
            int nearby = 0;
            for (Entity entity : f.world) {
                if (!active && entity instanceof Player player && !player.isSpectator()) {
                    if (entity.distanceSquared(f.spawner) <= radius * radius) active = true;
                } else if (entity instanceof BaseEntity && entity.distanceSquared(f.spawner) <= radius * radius) {
                    nearby++;
                }
            }
            f.spawner.onUpdate();
            assertEquals(active && nearby <= sample % 4 ? 1 : 0, f.attempts, "sample " + sample);
            f.verifyLocalScan();
        }
    }

    @Test
    void closedAndDelayedSpawnersDoNotSearch() {
        Fixture f = new Fixture(0, 0, 1);
        f.spawner.closed = true;
        assertFalse(f.spawner.onUpdate());
        f.spawner.closed = false;
        f.spawner.setSpawnDelay(200, 200);
        assertTrue(f.spawner.onUpdate());
        verify(f.level, never()).getPlayers();
        assertTrue(f.visited.isEmpty());
        f.verifyLocalScan();
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Map<Long, Player> players = new LinkedHashMap<>();
        final Map<Long, BaseFullChunk> chunks = new HashMap<>();
        final Set<Long> visited = new HashSet<>();
        final List<Entity> world = new ArrayList<>();
        final BlockEntitySpawner spawner;
        boolean cancelEvents = true;
        int attempts;

        Fixture(int x, int z, int count) {
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getPlayers()).thenReturn(players);
            doAnswer(invocation -> new HashMap<>(chunks)).when(level).getChunks();
            when(level.getEntities()).thenAnswer(invocation -> world.toArray(new Entity[0]));
            when(level.getChunkEntities(anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(invocation -> {
                long key = Level.chunkHash(invocation.getArgument(0), invocation.getArgument(1));
                visited.add(key);
                return chunks.get(key);
            });
            when(level.getBlock(any(Vector3.class))).thenReturn(new BlockAir());
            doAnswer(invocation -> {
                CreatureSpawnEvent event = invocation.getArgument(0);
                attempts++;
                event.setCancelled(cancelEvents);
                return null;
            }).when(plugins).callEvent(any(CreatureSpawnEvent.class));
            FullChunk chunk = mock(FullChunk.class);
            LevelProvider provider = mock(LevelProvider.class);
            when(level.getProvider()).thenReturn(provider);
            when(chunk.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            spawner = new BlockEntitySpawner(chunk, new CompoundTag()
                    .putInt("x", x).putInt("y", 64).putInt("z", z).putInt("EntityId", 32)
                    .putShort("SpawnRange", 0).putShort("MinSpawnDelay", 0).putShort("MaxSpawnDelay", 0)
                    .putShort("MinimumSpawnerCount", count).putShort("MaximumSpawnerCount", 1));
        }

        BaseFullChunk chunk(int x, int z) {
            return chunks.computeIfAbsent(Level.chunkHash(x, z), key -> {
                BaseFullChunk chunk = mock(BaseFullChunk.class);
                when(chunk.getEntities()).thenReturn(new LinkedHashMap<>());
                return chunk;
            });
        }

        <T extends Entity> T add(Class<T> type, double x, double y, double z) {
            T entity = mock(type);
            entity.x = x;
            entity.y = y;
            entity.z = z;
            when(entity.distanceSquared(any(Vector3.class))).thenCallRealMethod();
            long id = world.size() + 1;
            world.add(entity);
            chunk((int) x >> 4, (int) z >> 4).getEntities().put(id, entity);
            if (entity instanceof Player player) players.put(id, player);
            return entity;
        }

        void verifyLocalScan() {
            verify(level, never()).getEntities();
            verify(level, never()).getChunk(anyInt(), anyInt());
            verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        }
    }
}
