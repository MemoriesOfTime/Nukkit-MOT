package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.generic.BaseFullChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NetherPortalThreadOwnershipTest {
    @ParameterizedTest
    @CsvSource({"0,0", "-17,-33", "128,129"})
    void workerNeverReadsOrMountsColdChunks(int x, int z) throws Exception {
        Level level = world(false);
        AtomicReference<Position> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(BlockNetherPortal.findNearestPortal(new Position(x, 64, z, level)));
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertNull(result.get());
        verify(level, times(361)).getChunkIfLoaded(anyInt(), anyInt());
        assertNoDiskOrMutation(level);
    }

    @Test
    void workerFindsFarLoadedPortalWithoutWorldAccess() {
        Level level = world(false);
        BaseFullChunk chunk = frame();
        when(level.getChunkIfLoaded(-7, -2)).thenReturn(chunk);
        Position found = BlockNetherPortal.findNearestPortal(new Position(-1, 1, -17, level));
        assertNotNull(found);
        assertEquals((-7 << 4) + 3, found.getFloorX());
        assertEquals((-2 << 4) + 5, found.getFloorZ());
        assertEquals(1, found.getFloorY());
        assertNoDiskOrMutation(level);
    }

    @Test
    void serverOnlyLoadsNearbyNineChunks() {
        Level level = world(true);
        assertNull(BlockNetherPortal.findNearestPortal(new Position(-1, 1, -17, level)));
        verify(level, times(9)).loadChunk(anyInt(), anyInt(), eq(false));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void revalidationRejectsUnloadedAndBrokenFramesWithoutDiskRead() {
        Level level = world(true);
        Position portal = new Position(-13, 1, -27, level);
        assertFalse(portalLoaded(portal));
        BaseFullChunk chunk = frame();
        when(level.getChunkIfLoaded(-1, -2)).thenReturn(chunk);
        assertTrue(portalLoaded(portal));
        when(chunk.getBlockId(3, 1, 5)).thenReturn(BlockID.AIR);
        assertFalse(portalLoaded(portal));
        assertNoDiskOrMutation(level);
    }

    @Test
    void exitIsClearedOnlyDuringServerThreadConsumption() {
        Level level = world(true);
        Position portal = new Position(3, 1, 5, level);
        when(level.getBlockIdAt(3, 2, 5)).thenReturn(BlockID.STONE);
        Position safe = BlockNetherPortal.getSafePortal(portal);
        assertEquals(1, safe.getFloorY());
        verify(level, times(60)).setBlockAt(anyInt(), anyInt(), anyInt(), eq(BlockID.AIR));
    }

    @Test
    void workerCannotClearExit() {
        Level level = world(false);
        assertThrows(IllegalStateException.class,
                () -> BlockNetherPortal.getSafePortal(new Position(3, 1, 5, level)));
        assertNoDiskOrMutation(level);
    }

    @Test
    void ungeneratedChunkNeverSuppliesPortalOrAllowsFrame() {
        Level level = world(false);
        BaseFullChunk chunk = frame();
        when(chunk.isGenerated()).thenReturn(false);
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
        Position origin = new Position(3, 1, 5, level);
        assertNull(BlockNetherPortal.findNearestPortal(origin));
        assertFalse(portalLoaded(origin));
        assertFalse(portalDestinationReady(origin));
        assertNoDiskOrMutation(level);
    }

    @Test
    void generatedChunkReadinessRejectsUngeneratedReplacement() {
        Level level = world(true);
        BaseFullChunk chunk = frame();
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
        Position origin = new Position(3, 1, 5, level);
        assertTrue(portalDestinationReady(origin));
        when(chunk.isGenerated()).thenReturn(false);
        assertFalse(portalDestinationReady(origin));
        assertFalse(portalLoaded(origin));
    }

    private boolean portalLoaded(Position portal) {
        var method = assertDoesNotThrow(() -> BlockNetherPortal.class.getMethod("isPortalLoaded", Position.class),
                "The update exposes loaded-only revalidation without introducing a baseline compile dependency");
        return assertDoesNotThrow(() -> (Boolean) method.invoke(null, portal));
    }

    private boolean portalDestinationReady(Position portal) {
        var method = assertDoesNotThrow(() -> BlockNetherPortal.class.getMethod("isPortalDestinationReady", Position.class));
        return assertDoesNotThrow(() -> (Boolean) method.invoke(null, portal));
    }

    private Level world(boolean primary) {
        Server server = mock(Server.class);
        when(server.isPrimaryThread()).thenReturn(primary);
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.getMaxBlockY()).thenReturn(4);
        return level;
    }

    private BaseFullChunk frame() {
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isGenerated()).thenReturn(true);
        when(chunk.getBlockId(3, 1, 5)).thenReturn(BlockID.NETHER_PORTAL);
        return chunk;
    }

    private void assertNoDiskOrMutation(Level level) {
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(level, never()).loadChunk(anyInt(), anyInt(), anyBoolean());
        verify(level, never()).getBlockIdAt(anyInt(), anyInt(), anyInt());
        verify(level, never()).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt());
    }
}
