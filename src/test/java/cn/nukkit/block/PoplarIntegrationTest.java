package cn.nukkit.block;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.inventory.ItemTag;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBoat;
import cn.nukkit.item.ItemChestBoatPoplar;
import cn.nukkit.item.ItemID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 白杨木系列的跨系统接线：树叶状态位与枯死、方块类型、燃料、物品标签、船。 */
class PoplarIntegrationTest {

    private static final int[] POPLAR_LEAVES = {
            BlockID.ORANGE_POPLAR_LEAVES, BlockID.RED_POPLAR_LEAVES, BlockID.YELLOW_POPLAR_LEAVES
    };

    @BeforeAll
    static void initialize() {
        MockServer.init();
        Block.init();
    }

    @Test
    void leafStateBitsFollowThePaletteLayout() {
        for (int id : POPLAR_LEAVES) {
            assertFalse(leaves(id, 0).isPersistent(), id + ": bottom, no update");
            assertFalse(leaves(id, 0).isCheckDecay(), id + ": bottom, no update");
            assertFalse(leaves(id, 1).isPersistent(), id + ": update only");
            assertTrue(leaves(id, 1).isCheckDecay(), id + ": update only");
            assertTrue(leaves(id, 2).isPersistent(), id + ": persistent only");
            assertFalse(leaves(id, 2).isCheckDecay(), id + ": persistent only");
            assertTrue(leaves(id, 3).isPersistent(), id + ": persistent + update");
            assertTrue(leaves(id, 3).isCheckDecay(), id + ": persistent + update");
        }
    }

    @Test
    void placedLeavesArePersistentWithUpdateBit() {
        BlockLeaves leaves = leaves(BlockID.ORANGE_POPLAR_LEAVES, 0);
        leaves.setPersistent(true);

        assertEquals(BlockLeavesPoplar.PERSISTENT_BIT | BlockLeavesPoplar.UPDATE_BIT, leaves.getDamage());
        assertTrue(leaves.isPersistent());

        leaves.setCheckDecay(false);
        assertFalse(leaves.isCheckDecay());
        assertEquals(BlockLeavesPoplar.PERSISTENT_BIT, leaves.getDamage());
    }

    @Test
    void decaySearchRecognisesPoplarLeavesAndLogs() {
        BlockLeaves leaves = leaves(BlockID.ORANGE_POPLAR_LEAVES, 0);
        for (int id : POPLAR_LEAVES) {
            assertTrue(leaves.isLeaves(id), "leaf id " + id);
        }
        assertTrue(leaves.isLog(BlockID.POPLAR_LOG, 0), "poplar log");
        assertTrue(leaves.isLog(BlockID.POPLAR_WOOD, 16), "poplar wood");
        assertFalse(leaves.isLog(BlockID.POPLAR_PLANKS, 0), "planks are not logs");
    }

    @Test
    void blockTypeChecksCoverTheNewFamilies() {
        assertTrue(Block.isStairs(BlockID.POPLAR_STAIRS));
        assertTrue(Block.isSlab(BlockID.POPLAR_SLAB));
        assertTrue(Block.isButton(BlockID.POPLAR_BUTTON));
        assertTrue(Block.isFence(BlockID.POPLAR_FENCE));
        assertTrue(Block.isFenceGate(BlockID.POPLAR_FENCE_GATE));
        assertTrue(Block.isPressurePlate(BlockID.POPLAR_PRESSURE_PLATE));
        assertTrue(Block.isTrapdoor(BlockID.POPLAR_TRAPDOOR));
        assertTrue(Block.isDoor(BlockID.POPLAR_DOOR));
        assertTrue(Block.isSlab(BlockID.WHITE_WOOL_SLAB));
        assertTrue(Block.isStairs(BlockID.WHITE_WOOL_STAIRS));
        assertTrue(Block.isSlab(BlockID.PINK_CONCRETE_SLAB));
        assertTrue(Block.isStairs(BlockID.PINK_CONCRETE_STAIRS));
    }

    @Test
    void poplarWoodBurnsLikeOtherWoodTypes() {
        assertEquals(300, fuel("poplar_planks"));
        assertEquals(300, fuel("poplar_log"));
        assertEquals(300, fuel("stripped_poplar_log"));
        assertEquals(300, fuel("poplar_wood"));
        assertEquals(300, fuel("stripped_poplar_wood"));
        assertEquals(300, fuel("poplar_slab"));
        assertEquals(300, fuel("poplar_stairs"));
        assertEquals(300, fuel("poplar_fence"));
        assertEquals(300, fuel("poplar_fence_gate"));
        assertEquals(300, fuel("poplar_pressure_plate"));
        assertEquals(300, fuel("poplar_trapdoor"));
        assertEquals(100, fuel("poplar_button"));
        assertEquals(100, fuel("poplar_sapling"));
        assertEquals(200, fuel("poplar_sign"));
        assertEquals(200, fuel("poplar_hanging_sign"));
        assertEquals(200, fuel("poplar_door"));
        // 普通船共用物品 id、变体在 meta；带箱船每种木料一个 id
        assertEquals(1200, fuel("poplar_boat"));
        assertEquals(1200, fuel("poplar_chest_boat"));
    }

    @Test
    void itemTagsAcceptPoplarMaterials() {
        assertTagContains("minecraft:planks", "minecraft:poplar_planks");
        assertTagContains("minecraft:wooden_slabs", "minecraft:poplar_slab");
        assertTagContains("minecraft:door", "minecraft:poplar_door");
        assertTagContains("minecraft:sign", "minecraft:poplar_sign");
        assertTagContains("minecraft:sign", "minecraft:poplar_hanging_sign");
        assertTagContains("minecraft:hanging_sign", "minecraft:poplar_hanging_sign");
        assertTagContains("minecraft:boat", "minecraft:poplar_boat");
        assertTagContains("minecraft:boats", "minecraft:poplar_chest_boat");
        for (String log : new String[]{"minecraft:poplar_log", "minecraft:poplar_wood",
                "minecraft:stripped_poplar_log", "minecraft:stripped_poplar_wood"}) {
            assertTagContains("minecraft:logs", log);
            assertTagContains("minecraft:logs_that_burn", log);
        }
    }

    @Test
    void boatsExistForPoplar() {
        Item boat = Item.fromString("minecraft:poplar_boat");
        assertInstanceOf(ItemBoat.class, boat);
        assertEquals(10, boat.getDamage());
        assertFalse(boat.isSupportedOn(GameVersion.V1_21_130));
        assertTrue(boat.isSupportedOn(GameVersion.V1_26_40));

        Item chestBoat = Item.fromString("minecraft:poplar_chest_boat");
        ItemChestBoatPoplar poplarChestBoat = assertInstanceOf(ItemChestBoatPoplar.class, chestBoat);
        assertEquals(ItemID.POPLAR_CHEST_BOAT, poplarChestBoat.getId());
        assertEquals(10, poplarChestBoat.getBoatId());
        assertFalse(poplarChestBoat.isSupportedOn(GameVersion.V1_21_130));
        assertTrue(poplarChestBoat.isSupportedOn(GameVersion.V1_26_40));
    }

    private static BlockLeaves leaves(int id, int meta) {
        return (BlockLeaves) Block.get(id, meta);
    }

    private static int fuel(String identifier) {
        Item item = Item.fromString("minecraft:" + identifier);
        Short time = item.getFuelTime();
        assertNotNull(time, identifier + " must be a fuel");
        return time;
    }

    private static void assertTagContains(String tag, String identifier) {
        assertTrue(ItemTag.getItemSet(tag).contains(identifier), tag + " must contain " + identifier);
    }
}
