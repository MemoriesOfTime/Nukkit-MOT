package cn.nukkit.item.enchantment;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemSingletonEnchantmentFastPathTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void repeatedReadsSeeAnEnchantmentAddedRewrittenAndRemovedThroughTheLiveRoot() {
        Item bow = Item.get(Item.BOW).setNamedTag(new CompoundTag().putString("opaque", "keep"));
        CompoundTag retained = bow.getNamedTag();
        assertFalse(bow.hasEnchantments());
        assertEquals(0, bow.getEnchantmentLevel(Enchantment.ID_BOW_POWER));

        var enchantments = new ListTag<CompoundTag>("ench")
                .add(new CompoundTag().putShort("id", Enchantment.ID_BOW_POWER).putShort("lvl", 3));
        retained.putList(enchantments);
        assertTrue(bow.hasEnchantments());
        assertEquals(3, bow.getEnchantmentLevel(Enchantment.ID_BOW_POWER));

        enchantments.get(0).putShort("lvl", 99);
        boolean previousSafety = Server.getInstance().forcedSafetyEnchant;
        try {
            Server.getInstance().forcedSafetyEnchant = false;
            assertEquals(99, bow.getEnchantmentLevel(Enchantment.ID_BOW_POWER));
            Server.getInstance().forcedSafetyEnchant = true;
            assertEquals(Enchantment.getEnchantment(Enchantment.ID_BOW_POWER).getMaxLevel(),
                    bow.getEnchantmentLevel(Enchantment.ID_BOW_POWER));
        } finally {
            Server.getInstance().forcedSafetyEnchant = previousSafety;
        }
        retained.remove("ench");
        assertFalse(bow.hasEnchantments());
        assertEquals(0, bow.getEnchantmentLevel(Enchantment.ID_BOW_POWER));
    }

    @Test
    void singleEntrySnapshotSurvivesAReentrantListEditDuringLookup() {
        var enchantments = new ListTag<CompoundTag>("ench");
        CompoundTag removingTag = new CompoundTag() {
            @Override
            public int getShort(String name) {
                if (name.equals("id")) {
                    enchantments.remove(this);
                }
                return super.getShort(name);
            }
        };
        removingTag.putShort("id", Enchantment.ID_BOW_POWER).putShort("lvl", 3);
        enchantments.add(removingTag);
        Item bow = Item.get(Item.BOW).setNamedTag(new CompoundTag().putList(enchantments));

        assertEquals(3, bow.getEnchantmentLevel(Enchantment.ID_BOW_POWER));
        assertTrue(enchantments.isEmpty());
        assertEquals(0, bow.getEnchantmentLevel(Enchantment.ID_BOW_POWER));
    }
}
