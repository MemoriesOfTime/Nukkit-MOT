package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultiRecipe implements Recipe {

    private final UUID id;

    private final int networkId;

    public static final String TYPE_REPAIR_ITEM = "00000000-0000-0000-0000-000000000001";
    public static final String TYPE_MAP_EXTENDING = "D392B075-4BA1-40AE-8789-AF868D56F6CE";
    public static final String TYPE_MAP_CLONING = "85939755-BA10-4D9D-A4CC-EFB7A8E943C4";
    public static final String TYPE_MAP_UPGRADING = "AECD2294-4B94-434B-8667-4499BB2C9327";
    public static final String TYPE_BOOK_CLONING = "D1CA6B84-338E-4F2F-9C6B-76CC8B4BD98D";
    public static final String TYPE_DECORATED_POT_RECIPE = "685a742a-c42e-4a4e-88ea-5eb83fc98e5b";

    public static final String TYPE_BANNER_DUPLICATE = "B5C5D105-75A2-4076-AF2B-923EA2BF4BF0";
    public static final String TYPE_BANNER_ADD_PATTERN = "D81AAEAF-E172-4440-9225-868DF030D27B";
    public static final String TYPE_FIREWORKS = "00000000-0000-0000-0000-000000000002"; // already done by recipe json files

    public static final String TYPE_MAP_EXTENDING_CARTOGRAPHY = "8B36268C-1829-483C-A0F1-993B7156A8F2";
    public static final String TYPE_MAP_UPGRADING_CARTOGRAPHY = "98C84B38-1085-46BD-B1CE-DD38C159E6CC";
    public static final String TYPE_MAP_CLONING_CARTOGRAPHY = "442D85ED-8272-4543-A6F1-418F90DED05D";
    public static final String TYPE_MAP_LOCKING_CARTOGRAPHY = "602234E4-CAC1-4353-8BB7-B1EBFF70024B";

    public static final List<String> unsupportedRecipes = new ArrayList<>() {
        {
            this.add(TYPE_MAP_EXTENDING);
            this.add(TYPE_MAP_CLONING);
            this.add(TYPE_MAP_UPGRADING);
            this.add(TYPE_DECORATED_POT_RECIPE);
            this.add(TYPE_BANNER_DUPLICATE);
            this.add(TYPE_BANNER_ADD_PATTERN);
            this.add(TYPE_FIREWORKS);
            this.add(TYPE_MAP_EXTENDING_CARTOGRAPHY);
            this.add(TYPE_MAP_UPGRADING_CARTOGRAPHY);
            this.add(TYPE_MAP_CLONING_CARTOGRAPHY);
            this.add(TYPE_MAP_LOCKING_CARTOGRAPHY);
        }
    };

    public MultiRecipe(UUID id) {
        this.id = id;
        this.networkId = ++CraftingManager.NEXT_NETWORK_ID;
    }

    @Override
    public Item getResult() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerToCraftingManager(CraftingManager manager) {
        manager.registerMultiRecipe(this);
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

    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        return false;
    }

    public Recipe toRecipe(Item outputItem, List<Item> inputs) {
        return new ShapelessRecipe(outputItem, inputs);
    }
}
