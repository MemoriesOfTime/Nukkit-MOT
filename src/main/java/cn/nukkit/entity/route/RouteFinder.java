package cn.nukkit.entity.route;

import cn.nukkit.entity.EntityWalking;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author zzz1999 @ MobPlugin
 */
public abstract class RouteFinder {

    private static final AtomicReferenceFieldUpdater<RouteFinder, RouteFinderSearchTask> SEARCH_OWNER =
            AtomicReferenceFieldUpdater.newUpdater(RouteFinder.class, RouteFinderSearchTask.class, "pendingSearch");

    private volatile RouteFinderSearchTask pendingSearch;
    private volatile boolean searchClosed;

    protected final ArrayList<Node> nodes = new ArrayList<>();
    protected boolean finished;
    protected boolean searching;

    protected int current;

    protected final EntityWalking entity;

    protected Vector3 start;
    protected Vector3 destination;

    protected Level level;

    protected boolean interrupt;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    protected boolean reachable = true;

    RouteFinder(EntityWalking entity) {
        Objects.requireNonNull(entity, "RouteFinder: entity can not be null");
        this.entity = entity;
        this.searchClosed = entity.closed;
        this.level = entity.getLevel();
    }

    public void addNode(Node node) {
        try {
            lock.writeLock().lock();
            if (!this.searchClosed) {
                nodes.add(node);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addNode(ArrayList<Node> node) {
        try {
            lock.writeLock().lock();
            if (!this.searchClosed) {
                nodes.addAll(node);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getCurrent() {
        return this.current;
    }

    public boolean hasPendingSearch() {
        return this.pendingSearch != null;
    }

    /**
     * Cancel a request that has not started; a running task keeps its owner.
     */
    public void cancelQueuedSearch() {
        RouteFinderSearchTask task = this.pendingSearch;
        if (task != null) {
            task.discardQueued();
        }
    }

    /**
     * Permanently close native search without changing worker scratch state.
     */
    public void closeSearch() {
        if (!this.searchClosed) {
            try {
                this.lock.writeLock().lock();
                if (!this.searchClosed) {
                    this.searchClosed = true;
                    this.nodes.clear();
                    this.current = 0;
                }
            } finally {
                this.lock.writeLock().unlock();
            }
        }
        this.cancelQueuedSearch();
    }

    public boolean isSearchClosed() {
        return this.searchClosed;
    }

    boolean reserveSearch(RouteFinderSearchTask task) {
        return SEARCH_OWNER.compareAndSet(this, null, task);
    }

    void releaseSearch(RouteFinderSearchTask task) {
        SEARCH_OWNER.compareAndSet(this, task, null);
    }

    public Node getCurrentNode() {
        try {
            lock.readLock().lock();
            if (this.hasCurrentNode()) {
                return nodes.get(current);
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Vector3 getDestination() {
        return this.destination;
    }

    public void setDestination(Vector3 destination) {
        this.destination = destination;
        if (this.isSearching()) {
            this.interrupt = true;
            this.research();
        }
    }

    public EntityWalking getEntity() {
        return entity;
    }

    public Level getLevel() {
        return this.level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public Vector3 getStart() {
        return this.start;
    }

    public void setStart(Vector3 start) {
        if (!this.isSearching()) {
            this.start = start;
        }
    }

    public boolean hasArrivedNode(Vector3 vec) {
        try {
            lock.readLock().lock();
            Node node = this.getCurrentNode();
            if (node != null) {
                Vector3 cur = node.getVector3();
                if (cur != null && this.hasNext()) {
                    // Exact equality never happens (nodes are grid-aligned, entity coords are
                    // fractional), so node advancement never fired. Accept arrival within 1 block.
                    double dx = vec.getX() - cur.getX();
                    double dz = vec.getZ() - cur.getZ();
                    return dx * dx + dz * dz < 1.0D;
                }
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasCurrentNode() {
        return current < this.nodes.size();
    }

    /**
     * Check arrival and advance the same published path as one operation.
     * A null result leaves the caller's existing movement target intact.
     */
    public Vector3 advanceIfArrived(Vector3 position) {
        try {
            lock.writeLock().lock();
            if (searching || current + 1 >= nodes.size()) {
                return null;
            }
            Node node = nodes.get(current);
            Node next = nodes.get(current + 1);
            if (node == null || next == null || node.getVector3() == null) {
                return null;
            }
            Vector3 waypoint = node.getVector3();
            double dx = position.getX() - waypoint.getX();
            double dz = position.getZ() - waypoint.getZ();
            if (!(dx * dx + dz * dz < 1.0D)) {
                return null;
            }
            current++;
            return next.getVector3();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasNext() {
        try {
            if (this.current + 1 < nodes.size()) {
                return this.nodes.get(this.current + 1) != null;
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    public void interrupt() {
        this.interrupt ^= true;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isInterrupted() {
        return this.interrupt || this.searchClosed;
    }

    public boolean isReachable() {
        return reachable;
    }

    public boolean isSearching() {
        return searching;
    }

    public Vector3 next() {
        try {
            lock.readLock().lock();
            if (this.hasNext()) {
                return this.nodes.get(++current).getVector3();
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void research() {
        this.resetNodes();
        this.search();
    }

    public void resetNodes() {
        try {
            this.lock.writeLock().lock();
            this.nodes.clear();
            this.current = 0;
            this.interrupt = false;
            this.destination = null;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public abstract void search();
}
