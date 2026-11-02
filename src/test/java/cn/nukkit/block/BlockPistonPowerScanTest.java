package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntityPistonArm;
import cn.nukkit.event.block.BlockPistonEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.serverconfig.ServerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockPistonPowerScanTest {
    @BeforeAll
    static void initBlocks() {
        Block.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void movingPistonSkipsPowerReadsWithoutMutatingPendingMovement(int state) {
        Fixture fixture = new Fixture();
        fixture.arm.state = state;
        fixture.arm.powered = state == 1;
        for (int type : new int[] {Level.BLOCK_UPDATE_NORMAL, Level.BLOCK_UPDATE_REDSTONE, Level.BLOCK_UPDATE_SCHEDULED}) {
            assertEquals(type, fixture.piston.onUpdate(type));
        }
        assertEquals(0, fixture.reads.get(), "Moving arm ignores power; no neighbor blocks need fetching");
        assertEquals(state, fixture.arm.state);
        assertEquals(state == 1, fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());
        verify(fixture.plugins, never()).callEvent(any(BlockPistonEvent.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void stationaryPistonStillChecksAllFiveUnpoweredNeighbors(int state) {
        Fixture fixture = new Fixture();
        fixture.arm.state = state;
        assertEquals(Level.BLOCK_UPDATE_NORMAL, fixture.piston.onUpdate(Level.BLOCK_UPDATE_NORMAL));
        assertEquals(10, fixture.reads.get(), "Each air neighbor is fetched by getSide and isSidePowered");
        assertFalse(fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());
    }

    @Test
    void pulseDuringAnimationIsRecheckedWhenMovementCompletes() {
        Fixture fixture = new Fixture();
        fixture.arm.state = 3;
        fixture.wirePowered = true;
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertEquals(0, fixture.reads.get());
        assertFalse(fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());

        fixture.arm.state = 0;
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertTrue(fixture.arm.powered);
        verify(fixture.arm).move(eq(true), anyList());
        verify(fixture.plugins).callEvent(any(BlockPistonEvent.class));
    }

    @Test
    void stationaryExtendedPistonStillRetractsAfterPowerDisappears() {
        Fixture fixture = new Fixture();
        fixture.arm.state = 2;
        fixture.arm.powered = true;
        fixture.piston.extended = true;
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertFalse(fixture.arm.powered);
        verify(fixture.arm).move(eq(false), anyList());
    }

    @Test
    void cancelledMoveStillRetainsOldPowerSoFutureUpdatesCanRetry() {
        Fixture fixture = new Fixture();
        fixture.arm.state = 0;
        fixture.wirePowered = true;
        doAnswer(invocation -> {
            ((BlockPistonEvent) invocation.getArgument(0)).setCancelled();
            return null;
        }).when(fixture.plugins).callEvent(any(BlockPistonEvent.class));
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertFalse(fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());
    }

    private static final class TestPiston extends BlockPiston {
        boolean extended;
        @Override
        public boolean isExtended() {
            return extended;
        }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final BlockEntityPistonArm arm = mock(BlockEntityPistonArm.class);
        final TestPiston piston = new TestPiston();
        final AtomicInteger reads = new AtomicInteger();
        boolean wirePowered;

        Fixture() {
            when(level.getServer()).thenReturn(server);
            when(server.getServerConfig()).thenReturn(new ServerConfig());
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(invocation -> {
                Vector3 pos = invocation.getArgument(0);
                return pos.getFloorX() == 0 && pos.getFloorY() == 64 && pos.getFloorZ() == 0 ? arm : null;
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    blockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            when(level.getBlock(any(Vector3.class))).thenAnswer(invocation -> {
                Vector3 pos = invocation.getArgument(0);
                return blockAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.isSidePowered(any(Vector3.class), any(BlockFace.class))).thenCallRealMethod();
            when(level.getRedstonePower(any(Vector3.class), any(BlockFace.class))).thenCallRealMethod();
            piston.position(new Position(0, 64, 0, level));
            piston.level = level;
        }

        private Block blockAt(int x, int y, int z) {
            reads.incrementAndGet();
            // Default piston faces DOWN; UP is an eligible input while DOWN stays empty for extension.
            Block block = wirePowered && x == 0 && y == 65 && z == 0
                    ? new BlockRedstoneWire(15) : new BlockAir();
            block.position(new Position(x, y, z, level));
            block.level = level;
            return block;
        }
    }
}
