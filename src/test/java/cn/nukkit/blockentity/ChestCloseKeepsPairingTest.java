package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A chest that is closing (chunk unload, break) must not pair again.
 *
 * <p>The container close() asks getInventory() for the viewers after the chest close() has
 * dropped the double inventory. Re-pairing there built a new DoubleChestInventory, replayed every
 * stack through onSlotChange and woke the hoppers and comparators around the chest; on a chunk
 * unload that read the neighbour chunk from disk on the main thread.
 */
class ChestCloseKeepsPairingTest {

    private static BlockEntityChest pairedChest() {
        BlockEntityChest chest = mock(BlockEntityChest.class, CALLS_REAL_METHODS);
        chest.namedTag = new CompoundTag()
                .putInt("x", 15).putInt("y", 64).putInt("z", 0)
                .putInt("pairx", 14).putInt("pairz", 0)
                .putBoolean("pairlead", true);
        ChestInventory half = mock(ChestInventory.class);
        doReturn(Set.of()).when(half).getViewers();
        chest.inventory = half;
        // The pair is still loaded in the real world; a null pair keeps the old path reachable
        // without building a real DoubleChestInventory in the mock.
        chest.level = mock(Level.class);
        doReturn(null).when(chest).getPair();
        return chest;
    }

    @Test
    void closingAPairedChestDoesNotPairAgain() {
        BlockEntityChest chest = pairedChest();

        chest.close();

        verify(chest, never()).checkPairing();
        assertNull(chest.doubleInventory);
    }

    @Test
    void closingAHalfClosesTheDoubleChestOpenedThroughItsPair() {
        // The pair opened the double chest, so only the pair's field holds it; this half's real
        // inventory still points at it. Re-pairing used to close the viewer's window as a side
        // effect; without it the window would outlive the half whose chunk is unloading.
        BlockEntityChest chest = pairedChest();
        DoubleChestInventory shared = mock(DoubleChestInventory.class);
        Player viewer = mock(Player.class);
        doReturn(Set.of(viewer)).when(shared).getViewers();
        doReturn(shared).when((ChestInventory) chest.inventory).getDoubleInventory();

        chest.close();

        verify(viewer).removeWindow(shared);
        verify(chest, never()).checkPairing();
    }

    @Test
    void aDestroyedChestAnswersWithItsOwnHalf() {
        BlockEntityChest chest = pairedChest();
        chest.inventory.destroyed = true;

        assertSame(chest.inventory, chest.getInventory());
        verify(chest, never()).checkPairing();
    }
}
