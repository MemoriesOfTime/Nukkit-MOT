package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.inventory.ShapedRecipe;
import cn.nukkit.item.Item;

import java.util.*;

/**
 * @author glorydark
 * @date {2024/1/8} {16:04}
 */
public class BookCloningRecipe extends MultiRecipe {

    public BookCloningRecipe(){
        super(UUID.fromString(TYPE_BOOK_CLONING));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        // Processing the checks about the inputs and outputItem
        if (inputs.size() == 2) {
            Item item1 = inputs.get(0);
            Item item2 = inputs.get(1);
            if (item1.getId() == Item.BOOK_AND_QUILL && item2.getId() == Item.WRITTEN_BOOK) {
                return item2.hasCompoundTag();
            } else if (item2.getId() == Item.BOOK_AND_QUILL && item1.getId() == Item.WRITTEN_BOOK) {
                return item1.hasCompoundTag();
            }
        }
        return false;
    }

    @Override
    public Recipe toRecipe(Player player, Item outputItem, List<Item> inputs) {
        Item item1 = inputs.get(0);
        Item item2 = inputs.get(1);
        Item saveInput;
        Map<Character, Item> map = new LinkedHashMap<>();
        if (item2.getId() == Item.WRITTEN_BOOK) {
            saveInput = item2;
            map.put("B".toCharArray()[0], item1);
        } else {
            saveInput = item1;
            map.put("B".toCharArray()[0], item2);
        }

        return new ShapedRecipe(outputItem, new String[]{"B"}, map, Collections.singletonList(saveInput));
    }
}
