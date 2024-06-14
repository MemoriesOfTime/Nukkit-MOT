package cn.nukkit.inventory.data;

import cn.nukkit.item.Item;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Value;

import java.util.List;

@Value
public class RecipeUnlockingRequirement {

    public static final RecipeUnlockingRequirement INVALID = new RecipeUnlockingRequirement(UnlockingContext.NONE);
    public static final RecipeUnlockingRequirement ALWAYS_UNLOCKED = new RecipeUnlockingRequirement(UnlockingContext.ALWAYS_UNLOCKED);

    UnlockingContext context;
    List<Item> ingredients = new ObjectArrayList<>();

    public enum UnlockingContext {
        NONE,
        ALWAYS_UNLOCKED,
        PLAYER_IN_WATER,
        PLAYER_HAS_MANY_ITEMS;

        private static final UnlockingContext[] VALUES = values();

        public static UnlockingContext from(int id) {
            return VALUES[id];
        }
    }

    public boolean isInvalid() {
        return this.ingredients.isEmpty() && this.context.equals(UnlockingContext.NONE);
    }

}
