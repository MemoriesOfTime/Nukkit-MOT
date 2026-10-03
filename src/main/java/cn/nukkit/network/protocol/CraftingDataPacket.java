package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.data.RecipeUnlockingRequirement;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Nukkit Project Team
 */
@ToString
public class CraftingDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CRAFTING_DATA_PACKET;

    public static final String CRAFTING_TAG_CRAFTING_TABLE = "crafting_table";
    public static final String CRAFTING_TAG_CARTOGRAPHY_TABLE = "cartography_table";
    public static final String CRAFTING_TAG_STONECUTTER = "stonecutter";
    public static final String CRAFTING_TAG_FURNACE = "furnace";
    public static final String CRAFTING_TAG_CAMPFIRE = "campfire";
    public static final String CRAFTING_TAG_BLAST_FURNACE = "blast_furnace";
    public static final String CRAFTING_TAG_SMOKER = "smoker";
    public static final String CRAFTING_TAG_SMITHING_TABLE = "smithing_table";
    public static final int SMITHING_ARMOR_TRIM_NETWORK_ID = 1;

    private List<Recipe> entries = new ArrayList<>();
    private final List<StonecutterRecipe> stonecutterEntries = new ArrayList<>();
    private final List<BrewingRecipe> brewingEntries = new ArrayList<>();
    private final List<ContainerRecipe> containerEntries = new ArrayList<>();
    public boolean cleanRecipes = true;

    public void addShapelessRecipe(ShapelessRecipe... recipe) {
        Collections.addAll(entries, recipe);
    }

    public void addShapedRecipe(ShapedRecipe... recipe) {
        Collections.addAll(entries, recipe);
    }

    public void addFurnaceRecipe(FurnaceRecipe... recipe) {
        Collections.addAll(entries, recipe);
    }

    public void addBrewingRecipe(BrewingRecipe... recipe) {
        Collections.addAll(brewingEntries, recipe);
    }

    public void addMultiRecipe(MultiRecipe... recipe) {
        Collections.addAll(entries, recipe);
    }

    public void addStonecutterRecipe(StonecutterRecipe... recipe) {
        Collections.addAll(stonecutterEntries, recipe);
    }

    public void addContainerRecipe(ContainerRecipe... recipe) {
        Collections.addAll(containerEntries, recipe);
    }

    @Override
    public DataPacket clean() {
        entries = new ArrayList<>();
        stonecutterEntries.clear();
        return super.clean();
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_26_40) {
            this.encodeV2168();
            return;
        }
        int totalCount = entries.size();
        if (protocol >= 354) {
            totalCount += stonecutterEntries.size();
        }
        this.putUnsignedVarInt(protocol >= ProtocolInfo.v1_20_0_23 ? totalCount + 1 : totalCount);//1.20.0+ 有额外的smithing_trim

        if (protocol < 354) {
            BinaryStream writer = new BinaryStream();
            for (Object entry : entries) {
                int entryType = writeEntryLegacy(gameVersion, entry, writer);
                if (entryType >= 0) {
                    this.putVarInt(entryType);
                    this.put(writer.getBuffer());
                } else {
                    this.putVarInt(-1);
                }
                writer.reset();
            }
        } else {
            for (Recipe recipe : entries) {
                RecipeType networkType = recipe.getType();
                if ((networkType == RecipeType.FURNACE || networkType == RecipeType.FURNACE_DATA
                        || networkType == RecipeType.BLAST_FURNACE || networkType == RecipeType.BLAST_FURNACE_DATA)
                        && protocol >= ProtocolInfo.v1_26_20_26) {
                    networkType = RecipeType.SHAPELESS;
                }
                this.putVarInt(networkType.getNetworkType(protocol));
                switch (recipe.getType()) {
                    case SHAPELESS:
                    case SHULKER_BOX: // UserDataShapelessRecipe: same wire format as SHAPELESS
                        ShapelessRecipe shapeless = (ShapelessRecipe) recipe;
                        if (protocol >= 361) {
                            this.putString(shapeless.getRecipeId());
                        }
                        List<Item> ingredients = shapeless.getIngredientList();
                        this.putUnsignedVarInt(ingredients.size());
                        for (Item ingredient : ingredients) {
                            if (protocol < 361) {
                                this.putSlot(gameVersion, ingredient);
                            } else {
                                this.putRecipeIngredient(gameVersion, ingredient);
                            }
                        }
                        this.putUnsignedVarInt(1); // Results length
                        this.putSlot(gameVersion, shapeless.getResult(), protocol >= ProtocolInfo.v1_16_100);
                        this.putUUID(shapeless.getId());
                        if (protocol >= 354) {
                            this.putString(CRAFTING_TAG_CRAFTING_TABLE);
                            if (protocol >= 361) {
                                this.putVarInt(shapeless.getPriority());
                                if (protocol >= 407) {
                                    boolean isShulkerBox = recipe.getType() == RecipeType.SHULKER_BOX;
                                    if (protocol >= ProtocolInfo.v1_21_0
                                            && (!isShulkerBox || protocol >= ProtocolInfo.v1_21_40)) {
                                        this.writeRequirement(shapeless);
                                    }
                                    this.putUnsignedVarInt(shapeless.getNetworkId());
                                }
                            }
                        }
                        break;
                    case SMITHING_TRANSFORM:
                        SmithingRecipe smithing = (SmithingRecipe) recipe;
                        this.putString(smithing.getRecipeId());
                        if (protocol >= ProtocolInfo.v1_19_80) {
                            this.putRecipeIngredient(gameVersion, protocol >= ProtocolInfo.v1_20_0_23 ? smithing.getTemplate() : Item.AIR_ITEM); //template
                        }
                        this.putRecipeIngredient(gameVersion, smithing.getEquipment());
                        this.putRecipeIngredient(gameVersion, smithing.getIngredient());
                        this.putSlot(gameVersion, smithing.getResult(), true);
                        this.putString(CRAFTING_TAG_SMITHING_TABLE);
                        this.putUnsignedVarInt(smithing.getNetworkId());
                        break;
                    case SHAPED:
                        ShapedRecipe shaped = (ShapedRecipe) recipe;
                        if (protocol >= 361) {
                            this.putString(shaped.getRecipeId());
                        }
                        this.putVarInt(shaped.getWidth());
                        this.putVarInt(shaped.getHeight());

                        for (int z = 0; z < shaped.getHeight(); ++z) {
                            for (int x = 0; x < shaped.getWidth(); ++x) {
                                if (protocol < 361) {
                                    this.putSlot(gameVersion, shaped.getIngredient(x, z));
                                } else {
                                    this.putRecipeIngredient(gameVersion, shaped.getIngredient(x, z));
                                }
                            }
                        }
                        List<Item> outputs = new ArrayList<>();
                        outputs.add(shaped.getResult());
                        outputs.addAll(shaped.getExtraResults());
                        this.putUnsignedVarInt(outputs.size());
                        for (Item output : outputs) {
                            this.putSlot(gameVersion, output, protocol >= ProtocolInfo.v1_16_100);
                        }
                        this.putUUID(shaped.getId());
                        if (protocol >= 354) {
                            this.putString(CRAFTING_TAG_CRAFTING_TABLE);
                            if (protocol >= 361) {
                                this.putVarInt(shaped.getPriority());
                                if (this.protocol >= ProtocolInfo.v1_20_80) {
                                    this.putBoolean(shaped.isAssumeSymetry());
                                }
                                if (protocol >= 407) {
                                    if (protocol >= ProtocolInfo.v1_21_0) {
                                        this.writeRequirement(shaped);
                                    }
                                    this.putUnsignedVarInt(shaped.getNetworkId());
                                }
                            }
                        }
                        break;
                    case FURNACE:
                    case FURNACE_DATA:
                    case BLAST_FURNACE:
                    case BLAST_FURNACE_DATA:
                        FurnaceRecipe furnace = (FurnaceRecipe) recipe;
                        if (protocol >= ProtocolInfo.v1_26_20_26) {
                            this.putString(furnace.getRecipeId());
                            this.putUnsignedVarInt(1); // Ingredients length
                            this.putRecipeIngredient(gameVersion, furnace.getInput());
                            this.putUnsignedVarInt(1); // Results length
                            this.putSlot(gameVersion, furnace.getResult(), true);
                            this.putUUID(furnace.getId());
                            String craftingTag;
                            if (recipe instanceof SmokerRecipe) {
                                craftingTag = CRAFTING_TAG_SMOKER;
                            } else if (recipe instanceof BlastFurnaceRecipe) {
                                craftingTag = CRAFTING_TAG_BLAST_FURNACE;
                            } else {
                                craftingTag = CRAFTING_TAG_FURNACE;
                            }
                            this.putString(craftingTag);
                            this.putVarInt(0); // priority
                            this.putByte((byte) RecipeUnlockingRequirement.UnlockingContext.ALWAYS_UNLOCKED.ordinal());
                            this.putUnsignedVarInt(furnace.getNetworkId());
                        } else {
                            Item input = furnace.getInput();
                            int runtimeId;
                            int damage;
                            if (!input.hasMeta()) {
                                runtimeId = RuntimeItems.getMapping(gameVersion).toRuntime(input.getId(), 0).getRuntimeId();
                                damage = 0x7fff;
                            } else {
                                RuntimeItemMapping.RuntimeEntry runtimeEntry = RuntimeItems.getMapping(gameVersion).toRuntime(input.getId(), input.getDamage());
                                runtimeId = runtimeEntry.getRuntimeId();
                                damage = runtimeEntry.isHasDamage() ? 0 : input.getDamage();
                            }
                            this.putVarInt(runtimeId);
                            if (recipe.getType() == RecipeType.FURNACE_DATA || recipe.getType() == RecipeType.BLAST_FURNACE_DATA) {
                                this.putVarInt(damage);
                            }
                            this.putSlot(gameVersion, furnace.getResult(), protocol >= ProtocolInfo.v1_16_100);
                            if (protocol >= 354) {
                                if (recipe instanceof SmokerRecipe) {
                                    this.putString(CRAFTING_TAG_SMOKER);
                                } else if (recipe instanceof BlastFurnaceRecipe) {
                                    this.putString(CRAFTING_TAG_BLAST_FURNACE);
                                } else {
                                    this.putString(CRAFTING_TAG_FURNACE);
                                }
                            }
                        }
                        break;
                    case MULTI:
                        if (protocol >= ProtocolInfo.v1_16_0) { // ??
                            this.putUUID(((MultiRecipe) recipe).getId());
                            this.putUnsignedVarInt(((MultiRecipe) recipe).getNetworkId());
                            break;
                        }
                }
            }

            // Stonecutter recipes (encoded as SHAPELESS with "stonecutter" tag)
            for (StonecutterRecipe recipe : stonecutterEntries) {
                this.putVarInt(RecipeType.SHAPELESS.getNetworkType(protocol));
                if (protocol >= 361) {
                    this.putString(recipe.getRecipeId());
                }
                this.putUnsignedVarInt(1); // 1 ingredient
                if (protocol < 361) {
                    this.putSlot(gameVersion, recipe.getIngredient());
                } else {
                    this.putRecipeIngredient(gameVersion, recipe.getIngredient());
                }
                this.putUnsignedVarInt(1); // 1 result
                this.putSlot(gameVersion, recipe.getResult(), protocol >= ProtocolInfo.v1_16_100);
                this.putUUID(recipe.getId());
                if (protocol >= 354) {
                    this.putString(CRAFTING_TAG_STONECUTTER);
                    if (protocol >= 361) {
                        this.putVarInt(recipe.getPriority());
                        if (protocol >= 407) {
                            if (protocol >= ProtocolInfo.v1_21_0) {
                                this.putByte((byte) RecipeUnlockingRequirement.UnlockingContext.ALWAYS_UNLOCKED.ordinal());
                            }
                            this.putUnsignedVarInt(recipe.getNetworkId());
                        }
                    }
                }
            }

            if (protocol >= ProtocolInfo.v1_20_0_23) {
                // Identical smithing_trim recipe sent by BDS that uses tag-descriptors, as the client seems to ignore the
                // approach of using many default-descriptors (which we do for smithing_transform)
                this.putVarInt(RecipeType.SMITHING_TRIM.getNetworkType(protocol));
                this.putString("minecraft:smithing_armor_trim"); // Recipe
                this.putRecipeIngredient(protocol, "minecraft:trim_templates", 1);
                this.putRecipeIngredient(protocol, "minecraft:trimmable_armors", 1);
                this.putRecipeIngredient(protocol, "minecraft:trim_materials", 1);
                this.putString(CRAFTING_TAG_SMITHING_TABLE);
                this.putUnsignedVarInt(SMITHING_ARMOR_TRIM_NETWORK_ID); // Reserved by CraftingManager
            }

            if (protocol >= 388) {
                this.putUnsignedVarInt(this.brewingEntries.size());
                for (BrewingRecipe recipe : brewingEntries) {
                    if (protocol >= 407) {
                        this.putVarInt(recipe.getInput().getNetworkId(gameVersion));
                    }
                    this.putVarInt(recipe.getInput().getDamage());
                    this.putVarInt(recipe.getIngredient().getNetworkId(gameVersion));
                    if (protocol >= 407) {
                        this.putVarInt(recipe.getIngredient().getDamage());
                        this.putVarInt(recipe.getResult().getNetworkId(gameVersion));
                    }
                    this.putVarInt(recipe.getResult().getDamage());
                }

                this.putUnsignedVarInt(this.containerEntries.size());
                for (ContainerRecipe recipe : containerEntries) {
                    this.putVarInt(recipe.getInput().getNetworkId(gameVersion));
                    this.putVarInt(recipe.getIngredient().getNetworkId(gameVersion));
                    this.putVarInt(recipe.getResult().getNetworkId(gameVersion));
                }

                if (protocol >= ProtocolInfo.v1_17_30) {
                    this.putUnsignedVarInt(0); // Material reducers size
                }
            }
        }

        this.putBoolean(cleanRecipes);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    protected void writeRequirement(CraftingRecipe recipe) {
        this.putByte((byte) recipe.getRequirement().getContext().ordinal());
        if (recipe.getRequirement().getContext().equals(RecipeUnlockingRequirement.UnlockingContext.NONE)) {
            this.putArray(recipe.getRequirement().getIngredients(), (ingredient) -> this.putRecipeIngredient(gameVersion, ingredient));
        }
    }

    /**
     * v2168+ 版本的解锁条件写入 / v2168+ requirement writer.
     * 旧版: byte context + 条件性 ingredients
     * v2168: VarInt context + 显式 boolean 标记 + 条件性 ingredients
     */
    private void writeRequirementV2168(CraftingRecipe recipe) {
        RecipeUnlockingRequirement requirement = recipe.getRequirement();
        RecipeUnlockingRequirement.UnlockingContext context = requirement.getContext();
        this.putVarInt(context.ordinal()); // changed: byte -> VarInt
        boolean present = context.equals(RecipeUnlockingRequirement.UnlockingContext.NONE);
        this.putBoolean(present); // always-present flag
        if (present) {
            this.putArray(requirement.getIngredients(), (ingredient) -> this.putRecipeIngredient(gameVersion, ingredient));
        }
    }

    /**
     * v2168 (1.26.40) 编码: 将 entries 按 RecipeType 分桶后写入 10 个独立的类型化数组,
     * 每个数组以 VarUInt count 开头, 数组元素本身不再带 networkType 前缀.
     *
     * v2168 encoding: partitions entries into 10 typed arrays (count prefix per array,
     * entries do NOT carry the leading networkType varint since the array implies the type).
     */
    private void encodeV2168() {
        // 1. Partition entries by type / 按 RecipeType 分桶
        List<Recipe> shapedData = new ArrayList<>();
        List<Recipe> shapelessData = new ArrayList<>();
        List<Recipe> multiData = new ArrayList<>();
        List<Recipe> shapelessUserData = new ArrayList<>(); // ShulkerBox
        List<Recipe> smithingTransformData = new ArrayList<>();
        for (Recipe recipe : entries) {
            RecipeType type = recipe.getType();
            if (type == RecipeType.SHAPED) {
                shapedData.add(recipe);
            } else if (type == RecipeType.SHAPELESS) {
                shapelessData.add(recipe);
            } else if (type == RecipeType.SHULKER_BOX) {
                shapelessUserData.add(recipe);
            } else if (type == RecipeType.SMITHING_TRANSFORM) {
                smithingTransformData.add(recipe);
            } else if (type == RecipeType.MULTI) {
                multiData.add(recipe);
            } else if ((type == RecipeType.FURNACE || type == RecipeType.FURNACE_DATA)) {
                shapelessData.add(recipe);
            } else {
                shapelessData.add(recipe);
            }
        }

        // 2. shapedData
        this.putUnsignedVarInt(shapedData.size());
        for (Recipe recipe : shapedData) {
            this.writeShapedRecipeV2168((ShapedRecipe) recipe);
        }

        // 3. shapelessData (含 stonecutter / furnace / smithing 之外的普通无序配方)
        // shapelessData (also stonecutter / furnace encoded as shapeless since v1_26_20_26)
        this.putUnsignedVarInt(shapelessData.size() + stonecutterEntries.size());
        for (Recipe recipe : shapelessData) {
            if (recipe instanceof ShapelessRecipe shapeless) {
                boolean isShulker = shapeless.getType() == RecipeType.SHULKER_BOX;
                this.writeShapelessRecipeV2168(shapeless, isShulker, CRAFTING_TAG_CRAFTING_TABLE);
            } else if (recipe instanceof FurnaceRecipe furnace) {
                this.writeFurnaceRecipeV2168(furnace);
            } else {
                throw new IllegalArgumentException("Unexpected recipe type in shapeless bucket: " + recipe.getType());
            }
        }
        for (StonecutterRecipe recipe : stonecutterEntries) {
            this.putString(recipe.getRecipeId());
            List<Item> ingredients = Collections.singletonList(recipe.getIngredient());
            this.putUnsignedVarInt(ingredients.size());
            for (Item ingredient : ingredients) {
                this.putRecipeIngredient(gameVersion, ingredient);
            }
            this.putUnsignedVarInt(1); // Results length
            this.putSlot(gameVersion, recipe.getResult(), true);
            this.putUUID(recipe.getId());
            this.putString(CRAFTING_TAG_STONECUTTER);
            this.putVarInt(recipe.getPriority());
            this.putBoolean(false); // requirementPresent (v2168+: stonecutter has no requirement)
            this.putUnsignedVarInt(recipe.getNetworkId());
        }

        // 4. multiData
        this.putUnsignedVarInt(multiData.size());
        for (Recipe recipe : multiData) {
            MultiRecipe multi = (MultiRecipe) recipe;
            this.putUUID(multi.getId());
            this.putUnsignedVarInt(multi.getNetworkId());
        }

        // 5. shapelessUserData (ShulkerBox) - 与 shapelessData 线格式一致
        this.putUnsignedVarInt(shapelessUserData.size());
        for (Recipe recipe : shapelessUserData) {
            this.writeShapelessRecipeV2168((ShapelessRecipe) recipe, true, CRAFTING_TAG_CRAFTING_TABLE);
        }

        // 6. shapelessChemistryData (rarely used; chemistry 未单独识别, 此处留空)
        this.putUnsignedVarInt(0);

        // 7. shapedChemistryData (rarely used; chemistry 未单独识别, 此处留空)
        this.putUnsignedVarInt(0);

        // 8. smithingTransformData
        this.putUnsignedVarInt(smithingTransformData.size());
        for (Recipe recipe : smithingTransformData) {
            SmithingRecipe smithing = (SmithingRecipe) recipe;
            this.putString(smithing.getRecipeId());
            this.putRecipeIngredient(gameVersion, smithing.getTemplate()); // template
            this.putRecipeIngredient(gameVersion, smithing.getEquipment());
            this.putRecipeIngredient(gameVersion, smithing.getIngredient());
            this.putSlot(gameVersion, smithing.getResult(), true);
            this.putString(CRAFTING_TAG_SMITHING_TABLE);
            this.putUnsignedVarInt(smithing.getNetworkId());
        }

        // 9. smithingTrimData (固定 1 条, 复用 BDS 的 tag-descriptor 写法)
        this.putUnsignedVarInt(1);
        this.putString("minecraft:smithing_armor_trim"); // Recipe
        this.putRecipeIngredient(protocol, "minecraft:trim_templates", 1);
        this.putRecipeIngredient(protocol, "minecraft:trimmable_armors", 1);
        this.putRecipeIngredient(protocol, "minecraft:trim_materials", 1);
        this.putString(CRAFTING_TAG_SMITHING_TABLE);
        this.putUnsignedVarInt(SMITHING_ARMOR_TRIM_NETWORK_ID); // Reserved by CraftingManager

        // 10. potionMixData (brewingEntries)
        this.putUnsignedVarInt(this.brewingEntries.size());
        for (BrewingRecipe recipe : brewingEntries) {
            this.putVarInt(recipe.getInput().getNetworkId(gameVersion));
            this.putVarInt(recipe.getInput().getDamage());
            this.putVarInt(recipe.getIngredient().getNetworkId(gameVersion));
            this.putVarInt(recipe.getIngredient().getDamage());
            this.putVarInt(recipe.getResult().getNetworkId(gameVersion));
            this.putVarInt(recipe.getResult().getDamage());
        }

        // containerMixData
        this.putUnsignedVarInt(this.containerEntries.size());
        for (ContainerRecipe recipe : containerEntries) {
            this.putVarInt(recipe.getInput().getNetworkId(gameVersion));
            this.putVarInt(recipe.getIngredient().getNetworkId(gameVersion));
            this.putVarInt(recipe.getResult().getNetworkId(gameVersion));
        }

        // materialReducers
        this.putUnsignedVarInt(0);

        // cleanRecipes
        this.putBoolean(cleanRecipes);
    }

    /**
     * v2168 shaped 配方写入.
     * 变更: ingredients 由 width*height 直写改为 VarUInt count 前缀的数组;
     * priority + assumeSymmetry 之后, 显式写入 requirementPresent boolean, 仅 SHAPED 类型为 true.
     *
     * v2168 shaped writer. Ingredients now use a VarUInt length prefix;
     * an explicit boolean (true only for SHAPED type) precedes the requirement block.
     */
    private void writeShapedRecipeV2168(ShapedRecipe shaped) {
        this.putString(shaped.getRecipeId());
        this.putVarInt(shaped.getWidth());
        this.putVarInt(shaped.getHeight());

        // v2168: ingredients 数组带长度前缀 / length-prefixed ingredient array
        int ingredientCount = shaped.getWidth() * shaped.getHeight();
        this.putUnsignedVarInt(ingredientCount);
        for (int z = 0; z < shaped.getHeight(); ++z) {
            for (int x = 0; x < shaped.getWidth(); ++x) {
                this.putRecipeIngredient(gameVersion, shaped.getIngredient(x, z));
            }
        }

        List<Item> outputs = new ArrayList<>();
        outputs.add(shaped.getResult());
        outputs.addAll(shaped.getExtraResults());
        this.putUnsignedVarInt(outputs.size());
        for (Item output : outputs) {
            this.putSlot(gameVersion, output, true);
        }
        this.putUUID(shaped.getId());
        this.putString(CRAFTING_TAG_CRAFTING_TABLE);
        this.putVarInt(shaped.getPriority());
        this.putBoolean(shaped.isAssumeSymetry());
        this.putBoolean(true); // requirementPresent (SHAPED -> always write requirement)
        this.writeRequirementV2168(shaped);
        this.putUnsignedVarInt(shaped.getNetworkId());
    }

    /**
     * v2168 shapeless 配方写入.
     * 变更: 在 requirement 之前显式写入 boolean 标记 (SHAPELESS 或 SHULKER_BOX 时为 true).
     *
     * v2168 shapeless writer. An explicit boolean precedes the requirement block.
     */
    private void writeShapelessRecipeV2168(ShapelessRecipe shapeless, boolean writeRequirement, String craftingTag) {
        this.putString(shapeless.getRecipeId());
        List<Item> ingredients = shapeless.getIngredientList();
        this.putUnsignedVarInt(ingredients.size());
        for (Item ingredient : ingredients) {
            this.putRecipeIngredient(gameVersion, ingredient);
        }
        this.putUnsignedVarInt(1); // Results length
        this.putSlot(gameVersion, shapeless.getResult(), true);
        this.putUUID(shapeless.getId());
        this.putString(craftingTag);
        this.putVarInt(shapeless.getPriority());
        this.putBoolean(writeRequirement); // requirementPresent
        if (writeRequirement) {
            this.writeRequirementV2168(shapeless);
        }
        this.putUnsignedVarInt(shapeless.getNetworkId());
    }

    /**
     * v2168 熔炉配方写入 (与 shapeless 同线格式, tag 区分炉型).
     *
     * v2168 furnace writer (shapeless wire layout, tag distinguishes furnace type).
     */
    private void writeFurnaceRecipeV2168(FurnaceRecipe furnace) {
        this.putString(furnace.getRecipeId());
        this.putUnsignedVarInt(1); // Ingredients length
        this.putRecipeIngredient(gameVersion, furnace.getInput());
        this.putUnsignedVarInt(1); // Results length
        this.putSlot(gameVersion, furnace.getResult(), true);
        this.putUUID(furnace.getId());
        this.putString(furnace instanceof SmokerRecipe ? CRAFTING_TAG_SMOKER : furnace instanceof BlastFurnaceRecipe ? CRAFTING_TAG_BLAST_FURNACE : CRAFTING_TAG_FURNACE);
        this.putVarInt(0); // priority
        this.putBoolean(false); // requirementPresent (furnace has no unlock requirement)
        this.putUnsignedVarInt(furnace.getNetworkId());
    }

    private int writeEntryLegacy(GameVersion gameVersion, Object entry, BinaryStream stream) {
        if (entry instanceof ShapelessRecipe) {
            return writeShapelessRecipeLegacy(gameVersion, ((ShapelessRecipe) entry), stream);
        } else if (entry instanceof ShapedRecipe) {
            return writeShapedRecipeLegacy(gameVersion, ((ShapedRecipe) entry), stream);
        } else if (entry instanceof FurnaceRecipe) {
            return writeFurnaceRecipeLegacy(gameVersion, ((FurnaceRecipe) entry), stream);
        }
        return -1;
    }

    private int writeShapelessRecipeLegacy(GameVersion gameVersion, ShapelessRecipe recipe, BinaryStream stream) {
        stream.putUnsignedVarInt(recipe.getIngredientCount());
        for (Item item : recipe.getIngredientList()) {
            stream.putSlot(gameVersion, item);
        }
        stream.putUnsignedVarInt(1);
        stream.putSlot(gameVersion, recipe.getResult());
        stream.putUUID(recipe.getId());
        return 0;
    }

    private int writeShapedRecipeLegacy(GameVersion gameVersion, ShapedRecipe recipe, BinaryStream stream) {
        stream.putVarInt(recipe.getWidth());
        stream.putVarInt(recipe.getHeight());
        for (int z = 0; z < recipe.getHeight(); ++z) {
            for (int x = 0; x < recipe.getWidth(); ++x) {
                stream.putSlot(gameVersion, recipe.getIngredient(x, z));
            }
        }
        stream.putUnsignedVarInt(1);
        stream.putSlot(gameVersion, recipe.getResult());
        stream.putUUID(recipe.getId());
        return 1;
    }

    private int writeFurnaceRecipeLegacy(GameVersion gameVersion, FurnaceRecipe recipe, BinaryStream stream) {
        if (recipe.getInput().hasMeta()) {
            stream.putVarInt(recipe.getInput().getId());
            stream.putVarInt(recipe.getInput().getDamage());
            stream.putSlot(gameVersion, recipe.getResult());
            return 3;
        } else {
            stream.putVarInt(recipe.getInput().getId());
            stream.putSlot(gameVersion, recipe.getResult());
            return 2;
        }
    }
}
