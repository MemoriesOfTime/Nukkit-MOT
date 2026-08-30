package cn.nukkit.item;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemTotemStackLimitTest {

    @Test
    void plainItemWithTotemIdStillHasVanillaStackLimit() {
        Item fallback = new Item(Item.TOTEM, 0, 2, "Totem of Undying");

        assertEquals(1, fallback.getMaxStackSize());
    }

    @Test
    void persistedTotemOverstackSurvivesUntilInventoryMigration() {
        CompoundTag persisted = item(Item.TOTEM, 2);

        Item decoded = NBTIO.getItemHelper(persisted);

        assertEquals(Item.TOTEM, decoded.getId());
        assertEquals(2, decoded.getCount());
        assertEquals(2, persisted.getByte("Count"));
    }

    @Test
    void otherMalformedStacksKeepTheDefensiveClamp() {
        Item decoded = NBTIO.getItemHelper(item(Item.DIAMOND, 65));

        assertEquals(64, decoded.getCount());
    }

    private static CompoundTag item(int id, int count) {
        return new CompoundTag().putByte("Count", count).putShort("Damage", 0).putShort("id", id);
    }
}
