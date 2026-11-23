package cn.nukkit.level.format.generic;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.level.format.EntityPresenceIndex;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Typed presence cache over a chunk's existing entity map. Membership mutations invalidate the
 * cache, including the public Map API and mutable views. Iteration remains the backing map's
 * weakly consistent order; neither a second entity map nor a reordered typed snapshot is kept.
 */
final class PickupEntityIndex extends AbstractMap<Long, Entity> implements ConcurrentMap<Long, Entity>, EntityPresenceIndex {
    private static final int KIND_ITEM = 1;
    private static final int KIND_PICKUP_OTHER = 2;
    private static final int KIND_ANY_PICKUP = KIND_ITEM | KIND_PICKUP_OTHER;
    private static final int KIND_RIDEABLE = 4;
    private static final int KIND_ANY = 8;
    private static final int ALL_KINDS = KIND_ANY_PICKUP | KIND_RIDEABLE | KIND_ANY;

    private final Long2ObjectNonBlockingMap<Entity> backing;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicInteger writers = new AtomicInteger();
    private record Presence(long revision, int kinds) { }

    private volatile Presence cached = new Presence(-1, 0);
    private Collection<Entity> valuesView;
    private Set<Long> keySetView;
    private Set<Entry<Long, Entity>> entrySetView;

    PickupEntityIndex(Long2ObjectNonBlockingMap<Entity> backing) {
        this.backing = backing;
    }

    Long2ObjectNonBlockingMap<Entity> backing() {
        return backing;
    }

    @Override
    public boolean hasAnyEntities() {
        return hasKind(KIND_ANY);
    }

    @Override
    public boolean hasPickupEntities(boolean itemsOnly) {
        return hasKind(itemsOnly ? KIND_ITEM : KIND_ANY_PICKUP);
    }

    @Override
    public boolean hasRideableEntities() {
        return hasKind(KIND_RIDEABLE);
    }

    void scheduleUpdates() {
        if (hasAnyEntities()) {
            for (Entity entity : backing.values()) {
                entity.scheduleUpdate();
            }
        }
    }

    Iterator<Entity> readOnlyNativeIterator() {
        if (backing.getClass() != Long2ObjectNonBlockingMap.class) return null;
        return hasAnyEntities() ? backing.readOnlyValueIterator() : Collections.emptyIterator();
    }

    private boolean hasKind(int mask) {
        long stamp = revision.get();
        if (writers.get() != 0) return true;
        Presence present = cached;
        if (present.revision == stamp) return (present.kinds & mask) != 0;
        int kinds = 0;
        for (Entity entity : backing.values()) {
            kinds |= KIND_ANY;
            if (FullChunk.isPickupEntity(entity, true)) kinds |= KIND_ITEM;
            else if (FullChunk.isPickupEntity(entity, false)) kinds |= KIND_PICKUP_OTHER;
            if (entity instanceof EntityRideable) kinds |= KIND_RIDEABLE;
            if (kinds == ALL_KINDS) break;
        }
        // A concurrent insertion may have been missed by the weakly consistent iterator.
        // In-flight/changing membership means "possibly present", never a false empty result.
        if (writers.get() != 0 || revision.get() != stamp) return true;
        cached = new Presence(stamp, kinds);
        return (kinds & mask) != 0;
    }

    private void beginWrite() {
        writers.incrementAndGet();
    }

    private void endWrite() {
        revision.incrementAndGet();
        writers.decrementAndGet();
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return !hasAnyEntities() && backing.isEmpty();
    }

    @Override
    public Entity get(Object key) {
        return backing.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return backing.containsKey(key);
    }

    @Override
    public Entity put(Long key, Entity value) {
        beginWrite();
        try { return backing.put(key, value); }
        finally { endWrite(); }
    }

    @Override
    public Entity remove(Object key) {
        beginWrite();
        try { return backing.remove(key); }
        finally { endWrite(); }
    }

    @Override
    public void putAll(Map<? extends Long, ? extends Entity> map) {
        beginWrite();
        try { backing.putAll(map); }
        finally { endWrite(); }
    }

    @Override
    public void clear() {
        beginWrite();
        try { backing.clear(); }
        finally { endWrite(); }
    }

    @Override
    public Entity putIfAbsent(Long key, Entity value) {
        beginWrite();
        try { return backing.putIfAbsent(key, value); }
        finally { endWrite(); }
    }

    @Override
    public boolean remove(Object key, Object value) {
        beginWrite();
        try { return backing.remove(key, value); }
        finally { endWrite(); }
    }

    @Override
    public Entity replace(Long key, Entity value) {
        beginWrite();
        try { return backing.replace(key, value); }
        finally { endWrite(); }
    }

    @Override
    public boolean replace(Long key, Entity oldValue, Entity newValue) {
        beginWrite();
        try { return backing.replace(key, oldValue, newValue); }
        finally { endWrite(); }
    }

    private <T> Iterator<T> trackedIterator(Iterator<T> iterator) {
        return new Iterator<>() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public T next() { return iterator.next(); }
            @Override public void remove() {
                beginWrite();
                try { iterator.remove(); }
                finally { endWrite(); }
            }
        };
    }

    @Override
    public Collection<Entity> values() {
        Collection<Entity> view = this.valuesView;
        if (view != null) {
            return view;
        }
        return this.valuesView = new AbstractCollection<>() {
            @Override public int size() { return backing.size(); }
            @Override public boolean isEmpty() { return PickupEntityIndex.this.isEmpty(); }
            @Override public boolean contains(Object value) { return hasAnyEntities() && backing.containsValue(value); }
            @Override public void clear() { PickupEntityIndex.this.clear(); }
            @Override public Iterator<Entity> iterator() {
                if (!hasAnyEntities()) return Collections.emptyIterator();
                return trackedIterator(backing.values().iterator());
            }
        };
    }

    @Override
    public Set<Long> keySet() {
        Set<Long> view = this.keySetView;
        if (view != null) {
            return view;
        }
        return this.keySetView = new AbstractSet<>() {
            @Override public int size() { return backing.size(); }
            @Override public boolean isEmpty() { return PickupEntityIndex.this.isEmpty(); }
            @Override public boolean contains(Object key) { return hasAnyEntities() && backing.containsKey(key); }
            @Override public void clear() { PickupEntityIndex.this.clear(); }
            @Override public boolean remove(Object key) { return PickupEntityIndex.this.remove(key) != null; }
            @Override public Iterator<Long> iterator() {
                if (!hasAnyEntities()) return Collections.emptyIterator();
                return trackedIterator(backing.keySet().iterator());
            }
        };
    }

    @Override
    public Set<Entry<Long, Entity>> entrySet() {
        Set<Entry<Long, Entity>> view = this.entrySetView;
        if (view != null) {
            return view;
        }
        return this.entrySetView = new AbstractSet<>() {
            @Override public int size() { return backing.size(); }
            @Override public boolean isEmpty() { return PickupEntityIndex.this.isEmpty(); }
            @Override public void clear() { PickupEntityIndex.this.clear(); }
            @Override public boolean contains(Object object) { return hasAnyEntities() && backing.entrySet().contains(object); }
            @Override public boolean remove(Object object) {
                return object instanceof Entry<?, ?> entry
                        && PickupEntityIndex.this.remove(entry.getKey(), entry.getValue());
            }
            @Override public Iterator<Entry<Long, Entity>> iterator() {
                if (!hasAnyEntities()) return Collections.emptyIterator();
                Iterator<Entry<Long, Entity>> iterator = trackedIterator(backing.entrySet().iterator());
                return new Iterator<>() {
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public void remove() { iterator.remove(); }
                    @Override public Entry<Long, Entity> next() {
                        Entry<Long, Entity> entry = iterator.next();
                        return new SimpleEntry<>(entry) {
                            @Override public Entity setValue(Entity value) {
                                beginWrite();
                                try {
                                    Entity previous = entry.setValue(value);
                                    super.setValue(value);
                                    return previous;
                                } finally { endWrite(); }
                            }
                        };
                    }
                };
            }
        };
    }
}
