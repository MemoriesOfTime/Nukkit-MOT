package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class LevelUpdateAroundLayerTest {

    @BeforeAll
    static void initialize() {
        MockServer.init();
        Block.init();
    }

    @Test
    void layerOneUpdatesOnlyReachWaterloggedNeighbours() throws Exception {
        Level level = mock(Level.class);
        Queue<Object> queue = new ConcurrentLinkedQueue<>();
        field(level, "normalUpdateQueue", queue);
        field(level, "normalUpdateQueueSize", new AtomicInteger());
        doCallRealMethod().when(level).updateAround(any(Vector3.class));
        doCallRealMethod().when(level).updateAround(any(Vector3.class), anyInt());
        when(level.getBlock(any(Vector3.class))).thenReturn(at(level, Block.STONE, 0, 0, 64, 0));
        // Every neighbour is stone on layer 0; on layer 1 only the east one holds water.
        when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2), layer = call.getArgument(3);
            if (layer == 0) return at(level, Block.STONE, 0, x, y, z);
            return at(level, x == 1 ? Block.STILL_WATER : Block.AIR, 1, x, y, z);
        });

        level.updateAround(new Vector3(0, 64, 0));

        List<Block> layerOne = new ArrayList<>();
        int layerZero = 0;
        for (Object update : queue) {
            Method getter = update.getClass().getDeclaredMethod("getBlock");
            getter.setAccessible(true);
            Block block = (Block) getter.invoke(update);
            if (block.layer == 1) layerOne.add(block); else layerZero++;
        }
        assertEquals(6, layerZero);
        assertEquals(1, layerOne.size());
        assertEquals(Block.STILL_WATER, layerOne.get(0).getId());
        assertEquals(1, layerOne.get(0).getFloorX());
        assertEquals(7, ((AtomicInteger) get(level, "normalUpdateQueueSize")).get());
    }

    private static Block at(Level level, int id, int layer, int x, int y, int z) {
        Block block = Block.get(id);
        block.x = x;
        block.y = y;
        block.z = z;
        block.level = level;
        block.layer = layer;
        return block;
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
