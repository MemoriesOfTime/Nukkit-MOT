package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import com.google.common.collect.HashBiMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual native repair transaction and public Player.removeWindow/AnvilInventory.close. */
class NativeRecipeCloseEffectOrderingTest {
    @BeforeAll static void init() { CraftAnvilAtomicNativeAuditTest.init(); }

    private static void field(Player player, String name, Object value) throws Exception {
        Field field = Player.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(player, value);
    }

    @Test void acceptedRepairChargePrecedesDeferredNativeCloseEventAndKeepsLaterXpEdit() throws Exception {
        var fixture = new CraftAnvilAtomicNativeAuditTest.Fixture();
        var transaction = fixture.rename();
        var windows = HashBiMap.<Inventory,Integer>create();
        windows.put(fixture.anvil, Player.ANVIL_WINDOW_ID);
        field(fixture.player, "windows", windows);
        field(fixture.player, "windowIndex", windows.inverse());
        field(fixture.player, "permanentWindows", new HashSet<Integer>());
        field(fixture.player, "closingWindowId", Integer.MIN_VALUE);
        doCallRealMethod().when(fixture.player).getWindowId(any(Inventory.class));
        doCallRealMethod().when(fixture.player).getWindowById(anyInt());
        doCallRealMethod().when(fixture.player).removeWindow(any(Inventory.class));
        // The overload is protected; reflection only wires Mockito to call the stock native method.
        Method overload = Player.class.getDeclaredMethod("removeWindow", Inventory.class, boolean.class);
        overload.setAccessible(true);
        overload.invoke(doCallRealMethod().when(fixture.player), fixture.anvil, false);
        AtomicInteger experience = new AtomicInteger(10);
        when(fixture.player.getExperienceLevel()).thenAnswer(call -> experience.get());
        doAnswer(call -> { experience.set(call.getArgument(1)); return null; })
                .when(fixture.player).setExperience(anyInt(), anyInt());
        AtomicBoolean requested = new AtomicBoolean();
        AtomicInteger closes = new AtomicInteger();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem().getId() == Item.IRON_SWORD && requested.compareAndSet(false, true)) {
                fixture.player.removeWindow(fixture.anvil);
                assertTrue(windows.containsKey(fixture.anvil), "Native window identity survives a provisional setter");
            }
            if (event instanceof InventoryCloseEvent) {
                closes.incrementAndGet();
                assertEquals(9, experience.get(), "The accepted repair is paid before close callbacks run");
                assertTrue(transaction.hasExecuted());
                // An independent close callback may author a new XP balance after the paid recipe.
                experience.set(0);
            }
            return null;
        }).when(fixture.plugins).callEvent(any(Event.class));
        assertTrue(transaction.execute());
        assertTrue(requested.get());
        assertEquals(1, closes.get());
        assertFalse(windows.containsKey(fixture.anvil));
        assertEquals(0, experience.get(), "A later accepted callback is not overwritten by a delayed recipe charge");
        assertEquals("Renamed", fixture.main.getItem(0).getCustomName());
        assertTrue(fixture.anvil.getInputSlot().isNull());
        verify(fixture.player, times(1)).setExperience(0, 9);
        assertFalse(transaction.execute());
        verify(fixture.player, times(1)).setExperience(0, 9);
    }
}
