package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.ShulkerBoxInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockShulkerBoxTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    /**
     * A shulker box must be as fragile against explosions as vanilla Bedrock makes it.
     *
     * <p>Nukkit keeps five times the vanilla blast resistance, and {@link cn.nukkit.level.Explosion}
     * divides by five again while tracing a ray. A stored 30 therefore meant a blast resistance of 6
     * instead of the vanilla 2: TNT flattened the stone around the box and left the box itself intact.
     */
    @Nested
    class BlastResistance {

        private static final double VANILLA_BLAST_RESISTANCE = 2d;
        private static final double NUKKIT_SCALE = 5d;

        @Test
        void dyedAndUndyedBoxesKeepVanillaBlastResistance() {
            assertEquals(VANILLA_BLAST_RESISTANCE * NUKKIT_SCALE,
                    new BlockShulkerBox().getResistance(),
                    1e-12,
                    "dyed shulker box blast resistance");
            assertEquals(VANILLA_BLAST_RESISTANCE * NUKKIT_SCALE,
                    new BlockUndyedShulkerBox().getResistance(),
                    1e-12,
                    "undyed shulker box inherits the same value");
        }

        @Test
        void boxIsNotTougherThanAChest() {
            assertTrue(new BlockShulkerBox().getResistance() < new BlockChest().getResistance(),
                    "vanilla shulker boxes are weaker than chests, not stronger");
        }

        @Test
        void primedTntRemovesTheBoxThroughTheExplosionFormula() {
            // The ray in Explosion#explodeA starts at 0.7..1.3 times the blast size and pays
            // (resistance / 5 + 0.3) * 0.3 for every step through a block.
            double weakestRay = 4d * 0.7d;
            double cost = (new BlockShulkerBox().getResistance() / NUKKIT_SCALE + 0.3d) * 0.3d;
            assertTrue(weakestRay - cost > 0d,
                    "even the weakest TNT ray must break through a shulker box");
        }
    }

    @Nested
    class BreakTime {

        @Test
        void hasteUsesBedrockBreakSpeed() {
            Player player = mockPlayerWithEmptyHelmet();
            Effect haste = Mockito.mock(Effect.class);
            Mockito.when(player.getEffect(Effect.HASTE)).thenReturn(haste);
            Mockito.when(haste.getAmplifier()).thenReturn(3);

            Block shulkerBox = new BlockUndyedShulkerBox();
            int hasteLevel = 4;
            double expectedSeconds = 3d
                    / ((1d + 0.2d * hasteLevel) * Math.pow(1.2d, hasteLevel));

            assertEquals(3d,
                    shulkerBox.calculateBreakTimeNotInAir(Item.AIR_ITEM, null),
                    1e-12,
                    "base hand break time");
            assertEquals(expectedSeconds,
                    shulkerBox.calculateBreakTimeNotInAir(Item.AIR_ITEM, player),
                    1e-12,
                    "haste amplifier 3 must match Bedrock client progress");
        }

        @Test
        void conduitPowerKeepsExistingBreakSpeed() {
            Player player = mockPlayerWithEmptyHelmet();
            Mockito.when(player.hasEffect(Effect.CONDUIT_POWER)).thenReturn(true);
            Block shulkerBox = new BlockUndyedShulkerBox();

            assertEquals(3d / 1.4d,
                    shulkerBox.calculateBreakTimeNotInAir(Item.AIR_ITEM, player),
                    1e-12,
                    "conduit power must keep its existing linear multiplier");
        }

        private static Player mockPlayerWithEmptyHelmet() {
            Player player = Mockito.mock(Player.class);
            PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
            Mockito.when(player.getInventory()).thenReturn(inventory);
            Mockito.when(inventory.getHelmet()).thenReturn(Item.AIR_ITEM);
            return player;
        }
    }

    @Nested
    class SourceItemTag {

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
}
