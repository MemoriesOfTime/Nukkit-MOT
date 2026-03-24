package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
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
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nullable;
import java.util.*;
import java.util.zip.Deflater;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
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
    private static BatchPacket packet944;

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
    private final List<StonecutterRecipe> stonecutterRecipes = new ArrayList<>();

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

        Map<String, Object> root = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes.json")).getRootSection();
        RuntimeItemMapping itemMapping = RuntimeItems.getMapping(GameVersion.getLastVersion());
        Config furnaceXpConfig = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("recipes/furnace_xp.json"));

        for (Map recipe : (List<Map>) root.get("recipes")) {
            try {
                switch (Utils.toInt(recipe.get("type"))) {
                    case 0: // shapeless
                        if ("stonecutter".equals(recipe.get("block"))) {
                            loadStonecutterRecipe(itemMapping, recipe);
                        } else {
                            loadShapelessRecipe(itemMapping, recipe);
                        }
                        break;
                    case 1: // shaped
                        loadShapedRecipe(itemMapping, recipe);
                        break;
                    case 3: // smelting
                        loadSmeltingRecipe(itemMapping, recipe, furnaceXpConfig);
                        break;
                    case 4: // multi (hardcoded)
                        break;
                    case 5: // shulker_box
                        break;
                }
            } catch (Exception e) {
                log.debug("Error while loading recipe: {}", recipe, e);
            }
        }

        // Potion mixes
        for (Map potionMix : (List<Map>) root.get("potionMixes")) {
            RuntimeItemMapping.LegacyEntry legacyEntry;

            legacyEntry = itemMapping.fromIdentifier((String) potionMix.get("inputId"));
            if (legacyEntry == null) {
                log.trace("Unknown potion inputId: {}", potionMix);
                continue;
            }
            int fromPotionId = legacyEntry.getLegacyId();
            int fromPotionMeta = ((Integer) potionMix.get("inputMeta"));
            legacyEntry = itemMapping.fromIdentifier((String) potionMix.get("reagentId"));
            if (legacyEntry == null) {
                log.trace("Unknown potion reagentId: {}", potionMix);
                continue;
            }
            int ingredient = legacyEntry.getLegacyId();
            int ingredientMeta = ((Integer) potionMix.get("reagentMeta"));
            legacyEntry = itemMapping.fromIdentifier((String) potionMix.get("outputId"));
            if (legacyEntry == null) {
                log.trace("Unknown potion outputId: {}", potionMix);
                continue;
            }
            int toPotionId = legacyEntry.getLegacyId();
            int toPotionMeta = ((Integer) potionMix.get("outputMeta"));
            if (fromPotionId == 0 || ingredient == 0 || toPotionId == 0) {
                log.trace("Unknown potion mix: {}", potionMix);
                continue;
            }

            registerBrewingRecipe(new BrewingRecipe(Item.get(fromPotionId, fromPotionMeta), Item.get(ingredient, ingredientMeta), Item.get(toPotionId, toPotionMeta)));
        }

        // Container mixes
        for (Map containerMix : (List<Map>) root.get("containerMixes")) {
            RuntimeItemMapping.LegacyEntry legacyEntry;

            legacyEntry = itemMapping.fromIdentifier((String) containerMix.get("inputId"));
            if (legacyEntry == null) {
                log.trace("Unknown container inputId: {}", containerMix);
                continue;
            }
            int fromItemId = legacyEntry.getLegacyId();
            legacyEntry = itemMapping.fromIdentifier((String) containerMix.get("reagentId"));
            if (legacyEntry == null) {
                log.trace("Unknown container reagentId: {}", containerMix);
                continue;
            }
            int ingredientId = legacyEntry.getLegacyId();
            legacyEntry = itemMapping.fromIdentifier((String) containerMix.get("outputId"));
            if (legacyEntry == null) {
                log.trace("Unknown container outputId: {}", containerMix);
                continue;
            }
            int toItemId = legacyEntry.getLegacyId();
            if (fromItemId == 0 || ingredientId == 0 || toItemId == 0) {
                log.trace("Unknown container mix: {}", containerMix);
                continue;
            }

            registerContainerRecipe(new ContainerRecipe(Item.get(fromItemId), Item.get(ingredientId), Item.get(toItemId)));
        }

        // Smithing recipes
        ConfigSection smithing = new Config(Config.YAML).loadFromStream(Server.class.getClassLoader().getResourceAsStream("smithing.json")).getRootSection();
        top:
        for (Map<String, Object> recipe : (List<Map<String, Object>>) smithing.get((Object) "smithing")) {
            String recipeId = (String) recipe.get("id");
            Map<String, Object> first = ((List<Map<String, Object>>) recipe.get("output")).get(0);
            RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromIdentifier((String) first.get("id"));
            if (legacyEntry == null) {
                log.trace("Unknown smithing output: {}", recipe);
                continue;
            }

            Item item = Item.get(legacyEntry.getLegacyId(), 0, 1);

            List<Item> ingredients = new ArrayList<>();
            for (Map<String, Object> ingredient : ((List<Map<String, Object>>) recipe.get("input"))) {
                legacyEntry = itemMapping.fromIdentifier((String) ingredient.get("id"));
                if (legacyEntry == null) {
                    log.trace("Unknown smithing input: {}", recipe);
                    continue top;
                }
                ingredients.add(Item.get(legacyEntry.getLegacyId(), 0, 1));
            }

            this.registerSmithingRecipe(new SmithingRecipe(recipeId, 0, ingredients, item));
        }

        this.rebuildPacket();
        MainLogger.getLogger().debug("Loaded " + this.recipes.size() + " recipes, " + this.stonecutterRecipes.size() + " stonecutter recipes");
    }

    @SuppressWarnings("unchecked")
    private void loadShapelessRecipe(RuntimeItemMapping itemMapping, Map recipe) {
        if (!"crafting_table".equals(recipe.get("block"))) {
            return;
        }

        Map shapelessOutput = (Map) ((List) recipe.get("output")).get(0);
        RuntimeItemMapping.LegacyEntry shapelessOutputEntry = itemMapping.fromRuntime((int) shapelessOutput.get("legacyId"));
        top:
        if (shapelessOutputEntry != null && shapelessOutputEntry.getLegacyId() != 0) {
            int outputDamage = (int) shapelessOutput.getOrDefault("damage", 0);
            if (outputDamage == 0) {
                outputDamage = shapelessOutputEntry.getDamage();
            }
            String nbt = (String) shapelessOutput.get("nbt_b64");
            byte[] nbtBytes = nbt != null ? Base64.getDecoder().decode(nbt) : new byte[0];
            Item outputItem = Item.get(shapelessOutputEntry.getLegacyId(), outputDamage, (Integer) shapelessOutput.getOrDefault("count", 1), nbtBytes);
            List<Map> input = (List<Map>) recipe.get("input");
            List<Item> sorted = new ArrayList<>();

            for (Map<String, Object> ingredient : input) {
                String type = (String) ingredient.get("type");
                if (!"default".equals(type)) {
                    if ("item_tag".equals(type)) {
                        buildShapelessRecipeItemTagOverrides(itemMapping, input, outputItem, (String) ingredient.get("itemTag"), null, null);
                    } else {
                        log.trace("Unknown shapeless ingredient type: {}", recipe);
                    }
                    break top;
                }
                RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromRuntime((int) ingredient.get("itemId"));
                if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                    log.trace("Unknown shapeless input: {}", recipe);
                    break top;
                }
                int aux = (int) ingredient.getOrDefault("auxValue", 0);
                if (aux == 32767) {
                    aux = -1;
                } else if (aux == 0) {
                    aux = legacyEntry.getDamage();
                }
                sorted.add(Item.get(legacyEntry.getLegacyId(), aux, (Integer) ingredient.getOrDefault("count", 1)));
            }

            sorted.sort(recipeComparator);

            int priority = (int) recipe.getOrDefault("priority", 0);
            this.registerRecipe(new ShapelessRecipe((String) recipe.get("id"), priority, outputItem, sorted));

            // Inject recipes for flight duration 2 and 3 fireworks
            if (outputItem.getId() == Item.FIREWORKS && outputItem.getCount() == 3) {
                sorted.add(Item.get(Item.GUNPOWDER, 0, 1));
                sorted.sort(recipeComparator);
                ((ItemFirework) outputItem).setFlight(2);
                this.registerRecipe(new ShapelessRecipe(null, 0, outputItem, sorted));

                sorted.add(Item.get(Item.GUNPOWDER, 0, 1));
                sorted.sort(recipeComparator);
                ((ItemFirework) outputItem).setFlight(3);
                this.registerRecipe(new ShapelessRecipe(null, 0, outputItem, sorted));
            }
        } else {
            log.trace("Unknown shapeless output: {}", recipe);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadStonecutterRecipe(RuntimeItemMapping itemMapping, Map recipe) {
        Map outputMap = (Map) ((List) recipe.get("output")).get(0);
        RuntimeItemMapping.LegacyEntry outputEntry = itemMapping.fromRuntime((int) outputMap.get("legacyId"));
        if (outputEntry == null || outputEntry.getLegacyId() == 0) {
            log.trace("Unknown stonecutter output: {}", recipe);
            return;
        }

        int outputDamage = (int) outputMap.getOrDefault("damage", 0);
        if (outputDamage == 0) {
            outputDamage = outputEntry.getDamage();
        }
        String nbt = (String) outputMap.get("nbt_b64");
        byte[] nbtBytes = nbt != null ? Base64.getDecoder().decode(nbt) : new byte[0];
        Item outputItem = Item.get(outputEntry.getLegacyId(), outputDamage, (Integer) outputMap.getOrDefault("count", 1), nbtBytes);

        List<Map> input = (List<Map>) recipe.get("input");
        if (input.isEmpty()) {
            return;
        }
        Map<String, Object> ingredientMap = input.get(0);
        if (!"default".equals(ingredientMap.get("type"))) {
            log.trace("Unknown stonecutter ingredient type: {}", recipe);
            return;
        }
        RuntimeItemMapping.LegacyEntry inputEntry = itemMapping.fromRuntime((int) ingredientMap.get("itemId"));
        if (inputEntry == null || inputEntry.getLegacyId() == 0) {
            log.trace("Unknown stonecutter input: {}", recipe);
            return;
        }
        int inputAux = (int) ingredientMap.getOrDefault("auxValue", 0);
        int count = (Integer) ingredientMap.getOrDefault("count", 1);
        Item inputItem;
        if (inputAux == 32767) {
            if (inputEntry.isHasDamage()) {
                // 运行时物品映射到带 damage 的 legacy ID（如花岗岩→stone:1），使用具体 damage 值
                inputItem = Item.get(inputEntry.getLegacyId(), inputEntry.getDamage(), count);
            } else {
                // 真正的通配符：meta 传 null 使 hasMeta=false，编码时 damage=Short.MAX_VALUE(32767)，客户端按 runtimeId 过滤
                inputItem = Item.get(inputEntry.getLegacyId(), null, count);
            }
        } else {
            if (inputAux == 0) {
                inputAux = inputEntry.getDamage();
            }
            inputItem = Item.get(inputEntry.getLegacyId(), inputAux, count);
        }

        String recipeId = (String) recipe.get("id");
        int priority = (int) recipe.getOrDefault("priority", 0);
        this.registerStonecutterRecipe(new StonecutterRecipe(recipeId, priority, outputItem, inputItem));
    }

    @SuppressWarnings("unchecked")
    private void loadShapedRecipe(RuntimeItemMapping itemMapping, Map recipe) {
        if (!"crafting_table".equals(recipe.get("block"))) {
            return;
        }

        List<Map> outputList = (List<Map>) recipe.get("output");
        Map shapedOutput = outputList.get(0);
        RuntimeItemMapping.LegacyEntry shapedOutputEntry = itemMapping.fromRuntime((int) shapedOutput.get("legacyId"));
        top:
        if (shapedOutputEntry != null && shapedOutputEntry.getLegacyId() != 0) {
            int outputDamage = (int) shapedOutput.getOrDefault("damage", 0);
            if (outputDamage == 0) {
                outputDamage = shapedOutputEntry.getDamage();
            }
            String nbt = (String) shapedOutput.get("nbt_b64");
            byte[] nbtBytes = nbt != null ? Base64.getDecoder().decode(nbt) : new byte[0];
            Item outputItem = Item.get(shapedOutputEntry.getLegacyId(), outputDamage, (Integer) shapedOutput.getOrDefault("count", 1), nbtBytes);

            List<Item> extraOutputs = new ArrayList<>();
            for (int i = 1; i < outputList.size(); i++) {
                Map extraOutput = outputList.get(i);
                RuntimeItemMapping.LegacyEntry extraEntry = itemMapping.fromRuntime((int) extraOutput.get("legacyId"));
                if (extraEntry != null && extraEntry.getLegacyId() != 0) {
                    int extraDamage = (int) extraOutput.getOrDefault("damage", 0);
                    if (extraDamage == 0) {
                        extraDamage = extraEntry.getDamage();
                    }
                    String extraNbt = (String) extraOutput.get("nbt_b64");
                    byte[] extraNbtBytes = extraNbt != null ? Base64.getDecoder().decode(extraNbt) : new byte[0];
                    extraOutputs.add(Item.get(extraEntry.getLegacyId(), extraDamage, (Integer) extraOutput.getOrDefault("count", 1), extraNbtBytes));
                }
            }
            String[] shape = ((List<String>) recipe.get("shape")).toArray(new String[0]);
            Map<Character, Item> ingredients = new CharObjectHashMap<>();
            Map<String, Map<String, Object>> input = (Map) recipe.get("input");

            for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                Item inputItem = null;
                String type = (String) ingredientEntry.getValue().get("type");
                if (!"default".equals(type)) {
                    if ("item_tag".equals(type)) {
                        buildShapedRecipeItemTagOverrides(itemMapping, input, shape, outputItem, (String) ingredientEntry.getValue().get("itemTag"), null, null);
                    } else if ("complex_alias".equals(type)) {
                        switch ((String) recipe.get("id")) {
                            case "minecraft:painting":
                                inputItem = Item.get(BlockID.WOOL, 0, 1);
                                break;
                            case "minecraft:purpur_stairs":
                                inputItem = Item.get(BlockID.PURPUR_BLOCK, 0, 1);
                                break;
                            case "minecraft:stonecutter":
                                inputItem = Item.get(BlockID.STONE, 0, 1);
                                break;
                            case "minecraft:tnt":
                                inputItem = Item.get(BlockID.SAND, 0, 1);
                                break;
                            // TODO: bedrock allows alternative materials for some trim duplication
                            case "minecraft:dune_armor_trim_smithing_template_duplicate":
                                inputItem = Item.get(BlockID.SANDSTONE, 0, 1);
                                break;
                            case "minecraft:spire_armor_trim_smithing_template_duplicate":
                                inputItem = Item.get(BlockID.PURPUR_BLOCK, 0, 1);
                                break;
                            case "minecraft:tide_armor_trim_smithing_template_duplicate":
                                inputItem = Item.get(BlockID.PRISMARINE, 0, 1);
                                break;
                        }
                        if (inputItem == null) {
                            log.trace("Missing shaped ingredient complex_alias: {}", recipe);
                        }
                    } else {
                        log.trace("Unknown shaped ingredient type: {}", recipe);
                    }
                    if (inputItem == null) {
                        break top;
                    }
                }
                if (inputItem == null) {
                    RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromRuntime((int) ingredientEntry.getValue().get("itemId"));
                    if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                        log.trace("Unknown shaped input: {}", recipe);
                        break top;
                    }
                    int aux = (int) ingredientEntry.getValue().getOrDefault("auxValue", 0);
                    if (aux == 32767) {
                        aux = -1;
                    } else if (aux == 0) {
                        aux = legacyEntry.getDamage();
                    }
                    inputItem = Item.get(legacyEntry.getLegacyId(), aux, (Integer) ingredientEntry.getValue().getOrDefault("count", 1));
                }
                ingredients.put(ingredientEntry.getKey().charAt(0), inputItem);
            }

            int priority = (int) recipe.getOrDefault("priority", 0);
            this.registerRecipe(new ShapedRecipe((String) recipe.get("id"), priority, outputItem, shape, ingredients, extraOutputs));
        } else {
            log.trace("Unknown shaped output: {}", recipe);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSmeltingRecipe(RuntimeItemMapping itemMapping, Map recipe, Config furnaceXpConfig) {
        String smeltingBlock = (String) recipe.get("block");
        if (!"furnace".equals(smeltingBlock) && !"blast_furnace".equals(smeltingBlock) && !"campfire".equals(smeltingBlock)) {
            return;
        }

        Map input = (Map) recipe.get("input");
        Map output = (Map) recipe.get("output");
        RuntimeItemMapping.LegacyEntry furnaceInputEntry = itemMapping.fromIdentifier((String) input.get("id"));
        RuntimeItemMapping.LegacyEntry furnaceOutputEntry = itemMapping.fromIdentifier((String) output.get("id"));

        if (furnaceInputEntry != null && furnaceOutputEntry != null && furnaceInputEntry.getLegacyId() != 0 && furnaceOutputEntry.getLegacyId() != 0) {
            int inputDamage;
            if (input.containsKey("damage")) {
                inputDamage = ((Number) input.get("damage")).intValue();
                if (inputDamage == 32767) {
                    inputDamage = -1;
                }
            } else {
                inputDamage = furnaceInputEntry.getDamage();
            }
            int outputDamage;
            if (output.containsKey("damage")) {
                int rawOutputDamage = ((Number) output.get("damage")).intValue();
                outputDamage = (rawOutputDamage == 32767 || rawOutputDamage == -1) ? furnaceOutputEntry.getDamage() : rawOutputDamage;
            } else {
                outputDamage = furnaceOutputEntry.getDamage();
            }
            Item inputItem = Item.get(furnaceInputEntry.getLegacyId(), inputDamage, (Integer) input.getOrDefault("count", 1));
            Item outputItem = Item.get(furnaceOutputEntry.getLegacyId(), outputDamage, (Integer) output.getOrDefault("count", 1));

            switch (smeltingBlock) {
                case "furnace": {
                    FurnaceRecipe furnaceRecipe = new FurnaceRecipe(outputItem, inputItem);
                    double xp = furnaceXpConfig.getDouble(inputItem.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL) + ":" + inputItem.getDamage(), 0d);
                    if (xp != 0) {
                        this.setRecipeXp(furnaceRecipe, xp);
                    }
                    this.registerRecipe(furnaceRecipe);
                    break;
                }
                case "blast_furnace": {
                    BlastFurnaceRecipe furnaceRecipe = new BlastFurnaceRecipe(outputItem, inputItem);
                    double xp = furnaceXpConfig.getDouble(inputItem.getNamespaceId(ProtocolInfo.CURRENT_PROTOCOL) + ":" + inputItem.getDamage(), 0d);
                    if (xp != 0) {
                        this.setRecipeXp(furnaceRecipe, xp);
                    }
                    this.registerRecipe(furnaceRecipe);
                    break;
                }
                case "campfire":
                    this.registerRecipe(new CampfireRecipe(outputItem, inputItem));
                    break;
            }
        } else {
            log.trace("Unknown smelting recipe: {}", recipe);
        }
    }

    private static boolean needExpandLegacy(int id) {
        return id == BlockID.WOOL || id == BlockID.STONE || id == BlockID.PLANKS || id == BlockID.WOODEN_SLAB || id == ItemID.COAL;
    }

    @SuppressWarnings("unchecked")
    private void buildShapelessRecipeItemTagOverrides(RuntimeItemMapping itemMapping, List<Map> input, Item outputItem, String toReplaceTag, String replaceOtherTagKey, String replaceOtherTagValue) {
        Set<String> tags = ItemTag.getItemSet(toReplaceTag);
        if (tags.isEmpty()) {
            log.trace("Unknown item tag: {}", toReplaceTag);
            return;
        }

        top:
        for (String material : tags) {
            List<Item> sorted = new ArrayList<>();
            int expandLegacy = 0;

            for (Map<String, Object> ingredient : input) {
                Item inputItem;
                String type = (String) ingredient.get("type");

                if (!"default".equals(type)) {
                    if ("item_tag".equals(type)) {
                        String itemTag = (String) ingredient.get("itemTag");
                        if (!itemTag.equals(toReplaceTag)) {
                            if (itemTag.equals(replaceOtherTagKey)) {
                                RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromIdentifier(replaceOtherTagValue);
                                if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                                    log.trace("Unknown multi item tag input: {}", replaceOtherTagValue);
                                    continue top;
                                }
                                inputItem = Item.get(legacyEntry.getLegacyId(), legacyEntry.getDamage(), (Integer) ingredient.getOrDefault("count", 1));
                                if (needExpandLegacy(legacyEntry.getLegacyId())) {
                                    expandLegacy = legacyEntry.getLegacyId();
                                }
                            } else {
                                buildShapelessRecipeItemTagOverrides(itemMapping, input, outputItem, itemTag, toReplaceTag, material);
                                continue top;
                            }
                        } else {
                            RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromIdentifier(material);
                            if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                                log.trace("Unknown item tag input: {}", material);
                                continue top;
                            }
                            inputItem = Item.get(legacyEntry.getLegacyId(), legacyEntry.getDamage(), (Integer) ingredient.getOrDefault("count", 1));
                            if (needExpandLegacy(legacyEntry.getLegacyId())) {
                                expandLegacy = legacyEntry.getLegacyId();
                            }
                        }
                    } else {
                        log.trace("Unsupported shapeless ingredient type: {}", type);
                        continue top;
                    }
                } else {
                    RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromRuntime((int) ingredient.get("itemId"));
                    if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                        log.trace("Unknown shapeless input: {}", input);
                        continue top;
                    }
                    int aux = (int) ingredient.getOrDefault("auxValue", 0);
                    if (aux == 32767) {
                        aux = -1;
                    } else if (aux == 0) {
                        aux = legacyEntry.getDamage();
                    }
                    inputItem = Item.get(legacyEntry.getLegacyId(), aux, (Integer) ingredient.getOrDefault("count", 1));
                }

                sorted.add(inputItem);
            }

            sorted.sort(recipeComparator);

            if (expandLegacy != 0) {
                int lastMeta = expandLegacy == ItemID.COAL ? 1 : expandLegacy == BlockID.PLANKS || expandLegacy == BlockID.WOODEN_SLAB ? 5 : expandLegacy == BlockID.STONE ? 6 : 15;
                for (int meta = 0; meta <= lastMeta; meta++) {
                    for (Item item : sorted) {
                        if (item.getId() == expandLegacy) {
                            item.setDamage(meta);
                        }
                    }
                    this.registerRecipe(new ShapelessRecipe(null, 0, outputItem, sorted));
                }
            } else {
                this.registerRecipe(new ShapelessRecipe(null, 0, outputItem, sorted));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void buildShapedRecipeItemTagOverrides(RuntimeItemMapping itemMapping, Map<String, Map<String, Object>> input, String[] shape, Item outputItem, String toReplaceTag, String replaceOtherTagKey, String replaceOtherTagValue) {
        Set<String> tags = ItemTag.getItemSet(toReplaceTag);
        if (tags.isEmpty()) {
            log.trace("Unknown item tag: {}", toReplaceTag);
            return;
        }

        top:
        for (String material : tags) {
            Map<Character, Item> ingredients = new CharObjectHashMap<>();
            int expandLegacy = 0;

            for (Map.Entry<String, Map<String, Object>> ingredientEntry : input.entrySet()) {
                Item inputItem;
                String type = (String) ingredientEntry.getValue().get("type");

                if (!"default".equals(type)) {
                    if ("item_tag".equals(type)) {
                        String itemTag = (String) ingredientEntry.getValue().get("itemTag");
                        if (!itemTag.equals(toReplaceTag)) {
                            if (itemTag.equals(replaceOtherTagKey)) {
                                RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromIdentifier(replaceOtherTagValue);
                                if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                                    log.trace("Unknown multi item tag input: {}", replaceOtherTagValue);
                                    continue top;
                                }
                                inputItem = Item.get(legacyEntry.getLegacyId(), legacyEntry.getDamage(), (Integer) ingredientEntry.getValue().getOrDefault("count", 1));
                                if (needExpandLegacy(legacyEntry.getLegacyId())) {
                                    expandLegacy = legacyEntry.getLegacyId();
                                }
                            } else {
                                buildShapedRecipeItemTagOverrides(itemMapping, input, shape, outputItem, itemTag, toReplaceTag, material);
                                continue top;
                            }
                        } else {
                            RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromIdentifier(material);
                            if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                                log.trace("Unknown item tag input: {}", material);
                                continue top;
                            }
                            inputItem = Item.get(legacyEntry.getLegacyId(), legacyEntry.getDamage(), (Integer) ingredientEntry.getValue().getOrDefault("count", 1));
                            if (needExpandLegacy(legacyEntry.getLegacyId())) {
                                expandLegacy = legacyEntry.getLegacyId();
                            }
                        }
                    } else {
                        log.trace("Unsupported shaped ingredient type: {}", type);
                        continue top;
                    }
                } else {
                    RuntimeItemMapping.LegacyEntry legacyEntry = itemMapping.fromRuntime((int) ingredientEntry.getValue().get("itemId"));
                    if (legacyEntry == null || legacyEntry.getLegacyId() == 0) {
                        log.trace("Unknown shaped input: {}", input);
                        continue top;
                    }
                    int aux = (int) ingredientEntry.getValue().getOrDefault("auxValue", 0);
                    if (aux == 32767) {
                        aux = -1;
                    } else if (aux == 0) {
                        aux = legacyEntry.getDamage();
                    }
                    inputItem = Item.get(legacyEntry.getLegacyId(), aux, (Integer) ingredientEntry.getValue().getOrDefault("count", 1));
                }

                ingredients.put(ingredientEntry.getKey().charAt(0), inputItem);
            }

            if (expandLegacy != 0) {
                int lastMeta = expandLegacy == ItemID.COAL ? 1 : expandLegacy == BlockID.PLANKS || expandLegacy == BlockID.WOODEN_SLAB ? 5 : expandLegacy == BlockID.STONE ? 6 : 15;
                for (int meta = 0; meta <= lastMeta; meta++) {
                    for (Item item : ingredients.values()) {
                        if (item.getId() == expandLegacy) {
                            item.setDamage(meta);
                        }
                    }
                    this.registerRecipe(new ShapedRecipe(null, 0, outputItem, shape, ingredients, Collections.emptyList()));
                }
            } else {
                this.registerRecipe(new ShapedRecipe(null, 0, outputItem, shape, ingredients, Collections.emptyList()));
            }
        }
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
        if (protocol >= ProtocolInfo.v1_20_0_23) {
            for (SmithingRecipe recipe : this.getSmithingRecipes().values()) {
                if (recipe.getIngredient().isSupportedOn(protocol)
                        && recipe.getEquipment().isSupportedOn(protocol)
                        && recipe.getTemplate().isSupportedOn(protocol)
                        && recipe.getResult().isSupportedOn(protocol)) {
                    pk.addShapelessRecipe(recipe);
                }
            }
        }
        for (StonecutterRecipe recipe : this.getStonecutterRecipes()) {
            if (recipe.getIngredient().isSupportedOn(protocol) && recipe.getResult().isSupportedOn(protocol)) {
                pk.addStonecutterRecipe(recipe);
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
        packet944 = null;
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

        if (protocol >= GameVersion.V1_26_10.getProtocol()) {
            if (packet944 == null) {
                packet944 = packetFor(GameVersion.V1_26_10);
            }
            return packet944;
        } else if (protocol >= GameVersion.V1_21_130_28.getProtocol()) {
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

    public List<StonecutterRecipe> getStonecutterRecipes() {
        return stonecutterRecipes;
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

    public void registerStonecutterRecipe(StonecutterRecipe recipe) {
        recipe.setId(UUID.randomUUID());
        this.stonecutterRecipes.add(recipe);
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

    @Nullable
    public StonecutterRecipe matchStonecutterRecipe(Item input, Item output) {
        if (input == null || output == null) return null;
        for (StonecutterRecipe recipe : this.stonecutterRecipes) {
            boolean ingredientMatch = recipe.getIngredient().equals(input, recipe.getIngredient().hasMeta(), false);
            boolean resultMatch = recipe.getResult().equals(output, recipe.getResult().hasMeta(), false);
            if (ingredientMatch && resultMatch) {
                return recipe;
            }
            // 仅输入匹配但输出不匹配时，打印第一个供调试
            if (ingredientMatch && !resultMatch) {
                log.debug("Stonecutter ingredient matched but result mismatched: recipe.result={}, output={}", recipe.getResult(), output);
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
