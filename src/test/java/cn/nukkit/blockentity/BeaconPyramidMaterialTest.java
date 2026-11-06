package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BeaconPyramidMaterialTest {
    private static final int[] MATERIALS = {
            Block.IRON_BLOCK, Block.GOLD_BLOCK, Block.EMERALD_BLOCK, Block.DIAMOND_BLOCK, Block.NETHERITE_BLOCK
    };

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.IRON_BLOCK, Block.GOLD_BLOCK, Block.EMERALD_BLOCK,
            Block.DIAMOND_BLOCK, Block.NETHERITE_BLOCK})
    void acceptsEachVanillaBaseMaterial(int material) {
        Fixture fixture = pyramid(1, new int[]{material});
        assertTrue(fixture.beacon.onUpdate());
        assertEquals(1, fixture.beacon.getPowerLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void netheriteSupportsEveryPyramidTier(int layers) {
        Fixture fixture = pyramid(layers, new int[]{Block.NETHERITE_BLOCK});
        assertTrue(fixture.beacon.onUpdate());
        assertEquals(layers, fixture.beacon.getPowerLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void differentAllowedMaterialsCanShareEveryLayer(int layers) {
        Fixture fixture = pyramid(layers, MATERIALS);
        assertTrue(fixture.beacon.onUpdate());
        assertEquals(layers, fixture.beacon.getPowerLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void invalidMaterialStopsAtTheLastCompleteLayer(int brokenLayer) {
        for (int invalid : new int[]{Block.AIR, Block.STONE, Block.OBSIDIAN}) {
            Fixture fixture = pyramid(4, new int[]{Block.NETHERITE_BLOCK});
            fixture.blocks.put(new Pos(brokenLayer, 64 - brokenLayer, brokenLayer), invalid);
            assertTrue(fixture.beacon.onUpdate());
            assertEquals(brokenLayer - 1, fixture.beacon.getPowerLevel(), "invalid material " + invalid);
        }
    }

    private record Pos(int x, int y, int z) {}
    private record Fixture(BlockEntityBeacon beacon, Map<Pos, Integer> blocks) {}

    private static Fixture pyramid(int layers, int[] materials) {
        Map<Pos, Integer> blocks = new HashMap<>();
        int index = 0;
        for (int layer = 1; layer <= layers; layer++) {
            for (int x = -layer; x <= layer; x++) {
                for (int z = -layer; z <= layer; z++) {
                    blocks.put(new Pos(x, 64 - layer, z), materials[index++ % materials.length]);
                }
            }
        }
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(MockServer.get());
        when(level.getMaxBlockY()).thenReturn(65);
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
        when(level.getPlayers()).thenReturn(Map.of());
        when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                blocks.getOrDefault(new Pos(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)), Block.AIR));
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        FullChunk chunk = mock(FullChunk.class);
        when(chunk.getProvider()).thenReturn(provider);
        BlockEntityBeacon beacon = new BlockEntityBeacon(chunk, new CompoundTag()
                .putString("id", BlockEntity.BEACON).putInt("x", 0).putInt("y", 64).putInt("z", 0));
        return new Fixture(beacon, blocks);
    }
}
