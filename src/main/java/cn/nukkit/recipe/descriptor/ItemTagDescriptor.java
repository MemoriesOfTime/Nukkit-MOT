package cn.nukkit.recipe.descriptor;

import cn.nukkit.item.Item;
import cn.nukkit.item.material.tags.ItemTag;
import cn.nukkit.utils.BinaryStream;

public class ItemTagDescriptor extends ItemDescriptor {
    private final ItemTag itemTag;
    private final String id;

    public ItemTagDescriptor(ItemTag itemTag, String id) {
        this.itemTag = itemTag;
        this.id = id;
    }

    public ItemTag getItemTag() {
        return itemTag;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean putRecipe(BinaryStream stream, int protocol) {
        return new DefaultDescriptor(Item.get(1)).putRecipe(stream, protocol);
    }
}
