package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发配方构造的 networkId 唯一性：插件运行时注册与世界线程读索引并发的场景下，
 * 裸 ++ 自增会撞号，SAI 按 networkId 反查将命中错配方。
 * <p>
 * Network-id uniqueness under concurrent recipe construction: a bare ++ hands out
 * duplicates, and SAI networkId lookups then resolve to the wrong recipe.
 */
class CraftingManagerNetworkIdConcurrencyTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void concurrentRecipeConstructionNeverHandsOutDuplicateNetworkIds() throws Exception {
        int threads = 4;
        int perThread = 1000;
        Set<Integer> ids = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    ids.add(new FurnaceRecipe(Item.get(Item.STONE, 0, 1), Item.get(Item.COBBLESTONE, 0, 1)).getNetworkId());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(threads * perThread, ids.size(),
                "concurrent recipe construction must hand out unique network ids");
    }
}
