package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.inventory.AnvilInventory;
import cn.nukkit.inventory.EnchantInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.ContainerSetContentPacketV113;
import cn.nukkit.network.protocol.v113.ContainerSetSlotPacketV113;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class ContainerSetSlotProcessorV113 extends DataPacketProcessor<ContainerSetSlotPacketV113> {
    
    public static final ContainerSetSlotProcessorV113 INSTANCE = new ContainerSetSlotProcessorV113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ContainerSetSlotPacketV113 pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }
        
        if (pk.slot < 0) {
            return;
        }

        Inventory inv;
        InventoryAction transaction;
        if (pk.windowid == 0) { //Our inventory
            inv = player.getInventory();
            if (pk.slot >= inv.getSize()) {
                return;
            }
            if (player.isCreative()) {
                if (Item.getCreativeItemIndex(pk.item) != -1) {
                    inv.setItem(pk.slot, pk.item);
                    player.getInventory().setHotbarSlotIndex(pk.slot, pk.slot); //links hotbar[packet.slot] to slots[packet.slot]
                }
            }
            transaction = new SlotChangeAction(inv, pk.slot, inv.getItem(pk.slot), pk.item);
        } else if (pk.windowid == ContainerSetContentPacketV113.SPECIAL_ARMOR) { //Our armor
            inv = player.getInventory();
            if (pk.slot >= 4) {
                return;
            }
            transaction = new SlotChangeAction(inv, pk.slot + inv.getSize(), player.getInventory().getArmorItem(pk.slot), pk.item);
        } else if (playerHandle.getWindowIndex().containsKey(pk.windowid)) {
            inv = player.getWindowById(pk.windowid);

            if (!(inv instanceof AnvilInventory)) {
                //player.craftingType = CRAFTING_SMALL;
            }

            if (inv instanceof EnchantInventory && pk.item.hasEnchantments()) {
                //((EnchantInventory) inv).onEnchant(player, inv.getItem(pk.slot), pk.item);
            }

            transaction = new SlotChangeAction(inv, pk.slot, inv.getItem(pk.slot), pk.item);
        } else {
            return;
        }

        if (inv != null) {
            Item sourceItem = inv.getItem(pk.slot);
            Item heldItem = sourceItem.clone();
            heldItem.setCount(sourceItem.getCount() - pk.item.getCount());
            if (heldItem.getCount() > 0) { //In win10, click mouse and hold on item
                InventoryClickEvent inventoryClickEvent = new InventoryClickEvent(player, inv, pk.slot, sourceItem, heldItem);
                player.getServer().getPluginManager().callEvent(inventoryClickEvent);
                //TODO Fix hold on bug and support Cancellable
            }
        }

        if (transaction.getSourceItem().deepEquals(transaction.getTargetItem()) && transaction.getTargetItem().getCount() == transaction.getSourceItem().getCount()) { //No changes!
            //No changes, just a local inventory update sent by the server
            return;
        }


        //TODO
        /*if (player.currentTransaction == null || player.currentTransaction.getCreationTime() < (System.currentTimeMillis() - 8 * 1000)) {
            if (player.currentTransaction != null) {
                for (Inventory inventory : player.currentTransaction.getInventories()) {
                    if (inventory instanceof PlayerInventory) {
                        ((PlayerInventory) inventory).sendArmorContents(player);
                    }
                    inventory.sendContents(player);
                }
            }
            player.currentTransaction = new SimpleTransactionGroup(player);
        }

        player.currentTransaction.addTransaction(transaction);

        if (player.currentTransaction.canExecute() || player.isCreative()) {
            HashSet<String> achievements = new HashSet<>();

            for (Transaction tr : player.currentTransaction.getTransactions()) {
                Inventory inv1 = tr.getInventory();

                if (inv1 instanceof FurnaceInventory) {
                    if (tr.getSlot() == 2) {
                        switch (((FurnaceInventory) inv1).getResult().getId()) {
                            case Item.IRON_INGOT:
                                achievements.add("acquireIron");
                                break;
                        }
                    }
                }
            }

            if (player.currentTransaction.execute(player.isCreative())) {
                for (String achievement : achievements) {
                    player.awardAchievement(achievement);
                }
            }

            player.currentTransaction = null;
        } else {
            if (pk.item.getId() != 0) {
                inventory.sendSlot(pk.hotbarSlot, player);
                inventory.sendSlot(pk.slot, player);
            }
        }*/
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ContainerSetContentPacketV113.NETWORK_ID);
    }
    
}
