package cn.nukkit.recipe.descriptor;

import cn.nukkit.item.Item;
import cn.nukkit.utils.BinaryStream;

public abstract class ItemDescriptor implements Cloneable {
    public abstract boolean putRecipe(BinaryStream stream, int protocol);

    @Override
    public boolean equals(Object entry) {
        if(this instanceof DefaultDescriptor item1) {
            Item item2;
            if(entry instanceof Item item3) {
                item2 = item3;
            } else if(entry instanceof DefaultDescriptor item4) {
                item2 = item4.getItem();
            } else {
                return false;
            }
            return item1.getItem().getNamespaceId().equals(item2.getNamespaceId()) && (item1.getItem().getDamage() == -1 || item2.getDamage() == -1 || item1.getItem().getDamage() == item2.getDamage());
        }
        return false;
    }

    public void putInvalidRecipe(BinaryStream stream, int protocol) {
        if(stream != null) {
            stream.putVarInt(0);
        }
    }
}
