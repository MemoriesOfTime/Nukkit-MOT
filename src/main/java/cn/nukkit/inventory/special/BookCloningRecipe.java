package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;

import java.util.List;
import java.util.UUID;

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
            if (item1.getId() == Item.WRITTEN_BOOK && item2.getId() == Item.BOOK_AND_QUILL) {
                return true;
            } else return item1.getId() == Item.BOOK_AND_QUILL && item2.getId() == Item.WRITTEN_BOOK;
        }
        return false;
    }
}
