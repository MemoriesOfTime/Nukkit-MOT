package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockPistonCallbackP16Test {
    @BeforeAll static void initBlocks() { Block.init(); }

    static Stream<Arguments> changes() {
        return Stream.of(false, true).flatMap(extending ->
                Stream.of("remove", "foreign_tile", "new_arm", "block_only", "rotate_cell", "mutate_subject", "unload", "start_animation", "move_subject", "layer_subject", "level_subject")
                        .map(change -> Arguments.of(extending, change)));
    }

    @ParameterizedTest
    @MethodSource("changes")
    void callbackInvalidatingSourceStopsBeforeWorldWrites(boolean extending, String change) {
        Fixture f = new Fixture(extending);
        doAnswer(i -> {
            BlockPistonEvent event = i.getArgument(0);
            switch (change) {
                case "remove" -> { f.originalArm.close(); f.currentCell = new BlockAir(); }
                case "foreign_tile" -> f.currentTile = mock(BlockEntityChest.class);
                case "new_arm" -> f.currentTile = f.replacementArm;
                case "block_only" -> f.currentCell = new BlockStone();
                case "rotate_cell" -> f.currentCell.setDamage(BlockFace.NORTH.getIndex());
                case "mutate_subject" -> event.getBlock().setDamage(BlockFace.NORTH.getIndex());
                case "unload" -> f.loaded = false;
                case "move_subject" -> event.getBlock().x = 1;
                case "layer_subject" -> event.getBlock().layer = 1;
                case "level_subject" -> event.getBlock().level = f.foreignLevel;
                case "start_animation" -> {
                    doCallRealMethod().when(f.originalArm).move(anyBoolean(), anyList());
                    f.originalArm.move(extending, java.util.List.of());
                    clearInvocations(f.originalArm);
                }
                default -> fail(change);
            }
            return null;
        }).when(f.plugins).callEvent(any(BlockPistonEvent.class));
        Throwable failure = null;
        try { f.piston.onUpdate(Level.BLOCK_UPDATE_REDSTONE); } catch (Throwable caught) { failure = caught; }
        System.out.printf("piston extending=%s change=%s writes=%d failure=%s%n",
                extending, change, f.writes, failure == null ? "none" : failure.getClass().getSimpleName());
        assertNull(failure, "Callback removed/replaced the source; no stale arm access is valid");
        assertEquals(0, f.writes, "Source fence must run before even an orphan head is written");
        assertEquals(!extending, f.originalArm.powered);
        verify(f.originalArm, never()).move(anyBoolean(), anyList());
        verify(f.replacementArm, never()).move(anyBoolean(), anyList());
        verify(f.level, never()).useBreakOn(any(Vector3.class), any(), any(), anyBoolean());
        verifyNoInteractions(f.foreignLevel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unchangedSourceStillMovesItsOriginalArm(boolean extending) {
        Fixture f = new Fixture(extending);
        assertDoesNotThrow(() -> f.piston.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        assertEquals(extending ? 1 : 0, f.writes);
        assertEquals(extending, f.originalArm.powered);
        verify(f.originalArm).move(eq(extending), anyList());
        verify(f.replacementArm, never()).move(anyBoolean(), anyList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledEventStillStopsBeforeWrites(boolean extending) {
        Fixture f = new Fixture(extending);
        doAnswer(i -> { ((BlockPistonEvent) i.getArgument(0)).setCancelled(); return null; })
                .when(f.plugins).callEvent(any(BlockPistonEvent.class));
        assertDoesNotThrow(() -> f.piston.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
        assertEquals(0, f.writes);
        verify(f.originalArm, never()).move(anyBoolean(), anyList());
        assertEquals(!extending, f.originalArm.powered);
    }

    private static final class TestPiston extends BlockPiston {
        final boolean extended;
        TestPiston(boolean extended) { super(BlockFace.EAST.getIndex()); this.extended = extended; }
        @Override public boolean isExtended() { return extended; }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Level foreignLevel = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final BlockEntityPistonArm originalArm = mock(BlockEntityPistonArm.class);
        final BlockEntityPistonArm replacementArm = mock(BlockEntityPistonArm.class);
        final TestPiston piston;
        final boolean powered;
        Block currentCell;
        BlockEntity currentTile;
        int writes;
        boolean loaded = true;
        Fixture(boolean extending) {
            powered = extending;
            piston = new TestPiston(!extending);
            piston.position(new Position(0, 64, 0, level)); piston.level = level;
            currentCell = piston.clone(); currentTile = originalArm;
            originalArm.state = extending ? 0 : 2; originalArm.powered = !extending; originalArm.level = level;
            when(level.getServer()).thenReturn(server);
            when(server.getServerConfig()).thenReturn(new ServerConfig());
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(i -> atSource(i.getArgument(0)) ? currentTile : null);
            when(level.getBlockEntityIfLoaded(any(Vector3.class)))
                    .thenAnswer(i -> loaded && atSource(i.getArgument(0)) ? currentTile : null);
            doCallRealMethod().when(originalArm).close();
            doAnswer(i -> { if (currentTile == i.getArgument(0)) currentTile = null; return null; })
                    .when(level).removeBlockEntity(any(BlockEntity.class));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    blockAt(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
            when(level.getBlock(any(Vector3.class))).thenAnswer(i -> {
                Vector3 pos = i.getArgument(0); return blockAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.getBlock(any(Vector3.class), anyInt(), eq(false))).thenAnswer(i -> {
                Vector3 pos = i.getArgument(0); return blockAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            doAnswer(i -> { writes++; return true; })
                    .when(level).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
        }
        boolean atSource(Vector3 pos) { return pos.getFloorX() == 0 && pos.getFloorY() == 64 && pos.getFloorZ() == 0; }
        Block blockAt(int x, int y, int z) {
            Block block = x == 0 && y == 64 && z == 0 ? currentCell.clone()
                    : powered && x == 0 && y == 65 && z == 0 ? new BlockRedstoneWire(15) : new BlockAir();
            block.position(new Position(x, y, z, level)); block.level = level; return block;
        }
    }
}
