package cn.nukkit.recipe;


import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.inventory.SmithingInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.CraftingDataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.*;
import cn.nukkit.recipe.impl.special.*;
import cn.nukkit.recipe.parser.RecipeParser;
import cn.nukkit.utils.RecipeUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import org.apache.logging.log4j.core.net.Protocol;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;

@Getter
public class RecipeRegistry {
    public static int NEXT_NETWORK_ID = 1;

    private static BatchPacket PACKET;

    private static final Map<String, List<ShapedRecipe>> SHAPED = new HashMap<>();
    private static final Map<String, List<ShapelessRecipe>> SHAPELESS = new HashMap<>();
    private static final Map<String, FurnaceRecipe> FURNACE = new HashMap<>();
    private static final Map<String, BlastFurnaceRecipe> BLAST_FURNACE = new HashMap<>();

    private static final Map<UUID, MultiRecipe> MULTI = new HashMap<>();
    private static final Map<String, BrewingRecipe> BREWING = new HashMap<>();
    private static final Map<String, ContainerRecipe> CONTAINER = new HashMap<>();
    private static final Map<String, CampfireRecipe> CAMPFIRE = new HashMap<>();
    private static final Map<UUID, SmithingRecipe> SMITHING = new Object2ObjectOpenHashMap<>();
    private static final Object2DoubleOpenHashMap<Recipe> FURNACE_XP = new Object2DoubleOpenHashMap<>();
    private static final Map<String, StonecutterRecipe> STONECUTTER = new HashMap<>();
    private static final Collection<Recipe> RECIPES = new ArrayDeque<>();

    private static final AtomicBoolean isLoad = new AtomicBoolean(false);

    public static void init() {
        if (isLoad.getAndSet(true)) return;
        registerMultiRecipe(new RepairItemRecipe());
        registerMultiRecipe(new BookCloningRecipe());
        registerMultiRecipe(new MapCloningRecipe());
        registerMultiRecipe(new MapUpgradingRecipe());
        registerMultiRecipe(new MapExtendingRecipe());
        registerMultiRecipe(new BannerAddPatternRecipe());
        registerMultiRecipe(new BannerDuplicateRecipe());
        registerMultiRecipe(new FireworkRecipe());
        registerMultiRecipe(new DecoratedPotRecipe());

        final JsonArray potionMixes = JsonParser.parseReader(new InputStreamReader(Server.class.getClassLoader().getResourceAsStream("recipes/brewing_recipes.json"))).getAsJsonObject().get("potionMixes").getAsJsonArray();
        potionMixes.forEach((potionMix) -> {
            final JsonObject recipe = potionMix.getAsJsonObject();

            int fromPotionId = recipe.get("inputId").getAsInt();
            int fromPotionMeta = recipe.get("inputMeta").getAsInt();
            int ingredient = recipe.get("reagentId").getAsInt();
            int ingredientMeta = recipe.get("reagentMeta").getAsInt();
            int toPotionId = recipe.get("outputId").getAsInt();
            int toPotionMeta = recipe.get("outputMeta").getAsInt();

            registerBrewingRecipe(new BrewingRecipe(Item.get(fromPotionId, fromPotionMeta), Item.get(ingredient, ingredientMeta), Item.get(toPotionId, toPotionMeta)));
        });

        RecipeParser.loadRecipes(JsonParser.parseReader(new InputStreamReader(Server.class.getClassLoader().getResourceAsStream("recipes/recipes.json"))).getAsJsonObject().get("recipes").getAsJsonArray());
    }

    public static void reload() {
        isLoad.set(false);
        SHAPED.clear();
        SHAPELESS.clear();
        FURNACE.clear();
        BLAST_FURNACE.clear();
        MULTI.clear();
        BREWING.clear();
        CONTAINER.clear();
        CAMPFIRE.clear();
        SMITHING.clear();
        FURNACE_XP.clear();
        RECIPES.clear();
        init();
        buildPackets();
    }

    public static void registerFurnaceRecipe(FurnaceRecipe recipe, double xp) {
        FURNACE.put(RecipeUtils.getItemHash(recipe.getInput()), recipe);
        FURNACE_XP.put(recipe, xp);
    }

    public static void registerBlastFurnaceRecipe(BlastFurnaceRecipe recipe, double xp) {
        BLAST_FURNACE.put(RecipeUtils.getItemHash(recipe.getInput()), recipe);
        FURNACE_XP.put(recipe, xp);
    }

    public static void registerShapedRecipe(ShapedRecipe recipe) {
        String resultHash = RecipeUtils.getItemHash(recipe.getResult());
        SHAPED.computeIfAbsent(resultHash, (key) -> new ArrayList<>()).add(recipe);
        RECIPES.add(recipe);
    }

    public static void registerShapelessRecipe(ShapelessRecipe recipe) {
        String resultHash = RecipeUtils.getItemHash(recipe.getResult());
        SHAPELESS.computeIfAbsent(resultHash, (key) -> new ArrayList<>()).add(recipe);
        RECIPES.add(recipe);
    }

    public static void registerSmithingRecipe(SmithingRecipe recipe) {
        SMITHING.put(UUID.randomUUID(), recipe);
    }

    public static void registerBrewingRecipe(BrewingRecipe recipe) {
        Item input = recipe.getInput();
        Item ingredient = recipe.getIngredient();
        Item output = recipe.getResult();

        String recipeId = RecipeUtils.computeBrewingRecipeId(input, ingredient, output);
        BREWING.put(recipeId, recipe);
    }

    public static void registerContainerRecipe(ContainerRecipe recipe) {
        Item input = recipe.getInput();
        Item ingredient = recipe.getIngredient();
        Item output = recipe.getResult();

        String recipeId = RecipeUtils.computeContainerRecipeId(input, ingredient, output);
        CONTAINER.put(recipeId, recipe);
    }

    public static void registerCampfireRecipe(CampfireRecipe recipe, double xp) {
        Item input = recipe.getInput();
        CAMPFIRE.put(RecipeUtils.getItemHash(input), recipe);
        FURNACE_XP.put(recipe, xp);
    }

    public static void registerMultiRecipe(MultiRecipe recipe) {
        MULTI.put(recipe.getId(), recipe);
        RECIPES.add(recipe);
    }

    public static void addStonecutterRecipe(StonecutterRecipe recipe) {
        registerShapelessRecipe(recipe);

        for(ItemDescriptor descriptor : recipe.getIngredientList()) {
            if(descriptor instanceof DefaultDescriptor defaultDescriptor) {
                STONECUTTER.computeIfAbsent(RecipeUtils.getItemHash(defaultDescriptor.getItem()), (key) -> new StonecutterRecipe(recipe.getResult(), new ArrayList<>(recipe.getIngredientList()))).addResult(new DefaultDescriptor(recipe.getResult()));
            }
        }
    }

    public static StonecutterRecipe matchStoneCutterRecipe(Item input, List<Item> outputs) {
        StonecutterRecipe recipe = STONECUTTER.get(RecipeUtils.getItemHash(input));

        if(recipe != null) {
            boolean found = false;

            for(Item item : outputs) {
                for(ItemDescriptor descriptor : recipe.getIngredientList()) {
                    if(descriptor instanceof DefaultDescriptor defaultDescriptor && defaultDescriptor.getItem().getNamespaceId().equals(item.getNamespaceId())) {
                        found = true;
                        break;
                    }
                }
            }

            if(!found) {
                return null;
            }
        }

        return recipe;
    }

    public static FurnaceRecipe matchBlastFurnaceRecipe(Item input) {
        BlastFurnaceRecipe recipe = BLAST_FURNACE.get(RecipeUtils.getItemHash(input));
        if (recipe == null) recipe = BLAST_FURNACE.get(RecipeUtils.getItemHash(input, 0));
        return recipe;
    }

    public static FurnaceRecipe matchFurnaceRecipe(Item input) {
        FurnaceRecipe recipe = FURNACE.get(RecipeUtils.getItemHash(input));
        if (recipe == null) recipe = FURNACE.get(RecipeUtils.getItemHash(input, 0));
        return recipe;
    }

    public static CraftingRecipe matchRecipe(List<Item> inputList, Item primaryOutput, List<Item> extraOutputList) {
        String outputHash = RecipeUtils.getItemHash(primaryOutput);
        if (SHAPED.containsKey(outputHash)) {
            List<ShapedRecipe> recipes = SHAPED.get(outputHash);

            if (recipes != null) {
                for (ShapedRecipe shapedRecipe : recipes) {
                    if (shapedRecipe.matchItems(inputList, extraOutputList)) {
                        return shapedRecipe;
                    }
                }
            }
        }

        if (SHAPELESS.containsKey(outputHash)) {
            List<ShapelessRecipe> recipes = SHAPELESS.get(outputHash);

            if (recipes != null) {
                for (ShapelessRecipe shapelessRecipe : recipes) {
                    if (shapelessRecipe.matchItems(inputList, extraOutputList)) {
                        return shapelessRecipe;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    public static SmithingRecipe matchSmithingRecipe(List<Item> inputList) {
        ArrayList<Item> list = new ArrayList<>();
        for (Item item : inputList) {
            Item clone = item.clone();
            clone.setCount(1);
            if ((item.isTool() || item.isArmor()) && item.getDamage() > 0) {
                clone.setDamage(0);
            }
            list.add(clone);
        }

        for (SmithingRecipe smithingRecipe : SMITHING.values()) {
            if (smithingRecipe.matchItems(list)) {
                return smithingRecipe;
            }
        }

        return null;
    }

    public static BrewingRecipe matchBrewingRecipe(Item input, Item potion) {
        for(BrewingRecipe recipe : BREWING.values()) {
            if (recipe.fastCheck(input, potion)) return recipe;
        }
        return null;
    }

    public static CampfireRecipe matchCampfireRecipe(Item input) {
        CampfireRecipe recipe = CAMPFIRE.get(RecipeUtils.getItemHash(input));
        if (recipe == null) recipe = CAMPFIRE.get(RecipeUtils.getItemHash(input, 0));
        return recipe;
    }

    public static ContainerRecipe matchContainerRecipe(Item input, Item potion) {
        for(ContainerRecipe recipe : CONTAINER.values()) {
            if (recipe.fastCheck(input, potion)) return recipe;
        }
        return null;
    }

    public static MultiRecipe getMultiRecipe(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        if (outputItem == null) return null;
        return MULTI.values().stream().filter(multiRecipe -> multiRecipe.canExecute(player, outputItem, inputs)).findFirst().orElse(null);
    }

    public static double getRecipeXp(Recipe recipe) {
        return FURNACE_XP.getOrDefault(recipe, 0.0);
    }

    public static void setRecipeXp(Recipe recipe, double xp) {
        FURNACE_XP.put(recipe, xp);
    }

    public static BatchPacket getPacket(int protocol) {
        return PACKET;
    }

    @ApiStatus.Internal
    public static void buildPackets() {
        CraftingDataPacket pk = new CraftingDataPacket();
        pk.protocol = ProtocolInfo.v1_16_100;

        for (FurnaceRecipe recipe : FURNACE.values()) {
            pk.addFurnaceRecipe(recipe);
        }

        /*for (MultiRecipe recipe : MULTI.values()) {
            pk.addMultiRecipe(recipe);
        }*/

        for (Recipe recipe : RECIPES) {
            if (recipe instanceof ShapedRecipe) {
                pk.addShapedRecipe((ShapedRecipe) recipe);
            } else if (recipe instanceof ShapelessRecipe) {
                pk.addShapelessRecipe((ShapelessRecipe) recipe);
            }
        }

        for (SmithingRecipe recipe : SMITHING.values()) {
            pk.addShapelessRecipe(recipe);
        }

        for (BrewingRecipe recipe : BREWING.values()) {
            pk.addBrewingRecipe(recipe);
        }

        for (ContainerRecipe recipe : CONTAINER.values()) {
            pk.addContainerRecipe(recipe);
        }

        pk.tryEncode();

        PACKET = pk.compress(Deflater.BEST_COMPRESSION);
    }
}
