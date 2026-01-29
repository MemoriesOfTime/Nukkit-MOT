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

    public final Collection<Recipe> recipes = new ArrayDeque<>();

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

    private final Map<Integer, Map<UUID, ShapedRecipe>> shapedRecipes = new Int2ObjectOpenHashMap<>();

    private final Map<Integer, Map<UUID, ShapelessRecipe>> shapelessRecipes = new Int2ObjectOpenHashMap<>();

    public final Map<UUID, MultiRecipe> multiRecipes = new HashMap<>();

    public final Map<Integer, FurnaceRecipe> furnaceRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, BlastFurnaceRecipe> blastFurnaceRecipes = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, BrewingRecipe> brewingRecipes = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, ContainerRecipe> containerRecipes = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, CampfireRecipe> campfireRecipes = new Int2ObjectOpenHashMap<>();
    private final Map<UUID, SmithingRecipe> smithingRecipes = new Object2ObjectOpenHashMap<>();

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

        // 加载主配方文件（最新版本配方，通过 Item.isSupportedOn() 动态过滤实现多版本兼容）
        ConfigSection recipes_649_config = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes649.json")).getRootSection();
        ConfigSection recipes_smithing_config = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes_smithing.json")).getRootSection();
        Config furnaceXpConfig = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes/furnace_xp.json"));

        this.loadRecipes(recipes_649_config, furnaceXpConfig);

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

            this.registerSmithingRecipe(new SmithingRecipe(recipeId, priority, sorted, item));
        }

        // 加载酿造和容器配方（从 recipes649.json）
        List<Map> potionMixes = recipes_649_config.getMapList("potionMixes");
        for (Map potionMix : potionMixes) {
            int fromPotionId = ((Number) potionMix.get("inputId")).intValue();
            int fromPotionMeta = ((Number) potionMix.get("inputMeta")).intValue();
            int ingredient = ((Number) potionMix.get("reagentId")).intValue();
            int ingredientMeta = ((Number) potionMix.get("reagentMeta")).intValue();
            int toPotionId = ((Number) potionMix.get("outputId")).intValue();
            int toPotionMeta = ((Number) potionMix.get("outputMeta")).intValue();
            registerBrewingRecipe(new BrewingRecipe(Item.get(fromPotionId, fromPotionMeta), Item.get(ingredient, ingredientMeta), Item.get(toPotionId, toPotionMeta)));
        }

        List<Map> containerMixes = recipes_649_config.getMapList("containerMixes");
        for (Map containerMix : containerMixes) {
            int fromItemId = ((Number) containerMix.get("inputId")).intValue();
            int ingredient = ((Number) containerMix.get("reagentId")).intValue();
            int toItemId = ((Number) containerMix.get("outputId")).intValue();
            registerContainerRecipe(new ContainerRecipe(Item.get(fromItemId), Item.get(ingredient), Item.get(toItemId)));
        }

        this.rebuildPacket();
        MainLogger.getLogger().debug("Loaded " + this.recipes.size() + " recipes");
    }

    private void loadRecipes(ConfigSection configSection, Config furnaceXpConfig) {
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
            this.registerRecipe(recipe);
        }

        for (ShapelessRecipe recipe : loadShapelessRecips(shapelessRecipesList)) {
            this.registerRecipe(recipe);
        }

        for (SmeltingRecipe recipe : loadSmeltingRecipes(furnaceRecipesList, furnaceXpConfig)) {
            this.registerRecipe(recipe);
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
        for (Recipe recipe : this.getRecipes()) {
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
        for (SmithingRecipe recipe : this.getSmithingRecipes().values()) {
            if (recipe.getIngredient().isSupportedOn(protocol)
                    && recipe.getEquipment().isSupportedOn(protocol)
                    && recipe.getTemplate().isSupportedOn(protocol)
                    && recipe.getResult().isSupportedOn(protocol)) {
                pk.addShapelessRecipe(recipe);
            }
        }
        //TODO Fix 1.10.0 - 1.14.0 client crash
        if (protocol < ProtocolInfo.v1_10_0 || protocol > ProtocolInfo.v1_13_0) {
            for (FurnaceRecipe recipe : this.getFurnaceRecipes().values()) {
                if (recipe.getInput().isSupportedOn(protocol) && recipe.getResult().isSupportedOn(protocol)) {
                    pk.addFurnaceRecipe(recipe);
                }
            }
        }
        if (protocol >= ProtocolInfo.v1_13_0) {
            for (BrewingRecipe recipe : this.getBrewingRecipes().values()) {
                if (recipe.getIngredient().isSupportedOn(protocol)
                        && recipe.getInput().isSupportedOn(protocol)
                        && recipe.getResult().isSupportedOn(protocol)) {
                    pk.addBrewingRecipe(recipe);
                }
            }
            for (ContainerRecipe recipe : this.getContainerRecipes().values()) {
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

    public Map<UUID, SmithingRecipe> getSmithingRecipes() {
        return smithingRecipes;
    }

    @Deprecated
    public Map<UUID, SmithingRecipe> getSmithingRecipes(int protocol) {
        return this.getSmithingRecipes();
    }

    public Collection<Recipe> getRecipes() {
        return this.recipes;
    }

    @Deprecated
    public Collection<Recipe> getRecipes(int protocol) {
        return this.recipes;
    }

    public Map<Integer, FurnaceRecipe> getFurnaceRecipes() {
        return this.furnaceRecipes;
    }

    @Deprecated
    public Map<Integer, FurnaceRecipe> getFurnaceRecipes(int protocol) {
        return this.furnaceRecipes;
    }

    public Map<Integer, BlastFurnaceRecipe> getBlastFurnaceRecipes() {
        return this.blastFurnaceRecipes;
    }

    public Map<Integer, ContainerRecipe> getContainerRecipes() {
        return this.containerRecipes;
    }

    @Deprecated
    public Map<Integer, ContainerRecipe> getContainerRecipes(int protocol) {
        return this.containerRecipes;
    }

    public Map<Integer, BrewingRecipe> getBrewingRecipes() {
        return this.brewingRecipes;
    }

    @Deprecated
    public Map<Integer, BrewingRecipe> getBrewingRecipes(int protocol) {
        return this.brewingRecipes;
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
        FurnaceRecipe recipe = this.furnaceRecipes.get(getItemHash(input));
        if (recipe == null) recipe = this.furnaceRecipes.get(getItemHash(input, 0));
        return recipe;
    }

    @Deprecated
    public FurnaceRecipe matchFurnaceRecipe(int protocol, Item input) {
        return matchFurnaceRecipe(input);
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
        if (recipe instanceof BlastFurnaceRecipe) {
            this.registerBlastFurnaceRecipe((BlastFurnaceRecipe) recipe);
            return;
        }
        this.furnaceRecipes.put(getItemHash(recipe.getInput()), recipe);
    }

    @Deprecated
    public void registerFurnaceRecipe(int protocol, FurnaceRecipe recipe) {
        this.registerFurnaceRecipe(recipe);
    }

    public void registerBlastFurnaceRecipe(BlastFurnaceRecipe recipe) {
        this.blastFurnaceRecipes.put(getItemHash(recipe.getInput()), recipe);
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

    public Map<Integer, Map<UUID, ShapedRecipe>> getShapedRecipes() {
        return this.shapedRecipes;
    }

    @Deprecated
    public Map<Integer, Map<UUID, ShapedRecipe>> getShapedRecipes(int protocol) {
        return this.shapedRecipes;
    }

    public void registerShapedRecipe(ShapedRecipe recipe) {
        int resultHash = getItemHash(recipe.getResult());
        Map<UUID, ShapedRecipe> map = this.shapedRecipes.computeIfAbsent(resultHash, k -> new HashMap<>());
        map.put(getMultiItemHash(new LinkedList<>(recipe.getIngredientsAggregate())), recipe);
    }

    @Deprecated
    public void registerShapedRecipe(int protocol, ShapedRecipe recipe) {
        this.registerShapedRecipe(recipe);
    }

    public void registerRecipe(Recipe recipe) {
        if (recipe instanceof FurnaceRecipe furnaceRecipe) {
            this.registerFurnaceRecipe(furnaceRecipe);
        } else if (recipe instanceof SmithingRecipe smithingRecipe) {
            this.registerSmithingRecipe(smithingRecipe);
        } else if (recipe instanceof CraftingRecipe) {
            UUID id = Utils.dataToUUID(String.valueOf(++RECIPE_COUNT), String.valueOf(recipe.getResult().getId()), String.valueOf(recipe.getResult().getDamage()), String.valueOf(recipe.getResult().getCount()), Arrays.toString(recipe.getResult().getCompoundTag()));
            ((CraftingRecipe) recipe).setId(id);
            this.recipes.add(recipe);
            if (recipe instanceof ShapedRecipe) {
                this.registerShapedRecipe((ShapedRecipe) recipe);
            } else if (recipe instanceof ShapelessRecipe) {
                this.registerShapelessRecipe((ShapelessRecipe) recipe);
            }
        } else {
            recipe.registerToCraftingManager(this);
        }
    }

    @Deprecated
    public void registerRecipe(int protocol, Recipe recipe) {
        this.registerRecipe(recipe);
    }

    public Map<Integer, Map<UUID, ShapelessRecipe>> getShapelessRecipes() {
        return this.shapelessRecipes;
    }

    @Deprecated
    public Map<Integer, Map<UUID, ShapelessRecipe>> getShapelessRecipes(int protocol) {
        return this.shapelessRecipes;
    }

    public void registerShapelessRecipe(ShapelessRecipe recipe) {
        List<Item> list = recipe.getIngredientsAggregate();
        UUID hash = getMultiItemHash(list);
        int resultHash = getItemHash(recipe.getResult());
        Map<UUID, ShapelessRecipe> map = this.shapelessRecipes.computeIfAbsent(resultHash, k -> new HashMap<>());
        map.put(hash, recipe);
    }

    @Deprecated
    public void registerShapelessRecipe(int protocol, ShapelessRecipe recipe) {
        this.registerShapelessRecipe(recipe);
    }

    private static int getPotionHash(Item ingredient, Item potion) {
        int ingredientHash = ((ingredient.getId() & 0x3FF) << 6) | (ingredient.getDamage() & 0x3F);
        int potionHash = ((potion.getId() & 0x3FF) << 6) | (potion.getDamage() & 0x3F);
        return ingredientHash << 16 | potionHash;
    }

    private static int getContainerHash(int ingredientId, int containerId) {
        //return (ingredientId << 9) | containerId;
        return (ingredientId << 15) | containerId;
    }

    public void registerSmithingRecipe(SmithingRecipe recipe) {
        UUID multiItemHash = getMultiItemHash(recipe.getIngredientsAggregate());
        this.smithingRecipes.put(multiItemHash, recipe);
    }

    @Deprecated
    public void registerSmithingRecipe(int protocol, SmithingRecipe recipe) {
        this.registerSmithingRecipe(recipe);
    }

    public void registerBrewingRecipe(BrewingRecipe recipe) {
        Item input = recipe.getIngredient();
        Item potion = recipe.getInput();
        int potionHash = getPotionHash(input, potion);
        this.brewingRecipes.put(potionHash, recipe);
    }

    public void registerContainerRecipe(ContainerRecipe recipe) {
        Item input = recipe.getIngredient();
        Item potion = recipe.getInput();
        this.containerRecipes.put(getContainerHash(input.getId(), potion.getId()), recipe);
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
        int outputHash = getItemHash(primaryOutput);

        Map<UUID, ShapedRecipe> shapedRecipeMap = this.shapedRecipes.get(outputHash);
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

        Map<UUID, ShapelessRecipe> shapelessRecipeMap = this.shapelessRecipes.get(outputHash);
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

    @Deprecated
    public CraftingRecipe matchRecipe(int protocol, List<Item> inputList, Item primaryOutput, List<Item> extraOutputList) {
        return matchRecipe(inputList, primaryOutput, extraOutputList);
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
        return matchSmithingRecipe(Arrays.asList(equipment, ingredient));
    }

    @Deprecated
    @Nullable
    public SmithingRecipe matchSmithingRecipe(int protocol, List<Item> inputList) {
        return matchSmithingRecipe(inputList);
    }

    @Nullable
    public SmithingRecipe matchSmithingRecipe(List<Item> inputList) {
        inputList.sort(recipeComparator);

        SmithingRecipe recipe = this.getSmithingRecipes().get(getMultiItemHash(inputList));
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

        for (SmithingRecipe smithingRecipe : this.getSmithingRecipes().values()) {
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
