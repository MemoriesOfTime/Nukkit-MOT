package cn.nukkit.recipe.parser;

import cn.nukkit.Server;
import cn.nukkit.item.Item;
import cn.nukkit.item.material.tags.ItemTags;
import cn.nukkit.recipe.RecipeRegistry;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.descriptor.ItemTagDescriptor;
import cn.nukkit.recipe.impl.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RecipeParser {
    private static DefaultDescriptor parseItem(JsonObject item) {
        if(item.has("type") && item.get("type").getAsString().equals("complex_alias")) {
            throw new ComplexAliasException();
        }

        final Item result;
        if(item.has("id")) {
            result = Item.fromString(item.get("id").getAsString()).clone();
        } else {
            result = Item.fromString(item.get("itemId").getAsString()).clone();
        }

        if(item.has("count")) {
            result.setCount(item.get("count").getAsInt());
        }

        int damage = result.getDamage();
        if(item.has("damage")) {
            damage = item.get("damage").getAsInt();
        }

        if(item.has("auxValue")) {
            damage = item.get("auxValue").getAsInt();
        }

        if(damage != 32767 && result.getDamage() == 0) result.setDamage(damage);

        return new DefaultDescriptor(result);
    }

    private static ItemDescriptor parseInput(JsonObject input) {
        if(input.has("itemTag")) {
            String id = input.get("itemTag").getAsString();
            return new ItemTagDescriptor(ItemTags.getTag(id), id);
        }
        return parseItem(input);
    }

    private static DefaultDescriptor parseOutput(JsonElement output, List<DefaultDescriptor> extra) {
        DefaultDescriptor result;
        if (output.isJsonArray()) {
            JsonArray array = output.getAsJsonArray();
            if (array.isEmpty()) {
                throw new RuntimeException("Output is empty");
            }

            for(int i = 1; i < array.size(); ++i) {
                extra.add(parseItem(array.get(i).getAsJsonObject()));
            }

            result = parseItem(array.get(0).getAsJsonObject());
        } else {
            result = parseItem(output.getAsJsonObject());
        }
        return result;
    }

    public static void loadRecipes(JsonArray recipes) {
        JsonObject furnaceXp = JsonParser.parseReader(new InputStreamReader(Server.class.getClassLoader().getResourceAsStream("recipes/furnace_xp.json"))).getAsJsonObject();

        recipes.forEach(json -> {
            final JsonObject recipe = json.getAsJsonObject();

            try {
                final int type = recipe.get("type").getAsInt();

                switch (type) {
                    case 4, 9 -> {}

                    case 3 -> {
                        final String block = recipe.get("block").getAsString();
                        final Item input = parseItem(recipe.get("input").getAsJsonObject()).getItem();
                        double xp = 0;
                        if(furnaceXp.has(input.getNamespaceId() + ":" + input.getDamage())) {
                            xp = furnaceXp.get(input.getNamespaceId() + ":" + input.getDamage()).getAsDouble();
                        }
                        switch (block) {
                            case "furnace", "deprecated" -> {
                                RecipeRegistry.registerFurnaceRecipe(new FurnaceRecipe(
                                        parseItem(recipe.get("output").getAsJsonObject()).getItem(),
                                        input
                                ), xp);
                            }

                            case "blast_furnace" -> {
                                RecipeRegistry.registerBlastFurnaceRecipe(new BlastFurnaceRecipe(
                                        parseItem(recipe.get("output").getAsJsonObject()).getItem(),
                                        input
                                ), xp);
                            }

                            case "campfire" -> {
                                RecipeRegistry.registerCampfireRecipe(new CampfireRecipe(
                                        parseItem(recipe.get("output").getAsJsonObject()).getItem(),
                                        input
                                ), xp);
                            }

                            case "stonecutter", "smoker", "soul_campfire" -> {
                            }

                            default -> log.warn("Not support block type: {}", block);
                        }

                    }

                    case 1 -> {
                        final String block = recipe.get("block").getAsString();

                        switch (block) {
                            case "crafting_table", "deprecated" -> {
                                final String[] shape = new String[recipe.get("height").getAsInt()];
                                final JsonArray shapeJson = recipe.get("shape").getAsJsonArray();
                                for(int i = 0; i < shape.length; i++) {
                                    shape[i] = shapeJson.get(i).getAsString();
                                }

                                final Map<Character, ItemDescriptor> items = new HashMap<>();
                                final JsonObject input =  recipe.get("input").getAsJsonObject();

                                input.entrySet().forEach(entry -> {
                                   items.put(entry.getKey().charAt(0), parseInput(entry.getValue().getAsJsonObject()));
                                });

                                final List<DefaultDescriptor> extra = new ArrayList<>();

                                RecipeRegistry.registerShapedRecipe(new ShapedRecipe(
                                        recipe.get("id").getAsString(),
                                        recipe.get("priority").getAsInt(),
                                        parseOutput(recipe.get("output"), extra).getItem(),
                                        shape,
                                        items,
                                        extra.stream()
                                                .map(DefaultDescriptor::getItem)
                                                .collect(Collectors.toList())
                                ));
                            }
                        }
                    }

                    case 8 -> {
                        final String block = recipe.get("block").getAsString();
                        switch (block) {
                            case "smithing_table" -> {
                                RecipeRegistry.registerSmithingRecipe(new SmithingRecipe(
                                        recipe.get("id").getAsString(),
                                        0,
                                        List.of(
                                                parseItem(recipe.get("base").getAsJsonObject()),
                                                parseItem(recipe.get("addition").getAsJsonObject()),
                                                parseItem(recipe.get("template").getAsJsonObject())
                                        ),
                                        parseItem(recipe.get("result").getAsJsonObject()).getItem()
                                ));
                            }
                            default -> log.warn("Not support block type: {}", block);
                        }
                    }

                    case 0, 5 -> {
                        final String block = recipe.get("block").getAsString();

                        switch (block) {
                            case "crafting_table", "deprecated" -> {
                                final Collection<ItemDescriptor> inputs = new ArrayList<>();

                                recipe.getAsJsonArray("input").getAsJsonArray().forEach(item -> {
                                    inputs.add(parseInput(item.getAsJsonObject()));
                                });

                                RecipeRegistry.registerShapelessRecipe(new ShapelessRecipe(
                                        recipe.get("id").getAsString(),
                                        recipe.get("priority").getAsInt(),
                                        parseOutput(recipe.get("output"), List.of()).getItem(),
                                        inputs
                                ));
                            }

                            case "stonecutter", "cartography_table" -> {
                            }

                            default -> log.warn("Not support block type: {}", block);
                        }
                    }

                    default -> log.warn("Unknown recipe type: {}", type);
                }
            } catch (Exception e) {
                if(!(e instanceof ComplexAliasException)) {
                    if(recipe.has("id")) {
                        log.error("Failed to load recipe {}, exception {}", recipe.get("id").toString(), e);
                    } else {
                        log.error("Failed to load recipe {}, exception {}", "hz", e);
                    }
                }
            }
        });
    }
}
