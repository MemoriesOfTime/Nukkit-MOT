package cn.nukkit.level.vibration;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockWool;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.level.VibrationArriveEvent;
import cn.nukkit.event.level.VibrationOccurEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventGenericPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.plugin.InternalPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Default {@link VibrationManager} implementation. Adapted from PowerNukkitX.
 */
public class SimpleVibrationManager implements VibrationManager {

    protected final Set<VibrationListener> listeners = new CopyOnWriteArraySet<>();
    protected final Level level;

    public SimpleVibrationManager(Level level) {
        this.level = level;
    }

    @Override
    public void callVibrationEvent(VibrationEvent event) {
        if (!isValidVibration(event)) {
            return;
        }

        VibrationOccurEvent vibrationOccurPluginEvent = new VibrationOccurEvent(event);
        this.level.getServer().getPluginManager().callEvent(vibrationOccurPluginEvent);
        if (vibrationOccurPluginEvent.isCancelled()) {
            return;
        }

        Vector3 source = event.source();
        for (var listener : listeners) {
            Vector3 listenerPos = listener.getListenerVector();
            double range = listener.getListenRange();
            if (listenerPos.distanceSquared(source) > range * range) {
                continue;
            }
            if (!canVibrationArrive(level, source, listenerPos)) {
                continue;
            }
            if (!listener.onVibrationOccur(event)) {
                continue;
            }

            this.createVibration(listener, listenerPos, event);

            if (listener.handleArrivalSelf()) {
                // listener schedules its own arrival (persistence + single-flight); manager does not
                continue;
            }

            int delay = Math.max(1, (int) source.distance(listenerPos));
            this.level.getServer().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
                VibrationArriveEvent vibrationArrivePluginEvent = new VibrationArriveEvent(event, listener);
                this.level.getServer().getPluginManager().callEvent(vibrationArrivePluginEvent);
                if (vibrationArrivePluginEvent.isCancelled()) {
                    return;
                }
                listener.onVibrationArrive(event);
            }, delay);
        }
    }

    /**
     * Mirrors vanilla {@code VibrationSystem.User.isValidVibration}: an entity-triggered event is
     * rejected when its source entity is silent/spectator, is sneaking (for events in
     * {@link VibrationType#IGNORE_VIBRATIONS_SNEAKING}), or is standing on a vibration-dampening
     * block (wool / wool carpet).
     * <p>
     * NOTE: this intentionally does <b>not</b> filter by the {@code #minecraft:vibrations} tag.
     * Unlike vanilla (where every listener filters via its own {@code getListenableEvents()}),
     * this manager routes all emitted events and relies on each listener to decide relevance —
     * e.g. {@code SCULK_SENSOR_TENDRILS_CLICKING} is excluded from {@code vibrations} in vanilla
     * but must still reach shriekers here.
     */
    protected boolean isValidVibration(VibrationEvent event) {
        Object initiator = event.initiator();
        if (initiator instanceof Entity entity) {
            // Silent flag (e.g. silently tagged mobs)
            if (entity.getDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_SILENT)) {
                return false;
            }
            // Spectator players never emit vibrations
            if (entity instanceof Player player && player.getGamemode() == Player.SPECTATOR) {
                return false;
            }
            // Sneaking entities suppress events tagged ignore_vibrations_sneaking (step, swim, ...)
            if (entity.isSneaking() && VibrationType.IGNORE_VIBRATIONS_SNEAKING.contains(event.type())) {
                return false;
            }
            // Entity standing on wool/carpet dampens the vibration (JE dampensVibrations)
            if (entityDampensVibrations(entity)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the entity is currently standing on a vibration-dampening block (wool or wool
     * carpet), matching JE {@code Entity.dampensVibrations()} / the {@code #minecraft:dampens_vibrations}
     * block tag for the supporting surface.
     */
    protected boolean entityDampensVibrations(Entity entity) {
        if (!entity.onGround) {
            return false;
        }
        Block below = entity.level.getBlock(entity.getFloorX(), entity.getFloorY() - 1, entity.getFloorZ());
        return isDampener(below);
    }

    @Override
    public void addListener(VibrationListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(VibrationListener listener) {
        this.listeners.remove(listener);
    }

    protected void createVibration(VibrationListener listener, Vector3 listenerPos, VibrationEvent event) {
        var listenerVec = listenerPos.asVector3f();
        var sourceVec = event.source().asVector3f();
        var tag = new CompoundTag()
                .putCompound("origin", createVec3fTag(sourceVec))
                .putFloat("speed", 20.0f)
                .putCompound("target", listener.isEntity() ? createEntityTargetTag(listener.asEntity()) : createVec3fTag(listenerVec))
                .putFloat("timeToLive", (float) (listenerVec.distance(sourceVec) / 20.0));
        LevelEventGenericPacket packet = new LevelEventGenericPacket();
        packet.eventId = LevelEventPacket.EVENT_PARTICLE_VIBRATION_SIGNAL;
        packet.tag = tag;
        broadcastVibrationPacket(event.source(), listenerPos, packet);
    }

    /**
     * Broadcast the vibration signal particle only to players near the source or the listener
     * position (covering the signal's travel path), instead of every player in the level.
     */
    protected void broadcastVibrationPacket(Vector3 source, Vector3 listenerPos, LevelEventGenericPacket packet) {
        Set<Player> viewers = new HashSet<>();
        collectChunkPlayers(source, viewers);
        collectChunkPlayers(listenerPos, viewers);
        if (viewers.isEmpty()) {
            return;
        }
        Server.broadcastPacket(viewers, packet);
    }

    @SuppressWarnings("unchecked")
    protected void collectChunkPlayers(Vector3 pos, Set<Player> out) {
        int chunkX = pos.getFloorX() >> 4;
        int chunkZ = pos.getFloorZ() >> 4;
        for (int cx = chunkX - 1; cx <= chunkX + 1; cx++) {
            for (int cz = chunkZ - 1; cz <= chunkZ + 1; cz++) {
                out.addAll(level.getChunkPlayers(cx, cz).values());
            }
        }
    }

    protected CompoundTag createVec3fTag(Vector3f vec3f) {
        return new CompoundTag()
                .putString("type", "vec3")
                .putFloat("x", vec3f.x)
                .putFloat("y", vec3f.y)
                .putFloat("z", vec3f.z);
    }

    protected CompoundTag createEntityTargetTag(Entity entity) {
        return new CompoundTag()
                .putString("type", "actor")
                .putLong("uniqueID", entity.getId())
                .putInt("attachPos", 3);
    }

    /**
     * Whether a vibration signal can travel from {@code from} to {@code to}. Mirrors vanilla
     * {@code VibrationSystem.Listener.isOccluded}: the signal is blocked (occluded) only when the
     * line from the source's center to the listener's center is blocked by a wool block on
     * <b>every</b> one of the 6 face-offset rays. If any single ray has a clear line, the signal
     * gets through. Wool (not carpet) is the only occluding block per the vanilla tag
     * {@code #minecraft:occludes_vibration_signals}.
     */
    protected boolean canVibrationArrive(Level level, Vector3 from, Vector3 to) {
        return !isOccluded(level, from, to);
    }

    /**
     * Vanilla isOccluded: nudges the source center by a tiny epsilon along each of the 6 axis
     * directions and traces a voxel line to the destination center. Only if ALL six rays hit a
     * wool block is the signal considered occluded.
     */
    protected boolean isOccluded(Level level, Vector3 from, Vector3 to) {
        double fx = from.getFloorX() + 0.5;
        double fy = from.getFloorY() + 0.5;
        double fz = from.getFloorZ() + 0.5;
        double tx = to.getFloorX() + 0.5;
        double ty = to.getFloorY() + 0.5;
        double tz = to.getFloorZ() + 0.5;
        double[][] directions = {
                {1.0E-5, 0, 0}, {-1.0E-5, 0, 0},
                {0, 1.0E-5, 0}, {0, -1.0E-5, 0},
                {0, 0, 1.0E-5}, {0, 0, -1.0E-5}
        };
        for (double[] offset : directions) {
            if (!lineHitsOccluder(level, fx + offset[0], fy + offset[1], fz + offset[2], tx, ty, tz)) {
                // At least one direction has a clear path -> not occluded
                return false;
            }
        }
        return true;
    }

    /**
     * Voxel DDA ray traversal from ({@code ox,oy,oz}) to ({@code dx,dy,dz}); returns true if a wool
     * block is hit along the way (before reaching the destination cell). Mirrors JE
     * {@code Level.isBlockInLine} with the OCCLUDES_VIBRATION_SIGNALS predicate.
     */
    protected boolean lineHitsOccluder(Level level, double ox, double oy, double oz, double dx, double dy, double dz) {
        int x = floor(ox), y = floor(oy), z = floor(oz);
        int ex = floor(dx), ey = floor(dy), ez = floor(dz);
        // JE isBlockInLine tests every voxel the ray passes through, starting from the source cell.
        if (isOccluder(level, x, y, z)) {
            return true;
        }
        // Already in the same cell: the source-cell check above is enough.
        if (x == ex && y == ey && z == ez) {
            return false;
        }
        double stepX = Math.signum(dx - ox);
        double stepY = Math.signum(dy - oy);
        double stepZ = Math.signum(dz - oz);
        // Distance (in ray-progress units) to cross one voxel along each axis
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / (dx - ox)) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / (dy - oy)) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / (dz - oz)) : Double.POSITIVE_INFINITY;
        // Distance to the first voxel boundary along each axis
        double tMaxX = stepX > 0 ? (x + 1 - ox) * tDeltaX : (stepX < 0 ? (ox - x) * tDeltaX : Double.POSITIVE_INFINITY);
        double tMaxY = stepY > 0 ? (y + 1 - oy) * tDeltaY : (stepY < 0 ? (oy - y) * tDeltaY : Double.POSITIVE_INFINITY);
        double tMaxZ = stepZ > 0 ? (z + 1 - oz) * tDeltaZ : (stepZ < 0 ? (oz - z) * tDeltaZ : Double.POSITIVE_INFINITY);

        while (x != ex || y != ey || z != ez) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += (int) stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += (int) stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += (int) stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += (int) stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
            if (isOccluder(level, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    protected static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** JE {@code #minecraft:occludes_vibration_signals}: only wool blocks occlude. */
    protected boolean isOccluder(Level level, int x, int y, int z) {
        Block block = level.getBlock(x, y, z);
        return block instanceof BlockWool;
    }

    /**
     * JE {@code #minecraft:dampens_vibrations}: wool and wool carpets dampen vibrations emitted by
     * entities standing on them. Used by {@link #entityDampensVibrations}.
     */
    protected boolean isDampener(Block block) {
        return block instanceof BlockWool || block.getId() == BlockID.CARPET;
    }
}
