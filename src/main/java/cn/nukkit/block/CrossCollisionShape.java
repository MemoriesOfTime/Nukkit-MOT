package cn.nukkit.block;

import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;

import java.util.List;

/**
 * Collision of a fence, a wall and a pane: a post plus the arms towards the connected sides.
 *
 * <p>The block keeps a single box stretched to every connected side for its outline. Used for
 * collision, that box filled the inner quarter of a corner: a fence or a wall bent into an L was a
 * solid square on the server while the client, like vanilla, lets a body into that quarter. Mobs
 * and projectiles stopped in the air there, and a player the client placed against the post was
 * inside a block for the server.
 *
 * <p>The parts are cut out of that box, so the reach of the arms, the height and every straight
 * or lone shape stay exactly what they were; only the empty quarters of a corner, a T and a cross
 * open up.
 */
final class CrossCollisionShape {

    /** Half widths in blocks, from the vanilla collision shapes. */
    static final double FENCE_POST = 2d / 16d;
    static final double FENCE_ARM = 2d / 16d;
    static final double WALL_POST = 4d / 16d;
    static final double WALL_ARM = 3d / 16d;
    static final double PANE_POST = 1d / 16d;
    static final double PANE_ARM = 1d / 16d;

    private CrossCollisionShape() {}

    /**
     * @param bounds the single box of the block, stretched to its connected sides
     * @param centerX the centre of the cell along X
     * @param centerZ the centre of the cell along Z
     * @return one to three boxes, none of them contained in another
     */
    static AxisAlignedBB[] parts(AxisAlignedBB bounds, double centerX, double centerZ, double postHalf, double armHalf) {
        AxisAlignedBB alongX = clip(bounds, bounds.getMinX(), bounds.getMaxX(), centerZ - armHalf, centerZ + armHalf);
        AxisAlignedBB alongZ = clip(bounds, centerX - armHalf, centerX + armHalf, bounds.getMinZ(), bounds.getMaxZ());
        AxisAlignedBB post = clip(bounds, centerX - postHalf, centerX + postHalf, centerZ - postHalf, centerZ + postHalf);

        AxisAlignedBB[] candidates = {alongX, alongZ, post};
        AxisAlignedBB[] kept = new AxisAlignedBB[3];
        int count = 0;
        for (int i = 0; i < candidates.length; i++) {
            AxisAlignedBB part = candidates[i];
            if (part == null) {
                continue;
            }
            boolean covered = false;
            for (int j = 0; j < candidates.length && !covered; j++) {
                AxisAlignedBB other = candidates[j];
                if (j == i || other == null || !contains(other, part)) {
                    continue;
                }
                // Two equal parts: keep the first of them.
                covered = !contains(part, other) || j < i;
            }
            if (!covered) {
                kept[count++] = part;
            }
        }
        AxisAlignedBB[] result = new AxisAlignedBB[count];
        System.arraycopy(kept, 0, result, 0, count);
        return result;
    }

    static boolean collides(AxisAlignedBB[] parts, AxisAlignedBB bb) {
        for (AxisAlignedBB part : parts) {
            if (bb.intersectsWith(part)) {
                return true;
            }
        }
        return false;
    }

    static void addColliding(AxisAlignedBB[] parts, AxisAlignedBB bb, List<AxisAlignedBB> collidingBoxes) {
        for (AxisAlignedBB part : parts) {
            if (bb.intersectsWith(part)) {
                collidingBoxes.add(part);
            }
        }
    }

    private static AxisAlignedBB clip(AxisAlignedBB bounds, double minX, double maxX, double minZ, double maxZ) {
        double x0 = Math.max(bounds.getMinX(), minX);
        double x1 = Math.min(bounds.getMaxX(), maxX);
        double z0 = Math.max(bounds.getMinZ(), minZ);
        double z1 = Math.min(bounds.getMaxZ(), maxZ);
        if (x1 <= x0 || z1 <= z0) {
            return null;
        }
        return new SimpleAxisAlignedBB(x0, bounds.getMinY(), z0, x1, bounds.getMaxY(), z1);
    }

    private static boolean contains(AxisAlignedBB outer, AxisAlignedBB inner) {
        return outer.getMinX() <= inner.getMinX() && outer.getMaxX() >= inner.getMaxX()
                && outer.getMinZ() <= inner.getMinZ() && outer.getMaxZ() >= inner.getMaxZ();
    }
}
