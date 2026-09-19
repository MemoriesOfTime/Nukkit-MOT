package cn.nukkit;

import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;

/**
 * Whether a melee hit on a non-player body reaches it without passing through a block.
 *
 * <p>The client picks the attacked entity itself and only reports the runtime id. A big body
 * (a boss at scale 2 or 3, the ender dragon) is wider than its model, so it overlaps terrain and
 * encloses players who stand next to it: the client then reports a hit while its crosshair rests
 * on a stone wall, and the damage goes through. The server used to check only the distance and
 * the facing (the bounding-box reach check), never what lies between the eyes and the body.
 *
 * <p>The point the sword has to reach is chosen the way the swing sees it:
 * <ul>
 *     <li>the eyes are outside the body and the look ray enters it: the entry point;</li>
 *     <li>the eyes are outside and the ray misses (the rotation lags a tick behind the client):
 *     the nearest point of the body, the same point the reach is measured to;</li>
 *     <li>the eyes are inside the body: the look ray itself, as far as the centre of the body or
 *     the reach, whichever is closer. Looking into the open air inside a hitbox is a legal hit;
 *     looking at a wall the body stands behind is not.</li>
 * </ul>
 * The segment from the eyes to that point is walked block by block, and a block that cannot be
 * passed through and whose box the segment crosses refuses the hit. Grass, flowers, liquids and
 * other pass-through blocks never refuse it.
 */
final class MeleeLineOfSight {

    /** Keeps a block the eyes or the body merely touch from counting as in the way. */
    private static final double EDGE = 1.0e-3;

    /** Hard cap on the walk: the reach is at most eight blocks, the walk never needs more. */
    private static final int MAX_STEPS = 64;

    @FunctionalInterface
    interface Obstacles {
        /**
         * @return the box of the block at this position that stops a swing, or {@code null} when
         * the block lets a swing through (air, grass, liquid, an unloaded chunk)
         */
        AxisAlignedBB at(int x, int y, int z);
    }

    private MeleeLineOfSight() {}

    static boolean clear(double eyeX, double eyeY, double eyeZ,
                         double lookX, double lookY, double lookZ,
                         AxisAlignedBB body, double reach, Obstacles obstacles) {
        double length = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (length < 1.0e-9) {
            lookX = 0;
            lookY = 0;
            lookZ = 0;
        } else {
            lookX /= length;
            lookY /= length;
            lookZ /= length;
        }

        double targetX;
        double targetY;
        double targetZ;
        if (body.isVectorInside(eyeX, eyeY, eyeZ)) {
            double centreX = (body.getMinX() + body.getMaxX()) / 2;
            double centreY = (body.getMinY() + body.getMaxY()) / 2;
            double centreZ = (body.getMinZ() + body.getMaxZ()) / 2;
            double toCentre = Math.sqrt(sq(centreX - eyeX) + sq(centreY - eyeY) + sq(centreZ - eyeZ));
            double along = Math.min(toCentre, reach);
            targetX = eyeX + lookX * along;
            targetY = eyeY + lookY * along;
            targetZ = eyeZ + lookZ * along;
        } else {
            double entry = entry(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, body);
            if (entry >= 0 && entry <= reach) {
                targetX = eyeX + lookX * entry;
                targetY = eyeY + lookY * entry;
                targetZ = eyeZ + lookZ * entry;
            } else {
                targetX = NukkitMath.clamp(eyeX, body.getMinX(), body.getMaxX());
                targetY = NukkitMath.clamp(eyeY, body.getMinY(), body.getMaxY());
                targetZ = NukkitMath.clamp(eyeZ, body.getMinZ(), body.getMaxZ());
            }
        }
        return segmentClear(eyeX, eyeY, eyeZ, targetX, targetY, targetZ, obstacles);
    }

    /** Distance along a unit ray to where it enters the box, or -1 when it never does. */
    static double entry(double ox, double oy, double oz, double dx, double dy, double dz, AxisAlignedBB box) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        double[] origin = {ox, oy, oz};
        double[] dir = {dx, dy, dz};
        double[] min = {box.getMinX(), box.getMinY(), box.getMinZ()};
        double[] max = {box.getMaxX(), box.getMaxY(), box.getMaxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0e-12) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
                    return -1;
                }
                continue;
            }
            double t1 = (min[axis] - origin[axis]) / dir[axis];
            double t2 = (max[axis] - origin[axis]) / dir[axis];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }
        if (tMax < tMin || tMax < 0) {
            return -1;
        }
        return Math.max(tMin, 0);
    }

    static boolean segmentClear(double ax, double ay, double az, double bx, double by, double bz,
                                Obstacles obstacles) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 2 * EDGE) {
            return true;
        }
        // Both ends are pulled in a hair: the eyes may rest against a block face, and the body may
        // end exactly on one.
        double ux = dx / length;
        double uy = dy / length;
        double uz = dz / length;
        double sx = ax + ux * EDGE;
        double sy = ay + uy * EDGE;
        double sz = az + uz * EDGE;
        double span = length - 2 * EDGE;
        double ex = sx + ux * span;
        double ey = sy + uy * span;
        double ez = sz + uz * span;

        int x = floor(sx);
        int y = floor(sy);
        int z = floor(sz);
        int endX = floor(ex);
        int endY = floor(ey);
        int endZ = floor(ez);
        int stepX = Integer.signum(Double.compare(ux, 0));
        int stepY = Integer.signum(Double.compare(uy, 0));
        int stepZ = Integer.signum(Double.compare(uz, 0));
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / ux);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / uy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / uz);
        double nextX = stepX == 0 ? Double.POSITIVE_INFINITY : (stepX > 0 ? (x + 1 - sx) : (sx - x)) * deltaX;
        double nextY = stepY == 0 ? Double.POSITIVE_INFINITY : (stepY > 0 ? (y + 1 - sy) : (sy - y)) * deltaY;
        double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (stepZ > 0 ? (z + 1 - sz) : (sz - z)) * deltaZ;

        for (int steps = 0; steps < MAX_STEPS; steps++) {
            AxisAlignedBB box = obstacles.at(x, y, z);
            if (box != null && crosses(box, sx, sy, sz, ux, uy, uz, span)) {
                return false;
            }
            if (x == endX && y == endY && z == endZ) {
                return true;
            }
            if (nextX <= nextY && nextX <= nextZ) {
                if (nextX > span) {
                    return true;
                }
                x += stepX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                if (nextY > span) {
                    return true;
                }
                y += stepY;
                nextY += deltaY;
            } else {
                if (nextZ > span) {
                    return true;
                }
                z += stepZ;
                nextZ += deltaZ;
            }
        }
        return true;
    }

    private static boolean crosses(AxisAlignedBB box, double sx, double sy, double sz,
                                   double ux, double uy, double uz, double span) {
        if (box.isVectorInside(sx, sy, sz)) {
            // The eyes are already inside this block's box (a slab or a carpet under the head):
            // it is not something the swing passes through.
            return false;
        }
        double t = entry(sx, sy, sz, ux, uy, uz, box);
        return t >= 0 && t <= span;
    }

    private static int floor(double value) {
        return NukkitMath.floorDouble(value);
    }

    private static double sq(double value) {
        return value * value;
    }
}
