package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.level.generator.populator.impl.tree.SpruceBigTreePopulator;
import cn.nukkit.level.generator.populator.impl.tree.SpruceMegaTreePopulator;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(20)
class BlockSaplingTest {

    @BeforeAll
    static void initialize() { Block.init(); }

    /** Actual random-tick/generator path; only tick selection and world storage are controlled. */
    @ParameterizedTest
    @ValueSource(strings = {"obstruction", "soil", "ceiling"})
    void rejectedNaturalGrowthPreservesSourceAndEntireWorld(String reason) {
        AcaciaWorld f = new AcaciaWorld(reason.equals("ceiling") ? 253 : 64);
        f.put(0, f.sourceY, 0, Block.SAPLING, AcaciaWorld.AGED_ACACIA);
        if (reason.equals("obstruction")) f.put(0, f.sourceY + 1, 0, Block.STONE, 0);
        if (reason.equals("soil")) f.put(0, f.sourceY - 1, 0, Block.STONE, 0);
        Map<Cell, String> before = snapshot(f);

        randomTick(f, 1);

        assertEquals(Block.SAPLING, f.block(0, f.sourceY, 0).getId());
        assertEquals(AcaciaWorld.AGED_ACACIA, f.block(0, f.sourceY, 0).getDamage());
        assertEquals(before, snapshot(f), "generator refusal must not remove the sapling or change other cells");
        assertEquals(2, f.writes, "only remove and restore the source; rejected generator must not write");
        ArgumentCaptor<Vector3> positions = ArgumentCaptor.forClass(Vector3.class);
        ArgumentCaptor<Block> states = ArgumentCaptor.forClass(Block.class);
        verify(f.level, times(2)).setBlock(positions.capture(), states.capture(), anyBoolean(), anyBoolean());
        assertEquals(Block.AIR, states.getAllValues().get(0).getId());
        assertSame(positions.getAllValues().get(0), positions.getAllValues().get(1));
        assertSame(positions.getAllValues().get(0), states.getAllValues().get(1), "restore the original sapling instance");
    }

    @Test
    void blockedSaplingCanGrowOnALaterTickAfterObstacleIsRemoved() {
        AcaciaWorld f = new AcaciaWorld();
        f.put(0, 64, 0, Block.SAPLING, AcaciaWorld.AGED_ACACIA);
        f.put(0, 65, 0, Block.STONE, 0);
        randomTick(f, 1);
        assertEquals(Block.SAPLING, f.block(0, 64, 0).getId());
        f.put(0, 65, 0, Block.AIR, 0);
        randomTick(f, 1);
        assertAcaciaTrunk(f);
    }

    @Test
    void successfulNaturalGrowthKeepsItsAcaciaTrunk() {
        AcaciaWorld f = new AcaciaWorld();
        f.put(0, 64, 0, Block.SAPLING, AcaciaWorld.AGED_ACACIA);
        randomTick(f, 1);
        assertAcaciaTrunk(f);
    }

    @Test
    void youngAcaciaOnlyAgesOnItsFirstSelectedTick() {
        AcaciaWorld f = new AcaciaWorld();
        f.put(0, 64, 0, Block.SAPLING, BlockSapling.ACACIA);
        randomTick(f, 1);
        assertEquals(Block.SAPLING, f.block(0, 64, 0).getId());
        assertEquals(AcaciaWorld.AGED_ACACIA, f.block(0, 64, 0).getDamage());
        assertEquals(1, f.writes);
    }

    @Test
    void unselectedTickDoesNotTouchTheWorld() {
        AcaciaWorld f = new AcaciaWorld();
        f.put(0, 64, 0, Block.SAPLING, AcaciaWorld.AGED_ACACIA);
        Map<Cell, String> before = snapshot(f);
        randomTick(f, 2);
        assertEquals(before, snapshot(f));
        assertEquals(0, f.writes);
    }

    @Test
    void otherSpeciesKeepTheirExistingNaturalGrowthPath() {
        AcaciaWorld f = new AcaciaWorld();
        f.put(0, 64, 0, Block.SAPLING, BlockSapling.OAK | BlockSapling.AGED_BIT);
        randomTick(f, 1);
        assertEquals(Block.WOOD, f.block(0, 64, 0).getId());
        assertEquals(BlockSapling.OAK, f.block(0, 64, 0).getDamage() & 3);
    }

    private static void randomTick(AcaciaWorld f, int selectedRoll) {
        try (MockedStatic<Utils> random = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            random.when(() -> Utils.rand(1, 7)).thenReturn(selectedRoll);
            f.sapling().onUpdate(Level.BLOCK_UPDATE_RANDOM);
        }
    }

    private static void assertAcaciaTrunk(AcaciaWorld f) {
        assertEquals(Block.WOOD2, f.block(0, 64, 0).getId());
        assertEquals(BlockSapling.ACACIA - 4, f.block(0, 64, 0).getDamage() & 3);
    }

    /** Actual small/large jungle generators with controlled world storage. */
    @ParameterizedTest
    @ValueSource(ints = {BlockSapling.JUNGLE})
    void singleJungleGrowsAtItsSource(int metadata) {
        JungleWorld f = new JungleWorld();
        f.put(0, 64, 0, Block.SAPLING, metadata);
        assertTrue(f.sapling().grow());
        assertJungleTrunk(f, 0, 0);
        assertEquals(Block.AIR, f.block(-2, 64, -2).getId(), "failed square-search offsets must not move the trunk");
        assertEquals(Block.GRASS, f.block(-2, 63, -2).getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockSapling.JUNGLE})
    void blockedSingleJungleKeepsSourceAndDoesNotGrowElsewhere(int metadata) {
        JungleWorld f = new JungleWorld();
        f.put(0, 64, 0, Block.SAPLING, metadata);
        f.put(0, 65, 0, Block.STONE, 0);
        Map<Cell, String> before = snapshot(f);

        // Existing grow() reports true even when the generator refuses and restores the source.
        assertTrue(f.sapling().grow());

        assertEquals(Block.SAPLING, f.block(0, 64, 0).getId());
        assertEquals(metadata, f.block(0, 64, 0).getDamage());
        assertEquals(Block.STONE, f.block(0, 65, 0).getId());
        assertEquals(before, snapshot(f), "failed growth must not build a tree at another anchor");
    }

    static Stream<Arguments> squareTriggers() {
        return IntStream.of(BlockSapling.JUNGLE).boxed()
                .flatMap(metadata -> IntStream.range(0, 4)
                        .mapToObj(corner -> Arguments.of(metadata, corner / 2, corner % 2)));
    }

    @ParameterizedTest
    @MethodSource("squareTriggers")
    void matchedSquareRetainsItsAnchorFromEveryTrigger(int metadata, int sourceX, int sourceZ) {
        JungleWorld f = new JungleWorld();
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            f.put(x, 64, z, Block.SAPLING, metadata);
        }
        assertTrue(((BlockSapling) f.block(sourceX, 64, sourceZ)).grow());
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            assertJungleTrunk(f, x, z);
        }
    }

    private static void assertJungleTrunk(JungleWorld f, int x, int z) {
        assertEquals(Block.WOOD, f.block(x, 64, z).getId());
        assertEquals(BlockSapling.JUNGLE, f.block(x, 64, z).getDamage() & 3);
    }

    /** Real generators; setBlock storage also models Level.setBlock's block mutation. */
    private enum Refusal { OBSTRUCTION, SOIL, HEIGHT }

    static Stream<Arguments> refusedSquares() {
        return IntStream.of(BlockSapling.JUNGLE, BlockSapling.DARK_OAK).boxed()
                .flatMap(species -> IntStream.range(0, 4).boxed()
                        .flatMap(corner -> Stream.of(Refusal.values())
                                .map(reason -> Arguments.of(species, corner / 2, corner % 2, reason))));
    }

    @ParameterizedTest
    @MethodSource("refusedSquares")
    void refusedSquareRestoresEveryOriginalStateAndPosition(int species, int sourceX, int sourceZ, Refusal reason) {
        SquareWorld f = new SquareWorld(reason == Refusal.HEIGHT ? 253 : 64);
        f.square(species);
        if (reason == Refusal.OBSTRUCTION) f.put(0, f.y + 1, 0, Block.STONE, 0);
        if (reason == Refusal.SOIL) f.put(0, f.y - 1, 0, Block.STONE, 0);
        Map<Cell, State> before = Map.copyOf(f.cells);
        BlockSapling source = (BlockSapling) f.block(sourceX, f.y, sourceZ);
        Cell sourcePosition = cell(source);

        assertTrue(source.grow(), "retain the existing return value on refused generation");

        assertAll(
                () -> assertEquals(before, f.cells, "no lost, copied, displaced or re-aged saplings"),
                () -> assertEquals(sourcePosition, cell(source), "restoration must not move the triggering block object"),
                () -> assertEquals(8, f.writes.size(), "four clears followed by four restores"));
        for (int i = 0; i < 4; i++) {
            Write cleared = f.writes.get(i);
            Write restored = f.writes.get(i + 4);
            assertEquals(Block.AIR, cleared.state.id);
            assertTrue(cleared.direct);
            assertTrue(cleared.update);
            assertEquals(cleared.cell, restored.cell);
            assertEquals(before.get(restored.cell), restored.state);
            assertTrue(restored.direct);
            assertFalse(restored.update);
        }
    }

    static Stream<Arguments> successfulSquares() {
        return IntStream.of(BlockSapling.JUNGLE, BlockSapling.DARK_OAK).boxed()
                .flatMap(species -> IntStream.range(0, 4)
                        .mapToObj(corner -> Arguments.of(species, corner / 2, corner % 2)));
    }

    @ParameterizedTest
    @MethodSource("successfulSquares")
    void successfulSquareStillGrowsFromEveryTrigger(int species, int sourceX, int sourceZ) {
        SquareWorld f = new SquareWorld(64);
        f.square(species);
        BlockSapling source = (BlockSapling) f.block(sourceX, f.y, sourceZ);
        Cell sourcePosition = cell(source);

        assertTrue(source.grow());

        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            Block trunk = f.block(x, f.y, z);
            assertEquals(species == BlockSapling.JUNGLE ? Block.WOOD : Block.WOOD2, trunk.getId());
            assertEquals(species == BlockSapling.JUNGLE ? BlockWood.JUNGLE : BlockWood2.DARK_OAK,
                    trunk.getDamage() & 3);
        }
        assertEquals(sourcePosition, cell(source));
        assertEquals(4, f.writes.size(), "successful generators do not enter sapling restoration");
        assertTrue(f.cells.values().stream().noneMatch(state -> state.id == Block.SAPLING));
    }

    /** Native sapling growth and public world populators; only world storage is controlled. */
    static Stream<Arguments> foreignSaplings() {
        return IntStream.of(BlockSapling.OAK, BlockSapling.BIRCH, BlockSapling.JUNGLE,
                        BlockSapling.ACACIA, BlockSapling.DARK_OAK).boxed()
                .flatMap(species -> IntStream.of(0, BlockSapling.AGED_BIT).boxed()
                        .flatMap(age -> IntStream.range(0, 4)
                                .mapToObj(corner -> Arguments.of(species | age, corner / 2, corner % 2))));
    }

    @ParameterizedTest
    @MethodSource("foreignSaplings")
    void spruceSquarePreservesOtherSpeciesFromEveryTriggerCorner(int metadata, int sourceX, int sourceZ) {
        SpruceWorld f = spruceSquare();
        f.put(-1, 64, 0, Block.SAPLING, metadata);

        assertTrue(((BlockSapling) f.block(sourceX, 64, sourceZ)).grow());

        assertSquareTrunks(f);
        assertEquals(Block.SAPLING, f.block(-1, 64, 0).getId(), "foreign sapling must not become a spruce trunk");
        assertEquals(metadata, f.block(-1, 64, 0).getDamage());
        assertEquals(Block.GRASS, f.block(-1, 63, 0).getId(), "foreign sapling soil must remain unchanged");
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockSapling.SPRUCE, BlockSapling.SPRUCE | BlockSapling.AGED_BIT})
    void sameSpeciesNeighbourRetainsExistingGrowthBehaviour(int metadata) {
        SpruceWorld f = spruceSquare();
        f.put(-1, 64, 0, Block.SAPLING, metadata);
        assertTrue(f.sapling().grow());
        assertSquareTrunks(f);
        assertEquals(Block.WOOD, f.block(-1, 64, 0).getId());
        assertEquals(BlockSapling.SPRUCE, f.block(-1, 64, 0).getDamage() & 3);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 42L, 1337L, 9191L})
    void bigWorldPopulatorKeepsItsExistingShapeAndRandomSequence(long seed) throws NoSuchAlgorithmException {
        SpruceWorld f = new SpruceWorld();
        NukkitRandom random = new NukkitRandom(seed);
        SpruceBigTreePopulator populator = new SpruceBigTreePopulator();
        populator.setBaseAmount(1);
        populator.setRandomAmount(0);
        populator.populate(f.level, 0, 0, random, null);
        assertEquals(4, baseTrunks(f));
        assertFalse(f.cells.values().stream().anyMatch(b -> b.getId() == Block.SAPLING));
        // Captured from the selected native generator before the species guard.
        Receipt expected = switch ((int) seed) {
            case 1 -> new Receipt("72f981d3125302917eedf0aa52f8fc8b9ef549db440fca13ad9a28108e584241", 1445867414L);
            case 42 -> new Receipt("0af2fd1118d71dbc679e36677759a5e7b72d7abf3905b8864aa849f91cc7b36d", 4205656079L);
            case 1337 -> new Receipt("413015c1f5450cb9e99aed2e7a05ffd8d11dedade1e401ae07e773890d02aed6", 51568273L);
            case 9191 -> new Receipt("af0d651bfc17188bcabd1e67c8d03805793d415e4a2ac0e111578b8bb60c2bd2", -2717040273L);
            default -> throw new IllegalArgumentException("Unexpected test seed: " + seed);
        };
        assertEquals(expected.blocks(), snapshot(f));
        assertEquals(expected.randomState(), random.getSeed());
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 42L, 1337L, 9191L})
    void megaWorldPopulatorKeepsItsExistingShapeAndRandomSequence(long seed) throws NoSuchAlgorithmException {
        SpruceWorld f = new SpruceWorld();
        NukkitRandom random = new NukkitRandom(seed);
        SpruceMegaTreePopulator populator = new SpruceMegaTreePopulator();
        populator.setBaseAmount(1);
        populator.setRandomAmount(0);
        populator.populate(f.level, 0, 0, random, null);
        assertEquals(1, baseTrunks(f));
        // Captured from the selected native generator before the species guard.
        Receipt expected = switch ((int) seed) {
            case 1 -> new Receipt("00473c75213b29cb1bb26b50c09580efd3d0c6aeaa8fd5824f31f9b5049a320f", -1443522845L);
            case 42 -> new Receipt("6bf5f5fc18d3c9d77d9a3abc47cfd2075cbd25a69582917f6c72f90e6765eb19", 4202926512L);
            case 1337 -> new Receipt("c5a60b1ff8c74666505d39d5b075b2d3170f23cf0e3cdcd97ec5f240c09c0167", -53263779L);
            case 9191 -> new Receipt("3431633b179703d6180221b4de5de29b26d0ff235d14fad57eadfc1f8841dd82", 2710852198L);
            default -> throw new IllegalArgumentException("Unexpected test seed: " + seed);
        };
        assertEquals(expected.blocks(), snapshot(f));
        assertEquals(expected.randomState(), random.getSeed());
    }

    private static SpruceWorld spruceSquare() {
        SpruceWorld f = new SpruceWorld();
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            f.put(x, 64, z, Block.SAPLING, BlockSapling.SPRUCE);
        }
        return f;
    }

    private static void assertSquareTrunks(SpruceWorld f) {
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            assertEquals(Block.WOOD, f.block(x, 64, z).getId());
            assertEquals(BlockSapling.SPRUCE, f.block(x, 64, z).getDamage() & 3);
        }
    }

    private static long baseTrunks(SpruceWorld f) {
        return f.cells.entrySet().stream().filter(e -> e.getKey().y() == 64
                && e.getValue().getId() == Block.WOOD && (e.getValue().getDamage() & 3) == BlockSapling.SPRUCE).count();
    }

    private static String snapshot(SpruceWorld f) throws NoSuchAlgorithmException {
        StringBuilder text = new StringBuilder();
        f.cells.entrySet().stream().sorted(Map.Entry.comparingByKey(
                        Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z)))
                .forEach(e -> text.append(e.getKey().x()).append(',').append(e.getKey().y()).append(',')
                        .append(e.getKey().z()).append(':').append(e.getValue().getId()).append(':')
                        .append(e.getValue().getDamage()).append('\n'));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<Cell, String> snapshot(AcaciaWorld f) {
        Map<Cell, String> states = new HashMap<>();
        f.cells.forEach((cell, block) -> states.put(cell, block.getId() + ":" + block.getDamage()));
        return states;
    }

    private static Map<Cell, String> snapshot(JungleWorld f) {
        Map<Cell, String> states = new HashMap<>();
        f.cells.forEach((cell, block) -> states.put(cell, block.getId() + ":" + block.getDamage()));
        return states;
    }

    private record Cell(int x, int y, int z) {}

    private record State(int id, int data) {}

    private record Write(Cell cell, State state, boolean direct, boolean update) {}

    private record Receipt(String blocks, long randomState) {}

    private static Cell cell(Vector3 pos) { return new Cell(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()); }

    private static final class AcaciaWorld {
        static final int AGED_ACACIA = BlockSapling.ACACIA | BlockSapling.AGED_BIT;
        final Level level = mock(Level.class);
        final Map<Cell, Block> cells = new HashMap<>();
        final int sourceY;
        int writes;

        AcaciaWorld() { this(64); }

        AcaciaWorld(int sourceY) {
            this.sourceY = sourceY;
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); return block(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), c.getArgument(4)); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), 0); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt());
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block block = c.getArgument(1);
                write(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), block.getId(), block.getDamage()); return true;
            });
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block block = c.getArgument(1);
                write(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), block.getId(), block.getDamage()); return true;
            });
        }

        BlockSapling sapling() { return (BlockSapling) block(0, sourceY, 0); }

        Block block(int x, int y, int z) {
            Block source = cells.get(new Cell(x, y, z));
            Block block = source == null ? Block.get(y == sourceY - 1 ? Block.GRASS : Block.AIR) : source.clone();
            block.setLevel(level); block.setComponents(x, y, z); return block;
        }

        void put(int x, int y, int z, int id, int data) { cells.put(new Cell(x, y, z), Block.get(id, data)); }
        void write(int x, int y, int z, int id, int data) { writes++; put(x, y, z, id, data); }
    }

    private static final class JungleWorld {
        final Level level = mock(Level.class);
        final Map<Cell, Block> cells = new HashMap<>();
        int writes;

        JungleWorld() {
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); return block(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), c.getArgument(4)); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), 0); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt());
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block block = c.getArgument(1);
                write(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), block.getId(), block.getDamage()); return true;
            });
        }

        BlockSapling sapling() { return (BlockSapling) block(0, 64, 0); }

        Block block(int x, int y, int z) {
            Block source = cells.get(new Cell(x, y, z));
            Block block = source == null ? Block.get(y == 63 ? Block.GRASS : Block.AIR) : source.clone();
            block.setLevel(level); block.setComponents(x, y, z); return block;
        }

        void put(int x, int y, int z, int id, int data) { cells.put(new Cell(x, y, z), Block.get(id, data)); }
        void write(int x, int y, int z, int id, int data) { writes++; put(x, y, z, id, data); }
    }

    private static final class SquareWorld {
        final Level level = mock(Level.class);
        final Map<Cell, State> cells = new HashMap<>();
        final List<Write> writes = new ArrayList<>();
        final int y;

        SquareWorld(int y) {
            this.y = y;
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); return block(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            doAnswer(c -> { put(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), c.getArgument(4)); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(c -> { put(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), 0); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt());
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block value = c.getArgument(1);
                Cell target = cell(pos); State state = new State(value.getId(), value.getDamage());
                Block previous = block(target.x, target.y, target.z);
                if (previous.getId() == state.id && previous.getDamage() == state.data) return false;
                cells.put(target, state);
                writes.add(new Write(target, state, c.getArgument(2), c.getArgument(3)));
                // Level.setBlock assigns the supplied Block to its destination before notifying.
                value.setComponents(target.x, target.y, target.z);
                value.setLevel(level);
                value.layer = 0;
                return true;
            });
        }

        void square(int species) {
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                put(x, y, z, Block.SAPLING, species);
            }
        }

        Block block(int x, int y, int z) {
            State source = cells.get(new Cell(x, y, z));
            Block block = source == null ? Block.get(y == this.y - 1 ? Block.GRASS : Block.AIR)
                    : Block.get(source.id, source.data);
            block.setLevel(level); block.setComponents(x, y, z); return block;
        }

        void put(int x, int y, int z, int id, int data) { cells.put(new Cell(x, y, z), new State(id, data)); }
    }

    private static final class SpruceWorld {
        final Level level = mock(Level.class);
        final Map<Cell, Block> cells = new HashMap<>();
        int writes;

        SpruceWorld() {
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); return block(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), c.getArgument(4)); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), 0); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt());
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block block = c.getArgument(1);
                write(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), block.getId(), block.getDamage()); return true;
            });
        }

        BlockSapling sapling() { return (BlockSapling) block(0, 64, 0); }

        Block block(int x, int y, int z) {
            Block source = cells.get(new Cell(x, y, z));
            Block block = source == null ? Block.get(y == 63 ? Block.GRASS : Block.AIR) : source.clone();
            block.setLevel(level); block.setComponents(x, y, z); return block;
        }

        void put(int x, int y, int z, int id, int data) { cells.put(new Cell(x, y, z), Block.get(id, data)); }
        void write(int x, int y, int z, int id, int data) { writes++; put(x, y, z, id, data); }
    }
}
