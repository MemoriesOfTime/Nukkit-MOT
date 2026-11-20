package cn.nukkit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDirt;
import cn.nukkit.block.BlockStone;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class BlockUpdateEntryHashTest {

    private static Block blockPosition(double x, double y, double z) {
        Block block = new BlockDirt();
        block.setComponents(x, y, z);
        return block;
    }

    private static BlockUpdateEntry entry(Vector3 position) {
        return new BlockUpdateEntry(position, new BlockStone(), 10, 0);
    }

    @Test
    void equalNativePositionTypesRemainInterchangeableHashKeys() {
        List<Vector3> positions = List.of(
                new Vector3(-17, 65, 31),
                new Position(-17, 65, 31),
                new Location(-17, 65, 31, 90, 45),
                blockPosition(-17, 65, 31));
        for (Vector3 storedPosition : positions) {
            for (Vector3 queryPosition : positions) {
                BlockUpdateEntry stored = entry(storedPosition);
                BlockUpdateEntry query = entry(queryPosition);
                assertEquals(stored, query);
                assertEquals(query, stored);
                assertEquals(stored.hashCode(), query.hashCode());
                assertInterchangeable(new HashSet<>(), stored, query);
                assertInterchangeable(ConcurrentHashMap.newKeySet(), stored, query);
            }
        }
    }

    private static void assertInterchangeable(Set<BlockUpdateEntry> entries,
                                              BlockUpdateEntry stored, BlockUpdateEntry query) {
        assertTrue(entries.add(stored));
        assertTrue(entries.contains(query));
        assertFalse(entries.add(query));
        assertEquals(1, entries.size());
        assertTrue(entries.remove(query));
        assertTrue(entries.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 63, 64, 65, -64, 319})
    void schedulerCanFindDeduplicateAndCancelUsingNativeBlockPosition(int height) {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(null, 0);
        Block block = blockPosition(-1, height, 17);
        BlockUpdateEntry scheduled = new BlockUpdateEntry(block.floor(), block, 10, 0);
        BlockUpdateEntry query = new BlockUpdateEntry(block, block, 10, 0);
        scheduler.add(scheduled);
        assertTrue(scheduler.contains(query), "Block query must find the floored scheduled key");
        scheduler.add(query);
        assertEquals(1, scheduler.getPendingCount());
        assertTrue(scheduler.remove(query));
        assertFalse(scheduler.contains(scheduled));
        assertEquals(0, scheduler.getPendingCount());
    }

    @Test
    void exactFractionalCoordinatesAreNotChangedIntoFloorEquality() {
        BlockUpdateEntry first = entry(new Vector3(-1.25, 65.5, 3.75));
        BlockUpdateEntry same = entry(blockPosition(-1.25, 65.5, 3.75));
        BlockUpdateEntry different = entry(new Position(-1.75, 65.5, 3.75));
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
        Set<BlockUpdateEntry> entries = new HashSet<>();
        assertTrue(entries.add(first));
        assertTrue(entries.add(different));
        assertEquals(2, entries.size());
    }

    @Test
    void metadataAndSchedulingOrderDoNotChangeExistingKeyEquality() {
        BlockUpdateEntry first = new BlockUpdateEntry(new Vector3(1, 65, 3), new BlockStone(0), 10, 5);
        BlockUpdateEntry same = new BlockUpdateEntry(blockPosition(1, 65, 3), new BlockStone(3), 20, -5);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(0, first.compareTo(same));
        assertNotEquals(first, new BlockUpdateEntry(new Position(1, 65, 3), new BlockDirt()));
    }

    @Test
    void signedZeroAndEqualInfiniteCoordinatesKeepCoordinateEquality() {
        BlockUpdateEntry positive = entry(new Vector3(+0.0, -0.0, +0.0));
        BlockUpdateEntry negative = entry(blockPosition(-0.0, +0.0, -0.0));
        assertEquals(positive, negative);
        assertEquals(positive.hashCode(), negative.hashCode());
        BlockUpdateEntry infinite = entry(new Vector3(Double.POSITIVE_INFINITY, 1, Double.NEGATIVE_INFINITY));
        BlockUpdateEntry same = entry(new Position(Double.POSITIVE_INFINITY, 1, Double.NEGATIVE_INFINITY));
        assertEquals(infinite, same);
        assertEquals(infinite.hashCode(), same.hashCode());
    }

    @Test
    void coordinateKeysDoNotDelegateToUnrelatedPositionHashOrGetters() {
        Vector3 position = new Vector3(-17, 65, 31) {
            @Override
            public int hashCode() {
                throw new AssertionError("A position subclass hash is not the entry's key hash");
            }

            @Override
            public double getX() {
                throw new AssertionError("Coordinate equality reads the public fields");
            }
        };
        BlockUpdateEntry stored = entry(new Position(-17, 65, 31));
        BlockUpdateEntry query = entry(position);
        assertEquals(stored, query);
        assertEquals(stored.hashCode(), query.hashCode());
        assertInterchangeable(new HashSet<>(), stored, query);
    }
}
