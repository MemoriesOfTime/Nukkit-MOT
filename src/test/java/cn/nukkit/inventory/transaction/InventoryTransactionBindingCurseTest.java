package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.format.FullChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTransactionBindingCurseTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void bindingCurseOnlyBlocksPlayerArmorSlots() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        PlayerInventory playerInventory = new PlayerInventory(player);
        Mockito.when(player.getInventory()).thenReturn(playerInventory);

        BlockEntityChest leftHolder = chestHolder();
        BlockEntityChest rightHolder = chestHolder();
        ChestInventory left = new ChestInventory(leftHolder);
        ChestInventory right = new ChestInventory(rightHolder);
        Mockito.when(leftHolder.getRealInventory()).thenReturn(left);
        Mockito.when(rightHolder.getRealInventory()).thenReturn(right);
        DoubleChestInventory chest = new DoubleChestInventory(leftHolder, rightHolder);

        int[] armorIds = {Item.DIAMOND_HELMET, Item.DIAMOND_CHESTPLATE,
                Item.DIAMOND_LEGGINGS, Item.DIAMOND_BOOTS};
        List<InventoryAction> actions = new ArrayList<>();
        for (int index = 0; index < armorIds.length; index++) {
            int chestSlot = 36 + index;
            Item armor = Item.get(armorIds[index], 0, 1);
            armor.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_BINDING_CURSE));
            assertTrue(chest.setItem(chestSlot, armor, false));
            Item live = chest.getItem(chestSlot);
            actions.add(new SlotChangeAction(chest, chestSlot, live, Item.get(Item.AIR)));
            actions.add(new SlotChangeAction(playerInventory, index, Item.get(Item.AIR), live.clone()));
        }

        assertTrue(new InventoryTransaction(player, actions).execute());
        for (int index = 0; index < armorIds.length; index++) {
            assertTrue(chest.getItem(36 + index).isNull());
            assertTrue(playerInventory.getItem(index).hasEnchantment(Enchantment.ID_BINDING_CURSE));
        }

        Item equipped = Item.get(Item.DIAMOND_HELMET, 0, 1);
        equipped.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_BINDING_CURSE));
        assertTrue(playerInventory.setItem(36, equipped, false));
        Item liveEquipped = playerInventory.getItem(36);
        assertFalse(new InventoryTransaction(player, List.of(
                new SlotChangeAction(playerInventory, 36, liveEquipped, Item.get(Item.AIR)),
                new SlotChangeAction(playerInventory, 5, Item.get(Item.AIR), liveEquipped.clone())
        )).execute());
        assertTrue(playerInventory.getItem(36).hasEnchantment(Enchantment.ID_BINDING_CURSE));
        assertTrue(playerInventory.getItem(5).isNull());
    }

    private static BlockEntityChest chestHolder() {
        BlockEntityChest holder = Mockito.mock(BlockEntityChest.class);
        holder.chunk = Mockito.mock(FullChunk.class);
        return holder;
    }
}
