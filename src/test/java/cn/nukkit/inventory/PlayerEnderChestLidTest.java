package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.BlockEnderChest;
import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.BlockEventPacket;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The lid of an ender chest is animated for everyone around the chest, so the block event has to
 * be addressed to the chunk of the chest, not to the chunk the viewer happens to stand in when the
 * window closes. A viewer teleported with the window open (respawn, plugin teleport) closes the
 * window only after the move; the old code sent the close event to the destination and the lid
 * stayed open for every other player near the chest.
 */
class PlayerEnderChestLidTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void closeEventGoesToTheChestChunkAfterTheViewerWasTeleportedAway() {
        Level chestLevel = Mockito.mock(Level.class, Mockito.RETURNS_DEEP_STUBS);
        Level elsewhere = Mockito.mock(Level.class, Mockito.RETURNS_DEEP_STUBS);
        Player viewer = viewer(elsewhere, 5000, 5000);
        BlockEnderChest chest = chest(chestLevel, 585, 45, 731);
        chest.getViewers().add(viewer);
        Mockito.when(viewer.getViewingEnderChest()).thenReturn(chest);
        Mockito.when(viewer.getClosingWindowId()).thenReturn(Integer.MAX_VALUE);

        new PlayerEnderChestInventory(viewer).onClose(viewer);

        BlockEventPacket packet = lidPacket(chestLevel, 585 >> 4, 731 >> 4, 0);
        assertEquals(585, packet.x);
        assertEquals(45, packet.y);
        assertEquals(731, packet.z);
        verify(elsewhere, never()).addChunkPacket(anyInt(), anyInt(), any(DataPacket.class));
        verify(viewer).setViewingEnderChest(null);
    }

    @Test
    void openEventGoesToTheChestChunkEvenWhenTheViewerStandsInTheNeighbourChunk() {
        Level level = Mockito.mock(Level.class, Mockito.RETURNS_DEEP_STUBS);
        Player viewer = viewer(level, 592.5, 731.5);
        BlockEnderChest chest = chest(level, 591, 45, 731);
        chest.getViewers().add(viewer);
        Mockito.when(viewer.getViewingEnderChest()).thenReturn(chest);

        new PlayerEnderChestInventory(viewer).onOpen(viewer);

        BlockEventPacket packet = lidPacket(level, 591 >> 4, 731 >> 4, 1);
        assertEquals(591, packet.x);
        verify(level, never()).addChunkPacket(Mockito.eq(592 >> 4), anyInt(), any(DataPacket.class));
    }

    private static Player viewer(Level level, double x, double z) {
        Player viewer = Mockito.mock(Player.class);
        Mockito.when(viewer.getLevel()).thenReturn(level);
        Mockito.when(viewer.getX()).thenReturn(x);
        Mockito.when(viewer.getZ()).thenReturn(z);
        Mockito.when(viewer.getWindowId(any())).thenReturn(5);
        viewer.spawned = true;
        return viewer;
    }

    private static BlockEnderChest chest(Level level, int x, int y, int z) {
        BlockEnderChest chest = new BlockEnderChest();
        chest.x = x;
        chest.y = y;
        chest.z = z;
        chest.level = level;
        return chest;
    }

    private static BlockEventPacket lidPacket(Level level, int chunkX, int chunkZ, int eventData) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        verify(level, Mockito.atLeastOnce()).addChunkPacket(Mockito.eq(chunkX), Mockito.eq(chunkZ), captor.capture());
        return captor.getAllValues().stream()
                .filter(BlockEventPacket.class::isInstance)
                .map(BlockEventPacket.class::cast)
                .filter(packet -> packet.eventData == eventData)
                .reduce((first, second) -> {
                    throw new AssertionError("two lid events for one window");
                })
                .orElseThrow(() -> new AssertionError("no lid event in the chest chunk"));
    }
}
