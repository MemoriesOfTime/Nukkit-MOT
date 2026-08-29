package cn.nukkit.entity.route;

import cn.nukkit.entity.EntityWalking;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author zzz1999 @ MobPlugin
 */
public abstract class RouteFinder {

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
        this.level = entity.getLevel();
    }

    public void addNode(Node node) {
        try {
            lock.writeLock().lock();
            nodes.add(node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addNode(ArrayList<Node> node) {
        try {
            lock.writeLock().lock();
            nodes.addAll(node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getCurrent() {
        return this.current;
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
                    // Точное равенство координат не наступает никогда: узел стоит в целочисленной
                    // клетке, а сущность движется дробными шагами и останавливается рядом. Из-за
                    // этого путь никогда не переключался на следующий узел здесь, и единственным
                    // способом сдвинуться оставался повторный поиск раз в десять тактов.
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
        return this.interrupt;
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
