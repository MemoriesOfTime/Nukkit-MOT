package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.plugin.InternalPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * @author CreeperFace
 */
public class CraftingTransaction extends InventoryTransaction {

    protected int gridSize;

    protected List<Item> inputs;

    protected List<Item> secondaryOutputs;

    protected Item primaryOutput;

    protected CraftingRecipe recipe;

    private Recipe transactionRecipe;

    protected int craftingType;

    public CraftingTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions, false);

        this.craftingType = source.craftingType;
        this.gridSize = (source.getCraftingGrid() instanceof BigCraftingGrid) ? 3 : 2;
        this.inputs = new ArrayList<>();
        this.secondaryOutputs = new ArrayList<>();

        init(source, actions);
    }

    public void setInput(Item item) {
        if (inputs.size() < gridSize * gridSize) {
            for (Item existingInput : this.inputs) {
                if (existingInput.equals(item, item.hasMeta(), item.hasCompoundTag())) {
                    existingInput.setCount(existingInput.getCount() + item.getCount());
                    return;
                }
            }
            inputs.add(item.clone());
        } else {
            throw new RuntimeException("Input list is full can't add " + item);
        }
    }

    public List<Item> getInputList() {
        return inputs;
    }

    public void setExtraOutput(Item item) {
        if (secondaryOutputs.size() < gridSize * gridSize) {
            secondaryOutputs.add(item.clone());
        } else {
            throw new RuntimeException("Output list is full can't add " + item);
        }
    }

    public Item getPrimaryOutput() {
        return primaryOutput;
    }

    public void setPrimaryOutput(Item item) {
        if (primaryOutput == null) {
            primaryOutput = item.clone();
        } else if (!primaryOutput.equals(item)) {
            throw new RuntimeException("Primary result item has already been set and does not match the current item (expected " + primaryOutput + ", got " + item + ')');
        }
    }

    public CraftingRecipe getRecipe() {
        return recipe;
    }

    public Recipe getTransactionRecipe() {
        return this.transactionRecipe;
    }

    protected void setTransactionRecipe(Recipe recipe) {
        this.transactionRecipe = recipe;
        this.recipe = recipe instanceof CraftingRecipe ? (CraftingRecipe) recipe : null;
    }

    @Override
    public boolean canExecute() {
        Recipe recipe;
        recipe = source.getServer().getCraftingManager().matchRecipe(this.inputs, this.primaryOutput, this.secondaryOutputs);
        if (recipe == null) {
            MultiRecipe multiRecipe = source.getServer().getCraftingManager().getMultiRecipe(this.source, this.getPrimaryOutput(), this.getInputList());
            if (multiRecipe != null) {
                recipe = multiRecipe.toRecipe(this.getPrimaryOutput(), this.getInputList());
            }
        }
        this.setTransactionRecipe(recipe);
        return this.getTransactionRecipe() != null && super.canExecute();
    }

    @Override
    protected boolean callExecuteEvent() {
        CraftItemEvent ev;

        this.source.getServer().getPluginManager().callEvent(ev = new CraftItemEvent(this));
        return !ev.isCancelled();
    }

    @Override
    protected void sendInventories() {
        super.sendInventories();

        if (source.craftingType == Player.CRAFTING_SMALL) {
            return; // Already closed
        }

        /*
         * TODO: HACK!
         * we can't resend the contents of the crafting window, so we force the client to close it instead.
         * So people don't whine about messy desync issues when someone cancels CraftItemEvent, or when a crafting
         * transaction goes wrong.
         */
        source.getServer().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
            if (source.isOnline() && source.isAlive()) {
                ContainerClosePacket pk = new ContainerClosePacket();
                pk.windowId = ContainerIds.NONE;
                pk.wasServerInitiated = true;
                pk.type = ContainerType.NONE;
                source.dataPacket(pk);
            }
        }, 10);

        this.source.resetCraftingGridType();
    }

    @Override
    public boolean execute() {
        normalizeCraftingResultSlots();
        if (super.execute()) {
            switch (this.primaryOutput.getId()) {
                case Item.CRAFTING_TABLE -> source.awardAchievement("buildWorkBench");
                case Item.WOODEN_PICKAXE -> source.awardAchievement("buildPickaxe");
                case Item.FURNACE -> source.awardAchievement("buildFurnace");
                case Item.WOODEN_HOE -> source.awardAchievement("buildHoe");
                case Item.BREAD -> source.awardAchievement("makeBread");
                case Item.CAKE -> source.awardAchievement("bakeCake");
                case Item.STONE_PICKAXE, Item.GOLDEN_PICKAXE,
                    Item.IRON_PICKAXE, Item.DIAMOND_PICKAXE -> source.awardAchievement("buildBetterPickaxe");
                case Item.WOODEN_SWORD -> source.awardAchievement("buildSword");
                case Item.DIAMOND -> source.awardAchievement("diamond");
            }

            return true;
        }

        return false;
    }

    /**
     * Rewrites client-predicted crafting result slot changes to match the server inventory placement rules.
     * Crafting output should merge into existing partial stacks before occupying empty slots.
     */
    void normalizeCraftingResultSlots() {
        if (this.primaryOutput == null || this.primaryOutput.isNull()) {
            return;
        }

        PlayerInventory inventory = this.source.getInventory();
        if (inventory == null) {
            return;
        }

        List<InventoryAction> normalized = new ArrayList<>(this.actions.size());
        Item remaining = this.primaryOutput.clone();
        boolean resultPlacementReplaced = false;

        for (InventoryAction action : this.actions) {
            if (action instanceof SlotChangeAction slotChangeAction && isPrimaryOutputPlacement(slotChangeAction, inventory)) {
                if (!resultPlacementReplaced) {
                    addNormalizedResultActions(normalized, inventory, remaining);
                    resultPlacementReplaced = true;
                }
                continue;
            }

            normalized.add(action);
        }

        this.actions = normalized;
        this.inventories.add(inventory);
    }

    /**
     * Returns true when the slot change represents adding the primary crafting output to the player inventory.
     */
    private boolean isPrimaryOutputPlacement(SlotChangeAction action, PlayerInventory inventory) {
        if (action.getInventory() != inventory) {
            return false;
        }

        Item target = action.getTargetItem();
        if (target.isNull() || !target.equals(this.primaryOutput, true, true)) {
            return false;
        }

        Item source = action.getSourceItem();
        return source.isNull() || source.equals(this.primaryOutput, true, true) && target.getCount() > source.getCount();
    }

    /**
     * Adds replacement slot changes for the crafting result using the same order as {@link BaseInventory#addItem(Item...)}.
     */
    private void addNormalizedResultActions(List<InventoryAction> normalized, PlayerInventory inventory, Item remaining) {
        if (remaining.getCount() <= 0) {
            return;
        }

        // Mirror BaseInventory#addItem: fill partial stacks first, then use empty slots in slot order.
        for (int slot = 0; slot < inventory.getSize() && remaining.getCount() > 0; slot++) {
            Item current = inventory.getItem(slot);
            if (current.isNull() || !current.equals(remaining, true, true)) {
                continue;
            }

            int maxStackSize = Math.min(current.getMaxStackSize(), inventory.getMaxStackSize());
            int space = maxStackSize - current.getCount();
            if (space <= 0) {
                continue;
            }

            int moved = Math.min(space, remaining.getCount());
            Item target = current.clone();
            target.setCount(current.getCount() + moved);
            normalized.add(new SlotChangeAction(inventory, slot, current, target));
            remaining.setCount(remaining.getCount() - moved);
        }

        for (int slot = 0; slot < inventory.getSize() && remaining.getCount() > 0; slot++) {
            Item current = inventory.getItem(slot);
            if (!current.isNull()) {
                continue;
            }

            int moved = Math.min(Math.min(remaining.getMaxStackSize(), inventory.getMaxStackSize()), remaining.getCount());
            Item target = remaining.clone();
            target.setCount(moved);
            normalized.add(new SlotChangeAction(inventory, slot, current, target));
            remaining.setCount(remaining.getCount() - moved);
        }
    }

    @Override
    public boolean checkForItemPart(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (slotChangeAction.getInventory().getType() == InventoryType.UI) {
                    if (slotChangeAction.getSlot() == 50) {
                        if (!slotChangeAction.getSourceItem().equals(slotChangeAction.getTargetItem())) {
                            return true;
                        } else {
                            Server.getInstance().getLogger().debug("Source equals target");
                            return false;
                        }
                    } else {
                        Server.getInstance().getLogger().debug("Invalid slot: " + slotChangeAction.getSlot());
                        return false;
                    }
                } else {
                    Server.getInstance().getLogger().debug("Invalid action type: " + slotChangeAction.getInventory().getType());
                    return false;
                }
            } else {
                Server.getInstance().getLogger().debug("SlotChangeAction expected, got " + action);
                return false;
            }
        }
        Server.getInstance().getLogger().debug("No actions on the list");
        return false;
    }
}
