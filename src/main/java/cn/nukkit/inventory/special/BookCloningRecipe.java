package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.item.Item;

import java.util.List;

/**
 * @author glorydark
 * @date {2024/1/8} {16:04}
 */
public class BookCloningRecipe implements SpecialRecipe {

    public BookCloningRecipe(){}

    @Override
    public boolean canExecute(Player player, List<Item> inputs, Item outputItem) {
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
}
