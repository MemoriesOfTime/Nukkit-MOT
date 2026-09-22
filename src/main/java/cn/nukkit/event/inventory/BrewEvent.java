package cn.nukkit.event.inventory;

import cn.nukkit.blockentity.BlockEntityBrewingStand;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.Item;

import java.util.Objects;

/**
 * @author CreeperFace
 */
public class BrewEvent extends InventoryEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final BlockEntityBrewingStand brewingStand;
    private final Item ingredient;
    private final Item[] potions;
    private final Item[] results;
    private final int fuel;

    public BrewEvent(BlockEntityBrewingStand blockEntity) {
        this(blockEntity, new Item[]{Item.AIR_ITEM, Item.AIR_ITEM, Item.AIR_ITEM});
    }

    public BrewEvent(BlockEntityBrewingStand blockEntity, Item[] results) {
        super(blockEntity.getInventory());
        if (results == null || results.length != 3) {
            throw new IllegalArgumentException("Brewing results must contain exactly three slots");
        }
        this.brewingStand = blockEntity;
        this.fuel = blockEntity.fuelAmount;

        this.ingredient = blockEntity.getInventory().getIngredient();

        this.potions = new Item[3];
        this.results = new Item[3];
        for (int i = 0; i < 3; i++) {
            this.potions[i] = blockEntity.getInventory().getItem(i + 1);
            this.results[i] = Objects.requireNonNull(results[i], "result").clone();
        }
    }

    public BlockEntityBrewingStand getBrewingStand() {
        return brewingStand;
    }

    public Item getIngredient() {
        return ingredient;
    }

    public Item[] getPotions() {
        return potions;
    }

    /**
     * @param index Potion index in range 0 - 2
     * @return potion
     */
    public Item getPotion(int index) {
        return this.potions[index];
    }

    /**
     * @param index Potion index in range 0 - 2
     * @return event-authoritative brewing result
     */
    public Item getResult(int index) {
        return this.results[index];
    }

    /**
     * Replaces the item which the brewing stand will put into the matching potion slot.
     *
     * @param index Potion index in range 0 - 2
     * @param result non-null result item
     */
    public void setResult(int index, Item result) {
        this.results[index] = Objects.requireNonNull(result, "result").clone();
    }

    public int getFuel() {
        return fuel;
    }
}
