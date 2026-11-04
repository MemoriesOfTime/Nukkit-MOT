package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Curse of Binding belongs to equipped armor, not another inventory's numeric slots. */
class BindingCurseInventoryOwnershipTest {
    @BeforeAll static void init() { MockServer.init(); }
    @AfterEach void resetListeners() { reset(Server.getInstance().getPluginManager()); }

    @ParameterizedTest
    @ValueSource(ints = {36, 37, 38, 39})
    void cursedArmorInDoubleChestIsOrdinaryStorage(int slot) {
        Harness h = new Harness();
        DoubleChestInventory chest = chest();
        Item item = cursedArmor();
        assertTrue(chest.setItem(slot, item, false));
        InventoryTransaction tx = h.transfer(chest, slot, item);
        assertTrue(tx.canExecute(), "a chest slot is not equipped armor");
        assertTrue(tx.execute());
        assertTrue(chest.getItem(slot).isNull());
        assertTrue(h.inventory.getItem(0).equalsExact(item));
        assertFalse(tx.execute(), "a completed native transfer cannot execute twice");
        assertTrue(h.inventory.getItem(0).equalsExact(item));
    }

    @ParameterizedTest
    @ValueSource(ints = {36, 37, 38, 39})
    void equippedBindingArmorStillCannotBeRemovedInSurvival(int slot) {
        Harness h = new Harness();
        Item item = cursedArmor();
        h.inventory.setItemForce(slot, item);
        InventoryTransaction tx = h.transfer(h.inventory, slot, item);
        assertFalse(tx.canExecute());
        assertFalse(tx.execute());
        assertTrue(h.inventory.getItem(slot).equalsExact(item));
        assertTrue(h.inventory.getItem(0).isNull());
    }

    @ParameterizedTest
    @ValueSource(ints = {36, 37, 38, 39})
    void creativeMayRemoveEquippedBindingArmor(int slot) {
        Harness h = new Harness();
        h.creative.set(true);
        Item item = cursedArmor();
        h.inventory.setItemForce(slot, item);
        InventoryTransaction tx = h.transfer(h.inventory, slot, item);
        assertTrue(tx.canExecute());
        assertTrue(tx.execute());
        assertTrue(h.inventory.getItem(slot).isNull());
        assertTrue(h.inventory.getItem(0).equalsExact(item));
    }

    @ParameterizedTest
    @ValueSource(ints = {36, 37, 38, 39})
    void preExecuteRecheckKeepsChestStorageAccessibleAfterCreativeEnds(int slot) {
        Harness h = new Harness();
        h.creative.set(true);
        DoubleChestInventory chest = chest();
        Item item = cursedArmor();
        assertTrue(chest.setItem(slot, item, false));
        InventoryTransaction tx = h.transfer(chest, slot, item);
        assertTrue(tx.canExecute());
        h.creative.set(false);
        assertTrue(tx.execute(), "the second curse guard must also check inventory ownership");
        assertTrue(chest.getItem(slot).isNull());
        assertTrue(h.inventory.getItem(0).equalsExact(item));
    }

    @ParameterizedTest
    @ValueSource(ints = {36, 37, 38, 39})
    void preExecuteRecheckProtectsEquippedArmorAfterCreativeEnds(int slot) {
        Harness h = new Harness();
        h.creative.set(true);
        Item item = cursedArmor();
        h.inventory.setItemForce(slot, item);
        InventoryTransaction tx = h.transfer(h.inventory, slot, item);
        assertTrue(tx.canExecute());
        h.creative.set(false);
        assertFalse(tx.execute());
        assertTrue(h.inventory.getItem(slot).equalsExact(item));
        assertTrue(h.inventory.getItem(0).isNull());
    }

    private static Item cursedArmor() {
        Item item = Item.get(Item.DIAMOND_CHESTPLATE);
        item.addEnchantment(Enchantment.get(Enchantment.ID_BINDING_CURSE));
        item.setCustomName("named cursed armor");
        return item;
    }

    private static DoubleChestInventory chest() {
        BlockEntityChest leftHolder = mock(BlockEntityChest.class);
        BlockEntityChest rightHolder = mock(BlockEntityChest.class);
        ChestInventory left = new ChestInventory(leftHolder);
        ChestInventory right = new ChestInventory(rightHolder);
        when(leftHolder.getRealInventory()).thenReturn(left);
        when(rightHolder.getRealInventory()).thenReturn(right);
        return new DoubleChestInventory(leftHolder, rightHolder);
    }

    private static final class Harness {
        final Player player = mock(Player.class);
        final PlayerInventory inventory = new PlayerInventory(player);
        final AtomicBoolean creative = new AtomicBoolean(false);
        Harness() {
            when(player.getInventory()).thenReturn(inventory);
            when(player.getServer()).thenReturn(Server.getInstance());
            when(player.getGameVersion()).thenReturn(GameVersion.getLastVersion());
            when(player.isCreative()).thenAnswer(call -> creative.get());
            player.protocol = ProtocolInfo.v1_21_40;
        }
        InventoryTransaction transfer(Inventory source, int slot, Item item) {
            return new InventoryTransaction(player, List.of(
                    new SlotChangeAction(source, slot, item, Item.get(Item.AIR)),
                    new SlotChangeAction(inventory, 0, Item.get(Item.AIR), item))) {
                @Override protected void sendInventories() { }
            };
        }
    }
}
