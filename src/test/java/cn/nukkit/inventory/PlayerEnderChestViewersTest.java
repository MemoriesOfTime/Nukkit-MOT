package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.BlockEnderChest;
import cn.nukkit.blockentity.BlockEntityEnderChest;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.BlockEventPacket;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * Two players looking into one ender chest share its lid. Every lookup creates a new
 * {@link BlockEnderChest} object, so the viewers are kept on the block entity: the lid opens for
 * the first viewer and closes only after the last one.
 */
class PlayerEnderChestViewersTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void theFirstPlayerToCloseDoesNotShutTheLidOnTheSecond() {
        Level level = level(new HashSet<>());
        BlockEnderChest seenByA = chest(level);
        BlockEnderChest seenByB = chest(level);
        Player a = viewer(level, seenByA);
        Player b = viewer(level, seenByB);
        seenByA.getViewers().add(a);
        new PlayerEnderChestInventory(a).onOpen(a);
        seenByB.getViewers().add(b);
        new PlayerEnderChestInventory(b).onOpen(b);
        assertEquals(List.of(1), lidEvents(level), "the lid opens once, for the first viewer");

        new PlayerEnderChestInventory(a).onClose(a);
        assertEquals(List.of(1), lidEvents(level), "the second viewer still has the chest open");

        new PlayerEnderChestInventory(b).onClose(b);
        assertEquals(List.of(1, 0), lidEvents(level), "the lid closes after the last viewer");
        assertTrue(seenByA.getViewers().isEmpty());
    }

    @Test
    void aViewerIsRemovedEvenWhenAnotherPlayerStillLooks() {
        Set<Player> shared = new HashSet<>();
        Level level = level(shared);
        BlockEnderChest chest = chest(level);
        Player a = viewer(level, chest);
        Player b = viewer(level, chest);
        shared.add(a);
        shared.add(b);

        new PlayerEnderChestInventory(a).onClose(a);

        assertEquals(Set.of(b), shared);
        Mockito.verify(a).setViewingEnderChest(null);
    }

    private static Level level(Set<Player> viewers) {
        Level level = Mockito.mock(Level.class, Mockito.RETURNS_DEEP_STUBS);
        BlockEntityEnderChest blockEntity = Mockito.mock(BlockEntityEnderChest.class);
        Mockito.when(blockEntity.getViewers()).thenReturn(viewers);
        Mockito.when(level.getBlockEntity(any(Vector3.class))).thenReturn(blockEntity);
        return level;
    }

    private static BlockEnderChest chest(Level level) {
        BlockEnderChest chest = new BlockEnderChest();
        chest.x = 585;
        chest.y = 45;
        chest.z = 731;
        chest.level = level;
        return chest;
    }

    private static Player viewer(Level level, BlockEnderChest chest) {
        Player viewer = Mockito.mock(Player.class);
        Mockito.when(viewer.getLevel()).thenReturn(level);
        Mockito.when(viewer.getViewingEnderChest()).thenReturn(chest);
        Mockito.when(viewer.getWindowId(any())).thenReturn(5);
        Mockito.when(viewer.getClosingWindowId()).thenReturn(Integer.MAX_VALUE);
        viewer.spawned = true;
        return viewer;
    }

    private static List<Integer> lidEvents(Level level) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(level, Mockito.atLeast(0)).addChunkPacket(anyInt(), anyInt(), captor.capture());
        return captor.getAllValues().stream()
                .filter(BlockEventPacket.class::isInstance)
                .map(packet -> ((BlockEventPacket) packet).eventData)
                .toList();
    }
}
