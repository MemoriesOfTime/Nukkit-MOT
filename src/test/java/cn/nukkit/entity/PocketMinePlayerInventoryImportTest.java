package cn.nukkit.entity;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketMinePlayerInventoryImportTest {

    @Test
    void importsPocketMineOffhandAndEnderChest() {
        CompoundTag speedTalisman = item("minecraft:totem_of_undying", 1)
                .putCompound("tag", new CompoundTag().putString("talismanId", "speed"));
        CompoundTag playerData = new CompoundTag()
                .putCompound("OffHandItem", speedTalisman)
                .putList("EnderChestInventory", new ListTag<CompoundTag>()
                        .add(item("minecraft:diamond_sword", 1).putByte("Slot", 4)));

        EntityHumanType.importPocketMineInventories(playerData);

        CompoundTag importedOffhand = itemAt(playerData.getList("Inventory", CompoundTag.class), -106);
        assertEquals("minecraft:totem_of_undying", importedOffhand.getString("Name"));
        assertEquals("speed", importedOffhand.getCompound("tag").getString("talismanId"));
        assertEquals("minecraft:diamond_sword",
                itemAt(playerData.getList("EnderItems", CompoundTag.class), 4).getString("Name"));
        assertFalse(playerData.contains("OffHandItem"));
        assertFalse(playerData.contains("EnderChestInventory"));
        assertTrue(playerData.getBoolean(EntityHumanType.POCKETMINE_ENDER_IMPORT_PENDING));
    }

    @Test
    void preservesExistingNukkitItemsAndUsesFreeSlots() {
        ListTag<CompoundTag> inventory = new ListTag<CompoundTag>("Inventory")
                .add(item("minecraft:shield", 1).putByte("Slot", -106))
                .add(item("minecraft:diamond", 1).putByte("Slot", 9));
        ListTag<CompoundTag> ender = new ListTag<CompoundTag>("EnderItems")
                .add(item("minecraft:emerald", 1).putByte("Slot", 3));
        CompoundTag playerData = new CompoundTag()
                .putList(inventory)
                .putCompound("OffHandItem", item("minecraft:totem_of_undying", 1))
                .putList(ender)
                .putList("EnderChestInventory", new ListTag<CompoundTag>()
                        .add(item("minecraft:diamond_sword", 1).putByte("Slot", 3)));

        EntityHumanType.importPocketMineInventories(playerData);

        assertEquals("minecraft:shield", itemAt(inventory, -106).getString("Name"));
        assertEquals("minecraft:totem_of_undying", itemAt(inventory, 10).getString("Name"));
        assertEquals("minecraft:emerald", itemAt(ender, 3).getString("Name"));
        assertEquals("minecraft:diamond_sword", itemAt(ender, 0).getString("Name"));
        assertFalse(playerData.contains("OffHandItem"));
        assertFalse(playerData.contains("EnderChestInventory"));
        assertTrue(playerData.getBoolean(EntityHumanType.POCKETMINE_ENDER_IMPORT_PENDING));
    }

    @Test
    void retainsLegacyTagsWhenBothInventoriesAreFull() {
        ListTag<CompoundTag> inventory = new ListTag<CompoundTag>("Inventory")
                .add(item("minecraft:shield", 1).putByte("Slot", -106));
        for (int slot = 9; slot < 45; slot++) {
            inventory.add(item("minecraft:stone", 64).putByte("Slot", slot));
        }
        ListTag<CompoundTag> ender = new ListTag<>("EnderItems");
        for (int slot = 0; slot < 27; slot++) {
            ender.add(item("minecraft:cobblestone", 64).putByte("Slot", slot));
        }
        CompoundTag playerData = new CompoundTag()
                .putList(inventory)
                .putCompound("OffHandItem", item("minecraft:totem_of_undying", 1))
                .putList(ender)
                .putList("EnderChestInventory", new ListTag<CompoundTag>()
                        .add(item("minecraft:diamond_sword", 1).putByte("Slot", 4)));

        EntityHumanType.importPocketMineInventories(playerData);

        assertTrue(playerData.contains("OffHandItem"));
        assertEquals(1, playerData.getList("EnderChestInventory", CompoundTag.class).size());
        assertEquals(37, inventory.size());
        assertEquals(27, ender.size());
        assertFalse(playerData.getBoolean(EntityHumanType.POCKETMINE_ENDER_IMPORT_PENDING));
    }

    @Test
    void movesEnderOverflowToTheMainInventoryBeforeRetainingItAsHiddenData() {
        ListTag<CompoundTag> ender = new ListTag<>("EnderItems");
        for (int slot = 0; slot < 27; slot++) {
            ender.add(item("minecraft:cobblestone", 64).putByte("Slot", slot));
        }
        CompoundTag playerData = new CompoundTag()
                .putList(ender)
                .putList("EnderChestInventory", new ListTag<CompoundTag>()
                        .add(item("minecraft:diamond_sword", 1).putByte("Slot", 4)));

        EntityHumanType.importPocketMineInventories(playerData);

        assertEquals("minecraft:diamond_sword",
                itemAt(playerData.getList("Inventory", CompoundTag.class), 9).getString("Name"));
        assertFalse(playerData.contains("EnderChestInventory"));
        assertTrue(playerData.getBoolean(EntityHumanType.POCKETMINE_ENDER_IMPORT_PENDING));
    }

    @Test
    void importIsIdempotent() {
        CompoundTag playerData = new CompoundTag()
                .putCompound("OffHandItem", item("minecraft:totem_of_undying", 1))
                .putList("EnderChestInventory", new ListTag<CompoundTag>()
                        .add(item("minecraft:diamond", 2).putByte("Slot", 8)));

        EntityHumanType.importPocketMineInventories(playerData);
        EntityHumanType.importPocketMineInventories(playerData);

        assertEquals(1, playerData.getList("Inventory", CompoundTag.class).size());
        assertEquals(1, playerData.getList("EnderItems", CompoundTag.class).size());
    }

    private static CompoundTag item(String name, int count) {
        return new CompoundTag().putString("Name", name).putByte("Count", count).putShort("Damage", 0);
    }

    private static CompoundTag itemAt(ListTag<CompoundTag> items, int slot) {
        return items.getAll().stream()
                .filter(item -> item.getByte("Slot") == slot && item.getByte("Count") > 0)
                .findFirst()
                .orElseThrow();
    }
}
