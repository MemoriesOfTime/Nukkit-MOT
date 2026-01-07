package cn.nukkit.utils;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.Collection;
import java.util.UUID;

public class RecipeUtils {
    public static int getItemHash(Item item) {
        return getItemHash(item, item.getDamage());
    }

    public static int getItemHash(Item item, int meta) {
        int id = item.getId() == Item.STRING_IDENTIFIED_ITEM ? item.getNetworkId(ProtocolInfo.CURRENT_PROTOCOL) : item.getId();
        return (id << 12) | (meta & 0xfff);
    }

    public static UUID getMultiItemHash(Collection<Item> items) {
        BinaryStream stream = new BinaryStream(items.size() * 5);
        for (Item item : items) {
            stream.putVarInt(getFullItemHash(item)); //putVarInt 5 byte
        }
        return UUID.nameUUIDFromBytes(stream.getBuffer());
    }

    public static int getFullItemHash(Item item) {
        //return 31 * getItemHash(item) + item.getCount();
        return (RecipeUtils.getItemHash(item) << 6) | (item.getCount() & 0x3f);
    }


    public static int getPotionHash(Item ingredient, Item potion) {
        int ingredientHash = ((ingredient.getId() & 0x3FF) << 6) | (ingredient.getDamage() & 0x3F);
        int potionHash = ((potion.getId() & 0x3FF) << 6) | (potion.getDamage() & 0x3F);
        return ingredientHash << 16 | potionHash;
    }

    public static int getContainerHash(int ingredientId, int containerId) {
        //return (ingredientId << 9) | containerId;
        return (ingredientId << 15) | containerId;
    }
}

