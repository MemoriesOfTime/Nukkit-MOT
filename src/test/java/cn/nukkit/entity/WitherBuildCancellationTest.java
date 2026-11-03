package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockSkullWitherSkeleton;
import cn.nukkit.block.BlockSoulSand;
import cn.nukkit.entity.mob.EntityWither;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.parallel.Isolated("Native block registries and Server singleton")
class WitherBuildCancellationTest {
    @BeforeAll static void initializeNativeBlocks() { cn.nukkit.MockServer.init(); }

    @Test
    void cancellationPrecedesEveryStructureWriteAndEntityCreation() {
        Fixture f = new Fixture();
        AtomicInteger events = new AtomicInteger();
        doAnswer(call -> {
            CreatureSpawnEvent event = call.getArgument(0);
            assertEquals(EntityWither.NETWORK_ID, event.getEntityNetworkId());
            assertEquals(CreatureSpawnEvent.SpawnReason.BUILD_WITHER, event.getReason());
            assertSame(f.level, event.getPosition().getLevel());
            f.assertStructure();
            assertEquals(0, f.writes);
            events.incrementAndGet();
            event.setCancelled(true);
            return null;
        }).when(f.plugins).callEvent(any(CreatureSpawnEvent.class));
        try (MockedStatic<Server> servers = mockStatic(Server.class);
             MockedStatic<Entity> entities = mockStatic(Entity.class, CALLS_REAL_METHODS)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            assertFalse(EntityWither.checkAndSpawnWither(f.skull()));
            assertEquals(1, events.get());
            assertEquals(0, f.writes);
            f.assertStructure();
            entities.verifyNoInteractions();
        }
    }

    @Test
    void acceptedRitualKeepsOriginalSpawnPathAndRemovesStructure() {
        Fixture f = new Fixture();
        EntityWither wither = mock(EntityWither.class);
        when(wither.getLevel()).thenReturn(f.level);
        AtomicInteger events = new AtomicInteger();
        doAnswer(call -> { f.assertStructure(); events.incrementAndGet(); return null; })
                .when(f.plugins).callEvent(any(CreatureSpawnEvent.class));
        try (MockedStatic<Server> servers = mockStatic(Server.class);
             MockedStatic<Entity> entities = mockStatic(Entity.class, CALLS_REAL_METHODS)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            entities.when(() -> Entity.createEntity(eq("Wither"), same(f.chunk), any(CompoundTag.class)))
                    .thenReturn(wither);
            assertTrue(EntityWither.checkAndSpawnWither(f.skull()));
            assertEquals(1, events.get());
            assertEquals(java.util.List.of(
                    "0:66:0:0:" + Block.WITHER_SKELETON_SKULL + ":0",
                    "0:65:0:0:" + Block.SOUL_SAND + ":0",
                    "0:64:0:0:" + Block.SOUL_SAND + ":0",
                    "-1:65:0:0:" + Block.SOUL_SAND + ":0",
                    "-1:66:0:0:" + Block.WITHER_SKELETON_SKULL + ":0",
                    "0:65:0:0:" + Block.AIR + ":0",
                    "0:66:0:0:" + Block.AIR + ":0",
                    "1:65:0:0:" + Block.SOUL_SAND + ":0",
                    "1:66:0:0:" + Block.WITHER_SKELETON_SKULL + ":0"), f.removals,
                    "Keep stock nine-call order and AIR particle state for duplicate cells");
            assertTrue(f.cells.values().stream().allMatch(Block::isAir));
            verify(wither).spawnToAll();
            entities.verify(() -> Entity.createEntity(eq("Wither"), same(f.chunk), any(CompoundTag.class)), times(1));
        }
    }

    @Test
    void nestedAcceptedRitualCannotSpawnTwiceFromTheSameStructure() {
        Fixture f = new Fixture();
        EntityWither wither = mock(EntityWither.class);
        when(wither.getLevel()).thenReturn(f.level);
        AtomicInteger events = new AtomicInteger();
        doAnswer(call -> {
            if (events.incrementAndGet() == 1)
                assertTrue(EntityWither.checkAndSpawnWither(f.skull()));
            return null;
        }).when(f.plugins).callEvent(any(CreatureSpawnEvent.class));
        try (MockedStatic<Server> servers = mockStatic(Server.class);
             MockedStatic<Entity> entities = mockStatic(Entity.class, CALLS_REAL_METHODS)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            entities.when(() -> Entity.createEntity(eq("Wither"), same(f.chunk), any(CompoundTag.class)))
                    .thenReturn(wither);
            assertFalse(EntityWither.checkAndSpawnWither(f.skull()), "Nested build already consumed the structure");
            assertEquals(2, events.get());
            entities.verify(() -> Entity.createEntity(eq("Wither"), same(f.chunk), any(CompoundTag.class)), times(1));
            verify(wither, times(1)).spawnToAll();
        }
    }

    @Test
    void listenerReplacementIsPreservedWithoutPartialStructureConsumption() {
        Fixture f = new Fixture();
        doAnswer(call -> {
            f.put(-1, 66, 0, new cn.nukkit.block.BlockStone());
            return null;
        }).when(f.plugins).callEvent(any(CreatureSpawnEvent.class));
        try (MockedStatic<Server> servers = mockStatic(Server.class)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            assertFalse(EntityWither.checkAndSpawnWither(f.skull()));
            assertEquals(0, f.writes);
            assertEquals(Block.STONE, f.cell(-1, 66, 0).getId());
            assertEquals(Block.WITHER_SKELETON_SKULL, f.cell(0, 66, 0).getId());
            assertEquals(Block.SOUL_SAND, f.cell(0, 64, 0).getId());
        }
    }

    @Test
    void mutableLookupResultsCannotRetargetStructureRemoval() {
        Fixture f = new Fixture();
        EntityWither wither = mock(EntityWither.class);
        when(wither.getLevel()).thenReturn(f.level);
        doAnswer(call -> {
            Level foreign = mock(Level.class);
            for (Block lookup : f.lookups) {
                lookup.x = 999; lookup.y = -12; lookup.z = 1001; lookup.layer = 1;
                lookup.level = foreign;
                lookup.setDamage(7);
            }
            return null;
        }).when(f.plugins).callEvent(any(CreatureSpawnEvent.class));
        try (MockedStatic<Server> servers = mockStatic(Server.class);
             MockedStatic<Entity> entities = mockStatic(Entity.class, CALLS_REAL_METHODS)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            entities.when(() -> Entity.createEntity(eq("Wither"), same(f.chunk), any(CompoundTag.class)))
                    .thenReturn(wither);
            assertTrue(EntityWither.checkAndSpawnWither(f.skull()));
            assertTrue(f.cells.values().stream().allMatch(Block::isAir));
            entities.verify(() -> Entity.createEntity(eq("Wither"), same(f.chunk), any(CompoundTag.class)), times(1));
        }
    }

    @Test
    void placingPlayerIsExposedByCompatibleOverload() throws Throwable {
        Fixture f = new Fixture();
        Player creator = mock(Player.class);
        doAnswer(call -> {
            CreatureSpawnEvent event = call.getArgument(0);
            assertSame(creator, event.getCreator());
            event.setCancelled(true);
            return null;
        }).when(f.plugins).callEvent(any(CreatureSpawnEvent.class));
        try (MockedStatic<Server> servers = mockStatic(Server.class)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            try {
                var overload = EntityWither.class.getMethod("checkAndSpawnWither", Block.class, Player.class);
                assertEquals(false, overload.invoke(null, f.skull(), creator));
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
            f.assertStructure();
            assertEquals(0, f.writes);
        }
    }

    @Test
    void eventFailureDoesNotPartiallyDismantleStructure() {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("listener failed");
        doThrow(failure).when(f.plugins).callEvent(any(CreatureSpawnEvent.class));
        try (MockedStatic<Server> servers = mockStatic(Server.class)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> EntityWither.checkAndSpawnWither(f.skull())));
            f.assertStructure();
            assertEquals(0, f.writes);
        }
    }

    @Test
    void disabledOrIncompleteRitualDoesNotDispatchOrMutate() {
        Fixture f = new Fixture();
        try (MockedStatic<Server> servers = mockStatic(Server.class)) {
            servers.when(Server::getInstance).thenReturn(f.server);
            f.server.mobsFromBlocks = false;
            assertFalse(EntityWither.checkAndSpawnWither(f.skull()));
            f.server.mobsFromBlocks = true;
            f.put(0, 64, 0, new BlockAir());
            assertFalse(EntityWither.checkAndSpawnWither(f.skull()));
            verifyNoInteractions(f.plugins);
            assertEquals(0, f.writes);
        }
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final Level level = mock(Level.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final Map<String, Block> cells = new HashMap<>();
        final java.util.List<Block> lookups = new java.util.ArrayList<>();
        final java.util.List<String> removals = new java.util.ArrayList<>();
        int writes;

        Fixture() {
            server.mobsFromBlocks = true;
            when(server.getPluginManager()).thenReturn(plugins);
            GameRules rules = GameRules.getDefault();
            rules.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            when(level.getGameRules()).thenReturn(rules);
            when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                Block result = cell(call.getArgument(0), call.getArgument(1), call.getArgument(2));
                lookups.add(result);
                return result;
            });
            doAnswer(call -> {
                Block block = call.getArgument(0);
                assertSame(level, block.level);
                removals.add(block.getFloorX() + ":" + block.getFloorY() + ":" + block.getFloorZ()
                        + ":" + block.layer + ":" + block.getId() + ":" + block.getDamage());
                put(block.getFloorX(), block.getFloorY(), block.getFloorZ(), new BlockAir());
                writes++;
                return null;
            }).when(level).breakBlock(any(Block.class));
            for (int x = -1; x <= 1; x++) {
                put(x, 66, 0, new BlockSkullWitherSkeleton());
                put(x, 65, 0, new BlockSoulSand());
            }
            put(0, 64, 0, new BlockSoulSand());
        }

        Block skull() { return cell(0, 66, 0); }
        Block cell(int x, int y, int z) {
            Block block = cells.get(x + ":" + y + ":" + z);
            if (block == null) {
                block = new BlockAir();
                block.x = x; block.y = y; block.z = z; block.level = level;
            }
            return block.clone();
        }
        void put(int x, int y, int z, Block block) {
            block.x = x; block.y = y; block.z = z; block.level = level;
            cells.put(x + ":" + y + ":" + z, block);
        }
        void assertStructure() {
            for (int x = -1; x <= 1; x++) {
                assertEquals(Block.WITHER_SKELETON_SKULL, cell(x, 66, 0).getId());
                assertEquals(Block.SOUL_SAND, cell(x, 65, 0).getId());
            }
            assertEquals(Block.SOUL_SAND, cell(0, 64, 0).getId());
        }
    }
}
