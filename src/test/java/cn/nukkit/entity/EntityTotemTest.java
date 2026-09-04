package cn.nukkit.entity;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTotem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTotemTest {

    @Test
    void recognizesCanonicalTotemIdWithoutDependingOnTheJavaSubclass() {
        Item restoredAsPlainItem = new Item(Item.TOTEM, 0, 1, "Totem of Undying");

        assertTrue(Entity.isTotem(restoredAsPlainItem));
        assertTrue(Entity.isTotem(new ItemTotem(0)));
        assertTrue(Entity.isTotem(new Item(Item.TOTEM, 0, 2, "Totem of Undying")));
    }

    @Test
    void rejectsEmptyAndDifferentItems() {
        assertFalse(Entity.isTotem(null));
        assertFalse(Entity.isTotem(new Item(Item.TOTEM, 0, 0, "Totem of Undying")));
        assertFalse(Entity.isTotem(new Item(Item.STONE, 0, 1, "Stone")));
    }
}
