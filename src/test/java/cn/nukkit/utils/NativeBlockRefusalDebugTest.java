package cn.nukkit.utils;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.*;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Calls native denial paths; does not invoke a diagnostic helper directly. */
@Isolated("Changes the existing process-wide debug level")
class NativeBlockRefusalDebugTest {
    private int previousDebug;

    @BeforeEach
    void debugOn() {
        previousDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 2;
    }

    @AfterEach
    void restoreDebug() {
        Nukkit.DEBUG = previousDebug;
    }

    static Stream<Arguments> unsupportedBlocks() {
        return Stream.of(
                Arguments.of(Block.CACTUS, "cactus_support"),
                Arguments.of(Block.TORCH, "torch_support"),
                Arguments.of(Block.LADDER, "ladder_support"),
                Arguments.of(Block.RAIL, "rail_support"),
                Arguments.of(Block.CARPET, "carpet_support"),
                Arguments.of(Block.SNOW_LAYER, "snow_layer_support"),
                Arguments.of(Block.STONE_PRESSURE_PLATE, "pressure_plate_support"),
                Arguments.of(Block.DANDELION, "flower_support"),
                Arguments.of(Block.DEAD_BUSH, "dead_bush_support"),
                Arguments.of(Block.SAPLING, "sapling_support"));
    }

    @ParameterizedTest
    @MethodSource("unsupportedBlocks")
    void nativeSupportRefusalHasOneOptInDiagnostic(int id, String reason) throws Exception {
        Fixture f = new Fixture(id);
        assertFalse(f.place());
        verify(f.logger).debug(contains("reason=" + reason));
        f.noWrites();
        assertEquals(2, f.item.getCount());
        verifyNoInteractions(f.manager);
    }

    @ParameterizedTest
    @MethodSource("unsupportedBlocks")
    void defaultDebugDoesNotReadPlayerIdentityOrLog(int id, String reason) throws Exception {
        Fixture f = new Fixture(id);
        Nukkit.DEBUG = 1;
        clearInvocations(f.player);
        assertFalse(f.place());
        verify(f.player, never()).getUniqueId();
        verifyNoInteractions(f.logger);
        f.noWrites();
    }

    @Test
    void nativeAllowedPlacementDoesNotProduceADenial() throws Exception {
        Fixture f = new Fixture(Block.CARPET);
        doReturn(new BlockStone()).when(f.hand).down();
        assertTrue(f.place());
        verifyNoInteractions(f.logger);
        verify(f.level).setBlock(f.replacement, f.hand, true, true);
    }

    @Test
    void nonPlayerPlacementKeepsNativeRefusalWithoutLogging() throws Exception {
        Fixture f = new Fixture(Block.CACTUS);
        assertFalse(f.hand.place(f.item, f.replacement, f.target, BlockFace.UP, 0, 0, 0, null));
        verifyNoInteractions(f.logger);
        f.noWrites();
    }

    @Test
    void adventureBreakLogsBeforeAnyNativeEvent() throws Exception {
        Fixture f = new Fixture(Block.DANDELION);
        when(f.player.getGamemode()).thenReturn(Player.ADVENTURE);
        when(f.level.getBlock(any(Vector3.class))).thenReturn(f.target);
        doCallRealMethod().when(f.level).useBreakOn(any(Vector3.class), nullable(BlockFace.class),
                any(Item.class), any(Player.class), anyBoolean());
        assertNull(f.level.useBreakOn(f.position, BlockFace.UP, f.item, f.player, false));
        verify(f.logger).debug(contains("reason=adventure_can_destroy"));
        verifyNoInteractions(f.manager);
        f.noWrites();
    }

    @Test
    void adventurePlacementLogsOnlyAfterTheExistingEventStaysCancelled() throws Exception {
        Fixture f = new Fixture(Block.DANDELION);
        f.prepareLevelPlacement();
        when(f.player.getGamemode()).thenReturn(Player.ADVENTURE);
        assertNull(f.attempt());
        var order = inOrder(f.manager, f.logger);
        order.verify(f.manager, times(2)).callEvent(any(Event.class));
        order.verify(f.logger).debug(contains("reason=adventure_can_place_on"));
        f.noWrites();
        assertEquals(2, f.item.getCount());
    }

    @Test
    void pluginMayStillAllowTheExistingAdventurePlacement() throws Exception {
        Fixture f = new Fixture(Block.DANDELION);
        f.prepareLevelPlacement();
        when(f.player.getGamemode()).thenReturn(Player.ADVENTURE);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof BlockPlaceEvent event) event.setCancelled(false);
            return null;
        }).when(f.manager).callEvent(any(Event.class));
        assertNotNull(f.attempt());
        assertEquals(1, f.item.getCount());
        verifyNoInteractions(f.logger);
    }

    @ParameterizedTest
    @MethodSource("liquidPlants")
    void liquidRefusalUsesTheNativeLevelCaller(int id) throws Exception {
        Fixture f = new Fixture(id);
        f.prepareLevelPlacement();
        f.put(f.position.getSide(BlockFace.EAST), new BlockWater());
        assertNull(f.attempt());
        verify(f.logger).debug(contains("reason=flowable_liquid"));
        f.noWrites();
        assertEquals(2, f.item.getCount());
    }

    static Stream<Integer> liquidPlants() {
        return Stream.of(Block.SAPLING, Block.DANDELION, Block.WHEAT_BLOCK);
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final MainLogger logger = mock(MainLogger.class);
        final PluginManager manager = mock(PluginManager.class);
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final Vector3 position = new Vector3(4, 64, 7);
        final Block hand;
        final Block target = spy(new BlockStone());
        final Block replacement = spy(new BlockAir());
        final Item item;
        final Map<String, Block> world = new HashMap<>();

        Fixture(int id) throws Exception {
            hand = spy(Block.get(id));
            hand.level = level;
            hand.x = 4; hand.y = 65; hand.z = 7;
            target.level = level;
            replacement.level = level;
            doReturn(new BlockAir()).when(hand).down();
            item = new ItemBlock(hand, hand.getDamage(), 2);
            Field field = Level.class.getDeclaredField("server");
            field.setAccessible(true); field.set(level, server);
            field = Level.class.getDeclaredField("vibrationManager");
            field.setAccessible(true); field.set(level, mock(VibrationManager.class));
            when(level.getServer()).thenReturn(server);
            when(server.getLogger()).thenReturn(logger);
            when(server.getPluginManager()).thenReturn(manager);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOp()).thenReturn(true);
            when(player.getGamemode()).thenReturn(Player.SURVIVAL);
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenReturn(true);
        }

        boolean place() {
            return hand.place(item, replacement, replacement, BlockFace.UP, 0, 0, 0, player);
        }

        void prepareLevelPlacement() {
            put(position, target);
            put(position.getSide(BlockFace.EAST), replacement);
            put(position.getSide(BlockFace.EAST).getSide(BlockFace.DOWN), new BlockDirt());
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> at(call.getArgument(0), 0));
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(call ->
                    at(call.getArgument(0), call.getArgument(1)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                    at(new Vector3(call.<Integer>getArgument(0), call.<Integer>getArgument(1),
                            call.<Integer>getArgument(2)), call.getArgument(3)));
            when(level.isYInRange(anyInt())).thenReturn(true);
            doAnswer(call -> at(position.getSide(BlockFace.EAST), 0)).when(target).getSide(BlockFace.EAST);
            doReturn(true).when(hand).canPassThrough();
            doReturn(at(position.getSide(BlockFace.EAST).getSide(BlockFace.DOWN), 0)).when(hand).down();
            doCallRealMethod().when(level).useItemOn(any(Vector3.class), any(Item.class),
                    any(BlockFace.class), anyFloat(), anyFloat(), anyFloat(), any(Player.class), anyBoolean());
        }

        void put(Vector3 where, Block block) {
            block.x = where.x; block.y = where.y; block.z = where.z; block.level = level;
            world.put(key(where), block);
        }

        Block at(Vector3 where, int layer) {
            if (layer == 0 && world.containsKey(key(where))) return world.get(key(where));
            Block air = new BlockAir();
            air.x = where.x; air.y = where.y; air.z = where.z; air.level = level;
            return air;
        }

        static String key(Vector3 where) {
            return where.getFloorX() + ":" + where.getFloorY() + ":" + where.getFloorZ();
        }

        Item attempt() {
            return level.useItemOn(position, item, BlockFace.EAST, 0, 0, 0, player, false);
        }

        void noWrites() {
            verify(level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean());
            verify(level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
            verify(level, never()).setBlock(any(Vector3.class), anyInt(), any(Block.class), anyBoolean(), anyBoolean());
        }
    }
}
