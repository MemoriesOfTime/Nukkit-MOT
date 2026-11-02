package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.google.common.collect.ForwardingConcurrentMap;
import com.google.common.collect.ForwardingMapEntry;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/**
 * 在实体更新期间复用玩家引用快照，坐标仍实时读取。
 * <p>Reuses player reference snapshots during entity updates, keeping coordinates live.
 */
public final class PlayerRangeMap extends ForwardingConcurrentMap<Long, Player> {
    private final ConcurrentMap<Long, Player> players = new Long2ObjectNonBlockingMap<>();
    private final AtomicLong revision = new AtomicLong();
    private volatile Thread tickThread;
    private int tickDepth;
    private Snapshot snapshot;
    private final Collection<Player> values = trackedCollection(this.players.values(), UnaryOperator.identity());
    private final Set<Long> keys = trackedSet(this.players.keySet(), UnaryOperator.identity());
    private final Set<Entry<Long, Player>> entries = trackedSet(this.players.entrySet(), this::trackedEntry);

    private record Snapshot(long revision, Player[] players) {
    }

    @Override
    protected ConcurrentMap<Long, Player> delegate() {
        return this.players;
    }

    void beginTick() {
        if (this.tickDepth != 0 && this.tickThread != Thread.currentThread()) {
            throw new IllegalStateException("Concurrent entity ticks for the same level");
        }
        this.tickThread = Thread.currentThread();
        this.tickDepth++;
    }

    void endTick() {
        if (--this.tickDepth == 0) {
            this.snapshot = null;
            this.tickThread = null;
        }
    }

    public Player[] tickSnapshot() {
        if (this.tickThread != Thread.currentThread()) {
            return null;
        }
        long currentRevision = this.revision.get();
        Snapshot current = this.snapshot;
        if (current == null || current.revision != currentRevision) {
            // 先读版本：构建期间发生的修改会使下一次查询重新构建。
            // Read the revision first so mutations during capture invalidate the next query.
            current = new Snapshot(currentRevision, this.players.values().toArray(Player.EMPTY_ARRAY));
            this.snapshot = current;
        }
        return current.players;
    }

    @Override
    public Player put(Long key, Player value) {
        Player previous = this.players.put(key, value);
        this.revision.incrementAndGet();
        return previous;
    }

    @Override
    public void putAll(Map<? extends Long, ? extends Player> values) {
        // 每次写入分别失效，保证批量操作的回调及部分失败也能看到最新成员。
        // Invalidate each write, including callbacks and partial failures in bulk operations.
        standardPutAll(values);
    }

    @Override
    public Player putIfAbsent(Long key, Player value) {
        Player previous = this.players.putIfAbsent(key, value);
        if (previous == null) this.revision.incrementAndGet();
        return previous;
    }

    @Override
    public Player remove(Object key) {
        Player previous = this.players.remove(key);
        if (previous != null) this.revision.incrementAndGet();
        return previous;
    }

    @Override
    public boolean remove(Object key, Object value) {
        boolean removed = this.players.remove(key, value);
        if (removed) this.revision.incrementAndGet();
        return removed;
    }

    @Override
    public Player replace(Long key, Player value) {
        Player previous = this.players.replace(key, value);
        if (previous != null) this.revision.incrementAndGet();
        return previous;
    }

    @Override
    public boolean replace(Long key, Player oldValue, Player newValue) {
        boolean replaced = this.players.replace(key, oldValue, newValue);
        if (replaced) this.revision.incrementAndGet();
        return replaced;
    }

    @Override
    public void clear() {
        this.players.clear();
        this.revision.incrementAndGet();
    }

    @Override
    public Collection<Player> values() {
        return this.values;
    }

    @Override
    public Set<Long> keySet() {
        return this.keys;
    }

    @Override
    public Set<Entry<Long, Player>> entrySet() {
        return this.entries;
    }

    private Entry<Long, Player> trackedEntry(Entry<Long, Player> entry) {
        return new ForwardingMapEntry<>() {
            @Override
            protected Entry<Long, Player> delegate() {
                return entry;
            }

            @Override
            public boolean equals(Object other) {
                return other == this || super.equals(other);
            }

            @Override
            public Player setValue(Player value) {
                Player previous = entry.setValue(value);
                revision.incrementAndGet();
                return previous;
            }
        };
    }

    private <T> Collection<T> trackedCollection(Collection<T> view, UnaryOperator<T> wrap) {
        return new AbstractCollection<>() {
            @Override
            public int size() {
                return view.size();
            }

            @Override
            public boolean contains(Object value) {
                return view.contains(value);
            }

            @Override
            public boolean remove(Object value) {
                boolean removed = view.remove(value);
                if (removed) revision.incrementAndGet();
                return removed;
            }

            @Override
            public void clear() {
                PlayerRangeMap.this.clear();
            }

            @Override
            public Iterator<T> iterator() {
                Iterator<T> iterator = view.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public T next() {
                        return wrap.apply(iterator.next());
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                        revision.incrementAndGet();
                    }
                };
            }
        };
    }

    private <T> Set<T> trackedSet(Set<T> view, UnaryOperator<T> wrap) {
        Collection<T> tracked = trackedCollection(view, wrap);
        return new AbstractSet<>() {
            @Override
            public int size() {
                return tracked.size();
            }

            @Override
            public boolean contains(Object value) {
                return tracked.contains(value);
            }

            @Override
            public boolean remove(Object value) {
                return tracked.remove(value);
            }

            @Override
            public void clear() {
                tracked.clear();
            }

            @Override
            public Iterator<T> iterator() {
                return tracked.iterator();
            }
        };
    }
}
