package cn.nukkit.entity.item;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityItemFullMergeStopTest {
    private static Item stack(int initialCount) {
        Item item = mock(Item.class);
        AtomicInteger count = new AtomicInteger(initialCount);
        when(item.getCount()).thenAnswer(invocation -> count.get());
        when(item.getMaxStackSize()).thenReturn(64);
        doAnswer(invocation -> { count.set(invocation.getArgument(0)); return null; }).when(item).setCount(anyInt());
        when(item.equals(any(Item.class), eq(true), eq(true))).thenReturn(true);
        return item;
    }

    private static EntityItem drop(int count) {
        EntityItem item = mock(EntityItem.class);
        item.item = stack(count);
        when(item.isAlive()).thenReturn(true);
        when(item.isOnGround()).thenReturn(true);
        return item;
    }

    private static EntityItem receiver(Level level) {
        EntityItem receiver = drop(32);
        when(receiver.onUpdate(anyInt())).thenCallRealMethod();
        when(receiver.getLevel()).thenReturn(level);
        when(receiver.getViewers()).thenReturn(Map.of());
        receiver.level = level;
        receiver.y = 64;
        receiver.age = 200;
        receiver.onGround = true;
        receiver.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 0.25, 64.25, 0.25);
        when(receiver.getBoundingBox()).thenReturn(receiver.boundingBox);
        Block air = mock(Block.class);
        when(level.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(receiver.getLevelBlock()).thenReturn(air);
        when(level.getBlock(any(Vector3.class), eq(1))).thenReturn(air);
        return receiver;
    }

    @Test
    void stopsAfterTheFirstSuccessfulMergeFillsTheStackAndPreservesAllItems() {
        Level level = mock(Level.class);
        EntityItem receiver = receiver(level);
        EntityItem[] neighbors = new EntityItem[500];
        for (int i = 0; i < neighbors.length; i++) neighbors[i] = drop(32);
        when(level.getNearbyEntities(any(AxisAlignedBB.class), same(receiver), eq(false))).thenReturn(neighbors);
        receiver.onUpdate(1);
        assertEquals(64, receiver.item.getCount());
        assertEquals(0, neighbors[0].item.getCount());
        verify(neighbors[0]).close();
        int total = receiver.item.getCount();
        for (int i = 1; i < neighbors.length; i++) {
            assertEquals(32, neighbors[i].item.getCount());
            verify(neighbors[i], never()).close();
            verify(neighbors[i].item, never()).equals(any(Item.class), eq(true), eq(true));
            total += neighbors[i].item.getCount();
        }
        assertEquals(501 * 32, total);
    }

    @Test
    void anIncompatibleNeighborRemainsUntouchedBeforeTheSuccessfulMerge() {
        Level level = mock(Level.class);
        EntityItem receiver = receiver(level);
        EntityItem protectedDrop = drop(32);
        when(protectedDrop.item.equals(any(Item.class), eq(true), eq(true))).thenReturn(false);
        EntityItem ordinaryDrop = drop(32);
        when(level.getNearbyEntities(any(AxisAlignedBB.class), same(receiver), eq(false)))
                .thenReturn(new Entity[]{protectedDrop, ordinaryDrop});
        receiver.onUpdate(1);
        assertEquals(64, receiver.item.getCount());
        assertEquals(32, protectedDrop.item.getCount());
        verify(protectedDrop, never()).close();
        verify(ordinaryDrop).close();
    }
}
