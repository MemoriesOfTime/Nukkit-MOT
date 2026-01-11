package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.inventory.special.*;
import cn.nukkit.item.*;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.CraftingDataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.*;
import io.netty.util.collection.CharObjectHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

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
    private final Collection<Recipe> recipes527 = new ArrayDeque<>();
    public final Collection<Recipe> recipes = new ArrayDeque<>(); //649

    private static BatchPacket packet313;
    private static BatchPacket packet340;
    private static BatchPacket packet361;
    private static BatchPacket packet354;
    private static BatchPacket packet388;
    private static BatchPacket packet407;
    private static BatchPacket packet419;
    private static BatchPacket packet431;
    private static BatchPacket packet440;
    private static BatchPacket packet448;
    private static BatchPacket packet465;
    private static BatchPacket packet471;
    private static BatchPacket packet486;
    private static BatchPacket packet503;
    private static BatchPacket packet527;
    private static BatchPacket packet544;
    private static BatchPacket packet554;
    private static BatchPacket packet560;
    private static BatchPacket packet567;
    private static BatchPacket packet575;
    private static BatchPacket packet582;
    private static BatchPacket packet589;
    private static BatchPacket packet594;
    private static BatchPacket packet618;
    private static BatchPacket packet622;
    private static BatchPacket packet630;
    private static BatchPacket packet649;
    private static BatchPacket packet662;
    private static BatchPacket packet671;
    private static BatchPacket packet685;
    private static BatchPacket packet712;
    private static BatchPacket packet729;
    private static BatchPacket packet748;
    private static BatchPacket packet766;
    private static BatchPacket packet776;
    private static BatchPacket packet800;
    private static BatchPacket packet818;
    private static BatchPacket packet827;
    private static BatchPacket packet844;
    private static BatchPacket packet859;
    private static BatchPacket packet898;

    private static BatchPacket packet_netease_630;
    private static BatchPacket packet_netease_686;
    private static BatchPacket packet_netease_766;

    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes313 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes332 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes388 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes419 = new Int2ObjectOpenHashMap<>();
    protected final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes527 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes = new Int2ObjectOpenHashMap<>(); //649

    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes313 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes332 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes388 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes419 = new Int2ObjectOpenHashMap<>();
    protected final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes527 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes = new Int2ObjectOpenHashMap<>(); //649

    public final Map<UUID, MultiRecipe> multiRecipes = new HashMap<>();

    public final Map<Integer, FurnaceRecipe> furnaceRecipes = new Int2ObjectOpenHashMap<>(); //649
    public final Map<Integer, FurnaceRecipe> furnaceRecipes440 = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, FurnaceRecipe> furnaceRecipes340 = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, FurnaceRecipe> furnaceRecipesOld = new Int2ObjectOpenHashMap<>();

    private final Map<Integer, BlastFurnaceRecipe> blastFurnaceRecipes = new Int2ObjectOpenHashMap<>(); //649

    public final Map<Integer, BrewingRecipe> brewingRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, BrewingRecipe> brewingRecipesOld = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, ContainerRecipe> containerRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, ContainerRecipe> containerRecipesOld = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, CampfireRecipe> campfireRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<UUID, SmithingRecipe> smithingRecipes = new Object2ObjectOpenHashMap<>(); //589

    private final Object2DoubleOpenHashMap<Recipe> recipeXpMap = new Object2DoubleOpenHashMap<>();

    private static int RECIPE_COUNT = 0;
    static int NEXT_NETWORK_ID = 1; // Reserve 1 for smithing_armor_trim

    public static final Comparator<Item> recipeComparator = (i1, i2) -> {
        if (i1.getId() > i2.getId()) {
            return 1;
        } else if (i1.getId() < i2.getId()) {
            return -1;
        } else {
            if (!i1.isNull() && !i2.isNull()) {
                int i = MinecraftNamespaceComparator.compareFNV(i1.getNamespaceId(GameVersion.getLastVersion()), i2.getNamespaceId(GameVersion.getLastVersion()));
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
        this.registerMultiRecipe(new RepairItemRecipe());
        this.registerMultiRecipe(new BookCloningRecipe());
        this.registerMultiRecipe(new MapCloningRecipe());
        this.registerMultiRecipe(new MapUpgradingRecipe());
        this.registerMultiRecipe(new MapExtendingRecipe());
        this.registerMultiRecipe(new BannerAddPatternRecipe());
        this.registerMultiRecipe(new BannerDuplicateRecipe());
        this.registerMultiRecipe(new FireworkRecipe());
        this.registerMultiRecipe(new DecoratedPotRecipe());

        ConfigSection recipes_649_config = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes649.json")).getRootSection();
        ConfigSection recipes_419_config = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes419.json")).getRootSection();
        List<Map> recipes_388 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes388.json")).getRootSection().getMapList("recipes");
        List<Map> recipes_332 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes332.json")).getMapList("recipes");
        List<Map> recipes_313 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes313.json")).getMapList("recipes");

        ConfigSection recipes_smithing_config = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes_smithing.json")).getRootSection();
        ConfigSection recipes_extras_440 = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes/recipes_extras_440.json")).getRootSection();
        Config furnaceXpConfig = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes/furnace_xp.json"));

        this.loadRecipes(649, recipes_649_config, furnaceXpConfig);

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
            Item item = Item.fromJson(first, true);

            List<Item> sorted = new ArrayList<>();
            for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                sorted.add(Item.fromJson(ingredient, true));
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
            top:
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
                            Item sortedItem = Item.fromJson(ingredient, true);
                            if (sortedItem == null) {
                                break top;
                            }
                            sorted.add(sortedItem);
                        }
                        sorted.sort(recipeComparator);

                        String recipeId = (String) recipe.get("id");
                        int priority = Utils.toInt(recipe.get("priority"));

                        Item result = Item.fromJson(first, true);
                        if (result == null) {
                            break top;
                        }

                        this.registerRecipe(388, new ShapelessRecipe(recipeId, priority, result, sorted));
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
                            Item ingredient = Item.fromJson(ingredientEntry.getValue(), true);
                            if (ingredient == null) {
                                break top;
                            }

                            ingredients.put(ingredientChar, ingredient);
                        }

                        for (Map<String, Object> data : outputs) {
                            extraResults.add(Item.fromJson(data, true));
                        }

                        recipeId = (String) recipe.get("id");
                        priority = Utils.toInt(recipe.get("priority"));

                        result = Item.fromJson(first, true);
                        if (result == null) {
                            break top;
                        }

                        this.registerRecipe(388, new ShapedRecipe(recipeId, priority, result, shape, ingredients, extraResults));
                        break;
                    case 2:
                    case 3:
                        craftingBlock = (String) recipe.get("block");
                        if (!"furnace".equals(craftingBlock) && !"campfire".equals(craftingBlock)) {
                            // Ignore other recipes than furnaces
                            continue;
                        }
                        Map<String, Object> resultMap = (Map) recipe.get("output");
                        Item resultItem = Item.fromJson(resultMap, true);
                        if (resultItem == null) {
                            break top;
                        }
                        Item inputItem;
                        try {
                            Map<String, Object> inputMap = (Map) recipe.get("input");
                            inputItem = Item.fromJson(inputMap, true);
                        } catch (Exception old) {
                            inputItem = Item.get(Utils.toInt(recipe.get("inputId")), recipe.containsKey("inputDamage") ? Utils.toInt(recipe.get("inputDamage")) : -1, 1);
                        }

                        switch (craftingBlock){
                            case "furnace":
                                FurnaceRecipe furnaceRecipe = new FurnaceRecipe(resultItem, inputItem);
                                this.registerRecipe(388, furnaceRecipe);
                                String runtimeId = RuntimeItems.getMapping(GameVersion.V1_13_0).toRuntime(inputItem.getId(), inputItem.getDamage()).getIdentifier();
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
                    case 4:
                        String uuid = (String) recipe.get("uuid");
                        // TODO: when cartography is supported, this should be removed and add relevant checks like MapCloningRecipe.class.
                        if (MultiRecipe.unsupportedRecipes.contains(uuid)) {
                            this.registerRecipe(new UncheckedMultiRecipe(uuid));
                        }
                        break;
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

    private void loadRecipes(int protocol, ConfigSection configSection, Config furnaceXpConfig) {
        List<Map<String, Object>> shapedRecipesList = new ArrayList<>();
        List<Map<String, Object>> shapelessRecipesList = new ArrayList<>();
        List<Map<String, Object>> furnaceRecipesList = new ArrayList<>();
        List<Map<String, Object>> shulkerBoxRecipesList = new ArrayList<>();

        for (Map<String, Object> entry : (List<Map<String, Object>>) configSection.get("recipes")) {
            switch ((Integer) entry.getOrDefault("type", -1)) {
                case 0: // shapeless - Check block
                    shapelessRecipesList.add(entry);
                    break;
                case 1: // shaped
                    shapedRecipesList.add(entry);
                    break;
                case 3: // furnace
                    furnaceRecipesList.add(entry);
                    break;
                case 4: // hardcoded recipes
                    // Ignore type 4
                    break;
                case 5:
                    shulkerBoxRecipesList.add(entry);
                    break;
            }
        }
        for (ShapedRecipe recipe : loadShapedRecipes(shapedRecipesList)) {
            this.registerRecipe(protocol, recipe);
        }

        for (ShapelessRecipe recipe : loadShapelessRecips(shapelessRecipesList)) {
            this.registerRecipe(protocol, recipe);
        }

        for (SmeltingRecipe recipe : loadSmeltingRecipes(furnaceRecipesList, furnaceXpConfig)) {
            this.registerRecipe(protocol, recipe);
        }
        // TODO: shapeless_shulker_box
    }

    private List<ShapedRecipe> loadShapedRecipes(List<Map<String, Object>> recipes) {
        List<ShapedRecipe> recipesList = new ObjectArrayList<>();
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
                    Item ingredient = Item.fromJson(ingredientEntry.getValue(), true);
                    if (ingredient == null) {
                        break top;
                    }

                    // TODO: update recipes
                    //1.20.50开始 木板被拆分为单独方块，给每个方块都注册一次
                    if (ingredient.getId() == Item.PLANKS && Utils.toInt(ingredientEntry.getValue().getOrDefault("damage", 0)) == -1) {
                        recipesList.addAll(createLegacyPlanksRecipe(recipe, first));
                        break top;
                    }

                    ingredients.put(ingredientChar, ingredient);
                }

                for (Map<String, Object> data : outputs) {
                    Item eItem = Item.fromJson(data, true);
                    if (eItem == null) {
                        break top;
                    }
                    extraResults.add(eItem);
                }

                String recipeId = (String) recipe.get("id");
                int priority = Utils.toInt(recipe.get("priority"));
                Item result = Item.fromJson(first, true);
                if (result == null) {
                    continue ;
                }

                recipesList.add(new ShapedRecipe(recipeId, priority, result, shape, ingredients, extraResults));
            }
        }

        return recipesList;
    }

    private List<ShapelessRecipe> loadShapelessRecips(List<Map<String, Object>> recipes) {
        ArrayList<ShapelessRecipe> recipesList = new ArrayList<>();
        for (Map<String, Object> recipe : recipes) {
            top:
            {
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
                Item item = Item.fromJson(first, true);
                if (item == null) {
                    continue;
                }
                if (item.getId() == Item.FIREWORKS) {
                    Item itemFirework = item.clone();
                    List<Item> sorted = new ArrayList();
                    if (itemFirework instanceof ItemFirework) {
                        boolean hasResult = false;
                        for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                            Item ingredientItem = Item.fromJson(ingredient, true);
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
                    ((ItemFirework) itemFirework).setFlight(2);
                    recipesList.add(new ShapelessRecipe(recipeId, priority, item, sorted));

                    itemFirework = item.clone();
                    if (itemFirework instanceof ItemFirework) {
                        sorted = new ArrayList();
                        boolean hasResult = false;
                        for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                            Item ingredientItem = Item.fromJson(ingredient, true);
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
                        ((ItemFirework) itemFirework).setFlight(3);
                        recipesList.add(new ShapelessRecipe(recipeId, priority, itemFirework, sorted));
                    } else {
                        throw new RuntimeException("Unexpected result item: " + itemFirework.toString());
                    }
                }

                List<Item> sorted = new ArrayList<>();
                for (Map<String, Object> ingredient : ((List<Map>) recipe.get("input"))) {
                    Item sortedItem = Item.fromJson(ingredient, true);
                    if (sortedItem == null) {
                        break top;
                    }
                    sorted.add(sortedItem);
                }
                // Bake sorted list
                sorted.sort(recipeComparator);

                recipesList.add(new ShapelessRecipe(recipeId, priority, item, sorted));
            }
        }
        return recipesList;
    }

    private List<SmeltingRecipe> loadSmeltingRecipes(List<Map<String, Object>> recipes, Config furnaceXpConfig) {
        ArrayList<SmeltingRecipe> recipesList = new ArrayList<>();
        for (Map<String, Object> recipe : recipes) {
            String craftingBlock = (String)recipe.get("block");
            if (!"furnace".equals(craftingBlock)
                    && !"blast_furnace".equals(craftingBlock)
                    && !"campfire".equals(craftingBlock)) {
                continue;
            }

            Map<String, Object> resultMap = (Map) recipe.get("output");
            Item resultItem = Item.fromJson(resultMap, true);
            if (resultItem == null) {
                continue;
            }
            Item inputItem;
            try {
                inputItem = Item.fromJson((Map) recipe.get("input"), true);
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
                case "blast_furnace": {
                    BlastFurnaceRecipe furnaceRecipe = new BlastFurnaceRecipe(resultItem, inputItem);
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
            Item eItem = Item.fromJson(data, true);
            extraResults.add(eItem);
        }
        ArrayList<ShapedRecipe> list = new ArrayList<>();
        for (int planksMeta = 0; planksMeta <= 5; planksMeta++) {
            Map<Character, Item> ingredients = new CharObjectHashMap<>();
            Map<String, Map<String, Object>> input = (Map) recipe.get("input");
            for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                char ingredientChar = ingredientEntry.getKey().charAt(0);
                Map<String, Object> value = ingredientEntry.getValue();
                if ((int) value.getOrDefault("damage", -1) < 0) {
                    value.put("damage", 0);
                }
                Item ingredient = Item.fromJson(value, true);
                if (ingredient.getId() == Item.PLANKS) {
                    ingredient.setDamage(planksMeta);
                }
                ingredients.put(ingredientChar, Item.get(ingredient.getId(), ingredient.getDamage(), ingredient.getCount())); //使用Item.get()方法保证修改后的damage正常处理
            }
            Item result = Item.fromJson(first, true);
            if (result == null) {
                continue;
            }
            list.add(new ShapedRecipe((String) recipe.get("id")/* + "_" + planksMeta*/, Utils.toInt(recipe.get("priority")), result, shape, ingredients, extraResults));
        }
        return list;
    }

    private BatchPacket packetFor(GameVersion gameVersion) {
        int protocol = gameVersion.getProtocol();
        CraftingDataPacket pk = new CraftingDataPacket();
        pk.protocol = protocol;
        pk.gameVersion = gameVersion;
        for (Recipe recipe : this.getRecipes(protocol)) {
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                boolean isSupported = true;
                for (Item item : shapedRecipe.getAllResults()) {
                    if (!item.isSupportedOn(protocol)) {
                        isSupported = false;
                        break;
                    }
                }
                if (isSupported) {
                    for (Item ingredient : shapedRecipe.getIngredientList()) {
                        if (!ingredient.isSupportedOn(protocol)) {
                            isSupported = false;
                            break;
                        }
                    }
                }
                if (isSupported) {
                    pk.addShapedRecipe(shapedRecipe);
                }
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                boolean isSupported = true;
                if (!shapelessRecipe.getResult().isSupportedOn(protocol)) {
                    isSupported = false;
                    break;
                }
                if (isSupported) {
                    for (Item ingredient : shapelessRecipe.getIngredientList()) {
                        if (!ingredient.isSupportedOn(protocol)) {
                            isSupported = false;
                            break;
                        }
                    }
                }
                if (isSupported) {
                    pk.addShapelessRecipe(shapelessRecipe);
                }
            }
        }
        for (SmithingRecipe recipe : this.getSmithingRecipes(protocol).values()) {
            if (recipe.getIngredient().isSupportedOn(protocol)
                    && recipe.getEquipment().isSupportedOn(protocol)
                    && recipe.getTemplate().isSupportedOn(protocol)
                    && recipe.getResult().isSupportedOn(protocol)) {
                pk.addShapelessRecipe(recipe);
            }
        }
        //TODO Fix 1.10.0 - 1.14.0 client crash
        if (protocol < ProtocolInfo.v1_10_0 || protocol > ProtocolInfo.v1_13_0) {
            for (FurnaceRecipe recipe : this.getFurnaceRecipes(protocol).values()) {
                if (recipe.getInput().isSupportedOn(protocol) && recipe.getResult().isSupportedOn(protocol)) {
                    pk.addFurnaceRecipe(recipe);
                }
            }
        }
        if (protocol >= ProtocolInfo.v1_13_0) {
            for (BrewingRecipe recipe : this.getBrewingRecipes(protocol).values()) {
                if (recipe.getIngredient().isSupportedOn(protocol)
                        && recipe.getInput().isSupportedOn(protocol)
                        && recipe.getResult().isSupportedOn(protocol)) {
                    pk.addBrewingRecipe(recipe);
                }
            }
            for (ContainerRecipe recipe : this.getContainerRecipes(protocol).values()) {
                if (recipe.getIngredient().isSupportedOn(protocol)
                        && recipe.getInput().isSupportedOn(protocol)
                        && recipe.getResult().isSupportedOn(protocol)) {
                    pk.addContainerRecipe(recipe);
                }
            }
            if (protocol >= ProtocolInfo.v1_16_0) {
                for (MultiRecipe recipe : this.getMultiRecipes().values()) {
                    if (recipe.isSupportedOn(protocol)) {
                        pk.addMultiRecipe(recipe);
                    }
                }
            }
        }
        pk.tryEncode();
        return pk.compress(Deflater.BEST_COMPRESSION);
    }

    public void rebuildPacket() {
        //TODO Multiversion 添加新版本支持时修改这里
        packet898 = null;
        packet859 = null;
        packet844 = null;
        packet827 = null;
        packet818 = null;
        packet800 = null;
        packet776 = null;
        packet766 = null;
        packet748 = null;
        packet729 = null;
        packet712 = null;
        packet685 = null;
        packet671 = null;
        packet662 = null;
        packet649 = null;
        packet630 = null;
        packet622 = null;
        packet618 = null;
        packet594 = null;
        packet589 = null;
        packet582 = null;
        packet575 = null;
        packet567 = null;
        packet560 = null;
        packet554 = null;
        packet544 = null;
        packet527 = null;
        packet503 = null;
        packet486 = null;
        packet471 = null;
        packet465 = null;
        packet448 = null;
        packet440 = null;
        packet431 = null;
        packet419 = null;
        packet407 = null;
        packet388 = null;
        packet361 = null;
        packet354 = null;
        packet340 = null;
        packet313 = null;

        packet_netease_630 = null;
        packet_netease_686 = null;
        packet_netease_766 = null;

        this.getCachedPacket(GameVersion.getLastVersion()); // 缓存当前协议版本的数据包
        this.getCachedPacket(GameVersion.V1_21_50_NETEASE);
    }

    @Deprecated
    public BatchPacket getCachedPacket(int protocol) {
        return getCachedPacket(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode));
    }

    /**
     * 获取缓存的数据包，根据不同的协议版本返回对应的数据包实例。<br/>
     * 该方法通过检查协议版本号，选择合适的缓存数据包。如果缓存中没有对应的数据包，则创建一个新的并缓存起来。<br/>
     * Get cached data packet based on the protocol version.<br/>
     * Choose the appropriate cached data packet based on the protocol version. If no cached data packet is found, create a new one and cache it.
     *
     * @param gameVersion 协议版本号，用于确定使用哪个缓存的数据包 <br/>
     *                 Protocol version used to determine which cached data packet to use
     * @return 返回对应协议版本的缓存数据包，如果没有找到对应的缓存，则返回null <br/>
     * Return the cached data packet for the specified protocol version, or null if no cached data packet is found
     */
    public BatchPacket getCachedPacket(GameVersion gameVersion) {
        //TODO Multiversion 添加新版本支持时修改这里
        int protocol = gameVersion.getProtocol();

        if (gameVersion.isNetEase()) {
            if (protocol >= GameVersion.V1_21_50_NETEASE.getProtocol()) {
                if (packet_netease_766 == null) {
                    packet_netease_766 = this.packetFor(GameVersion.V1_21_50_NETEASE);
                }
                return packet_netease_766;
            } else if (protocol >= GameVersion.V1_21_2_NETEASE.getProtocol()) {
                if (packet_netease_686 == null) {
                    packet_netease_686 = this.packetFor(GameVersion.V1_21_2_NETEASE);
                }
                return packet_netease_686;
            } else if (protocol >= GameVersion.V1_20_50_NETEASE.getProtocol()) {
                if (packet_netease_630 == null) {
                    packet_netease_630 = this.packetFor(GameVersion.V1_20_50_NETEASE);
                }
                return packet_netease_630;
            }
        }

        if (protocol >= GameVersion.V1_21_130_28.getProtocol()) {
            if (packet898 == null) {
                packet898 = packetFor(GameVersion.V1_21_130);
            }
            return packet898;
        } else if (protocol >= GameVersion.V1_21_120.getProtocol()) {
            if (packet859 == null) {
                packet859 = packetFor(GameVersion.V1_21_120);
            }
            return packet859;
        } else if (protocol >= GameVersion.V1_21_110_26.getProtocol()) {
            if (packet844 == null) {
                packet844 = packetFor(GameVersion.V1_21_110);
            }
            return packet844;
        } else if (protocol >= GameVersion.V1_21_100.getProtocol()) {
            if (packet827 == null) {
                packet827 = packetFor(GameVersion.V1_21_100);
            }
            return packet827;
        } else if (protocol >= ProtocolInfo.v1_21_90) {
            if (packet818 == null) {
                packet818 = packetFor(GameVersion.V1_21_90);
            }
            return packet818;
        } else if (protocol >= ProtocolInfo.v1_21_80) {
            if (packet800 == null) {
                packet800 = packetFor(GameVersion.V1_21_80);
            }
            return packet800;
        } else if (protocol >= ProtocolInfo.v1_21_60) {
            if (packet776 == null) {
                packet776 = packetFor(GameVersion.V1_21_60);
            }
            return packet776;
        } else if (protocol >= ProtocolInfo.v1_21_50_26) {
            if (packet766 == null) {
                packet766 = packetFor(GameVersion.V1_21_50);
            }
            return packet766;
        } else if (protocol >= ProtocolInfo.v1_21_40) {
            if (packet748 == null) {
                packet748 = packetFor(GameVersion.V1_21_40);
            }
            return packet748;
        } else if (protocol >= ProtocolInfo.v1_21_30) {
            if (packet729 == null) {
                packet729 = packetFor(GameVersion.V1_21_30);
            }
            return packet729;
        } else if (protocol >= ProtocolInfo.v1_21_20) {
            if (packet712 == null) {
                packet712 = packetFor(GameVersion.V1_21_20);
            }
            return packet712;
        } else if (protocol >= ProtocolInfo.v1_21_0) {
            if (packet685 == null) {
                packet685 = packetFor(GameVersion.V1_21_0);
            }
            return packet685;
        } else if (protocol >= ProtocolInfo.v1_20_80) {
            if (packet671 == null) {
                packet671 = packetFor(GameVersion.V1_20_80);
            }
            return packet671;
        } else if (protocol >= ProtocolInfo.v1_20_70) {
            if (packet662 == null) {
                packet662 = packetFor(GameVersion.V1_20_70);
            }
            return packet662;
        } else if (protocol >= ProtocolInfo.v1_20_60) {
            if (packet649 == null) {
                packet649 = packetFor(GameVersion.V1_20_60);
            }
            return packet649;
        } else if (protocol >= ProtocolInfo.v1_20_50) {
            if (packet630 == null) {
                packet630 = packetFor(GameVersion.V1_20_50);
            }
            return packet630;
        } else if (protocol >= ProtocolInfo.v1_20_40) {
            if (packet622 == null) {
                packet622 = packetFor(GameVersion.V1_20_40);
            }
            return packet622;
        } else if (protocol >= ProtocolInfo.v1_20_30_24) {
            if (packet618 == null) {
                packet618 = packetFor(GameVersion.V1_20_30);
            }
            return packet618;
        } else if (protocol >= ProtocolInfo.v1_20_10_21) {
            if (packet594 == null) {
                packet594 = packetFor(GameVersion.V1_20_10);
            }
            return packet594;
        } else if (protocol >= ProtocolInfo.v1_20_0_23) {
            if (packet589 == null) {
                packet589 = packetFor(GameVersion.V1_20_0);
            }
            return packet589;
        } else if (protocol >= ProtocolInfo.v1_19_80) {
            if (packet582 == null) {
                packet582 = packetFor(GameVersion.V1_19_80);
            }
            return packet582;
        } else if (protocol >= ProtocolInfo.v1_19_70_24) {
            if (packet575 == null) {
                packet575 = packetFor(GameVersion.V1_19_70);
            }
            return packet575;
        } else if (protocol >= ProtocolInfo.v1_19_60) {
            if (packet567 == null) {
                packet567 = packetFor(GameVersion.V1_19_60);
            }
            return packet567;
        } else if (protocol >= ProtocolInfo.v1_19_50_20) {
            if (packet560 == null) {
                packet560 = packetFor(GameVersion.V1_19_50);
            }
            return packet560;
        } else if (protocol >= ProtocolInfo.v1_19_30_23) {
            if (packet554 == null) {
                packet554 = packetFor(GameVersion.V1_19_30);
            }
            return packet554;
        } else if (protocol >= ProtocolInfo.v1_19_20) {
            if (packet544 == null) {
                packet544 = packetFor(GameVersion.V1_19_20);
            }
            return packet544;
        } else if (protocol >= ProtocolInfo.v1_19_0_29) {
            if (packet527 == null) {
                packet527 = packetFor(GameVersion.V1_19_0);
            }
            return packet527;
        } else if (protocol >= ProtocolInfo.v1_18_30) {
            if (packet503 == null) {
                packet503 = packetFor(GameVersion.V1_18_30);
            }
            return packet503;
        } else if (protocol >= ProtocolInfo.v1_18_10_26) {
            if (packet486 == null) {
                packet486 = packetFor(GameVersion.V1_18_10);
            }
            return packet486;
        } else if (protocol >= ProtocolInfo.v1_17_40) {
            if (packet471 == null) {
                packet471 = packetFor(GameVersion.V1_17_40);
            }
            return packet471;
        } else if (protocol >= ProtocolInfo.v1_17_30) {
            if (packet465 == null) {
                packet465 = packetFor(GameVersion.V1_17_30);
            }
            return packet465;
        } else if (protocol >= ProtocolInfo.v1_17_10) {
            if (packet448 == null) {
                packet448 = packetFor(GameVersion.V1_17_10);
            }
            return packet448;
        } else if (protocol >= ProtocolInfo.v1_17_0) {
            if (packet440 == null) {
                packet440 = packetFor(GameVersion.V1_17_0);
            }
            return packet440;
        } else if (protocol >= ProtocolInfo.v1_16_220) {
            if (packet431 == null) {
                packet431 = packetFor(GameVersion.V1_16_220);
            }
            return packet431;
        } else if (protocol >= ProtocolInfo.v1_16_100) {
            if (packet419 == null) {
                packet419 = packetFor(GameVersion.V1_16_100);
            }
            return packet419;
        } else if (protocol >= ProtocolInfo.v1_16_0) {
            if (packet407 == null) {
                packet407 = packetFor(GameVersion.V1_16_0);
            }
            return packet407;
        } else if (protocol >= ProtocolInfo.v1_13_0) {
            if (packet388 == null) {
                packet388 = packetFor(GameVersion.V1_13_0);
            }
            return packet388;
        } else if (protocol == ProtocolInfo.v1_12_0) {
            if (packet361 == null) {
                packet361 = packetFor(GameVersion.V1_12_0);
            }
            return packet361;
        } else if (protocol == ProtocolInfo.v1_11_0) {
            if (packet354 == null) {
                packet354 = packetFor(GameVersion.V1_11_0);
            }
            return packet354;
        } else if (protocol == ProtocolInfo.v1_10_0) {
            if (packet340 == null) {
                packet340 = packetFor(GameVersion.V1_10_0);
            }
            return packet340;
        } else if (protocol == ProtocolInfo.v1_9_0 || protocol == ProtocolInfo.v1_8_0 || protocol == ProtocolInfo.v1_7_0) { // these should work just fine
            if (packet313 == null) {
                packet313 = packetFor(GameVersion.V1_8_0);
            }
            return packet313;
        }
        return null;
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
        if (protocol >= ProtocolInfo.v1_20_60) {
            return this.recipes;
        }
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            return this.recipes527;
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
        if (protocol == 649) {
            return this.recipes;
        }
        if (protocol == 527) {
            return this.recipes527;
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
        throw new IllegalArgumentException("Invalid protocol: " + protocol + " Supported: 649 527, 419, 388, 332, 313");
    }

    public Map<Integer, FurnaceRecipe> getFurnaceRecipes() {
        Server.mvw("CraftingManager#getFurnaceRecipes()");
        return this.getFurnaceRecipes(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public Map<Integer, FurnaceRecipe> getFurnaceRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_20_60) {
            return this.furnaceRecipes;
        } else if (protocol >= ProtocolInfo.v1_17_0) {
            return this.furnaceRecipes440;
        } else if (protocol >= ProtocolInfo.v1_10_0) {
            return this.furnaceRecipes340;
        }
        return this.furnaceRecipesOld;
    }

    public Map<Integer, BlastFurnaceRecipe> getBlastFurnaceRecipes() {
        return this.blastFurnaceRecipes;
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

    @Deprecated
    public Map<UUID, MultiRecipe> getMultiRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_16_0) {
            return this.multiRecipes;
        }
        throw new IllegalArgumentException("Multi recipes are not supported for protocol " + protocol + " (< 407)");
    }

    public Map<UUID, MultiRecipe> getMultiRecipes() {
        return this.multiRecipes;
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

    public FurnaceRecipe matchBlastFurnaceRecipe(Item input) {
        Map<Integer, BlastFurnaceRecipe> recipes = this.getBlastFurnaceRecipes();
        if (recipes == null) {
            return null;
        }
        FurnaceRecipe recipe = recipes.get(getItemHash(input));
        if (recipe == null) recipe = recipes.get(getItemHash(input, 0));
        return recipe;
    }

    public static UUID getMultiItemHash(Collection<Item> items) {
        BinaryStream stream = new BinaryStream(items.size() * 5);
        for (Item item : items) {
            stream.putVarInt(getFullItemHash(item)); //putVarInt 5 byte
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
        if (recipe instanceof BlastFurnaceRecipe) {
            this.registerBlastFurnaceRecipe((BlastFurnaceRecipe) recipe);
            return;
        }
        this.getFurnaceRecipes(protocol).put(getItemHash(recipe.getInput()), recipe);
    }

    public void registerBlastFurnaceRecipe(BlastFurnaceRecipe recipe) {
        this.getBlastFurnaceRecipes().put(getItemHash(recipe.getInput()), recipe);
    }

    public void registerCampfireRecipe(CampfireRecipe recipe) {
        Item input = recipe.getInput();
        this.campfireRecipes.put(getItemHash(input), recipe);
    }

    private static int getItemHash(Item item) {
        return getItemHash(item, item.getDamage());
    }

    private static int getItemHash(Item item, int meta) {
        int id = item.getId() == Item.STRING_IDENTIFIED_ITEM ? item.getNetworkId(GameVersion.getLastVersion()) : item.getId();
        return (id << Block.DATA_BITS) | (meta & Block.DATA_MASK);
    }

    @Deprecated
    private static int getItemHash(int id, int meta) {
        return (id << Block.DATA_BITS) | (meta & Block.DATA_MASK);
    }

    public Map<Integer, Map<UUID, ShapedRecipe>> getShapedRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_20_60) {
            return this.shapedRecipes;
        }
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            return this.shapedRecipes527;
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
        this.registerShapedRecipe(649, recipe);
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
                map = this.shapedRecipes527.computeIfAbsent(resultHash, n -> new HashMap());
                break;
            }
            case 649: {
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
        this.registerRecipe(649, recipe);
    }

    public void registerRecipe(int protocol, Recipe recipe) {
        if (recipe instanceof FurnaceRecipe furnaceRecipe) {  // FurnaceRecipe implements SmeltingRecipe
            this.registerFurnaceRecipe(protocol, furnaceRecipe);
        } else if (recipe instanceof SmithingRecipe smithingRecipe) {
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
        } else {
            recipe.registerToCraftingManager(this);
        }
    }

    public Map<Integer, Map<UUID, ShapelessRecipe>> getShapelessRecipes(int protocol) {
        if (protocol >= ProtocolInfo.v1_20_60) {
            return this.shapelessRecipes;
        }
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            return this.shapelessRecipes527;
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
                map = shapelessRecipes527.computeIfAbsent(resultHash, k -> new HashMap<>());
                break;
            case 649:
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
        if (protocol >= ProtocolInfo.v1_20_0_23) {
            UUID multiItemHash = getMultiItemHash(recipe.getIngredientsAggregate());
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

        Map<UUID, ShapedRecipe> shapedRecipeMap = this.getShapedRecipes(protocol).get(outputHash);
        if (shapedRecipeMap != null) {
            inputList.sort(recipeComparator);
            UUID inputHash = getMultiItemHash(inputList);
            ShapedRecipe recipe = shapedRecipeMap.get(inputHash);
            if (recipe != null && (recipe.matchItems(inputList, extraOutputList) || matchItemsAccumulation(recipe, inputList, primaryOutput, extraOutputList))) {
                return recipe;
            }
            for (ShapedRecipe shapedRecipe : shapedRecipeMap.values()) {
                if (shapedRecipe.matchItems(inputList, extraOutputList) || matchItemsAccumulation(shapedRecipe, inputList, primaryOutput, extraOutputList)) {
                    return shapedRecipe;
                }
            }
        }

        Map<UUID, ShapelessRecipe> shapelessRecipeMap = this.getShapelessRecipes(protocol).get(outputHash);
        if (shapelessRecipeMap != null) {
            inputList.sort(recipeComparator);
            UUID inputHash = getMultiItemHash(inputList);
            ShapelessRecipe recipe = shapelessRecipeMap.get(inputHash);
            if (recipe != null && (recipe.matchItems(inputList, extraOutputList) || matchItemsAccumulation(recipe, inputList, primaryOutput, extraOutputList))) {
                return recipe;
            }
            for (ShapelessRecipe shapelessRecipe : shapelessRecipeMap.values()) {
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

        SmithingRecipe recipe = this.getSmithingRecipes(protocol).get(getMultiItemHash(inputList));
        if (recipe != null && recipe.matchItems(inputList)) {
            return recipe;
        }

        ArrayList<Item> list = new ArrayList<>(inputList.size());
        for (Item item : inputList) {
            Item clone = item.clone();
            clone.setCount(1);
            if (item instanceof ItemDurable && item.getDamage() > 0) {
                clone.setDamage(0);
            }
            list.add(clone);
        }

        for (SmithingRecipe smithingRecipe : this.getSmithingRecipes(protocol).values()) {
            if (smithingRecipe.matchItems(list)) {
                return smithingRecipe;
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
