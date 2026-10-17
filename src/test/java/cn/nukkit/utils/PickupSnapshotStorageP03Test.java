package cn.nukkit.utils;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupSnapshotStorageP03Test {
    private static final AxisAlignedBB AREA = new SimpleAxisAlignedBB(8,64,8,9,65,9);
    private static final AxisAlignedBB INSIDE = new SimpleAxisAlignedBB(8.1,64.1,8.1,8.9,64.9,8.9);
    private static final AxisAlignedBB OUTSIDE = new SimpleAxisAlignedBB(32,64,32,33,65,33);

    @Test
    void listAndIteratorMutationDoNotChangeIndexOrAnotherSnapshot() {
        Fixture f = new Fixture(); EntityItem a = f.add(1, EntityItem.class), b = f.add(2, EntityItem.class), c = f.add(3, EntityItem.class);
        List<Entity> first = f.query(false), retained = f.query(false);
        assertNotSame(first, retained); assertEquals(List.of(a,b,c), first);
        Iterator<Entity> iterator = first.iterator(); assertSame(a, iterator.next()); iterator.remove();
        assertEquals(List.of(b,c), first); assertEquals(List.of(a,b,c), retained);
        first.set(0,a); first.add(c); first.remove(1); first.clear();
        assertEquals(List.of(a,b,c), retained); assertEquals(List.of(a,b,c), List.copyOf(f.entities.values()));
        assertEquals(List.of(a,b,c), f.query(false)); verify(f.chunk,times(3)).getEntities();
    }

    @Test
    void trueUsesVirtualBoundsAndFalseUsesTheDirectField() {
        Fixture f = new Fixture(); EntityItem item = f.add(1, EntityItem.class);
        when(item.getBoundingBox()).thenReturn(OUTSIDE);
        assertSame(Collections.emptyList(), f.query(true));
        assertEquals(List.of(item), f.query(false)); verify(item).getBoundingBox();
    }

    @Test
    void reentrantGetterOwnsAnIndependentCompletedSnapshot() {
        Fixture f = new Fixture(); EntityItem item = f.add(1, EntityItem.class);
        AtomicBoolean entered = new AtomicBoolean(); AtomicReference<List<Entity>> inner = new AtomicReference<>();
        when(item.getBoundingBox()).thenAnswer(invocation -> {
            if (entered.compareAndSet(false,true)) inner.set(f.query(true)); return INSIDE;
        });
        List<Entity> outer = f.query(true);
        assertEquals(List.of(item), outer); assertEquals(List.of(item), inner.get()); assertNotSame(outer,inner.get());
        outer.clear(); assertEquals(List.of(item), inner.get()); assertSame(item,f.entities.get(1L));
        verify(item,times(2)).getBoundingBox(); verify(f.chunk,times(2)).getEntities();
    }

    @Test
    void fourKindsKeepOrderAndNonItemFilteringPrecedesGetterCalls() {
        Fixture f = new Fixture(); EntityItem item = f.add(1,EntityItem.class); EntityXPOrb xp=f.add(2,EntityXPOrb.class);
        EntityArrow arrow=f.add(3,EntityArrow.class); EntityThrownTrident trident=f.add(4,EntityThrownTrident.class);
        assertEquals(List.of(item,xp,arrow,trident), f.query(false)); assertEquals(List.of(item), f.query(true));
        verify(item).getBoundingBox(); verify(xp,never()).getBoundingBox(); verify(arrow,never()).getBoundingBox(); verify(trident,never()).getBoundingBox();
    }

    private static final class Fixture {
        final Level level=mock(Level.class); final BaseFullChunk chunk=mock(BaseFullChunk.class);
        final Map<Long,Entity> entities=new LinkedHashMap<>();
        Fixture() {
            when(level.getChunkIfLoaded(0,0)).thenReturn(chunk); when(chunk.hasPickupEntities(anyBoolean())).thenReturn(true); when(chunk.getEntities()).thenReturn(entities);
        }
        <T extends Entity> T add(long id,Class<T> type) { T entity=mock(type);entity.boundingBox=INSIDE;when(entity.getBoundingBox()).thenReturn(INSIDE);entities.put(id,entity);return entity; }
        List<Entity> query(boolean itemsOnly) { return CollisionHelper.getPickupEntities(level,AREA,itemsOnly); }
    }
}
