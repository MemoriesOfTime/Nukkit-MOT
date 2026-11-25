package cn.nukkit.level.explosion;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Historical palette aliases produce their declared inventory state without changing the world block. */
@Isolated("native Server.instance, item registries and hashed block palette mode")
public final class NativeLegacyWoodDropStateTest {
    @BeforeAll static void bootstrap() throws Exception { cn.nukkit.MockServer.init(); }

    private record DropCase(int sourceId, int sourceMeta, int itemBlockId, int itemMeta) { }
    private record Slot(int networkId, int count, int damage, int blockRuntime, CompoundTag tag) { }
    private static final Map<Integer, Map<Integer, CompoundTag>> STATES = new HashMap<>();
    private static final Map<Integer, int[]> DECLARED_TUPLES = Map.ofEntries(
        Map.entry(1001, new int[]{18168865, 8490, 15178, 2903, 2919, 13348, 10, 8043, 7819, 10388}),
        Map.entry(2168, new int[]{18168865, 8920, 15734, 2933, 2949, 13891, 10, 8473, 8249, 10850}),
        Map.entry(2193, new int[]{18168865, 11215, 20158, 3533, 3589, 17359, 10, 10768, 10400, 13437}),
        Map.entry(589, new int[]{18087969, 4577, 4578, 4579, 4580, 4581, 4582, 4583, 7378, 8915}),
        Map.entry(594, new int[]{18090528, 4566, 4567, 4568, 4569, 4570, 4571, 4572, 7374, 8896}),
        Map.entry(618, new int[]{18095666, 4563, 4564, 4565, 4566, 4567, 4568, 4569, 7388, 8900}),
        Map.entry(622, new int[]{18098179, 4563, 4564, 4565, 4566, 4567, 4568, 4569, 7386, 8896}),
        Map.entry(630, new int[]{18100737, 4902, 4903, 4904, 4905, 4906, 4907, 4908, 7936, 9547}),
        Map.entry(649, new int[]{18103297, 4910, 4911, 4912, 4913, 4914, 4915, 4916, 7953, 9565}),
        Map.entry(662, new int[]{18105860, 8619, 13296, 1982, 1997, 12113, 10, 8176, 7960, 9575}),
        Map.entry(671, new int[]{18108419, 8599, 13287, 1987, 2002, 12107, 10, 8156, 7940, 9556}),
        Map.entry(685, new int[]{18153475, 8590, 13299, 1989, 2004, 12110, 10, 8147, 7931, 9529}),
        Map.entry(712, new int[]{18158598, 8629, 13289, 2030, 2046, 12076, 10, 8182, 7962, 9581}),
        Map.entry(729, new int[]{18161159, 7352, 12957, 2516, 2532, 11454, 10, 6905, 6685, 8793}),
        Map.entry(748, new int[]{18163713, 7361, 13003, 2522, 2538, 11491, 10, 6914, 6694, 8802}),
        Map.entry(766, new int[]{18163713, 7530, 13805, 2600, 2616, 12243, 10, 7083, 6863, 9358}),
        Map.entry(776, new int[]{18168865, 7530, 13811, 2600, 2616, 12243, 10, 7083, 6863, 9358}),
        Map.entry(786, new int[]{18168865, 7564, 13848, 2634, 2650, 12279, 10, 7117, 6897, 9393}),
        Map.entry(800, new int[]{18168865, 7580, 13864, 2634, 2650, 12295, 10, 7133, 6913, 9409}),
        Map.entry(818, new int[]{18168865, 7580, 13864, 2634, 2650, 12295, 10, 7133, 6913, 9409}),
        Map.entry(827, new int[]{18168865, 7600, 13896, 2642, 2658, 12327, 10, 7153, 6933, 9437}),
        Map.entry(844, new int[]{18168865, 7957, 14436, 2729, 2745, 12784, 10, 7510, 7286, 9848}),
        Map.entry(944, new int[]{18168865, 7958, 14437, 2730, 2746, 12785, 10, 7511, 7287, 9849}),
        Map.entry(975, new int[]{18168865, 8480, 15164, 2893, 2909, 13334, 10, 8033, 7809, 10378})
    );

    private static int itemId(int blockId) { return blockId > 255 ? 255 - blockId : blockId; }

    private static int effectivePalette(GameVersion version) {
        // The independently reviewed resource cohort includes forward preview mappings, not a numeric floor.
        return switch (version.getProtocol()) {
            case 593 -> 594;
            case 617 -> 618;
            case 686 -> 685;
            case 765 -> 766;
            case 785 -> 786;
            case 819 -> 818;
            case 843, 859, 860, 897, 898, 924 -> 844;
            case 974 -> 975;
            case 2169 -> 2168;
            case 2192 -> 2193;
            default -> version.getProtocol();
        };
    }

    private static Item canonicalDrop(DropCase expected) {
        Block source = Block.get(expected.sourceId(), expected.sourceMeta());
        int original = source.getFullId();
        Item item = source.toItem();
        assertInstanceOf(ItemBlock.class, item);
        assertEquals(itemId(expected.itemBlockId()), item.getId(), "The item prefix belongs to the canonical block");
        assertEquals(expected.itemMeta(), item.getDamage());
        assertEquals(expected.itemBlockId(), item.getBlockUnsafe().getId());
        assertEquals(expected.itemMeta(), item.getBlockUnsafe().getDamage(), "The attached block agrees with inventory metadata");
        assertEquals(1, item.getCount());
        assertEquals(original, source.getFullId(), "Drop conversion cannot change the world source");
        Item[] actualDrops = source.getDrops(Item.get(Item.AIR));
        assertEquals(1, actualDrops.length, "The ordinary harvest path returns one block item");
        assertEquals(item.getId(), actualDrops[0].getId());
        assertEquals(item.getDamage(), actualDrops[0].getDamage());
        assertEquals(item.getBlockUnsafe().getFullId(), actualDrops[0].getBlockUnsafe().getFullId());
        assertEquals(original, source.getFullId());
        return item;
    }

    @Test void fullBarkLogAliasesDropTheirWoodFamilyAndPreserveSource() {
        for (int meta = 12; meta <= 15; meta++) {
            canonicalDrop(new DropCase(Block.WOOD, meta, Block.WOOD_BARK, meta & 3));
            // The old typed DAT explicitly declares both padding14/15 as acacia, not dark oak.
            canonicalDrop(new DropCase(Block.WOOD2, meta, Block.WOOD_BARK, meta == 13 ? 5 : 4));
        }
    }

    @Test void barkPaddingRetainsTheStrippedBitAndCanonicalSpecies() {
        for (int axis : new int[]{0, 0x10, 0x20}) {
            for (int meta : new int[]{6, 7, 14, 15}) {
                canonicalDrop(new DropCase(Block.WOOD_BARK, axis | meta, Block.WOOD_BARK, meta & 8));
            }
        }
    }

    @Test void torchAndFencePaddingUseFreshCanonicalInventoryBlocks() {
        for (int meta : new int[]{6, 7}) {
            canonicalDrop(new DropCase(Block.SOUL_TORCH, meta, Block.SOUL_TORCH, 0));
            canonicalDrop(new DropCase(Block.TORCH, meta, Block.TORCH, 0));
            for (int flags = 0; flags <= BlockFence.CONNECTION_FLAGS; flags += 0x10) {
                canonicalDrop(new DropCase(Block.FENCE, flags | meta, Block.FENCE, 0));
            }
        }
    }

    @Test void secondLogFamilyRetainsTheDeclaredAcaciaPaddingIdentity() {
        canonicalDrop(new DropCase(Block.WOOD2, 12, Block.WOOD_BARK, 4));
        canonicalDrop(new DropCase(Block.WOOD2, 13, Block.WOOD_BARK, 5));
        canonicalDrop(new DropCase(Block.WOOD2, 14, Block.WOOD_BARK, 4));
        canonicalDrop(new DropCase(Block.WOOD2, 15, Block.WOOD_BARK, 4));
    }

    @Test void customLegacySubclassesKeepTheirExistingVirtualItemConversion() {
        Block customWood = new cn.nukkit.block.BlockWood(12) { };
        Item wood = customWood.toItem();
        assertSame(customWood, wood.getBlockUnsafe());
        assertEquals(0, wood.getDamage());
        Block customWood2 = new cn.nukkit.block.BlockWood2(14) { };
        Item wood2 = customWood2.toItem();
        assertSame(customWood2, wood2.getBlockUnsafe());
        assertEquals(2, wood2.getDamage());
        Block customBark = new cn.nukkit.block.BlockWoodBark(14) { };
        Item bark = customBark.toItem();
        assertEquals(Block.WOOD_BARK, bark.getBlockUnsafe().getId());
        assertEquals(14, bark.getDamage());
        assertEquals(14, bark.getBlockUnsafe().getDamage());
        Block customFence = new cn.nukkit.block.BlockFence(0xf0 | 7) { };
        Item fence = customFence.toItem();
        assertSame(customFence, fence.getBlockUnsafe());
        assertEquals(7, fence.getDamage());
    }

    private static void unchangedDrop(int id, int sourceMeta, int itemMeta, boolean sameAttachedSource) {
        Block source = Block.get(id, sourceMeta);
        Item item = source.toItem();
        assertInstanceOf(ItemBlock.class, item);
        assertEquals(itemId(id), item.getId());
        assertEquals(itemMeta, item.getDamage());
        assertEquals(id, item.getBlockUnsafe().getId());
        assertEquals(source.getClass(), item.getBlockUnsafe().getClass(), "Named descendant block class is preserved");
        assertEquals(sameAttachedSource ? sourceMeta : itemMeta, item.getBlockUnsafe().getDamage());
        if (sameAttachedSource) assertSame(source, item.getBlockUnsafe(), "Existing non-alias attachment behavior is retained");
        assertEquals(1, item.getCount());
        assertEquals(sourceMeta, source.getDamage());
    }

    @Test void ordinaryNeighborsAndAllFortyVersionedSlotsKeepTheirTypedContract() throws Exception {
        for (int meta = 0; meta <= 11; meta++) unchangedDrop(Block.WOOD, meta, meta & 3, true);
        for (int meta : new int[]{0, 1, 4, 5, 8, 9}) unchangedDrop(Block.WOOD2, meta, meta & 3, true);
        for (int axis : new int[]{0, 0x10, 0x20}) {
            for (int species = 0; species <= 5; species++) {
                unchangedDrop(Block.WOOD_BARK, axis | species, species, false);
                unchangedDrop(Block.WOOD_BARK, axis | species | 8, species | 8, false);
            }
        }
        for (int flags = 0; flags <= BlockFence.CONNECTION_FLAGS; flags += 0x10) {
            for (int species = 0; species <= 5; species++) unchangedDrop(Block.FENCE, flags | species, species, true);
        }
        for (int meta = 0; meta <= 5; meta++) {
            unchangedDrop(Block.SOUL_TORCH, meta, 0, true);
            unchangedDrop(Block.TORCH, meta, 0, true);
        }
        for (int meta : new int[]{0, 6, 7, 0xf0 | 7}) {
            unchangedDrop(Block.NETHER_BRICK_FENCE, meta, meta & ~BlockFence.CONNECTION_FLAGS, true);
        }
        // Named descendants retain their own class/id even at the old full-bark metadata boundary.
        for (int meta : new int[]{0, 4, 8, 12}) {
            unchangedDrop(Block.STRIPPED_OAK_LOG, meta, 0, true);
            unchangedDrop(Block.MANGROVE_LOG, meta, 0, false);
            unchangedDrop(Block.CHERRY_LOG, meta, meta & 3, true);
            unchangedDrop(Block.PALE_OAK_LOG, meta, meta & 3, true);
            unchangedDrop(Block.BAMBOO_BLOCK, meta, meta & 3, true);
        }

        DropCase[] cases = {
            new DropCase(Block.WOOD, 12, Block.WOOD_BARK, 0),
            new DropCase(Block.WOOD, 13, Block.WOOD_BARK, 1),
            new DropCase(Block.WOOD, 14, Block.WOOD_BARK, 2),
            new DropCase(Block.WOOD, 15, Block.WOOD_BARK, 3),
            new DropCase(Block.WOOD2, 12, Block.WOOD_BARK, 4),
            new DropCase(Block.WOOD2, 13, Block.WOOD_BARK, 5),
            new DropCase(Block.WOOD2, 14, Block.WOOD_BARK, 4),
            new DropCase(Block.WOOD2, 15, Block.WOOD_BARK, 4),
            new DropCase(Block.WOOD_BARK, 6, Block.WOOD_BARK, 0),
            new DropCase(Block.WOOD_BARK, 7, Block.WOOD_BARK, 0),
            new DropCase(Block.WOOD_BARK, 14, Block.WOOD_BARK, 8),
            new DropCase(Block.WOOD_BARK, 15, Block.WOOD_BARK, 8),
            new DropCase(Block.SOUL_TORCH, 6, Block.SOUL_TORCH, 0),
            new DropCase(Block.SOUL_TORCH, 7, Block.SOUL_TORCH, 0),
            new DropCase(Block.FENCE, 6, Block.FENCE, 0),
            new DropCase(Block.FENCE, 0xf0 | 7, Block.FENCE, 0)
        };
        boolean previous = GlobalBlockPalette.useHashedBlockNetworkIds();
        int versions = 0;
        try {
            for (GameVersion version : GameVersion.values()) {
                if (version.isNetEase() || version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
                versions++;
                int protocol = effectivePalette(version);
                assertEquals(protocol, GlobalBlockPalette.getPaletteByProtocol(version).getProtocol());
                for (DropCase expected : cases) {
                    Item item = canonicalDrop(expected);
                    CompoundTag row = nativeState(protocol, expected.itemBlockId(), expected.itemMeta());
                    assertDeclaredSemantics(protocol, expected.itemBlockId(), expected.itemMeta(), row);
                    int expectedHash = independentHash(row.getString("name"), row.getCompound("states"));
                    item.setCount(7);
                    item.setNamedTag(new CompoundTag().putString("opaque", "canonical-drop").putInt("receipt", 41));
                    byte[] before = item.getCompoundTag().clone();
                    Block attached = item.getBlockUnsafe();
                    for (boolean hashed : new boolean[]{false, true}) {
                        GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                        Slot actual = actualSlot(version, item);
                        // The unchanged item-table API is an adjacent prefix witness; block expectations are independent typed DAT.
                        assertEquals(RuntimeItems.getMapping(version).toRuntime(item.getId(), item.getDamage()).getRuntimeId(), actual.networkId());
                        assertEquals(7, actual.count());
                        assertEquals(0, actual.damage(), "Canonical block items use the block state, not auxiliary item damage");
                        assertEquals(hashed ? expectedHash : row.getInt("runtimeId"), actual.blockRuntime());
                        assertEquals(item.getNamedTag(), actual.tag());
                        assertArrayEquals(before, item.getCompoundTag());
                        assertSame(attached, item.getBlockUnsafe());
                        assertEquals(expected.itemBlockId(), attached.getId());
                        assertEquals(expected.itemMeta(), attached.getDamage());
                        assertEquals(7, item.getCount());
                        assertEquals(itemId(expected.itemBlockId()), item.getId());
                        assertEquals(expected.itemMeta(), item.getDamage());
                    }
                }
            }
            assertEquals(40, versions, "Complete ordinary 1.20 through 1.26.50 cohort");
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(previous); }
    }

    private static CompoundTag nativeState(int protocol, int id, int meta) throws Exception {
        Map<Integer, CompoundTag> selected = STATES.get(protocol);
        if (selected == null) {
            byte[] bytes;
            String resource = "runtime_block_states_" + protocol + ".dat";
            try (var input = NativeLegacyWoodDropStateTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource); bytes = input.readAllBytes();
            }
            assertTrue(DECLARED_TUPLES.containsKey(protocol), "Complete ordinary native palette cohort");
            var root = NBTIO.readTag(new GZIPInputStream(new ByteArrayInputStream(bytes)), ByteOrder.BIG_ENDIAN, false);
            assertInstanceOf(ListTag.class, root);
            selected = new HashMap<>();
            for (var value : ((ListTag<?>) root).getAll()) {
                CompoundTag row = assertInstanceOf(CompoundTag.class, value);
                assertTrue(row.containsInt("id") && (row.containsShort("data") || row.containsInt("data")));
                assertTrue(!row.exist("stateOverload") || row.containsByte("stateOverload"));
                int rowId = row.getInt("id"); int data = row.getShort("data");
                if (row.getBoolean("stateOverload") || !(rowId == Block.WOOD_BARK && (data <= 5 || data == 8)
                        || rowId == Block.SOUL_TORCH && data == 0 || rowId == Block.FENCE && data == 0)) continue;
                assertTrue(row.containsInt("runtimeId") && row.containsString("name") && row.containsCompound("states"));
                assertNull(selected.put(rowId << Block.DATA_BITS | data, row.copy()), "Unique exact typed native declaration");
            }
            STATES.put(protocol, selected);
        }
        CompoundTag row = selected.get(id << Block.DATA_BITS | meta);
        assertNotNull(row, "Independent version-native state " + protocol + ":" + id + ":" + meta);
        return row;
    }

    private static void assertDeclaredSemantics(int protocol, int id, int meta, CompoundTag row) {
        CompoundTag states = row.getCompound("states");
        int[] declared = DECLARED_TUPLES.get(protocol);
        int target = id == Block.WOOD_BARK ? (meta == 8 ? 6 : meta) : id == Block.SOUL_TORCH ? 7 : 8;
        assertTrue(row.containsInt("version") && row.containsInt("runtimeId"));
        assertEquals(declared[0], row.getInt("version"));
        assertEquals(declared[target + 1], row.getInt("runtimeId"));
        if (id == Block.WOOD_BARK) {
            String[] species = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
            assertTrue(states.containsString("pillar_axis")); assertEquals("y", states.getString("pillar_axis"));
            if (protocol < 662) {
                assertEquals("minecraft:wood", row.getString("name"));
                assertTrue(states.containsString("wood_type") && states.containsByte("stripped_bit"));
                assertEquals(species[meta & 7], states.getString("wood_type"));
                assertEquals((meta & 8) != 0, states.getBoolean("stripped_bit")); assertEquals(3, states.getTags().size());
            } else {
                assertEquals("minecraft:" + ((meta & 8) == 0 ? "" : "stripped_") + species[meta & 7] + "_wood", row.getString("name"));
                assertEquals(1, states.getTags().size());
            }
        } else if (id == Block.SOUL_TORCH) {
            assertEquals("minecraft:soul_torch", row.getString("name"));
            assertTrue(states.containsString("torch_facing_direction")); assertEquals("unknown", states.getString("torch_facing_direction"));
            assertEquals(1, states.getTags().size());
        } else {
            assertEquals(Block.FENCE, id); assertEquals("minecraft:oak_fence", row.getString("name"));
            assertEquals(protocol >= 2193 ? 4 : 0, states.getTags().size());
            for (String face : new String[]{"north", "east", "south", "west"}) {
                if (protocol < 2193) continue;
                String key = "minecraft:connection_" + face;
                assertTrue(states.containsByte(key)); assertEquals(0, states.getByte(key));
            }
        }
    }

    private static int independentHash(String name, CompoundTag states) throws Exception {
        CompoundTag canonical = new CompoundTag(new LinkedHashMap<>()).putString("name", name)
                .putCompound("states", new CompoundTag(new TreeMap<>(states.getTags())));
        int hash = 0x811c9dc5;
        for (byte value : NBTIO.write(canonical, ByteOrder.LITTLE_ENDIAN)) hash = (hash ^ (value & 255)) * 0x01000193;
        return hash;
    }

    private static Slot actualSlot(GameVersion version, Item item) throws Exception {
        BinaryStream output = new BinaryStream(); output.putSlot(version, item, false);
        BinaryStream wire = new BinaryStream(output.getBuffer());
        // Protocol2168 introduces the NetworkItemStackDescriptor short prefix/unsigned block body.
        int network = version.getProtocol() >= 2168 ? (short) wire.getLShort() : wire.getVarInt();
        int count = wire.getLShort(); int damage = Math.toIntExact(wire.getUnsignedVarInt());
        assertFalse(wire.getBoolean());
        int runtime = version.getProtocol() >= 2168 ? (int) wire.getUnsignedVarInt() : wire.getVarInt();
        byte[] userBytes = wire.getByteArray();
        assertEquals(wire.getCount(), wire.getOffset());
        var user = new ByteArrayInputStream(userBytes);
        assertEquals(255, user.read()); assertEquals(255, user.read()); assertEquals(1, user.read());
        CompoundTag tag = NBTIO.read(user, ByteOrder.LITTLE_ENDIAN);
        assertArrayEquals(new byte[8], user.readNBytes(8)); assertEquals(0, user.available());
        return new Slot(network, count, damage, runtime, tag);
    }
}
