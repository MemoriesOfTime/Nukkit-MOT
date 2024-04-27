package cn.nukkit.network.protocol;

import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
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
                int entryType = writeEntryLegacy(entry, writer);
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
                                this.putSlot(protocol, ingredient);
                            } else {
                                this.putRecipeIngredient(this.protocol, ingredient);
                            }
                        }
                        this.putUnsignedVarInt(1);
                        this.putSlot(protocol, shapeless.getResult(), protocol >= ProtocolInfo.v1_16_100);
                        this.putUUID(shapeless.getId());
                        if (protocol >= 354) {
                            this.putString(CRAFTING_TAG_CRAFTING_TABLE);
                            if (protocol >= 361) {
                                this.putVarInt(shapeless.getPriority());
                                if (protocol >= 407) {
                                    this.putUnsignedVarInt(shapeless.getNetworkId());
                                }
                            }
                        }
                        break;
                    case SMITHING_TRANSFORM:
                        SmithingRecipe smithing = (SmithingRecipe) recipe;
                        this.putString(smithing.getRecipeId());
                        if (protocol >= ProtocolInfo.v1_19_80) {
                            this.putRecipeIngredient(protocol, protocol >= ProtocolInfo.v1_20_0_23 ? smithing.getTemplate() : Item.AIR_ITEM); //template
                        }
                        this.putRecipeIngredient(protocol, smithing.getEquipment());
                        this.putRecipeIngredient(protocol, smithing.getIngredient());
                        this.putSlot(protocol, smithing.getResult(), true);
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
                                    this.putSlot(protocol, shaped.getIngredient(x, z));
                                } else {
                                    this.putRecipeIngredient(this.protocol, shaped.getIngredient(x, z));
                                }
                            }
                        }
                        List<Item> outputs = new ArrayList<>();
                        outputs.add(shaped.getResult());
                        outputs.addAll(shaped.getExtraResults());
                        this.putUnsignedVarInt(outputs.size());
                        for (Item output : outputs) {
                            this.putSlot(protocol, output, protocol >= ProtocolInfo.v1_16_100);
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
                                    this.putUnsignedVarInt(shaped.getNetworkId());
                                }
                            }
                        }
                        break;
                    case FURNACE:
                    case FURNACE_DATA:
                        FurnaceRecipe furnace = (FurnaceRecipe) recipe;
                        Item input = furnace.getInput();
                        this.putVarInt(input.getId());
                        if (recipe.getType() == RecipeType.FURNACE_DATA) {
                            this.putVarInt(input.getDamage());
                        }
                        this.putSlot(protocol, furnace.getResult(), protocol >= ProtocolInfo.v1_16_100);
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
                this.putString("minecraft:smithing_armor_trim");
                this.putRecipeIngredient(protocol, "minecraft:trim_templates", 1);
                this.putRecipeIngredient(protocol, "minecraft:trimmable_armors", 1);
                this.putRecipeIngredient(protocol, "minecraft:trim_materials", 1);
                this.putString(CRAFTING_TAG_SMITHING_TABLE);
                this.putUnsignedVarInt(1);
            }

            if (protocol >= 388) {
                this.putUnsignedVarInt(this.brewingEntries.size());
                for (BrewingRecipe recipe : brewingEntries) {
                    if (protocol >= 407) {
                        this.putVarInt(recipe.getInput().getNetworkId(protocol));
                    }
                    this.putVarInt(recipe.getInput().getDamage());
                    this.putVarInt(recipe.getIngredient().getNetworkId(protocol));
                    if (protocol >= 407) {
                        this.putVarInt(recipe.getIngredient().getDamage());
                        this.putVarInt(recipe.getResult().getNetworkId(protocol));
                    }
                    this.putVarInt(recipe.getResult().getDamage());
                }

                this.putUnsignedVarInt(this.containerEntries.size());
                for (ContainerRecipe recipe : containerEntries) {
                    this.putVarInt(recipe.getInput().getNetworkId(protocol));
                    this.putVarInt(recipe.getIngredient().getNetworkId(protocol));
                    this.putVarInt(recipe.getResult().getNetworkId(protocol));
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

    private int writeEntryLegacy(Object entry, BinaryStream stream) {
        if (entry instanceof ShapelessRecipe) {
            return writeShapelessRecipeLegacy(((ShapelessRecipe) entry), stream);
        } else if (entry instanceof ShapedRecipe) {
            return writeShapedRecipeLegacy(((ShapedRecipe) entry), stream);
        } else if (entry instanceof FurnaceRecipe) {
            return writeFurnaceRecipeLegacy(((FurnaceRecipe) entry), stream);
        }
        return -1;
    }

    private int writeShapelessRecipeLegacy(ShapelessRecipe recipe, BinaryStream stream) {
        stream.putUnsignedVarInt(recipe.getIngredientCount());
        for (Item item : recipe.getIngredientList()) {
            stream.putSlot(0, item);
        }
        stream.putUnsignedVarInt(1);
        stream.putSlot(0, recipe.getResult());
        stream.putUUID(recipe.getId());
        return 0;
    }

    private int writeShapedRecipeLegacy(ShapedRecipe recipe, BinaryStream stream) {
        stream.putVarInt(recipe.getWidth());
        stream.putVarInt(recipe.getHeight());
        for (int z = 0; z < recipe.getHeight(); ++z) {
            for (int x = 0; x < recipe.getWidth(); ++x) {
                stream.putSlot(0, recipe.getIngredient(x, z));
            }
        }
        stream.putUnsignedVarInt(1);
        stream.putSlot(0, recipe.getResult());
        stream.putUUID(recipe.getId());
        return 1;
    }

    private int writeFurnaceRecipeLegacy(FurnaceRecipe recipe, BinaryStream stream) {
        if (recipe.getInput().hasMeta()) {
            stream.putVarInt(recipe.getInput().getId());
            stream.putVarInt(recipe.getInput().getDamage());
            stream.putSlot(0, recipe.getResult());
            return 3;
        } else {
            stream.putVarInt(recipe.getInput().getId());
            stream.putSlot(0, recipe.getResult());
            return 2;
        }
    }
}
