package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 超叠拆分（splitOverstack/routeOverflow/setContents 延迟溢出）的行为回归。
 * <p>
 * Behavior regression for overstack splitting: splitOverstack, routeOverflow
 * and the deferred overflow routing in setContents.
 */
class OverstackSplitTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    /** null holder 的最小库存：无事件、无网络包，溢出无处掉落时记 warn 丢弃 */
    private static final class TestInventory extends BaseInventory {
        TestInventory(int size) {
            super(null, InventoryType.CHEST, new HashMap<>(), size);
        }
    }

    /** 模拟 ShelfInventory 式的"覆写 getter 收紧单槽上限"（字段仍是默认 64） */
    private static final class LimitOneInventory extends BaseInventory {
        LimitOneInventory(int size) {
            super(null, InventoryType.CHEST, new HashMap<>(), size);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    @Test
    void setItemSplitsOverstackIntoSlotAndRoutesOverflow() {
        TestInventory inventory = new TestInventory(3);
        Item overstacked = Item.get(Item.STONE, 0, 100);

        assertTrue(inventory.setItem(0, overstacked, false));

        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(36, inventory.getItem(1).getCount());
        assertTrue(inventory.getItem(2).isNull());
        // 拆分不动调用方传入的对象
        assertEquals(100, overstacked.getCount());
        // 溢出是独立堆：netId 非零且互不相同（SAI 堆唯一标识）
        int slot0NetId = inventory.getItem(0).getStackNetId();
        int slot1NetId = inventory.getItem(1).getStackNetId();
        assertNotEquals(0, slot0NetId);
        assertNotEquals(0, slot1NetId);
        assertNotEquals(slot0NetId, slot1NetId);
    }

    @Test
    void setItemDiscardsOverflowWhenInventoryCannotHoldIt() {
        TestInventory inventory = new TestInventory(2);

        assertTrue(inventory.setItem(0, Item.get(Item.STONE, 0, 300), false));

        // 装得下的部分保持封顶堆；剩余在无 holder 库存中丢弃（仅 warn）
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(64, inventory.getItem(1).getCount());
    }

    @Test
    void setContentsRoutesOverflowOnlyAfterAllSlotsAreFinal() {
        TestInventory inventory = new TestInventory(3);
        Map<Integer, Item> items = new HashMap<>();
        items.put(0, Item.get(Item.STONE, 0, 100));
        items.put(1, Item.get(Item.DIRT, 0, 1));

        inventory.setContents(items);

        // 若溢出在循环中途路由，36 个石头会先落进槽 1，再被 dirt 的 setItem 覆盖丢失
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(Item.DIRT, inventory.getItem(1).getId());
        assertEquals(1, inventory.getItem(1).getCount());
        assertEquals(36, inventory.getItem(2).getCount());
        assertEquals(Item.STONE, inventory.getItem(2).getId());
    }

    @Test
    void setItemForcePreservesSnapshotInFullInventory() {
        TestInventory inventory = new TestInventory(3);
        inventory.setItemForce(1, Item.get(Item.DIRT, 0, 64));
        inventory.setItemForce(2, Item.get(Item.SAND, 0, 64));
        Item snapshot = Item.get(Item.STONE, 0, 150);
        snapshot.autoAssignStackNetworkId();

        inventory.setItemForce(0, snapshot);

        assertEquals(150, inventory.getItem(0).getCount());
        assertEquals(Item.STONE, inventory.getItem(0).getId());
        assertEquals(snapshot.getDamage(), inventory.getItem(0).getDamage());
        assertEquals(64, inventory.getItem(1).getCount());
        assertEquals(Item.DIRT, inventory.getItem(1).getId());
        assertEquals(64, inventory.getItem(2).getCount());
        assertEquals(Item.SAND, inventory.getItem(2).getId());
        assertEquals(snapshot.getStackNetId(), inventory.getItem(0).getStackNetId());
    }

    @Test
    void setItemForceDoesNotOverwriteLaterSnapshotSlotsWithOverflow() {
        TestInventory inventory = new TestInventory(3);

        inventory.setItemForce(0, Item.get(Item.STONE, 0, 150));
        inventory.setItemForce(1, Item.get(Item.DIAMOND, 0, 11));

        assertEquals(150, inventory.getItem(0).getCount());
        assertEquals(Item.DIAMOND, inventory.getItem(1).getId());
        assertEquals(11, inventory.getItem(1).getCount());
        assertTrue(inventory.getItem(2).isNull());
        assertEquals(161, inventory.getContents().values().stream().mapToInt(Item::getCount).sum());
    }

    @Test
    void overriddenMaxStackSizeGetterLimitsTheSplit() {
        // 覆写 getter 收紧到 1：修复前 splitOverstack 按字段 64 计算，5 个不会拆
        LimitOneInventory inventory = new LimitOneInventory(2);

        assertTrue(inventory.setItem(0, Item.get(Item.STONE, 0, 5), false));

        assertEquals(1, inventory.getItem(0).getCount());
        assertEquals(1, inventory.getItem(1).getCount());
    }

    @Test
    void setItemForcePreservesSnapshotCountDespiteTighterSlotLimit() {
        LimitOneInventory inventory = new LimitOneInventory(2);

        inventory.setItemForce(0, Item.get(Item.STONE, 0, 3));

        assertEquals(3, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isNull());
    }

    @Test
    void doubleChestSetContentsRoutesOverflowAfterSlotsAreFinal() {
        ChestInventory left = new ChestInventory(null);
        ChestInventory right = new ChestInventory(null);
        BlockEntityChest leftChest = Mockito.mock(BlockEntityChest.class);
        BlockEntityChest rightChest = Mockito.mock(BlockEntityChest.class);
        Mockito.when(leftChest.getRealInventory()).thenReturn(left);
        Mockito.when(rightChest.getRealInventory()).thenReturn(right);

        DoubleChestInventory inventory = new DoubleChestInventory(leftChest, rightChest);
        Map<Integer, Item> items = new HashMap<>();
        items.put(0, Item.get(Item.STONE, 0, 100));
        items.put(1, Item.get(Item.DIRT, 0, 1));
        inventory.setContents(items);

        assertEquals(64, left.getItem(0).getCount());
        assertEquals(Item.DIRT, left.getItem(1).getId());
        assertEquals(36, left.getItem(2).getCount());
        assertEquals(Item.STONE, left.getItem(2).getId());
    }
}
