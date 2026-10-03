package cn.nukkit.item;

import cn.nukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ItemFlintSteelTest {

    @Test
    void useOnEntityConsumesOneDurability() {
        ItemFlintSteel flintSteel = new ItemFlintSteel();

        flintSteel.useOn(mock(Entity.class));

        assertEquals(1, flintSteel.getDamage());
    }
}
