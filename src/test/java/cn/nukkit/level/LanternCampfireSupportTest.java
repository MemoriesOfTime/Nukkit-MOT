package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCampfire;
import cn.nukkit.block.BlockLantern;
import cn.nukkit.item.Item;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LanternCampfireSupportTest {
    private static final int[] LANTERNS = {
            Block.LANTERN, Block.SOUL_LANTERN, Block.COPPER_LANTERN,
            Block.EXPOSED_COPPER_LANTERN, Block.WEATHERED_COPPER_LANTERN,
            Block.OXIDIZED_COPPER_LANTERN, Block.WAXED_COPPER_LANTERN,
            Block.WAXED_EXPOSED_COPPER_LANTERN, Block.WAXED_WEATHERED_COPPER_LANTERN,
            Block.WAXED_OXIDIZED_COPPER_LANTERN
    };
    private static final int[] CAMPFIRES = {Block.CAMPFIRE_BLOCK, Block.SOUL_CAMPFIRE_BLOCK};

    @BeforeAll
    static void init() {
        Block.init();
    }

    @Test
    void soulLanternFactoryRoundTripPreservesHangingMetadata() {
        BlockLantern hanging = (BlockLantern) Block.get(Block.SOUL_LANTERN, BlockLantern.HANGING_BIT);
        assertEquals(Block.SOUL_LANTERN, hanging.getId());
        assertEquals(BlockLantern.HANGING_BIT, hanging.getDamage());
        assertTrue(hanging.isHanging());

        BlockLantern standing = (BlockLantern) Block.get(Block.SOUL_LANTERN, 0);
        assertEquals(0, standing.getDamage());
        assertFalse(standing.isHanging());
    }

    static Stream<Arguments> campfireStates() {
        return Arrays.stream(CAMPFIRES).boxed().flatMap(id ->
                IntStream.range(0, 8).mapToObj(meta -> Arguments.of(id, meta)));
    }

    static Stream<Arguments> lanternCampfireStates() {
        return Arrays.stream(LANTERNS).boxed().flatMap(lantern ->
                Arrays.stream(CAMPFIRES).boxed().flatMap(campfire ->
                        IntStream.range(0, 8).mapToObj(meta -> Arguments.of(lantern, campfire, meta))));
    }

    @ParameterizedTest(name = "lantern={0}, campfire={1}, state={2}")
    @MethodSource("lanternCampfireStates")
    void downPlacementAndNormalUpdateAcceptEveryCampfireState(
            int lanternId, int campfireId, int meta) {
        Fixture f = new Fixture();
        f.put(0, 65, 0, campfireId, meta);

        assertTrue(f.place(lanternId, BlockFace.DOWN));
        assertHanging(f, lanternId);
        assertEquals(meta, f.block(0, 65, 0).getDamage());
        assertTrue(f.block(0, 65, 0).isTransparent(), "Campfire transparency must remain unchanged");
        f.normal();
        assertHanging(f, lanternId);
        assertTrue(f.drops.isEmpty());
        verify(f.level, never()).useBreakOn(any(Vector3.class));
    }

    @ParameterizedTest(name = "campfire={0}, initial state={1}")
    @MethodSource("campfireStates")
    void anExistingHangingLanternSurvivesExtinguishingAndRelighting(int campfireId, int meta) {
        Fixture f = new Fixture();
        f.put(0, 65, 0, campfireId, meta);
        f.put(0, 64, 0, Block.LANTERN, BlockLantern.HANGING_BIT);

        f.normal();
        assertHanging(f, Block.LANTERN);
        for (boolean extinguished : new boolean[] {true, false}) {
            BlockCampfire campfire = (BlockCampfire) f.block(0, 65, 0);
            campfire.setExtinguished(extinguished);
            f.put(0, 65, 0, campfireId, campfire.getDamage());
            f.normal();
            assertHanging(f, Block.LANTERN);
            assertEquals(meta & 3, f.block(0, 65, 0).getDamage() & 3);
        }
        assertTrue(f.drops.isEmpty());
        verify(f.level, never()).useBreakOn(any(Vector3.class));
    }

    @ParameterizedTest
    @MethodSource("campfireStates")
    void downClickPrefersTheCampfireCeilingEvenWithAValidFloor(int campfireId, int meta) {
        Fixture f = new Fixture();
        f.put(0, 65, 0, campfireId, meta);
        f.put(0, 63, 0, Block.STONE, 0);

        assertTrue(f.place(Block.LANTERN, BlockFace.DOWN));
        assertHanging(f, Block.LANTERN);
    }

    @ParameterizedTest
    @MethodSource("campfireStates")
    void upClickWithAValidFloorStillPlacesAStandingLantern(int campfireId, int meta) {
        Fixture f = new Fixture();
        f.put(0, 65, 0, campfireId, meta);
        f.put(0, 63, 0, Block.STONE, 0);

        assertTrue(f.place(Block.LANTERN, BlockFace.UP));
        assertFalse(((BlockLantern) f.block(0, 64, 0)).isHanging());
    }

    @ParameterizedTest
    @MethodSource("campfireStates")
    void removingCampfireSupportBreaksAndDropsTheLanternExactlyOnce(int campfireId, int meta) {
        Fixture f = new Fixture();
        f.put(0, 65, 0, campfireId, meta);
        f.put(0, 64, 0, Block.LANTERN, BlockLantern.HANGING_BIT);
        f.normal();
        assertHanging(f, Block.LANTERN);

        f.put(0, 65, 0, Block.AIR, 0);
        f.normal();
        f.normal();

        assertEquals(Block.AIR, f.block(0, 64, 0).getId());
        assertEquals(1, f.drops.size());
        assertEquals(Block.LANTERN, f.drops.get(0).getBlockUnsafe().getId());
        assertEquals(1, f.drops.get(0).getCount());
        verify(f.level, times(1)).useBreakOn(any(Vector3.class));
    }

    static Stream<Arguments> priorCeilingSupports() {
        return Stream.of(
                Arguments.of(Block.STONE, 0, true), Arguments.of(Block.AIR, 0, false),
                Arguments.of(Block.CHAIN_BLOCK, 0, true), Arguments.of(Block.FENCE, 0, true),
                Arguments.of(Block.IRON_BARS, 0, true), Arguments.of(Block.HOPPER_BLOCK, 0, true),
                Arguments.of(Block.STONE_SLAB, 0, true), Arguments.of(Block.STONE_SLAB, 8, false),
                Arguments.of(Block.COBBLESTONE_STAIRS, 0, true),
                Arguments.of(Block.COBBLESTONE_STAIRS, 4, true),
                Arguments.of(Block.GLASS, 0, false), Arguments.of(Block.WATER, 0, false),
                Arguments.of(Block.REDSTONE_BLOCK, 0, false));
    }

    @ParameterizedTest(name = "support={0}:{1}, allowed={2}")
    @MethodSource("priorCeilingSupports")
    void preexistingCeilingPlacementAndUpdateRulesStayUnchanged(int support, int meta, boolean allowed) {
        Fixture f = new Fixture();
        f.put(0, 65, 0, support, meta);
        assertEquals(allowed, f.place(Block.LANTERN, BlockFace.DOWN));
        if (allowed) assertHanging(f, Block.LANTERN);
        else assertEquals(Block.AIR, f.block(0, 64, 0).getId());

        f.put(0, 64, 0, Block.LANTERN, BlockLantern.HANGING_BIT);
        f.normal();
        assertEquals(allowed ? Block.LANTERN : Block.AIR, f.block(0, 64, 0).getId());
        assertEquals(allowed ? 0 : 1, f.drops.size());
    }

    @ParameterizedTest
    @MethodSource("campfireStates")
    void campfireDoesNotBecomeAValidFloor(int campfireId, int meta) {
        Fixture f = new Fixture();
        f.put(0, 63, 0, campfireId, meta);

        assertFalse(f.place(Block.LANTERN, BlockFace.UP));
        assertEquals(Block.AIR, f.block(0, 64, 0).getId());
    }

    @ParameterizedTest
    @MethodSource("campfireStates")
    void clickingUpCannotTurnAnUnsupportedLanternIntoAHangingOne(int campfireId, int meta) {
        Fixture f = new Fixture();
        f.put(0, 65, 0, campfireId, meta);

        assertFalse(f.place(Block.LANTERN, BlockFace.UP));
        assertEquals(Block.AIR, f.block(0, 64, 0).getId());
    }

    @ParameterizedTest
    @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "WEST", "EAST"})
    void aCampfireOnTheSideCannotSupportTheLantern(BlockFace face) {
        for (int campfire : CAMPFIRES) {
            Fixture f = new Fixture();
            BlockFace support = face.getOpposite();
            f.put(support.getXOffset(), 64, support.getZOffset(), campfire, 4);
            assertFalse(f.place(Block.LANTERN, face));
            assertEquals(Block.AIR, f.block(0, 64, 0).getId());
        }
    }

    @Test
    void waterAtEitherLanternLayerStillPreventsPlacement() {
        for (int layer = 0; layer <= 1; layer++) {
            Fixture f = new Fixture();
            f.put(0, 65, 0, Block.CAMPFIRE_BLOCK, 4);
            f.put(0, 64, 0, layer, Block.WATER, 0);
            assertFalse(f.place(Block.LANTERN, BlockFace.DOWN));
        }
    }

    private static void assertHanging(Fixture f, int id) {
        Block actual = f.block(0, 64, 0);
        assertEquals(id, actual.getId());
        assertTrue(((BlockLantern) actual).isHanging());
    }

    private record Cell(int x, int y, int z, int layer) {}

    /** Native placement/update and Level.useBreakOn; only terrain and external effects are bounded. */
    private static final class Fixture {
        final Level level = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final Map<Cell, Block> blocks = new HashMap<>();
        final List<Item> drops = new ArrayList<>();

        Fixture() {
            field("server", server);
            field("vibrationManager", mock(VibrationManager.class));
            level.gameRules = GameRules.getDefault();
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getServer()).thenReturn(server);
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> {
                Vector3 at = call.getArgument(0);
                return block(at.getFloorX(), at.getFloorY(), at.getFloorZ());
            });
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(call -> {
                Vector3 at = call.getArgument(0);
                return block(at.getFloorX(), at.getFloorY(), at.getFloorZ(), call.getArgument(1));
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                    block(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(call -> {
                        Vector3 at = call.getArgument(0);
                        Block next = call.getArgument(1);
                        put(at.getFloorX(), at.getFloorY(), at.getFloorZ(), next.getId(), next.getDamage());
                        return true;
                    });
            when(level.useBreakOn(any(Vector3.class))).thenCallRealMethod();
            when(level.useBreakOn(any(Vector3.class), nullable(Item.class))).thenCallRealMethod();
            when(level.useBreakOn(any(Vector3.class), nullable(Item.class), nullable(Player.class))).thenCallRealMethod();
            when(level.useBreakOn(any(Vector3.class), nullable(Item.class), nullable(Player.class), anyBoolean())).thenCallRealMethod();
            when(level.useBreakOn(any(Vector3.class), nullable(BlockFace.class), nullable(Item.class), nullable(Player.class), anyBoolean())).thenCallRealMethod();
            doAnswer(call -> {
                drops.add(((Item) call.getArgument(1)).clone());
                return null;
            }).when(level).dropItem(any(Vector3.class), any(Item.class));
        }

        boolean place(int id, BlockFace face) {
            BlockLantern lantern = (BlockLantern) positioned(id, 0, 0, 64, 0, 0);
            return lantern.place(lantern.toItem(), block(0, 64, 0),
                    lantern.getSide(face.getOpposite()), face, 0.5, 0.5, 0.5, null);
        }

        void normal() {
            block(0, 64, 0).onUpdate(Level.BLOCK_UPDATE_NORMAL);
        }

        void put(int x, int y, int z, int id, int meta) {
            put(x, y, z, 0, id, meta);
        }

        void put(int x, int y, int z, int layer, int id, int meta) {
            blocks.put(new Cell(x, y, z, layer), positioned(id, meta, x, y, z, layer));
        }

        Block block(int x, int y, int z) {
            return block(x, y, z, 0);
        }

        Block block(int x, int y, int z, int layer) {
            Block current = blocks.get(new Cell(x, y, z, layer));
            return current == null ? positioned(Block.AIR, 0, x, y, z, layer) : current.clone();
        }

        Block positioned(int id, int meta, int x, int y, int z, int layer) {
            Block value = Block.get(id, meta);
            value.x = x;
            value.y = y;
            value.z = z;
            value.layer = layer;
            value.level = level;
            return value;
        }

        void field(String name, Object value) {
            try {
                Field field = Level.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(level, value);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
