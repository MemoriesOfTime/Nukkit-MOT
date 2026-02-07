package cn.nukkit.utils;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.recipe.RecipeType;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;

import java.util.*;

public class RecipeUtils {
    public static String getItemHash(Item item) {
        return getItemHash(item, item.getDamage());
    }

    public static String getItemHash(Item item, int meta) {
        String hash = "" + (item.getId() == Item.STRING_IDENTIFIED_ITEM ? item.getNamespaceId() : item.getId());
        return hash + meta;
    }

    public static int getPotionHash(Item ingredient, Item potion) {
        int ingredientHash = ((ingredient.getId() & 0x3FF) << 6) | (ingredient.getDamage() & 0x3F);
        int potionHash = ((potion.getId() & 0x3FF) << 6) | (potion.getDamage() & 0x3F);
        return ingredientHash << 16 | potionHash;
    }

    public static String computeBrewingRecipeId(Item input, Item ingredient, Item output) {
        return computeRecipeIdWithItem(List.of(output), List.of(input, ingredient), RecipeType.BREWING);
    }

    public static String computeContainerRecipeId(Item input, Item ingredient, Item output) {
        return computeRecipeIdWithItem(List.of(output), List.of(input, ingredient), RecipeType.CONTAINER);
    }

    private static String computeRecipeIdWithItem(Collection<Item> results, Collection<Item> inputs, RecipeType type) {
        List<Item> inputs1 = new ArrayList<>(inputs);
        return computeRecipeId(results, inputs1.stream().map(DefaultDescriptor::new).toList(), type);
    }

    private static String computeRecipeId(Collection<Item> results, Collection<? extends ItemDescriptor> inputs, RecipeType type) {
        StringBuilder builder = new StringBuilder();
        Optional<Item> first = results.stream().findFirst();
        first.ifPresent(item -> builder.append(new Identifier(item.getNamespaceId()).getPath())
                .append('_')
                .append(item.getCount())
                .append('_')
                .append(item.getDamage())
                .append("_from_"));
        int limit = 5;
        for (var des : inputs) {
            if ((limit--) == 0) {
                break;
            }
            if (des instanceof DefaultDescriptor def) {
                Item item = def.getItem();
                builder.append(new Identifier(item.getNamespaceId()).getPath())
                        .append('_')
                        .append(item.getCount())
                        .append('_')
                        .append(item.getDamage())
                        .append("_and_");
            }
        }
        String r = builder.toString();
        return r.substring(0, r.lastIndexOf("_and_")) + "_" + type.name().toLowerCase(Locale.ENGLISH);
    }
}

