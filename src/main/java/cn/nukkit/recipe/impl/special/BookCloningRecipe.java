package cn.nukkit.recipe.impl.special;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.MultiRecipe;

import java.util.Collection;

public class BookCloningRecipe extends MultiRecipe {

    public BookCloningRecipe(){
        super(TYPE_BOOK_CLONING);
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        // Processing the checks about the inputs and outputItem
        ItemDescriptor[] items = inputs.toArray(new ItemDescriptor[0]);
        if (inputs.size() == 2 && items[0] instanceof DefaultDescriptor item1 && items[1] instanceof DefaultDescriptor item2) {
            if (item1.getItem().getId() == Item.WRITTEN_BOOK && item2.getItem().getId() == Item.BOOK_AND_QUILL) {
                return true;
            } else return item1.getItem().getId() == Item.BOOK_AND_QUILL && item2.getItem().getId() == Item.WRITTEN_BOOK;
        }
        return false;
    }
}