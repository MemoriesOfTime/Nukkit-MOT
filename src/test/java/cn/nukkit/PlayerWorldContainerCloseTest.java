package cn.nukkit;

import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.CustomInventory;
import cn.nukkit.inventory.FakeBlockMenu;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A 1.21 client closes a chest, barrel or shulker box with window id -1 while its own inventory is
 * not open. The window of a real world container closes; a plugin window drawn on a fake block does
 * not, because a late -1 must not shut a fresh window of that kind.
 */
class PlayerWorldContainerCloseTest {

    @Test
    void aContainerOfAStandingBlockEntityIsAWorldContainer() {
        Level level = mock(Level.class);
        BlockEntityChest chest = chest(level);
        doReturn(chest).when(level).getBlockEntityIfLoaded(any(Vector3.class));

        assertTrue(Player.isWorldBlockContainer(inventory(chest)));
    }

    @Test
    void aFakeBlockWindowIsNotAWorldContainer() {
        Level level = mock(Level.class);
        FakeBlockMenu fake = new FakeBlockMenu(null, new cn.nukkit.level.Position(1, 2, 3, level));

        assertFalse(Player.isWorldBlockContainer(inventory(fake)));
    }

    @Test
    void aClosedOrReplacedBlockEntityIsNotAWorldContainer() throws Exception {
        Level level = mock(Level.class);
        BlockEntityChest replaced = chest(level);
        doReturn(chest(level)).when(level).getBlockEntityIfLoaded(any(Vector3.class));
        assertFalse(Player.isWorldBlockContainer(inventory(replaced)), "another block entity stands there");

        BlockEntityChest closed = chest(level);
        doReturn(closed).when(level).getBlockEntityIfLoaded(any(Vector3.class));
        closed.closed = true;
        assertFalse(Player.isWorldBlockContainer(inventory(closed)));
    }

    @Test
    void unknownWindowIdClosesTheWorldChestAndLeavesTheFakeWindow() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        PluginManager plugins = mock(PluginManager.class);
        setServer(player, plugins);
        Level level = mock(Level.class);
        BlockEntityChest chest = chest(level);
        doReturn(chest).when(level).getBlockEntityIfLoaded(any(Vector3.class));
        Inventory world = inventory(chest);
        Inventory fake = inventory(new FakeBlockMenu(null, new cn.nukkit.level.Position(1, 2, 3, level)));
        doReturn(List.of(world, fake)).when(player).openWindowsSnapshot();
        doReturn(5).when(player).getWindowId(world);
        doReturn(6).when(player).getWindowId(fake);
        doNothing().when(player).removeWindow(any(), anyBoolean());

        player.closeWorldContainerWindowsForUnknownId();

        verify(plugins, times(1)).callEvent(any(InventoryCloseEvent.class));
        verify(player).removeWindow(same(world), eq(true));
        verify(player, never()).removeWindow(same(fake), anyBoolean());
    }

    private static BlockEntityChest chest(Level level) {
        BlockEntityChest chest = mock(BlockEntityChest.class);
        doReturn(level).when(chest).getLevel();
        return chest;
    }

    private static Inventory inventory(cn.nukkit.inventory.InventoryHolder holder) {
        ContainerInventory inventory = holder instanceof BlockEntityChest
                ? mock(ChestInventory.class)
                : mock(CustomInventory.class);
        doReturn(holder).when(inventory).getHolder();
        return inventory;
    }

    private static void setServer(Player player, PluginManager plugins) throws Exception {
        Server server = mock(Server.class);
        doReturn(plugins).when(server).getPluginManager();
        Field field = cn.nukkit.entity.Entity.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(player, server);
    }
}
