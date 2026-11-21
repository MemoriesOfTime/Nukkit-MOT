package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryTransactionErrorRollbackTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void clearListener() { reset(Server.getInstance().getPluginManager()); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void errorAfterEarlierSlotWriteRestoresBothSidesAndAllowsRetry(boolean creditFirst) {
        Player player = mock(Player.class);
        PlayerInventory inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getServer()).thenReturn(Server.getInstance());
        when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
        player.protocol = ProtocolInfo.v1_26_50;
        Item source = Item.get(Item.DIAMOND, 0, 10);
        source.setCustomName("retained source");
        inventory.setItemForce(0, source);
        SlotChangeAction debit = new SlotChangeAction(inventory, 0, source, Item.get(Item.AIR));
        SlotChangeAction credit = new SlotChangeAction(inventory, 1, Item.get(Item.AIR), source);
        InventoryTransaction transaction = new InventoryTransaction(player,
                creditFirst ? List.of(credit, debit) : List.of(debit, credit));
        AssertionError failure = new AssertionError("native slot listener failed");
        AtomicBoolean thrown = new AtomicBoolean();
        int secondSlot = creditFirst ? 0 : 1;
        PluginManager plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == secondSlot && thrown.compareAndSet(false, true)) {
                throw failure;
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));

        assertSame(failure, assertThrows(AssertionError.class, transaction::execute));
        assertTrue(inventory.getItem(0).equalsExact(source), "the source must survive partial native debit");
        assertTrue(inventory.getItem(1).isNull(), "an earlier credit must not mint a second stack");
        assertEquals(10, count(inventory));
        assertFalse(transaction.hasExecuted());

        reset(Server.getInstance().getPluginManager());
        assertTrue(transaction.execute(), "error cleanup must release ownership so a lawful retry can run");
        assertTrue(inventory.getItem(0).isNull());
        assertTrue(inventory.getItem(1).equalsExact(source));
        assertEquals(10, count(inventory));
        assertFalse(transaction.execute(), "completed retry cannot execute a second time");
        assertEquals(10, count(inventory));
    }

    private static int count(PlayerInventory inventory) {
        return inventory.getContents().values().stream().mapToInt(Item::getCount).sum();
    }
}
