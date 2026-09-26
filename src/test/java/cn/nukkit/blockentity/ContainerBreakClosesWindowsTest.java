package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DispenserInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.PlayerEnderChestInventory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Breaking a container closes the window of every player looking into it, like vanilla: the ender
 * chest, the double chest (its window was lost by unpairing first) and the dispenser and dropper.
 */
class ContainerBreakClosesWindowsTest {

    @Test
    void breakingAnEnderChestClosesTheWindowsOfItsViewers() throws Exception {
        BlockEntityEnderChest chest = mock(BlockEntityEnderChest.class, CALLS_REAL_METHODS);
        Set<Player> viewers = new HashSet<>();
        doReturn(viewers).when(chest).getViewers();
        Player looking = mock(Player.class);
        PlayerEnderChestInventory enderChest = mock(PlayerEnderChestInventory.class);
        doReturn(enderChest).when(looking).getEnderChestInventory();
        doReturn(3).when(looking).getWindowId(enderChest);
        Player stale = mock(Player.class);
        PlayerEnderChestInventory staleInventory = mock(PlayerEnderChestInventory.class);
        doReturn(staleInventory).when(stale).getEnderChestInventory();
        doReturn(-1).when(stale).getWindowId(staleInventory);
        viewers.add(looking);
        viewers.add(stale);

        chest.close();

        verify(looking).removeWindow(enderChest);
        verify(stale, never()).removeWindow(staleInventory);
        assertTrue(viewers.isEmpty());
    }

    @Test
    void breakingHalfOfADoubleChestClosesTheDoubleWindow() throws Exception {
        BlockEntityChest chest = mock(BlockEntityChest.class, CALLS_REAL_METHODS);
        DoubleChestInventory doubleInventory = mock(DoubleChestInventory.class);
        Player looking = mock(Player.class);
        doReturn(Set.of(looking)).when(doubleInventory).getViewers();
        chest.doubleInventory = doubleInventory;
        ChestInventory half = mock(ChestInventory.class);
        doReturn(Map.of()).when(half).getContents();
        chest.inventory = half;
        doReturn(null).when(chest).getPair();
        doReturn(false).when(chest).unpair();

        chest.onBreak();

        verify(looking).removeWindow(doubleInventory);
    }

    @Test
    void breakingADispenserClosesTheWindowsOfItsViewers() throws Exception {
        BlockEntityDispenser dispenser = mock(BlockEntityDispenser.class, CALLS_REAL_METHODS);
        DispenserInventory inventory = mock(DispenserInventory.class);
        Player looking = mock(Player.class);
        doReturn(Set.of(looking)).when(inventory).getViewers();
        dispenser.inventory = inventory;
        doNothing().when(looking).removeWindow(inventory);

        dispenser.close();

        verify(looking).removeWindow(inventory);
    }

}
