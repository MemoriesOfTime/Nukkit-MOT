package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.inventory.special.RepairItemRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.CraftingDataPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.*;
import io.netty.util.collection.CharObjectHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nullable;
import java.util.*;
import java.util.zip.Deflater;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class CraftingManager {

    private final Collection<Recipe> recipes313 = new ArrayDeque<>();
    private final Collection<Recipe> recipes332 = new ArrayDeque<>();
    private final Collection<Recipe> recipes354 = new ArrayDeque<>();
    private final Collection<Recipe> recipes419 = new ArrayDeque<>();
    public final Collection<Recipe> recipes = new ArrayDeque<>(); //527

    public static BatchPacket packet313;
    public static BatchPacket packet340;
    public static BatchPacket packet361;
    public static BatchPacket packet354;
    public static BatchPacket packet388;
    public static DataPacket packet407;
    public static DataPacket packet419;
    public static DataPacket packet431;
    public static DataPacket packet440;
    public static DataPacket packet448;
    public static DataPacket packet465;
    public static DataPacket packet471;
    public static DataPacket packet486;
    public static DataPacket packet503;
    public static DataPacket packet527;
    public static DataPacket packet544;
    public static DataPacket packet554;
    public static DataPacket packet560;
    public static DataPacket packet567;
    public static DataPacket packet575;
    public static DataPacket packet582;
    public static DataPacket packet589;
    public static DataPacket packet594;
    public static DataPacket packet618;
    public static DataPacket packet622;
    public static DataPacket packet630;

    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes313 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes332 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes388 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes419 = new Int2ObjectOpenHashMap<>();
    protected final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes = new Int2ObjectOpenHashMap<>(); //527

    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes313 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes332 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes388 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes419 = new Int2ObjectOpenHashMap<>();
    protected final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes = new Int2ObjectOpenHashMap<>(); //527

    public final Map<UUID, MultiRecipe> multiRecipes = new HashMap<>();
    public final Map<Integer, FurnaceRecipe> furnaceRecipes = new Int2ObjectOpenHashMap<>(); //440
    public final Map<Integer, FurnaceRecipe> furnaceRecipes340 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, FurnaceRecipe> furnaceRecipesOld = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, BrewingRecipe> brewingRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, BrewingRecipe> brewingRecipesOld = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, ContainerRecipe> containerRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, ContainerRecipe> containerRecipesOld = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, CampfireRecipe> campfireRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<UUID, SmithingRecipe> smithingRecipes = new Object2ObjectOpenHashMap<>(); //589

    private final Object2DoubleOpenHashMap<Recipe> recipeXpMap = new Object2DoubleOpenHashMap<>();

    private static int RECIPE_COUNT = 0;
    static int NEXT_NETWORK_ID = 1;

    public static final Comparator<Item> recipeComparator = (i1, i2) -> {
        if (i1.getId() > i2.getId()) {
            return 1;
        } else if (i1.getId() < i2.getId()) {
            return -1;
        } else {
            if (!i1.isNull() && !i2.isNull()) {
                int i = MinecraftNamespaceComparator.compareFNV(i1.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL), i2.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL));
                if (i != 0) {
                    return i;
                }
            }
            if (i1.getDamage() > i2.getDamage()) {
                return 1;
            } else if (i1.getDamage() < i2.getDamage()) {
                return -1;
            } else return Integer.compare(i1.getCount(), i2.getCount());
        }
    };

    @SuppressWarnings("unchecked")
    public CraftingManager() {
        MainLogger.getLogger().debug("Loading recipes...");

        // Register multi-recipes internally
        // todo:
        //  Currently, we can take the original written book out from the crafting slot,
        //  but book cloning recipe requires a further fix
        //  because original written books will soon vanish due to a mysterious SlotChangeAction
        // this.registerMultiRecipe(new BookCloningRecipe());
        this.registerMultiRecipe(new RepairItemRecipe());

        ConfigSection recipes_419_config = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes419.json")).getRootSection();
        List<Map> recipes_388 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes388.json")).getRootSection().getMapList("recipes");
        List<Map> recipes_332 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes332.json")).getMapList("recipes");
        List<Map> recipes_313 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes313.json")).getMapList("recipes");

        ConfigSection recipes_smithing_config = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes_smithing.json")).getRootSection();
        ConfigSection recipes_extras_440 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes/recipes_extras_440.json")).getRootSection();
        Config furnaceXpConfig = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes/furnace_xp.json"));

        for (ShapedRecipe recipe : loadShapedRecipes((List<Map<String, Object>>) recipes_419_config.get((Object)"shaped"))) {
            this.registerRecipe(419, recipe);
            this.registerRecipe(527, recipe);
        }

        for (ShapelessRecipe recipe : loadShapelessRecips((List<Map<String, Object>>) recipes_419_config.get((Object)"shapeless"))) {
            this.registerRecipe(419, recipe);
            this.registerRecipe(527, recipe);
        }

        // Smithing recipes 锻造配方
        for (Map<String, Object> recipe : (List<Map<String, Object>>)recipes_smithing_config.get((Object)"smithing")) {
            List<Map> outputs = ((List<Map>) recipe.get("output"));
            if (outputs.size() > 1) {
                continue;
            }

            String recipeId = (String) recipe.get("id");
            int priority = Math.max(Utils.toInt(recipe.get("priority")) - 1, 0);

            Map<String, Object> first = outputs.get(0);
            Item item = Item.fromJson(first);

            List<Item> sorted = new ArrayList<>();
            for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                sorted.add(Item.fromJson(ingredient));
            }

            this.registerRecipe(589, new SmithingRecipe(recipeId, priority, sorted, item));
        }

        for (SmeltingRecipe recipe : loadSmeltingRecipes((List<Map<String, Object>>) recipes_419_config.get((Object)"smelting"), furnaceXpConfig)) {
            this.registerRecipe(419, recipe);
            this.registerRecipe(440, recipe);
        }

        for (SmeltingRecipe recipe : loadSmeltingRecipes((List<Map<String, Object>>) recipes_extras_440.get((Object)"smelting"), furnaceXpConfig)) {
            this.registerRecipe(440, recipe);
        }

        for (Map<String, Object> recipe : recipes_388) {
            try {
                switch (Utils.toInt(recipe.get("type"))) {
                    case 0:
                        String craftingBlock = (String) recipe.get("block");
                        if (!"crafting_table".equals(craftingBlock)) {
                            // Ignore other recipes than crafting table ones
                            continue;
                        }
                        List<Map> outputs = ((List<Map>) recipe.get("output"));
                        if (outputs.size() > 1) {
                            continue;
                        }
                        Map<String, Object> first = outputs.get(0);
                        List<Item> sorted = new ArrayList<>();
                        for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                            sorted.add(Item.fromJson(ingredient));
                        }
                        sorted.sort(recipeComparator);

                        String recipeId = (String) recipe.get("id");
                        int priority = Utils.toInt(recipe.get("priority"));

                        this.registerRecipe(388, new ShapelessRecipe(recipeId, priority, Item.fromJson(first), sorted));
                        break;
                    case 1:
                        craftingBlock = (String) recipe.get("block");
                        if (!"crafting_table".equals(craftingBlock)) {
                            // Ignore other recipes than crafting table ones
                            continue;
                        }
                        outputs = (List<Map>) recipe.get("output");

                        first = outputs.remove(0);
                        String[] shape = ((List<String>) recipe.get("shape")).toArray(new String[0]);
                        Map<Character, Item> ingredients = new CharObjectHashMap<>();
                        List<Item> extraResults = new ArrayList<>();

                        Map<String, Map<String, Object>> input = (Map) recipe.get("input");
                        for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                            char ingredientChar = ingredientEntry.getKey().charAt(0);
                            Item ingredient = Item.fromJson(ingredientEntry.getValue());

                            ingredients.put(ingredientChar, ingredient);
                        }

                        for (Map<String, Object> data : outputs) {
                            extraResults.add(Item.fromJson(data));
                        }

                        recipeId = (String) recipe.get("id");
                        priority = Utils.toInt(recipe.get("priority"));

                        this.registerRecipe(388, new ShapedRecipe(recipeId, priority, Item.fromJson(first), shape, ingredients, extraResults));
                        break;
                    case 2:
                    case 3:
                        craftingBlock = (String) recipe.get("block");
                        if (!"furnace".equals(craftingBlock) && !"campfire".equals(craftingBlock)) {
                            // Ignore other recipes than furnaces
                            continue;
                        }
                        Map<String, Object> resultMap = (Map) recipe.get("output");
                        Item resultItem = Item.fromJson(resultMap);
                        Item inputItem;
                        try {
                            Map<String, Object> inputMap = (Map) recipe.get("input");
                            inputItem = Item.fromJson(inputMap);
                        } catch (Exception old) {
                            inputItem = Item.get(Utils.toInt(recipe.get("inputId")), recipe.containsKey("inputDamage") ? Utils.toInt(recipe.get("inputDamage")) : -1, 1);
                        }

                        switch (craftingBlock){
                            case "furnace":
                                FurnaceRecipe furnaceRecipe = new FurnaceRecipe(resultItem, inputItem);
                                this.registerRecipe(388, furnaceRecipe);
                                String runtimeId = RuntimeItems.getMapping(388).toRuntime(inputItem.getId(), inputItem.getDamage()).getIdentifier();
                                double xp = furnaceXpConfig.getDouble(runtimeId + ":" + inputItem.getDamage(), 0d);
                                if (xp != 0) {
                                    this.setRecipeXp(furnaceRecipe, xp);
                                }
                                break;
                            case "campfire":
                                this.registerRecipe(388, new CampfireRecipe(resultItem, inputItem));
                                break;
                        }
                        break;
                    /*case 4:
                        this.registerRecipe(new MultiRecipe(UUID.fromString((String) recipe.get("uuid"))));
                        break;*/
                    default:
                        break;
                }
            } catch (Exception e) {
                MainLogger.getLogger().error("Exception during registering (protocol 388) recipe", e);
            }
        }

        for (Map<String, Object> recipe : recipes_313) {
            try {
                switch (Utils.toInt(recipe.get("type"))) {
                    case 0:
                        Map<String, Object> first = ((List<Map>) recipe.get("output")).get(0);
                        List<Item> sorted = new ArrayList<>();
                        for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                            sorted.add(Item.fromJsonOld(ingredient));
                        }
                        sorted.sort(recipeComparator);
                        this.registerRecipe(313, new ShapelessRecipe(Item.fromJsonOld(first), sorted));
                        break;
                    case 1:
                        List<Map> output = (List<Map>) recipe.get("output");
                        first = output.remove(0);
                        String[] shape = ((List<String>) recipe.get("shape")).toArray(new String[0]);
                        Map<Character, Item> ingredients = new CharObjectHashMap<>();
                        List<Item> extraResults = new ArrayList<>();
                        Map<String, Map<String, Object>> input = (Map) recipe.get("input");
                        for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                            char ingredientChar = ingredientEntry.getKey().charAt(0);
                            Item ingredient = Item.fromJsonOld(ingredientEntry.getValue());
                            ingredients.put(ingredientChar, ingredient);
                        }
                        for (Map<String, Object> data : output) {
                            extraResults.add(Item.fromJsonOld(data));
                        }
                        this.registerRecipe(313, new ShapedRecipe(Item.fromJsonOld(first), shape, ingredients, extraResults));
                        break;
                    case 2:
                    case 3:
                        Map<String, Object> resultMap = (Map) recipe.get("output");
                        Item resultItem = Item.fromJsonOld(resultMap);
                        Item inputItem;
                        try {
                            Map<String, Object> inputMap = (Map) recipe.get("input");
                            inputItem = Item.fromJsonOld(inputMap);
                        } catch (Exception old) {
                            inputItem = Item.get(Utils.toInt(recipe.get("inputId")), recipe.containsKey("inputDamage") ? Utils.toInt(recipe.get("inputDamage")) : -1, 1);
                        }
                        FurnaceRecipe furnaceRecipe = new FurnaceRecipe(resultItem, inputItem);
                        this.furnaceRecipesOld.put(getItemHash(inputItem), furnaceRecipe);
                        double xp = furnaceXpConfig.getDouble(inputItem.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL) + ":" + inputItem.getDamage(), 0d);
                        if (xp != 0) {
                            this.setRecipeXp(furnaceRecipe, xp);
                        }
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                MainLogger.getLogger().error("Exception during registering (protocol 313) recipe", e);
            }
        }

        for (Map<String, Object> recipe : recipes_332) {
            try {
                switch (Utils.toInt(recipe.get("type"))) {
                    case 0:
                        Map<String, Object> first = ((List<Map>) recipe.get("output")).get(0);
                        List<Item> sorted = new ArrayList<>();
                        for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                            sorted.add(Item.fromJsonOld(ingredient));
                        }
                        sorted.sort(recipeComparator);
                        this.registerRecipe(332, new ShapelessRecipe(Item.fromJsonOld(first), sorted));
                        break;
                    case 1:
                        List<Map> output = (List<Map>) recipe.get("output");
                        first = output.remove(0);
                        String[] shape = ((List<String>) recipe.get("shape")).toArray(new String[0]);
                        Map<Character, Item> ingredients = new CharObjectHashMap<>();
                        List<Item> extraResults = new ArrayList<>();
                        Map<String, Map<String, Object>> input = (Map) recipe.get("input");
                        for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                            char ingredientChar = ingredientEntry.getKey().charAt(0);
                            Item ingredient = Item.fromJsonOld(ingredientEntry.getValue());
                            ingredients.put(ingredientChar, ingredient);
                        }
                        for (Map<String, Object> data : output) {
                            extraResults.add(Item.fromJsonOld(data));
                        }
                        this.registerRecipe(332, new ShapedRecipe(Item.fromJsonOld(first), shape, ingredients, extraResults));
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                MainLogger.getLogger().error("Exception during registering (protocol 332) recipe", e);
            }
        }

        Config extras = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes388.json"));
        List<Map> potionMixes = extras.getMapList("potionMixes");
        for (Map potionMix : potionMixes) {
            int fromPotionId = ((Number) potionMix.get("fromPotionId")).intValue();
            int ingredient = ((Number) potionMix.get("ingredient")).intValue();
            int toPotionId = ((Number) potionMix.get("toPotionId")).intValue();
            registerBrewingRecipeOld(new BrewingRecipe(Item.get(ItemID.POTION, fromPotionId), Item.get(ingredient), Item.get(ItemID.POTION, toPotionId)));
        }

        List<Map> containerMixes = extras.getMapList("containerMixes");
        for (Map containerMix : containerMixes) {
            int fromItemId = ((Number) containerMix.get("fromItemId")).intValue();
            int ingredient = ((Number) containerMix.get("ingredient")).intValue();
            int toItemId = ((Number) containerMix.get("toItemId")).intValue();
            registerContainerRecipeOld(new ContainerRecipe(Item.get(fromItemId), Item.get(ingredient), Item.get(toItemId)));
        }

        Config extras407 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("extras_407.json"));
        List<Map> potionMixes407 = extras407.getMapList("potionMixes");
        for (Map potionMix : potionMixes407) {
            int fromPotionId = ((Number) potionMix.get("inputId")).intValue();
            int fromPotionMeta = ((Number) potionMix.get("inputMeta")).intValue();
            int ingredient = ((Number) potionMix.get("reagentId")).intValue();
            int ingredientMeta = ((Number) potionMix.get("reagentMeta")).intValue();
            int toPotionId = ((Number) potionMix.get("outputId")).intValue();
            int toPotionMeta = ((Number) potionMix.get("outputMeta")).intValue();
            registerBrewingRecipe(new BrewingRecipe(Item.get(fromPotionId, fromPotionMeta), Item.get(ingredient, ingredientMeta), Item.get(toPotionId, toPotionMeta)));
        }

        List<Map> containerMixes407 = extras407.getMapList("containerMixes");
        for (Map containerMix : containerMixes407) {
            int fromItemId = ((Number) containerMix.get("inputId")).intValue();
            int ingredient = ((Number) containerMix.get("reagentId")).intValue();
            int toItemId = ((Number) containerMix.get("outputId")).intValue();
            registerContainerRecipe(new ContainerRecipe(Item.get(fromItemId), Item.get(ingredient), Item.get(toItemId)));
        }

        this.rebuildPacket();
        MainLogger.getLogger().debug("Loaded " + this.recipes.size() + " recipes");
    }

    private List<ShapedRecipe> loadShapedRecipes(List<Map<String, Object>> recipes) {
        ArrayList<ShapedRecipe> recipesList = new ArrayList<>();
        for (Map<String, Object> recipe : recipes) {
            top:
            {
                if (!"crafting_table".equals(recipe.get("block"))) {
                    // Ignore other recipes than crafting table ones
                    continue;
                }
                List<Map> outputs = ((List<Map>) recipe.get("output"));
                Map<String, Object> first = outputs.remove(0);
                String[] shape = ((List<String>) recipe.get("shape")).toArray(new String[0]);
                Map<Character, Item> ingredients = new CharObjectHashMap();
                List<Item> extraResults = new ArrayList();
                Map<String, Map<String, Object>> input = (Map) recipe.get("input");
                for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                    char ingredientChar = ingredientEntry.getKey().charAt(0);
                    Item ingredient = Item.fromJson(ingredientEntry.getValue());

                    // TODO: update recipes
                    //1.20.50开始 木板被拆分为单独方块，给每个方块都注册一次
                    if (ingredient.getId() == Item.PLANKS && Utils.toInt(ingredientEntry.getValue().getOrDefault("damage", 0)) == -1) {
                        recipesList.addAll(createLegacyPlanksRecipe(recipe, first));
                        break top;
                    }

                    ingredients.put(ingredientChar, ingredient);
                }

                for (Map<String, Object> data : outputs) {
                    extraResults.add(Item.fromJson(data));
                }

                String recipeId = (String) recipe.get("id");
                int priority = Utils.toInt(recipe.get("priority"));
                Item result = Item.fromJson(first);

                recipesList.add(new ShapedRecipe(recipeId, priority, result, shape, ingredients, extraResults));
            }
        }

        return recipesList;
    }

    private List<ShapelessRecipe> loadShapelessRecips(List<Map<String, Object>> recipes) {
        ArrayList<ShapelessRecipe> recipesList = new ArrayList<>();
        for (Map<String, Object> recipe : recipes) {
            if (!"crafting_table".equals(recipe.get("block"))) {
                // Ignore other recipes than crafting table ones
                continue;
            }
            // TODO: handle multiple result items
            List<Map> outputs = ((List<Map>) recipe.get("output"));
            if (outputs.size() > 1) {
                continue;
            }

            String recipeId = (String) recipe.get("id");
            int priority = Math.max(Utils.toInt(recipe.get("priority")) - 1, 0);

            Map<String, Object> first = outputs.get(0);
            Item item = Item.fromJson(first);
            if (item.getId() == Item.FIREWORKS) {
                Item itemFirework = item.clone();
                List<Item> sorted = new ArrayList();
                if (itemFirework instanceof ItemFirework) {
                    boolean hasResult = false;
                    for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                        Item ingredientItem = Item.fromJson(ingredient);
                        sorted.add(ingredientItem);
                        if (ingredientItem.getId() != 289) {
                            continue;
                        }
                        sorted.add(ingredientItem.clone());
                        hasResult = true;
                    }
                    if (!hasResult) {
                        throw new RuntimeException("Missing result item for " + recipe);
                    }
                } else {
                    throw new RuntimeException("Unexpected result item: " + itemFirework.toString());
                }
                sorted.sort(recipeComparator);
                ((ItemFirework)itemFirework).setFlight(2);
                recipesList.add(new ShapelessRecipe(recipeId, priority, item, sorted));

                itemFirework = item.clone();
                if (itemFirework instanceof ItemFirework) {
                    sorted = new ArrayList();
                    boolean hasResult = false;
                    for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                        Item ingredientItem = Item.fromJson(ingredient);
                        sorted.add(ingredientItem);
                        if (ingredientItem.getId() != 289) {
                            continue;
                        }
                        sorted.add(ingredientItem.clone());
                        sorted.add(ingredientItem.clone());
                        hasResult = true;
                    }
                    if (!hasResult) {
                        throw new RuntimeException("Missing result item for " + recipe);
                    }
                    sorted.sort(recipeComparator);
                    ((ItemFirework)itemFirework).setFlight(3);
                    recipesList.add(new ShapelessRecipe(recipeId, priority, itemFirework, sorted));
                } else {
                    throw new RuntimeException("Unexpected result item: " + itemFirework.toString());
                }
            }

            List<Item> sorted = new ArrayList<>();
            for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                sorted.add(Item.fromJson(ingredient));
            }
            // Bake sorted list
            sorted.sort(recipeComparator);

            recipesList.add(new ShapelessRecipe(recipeId, priority, item, sorted));
        }
        return recipesList;
    }

    private List<SmeltingRecipe> loadSmeltingRecipes(List<Map<String, Object>> recipes, Config furnaceXpConfig) {
        ArrayList<SmeltingRecipe> recipesList = new ArrayList<>();
        for (Map<String, Object> recipe : recipes) {
            String craftingBlock = (String)recipe.get("block");
            if (!"furnace".equals(craftingBlock) && !"campfire".equals(craftingBlock)) {
                continue;
            }

            Map<String, Object> resultMap = (Map) recipe.get("output");
            Item resultItem = Item.fromJson(resultMap);
            Item inputItem;
            try {
                inputItem = Item.fromJson((Map) recipe.get("input"));
            } catch (Exception exception) {
                inputItem = Item.get(Utils.toInt(recipe.get("inputId")), recipe.containsKey("inputDamage") ? Utils.toInt(recipe.get("inputDamage")) : -1, 1);
            }

            switch (craftingBlock) {
                case "furnace": {
                    FurnaceRecipe furnaceRecipe = new FurnaceRecipe(resultItem, inputItem);
                    double xp = furnaceXpConfig.getDouble(inputItem.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL) + ":" + inputItem.getDamage(), 0d);
                    if (xp != 0) {
                        this.setRecipeXp(furnaceRecipe, xp);
                    }
                    recipesList.add(furnaceRecipe);
                    break;
                }
                case "campfire": {
                    recipesList.add(new CampfireRecipe(resultItem, inputItem));
                }
            }
        }
        return recipesList;
    }

    private ArrayList<ShapedRecipe> createLegacyPlanksRecipe(Map<String, Object> recipe, Map<String, Object> first) {
        List<Map> outputs = (List<Map>) recipe.get("output");
        String[] shape = ((List<String>) recipe.get("shape")).toArray(new String[0]);
        List<Item> extraResults = new ArrayList<>();
        for (Map data : outputs) {
            extraResults.add(Item.fromJson(data));
        }
        ArrayList<ShapedRecipe> list = new ArrayList<>();
        for (int planksMeta = 0; planksMeta <= 5; planksMeta++) {
            Map<Character, Item> ingredients = new CharObjectHashMap<>();
            Map<String, Map<String, Object>> input = (Map) recipe.get("input");
            for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                char ingredientChar = ingredientEntry.getKey().charAt(0);
                ingredientEntry.getValue().put("damage", 0);
                Item ingredient = Item.fromJson(ingredientEntry.getValue());
                if (ingredient.getId() == Item.PLANKS) {
                    ingredient.setDamage(planksMeta);
                }
                ingredients.put(ingredientChar, ingredient);
            }
            list.add(new ShapedRecipe((String) recipe.get("id")/* + "_" + planksMeta*/, Utils.toInt(recipe.get("priority")), Item.fromJson(first), shape, ingredients, extraResults));
        }
        return list;
    }

    private CraftingDataPacket packetFor(int protocol) {
        CraftingDataPacket pk = new CraftingDataPacket();
        pk.protocol = protocol;
        for (Recipe recipe : this.getRecipes(protocol)) {
            if (recipe instanceof ShapedRecipe) {
                pk.addShapedRecipe((ShapedRecipe) recipe);
            } else if (recipe instanceof ShapelessRecipe) {
                pk.addShapelessRecipe((ShapelessRecipe) recipe);
            }
        }
        for (SmithingRecipe recipe : this.getSmithingRecipes(protocol).values()) {
            pk.addShapelessRecipe(recipe);
        }
        //TODO Fix 1.13.0 - 1.14.0 client crash
        if (protocol != ProtocolInfo.v1_13_0) {
            for (FurnaceRecipe recipe : this.getFurnaceRecipes(protocol).values()) {
                pk.addFurnaceRecipe(recipe);
            }
        }
        if (protocol >= ProtocolInfo.v1_13_0) {
            for (BrewingRecipe recipe : this.getBrewingRecipes(protocol).values()) {
                pk.addBrewingRecipe(recipe);
            }
            for (ContainerRecipe recipe : this.getContainerRecipes(protocol).values()) {
                pk.addContainerRecipe(recipe);
            }
            if (protocol >= ProtocolInfo.v1_16_0) {
                for (MultiRecipe recipe : this.getMultiRecipes(protocol).values()) {
                    pk.addMultiRecipe(recipe);
                }
            }
        }
        pk.tryEncode();
        return pk;
    }

    public void rebuildPacket() {
        //TODO Multiversion 添加新版本支持时修改这里
        packet630 = packetFor(ProtocolInfo.v1_20_50);
        packet622 = packetFor(ProtocolInfo.v1_20_40);
        packet618 = packetFor(ProtocolInfo.v1_20_30);
        packet594 = packetFor(ProtocolInfo.v1_20_10);
        packet589 = packetFor(ProtocolInfo.v1_20_0);
        packet582 = packetFor(582);
        packet575 = packetFor(575);
        packet567 = packetFor(567);
        packet560 = packetFor(560);
        packet554 = packetFor(554);
        packet544 = packetFor(544);
        packet527 = packetFor(527);
        packet503 = packetFor(503);
        packet486 = packetFor(486);
        packet471 = packetFor(471);
        packet465 = packetFor(465);
        packet448 = packetFor(448);
        packet440 = packetFor(440);
        packet431 = packetFor(431);
        packet419 = packetFor(419);
        packet407 = packetFor(407).compress(Deflater.BEST_COMPRESSION);
        packet388 = packetFor(388).compress(Deflater.BEST_COMPRESSION);
        packet361 = packetFor(361).compress(Deflater.BEST_COMPRESSION);
        packet354 = packetFor(354).compress(Deflater.BEST_COMPRESSION);
        packet340 = packetFor(340).compress(Deflater.BEST_COMPRESSION);
        packet313 = packetFor(313).compress(Deflater.BEST_COMPRESSION);
    }

    public Map<UUID, SmithingRecipe> getSmithingRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_20_0_23) {
            return smithingRecipes;
        } else {
            return Collections.emptyMap();
        }
    }

    public Collection<Recipe> getRecipes() {
        Server.mvw("CraftingManager#getRecipes()");
        return this.getRecipes(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public Collection<Recipe> getRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            return this.recipes;
        }
        if (protocol >= 419) {
            return this.recipes419;
        }
        if (protocol >= 354) {
            return this.recipes354;
        }
        if (protocol >= 340) {
            return this.recipes332;
        }
        return this.recipes313;
    }

    private Collection<Recipe> getRegisterRecipes(int protocol) {
        if (protocol == 527) {
            return this.recipes;
        }
        if (protocol == 419) {
            return this.recipes419;
        }
        if (protocol == 388) {
            return this.recipes354;
        }
        if (protocol == 332) {
            return this.recipes332;
        }
        if (protocol == 313) {
            return this.recipes313;
        }
        throw new IllegalArgumentException("Invalid protocol: " + protocol + " Supported: 527, 419, 388, 332, 313");
    }

    public Map<Integer, FurnaceRecipe> getFurnaceRecipes() {
        Server.mvw("CraftingManager#getFurnaceRecipes()");
        return this.getFurnaceRecipes(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public Map<Integer, FurnaceRecipe> getFurnaceRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_17_0) {
            return this.furnaceRecipes;
        } else if (protocol >= ProtocolInfo.v1_10_0) {
            return this.furnaceRecipes340;
        }
        return this.furnaceRecipesOld;
    }

    public Map<Integer, ContainerRecipe> getContainerRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_16_0) {
            return this.containerRecipes;
        }
        return this.containerRecipesOld;
    }

    public Map<Integer, BrewingRecipe> getBrewingRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_16_0) {
            return this.brewingRecipes;
        }
        return this.brewingRecipesOld;
    }

    public Map<UUID, MultiRecipe> getMultiRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_16_0) {
            return this.multiRecipes;
        }
        throw new IllegalArgumentException("Multi recipes are not supported for protocol " + protocol + " (< 407)");
    }

    public MultiRecipe getMultiRecipe(Player player, Item outputItem, List<Item> inputs) {
        return this.multiRecipes.values().stream().filter(multiRecipe -> multiRecipe.canExecute(player, outputItem, inputs)).findFirst().orElse(null);
    }

    public FurnaceRecipe matchFurnaceRecipe(Item input) {
        Server.mvw("CraftingManager#matchFurnaceRecipe()");
        return matchFurnaceRecipe(ProtocolInfo.CURRENT_PROTOCOL, input);
    }

    public FurnaceRecipe matchFurnaceRecipe(int protocol, Item input) {
        Map<Integer, FurnaceRecipe> recipes = this.getFurnaceRecipes(protocol);
        FurnaceRecipe recipe = recipes.get(getItemHash(input));
        if (recipe == null) recipe = recipes.get(getItemHash(input, 0));
        return recipe;
    }

    public static UUID getMultiItemHash(Collection<Item> items) {
        BinaryStream stream = new BinaryStream();
        for (Item item : items) {
            stream.putVarInt(getFullItemHash(item));
        }
        return UUID.nameUUIDFromBytes(stream.getBuffer());
    }

    private static int getFullItemHash(Item item) {
        //return 31 * getItemHash(item) + item.getCount();
        return (getItemHash(item) << 6) | (item.getCount() & 0x3f);
    }

    public void registerFurnaceRecipe(FurnaceRecipe recipe) {
        Server.mvw("CraftingManager#registerFurnaceRecipe()");
        this.registerFurnaceRecipe(ProtocolInfo.CURRENT_PROTOCOL, recipe);
    }

    public void registerFurnaceRecipe(int protocol, FurnaceRecipe recipe) {
        this.getFurnaceRecipes(protocol).put(getItemHash(recipe.getInput()), recipe);
    }

    public void registerCampfireRecipe(CampfireRecipe recipe) {
        Item input = recipe.getInput();
        this.campfireRecipes.put(getItemHash(input), recipe);
    }

    private static int getItemHash(Item item) {
        return getItemHash(item, item.getDamage());
    }

    private static int getItemHash(Item item, int meta) {
        int id = item.getId() == Item.STRING_IDENTIFIED_ITEM ? item.getNetworkId(ProtocolInfo.CURRENT_PROTOCOL) : item.getId();
        return (id << 12) | (meta & 0xfff);
    }

    @Deprecated
    private static int getItemHash(int id, int meta) {
        //return (id << 4) | (meta & 0xf);
        //return (id << Block.DATA_BITS) | (meta & Block.DATA_MASK);
        return (id << 12) | (meta & 0xfff);
    }

    public Map<Integer, Map<UUID, ShapedRecipe>> getShapedRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            return this.shapedRecipes;
        }
        if (protocol >= 419) {
            return this.shapedRecipes419;
        }
        if (protocol >= 354) {
            return this.shapedRecipes388;
        }
        if (protocol >= 340) {
            return this.shapedRecipes332;
        }
        return this.shapedRecipes313;
    }

    public void registerShapedRecipe(ShapedRecipe recipe) {
        Server.mvw("CraftingManager#registerShapedRecipe(ShapedRecipe)");
        this.registerShapedRecipe(313, recipe);
        this.registerShapedRecipe(332, recipe);
        this.registerShapedRecipe(388, recipe);
        this.registerShapedRecipe(419, recipe);
        this.registerShapedRecipe(527, recipe);
    }

    public void registerShapedRecipe(int protocol, ShapedRecipe recipe) {
        int resultHash = getItemHash(recipe.getResult());
        Map<UUID, ShapedRecipe> map;
        switch (protocol) {
            case 313:
                map = shapedRecipes313.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            case 332:
                map = shapedRecipes332.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            case 388: {
                map = this.shapedRecipes388.computeIfAbsent(resultHash, n -> new HashMap());
                break;
            }
            case 419: {
                map = this.shapedRecipes419.computeIfAbsent(resultHash, n -> new HashMap());
                break;
            }
            case 527: {
                map = this.shapedRecipes.computeIfAbsent(resultHash, n -> new HashMap());
                break;
            }
            default:
                throw new IllegalArgumentException("Tried to register a shaped recipe for unsupported protocol version: " + protocol);
        }
        map.put(getMultiItemHash(new LinkedList<>(recipe.getIngredientsAggregate())), recipe);
    }

    public void registerRecipe(Recipe recipe) {
        Server.mvw("CraftingManager#registerRecipe(Recipe)");
        this.registerRecipe(527, recipe);
    }

    public void registerRecipe(int protocol, Recipe recipe) {
        if (recipe instanceof SmithingRecipe smithingRecipe) {
            this.registerSmithingRecipe(protocol, smithingRecipe);
        } else if (recipe instanceof CraftingRecipe) {
            UUID id = Utils.dataToUUID(String.valueOf(++RECIPE_COUNT), String.valueOf(recipe.getResult().getId()), String.valueOf(recipe.getResult().getDamage()), String.valueOf(recipe.getResult().getCount()), Arrays.toString(recipe.getResult().getCompoundTag()));
            ((CraftingRecipe) recipe).setId(id);
            this.getRegisterRecipes(protocol).add(recipe);
            if (recipe instanceof ShapedRecipe) {
                this.registerShapedRecipe(protocol, (ShapedRecipe) recipe);
            } else if (recipe instanceof ShapelessRecipe) {
                this.registerShapelessRecipe(protocol, (ShapelessRecipe) recipe);
            }
        } else if (recipe instanceof FurnaceRecipe furnaceRecipe) {
            this.registerFurnaceRecipe(protocol, furnaceRecipe);
        }
        recipe.registerToCraftingManager(this);
    }

    public Map<Integer, Map<UUID, ShapelessRecipe>> getShapelessRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            return this.shapelessRecipes;
        }
        if (protocol >= 419) {
            return this.shapelessRecipes419;
        }
        if (protocol >= 354) {
            return this.shapelessRecipes388;
        }
        if (protocol >= 340) {
            return this.shapelessRecipes332;
        }
        return this.shapelessRecipes313;
    }

    public void registerShapelessRecipe(ShapelessRecipe recipe) {
        Server.mvw("CraftingManager#registerShapelessRecipe(ShapelessRecipe)");
        this.registerShapelessRecipe(313, recipe);
        this.registerShapelessRecipe(332, recipe);
        this.registerShapelessRecipe(388, recipe);
        this.registerShapelessRecipe(419, recipe);
        this.registerShapelessRecipe(527, recipe);
    }

    public void registerShapelessRecipe(int protocol, ShapelessRecipe recipe) {
        List<Item> list = recipe.getIngredientsAggregate();
        UUID hash = getMultiItemHash(list);
        int resultHash = getItemHash(recipe.getResult());
        Map<UUID, ShapelessRecipe> map;
        switch (protocol) {
            case 313:
                map = shapelessRecipes313.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            case 332:
                map = shapelessRecipes332.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            case 388:
                map = shapelessRecipes388.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            case 419:
                map = shapelessRecipes419.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            case 527:
                map = shapelessRecipes.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            default:
                throw new IllegalArgumentException("Tried to register a shapeless recipe for unsupported protocol version: " + protocol);
        }
        map.put(hash, recipe);
    }

    private static int getPotionHash(Item ingredient, Item potion) {
        int ingredientHash = ((ingredient.getId() & 0x3FF) << 6) | (ingredient.getDamage() & 0x3F);
        int potionHash = ((potion.getId() & 0x3FF) << 6) | (potion.getDamage() & 0x3F);
        return ingredientHash << 16 | potionHash;
    }

    private static int getPotionHashOld(int ingredientId, int potionType) {
        //return (ingredientId << 6) | potionType;
        return (ingredientId << 15) | potionType;
    }

    private static int getContainerHash(int ingredientId, int containerId) {
        //return (ingredientId << 9) | containerId;
        return (ingredientId << 15) | containerId;
    }

    public void registerSmithingRecipe(int protocol, SmithingRecipe recipe) {
        UUID multiItemHash = getMultiItemHash(recipe.getIngredientsAggregate());
        if (protocol >= ProtocolInfo.v1_20_0_23) {
            this.smithingRecipes.put(multiItemHash, recipe);
        }
    }

    public void registerBrewingRecipe(BrewingRecipe recipe) {
        Item input = recipe.getIngredient();
        Item potion = recipe.getInput();
        int potionHash = getPotionHash(input, potion);
        this.brewingRecipes.put(potionHash, recipe);
    }

    public void registerBrewingRecipeOld(BrewingRecipe recipe) {
        Item input = recipe.getIngredient();
        Item potion = recipe.getInput();
        this.brewingRecipesOld.put(getPotionHashOld(input.getId(), potion.getDamage()), recipe);
    }

    public void registerContainerRecipe(ContainerRecipe recipe) {
        Item input = recipe.getIngredient();
        Item potion = recipe.getInput();
        this.containerRecipes.put(getContainerHash(input.getId(), potion.getId()), recipe);
    }

    public void registerContainerRecipeOld(ContainerRecipe recipe) {
        Item input = recipe.getIngredient();
        Item potion = recipe.getInput();
        this.containerRecipesOld.put(getContainerHash(input.getId(), potion.getId()), recipe);
    }

    public BrewingRecipe matchBrewingRecipe(Item input, Item potion) {
        return this.brewingRecipes.get(getPotionHash(input, potion));
    }

    public CampfireRecipe matchCampfireRecipe(Item input) {
        CampfireRecipe recipe = this.campfireRecipes.get(getItemHash(input));
        if (recipe == null) recipe = this.campfireRecipes.get(getItemHash(input, 0));
        return recipe;
    }

    public ContainerRecipe matchContainerRecipe(Item input, Item potion) {
        return this.containerRecipes.get(getContainerHash(input.getId(), potion.getId()));
    }

    public CraftingRecipe matchRecipe(List<Item> inputList, Item primaryOutput, List<Item> extraOutputList) {
        Server.mvw("CraftingManager#matchRecipe(List<Item>, Item , List<Item>)");
        return this.matchRecipe(ProtocolInfo.CURRENT_PROTOCOL, inputList, primaryOutput, extraOutputList);
    }

    public CraftingRecipe matchRecipe(int protocol, List<Item> inputList, Item primaryOutput, List<Item> extraOutputList) {
        int outputHash = getItemHash(primaryOutput);
        if (this.getShapedRecipes(protocol).containsKey(outputHash)) {
            inputList.sort(recipeComparator);

            UUID inputHash = getMultiItemHash(inputList);

            Map<UUID, ShapedRecipe> recipeMap = this.getShapedRecipes(protocol).get(outputHash);

            if (recipeMap != null) {
                ShapedRecipe recipe = recipeMap.get(inputHash);

                if (recipe != null && (recipe.matchItems(inputList, extraOutputList) || matchItemsAccumulation(recipe, inputList, primaryOutput, extraOutputList))) {
                    return recipe;
                }

                for (ShapedRecipe shapedRecipe : recipeMap.values()) {
                    if (shapedRecipe.matchItems(inputList, extraOutputList) || matchItemsAccumulation(shapedRecipe, inputList, primaryOutput, extraOutputList)) {
                        return shapedRecipe;
                    }
                }
            }
        }

        if (this.getShapelessRecipes(protocol).containsKey(outputHash)) {
            inputList.sort(recipeComparator);

            Map<UUID, ShapelessRecipe> recipes = this.getShapelessRecipes(protocol).get(outputHash);

            if (recipes == null) {
                return null;
            }

            UUID inputHash = getMultiItemHash(inputList);
            ShapelessRecipe recipe = recipes.get(inputHash);

            if (recipe != null && (recipe.matchItems(inputList, extraOutputList) || matchItemsAccumulation(recipe, inputList, primaryOutput, extraOutputList))) {
                return recipe;
            }

            for (ShapelessRecipe shapelessRecipe : recipes.values()) {
                if (shapelessRecipe.matchItems(inputList, extraOutputList) || matchItemsAccumulation(shapelessRecipe, inputList, primaryOutput, extraOutputList)) {
                    return shapelessRecipe;
                }
            }
        }
        return null;
    }

    private static boolean matchItemsAccumulation(CraftingRecipe recipe, List<Item> inputList, Item primaryOutput, List<Item> extraOutputList) {
        Item recipeResult = recipe.getResult();
        if (primaryOutput.equals(recipeResult, recipeResult.hasMeta(), recipeResult.hasCompoundTag()) && primaryOutput.getCount() % recipeResult.getCount() == 0) {
            int multiplier = primaryOutput.getCount() / recipeResult.getCount();
            return recipe.matchItems(inputList, extraOutputList, multiplier);
        }
        return false;
    }

    public void registerMultiRecipe(MultiRecipe recipe) {
        this.multiRecipes.put(recipe.getId(), recipe);
    }

    @Deprecated
    public SmithingRecipe matchSmithingRecipe(Item equipment, Item ingredient) {
        return matchSmithingRecipe(ProtocolInfo.CURRENT_PROTOCOL, Arrays.asList(equipment, ingredient));
    }

    @Nullable
    public SmithingRecipe matchSmithingRecipe(int protocol, List<Item> inputList) {
        inputList.sort(recipeComparator);
        UUID inputHash = getMultiItemHash(inputList);

        Map<UUID, SmithingRecipe> recipeMap = this.getSmithingRecipes(protocol);

        if (recipeMap != null) {
            SmithingRecipe recipe = recipeMap.get(inputHash);

            if (recipe != null && recipe.matchItems(inputList)) {
                return recipe;
            }

            ArrayList<Item> list = new ArrayList<>();
            for (Item item : inputList) {
                Item clone = item.clone();
                clone.setCount(1);
                if (item.isTool() && item.getDamage() > 0) {
                    clone.setDamage(0);
                }
                list.add(clone);
            }

            for (SmithingRecipe smithingRecipe : recipeMap.values()) {
                if (smithingRecipe.matchItems(list)) {
                    return smithingRecipe;
                }
            }
        }
        return null;
    }

    public static class Entry {
        final int resultItemId;
        final int resultMeta;
        final int ingredientItemId;
        final int ingredientMeta;
        final String recipeShape;
        final int resultAmount;

        public Entry(int resultItemId, int resultMeta, int ingredientItemId, int ingredientMeta, String recipeShape, int resultAmount) {
            this.resultItemId = resultItemId;
            this.resultMeta = resultMeta;
            this.ingredientItemId = ingredientItemId;
            this.ingredientMeta = ingredientMeta;
            this.recipeShape = recipeShape;
            this.resultAmount = resultAmount;
        }
    }

    public double getRecipeXp(Recipe recipe) {
        return recipeXpMap.getOrDefault(recipe, 0.0);
    }

    public Object2DoubleOpenHashMap<Recipe> getRecipeXpMap() {
        return recipeXpMap;
    }

    public void setRecipeXp(Recipe recipe, double xp) {
        recipeXpMap.put(recipe, xp);
    }
}
