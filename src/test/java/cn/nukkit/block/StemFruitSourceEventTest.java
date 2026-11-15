package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StemFruitSourceEventTest {
    @BeforeAll
    static void initialize() { Block.init(); }

    @Test
    void legacyConstructorPreservesTargetAndStateWithoutInventingSource() {
        Block target = Block.get(Block.AIR), fruit = Block.get(Block.MELON_BLOCK);
        BlockGrowEvent event = new BlockGrowEvent(target, fruit);
        assertSame(target, event.getBlock());
        assertSame(fruit, event.getNewState());
        assertNull(sourceOf(event));
    }

    @Test
    void explicitConstructorPreservesAllThreeIdentities() {
        Block target = Block.get(Block.AIR), stem = Block.get(Block.MELON_STEM);
        Block fruit = Block.get(Block.MELON_BLOCK);
        BlockGrowEvent event = explicitEvent(target, stem, fruit);
        assertSame(target, event.getBlock());
        assertSame(stem, sourceOf(event));
        assertSame(fruit, event.getNewState());
        event.setCancelled();
        assertTrue(event.isCancelled());
        assertSame(stem, sourceOf(event));
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.MELON_STEM, Block.PUMPKIN_STEM})
    void nativeFruitEventIdentifiesActualStemWithoutChangingTarget(int stem) {
        try (Fixture f = new Fixture(stem, 7, false)) {
            f.source.onUpdate(Level.BLOCK_UPDATE_RANDOM);
            assertEquals(1, f.events.size());
            BlockGrowEvent event = f.events.get(0);
            assertSame(f.source, sourceOf(event));
            assertSame(f.level, event.getBlock().getLevel());
            assertEquals(Block.AIR, event.getBlock().getId());
            assertEquals(64, event.getBlock().getFloorY());
            assertEquals(1, Math.abs(event.getBlock().getFloorX()) + Math.abs(event.getBlock().getFloorZ()));
            assertEquals(f.fruit, event.getNewState().getId());
            assertNull(event.getNewState().getLevel(), "keep the existing unbound new-state contract");
            assertEquals(1, f.writes.stream().filter(block -> block.getId() == f.fruit).count());
            assertEquals(f.fruit, f.at(event.getBlock()).getId());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.MELON_STEM, Block.PUMPKIN_STEM})
    void cancelledNativeFruitLeavesWorldAndStemUnchanged(int stem) {
        try (Fixture f = new Fixture(stem, 7, true)) {
            int before = f.source.getDamage();
            f.source.onUpdate(Level.BLOCK_UPDATE_RANDOM);
            assertEquals(1, f.events.size());
            assertSame(f.source, sourceOf(f.events.get(0)));
            assertTrue(f.events.get(0).isCancelled());
            assertTrue(f.writes.isEmpty());
            assertEquals(before, f.source.getDamage());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.MELON_STEM, Block.PUMPKIN_STEM})
    void sameCellAgeEventKeepsLegacyContract(int stem) {
        try (Fixture f = new Fixture(stem, 0, false)) {
            f.source.onUpdate(Level.BLOCK_UPDATE_RANDOM);
            assertEquals(1, f.events.size());
            BlockGrowEvent event = f.events.get(0);
            assertSame(f.source, event.getBlock());
            assertNull(sourceOf(event));
            assertEquals(stem, event.getNewState().getId());
            assertEquals(1, f.writes.size());
            assertEquals(stem, f.writes.get(0).getId());
        }
    }

    private static Block sourceOf(BlockGrowEvent event) {
        try {
            return (Block) BlockGrowEvent.class.getMethod("getSource").invoke(event);
        } catch (ReflectiveOperationException missingSourceApi) {
            throw new AssertionError("Growth events must expose the actual optional source", missingSourceApi);
        }
    }

    private static BlockGrowEvent explicitEvent(Block target, Block source, Block newState) {
        try {
            return BlockGrowEvent.class.getConstructor(Block.class, Block.class, Block.class)
                    .newInstance(target, source, newState);
        } catch (ReflectiveOperationException missingSourceApi) {
            throw new AssertionError("Growth events must accept an explicit source", missingSourceApi);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Level level = mock(Level.class);
        final Block source;
        final int fruit;
        final List<BlockGrowEvent> events = new ArrayList<>();
        final List<Block> writes = new ArrayList<>();
        final Map<String, Block> cells = new HashMap<>();
        final MockedStatic<Server> serverScope;
        final MockedStatic<Utils> randomScope;

        Fixture(int stem, int age, boolean cancelled) {
            source = positioned(Block.get(stem, age), new Vector3(0, 64, 0));
            fruit = stem == Block.MELON_STEM ? Block.MELON_BLOCK : Block.PUMPKIN;
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> at(c.getArgument(0)));
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c -> at(new Vector3(
                    c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> at(new Vector3(
                    c.getArgument(0, Integer.class), c.getArgument(1, Integer.class), c.getArgument(2, Integer.class))));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0);
                Block block = positioned(c.getArgument(1, Block.class).clone(), pos);
                writes.add(block); cells.put(key(pos), block); return true;
            });
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            serverScope = mockStatic(Server.class);
            serverScope.when(Server::getInstance).thenReturn(server);
            randomScope = mockStatic(Utils.class, CALLS_REAL_METHODS);
            randomScope.when(() -> Utils.rand(1, 2)).thenReturn(1);
            doAnswer(c -> {
                BlockGrowEvent event = c.getArgument(0);
                events.add(event);
                if (cancelled) event.setCancelled();
                return null;
            }).when(plugins).callEvent(any(Event.class));
        }

        Block at(Vector3 pos) {
            return cells.computeIfAbsent(key(pos), ignored -> positioned(
                    Block.get(pos.getFloorY() == 63 ? Block.DIRT : Block.AIR), pos));
        }

        Block positioned(Block block, Vector3 pos) {
            block.setLevel(level); block.setComponents(pos.x, pos.y, pos.z); return block;
        }

        static String key(Vector3 pos) { return pos.getFloorX() + ":" + pos.getFloorY() + ":" + pos.getFloorZ(); }

        @Override
        public void close() { randomScope.close(); serverScope.close(); }
    }
}
