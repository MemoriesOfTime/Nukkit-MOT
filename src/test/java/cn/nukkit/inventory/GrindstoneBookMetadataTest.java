package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GrindstoneBookMetadataTest {
    private GrindstoneInventory grindstone;

    @BeforeAll static void init() { MockServer.init(); }
    @BeforeEach void setup() {
        grindstone = new GrindstoneInventory(new PlayerUIInventory(mock(Player.class)),
                new Position(0, 64, 0));
    }

    @Test void convertedNamedBookPreservesNonEnchantmentMetadataWithoutMutatingSource() {
        Item book = namedBook();
        grindstone.setItemForce(0, book);
        byte[] before = grindstone.getEquipment().getCompoundTag().clone();
        Item result = grindstone.getResult();
        assertEquals(Item.BOOK, result.getId());
        assertEquals(book.getCount(), result.getCount());
        assertEquals(book.getCustomName(), result.getCustomName());
        assertArrayEquals(book.getLore(), result.getLore());
        assertEquals("original", result.getNamedTag().getCompound("custom_payload").getString("nested"));
        assertFalse(result.hasEnchantments());
        assertFalse(result.getNamedTag().contains("ench"));
        assertFalse(result.getNamedTag().contains("custom_ench"));
        assertFalse(result.getNamedTag().contains("RepairCost"));
        assertArrayEquals(before, grindstone.getEquipment().getCompoundTag());
        CompoundTag changed = result.getNamedTag();
        changed.getCompound("custom_payload").putString("nested", "modified output");
        result.setNamedTag(changed);
        assertArrayEquals(before, grindstone.getEquipment().getCompoundTag(),
                "returned output must not borrow retained source metadata");
    }

    @Test void plainDisenchantedBookHasNoLeftoverMetadata() {
        Item book = Item.get(Item.ENCHANTED_BOOK);
        book.addEnchantment(Enchantment.get(Enchantment.ID_PROTECTION_ALL));
        grindstone.setItemForce(0, book);
        Item result = grindstone.getResult();
        assertTrue(result.equalsExact(Item.get(Item.BOOK)), "metadata-free result remains ordinary book");
    }

    @Test void ingredientOnlyBookUsesTheSameMetadataPreservingResult() {
        Item book = namedBook();
        grindstone.setItemForce(1, book);
        Item result = grindstone.getResult();
        assertEquals(Item.BOOK, result.getId());
        assertEquals(book.getCustomName(), result.getCustomName());
        assertArrayEquals(book.getLore(), result.getLore());
        assertTrue(grindstone.getEquipment().isNull());
        assertTrue(grindstone.getIngredient().equalsExact(book));
    }

    @Test void unrelatedSecondInputIsRejectedBeforeEnchantedBookConversion() {
        Item book = namedBook();
        Item other = Item.get(Item.DIAMOND);
        grindstone.setItemForce(0, book);
        grindstone.setItemForce(1, other);
        assertTrue(grindstone.getResult().isNull());
        assertTrue(grindstone.getEquipment().equalsExact(book));
        assertTrue(grindstone.getIngredient().equalsExact(other));
    }

    @Test void onlyCurseBookRemainsAnUnchangedNoOp() {
        Item book = Item.get(Item.ENCHANTED_BOOK);
        book.addEnchantment(Enchantment.get(Enchantment.ID_BINDING_CURSE));
        book.setCustomName("cursed book");
        grindstone.setItemForce(0, book);
        assertTrue(grindstone.getResult().isNull());
        assertTrue(grindstone.getEquipment().equalsExact(book));
    }

    @Test void mixedBookKeepsCurseAndMetadataAsEnchantedBook() {
        Item book = namedBook();
        book.addEnchantment(Enchantment.get(Enchantment.ID_BINDING_CURSE));
        grindstone.setItemForce(0, book);
        Item result = grindstone.getResult();
        assertEquals(Item.ENCHANTED_BOOK, result.getId());
        assertTrue(result.hasEnchantment(Enchantment.ID_BINDING_CURSE));
        assertFalse(result.hasEnchantment(Enchantment.ID_PROTECTION_ALL));
        assertEquals(book.getCustomName(), result.getCustomName());
        assertArrayEquals(book.getLore(), result.getLore());
        assertTrue(grindstone.getEquipment().equalsExact(book));
    }

    private static Item namedBook() {
        Item book = Item.get(Item.ENCHANTED_BOOK);
        book.addEnchantment(Enchantment.get(Enchantment.ID_PROTECTION_ALL));
        book.setCustomName("plugin named book");
        book.setLore("first line", "second line");
        CompoundTag tag = book.getNamedTag().copy();
        tag.putCompound("custom_payload", new CompoundTag().putString("nested", "original"));
        tag.putString("custom_ench", "plugin enchantment state");
        tag.putInt("RepairCost", 7);
        book.setNamedTag(tag);
        return book;
    }
}
