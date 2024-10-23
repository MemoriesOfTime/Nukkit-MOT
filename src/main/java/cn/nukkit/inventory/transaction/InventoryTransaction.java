package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.ShulkerBoxInventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.*;

/**
 * @author CreeperFace
 */
public class InventoryTransaction {

    private boolean invalid;
    protected boolean hasExecuted;

    protected Player source;

    protected Set<Inventory> inventories = new HashSet<>();

    protected List<InventoryAction> actions = new ArrayList<>();

    public InventoryTransaction(Player source, List<InventoryAction> actions) {
        this(source, actions, true);
    }

    public InventoryTransaction(Player source, List<InventoryAction> actions, boolean init) {
        if (init) {
            init(source, actions);
        }
    }

    protected void init(Player source, List<InventoryAction> actions) {
        //creationTime = System.currentTimeMillis();
        this.source = source;

        for (InventoryAction action : actions) {
            this.addAction(action);
        }
    }

    public Player getSource() {
        return source;
    }

    public long getCreationTime() {
        return 0; // unused
    }

    public Set<Inventory> getInventories() {
        return inventories;
    }

    public List<InventoryAction> getActionList() {
        return actions;
    }

    public Set<InventoryAction> getActions() {
        return new HashSet<>(actions);
    }

    public void addAction(InventoryAction action) {
        if (invalid) {
            Server.getInstance().getLogger().debug("Failed to add InventoryAction for " + source.getName() + ": previous run was marked as invalid");
            return;
        }

        if (action instanceof SlotChangeAction slotChangeAction) {
            Item targetItem = slotChangeAction.getTargetItem();
            Item sourceItem = slotChangeAction.getSourceItem();
            if (targetItem.getCount() > targetItem.getMaxStackSize() || sourceItem.getCount() > sourceItem.getMaxStackSize()) {
                invalid = true;
                Server.getInstance().getLogger().debug("Failed to add SlotChangeAction for " + source.getName() + ": illegal item stack size");
                return;
            }

            if (!slotChangeAction.getInventory().allowedToAdd(targetItem)) {
                invalid = true;
                Server.getInstance().getLogger().debug("Failed to add SlotChangeAction for " + source.getName() + ": " + slotChangeAction.getInventory().getName() + " inventory doesn't allow item " + targetItem.getId());
                return;
            }

            if (!source.isCreative()) {
                int slot = slotChangeAction.getSlot();
                if (slot == 36 || slot == 37 || slot == 38 || slot == 39) {
                    if (sourceItem.hasEnchantment(Enchantment.ID_BINDING_CURSE)) {
                        invalid = true;
                        Server.getInstance().getLogger().debug("Failed to add SlotChangeAction for " + source.getName() + ": armor has binding curse");
                        return;
                    }
                }
            }

            ListIterator<InventoryAction> iterator = this.actions.listIterator();

            while (iterator.hasNext()) {
                InventoryAction existingAction = iterator.next();
                if (existingAction instanceof SlotChangeAction existingSlotChangeAction) {
                    if (slotChangeAction.getInventory() instanceof ShulkerBoxInventory && (targetItem.getId() == BlockID.SHULKER_BOX || targetItem.getId() == BlockID.UNDYED_SHULKER_BOX)) {
                        invalid = true;
                        return;
                    }
                    if (!existingSlotChangeAction.getInventory().equals(slotChangeAction.getInventory())) {
                        continue;
                    }
                    Item existingSource = existingSlotChangeAction.getSourceItem();
                    Item existingTarget = existingSlotChangeAction.getTargetItem();
                    if (existingSlotChangeAction.getSlot() == slotChangeAction.getSlot() && slotChangeAction.getSourceItem().equals(existingTarget, existingTarget.hasMeta(), existingTarget.hasCompoundTag())) {
                        iterator.set(new SlotChangeAction(existingSlotChangeAction.getInventory(), existingSlotChangeAction.getSlot(), existingSlotChangeAction.getSourceItem(), slotChangeAction.getTargetItem()));
                        action.onAddToTransaction(this);
                        return;
                    } else if (existingSlotChangeAction.getSlot() == slotChangeAction.getSlot()
                            && slotChangeAction.getSourceItem().equals(existingSource, existingSource.hasMeta(), existingSource.hasCompoundTag())
                            && slotChangeAction.getTargetItem().equals(existingTarget, existingTarget.hasMeta(), existingTarget.hasCompoundTag())) {
                        existingSource.setCount(existingSource.getCount() + slotChangeAction.getSourceItem().getCount());
                        existingTarget.setCount(existingTarget.getCount() + slotChangeAction.getTargetItem().getCount());
                        iterator.set(new SlotChangeAction(existingSlotChangeAction.getInventory(), existingSlotChangeAction.getSlot(), existingSource, existingTarget));
                        return;
                    }
                }
            }
        }

        this.actions.add(action);
        action.onAddToTransaction(this);
    }

    /**
     * This method should not be used by plugins, it's used to add tracked inventories for InventoryActions
     * involving inventories.
     *
     * @param inventory to add
     */
    public void addInventory(Inventory inventory) {
        this.inventories.add(inventory);
    }

    protected boolean matchItems(List<Item> needItems, List<Item> haveItems) {
        for (InventoryAction action : this.actions) {
            if (action.getTargetItem().getId() != Item.AIR) {
                needItems.add(action.getTargetItem());
            }

            if (!action.isValid(this.source)) {
                invalid = true;
                return false;
            }

            if (action.getSourceItem().getId() != Item.AIR) {
                haveItems.add(action.getSourceItem());
            }
        }

        for (Item needItem : new ArrayList<>(needItems)) {
            for (Item haveItem : new ArrayList<>(haveItems)) {
                if (needItem.equals(haveItem)) {
                    int amount = Math.min(haveItem.getCount(), needItem.getCount());
                    needItem.setCount(needItem.getCount() - amount);
                    haveItem.setCount(haveItem.getCount() - amount);
                    if (haveItem.getCount() == 0) {
                        haveItems.remove(haveItem);
                    }
                    if (needItem.getCount() == 0) {
                        needItems.remove(needItem);
                        break;
                    }
                }
            }
        }

        return haveItems.isEmpty() && needItems.isEmpty();
    }

    protected void sendInventories() {
        if (this.getSource().protocol >= ProtocolInfo.v1_16_0) {
            for (InventoryAction action : this.actions) {
                if (action instanceof SlotChangeAction) {
                    SlotChangeAction sca = (SlotChangeAction) action;
                    sca.getInventory().sendSlot(sca.getSlot(), this.source);
                }
            }
        } else {
            for (Inventory inventory : this.inventories) {
                inventory.sendContents(this.source);
                if (inventory instanceof PlayerInventory) {
                    ((PlayerInventory) inventory).sendArmorContents(this.source);
                }
            }
        }
    }

    public boolean canExecute() {
        List<Item> haveItems = new ArrayList<>();
        List<Item> needItems = new ArrayList<>();
        return matchItems(needItems, haveItems) && !this.actions.isEmpty() && haveItems.isEmpty() && needItems.isEmpty();
    }

    protected boolean callExecuteEvent() {
        InventoryTransactionEvent ev = new InventoryTransactionEvent(this);
        this.source.getServer().getPluginManager().callEvent(ev);

        SlotChangeAction from = null;
        SlotChangeAction to = null;
        Player who = null;

        for (InventoryAction action : this.actions) {
            if (!(action instanceof SlotChangeAction)) {
                continue;
            }
            SlotChangeAction slotChange = (SlotChangeAction) action;

            if (slotChange.getInventory().getHolder() instanceof Player) {
                who = (Player) slotChange.getInventory().getHolder();
            }

            if (from == null) {
                from = slotChange;
            } else {
                to = slotChange;
            }
        }

        if (who != null && to != null) {
            if (from.getTargetItem().getCount() > from.getSourceItem().getCount()) {
                from = to;
            }

            InventoryClickEvent ev2 = new InventoryClickEvent(who, from.getInventory(), from.getSlot(), from.getSourceItem(), from.getTargetItem());
            this.source.getServer().getPluginManager().callEvent(ev2);

            if (ev2.isCancelled()) {
                return false;
            }
        }

        return !ev.isCancelled();
    }

    public boolean execute() {
        if (this.hasExecuted() || !this.canExecute()) {
            this.sendInventories();
            return false;
        }


        if (!callExecuteEvent()) {
            this.sendInventories();
            return true;
        }

        for (InventoryAction action : this.actions) {
            if (!action.onPreExecute(this.source)) {
                this.sendInventories();
                return true;
            }
            if (action instanceof SlotChangeAction) {
                if (!source.isCreative()) {
                    int slot = ((SlotChangeAction) action).getSlot();
                    if (slot == 36 || slot == 37 || slot == 38 || slot == 39) {
                        if (action.getSourceItem().hasEnchantment(Enchantment.ID_BINDING_CURSE)) {
                            this.sendInventories();
                            return false;
                        }
                    }
                }
            }
        }

        for (InventoryAction action : this.actions) {
            if (action.execute(this.source)) {
                action.onExecuteSuccess(this.source);
            } else {
                action.onExecuteFail(this.source);
            }
        }

        this.hasExecuted = true;
        return true;
    }

    public boolean hasExecuted() {
        return this.hasExecuted;
    }
}
