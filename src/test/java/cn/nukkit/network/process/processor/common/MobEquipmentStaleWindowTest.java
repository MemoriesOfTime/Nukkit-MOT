package cn.nukkit.network.process.processor.common;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerKickEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobEquipmentStaleWindowTest {
    @BeforeAll
    static void initializeItems() {
        MockServer.init();
    }

    @Test
    void delayedRepliesFromClosedWindowsDoNotDisconnectOrPreventCurrentSelection() {
        Player player = player();
        PlayerHandle handle = new PlayerHandle(player);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item held = Item.get(Item.STONE, 0, 1);
        when(player.getWindowById(0)).thenReturn(inventory);
        when(inventory.getItem(3)).thenReturn(held);
        when(inventory.getHeldItemIndex()).thenReturn(2);

        // A finite set of replies arriving after six previously open windows closed.
        for (int oldWindow = 1; oldWindow <= 6; oldWindow++) {
            MobEquipmentProcessor.INSTANCE.handle(handle, equipment(oldWindow, 3, held));
        }
        MobEquipmentProcessor.INSTANCE.handle(handle, equipment(0, 3, held));

        verify(player, never()).kick(any(PlayerKickEvent.Reason.class), anyString(), anyBoolean(), anyString());
        verify(inventory).equipItem(3);
        verify(inventory, never()).sendContents(player);
        verify(inventory, never()).setItem(anyInt(), any(Item.class));
    }

    @Test
    void currentWindowStillResendsCanonicalContentsWhenClientItemDiffers() {
        Player player = player();
        PlayerInventory inventory = mock(PlayerInventory.class);
        Item held = Item.get(Item.STONE, 0, 1);
        when(player.getWindowById(0)).thenReturn(inventory);
        when(inventory.getItem(3)).thenReturn(held);

        MobEquipmentProcessor.INSTANCE.handle(new PlayerHandle(player), equipment(0, 3, Item.get(Item.DIRT)));

        verify(inventory).sendContents(player);
        verify(inventory, never()).setItem(anyInt(), any(Item.class));
        verify(player, never()).kick(any(PlayerKickEvent.Reason.class), anyString(), anyBoolean(), anyString());
    }

    @Test
    void repliesFromClosedWindowsLeaveCurrentUseAndInventoryUntouched() {
        Player player = player();

        MobEquipmentProcessor.INSTANCE.handle(new PlayerHandle(player), equipment(6, 3, Item.get(Item.STONE)));

        verify(player, never()).setUsingItem(anyBoolean());
        verify(player, never()).getInventory();
    }

    private static Player player() {
        MockServer.reset();
        Player player = mock(Player.class);
        player.spawned = true;
        when(player.isAlive()).thenReturn(true);
        when(player.getServer()).thenReturn(cn.nukkit.Server.getInstance());
        when(player.getName()).thenReturn("RegularPlayer");
        return player;
    }

    private static MobEquipmentPacket equipment(int windowId, int slot, Item item) {
        MobEquipmentPacket packet = new MobEquipmentPacket();
        packet.windowId = windowId;
        packet.hotbarSlot = slot;
        packet.item = item;
        return packet;
    }
}
