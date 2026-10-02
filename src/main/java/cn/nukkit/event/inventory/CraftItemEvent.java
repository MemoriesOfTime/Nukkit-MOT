package cn.nukkit.event.inventory;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;
import cn.nukkit.event.HandlerList;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.item.Item;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class CraftItemEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private Item[] input;

    private final Recipe recipe;

    private final Player player;

    private CraftingTransaction transaction;

    private final int repetitions;

    public CraftItemEvent(CraftingTransaction transaction) {
        this(transaction, 1);
    }

    public CraftItemEvent(CraftingTransaction transaction, int repetitions) {
        this.transaction = transaction;
        this.player = transaction.getSource();
        this.input = transaction.getInputList().toArray(Item.EMPTY_ARRAY);
        // 取宽类型 transactionRecipe，使 MultiRecipe 等非 CraftingRecipe 能透传到事件
        // Use the wide-typed transactionRecipe so non-CraftingRecipe types (e.g. MultiRecipe) survive
        this.recipe = transaction.getTransactionRecipe();
        this.repetitions = Math.max(1, repetitions);
    }

    public CraftItemEvent(Player player, Item[] input, Recipe recipe) {
        this.player = player;
        this.input = input;
        this.recipe = recipe;
        this.repetitions = 1;
    }

    public CraftingTransaction getTransaction() {
        return transaction;
    }

    public Item[] getInput() {
        return input;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public Player getPlayer() {
        return this.player;
    }

    /** Number of recipe executions represented by this event, not the output stack size. */
    public int getRepetitions() {
        return repetitions;
    }
}
