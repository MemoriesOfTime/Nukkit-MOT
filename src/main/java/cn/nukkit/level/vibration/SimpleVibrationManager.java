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
            double distSq = listenerPos.distanceSquared(source);
            if (distSq > range * range) {
                continue;
            }
            if (!canVibrationArrive(level, source, listenerPos)) {
                continue;
            }
            boolean accepted = listener.onVibrationOccur(event);
            if (!accepted) {
                continue;
            }

            this.createVibration(listener, listenerPos, event);

            if (listener.handleArrivalSelf()) {
                // listener owns arrival (persistence + single-flight); manager skips scheduling
                continue;
            }

            int delay = Math.max(1, (int) source.distance(listenerPos));
            this.level.getServer().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
                if (!listeners.contains(listener)) {
                    return;
                }
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
     * Mirrors vanilla {@code VibrationSystem.User.isValidVibration}: rejects events from silent/spectator
     * entities, sneaking entities ({@link VibrationType#IGNORE_VIBRATIONS_SNEAKING}), or entities on wool.
     * Skips the {@code #minecraft:vibrations} tag — routing is per-listener (shriekers still need tendril clicks).
     */
    protected boolean isValidVibration(VibrationEvent event) {
        Object initiator = event.initiator();
        if (initiator instanceof Entity entity) {
            if (entity.getDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_SILENT)) {
                return false;
            }
            if (entity instanceof Player player && player.getGamemode() == Player.SPECTATOR) {
                return false;
            }
            if (entity.isSneaking() && VibrationType.IGNORE_VIBRATIONS_SNEAKING.contains(event.type())) {
                return false;
            }
            if (entityDampensVibrations(entity)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Entity standing on wool/carpet — JE {@code Entity.dampensVibrations()} /
     * {@code #minecraft:dampens_vibrations} for the supporting surface.
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
        // Scope to players tracking the source/listener chunk (signal particle only visible there),
        // plus the triggering player. Generic packets carry no position, so the server must scope them.
        Vector3 source = event.source();
        Set<Player> viewers = new HashSet<>();
        viewers.addAll(this.level.getChunkPlayers(source.getFloorX() >> 4, source.getFloorZ() >> 4).values());
        viewers.addAll(this.level.getChunkPlayers(listenerPos.getFloorX() >> 4, listenerPos.getFloorZ() >> 4).values());
        if (event.initiator() instanceof Player player) {
            viewers.add(player);
        }
        Server.broadcastPacket(viewers, packet);
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
     * Mirrors vanilla {@code VibrationSystem.Listener.isOccluded}: blocked only when wool
     * ({@code #minecraft:occludes_vibration_signals}, not carpet) occludes all 6 face-offset rays
     * from source center to listener center; any clear ray lets the signal through.
     */
    protected boolean canVibrationArrive(Level level, Vector3 from, Vector3 to) {
        return !isOccluded(level, from, to);
    }

    /**
     * Vanilla isOccluded: nudges the source center by epsilon along each of 6 axes and traces a
     * voxel line to the destination — occluded only if all six rays hit wool.
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
                return false;
            }
        }
        return true;
    }

    /**
     * Voxel DDA traversal; returns true if wool is hit before the destination cell. Mirrors JE
     * {@code Level.isBlockInLine} with the OCCLUDES_VIBRATION_SIGNALS predicate.
     */
    protected boolean lineHitsOccluder(Level level, double ox, double oy, double oz, double dx, double dy, double dz) {
        int x = floor(ox), y = floor(oy), z = floor(oz);
        int ex = floor(dx), ey = floor(dy), ez = floor(dz);
        if (isOccluder(level, x, y, z)) {
            return true;
        }
        // Source and destination in the same cell: already checked above.
        if (x == ex && y == ey && z == ez) {
            return false;
        }
        double stepX = Math.signum(dx - ox);
        double stepY = Math.signum(dy - oy);
        double stepZ = Math.signum(dz - oz);
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / (dx - ox)) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / (dy - oy)) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / (dz - oz)) : Double.POSITIVE_INFINITY;
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

    /** JE {@code #minecraft:dampens_vibrations}: wool and wool carpets dampen for entities on them. */
    protected boolean isDampener(Block block) {
        return block instanceof BlockWool || block.getId() == BlockID.CARPET;
    }
}
