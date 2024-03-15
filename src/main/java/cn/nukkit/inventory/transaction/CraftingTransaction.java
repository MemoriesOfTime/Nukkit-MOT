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
import cn.nukkit.scheduler.Task;

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

    public boolean canExecute() {
        CraftingManager craftingManager = source.getServer().getCraftingManager();
        Inventory inventory;
        if (craftingType == Player.CRAFTING_SMITHING) {
            inventory = source.getWindowById(Player.SMITHING_WINDOW_ID);
            if (inventory instanceof SmithingInventory smithingInventory) {
                addInventory(inventory);
                SmithingRecipe smithingRecipe = smithingInventory.matchRecipe();
                if (smithingRecipe != null && this.primaryOutput.equals(smithingRecipe.getFinalResult(smithingInventory.getEquipment(), smithingInventory.getTemplate()), true, true)) {
                    setTransactionRecipe(smithingRecipe);
                }
            }
        } else {
            MultiRecipe multiRecipe = Server.getInstance().getCraftingManager().getMultiRecipe(this.source, this.getPrimaryOutput(), this.getInputList());
            if (multiRecipe != null) {
                setTransactionRecipe(multiRecipe.toRecipe(this.getPrimaryOutput(), this.getInputList()));
            } else {
                setTransactionRecipe(craftingManager.matchRecipe(source.protocol, inputs, this.primaryOutput, this.secondaryOutputs));
            }
        }
        return this.getTransactionRecipe() != null && super.canExecute();
    }

    protected boolean callExecuteEvent() {
        CraftItemEvent ev;

        this.source.getServer().getPluginManager().callEvent(ev = new CraftItemEvent(this));
        return !ev.isCancelled();
    }

    protected void sendInventories() {
        super.sendInventories();

        /*
         * TODO: HACK!
         * we can't resend the contents of the crafting window, so we force the client to close it instead.
         * So people don't whine about messy desync issues when someone cancels CraftItemEvent, or when a crafting
         * transaction goes wrong.
         */
        ContainerClosePacket pk = new ContainerClosePacket();
        pk.windowId = ContainerIds.NONE;
        pk.wasServerInitiated = true;
        source.getServer().getScheduler().scheduleDelayedTask(new Task() {
            @Override
            public void onRun(int currentTick) {
                source.dataPacket(pk);
            }
        }, 10);

        this.source.resetCraftingGridType();
    }

    public boolean execute() {
        if (super.execute()) {
            if (Server.getInstance().achievementsEnabled) {
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
            }

            return true;
        }

        return false;
    }

    public boolean checkForCraftingPart(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction) {
                SlotChangeAction slotChangeAction = (SlotChangeAction) action;
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
