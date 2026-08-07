package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.StonecutterItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.StonecutterInventory;
import cn.nukkit.inventory.StonecutterRecipe;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.inventory.transaction.action.StonecutterItemAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class StonecutterTransaction extends InventoryTransaction {

    @Getter
    private Item inputItem;
    @Getter
    private Item outputItem;
    private final List<Item> outputItemCheck = new ArrayList<>();
    private StonecutterRecipe recipe;

    public StonecutterTransaction(Player source, List<InventoryAction> actions) {
        super(source, actions);

        for (InventoryAction action : actions) {
            if (action instanceof SlotChangeAction slotChangeAction) {
                if (!(slotChangeAction.getInventory() instanceof StonecutterInventory)) {
                    this.outputItemCheck.add(slotChangeAction.getTargetItemUnsafe());
                }
            }
        }
    }

    @Override
    public void addAction(InventoryAction action) {
        super.addAction(action);
        if (action instanceof StonecutterItemAction sta) {
            switch (sta.getType()) {
                case NetworkInventoryAction.SOURCE_TYPE_CRAFTING_USE_INGREDIENT:
                    if (action.getTargetItem().isNull()) {
                        // target=Air → 取出结果，source 是输出物品
                        this.outputItem = action.getSourceItem();
                    } else {
                        // target 非空 → 放入原料，target 是输入物品
                        this.inputItem = action.getTargetItem();
                    }
                    break;
                case NetworkInventoryAction.SOURCE_TYPE_CRAFTING_RESULT:
                    this.outputItem = action.getSourceItem();
                    break;
            }
        }
    }

    @Override
    public boolean canExecute() {
        // 不调用 super.canExecute()，避免 matchItems 的 setCount 副作用
        if (this.invalid || this.actions.isEmpty()) {
            return false;
        }

        for (InventoryAction action : this.actions) {
            if (!action.isValid(this.source)) {
                return false;
            }
        }

        Inventory inventory = getSource().getWindowById(Player.STONECUTTER_WINDOW_ID);
        if (!(inventory instanceof StonecutterInventory stonecutterInventory)) {
            return false;
        }

        if (this.outputItem == null || this.outputItem.isNull()) {
            return false;
        }

        // 重复合成时，虚拟合成格只有消耗动作(target=Air)，不会触发 inputItem 赋值
        // 此时从切石机库存中读取已有的输入物品
        if (this.inputItem == null || this.inputItem.isNull()) {
            Item existingInput = stonecutterInventory.getInput();
            if (existingInput == null || existingInput.isNull()) {
                return false;
            }
            this.inputItem = existingInput;
        }

        // 验证客户端声称的输出与库存中其他 action 的一致性
        for (Item check : this.outputItemCheck) {
            if (check != null && !this.outputItem.equals(check)) {
                return false;
            }
        }

        // 验证客户端声称的输入与库存中实际物品一致
        if (!inputItem.equals(stonecutterInventory.getInput(), true, true)) {
            return false;
        }

        // 验证库存中输入物品数量足够
        Item actualInput = stonecutterInventory.getInput();
        if (actualInput.isNull()) {
            return false;
        }

        this.recipe = Server.getInstance().getCraftingManager().matchStonecutterRecipe(this.inputItem, this.outputItem);
        if (this.recipe == null) {
            return false;
        }

        if (this.outputItem.getCount() != this.recipe.getResult().getCount()) {
            return false;
        }

        if (actualInput.getCount() < this.recipe.getIngredient().getCount()) {
            return false;
        }

        return true;
    }

    @Override
    public boolean execute() {
        if (this.invalid || this.hasExecuted() || !this.canExecute()) {
            this.source.removeAllWindows(false);
            this.sendInventories();
            return false;
        }

        StonecutterInventory inventory = (StonecutterInventory) getSource().getWindowById(Player.STONECUTTER_WINDOW_ID);
        StonecutterItemEvent event = new StonecutterItemEvent(inventory, this.inputItem, this.outputItem, this.source);
        this.source.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.sendInventories();
            source.setNeedSendInventory(true);
            return true;
        }

        // 服务端主动扣除输入物品，不依赖客户端发送的 SlotChangeAction
        Item currentInput = inventory.getInput();
        int consumeCount = this.recipe.getIngredient().getCount();
        if (currentInput.isNull() || currentInput.getCount() < consumeCount) {
            this.sendInventories();
            return false;
        }
        currentInput.setCount(currentInput.getCount() - consumeCount);
        inventory.setItem(0, currentInput.getCount() > 0 ? currentInput : Item.get(Item.AIR));

        // 使用 addItem 放置产出物品，自动合并到已有堆叠
        PlayerInventory playerInventory = source.getInventory();
        Item[] remaining = playerInventory.addItem(this.outputItem.clone());
        for (Item drop : remaining) {
            if (!source.dropItem(drop)) {
                inventory.getHolder().getLevel().dropItem(inventory.getHolder().add(0.5, 0.5, 0.5), drop);
            }
        }

        // 同步完整背包给客户端，纠正客户端预测与服务器实际状态的差异
        playerInventory.sendContents(source);
        return true;
    }

    public static boolean isIn(List<InventoryAction> actions) {
        for (InventoryAction action : actions) {
            if (action instanceof StonecutterItemAction) return true;
        }
        return false;
    }

    @Override
    public boolean checkForItemPart(List<InventoryAction> actions) {
        return isIn(actions);
    }
}
