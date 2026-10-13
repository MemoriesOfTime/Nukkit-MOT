package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 复合操作并发压力：addItem/setItem 多线程并发写同一背包不丢不复制物品
 * （slots 监视器守护复合段；修复前为裸 check-then-act，可静默丢/复制物品）。
 * <p>
 * Concurrency stress for the compound operations: concurrent addItem/setItem on one
 * inventory must neither lose nor duplicate items (slots-monitor-protected compounds;
 * before the fix they were bare check-then-act races).
 */
class BaseInventoryConcurrencyTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    private static final class TestInventory extends BaseInventory {

        TestInventory() {
            super(Mockito.mock(InventoryHolder.class), InventoryType.CHEST);
        }
    }

    @Test
    void concurrentAddItemNeverLosesOrDuplicatesItems() throws Exception {
        TestInventory inventory = new TestInventory();
        int threads = 4;
        int perThread = 200;
        // 27 槽 × 64 = 1728 ≥ 800，全部应装入，leftover 恒 0
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Item[]>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                Item[] leftover = Item.EMPTY_ARRAY;
                for (int i = 0; i < perThread; i++) {
                    leftover = inventory.addItem(Item.get(Item.STONE, 0, 1));
                }
                return leftover;
            }));
        }
        start.countDown();

        int leftoverCount = 0;
        for (Future<Item[]> future : futures) {
            for (Item item : future.get(60, TimeUnit.SECONDS)) {
                if (item != null) {
                    leftoverCount += item.getCount();
                }
            }
        }
        pool.shutdown();

        int inInventory = 0;
        for (Item item : inventory.getContents().values()) {
            inInventory += item.getCount();
        }
        assertEquals(threads * perThread, inInventory + leftoverCount,
                "concurrent addItem must not lose or duplicate items");
    }

    @Test
    void concurrentDecreaseCountNeverLosesADecrement() throws Exception {
        TestInventory inventory = new TestInventory();
        int decrements = 64;
        inventory.setItemForce(5, Item.get(Item.STONE, 0, decrements));
        // 修复前两线程交错会丢一次递减（计数偏大）/ interleaved calls used to lose a decrement
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < decrements; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                inventory.decreaseCount(5);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(0, inventory.getItem(5).getCount(),
                "every concurrent decrement must land exactly once");
    }

    @Test
    void concurrentRemoveItemNeverLosesARemoval() throws Exception {
        TestInventory inventory = new TestInventory();
        int removals = 64;
        inventory.setItemForce(5, Item.get(Item.STONE, 0, removals));
        // 修复前两线程读到同一 count 各写回，丢一次扣减 / interleaved calls both read
        // the same count and lose one decrement
        int leftoverCount = 0;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Item[]>> futures = new ArrayList<>();
        for (int i = 0; i < removals; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return inventory.removeItem(Item.get(Item.STONE, 0, 1));
            }));
        }
        start.countDown();
        for (Future<Item[]> future : futures) {
            for (Item item : future.get(60, TimeUnit.SECONDS)) {
                if (item != null) {
                    leftoverCount += item.getCount();
                }
            }
        }
        pool.shutdown();
        assertEquals(0, leftoverCount, "every removal is satisfiable, none may be returned un-removed");
        assertEquals(0, inventory.getItem(5).getCount(),
                "every concurrent removal must land exactly once");
    }

    @Test
    void concurrentAddAndRemoveKeepItemAccounting() throws Exception {
        TestInventory inventory = new TestInventory();
        int adders = 2;
        int removers = 2;
        int perThread = 300;
        // 容量 27x64 >= 600，adder 侧必然全装入；两侧以"装入-移除=终态"闭环记账，锁缺失则破坏等式
        // Capacity fits all adds; added - removed == final holds only when both compounds are atomic
        ExecutorService pool = Executors.newFixedThreadPool(adders + removers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int t = 0; t < adders; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                int unplaced = 0;
                for (int i = 0; i < perThread; i++) {
                    // 逐次累加：只留最后一次返回会丢中途未装入的堆 / accumulate per call:
                    // keeping only the last return loses mid-run unplaced stacks
                    for (Item item : inventory.addItem(Item.get(Item.STONE, 0, 1))) {
                        if (item != null) {
                            unplaced += item.getCount();
                        }
                    }
                }
                return unplaced;
            }));
        }
        for (int t = 0; t < removers; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                int unremoved = 0;
                for (int i = 0; i < perThread; i++) {
                    for (Item item : inventory.removeItem(Item.get(Item.STONE, 0, 1))) {
                        if (item != null) {
                            unremoved += item.getCount();
                        }
                    }
                }
                return unremoved;
            }));
        }
        start.countDown();

        int addLeftover = 0;
        int removeLeftover = 0;
        for (int i = 0; i < futures.size(); i++) {
            int leftover = futures.get(i).get(60, TimeUnit.SECONDS);
            if (i < adders) {
                addLeftover += leftover;
            } else {
                removeLeftover += leftover;
            }
        }
        pool.shutdown();

        int added = adders * perThread - addLeftover;
        int removed = removers * perThread - removeLeftover;
        int inInventory = 0;
        for (Item item : inventory.getContents().values()) {
            inInventory += item.getCount();
        }
        assertEquals(added, removed + inInventory,
                "concurrent addItem/removeItem must keep item accounting closed");
    }
}
