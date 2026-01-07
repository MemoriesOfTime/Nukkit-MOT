package cn.nukkit.recipe.descriptor;

import cn.nukkit.utils.BinaryStream;

public abstract class ItemDescriptor implements Cloneable {
    public abstract boolean putRecipe(BinaryStream stream, int protocol);

    @Override
    public boolean equals(Object entry) {
        if(this instanceof ItemTagDescriptor tag && entry instanceof DefaultDescriptor item) {
            return tag.getItemTag().has(item.getItem().getItemType());
        }

        if(this instanceof DefaultDescriptor item1 && entry instanceof DefaultDescriptor item2) {
            return item1.getItem().getNamespaceId().equals(item2.getItem().getNamespaceId()) && (item1.getItem().getDamage() == -1 || item2.getItem().getDamage() == -1 || item1.getItem().getDamage() == item2.getItem().getDamage());
        }
        return false;
    }

    public void putInvalidRecipe(BinaryStream stream, int protocol) {
        if(stream != null) {
            stream.putVarInt(0);
        }
    }
}
