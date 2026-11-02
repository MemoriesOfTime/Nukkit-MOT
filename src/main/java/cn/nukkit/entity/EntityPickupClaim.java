package cn.nukkit.entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** A temporary identity claim shared by ordinary pickup routes and item merging. */
public final class EntityPickupClaim {
    private static final Set<Entity> CLAIMED = Collections.newSetFromMap(new IdentityHashMap<>());
    private EntityPickupClaim() { }

    public static Lease tryClaim(Entity entity) {
        synchronized (CLAIMED) {
            if (entity == null || entity.isPublicationDeferred() || entity.closed || !CLAIMED.add(entity)) return null;
        }
        return new Lease(entity);
    }

    public static boolean isClaimed(Entity entity) {
        synchronized (CLAIMED) { return CLAIMED.contains(entity); }
    }

    public static final class Lease implements AutoCloseable {
        private final Entity entity;
        private boolean closed;
        private Lease(Entity entity) { this.entity = entity; }
        @Override public void close() {
            synchronized (CLAIMED) {
                if (!closed) {
                    CLAIMED.remove(entity);
                    closed = true;
                }
            }
        }
    }
}
