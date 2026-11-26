package cn.nukkit.entity;

import java.io.Serial;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Native viewer membership with a certified empty fast path for movement dispatch. */
public final class EntityViewerMap<K, V> extends ConcurrentHashMap<K, V> {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int EMPTY = -1;
    private static final int UNCHECKED = -2;
    // A deserialized map may already have insertion-capable views in the same object graph.
    private static final int DISABLED = 0;
    private final transient Object certificationLock = new Object();
    // Positive values count insertion-capable operations inside ConcurrentHashMap.
    private transient volatile int state = EMPTY;

    public static boolean isKnownEmpty(Map<?, ?> viewers) {
        return viewers instanceof EntityViewerMap<?, ?> nativeMap && nativeMap.state == EMPTY;
    }

    public static void noteEmptyIteration(Map<?, ?> viewers) {
        if (viewers instanceof EntityViewerMap<?, ?> nativeMap) nativeMap.confirmEmpty();
    }

    private void confirmEmpty() {
        if (state != UNCHECKED) return;
        synchronized (certificationLock) {
            // With new insertions excluded and all admitted puts counted, a removal can
            // only make the native count an overestimate. No user callback runs here.
            if (state == UNCHECKED && super.isEmpty()) state = EMPTY;
        }
    }

    private void beginInsert() {
        if (state == DISABLED) return;
        synchronized (certificationLock) {
            if (state != DISABLED) state = state > 0 ? state + 1 : 1;
        }
    }

    private void finishInsert() {
        if (state == DISABLED) return;
        synchronized (certificationLock) {
            if (state > 0) state = state == 1 ? UNCHECKED : state - 1;
        }
    }

    private void disableCertification() {
        if (state == DISABLED) return;
        synchronized (certificationLock) {
            state = DISABLED;
        }
    }

    @Override
    public V put(K key, V value) {
        beginInsert();
        try {
            return super.put(key, value);
        } finally {
            finishInsert();
        }
    }

    @Override
    public V remove(Object key) {
        V previous = super.remove(key);
        if (previous != null) confirmEmpty();
        return previous;
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (!super.remove(key, value)) return false;
        confirmEmpty();
        return true;
    }

    @Override
    public void clear() {
        super.clear();
        confirmEmpty();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        beginInsert();
        try {
            return super.putIfAbsent(key, value);
        } finally {
            finishInsert();
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> source) {
        beginInsert();
        try {
            super.putAll(source);
        } finally {
            finishInsert();
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> fn) {
        beginInsert();
        try {
            return super.computeIfAbsent(key, fn);
        } finally {
            finishInsert();
        }
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> fn) {
        beginInsert();
        try {
            return super.compute(key, fn);
        } finally {
            finishInsert();
        }
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> fn) {
        beginInsert();
        try {
            return super.merge(key, value, fn);
        } finally {
            finishInsert();
        }
    }

    // These live views can add through the JDK's private putVal, bypassing public mutators.
    @Override
    public KeySetView<K, V> keySet(V mappedValue) {
        KeySetView<K, V> view = super.keySet(mappedValue);
        disableCertification();
        return view;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> view = super.entrySet();
        disableCertification();
        return view;
    }
}
