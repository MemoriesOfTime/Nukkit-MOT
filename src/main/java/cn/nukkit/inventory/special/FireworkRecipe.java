package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.inventory.ShapelessRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.item.ItemID;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FireworkRecipe extends MultiRecipe {

    private static final int FIREWORKS_PER_CRAFT = 3;
    private static final int MIN_GUNPOWDER = 1;
    private static final int MAX_GUNPOWDER = 3;
    private static final int MAX_STARS = 3;
    private static final int SMALL_CRAFTING_GRID_SIZE = 4;
    private static final int BIG_CRAFTING_GRID_SIZE = 9;

    public FireworkRecipe(){
        super(UUID.fromString(TYPE_FIREWORKS));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getId() != ItemID.FIREWORKS) {
            return false;
        }

        int gridSize = player != null && player.craftingType == Player.CRAFTING_SMALL
                ? SMALL_CRAFTING_GRID_SIZE : BIG_CRAFTING_GRID_SIZE;
        return collectIngredients(inputs, gridSize) != null;
    }

    @Override
    public Recipe toRecipe(Item outputItem, List<Item> inputs) {
        FireworkIngredients ingredients = collectIngredients(inputs, BIG_CRAFTING_GRID_SIZE);
        if (ingredients == null) {
            throw new IllegalArgumentException("Invalid firework recipe ingredients for output " + outputItem);
        }
        return new ShapelessRecipe(createResult(ingredients), inputs);
    }

    private static ItemFirework createResult(FireworkIngredients ingredients) {
        ItemFirework firework = new ItemFirework(0, FIREWORKS_PER_CRAFT);
        firework.setFlight(ingredients.flight());
        CompoundTag tag = firework.getNamedTag();
        ListTag<CompoundTag> explosionList = tag.getCompound("Fireworks").getList("Explosions", CompoundTag.class);

        for (Item star : ingredients.stars()) {
            CompoundTag explosion = getExplosionComponent(star);
            if (explosion != null) {
                explosionList.add(explosion);
            }
        }

        if (!ingredients.stars().isEmpty()) {
            firework.setNamedTag(tag);
        }

        return firework;
    }

    private static CompoundTag getExplosionComponent(Item star) {
        if (!star.hasCompoundTag()) {
            return null;
        }

        CompoundTag starTag = star.getNamedTag();
        if (starTag.contains("FireworksItem")) {
            return buildExplosion(starTag.getCompound("FireworksItem"));
        }

        return hasExplosionData(starTag) ? buildExplosion(starTag) : null;
    }

    private static boolean hasExplosionData(CompoundTag tag) {
        return tag.exist("FireworkColor")
                || tag.exist("FireworkFade")
                || tag.exist("FireworkFlicker")
                || tag.exist("FireworkTrail")
                || tag.exist("FireworkType");
    }

    private static CompoundTag buildExplosion(CompoundTag source) {
        CompoundTag explosion = new CompoundTag();
        if (source.exist("FireworkColor")) {
            explosion.putByteArray("FireworkColor", source.getByteArray("FireworkColor"));
        }
        if (source.exist("FireworkFade")) {
            explosion.putByteArray("FireworkFade", source.getByteArray("FireworkFade"));
        }
        explosion.putBoolean("FireworkFlicker", source.getBoolean("FireworkFlicker"));
        explosion.putBoolean("FireworkTrail", source.getBoolean("FireworkTrail"));
        explosion.putByte("FireworkType", source.getByte("FireworkType"));
        return explosion;
    }

    /**
     * Validates inputs by entry (slot) count, ignoring stack size. Works for both
     * legacy grid slots (creative stacks of 64) and SAI consumed-amount entries.
     */
    private static FireworkIngredients collectIngredients(List<Item> inputs, int craftingGridSize) {
        int paper = 0;
        int gunpowder = 0;
        List<Item> stars = new ArrayList<>();

        for (Item input : inputs) {
            switch (input.getId()) {
                case ItemID.PAPER -> paper++;
                case ItemID.GUNPOWDER -> gunpowder++;
                case ItemID.FIREWORKSCHARGE -> stars.add(input);
                default -> {
                    return null;
                }
            }
        }

        if (paper != 1) {
            return null;
        }
        if (gunpowder < MIN_GUNPOWDER || gunpowder > MAX_GUNPOWDER) {
            return null;
        }
        if (stars.size() > MAX_STARS) {
            return null;
        }
        if (1 + gunpowder + stars.size() > craftingGridSize) {
            return null;
        }

        return new FireworkIngredients(gunpowder, stars);
    }

    private record FireworkIngredients(int flight, List<Item> stars) {
    }
}
