package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockNameTableTest {
    private record Spec(Class<? extends Block> type, Function<Block, String> original, String[] literals) {
        Block create(int meta) {
            try { return type.getConstructor(int.class).newInstance(meta); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
    private static final List<Spec> SPECS = List.of(
            new Spec(BlockBricksStone.class, block -> originalBlockBricksStone((BlockBricksStone) block), new String[]{"Stone Bricks", "Mossy Stone Bricks", "Cracked Stone Bricks", "Chiseled Stone Bricks"}),
            new Spec(BlockCoralBlock.class, block -> originalBlockCoralBlock((BlockCoralBlock) block), new String[]{"Tube Coral Block", "Brain Coral Block", "Bubble Coral Block", "Fire Coral Block", "Horn Coral Block", "Tube Coral Block", "Tube Coral Block", "Tube Coral Block"}),
            new Spec(BlockCoralFan.class, block -> originalBlockCoralFan((BlockCoralFan) block), new String[]{"Tube Coral Fan", "Brain Coral Fan", "Bubble Coral Fan", "Fire Coral Fan", "Horn Coral Fan"}),
            new Spec(BlockDoubleSlabRedSandstone.class, block -> originalBlockDoubleSlabRedSandstone((BlockDoubleSlabRedSandstone) block), new String[]{"Red Sandstone", "Purpur", "", "", "", "", "", ""}),
            new Spec(BlockDoubleSlabStone.class, block -> originalBlockDoubleSlabStone((BlockDoubleSlabStone) block), new String[]{"Stone", "Sandstone", "Wooden", "Cobblestone", "Brick", "Stone Brick", "Quartz", "Nether Brick"}),
            new Spec(BlockDoubleSlabStone3.class, block -> originalBlockDoubleSlabStone3((BlockDoubleSlabStone3) block), new String[]{"End Stone Brick", "Smooth Red Sandstone", "Polished Andesite", "Andesite", "Diorite", "Polished Diorite", "Granite", "Polisehd Granite"}),
            new Spec(BlockDoubleSlabStone4.class, block -> originalBlockDoubleSlabStone4((BlockDoubleSlabStone4) block), new String[]{"Mossy Stone Brick", "Smooth Quartz", "Stone", "Cut Sandstone", "Cut Red Sandstone", "", "", ""}),
            new Spec(BlockDoubleSlabWood.class, block -> originalBlockDoubleSlabWood((BlockDoubleSlabWood) block), new String[]{"Oak", "Spruce", "Birch", "Jungle", "Acacia", "Dark Oak", "", ""}),
            new Spec(BlockFence.class, block -> originalBlockFence((BlockFence) block), new String[]{"Oak Fence", "Spruce Fence", "Birch Fence", "Jungle Fence", "Acacia Fence", "Dark Oak Fence", "", ""}),
            new Spec(BlockFlower.class, block -> originalBlockFlower((BlockFlower) block), new String[]{"Poppy", "Blue Orchid", "Allium", "Azure Bluet", "Red Tulip", "Orange Tulip", "White Tulip", "Pink Tulip", "Oxeye Daisy", "Cornflower", "Lily of the Valley", "Unknown", "Unknown", "Unknown", "Unknown", "Unknown"}),
            new Spec(BlockLeaves.class, block -> originalBlockLeaves((BlockLeaves) block), new String[]{"Oak Leaves", "Spruce Leaves", "Birch Leaves", "Jungle Leaves"}),
            new Spec(BlockLeaves2.class, block -> originalBlockLeaves2((BlockLeaves2) block), new String[]{"Acacia Leaves", "Dark Oak Leaves"}),
            new Spec(BlockPlanks.class, block -> originalBlockPlanks((BlockPlanks) block), new String[]{"Oak Wood Planks", "Spruce Wood Planks", "Birch Wood Planks", "Jungle Wood Planks", "Acacia Wood Planks", "Dark Oak Wood Planks"}),
            new Spec(BlockPurpur.class, block -> originalBlockPurpur((BlockPurpur) block), new String[]{"Purpur Block", "", "Purpur Pillar", ""}),
            new Spec(BlockQuartz.class, block -> originalBlockQuartz((BlockQuartz) block), new String[]{"Quartz Block", "Chiseled Quartz Block", "Quartz Pillar", "Quartz Pillar"}),
            new Spec(BlockRedSandstone.class, block -> originalBlockRedSandstone((BlockRedSandstone) block), new String[]{"Red Sandstone", "Chiseled Red Sandstone", "Smooth Red Sandstone", ""}),
            new Spec(BlockSandstone.class, block -> originalBlockSandstone((BlockSandstone) block), new String[]{"Sandstone", "Chiseled Sandstone", "Smooth Sandstone", ""}),
            new Spec(BlockSapling.class, block -> originalBlockSapling((BlockSapling) block), new String[]{"Oak Sapling", "Spruce Sapling", "Birch Sapling", "Jungle Sapling", "Acacia Sapling", "Dark Oak Sapling", "", ""}),
            new Spec(BlockSlabRedSandstone.class, block -> originalBlockSlabRedSandstone((BlockSlabRedSandstone) block), new String[]{"Red Sandstone", "Purpur", "", "", "", "", "", ""}),
            new Spec(BlockSlabStone.class, block -> originalBlockSlabStone((BlockSlabStone) block), new String[]{"Stone", "Sandstone", "Wooden", "Cobblestone", "Brick", "Stone Brick", "Quartz", "Nether Brick"}),
            new Spec(BlockSlabStone3.class, block -> originalBlockSlabStone3((BlockSlabStone3) block), new String[]{"End Stone Brick", "Smooth Red Sandstone", "Polished Andesite", "Andesite", "Diorite", "Polished Diorite", "Granite", "Polisehd Granite"}),
            new Spec(BlockSlabStone4.class, block -> originalBlockSlabStone4((BlockSlabStone4) block), new String[]{"Mossy Stone Brick", "Smooth Quartz", "Stone", "Cut Sandstone", "Cut Red Sandstone", "", "", ""}),
            new Spec(BlockSlabWood.class, block -> originalBlockSlabWood((BlockSlabWood) block), new String[]{"Oak", "Spruce", "Birch", "Jungle", "Acacia", "Dark Oak", "", ""}),
            new Spec(BlockStone.class, block -> originalBlockStone((BlockStone) block), new String[]{"Stone", "Granite", "Polished Granite", "Diorite", "Polished Diorite", "Andesite", "Polished Andesite", "Unknown Stone"}),
            new Spec(BlockTallGrass.class, block -> originalBlockTallGrass((BlockTallGrass) block), new String[]{"Grass", "Grass", "Fern", "Fern"}),
            new Spec(BlockWood.class, block -> originalBlockWood((BlockWood) block), new String[]{"Oak Wood", "Spruce Wood", "Birch Wood", "Jungle Wood"})
    );

    @BeforeAll static void initialize() { MockServer.init(); }

    private record Outcome(Object value, Class<?> failure) {}
    private static Outcome outcome(java.util.function.Supplier<?> action) {
        try { return new Outcome(action.get(), null); }
        catch (RuntimeException failure) { return new Outcome(null, failure.getClass()); }
    }
    private record ItemView(int id, int damage, int count, String name) {}
    private static ItemView itemView(Block block) {
        Item item = block.toItem();
        return new ItemView(item.getId(), item.getDamage(), item.getCount(), item.getName());
    }
    private record Call(Block block, List<String> trace) {}

    private static Call scripted(Spec spec, int seed, boolean original, boolean reentrant) {
        Block block = spy(spec.create(0));
        List<String> trace = new ArrayList<>();
        int[] calls = {0};
        boolean[] entered = {false};
        doAnswer(call -> {
            int index = calls[0]++;
            int value = index % 3 == 0 ? seed : index % 3 == 1 ? seed ^ 8 : seed + 1;
            trace.add("damage:" + value);
            if (reentrant && !entered[0]) {
                entered[0] = true;
                trace.add("enter");
                trace.add("nested:" + outcome(block::getName));
                trace.add("exit");
            }
            return value;
        }).when(block).getDamage();
        if (block instanceof BlockCoralFan fan) {
            doAnswer(call -> { trace.add("type"); return call.callRealMethod(); }).when(fan).getType();
        }
        if (block instanceof BlockCoralBlock coral) {
            doAnswer(call -> { trace.add("dead"); return call.callRealMethod(); }).when(coral).isDead();
        }
        doAnswer(call -> {
            trace.add("name");
            return original ? spec.original.apply(block) : call.callRealMethod();
        }).when(block).getName();
        return new Call(block, trace);
    }

    private static void assertSameBehavior(Call expected, Call actual, boolean items, String context) {
        Outcome before = outcome(() -> items ? itemView(expected.block) : expected.block.getName());
        Outcome after = outcome(() -> items ? itemView(actual.block) : actual.block.getName());
        assertEquals(before, after, context);
        assertEquals(expected.trace, actual.trace, context + " callback order");
    }

    @Test void nativeMetadataKeepsExactNamesAndLiteralIdentity() {
        for (Spec spec : SPECS) for (int meta = 0; meta < 32; meta++) {
            Block block = spec.create(meta);
            Outcome expected = outcome(() -> spec.original.apply(block));
            Outcome actual = outcome(block::getName);
            assertEquals(expected, actual, spec.type.getSimpleName() + ":" + meta);
            if (expected.failure == null) {
                boolean literal = java.util.Arrays.asList(spec.literals).contains(expected.value);
                if (literal) assertSame(expected.value, actual.value);
                else assertNotSame(actual.value, block.getName(), "Dynamic concatenation must remain fresh");
            }
        }
    }

    @Test void changingVirtualMetadataKeepsCallbackOrder() {
        int[] seeds = {0, 1, 2, 3, 4, 5, 7, 8, 15, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (Spec spec : SPECS) for (int seed : seeds) {
            assertSameBehavior(scripted(spec, seed, true, false), scripted(spec, seed, false, false),
                    false, spec.type.getSimpleName() + ":" + seed);
        }
    }

    @Test void virtualFailuresPropagateTheSameObject() {
        for (Spec spec : SPECS) {
            Call call = scripted(spec, 0, false, false);
            IllegalStateException failure = new IllegalStateException("metadata");
            doThrow(failure).when(call.block).getDamage();
            assertSame(failure, assertThrows(IllegalStateException.class, call.block::getName));
            if (call.block instanceof BlockCoralFan fan) {
                doThrow(failure).when(fan).getType();
                assertSame(failure, assertThrows(IllegalStateException.class, fan::getName));
            }
            if (call.block instanceof BlockCoralBlock coral) {
                doReturn(0).when(coral).getDamage();
                doThrow(failure).when(coral).isDead();
                assertSame(failure, assertThrows(IllegalStateException.class, coral::getName));
            }
        }
    }

    @Test void reentrantMetadataKeepsOuterAndNestedNamesIndependent() {
        for (Spec spec : SPECS) {
            assertSameBehavior(scripted(spec, 0, true, true), scripted(spec, 0, false, true),
                    false, spec.type.getSimpleName());
        }
    }

    @Test void wholeToItemKeepsValuesAndVirtualCallbacks() {
        for (Spec spec : SPECS) for (int seed = 0; seed < 16; seed++) {
            assertSameBehavior(scripted(spec, seed, true, false), scripted(spec, seed, false, false),
                    true, spec.type.getSimpleName() + ":" + seed);
        }
    }

    @Test void rawIndicesAndExistingCachedControlsRetainFailures() {
        for (Spec spec : SPECS) for (int value : new int[]{Integer.MIN_VALUE, -7, -1, 0, 5, 7, 31, Integer.MAX_VALUE}) {
            Call expected = scripted(spec, 0, true, false);
            Call actual = scripted(spec, 0, false, false);
            doReturn(value).when(expected.block).getDamage();
            doReturn(value).when(actual.block).getDamage();
            if (expected.block instanceof BlockCoralFan left && actual.block instanceof BlockCoralFan right) {
                doReturn(value).when(left).getType();
                doReturn(value).when(right).getType();
            }
            assertSameBehavior(expected, actual, false, spec.type.getSimpleName() + ":" + value);
        }
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockBricksStone(BlockBricksStone block) {
        String[] names = new String[]{
                "Stone Bricks",
                "Mossy Stone Bricks",
                "Cracked Stone Bricks",
                "Chiseled Stone Bricks"
        };

        return names[block.getDamage() & 0x03];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockCoralBlock(BlockCoralBlock block) {
        String[] names = new String[] {
                "Tube Coral Block",
                "Brain Coral Block",
                "Bubble Coral Block",
                "Fire Coral Block",
                "Horn Coral Block",
                // Invalid
                "Tube Coral Block",
                "Tube Coral Block",
                "Tube Coral Block"
        };
        String name = names[block.getDamage() & 0x7];
        if (block.isDead()) {
            return "Dead " + name;
        } else {
            return name;
        }
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockCoralFan(BlockCoralFan block) {
        String[] names = new String[] {
                "Tube Coral Fan",
                "Brain Coral Fan",
                "Bubble Coral Fan",
                "Fire Coral Fan",
                "Horn Coral Fan"
        };
        return names[block.getType()];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockDoubleSlabRedSandstone(BlockDoubleSlabRedSandstone block) {
        String[] names = new String[]{
                "Red Sandstone",
                "Purpur",
                "",
                "",
                "",
                "",
                "",
                ""
        };

        return "Double " + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockDoubleSlabStone(BlockDoubleSlabStone block) {
        String[] names = new String[]{
                "Stone",
                "Sandstone",
                "Wooden",
                "Cobblestone",
                "Brick",
                "Stone Brick",
                "Quartz",
                "Nether Brick"
        };
        return "Double " + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockDoubleSlabStone3(BlockDoubleSlabStone3 block) {
        String[] names = new String[]{
                "End Stone Brick",
                "Smooth Red Sandstone",
                "Polished Andesite",
                "Andesite",
                "Diorite",
                "Polished Diorite",
                "Granite",
                "Polisehd Granite"
        };
        return "Double " + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockDoubleSlabStone4(BlockDoubleSlabStone4 block) {
        String[] names = new String[]{
                "Mossy Stone Brick",
                "Smooth Quartz",
                "Stone",
                "Cut Sandstone",
                "Cut Red Sandstone",
                "",
                "",
                ""
        };
        return "Double " + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockDoubleSlabWood(BlockDoubleSlabWood block) {
        String[] names = new String[]{
                "Oak",
                "Spruce",
                "Birch",
                "Jungle",
                "Acacia",
                "Dark Oak",
                "",
                ""
        };
        return "Double " + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockFence(BlockFence block) {
        String[] names = new String[]{
                "Oak Fence",
                "Spruce Fence",
                "Birch Fence",
                "Jungle Fence",
                "Acacia Fence",
                "Dark Oak Fence",
                "",
                ""
        };
        return names[block.getDamage() & 0x07];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockFlower(BlockFlower block) {
        String[] names = new String[]{"Poppy", "Blue Orchid", "Allium", "Azure Bluet", "Red Tulip", "Orange Tulip", "White Tulip", "Pink Tulip", "Oxeye Daisy", "Cornflower", "Lily of the Valley", "Unknown", "Unknown", "Unknown", "Unknown", "Unknown"};
        return names[block.getDamage() & 0x0f];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockLeaves(BlockLeaves block) {
        String[] names = new String[]{
                "Oak Leaves",
                "Spruce Leaves",
                "Birch Leaves",
                "Jungle Leaves"
        };
        return names[block.getDamage() & 0x03];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockLeaves2(BlockLeaves2 block) {
        String[] names = new String[]{"Acacia Leaves", "Dark Oak Leaves"};
        return names[block.getDamage() & 0x01];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockPlanks(BlockPlanks block) {
        String[] names = new String[]{
                "Oak Wood Planks",
                "Spruce Wood Planks",
                "Birch Wood Planks",
                "Jungle Wood Planks",
                "Acacia Wood Planks",
                "Dark Oak Wood Planks",
        };

        // palette carries junk stateless metas 6/7; normalize like the old % 6 ctor did
        return names[block.getDamage() % 6];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockPurpur(BlockPurpur block) {
        String[] names = new String[]{
                "Purpur Block",
                "",
                "Purpur Pillar",
                ""
        };

        return names[block.getDamage() & 0x03];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockQuartz(BlockQuartz block) {
        String[] names = new String[]{
                "Quartz Block",
                "Chiseled Quartz Block",
                "Quartz Pillar",
                "Quartz Pillar"
        };

        return names[block.getDamage() & 0x03];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockRedSandstone(BlockRedSandstone block) {
        String[] names = new String[]{
                "Red Sandstone",
                "Chiseled Red Sandstone",
                "Smooth Red Sandstone",
                ""
        };

        return names[block.getDamage() & 0x03];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockSandstone(BlockSandstone block) {
        String[] names = new String[]{
                "Sandstone",
                "Chiseled Sandstone",
                "Smooth Sandstone",
                ""
        };

        return names[block.getDamage() & 0x03];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockSapling(BlockSapling block) {
        String[] names = new String[]{
                "Oak Sapling",
                "Spruce Sapling",
                "Birch Sapling",
                "Jungle Sapling",
                "Acacia Sapling",
                "Dark Oak Sapling",
                "",
                ""
        };
        return names[block.getDamage() & BlockSapling.TYPE_BIT];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockSlabRedSandstone(BlockSlabRedSandstone block) {
        String[] names = new String[]{
                "Red Sandstone",
                "Purpur",
                "",
                "",
                "",
                "",
                "",
                ""
        };

        return ((block.getDamage() & 0x08) > 0 ? "Upper " : "") + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockSlabStone(BlockSlabStone block) {
        String[] names = new String[]{
                "Stone",
                "Sandstone",
                "Wooden",
                "Cobblestone",
                "Brick",
                "Stone Brick",
                "Quartz",
                "Nether Brick"
        };

        return ((block.getDamage() & 0x08) > 0 ? "Upper " : "") + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockSlabStone3(BlockSlabStone3 block) {
        String[] names = new String[]{
                "End Stone Brick",
                "Smooth Red Sandstone",
                "Polished Andesite",
                "Andesite",
                "Diorite",
                "Polished Diorite",
                "Granite",
                "Polisehd Granite"
        };

        return ((block.getDamage() & 0x08) > 0 ? "Upper " : "") + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockSlabStone4(BlockSlabStone4 block) {
        String[] names = new String[]{
                "Mossy Stone Brick",
                "Smooth Quartz",
                "Stone",
                "Cut Sandstone",
                "Cut Red Sandstone",
                "",
                "",
                ""
        };

        return ((block.getDamage() & 0x08) > 0 ? "Upper " : "") + names[block.getDamage() & 0x07] + " Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockSlabWood(BlockSlabWood block) {
        String[] names = new String[]{
                "Oak",
                "Spruce",
                "Birch",
                "Jungle",
                "Acacia",
                "Dark Oak",
                "",
                ""
        };
        return (((block.getDamage() & 0x08) == 0x08) ? "Upper " : "") + names[block.getDamage() & 0x07] + " Wooden Slab";
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockStone(BlockStone block) {
        String[] names = new String[]{
                "Stone",
                "Granite",
                "Polished Granite",
                "Diorite",
                "Polished Diorite",
                "Andesite",
                "Polished Andesite",
                "Unknown Stone"
        };
        return names[block.getDamage() & 0x07];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockTallGrass(BlockTallGrass block) {
        String[] names = new String[]{
                "Grass",
                "Grass",
                "Fern",
                "Fern"
        };
        return names[block.getDamage() & 0x03];
    }

    // Frozen upstream b216 getter oracle; retains callbacks and failure prefixes.
    private static String originalBlockWood(BlockWood block) {
        String[] names = new String[]{
                "Oak Wood",
                "Spruce Wood",
                "Birch Wood",
                "Jungle Wood"
        };

        return names[block.getDamage() & 0x03];
    }
}
