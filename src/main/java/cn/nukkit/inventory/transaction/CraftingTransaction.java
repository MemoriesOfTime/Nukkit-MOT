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
import cn.nukkit.recipe.*;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.MultiRecipe;
import cn.nukkit.recipe.impl.SmithingRecipe;

import java.util.ArrayList;
import java.util.Collection;
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
                if (new DefaultDescriptor(existingInput).equals(new DefaultDescriptor(item))) {
                    existingInput.setCount(existingInput.getCount() + item.getCount());
                    return;
                }
            }
            inputs.add(item.clone());
        } else {
            throw new RuntimeException("Input list is full can't add " + item);
        }
    }

    public Collection<ItemDescriptor> getInputList() {
        Collection<ItemDescriptor> list = new ArrayList<>();
        for(Item item : inputs) {
            list.add(new DefaultDescriptor(item));
        }
        return list;
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
        Inventory inventory;
        if (craftingType == Player.CRAFTING_SMITHING) {
            inventory = source.getWindowById(Player.SMITHING_WINDOW_ID);
            if (inventory instanceof SmithingInventory smithingInventory) {
                addInventory(inventory);
                SmithingRecipe smithingRecipe = smithingInventory.matchRecipe();
                if (smithingRecipe != null && this.primaryOutput.equals(smithingRecipe.getFinalResult(smithingInventory.getEquipment()), true, true)) {
                    setTransactionRecipe(smithingRecipe);
                }
            }
        } else {
            MultiRecipe multiRecipe = RecipeRegistry.getMultiRecipe(this.source, this.getPrimaryOutput(), this.getInputList());
            if (multiRecipe != null) {
                setTransactionRecipe(multiRecipe.toRecipe(this.getPrimaryOutput(), this.getInputList()));
            } else {
                setTransactionRecipe(RecipeRegistry.matchRecipe(inputs, this.primaryOutput, this.secondaryOutputs));
            }
        }
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
        ContainerClosePacket packet = new ContainerClosePacket();
        packet.windowId = ContainerIds.NONE;
        packet.wasServerInitiated = true;
        packet.type = ContainerType.NONE;
        source.getServer().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> source.dataPacket(packet), 20);

        this.source.resetCraftingGridType();
    }

    public boolean checkForCraftingPart(List<InventoryAction> actions) {
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
