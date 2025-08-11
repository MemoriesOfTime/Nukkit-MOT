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

    private List<Recipe> entries = new ArrayList<>();
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

    public void addContainerRecipe(ContainerRecipe... recipe) {
        Collections.addAll(containerEntries, recipe);
    }

    @Override
    public DataPacket clean() {
        entries = new ArrayList<>();
        return super.clean();
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(protocol >= ProtocolInfo.v1_20_0_23 ? entries.size() + 1 : entries.size());//1.20.0+ 有额外的smithing_trim

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
                this.putVarInt(recipe.getType().getNetworkType(protocol));
                switch (recipe.getType()) {
                    case SHAPELESS:
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
                                    if (protocol >= ProtocolInfo.v1_21_0) {
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
                        FurnaceRecipe furnace = (FurnaceRecipe) recipe;
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
                        if (recipe.getType() == RecipeType.FURNACE_DATA) {
                            this.putVarInt(damage);
                        }
                        this.putSlot(gameVersion, furnace.getResult(), protocol >= ProtocolInfo.v1_16_100);
                        if (protocol >= 354) {
                            this.putString(CRAFTING_TAG_FURNACE);
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

            if (protocol >= ProtocolInfo.v1_20_0_23) {
                // Identical smithing_trim recipe sent by BDS that uses tag-descriptors, as the client seems to ignore the
                // approach of using many default-descriptors (which we do for smithing_transform)
                this.putVarInt(RecipeType.SMITHING_TRIM.getNetworkType(protocol));
                this.putString("minecraft:smithing_armor_trim"); // Recipe
                this.putRecipeIngredient(protocol, "minecraft:trim_templates", 1);
                this.putRecipeIngredient(protocol, "minecraft:trimmable_armors", 1);
                this.putRecipeIngredient(protocol, "minecraft:trim_materials", 1);
                this.putString(CRAFTING_TAG_SMITHING_TABLE);
                this.putUnsignedVarInt(1); // Network ID (hardcoded in CraftingManager)
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
