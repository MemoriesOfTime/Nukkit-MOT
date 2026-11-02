package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NearbyEntityBufferRetentionTest {
    private static final AxisAlignedBB QUERY = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);

    @BeforeAll
    static void initializeServer() { MockServer.init(); }

    static Entity[] buffer() throws Exception {
        Field field = Level.class.getDeclaredField("ENTITY_BUFFER");
        field.setAccessible(true);
        Object scratch = field.get(null);
        return scratch instanceof ThreadLocal<?> local ? (Entity[]) local.get() : (Entity[]) scratch;
    }

    @AfterEach
    void clearBuffer() throws Exception { Arrays.fill(buffer(), null); }

    private Level level(Map<Long, Entity> entities) {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        doReturn(Map.of()).when(level).getChunkEntities(anyInt(), anyInt(), anyBoolean());
        doReturn(entities).when(level).getChunkEntities(0, 0, false);
        return level;
    }

    private Entity entity() {
        Entity entity = new ObjenesisStd().newInstance(EntityItem.class);
        entity.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);
        return entity;
    }

    @Test
    void overflowReturnsAllEntitiesInOrderWithoutRetainingAny() throws Exception {
        Map<Long, Entity> entities = new LinkedHashMap<>();
        for (long i = 0; i < 513; i++) entities.put(i, entity());
        Level level = level(entities);
        assertArrayEquals(entities.values().toArray(new Entity[0]), level.getNearbyEntities(QUERY));
        assertEquals(0, Arrays.stream(buffer()).filter(e -> e != null).count(), "overflow leaves no static entity roots");
    }

    @Test
    void throwingBoundsDoesNotKeepPreviouslyMatchedEntities() throws Exception {
        Map<Long, Entity> entities = new LinkedHashMap<>();
        entities.put(0L, entity());
        Entity throwing = entity();
        throwing.boundingBox = mock(AxisAlignedBB.class);
        when(throwing.boundingBox.intersectsWith(QUERY)).thenThrow(new IllegalStateException("test bounds failure"));
        entities.put(1L, throwing);
        Level level = level(entities);
        assertThrows(IllegalStateException.class, () -> level.getNearbyEntities(QUERY));
        assertEquals(0, Arrays.stream(buffer()).filter(e -> e != null).count(), "exceptions must release the written prefix");
    }

    @Test
    void smallAndEmptyQueriesPreserveResultsAndClearScratchBuffer() throws Exception {
        Entity entity = entity();
        Level level = level(Map.of(0L, entity));
        assertArrayEquals(new Entity[]{entity}, level.getNearbyEntities(QUERY));
        assertEquals(0, Arrays.stream(buffer()).filter(e -> e != null).count());
        doReturn(Map.of()).when(level).getChunkEntities(0, 0, false);
        assertEquals(0, level.getNearbyEntities(QUERY).length);
        assertEquals(0, Arrays.stream(buffer()).filter(e -> e != null).count());
    }
}
