package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.DataPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Actual native furnace update; no new helper APIs are needed by this behavioral regression. */
class NativeFurnaceCommitOrderingTest {
    @BeforeAll static void init() throws Exception { NativeFurnaceAtomicConversionTest.init(); }
    @AfterAll static void restore() throws Exception { NativeFurnaceAtomicConversionTest.restore(); }

    @Test void acceptedViewerErrorConsumesCookProgressBeforeTheNextNativeTick() {
        var furnace = new NativeFurnaceAtomicConversionTest().make(true);
        Player viewer = mock(Player.class);
        viewer.protocol = 2193;
        when(viewer.getWindowId(furnace.getInventory())).thenReturn(1);
        furnace.getInventory().getViewers().add(viewer);
        doAnswer(call -> { throw new AssertionError("accepted viewer notification"); })
                .when(viewer).dataPacket(any(DataPacket.class));
        assertThrows(AssertionError.class, furnace::onUpdate);
        assertEquals(4, furnace.getInventory().getSmelting().getCount());
        assertEquals(1, furnace.getInventory().getResult().getCount());
        assertEquals(0.7f, furnace.getExperience());
        assertEquals(0, furnace.cookTime, "The accepted input/output includes its spent cook progress");
        furnace.getInventory().getViewers().clear();
        furnace.onUpdate();
        assertEquals(4, furnace.getInventory().getSmelting().getCount());
        assertEquals(1, furnace.getInventory().getResult().getCount(), "A viewer error cannot fund a second instant smelt");
        assertEquals(1, furnace.cookTime);
    }
}
