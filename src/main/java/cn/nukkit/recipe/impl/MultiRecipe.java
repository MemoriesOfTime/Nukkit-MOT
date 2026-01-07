package cn.nukkit.recipe.impl;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.recipe.Recipe;
import cn.nukkit.recipe.RecipeRegistry;
import cn.nukkit.recipe.RecipeType;
import cn.nukkit.recipe.descriptor.ItemDescriptor;

import java.util.Collection;
import java.util.UUID;

public class MultiRecipe implements Recipe {

    private final UUID id;

    private final int networkId;

    public static final UUID TYPE_REPAIR_ITEM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID TYPE_MAP_EXTENDING = UUID.fromString("D392B075-4BA1-40AE-8789-AF868D56F6CE");
    public static final UUID TYPE_MAP_CLONING = UUID.fromString("85939755-BA10-4D9D-A4CC-EFB7A8E943C4");
    public static final UUID TYPE_MAP_UPGRADING = UUID.fromString("AECD2294-4B94-434B-8667-4499BB2C9327");
    public static final UUID TYPE_BOOK_CLONING = UUID.fromString("D1CA6B84-338E-4F2F-9C6B-76CC8B4BD98D");
    public static final UUID TYPE_DECORATED_POT_RECIPE = UUID.fromString("685a742a-c42e-4a4e-88ea-5eb83fc98e5b");

    public static final UUID TYPE_BANNER_DUPLICATE = UUID.fromString("B5C5D105-75A2-4076-AF2B-923EA2BF4BF0");
    public static final UUID TYPE_BANNER_ADD_PATTERN = UUID.fromString("D81AAEAF-E172-4440-9225-868DF030D27B");
    public static final UUID TYPE_FIREWORKS = UUID.fromString("00000000-0000-0000-0000-000000000002"); // already done by recipe json files

    // TODO: Unused. implement when cartography table will work
    public static final UUID TYPE_MAP_EXTENDING_CARTOGRAPHY = UUID.fromString("8B36268C-1829-483C-A0F1-993B7156A8F2");
    public static final UUID TYPE_MAP_UPGRADING_CARTOGRAPHY = UUID.fromString("98C84B38-1085-46BD-B1CE-DD38C159E6CC");
    public static final UUID TYPE_MAP_CLONING_CARTOGRAPHY = UUID.fromString("442D85ED-8272-4543-A6F1-418F90DED05D");
    public static final UUID TYPE_MAP_LOCKING_CARTOGRAPHY = UUID.fromString("602234E4-CAC1-4353-8BB7-B1EBFF70024B");

    public MultiRecipe(UUID id) {
        this.id = id;
        this.networkId = ++RecipeRegistry.NEXT_NETWORK_ID;
    }

    @Override
    public Item getResult() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecipeType getType() {
        return RecipeType.MULTI;
    }

    public UUID getId() {
        return this.id;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public boolean canExecute(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        return false;
    }

    @Override
    public boolean isValidRecipe(int protocol) {
        return true;
    }

    public Recipe toRecipe(Item outputItem, Collection<ItemDescriptor> inputs) {
        return new ShapelessRecipe(outputItem, inputs);
    }
}
