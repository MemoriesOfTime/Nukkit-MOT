package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.inventory.PlayerEnderChestInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * An ender chest window must not outlive the reason it was open: a 1.21 client closes it with
 * window id -1 while its own inventory is not open, and a dead player keeps nothing open.
 */
class PlayerEnderChestCloseTest {

    @Test
    void unknownWindowIdClosesTheOpenEnderChest() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        PluginManager plugins = mock(PluginManager.class);
        setServer(player, plugins);
        PlayerEnderChestInventory enderChest = mock(PlayerEnderChestInventory.class);
        doReturn(enderChest).when(player).getEnderChestInventory();
        doReturn(7).when(player).getWindowId(enderChest);
        doNothing().when(player).removeWindow(any(), anyBoolean());

        player.closeEnderChestWindowForUnknownId();

        verify(plugins).callEvent(any(InventoryCloseEvent.class));
        verify(player).removeWindow(same(enderChest), eq(true));
    }

    @Test
    void unknownWindowIdLeavesPlayersWithoutAnEnderChestAlone() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        PluginManager plugins = mock(PluginManager.class);
        setServer(player, plugins);
        PlayerEnderChestInventory enderChest = mock(PlayerEnderChestInventory.class);
        doReturn(enderChest).when(player).getEnderChestInventory();
        doReturn(-1).when(player).getWindowId(enderChest);

        player.closeEnderChestWindowForUnknownId();

        verify(plugins, never()).callEvent(any());
        verify(player, never()).removeWindow(any(), anyBoolean());
    }

    @Test
    void deathClosesWindowsBeforeTheDropsAreCollected() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        PluginManager plugins = mock(PluginManager.class);
        setServer(player, plugins);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof PlayerDeathEvent death) {
                death.setCancelled(true);
            }
            return null;
        }).when(plugins).callEvent(any());
        player.spawned = true;
        player.level = mock(Level.class, Mockito.RETURNS_DEEP_STUBS);
        player.level.gameRules = mock(GameRules.class);
        doReturn(null).when(player).getLastDamageCause();
        doNothing().when(player).removeAllWindows();
        doReturn(Item.EMPTY_ARRAY).when(player).getDrops();

        player.kill();

        InOrder order = Mockito.inOrder(player);
        order.verify(player).removeAllWindows();
        order.verify(player).getDrops();
    }

    private static void setServer(Player player, PluginManager plugins) throws Exception {
        Server server = mock(Server.class);
        doReturn(plugins).when(server).getPluginManager();
        Field field = cn.nukkit.entity.Entity.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(player, server);
    }
}
