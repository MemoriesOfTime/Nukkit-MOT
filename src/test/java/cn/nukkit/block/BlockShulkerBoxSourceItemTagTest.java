package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.inventory.ShulkerBoxInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockShulkerBoxSourceItemTagTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void keptTagDropsItemsAndNameButKeepsLoreAndCustomKeys() {
        Item source = taggedBox();
        source.setCustomName("Box");
        CompoundTag tag = source.getNamedTag();
        tag.putList(new cn.nukkit.nbt.tag.ListTag<CompoundTag>("Items"));
        source.setNamedTag(tag);

        CompoundTag kept = BlockShulkerBox.sourceItemTag(source);

        assertNotNull(kept);
        assertFalse(kept.contains("Items"));
        assertFalse(kept.getCompound("display").contains("Name"));
        assertEquals(1, kept.getCompound("display").getList("Lore").size());
        assertEquals("shulker_box", kept.getString("plugin_key"));
        assertEquals("0001", kept.getString("plugin_serial"));
        assertTrue(source.getNamedTag().contains("Items"), "the item itself must stay untouched");
        assertTrue(source.hasCustomName(), "the item itself must stay untouched");
    }

    @Test
    void plainOrOnlyNamedBoxKeepsNothing() {
        assertNull(BlockShulkerBox.sourceItemTag(Item.get(BlockID.UNDYED_SHULKER_BOX)));
        Item named = Item.get(BlockID.UNDYED_SHULKER_BOX);
        named.setCustomName("Box");
        assertNull(BlockShulkerBox.sourceItemTag(named));

        CompoundTag nbt = new CompoundTag();
        BlockShulkerBox.putSourceItemTag(nbt, named);
        assertFalse(nbt.contains(BlockShulkerBox.SOURCE_ITEM_TAG));
    }

    @Test
    void brokenBoxComesBackWithItsLoreMarkerAndName() {
        CompoundTag nbt = new CompoundTag().putString("CustomName", "Box");
        BlockShulkerBox.putSourceItemTag(nbt, taggedBox());
        BlockUndyedShulkerBox box = placed(nbt, false);

        Item drop = box.toItem();

        assertEquals("Box", drop.getCustomName());
        assertArrayEquals(new String[]{"§7Things inside stay"}, drop.getLore());
        assertEquals("shulker_box", drop.getNamedTag().getString("plugin_key"));
        assertEquals("0001", drop.getNamedTag().getString("plugin_serial"));
        assertEquals("marker", drop.getNamedTag().getString("plugin_marker"));
        assertFalse(drop.getNamedTag().contains("Items"), "an empty box carries no contents list");

        Item second = box.toItem();
        second.getNamedTag().putString("plugin_marker", "changed");
        assertEquals("marker", box.toItem().getNamedTag().getString("plugin_marker"),
                "a drop must not share the tag object stored on the block entity");
    }

    @Test
    void contentsAreStillWrittenNextToTheKeptTag() {
        CompoundTag nbt = new CompoundTag();
        BlockShulkerBox.putSourceItemTag(nbt, taggedBox());
        BlockUndyedShulkerBox box = placed(nbt, true);

        Item drop = box.toItem();

        assertEquals(1, drop.getNamedTag().getList("Items").size());
        assertEquals("0001", drop.getNamedTag().getString("plugin_serial"));
    }

    @Test
    void pickBlockDoesNotMintTheKeptTag() {
        CompoundTag nbt = new CompoundTag().putString("CustomName", "Box");
        BlockShulkerBox.putSourceItemTag(nbt, taggedBox());
        BlockUndyedShulkerBox box = placed(nbt, false);

        Item picked = box.toPickItem();

        assertEquals("Box", picked.getCustomName());
        assertEquals(0, picked.getLore().length);
        assertFalse(picked.getNamedTag().contains("plugin_serial"));
        assertFalse(picked.getNamedTag().contains("plugin_marker"));
    }

    @Test
    void boxPlacedBeforeTheTagExistedDropsAsBefore() {
        BlockUndyedShulkerBox box = placed(new CompoundTag().putString("CustomName", "Box"), false);

        Item drop = box.toItem();

        assertEquals("Box", drop.getCustomName());
        assertEquals(0, drop.getLore().length);
    }

    private static Item taggedBox() {
        Item item = Item.get(BlockID.UNDYED_SHULKER_BOX);
        item.setLore("§7Things inside stay");
        CompoundTag tag = item.getNamedTag();
        tag.putString("plugin_key", "shulker_box");
        tag.putString("plugin_serial", "0001");
        tag.putString("plugin_marker", "marker");
        item.setNamedTag(tag);
        return item;
    }

    private static BlockUndyedShulkerBox placed(CompoundTag namedTag, boolean withContents) {
        BlockUndyedShulkerBox box = new BlockUndyedShulkerBox();
        box.level = mock(Level.class);
        BlockEntityShulkerBox tile = mock(BlockEntityShulkerBox.class);
        tile.namedTag = namedTag;
        when(tile.hasName()).thenReturn(namedTag.contains("CustomName"));
        when(tile.getName()).thenReturn(namedTag.getString("CustomName"));
        ShulkerBoxInventory inventory = mock(ShulkerBoxInventory.class);
        when(inventory.isEmpty()).thenReturn(!withContents);
        when(inventory.getSize()).thenReturn(27);
        when(inventory.getItem(anyInt())).thenReturn(Item.get(Item.AIR));
        if (withContents) {
            when(inventory.getItem(0)).thenReturn(Item.get(Item.DIAMOND, 0, 3));
        }
        when(tile.getRealInventory()).thenReturn(inventory);
        when(box.level.getBlockEntity(box)).thenReturn(tile);
        return box;
    }
}
