package cn.nukkit.level.format.generic;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
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
final class PickupEntityIndex extends AbstractMap<Long, Entity> implements ConcurrentMap<Long, Entity> {
    private final Long2ObjectNonBlockingMap<Entity> backing;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicInteger writers = new AtomicInteger();
    private record Presence(long revision, int kinds) { }

    private volatile Presence cached = new Presence(-1, 0);

    PickupEntityIndex(Long2ObjectNonBlockingMap<Entity> backing) {
        this.backing = backing;
    }

    Long2ObjectNonBlockingMap<Entity> backing() {
        return backing;
    }

    boolean hasPickupEntities(boolean itemsOnly) {
        long stamp = revision.get();
        if (writers.get() != 0) return true;
        Presence present = cached;
        if (present.revision == stamp) return (present.kinds & (itemsOnly ? 1 : 3)) != 0;
        int kinds = 0;
        for (Entity entity : backing.values()) {
            if (FullChunk.isPickupEntity(entity, true)) kinds |= 1;
            else if (FullChunk.isPickupEntity(entity, false)) kinds |= 2;
            if (kinds == 3) break;
        }
        // A concurrent insertion may have been missed by the weakly consistent iterator.
        // In-flight/changing membership means "possibly present", never a false empty result.
        if (writers.get() != 0 || revision.get() != stamp) return true;
        cached = new Presence(stamp, kinds);
        return (kinds & (itemsOnly ? 1 : 3)) != 0;
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
        return new AbstractCollection<>() {
            @Override public int size() { return backing.size(); }
            @Override public boolean contains(Object value) { return backing.containsValue(value); }
            @Override public void clear() { PickupEntityIndex.this.clear(); }
            @Override public Iterator<Entity> iterator() { return trackedIterator(backing.values().iterator()); }
        };
    }

    @Override
    public Set<Long> keySet() {
        return new AbstractSet<>() {
            @Override public int size() { return backing.size(); }
            @Override public boolean contains(Object key) { return backing.containsKey(key); }
            @Override public void clear() { PickupEntityIndex.this.clear(); }
            @Override public boolean remove(Object key) { return PickupEntityIndex.this.remove(key) != null; }
            @Override public Iterator<Long> iterator() { return trackedIterator(backing.keySet().iterator()); }
        };
    }

    @Override
    public Set<Entry<Long, Entity>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return backing.size(); }
            @Override public void clear() { PickupEntityIndex.this.clear(); }
            @Override public boolean contains(Object object) { return backing.entrySet().contains(object); }
            @Override public boolean remove(Object object) {
                return object instanceof Entry<?, ?> entry
                        && PickupEntityIndex.this.remove(entry.getKey(), entry.getValue());
            }
            @Override public Iterator<Entry<Long, Entity>> iterator() {
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
