package cn.nukkit.inventory.request;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.*;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMappingTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void levelEntityAndCrafterContainerResolveToTopWindow() {
        Player player = Mockito.mock(Player.class);
        Inventory topWindow = Mockito.mock(Inventory.class);

        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(topWindow));

        assertSame(topWindow, NetworkMapping.getInventory(player, ContainerSlotType.LEVEL_ENTITY, null));
        assertSame(topWindow, NetworkMapping.getInventory(player, ContainerSlotType.CRAFTER_BLOCK_CONTAINER, null));
    }

    @Test
    void dynamicContainerCanResolveNestedBundleFromAccessibleInventories() {
        Player player = Mockito.mock(Player.class);
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        PlayerOffhandInventory offhand = Mockito.mock(PlayerOffhandInventory.class);
        PlayerCursorInventory cursor = Mockito.mock(PlayerCursorInventory.class);
        CraftingGrid craftingGrid = Mockito.mock(CraftingGrid.class);

        ItemBundle outerBundle = new ItemBundle();
        ItemBundle innerBundle = new ItemBundle();
        outerBundle.getInventory().setItem(0, innerBundle, false);

        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getCursorInventory()).thenReturn(cursor);
        Mockito.when(player.getCraftingGrid()).thenReturn(craftingGrid);
        Mockito.when(inventory.getContents()).thenReturn(Map.of());
        Mockito.when(offhand.getContents()).thenReturn(Map.of(0, outerBundle));
        Mockito.when(cursor.getContents()).thenReturn(Map.of());
        Mockito.when(craftingGrid.getContents()).thenReturn(Map.of());

        Inventory resolved = NetworkMapping.getInventory(player, ContainerSlotType.DYNAMIC_CONTAINER, innerBundle.getBundleId());
        BundleInventory bundleInventory = assertInstanceOf(BundleInventory.class, resolved);

        assertEquals(innerBundle.getBundleId(), bundleInventory.getHolder().getBundleId());
        assertEquals(innerBundle.getInventory().getContents(), bundleInventory.getContents());
    }
}
