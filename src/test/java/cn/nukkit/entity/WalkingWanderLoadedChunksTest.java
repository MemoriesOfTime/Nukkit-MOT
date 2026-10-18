package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkingWanderLoadedChunksTest {
    @BeforeAll
    static void init() { MockServer.init(); }

    @Test
    void unloadedCandidatesNeverLoadChunksAndCanRetryAfterALaterLoad() throws Exception {
        Fixture f = new Fixture(15.5, 15.5);
        try (MockedStatic<Utils> random = random()) { f.cow.scan(); }
        assertNull(f.cow.wanderTarget());
        assertEquals(0, f.cow.stayTime, "no rest timer that can become stuck in water");
        verify(f.level, times(10)).getChunkIfLoaded(1, 1);
        verify(f.level, never()).getHighestBlockAt(anyInt(), anyInt());
        verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(f.level, never()).getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt());
        clearInvocations(f.level);
        BaseFullChunk neighbour = mock(BaseFullChunk.class);
        when(f.level.getChunkIfLoaded(1, 1)).thenReturn(neighbour);
        when(neighbour.getHighestBlockAt(3, 3)).thenReturn(70);
        when(f.level.getBlockIdAt(neighbour, 19, 70, 19)).thenReturn(Block.STONE);
        try (MockedStatic<Utils> random = random()) { f.cow.scan(); }
        assertEquals(new Vector3(19.5, 64, 19.5), f.cow.wanderTarget());
        verify(f.level).getChunkIfLoaded(1, 1);
    }

    @Test
    void loadedDryNeighbourPreservesPositionAndReadsItsOwnColumn() throws Exception {
        Fixture f = new Fixture(15.5, 15.5);
        BaseFullChunk neighbour = mock(BaseFullChunk.class);
        when(f.level.getChunkIfLoaded(1, 1)).thenReturn(neighbour);
        when(neighbour.getHighestBlockAt(3, 3)).thenReturn(70);
        when(f.level.getBlockIdAt(neighbour, 19, 70, 19)).thenReturn(Block.STONE);
        try (MockedStatic<Utils> random = random()) { f.cow.scan(); }
        assertEquals(new Vector3(19.5, 64, 19.5), f.cow.wanderTarget());
        verify(f.level, times(1)).getChunkIfLoaded(1, 1);
        verify(f.level).getBlockIdAt(neighbour, 19, 70, 19);
        verify(f.level, never()).getHighestBlockAt(anyInt(), anyInt());
    }

    @Test
    void negativeCoordinatesUseFloorAndLocalColumn() throws Exception {
        Fixture f = new Fixture(-5.5, -5.5);
        BaseFullChunk neighbour = mock(BaseFullChunk.class);
        when(f.level.getChunkIfLoaded(-1, -1)).thenReturn(neighbour);
        when(neighbour.getHighestBlockAt(14, 14)).thenReturn(63);
        when(f.level.getBlockIdAt(neighbour, -2, 63, -2)).thenReturn(Block.STONE);
        try (MockedStatic<Utils> random = random()) { f.cow.scan(); }
        assertEquals(new Vector3(-1.5, 64, -1.5), f.cow.wanderTarget());
        verify(neighbour).getHighestBlockAt(14, 14);
    }

    @Test
    void loadedWaterKeepsTheExistingTenAttemptFallback() throws Exception {
        Fixture f = new Fixture(15.5, 15.5);
        BaseFullChunk neighbour = mock(BaseFullChunk.class);
        when(f.level.getChunkIfLoaded(1, 1)).thenReturn(neighbour);
        when(neighbour.getHighestBlockAt(3, 3)).thenReturn(70);
        when(f.level.getBlockIdAt(neighbour, 19, 70, 19)).thenReturn(Block.WATER);
        try (MockedStatic<Utils> random = random()) { f.cow.scan(); }
        assertEquals(new Vector3(19.5, 64, 19.5), f.cow.wanderTarget());
        verify(f.level, times(10)).getChunkIfLoaded(1, 1);
    }

    private static MockedStatic<Utils> random() {
        MockedStatic<Utils> random = mockStatic(Utils.class);
        random.when(() -> Utils.rand(1, 100)).thenReturn(50);
        random.when(() -> Utils.rand(80, 200)).thenReturn(80);
        random.when(() -> Utils.rand(4, 10)).thenReturn(4);
        random.when(Utils::rand).thenReturn(true);
        random.when(() -> Utils.rand(-20.0, 20.0)).thenReturn(0.0);
        return random;
    }

    private static final class Cow extends EntityCow {
        Cow(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }
        void scan() { checkTarget(); }
        Vector3 wanderTarget() { return target; }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Cow cow;
        Fixture(double x, double z) throws Exception {
            when(level.getServer()).thenReturn(MockServer.get());
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
            when(level.getNearbyEntities(any(), any(), anyBoolean(), anyBoolean())).thenReturn(new Entity[0]);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            Field field = Level.class.getDeclaredField("isBeingConverted");
            field.setAccessible(true);
            field.setBoolean(level, true);
            FullChunk chunk = mock(FullChunk.class);
            when(chunk.getX()).thenReturn(((int) Math.floor(x)) >> 4);
            when(chunk.getZ()).thenReturn(((int) Math.floor(z)) >> 4);
            LevelProvider provider = mock(LevelProvider.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            CompoundTag nbt = new CompoundTag()
                    .putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", x)).add(new DoubleTag("", 64)).add(new DoubleTag("", z)))
                    .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", 0)).add(new DoubleTag("", 0)).add(new DoubleTag("", 0)))
                    .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", 0)).add(new FloatTag("", 0)));
            cow = new Cow(chunk, nbt);
            clearInvocations(level);
        }
    }
}
