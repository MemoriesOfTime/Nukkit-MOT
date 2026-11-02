package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.level.generator.populator.impl.tree.SpruceBigTreePopulator;
import cn.nukkit.level.generator.populator.impl.tree.SpruceMegaTreePopulator;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native sapling growth and public world populators; only world storage is controlled. */
@Timeout(20)
class SpruceNeighbourSpeciesTest {
    @BeforeAll
    static void initialize() { Block.init(); }

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
        World f = spruceSquare();
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
        World f = spruceSquare();
        f.put(-1, 64, 0, Block.SAPLING, metadata);
        assertTrue(f.sapling().grow());
        assertSquareTrunks(f);
        assertEquals(Block.WOOD, f.block(-1, 64, 0).getId());
        assertEquals(BlockSapling.SPRUCE, f.block(-1, 64, 0).getDamage() & 3);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 42L, 1337L, 9191L})
    void bigWorldPopulatorKeepsItsExistingShapeAndRandomSequence(long seed) throws NoSuchAlgorithmException {
        World f = new World();
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
        World f = new World();
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

    private static World spruceSquare() {
        World f = new World();
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            f.put(x, 64, z, Block.SAPLING, BlockSapling.SPRUCE);
        }
        return f;
    }

    private static void assertSquareTrunks(World f) {
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            assertEquals(Block.WOOD, f.block(x, 64, z).getId());
            assertEquals(BlockSapling.SPRUCE, f.block(x, 64, z).getDamage() & 3);
        }
    }

    private static long baseTrunks(World f) {
        return f.cells.entrySet().stream().filter(e -> e.getKey().y() == 64
                && e.getValue().getId() == Block.WOOD && (e.getValue().getDamage() & 3) == BlockSapling.SPRUCE).count();
    }

    private static String snapshot(World f) throws NoSuchAlgorithmException {
        StringBuilder text = new StringBuilder();
        f.cells.entrySet().stream().sorted(Map.Entry.comparingByKey(
                Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z)))
                .forEach(e -> text.append(e.getKey().x()).append(',').append(e.getKey().y()).append(',')
                        .append(e.getKey().z()).append(':').append(e.getValue().getId()).append(':')
                        .append(e.getValue().getDamage()).append('\n'));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private record Receipt(String blocks, long randomState) {}

    private record Cell(int x, int y, int z) {}

    private static final class World {
        final Level level = mock(Level.class);
        final Map<Cell, Block> cells = new HashMap<>();
        int writes;

        World() {
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
