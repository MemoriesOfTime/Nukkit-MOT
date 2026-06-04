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

    private static final int FIREWORKS_PER_BATCH = 3;
    private static final int MIN_GUNPOWDER_PER_BATCH = 1;
    private static final int MAX_GUNPOWDER_PER_BATCH = 3;
    private static final int CRAFTING_GRID_SIZE = 9;

    public FireworkRecipe(){
        super(UUID.fromString(TYPE_FIREWORKS));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        return outputItem.getId() == ItemID.FIREWORKS && collectIngredients(outputItem, inputs) != null;
    }

    @Override
    public Recipe toRecipe(Item outputItem, List<Item> inputs) {
        // 服务端根据输入材料构建正确的输出，不信任客户端 NBT
        FireworkIngredients ingredients = collectIngredients(outputItem, inputs);
        if (ingredients == null) {
            throw new IllegalArgumentException("Invalid firework recipe ingredients for output " + outputItem);
        }
        List<CompoundTag> explosions = new ArrayList<>();

        for (Item input : inputs) {
            if (input.getId() == ItemID.FIREWORKSCHARGE && input.hasCompoundTag()) {
                CompoundTag starTag = input.getNamedTag();
                CompoundTag fireworksItem = starTag.contains("FireworksItem")
                        ? starTag.getCompound("FireworksItem") : starTag;
                int explosionsPerBatch = input.getCount() / ingredients.batchCount();
                for (int i = 0; i < explosionsPerBatch; i++) {
                    CompoundTag explosion = new CompoundTag();
                    if (fireworksItem.exist("FireworkColor")) {
                        explosion.putByteArray("FireworkColor", fireworksItem.getByteArray("FireworkColor"));
                    }
                    if (fireworksItem.exist("FireworkFade")) {
                        explosion.putByteArray("FireworkFade", fireworksItem.getByteArray("FireworkFade"));
                    }
                    explosion.putBoolean("FireworkFlicker", fireworksItem.getBoolean("FireworkFlicker"));
                    explosion.putBoolean("FireworkTrail", fireworksItem.getBoolean("FireworkTrail"));
                    explosion.putByte("FireworkType", fireworksItem.getByte("FireworkType"));
                    explosions.add(explosion);
                }
            }
        }

        ItemFirework firework = new ItemFirework(0, outputItem.getCount());
        firework.setFlight(ingredients.flight());

        if (!explosions.isEmpty()) {
            CompoundTag tag = firework.getNamedTag();
            ListTag<CompoundTag> explosionList = tag.getCompound("Fireworks").getList("Explosions", CompoundTag.class);
            for (CompoundTag explosion : explosions) {
                explosionList.add(explosion);
            }
            firework.setNamedTag(tag);
        }

        return new ShapelessRecipe(firework, inputs);
    }

    private static FireworkIngredients collectIngredients(Item outputItem, List<Item> inputs) {
        if (outputItem.getCount() < FIREWORKS_PER_BATCH || outputItem.getCount() % FIREWORKS_PER_BATCH != 0) {
            return null;
        }

        int batchCount = outputItem.getCount() / FIREWORKS_PER_BATCH;
        long paper = 0;
        long gunpowder = 0;
        long stars = 0;

        for (Item input : inputs) {
            if (input.getCount() <= 0) {
                return null;
            }
            switch (input.getId()) {
                case ItemID.PAPER -> paper += input.getCount();
                case ItemID.GUNPOWDER -> gunpowder += input.getCount();
                case ItemID.FIREWORKSCHARGE -> stars += input.getCount();
                default -> {
                    return null;
                }
            }
        }

        if (paper != batchCount || gunpowder % batchCount != 0 || stars % batchCount != 0) {
            return null;
        }

        long flight = gunpowder / batchCount;
        if (flight < MIN_GUNPOWDER_PER_BATCH || flight > MAX_GUNPOWDER_PER_BATCH) {
            return null;
        }

        long starsPerBatch = stars / batchCount;
        if (1 + flight + starsPerBatch > CRAFTING_GRID_SIZE) {
            return null;
        }

        return new FireworkIngredients(batchCount, (int) flight);
    }

    private record FireworkIngredients(int batchCount, int flight) {
    }
}
