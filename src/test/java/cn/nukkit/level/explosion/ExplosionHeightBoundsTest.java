package cn.nukkit.level.explosion;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.StrongExplosion;
import cn.nukkit.level.WeakExplosion;
import cn.nukkit.level.util.ExplosionSource;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class ExplosionHeightBoundsTest {
    enum Route { ORDINARY, WEAK, STRONG }

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    private static final class WeakBlockBlast extends WeakExplosion {
        WeakBlockBlast(Position center, double size, Block source) {
            super(center, size, new ExplosionSource.BlockSource(source));
        }
    }

    private static final class StrongBlockBlast extends StrongExplosion {
        StrongBlockBlast(Position center, double size, Block source) {
            super(center, size, new ExplosionSource.BlockSource(source));
        }
    }

    private static Explosion explosion(Route route, Position center, double size) {
        Block source = Block.get(Block.AIR);
        return switch (route) {
            case ORDINARY -> new Explosion(center, size, source);
            case WEAK -> new WeakBlockBlast(center, size, source);
            case STRONG -> new StrongBlockBlast(center, size, source);
        };
    }

    private static final class World {
        final Level level = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final AtomicInteger reads = new AtomicInteger();

        World(int minY, int maxY, int targetY, boolean nonfiniteResistance) {
            server.explosionBreakBlocks = true;
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getServer()).thenReturn(server);
            when(level.getMinBlockY()).thenReturn(minY);
            when(level.getMaxBlockY()).thenReturn(maxY);
            when(level.isYInRange(anyInt())).thenAnswer(call -> {
                int y = call.getArgument(0);
                return y >= minY && y <= maxY;
            });
            when(level.isYInRange(anyDouble())).thenAnswer(call -> {
                double y = call.getArgument(0);
                return y >= minY && y <= maxY;
            });
            when(level.getNearbyEntities(any(AxisAlignedBB.class), nullable(Entity.class)))
                    .thenReturn(new Entity[0]);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> {
                Vector3 at = call.getArgument(0);
                assertTrue(at.y >= minY && at.y <= maxY, "provider read outside height");
                reads.incrementAndGet();
                Block block = spy(Block.get(at.getFloorY() == targetY ? Block.DIRT : Block.AIR,
                        0, level, at.getFloorX(), at.getFloorY(), at.getFloorZ()));
                doReturn(Block.get(Block.AIR)).when(block).getLevelBlockAtLayer(1);
                if (nonfiniteResistance && !block.isAir()) {
                    doReturn(Double.NaN).when(block).getResistance();
                }
                return block;
            });
        }
    }

    private static Collection<?> selected(Explosion explosion) throws Exception {
        Class<?> owner = explosion instanceof StrongExplosion ? StrongExplosion.class
                : explosion instanceof WeakExplosion ? WeakExplosion.class : Explosion.class;
        Field field = owner.getDeclaredField("affectedBlocks");
        field.setAccessible(true);
        return (Collection<?>) field.get(explosion);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void inwardRaysReachTopLayerFromAboveProvider(Route route) throws Exception {
        World world = new World(-64, 63, 63, false);
        Explosion blast = explosion(route, new Position(0, 64.25, 0, world.level), 4);
        assertTrue(blast.explodeA());
        assertTrue(world.reads.get() > 0, "inward rays must reach valid terrain");
        assertTrue(selected(blast).stream().anyMatch(value -> ((Block) value).getFloorY() == 63));
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void inwardRaysReachBottomLayerFromBelowProvider(Route route) throws Exception {
        World world = new World(-64, 63, -64, false);
        Explosion blast = explosion(route, new Position(0, -64.5, 0, world.level), 4);
        assertTrue(blast.explodeA());
        assertTrue(world.reads.get() > 0);
        assertTrue(selected(blast).stream().anyMatch(value -> ((Block) value).getFloorY() == -64));
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void nonfiniteAndUnrepresentableInputsRefuseBothPhases(Route route) {
        World world = new World(-64, 63, 0, false);
        world.server.explosionBreakBlocks = false;
        for (double size : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MAX_VALUE, 1_000_000_000}) {
            Explosion blast = explosion(route, new Position(Integer.MAX_VALUE - 1, 64, 0, world.level), size);
            assertFalse(blast.explodeA(), "unsafe size must refuse even with block destruction disabled");
            assertFalse(blast.explodeB(), "B cannot bypass numerical refusal");
        }
        verifyNoInteractions(world.plugins);
        assertEquals(0, world.reads.get());
    }

    @ParameterizedTest
    @EnumSource(value = Route.class, names = {"ORDINARY", "STRONG"})
    void nonfiniteResistanceRefusesLaterEffects(Route route) {
        World world = new World(-64, 63, 0, true);
        Explosion blast = explosion(route, new Position(0, 0, 0, world.level), 1);
        assertFalse(blast.explodeA());
        assertFalse(blast.explodeB(), "numerical refusal must remain sticky");
        verifyNoInteractions(world.plugins);
    }

    @Test
    void callbackCannotReplaceSourceWithNonfiniteCoordinates() {
        World world = new World(-64, 63, 0, false);
        Position center = new Position(0, 64, 0, world.level);
        doAnswer(call -> {
            center.x = Double.NaN;
            return null;
        }).when(world.plugins).callEvent(any(Event.class));
        assertFalse(explosion(Route.ORDINARY, center, 1).explodeB());
        verify(world.level, never()).getNearbyEntities(any(AxisAlignedBB.class), nullable(Entity.class));
    }
}
