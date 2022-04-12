package cn.nukkit.math;

import cn.nukkit.Server;
import cn.nukkit.level.MovingObjectPosition;

public class AxisAlignedBB implements Cloneable {

    public double minX;
    public double minY;
    public double minZ;
    public double maxX;
    public double maxY;
    public double maxZ;

    public AxisAlignedBB(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5, double paramDouble6) {
        this.minX = paramDouble1;
        this.minY = paramDouble2;
        this.minZ = paramDouble3;
        this.maxX = paramDouble4;
        this.maxY = paramDouble5;
        this.maxZ = paramDouble6;
    }

    public AxisAlignedBB(Vector3 paramVector31, Vector3 paramVector32) {
        this.minX = Math.min(paramVector31.x, paramVector32.x);
        this.minY = Math.min(paramVector31.y, paramVector32.y);
        this.minZ = Math.min(paramVector31.z, paramVector32.z);
        this.maxX = Math.max(paramVector31.x, paramVector32.x);
        this.maxY = Math.max(paramVector31.y, paramVector32.y);
        this.maxZ = Math.max(paramVector31.z, paramVector32.z);
    }

    public AxisAlignedBB setBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.setMinX(minX);
        this.setMinY(minY);
        this.setMinZ(minZ);
        this.setMaxX(maxX);
        this.setMaxY(maxY);
        this.setMaxZ(maxZ);
        return this;
    }

    public AxisAlignedBB addCoord(double x, double y, double z) {
        double minX = this.getMinX();
        double minY = this.getMinY();
        double minZ = this.getMinZ();
        double maxX = this.getMaxX();
        double maxY = this.getMaxY();
        double maxZ = this.getMaxZ();

        if (x < 0) minX += x;
        if (x > 0) maxX += x;

        if (y < 0) minY += y;
        if (y > 0) maxY += y;

        if (z < 0) minZ += z;
        if (z > 0) maxZ += z;

        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public AxisAlignedBB grow(double x, double y, double z) {
        return new AxisAlignedBB(this.getMinX() - x, this.getMinY() - y, this.getMinZ() - z, this.getMaxX() + x, this.getMaxY() + y, this.getMaxZ() + z);
    }

    public AxisAlignedBB growNoUp(double x, double y, double z) {
        return new AxisAlignedBB(this.getMinX() - x, this.getMinY() - y, this.getMinZ() - z, this.getMaxX() + x, this.getMaxY(), this.getMaxZ() + z);
    }

    public AxisAlignedBB expand(double x, double y, double z)  {
        this.setMinX(this.getMinX() - x);
        this.setMinY(this.getMinY() - y);
        this.setMinZ(this.getMinZ() - z);
        this.setMaxX(this.getMaxX() + x);
        this.setMaxY(this.getMaxY() + y);
        this.setMaxZ(this.getMaxZ() + z);

        return this;
    }

    public AxisAlignedBB offset(double x, double y, double z) {
        this.setMinX(this.getMinX() + x);
        this.setMinY(this.getMinY() + y);
        this.setMinZ(this.getMinZ() + z);
        this.setMaxX(this.getMaxX() + x);
        this.setMaxY(this.getMaxY() + y);
        this.setMaxZ(this.getMaxZ() + z);

        return this;
    }

    public AxisAlignedBB shrink(double x, double y, double z) {
        return new AxisAlignedBB(this.getMinX() + x, this.getMinY() + y, this.getMinZ() + z, this.getMaxX() - x, this.getMaxY() - y, this.getMaxZ() - z);
    }

    public AxisAlignedBB contract(double x, double y, double z) {
        this.setMinX(this.getMinX() + x);
        this.setMinY(this.getMinY() + y);
        this.setMinZ(this.getMinZ() + z);
        this.setMaxX(this.getMaxX() - x);
        this.setMaxY(this.getMaxY() - y);
        this.setMaxZ(this.getMaxZ() - z);

        return this;
    }

    public AxisAlignedBB setBB(AxisAlignedBB bb) {
        this.setMinX(bb.getMinX());
        this.setMinY(bb.getMinY());
        this.setMinZ(bb.getMinZ());
        this.setMaxX(bb.getMaxX());
        this.setMaxY(bb.getMaxY());
        this.setMaxZ(bb.getMaxZ());
        return this;
    }

    public AxisAlignedBB getOffsetBoundingBox(BlockFace face, double x, double y, double z) {
        return getOffsetBoundingBox(face.getXOffset() * x, face.getYOffset() * y, face.getZOffset() * z);
    }

    public AxisAlignedBB getOffsetBoundingBox(double x, double y, double z) {
        return new AxisAlignedBB(this.getMinX() + x, this.getMinY() + y, this.getMinZ() + z, this.getMaxX() + x, this.getMaxY() + y, this.getMaxZ() + z);
    }

    public double calculateXOffset(AxisAlignedBB bb, double x) {
        if (bb.getMaxY() <= this.getMinY() || bb.getMinY() >= this.getMaxY()) {
            return x;
        }
        if (bb.getMaxZ() <= this.getMinZ() || bb.getMinZ() >= this.getMaxZ()) {
            return x;
        }
        if (x > 0 && bb.getMaxX() <= this.getMinX()) {
            double x1 = this.getMinX() - bb.getMaxX();
            if (x1 < x) {
                x = x1;
            }
        }
        if (x < 0 && bb.getMinX() >= this.getMaxX()) {
            double x2 = this.getMaxX() - bb.getMinX();
            if (x2 > x) {
                x = x2;
            }
        }

        return x;
    }

    public double calculateYOffset(AxisAlignedBB bb, double y) {
        if (bb.getMaxX() <= this.getMinX() || bb.getMinX() >= this.getMaxX()) {
            return y;
        }
        if (bb.getMaxZ() <= this.getMinZ() || bb.getMinZ() >= this.getMaxZ()) {
            return y;
        }
        if (y > 0 && bb.getMaxY() <= this.getMinY()) {
            double y1 = this.getMinY() - bb.getMaxY();
            if (y1 < y) {
                y = y1;
            }
        }
        if (y < 0 && bb.getMinY() >= this.getMaxY()) {
            double y2 = this.getMaxY() - bb.getMinY();
            if (y2 > y) {
                y = y2;
            }
        }

        return y;
    }

    public double calculateZOffset(AxisAlignedBB bb, double z) {
        if (bb.getMaxX() <= this.getMinX() || bb.getMinX() >= this.getMaxX()) {
            return z;
        }
        if (bb.getMaxY() <= this.getMinY() || bb.getMinY() >= this.getMaxY()) {
            return z;
        }
        if (z > 0 && bb.getMaxZ() <= this.getMinZ()) {
            double z1 = this.getMinZ() - bb.getMaxZ();
            if (z1 < z) {
                z = z1;
            }
        }
        if (z < 0 && bb.getMinZ() >= this.getMaxZ()) {
            double z2 = this.getMaxZ() - bb.getMinZ();
            if (z2 > z) {
                z = z2;
            }
        }

        return z;
    }

    public boolean intersectsWith(AxisAlignedBB bb) {
        if (bb.getMaxY() > this.getMinY() && bb.getMinY() < this.getMaxY()) {
            if (bb.getMaxX() > this.getMinX() && bb.getMinX() < this.getMaxX()) {
                return bb.getMaxZ() > this.getMinZ() && bb.getMinZ() < this.getMaxZ();
            }
        }

        return false;
    }

    public boolean isVectorInside(Vector3 vector) {
        return vector.x >= this.getMinX() && vector.x <= this.getMaxX() && vector.y >= this.getMinY() && vector.y <= this.getMaxY() && vector.z >= this.getMinZ() && vector.z <= this.getMaxZ();

    }

    public double getAverageEdgeLength() {
        return (this.getMaxX() - this.getMinX() + this.getMaxY() - this.getMinY() + this.getMaxZ() - this.getMinZ()) / 3;
    }

    public boolean isVectorInYZ(Vector3 vector) {
        return vector.y >= this.getMinY() && vector.y <= this.getMaxY() && vector.z >= this.getMinZ() && vector.z <= this.getMaxZ();
    }

    public boolean isVectorInXZ(Vector3 vector) {
        return vector.x >= this.getMinX() && vector.x <= this.getMaxX() && vector.z >= this.getMinZ() && vector.z <= this.getMaxZ();
    }

    public boolean isVectorInXY(Vector3 vector) {
        return vector.x >= this.getMinX() && vector.x <= this.getMaxX() && vector.y >= this.getMinY() && vector.y <= this.getMaxY();
    }

    public MovingObjectPosition calculateIntercept(Vector3 pos1, Vector3 pos2) {
        Vector3 v1 = pos1.getIntermediateWithXValue(pos2, this.getMinX());
        Vector3 v2 = pos1.getIntermediateWithXValue(pos2, this.getMaxX());
        Vector3 v3 = pos1.getIntermediateWithYValue(pos2, this.getMinY());
        Vector3 v4 = pos1.getIntermediateWithYValue(pos2, this.getMaxY());
        Vector3 v5 = pos1.getIntermediateWithZValue(pos2, this.getMinZ());
        Vector3 v6 = pos1.getIntermediateWithZValue(pos2, this.getMaxZ());

        if (v1 != null && !this.isVectorInYZ(v1)) {
            v1 = null;
        }

        if (v2 != null && !this.isVectorInYZ(v2)) {
            v2 = null;
        }

        if (v3 != null && !this.isVectorInXZ(v3)) {
            v3 = null;
        }

        if (v4 != null && !this.isVectorInXZ(v4)) {
            v4 = null;
        }

        if (v5 != null && !this.isVectorInXY(v5)) {
            v5 = null;
        }

        if (v6 != null && !this.isVectorInXY(v6)) {
            v6 = null;
        }

        Vector3 vector = null;

        //if (v1 != null && (vector == null || pos1.distanceSquared(v1) < pos1.distanceSquared(vector))) {
        if (v1 != null) {
            vector = v1;
        }

        if (v2 != null && (vector == null || pos1.distanceSquared(v2) < pos1.distanceSquared(vector))) {
            vector = v2;
        }

        if (v3 != null && (vector == null || pos1.distanceSquared(v3) < pos1.distanceSquared(vector))) {
            vector = v3;
        }

        if (v4 != null && (vector == null || pos1.distanceSquared(v4) < pos1.distanceSquared(vector))) {
            vector = v4;
        }

        if (v5 != null && (vector == null || pos1.distanceSquared(v5) < pos1.distanceSquared(vector))) {
            vector = v5;
        }

        if (v6 != null && (vector == null || pos1.distanceSquared(v6) < pos1.distanceSquared(vector))) {
            vector = v6;
        }

        if (vector == null) {
            return null;
        }

        int face = -1;

        if (vector == v1) {
            face = 4;
        } else if (vector == v2) {
            face = 5;
        } else if (vector == v3) {
            face = 0;
        } else if (vector == v4) {
            face = 1;
        } else if (vector == v5) {
            face = 2;
        } else if (vector == v6) {
            face = 3;
        }

        return MovingObjectPosition.fromBlock(0, 0, 0, face, vector);
    }

    public void setMinX(double minX) {
        this.minX = minX;
    }

    public void setMinY(double minY) {
        this.minY = minY;
    }

    public void setMinZ(double minZ) {
        this.minZ = minZ;
    }

    public void setMaxX(double maxX) {
        this.maxX = maxX;
    }

    public void setMaxY(double maxY) {
        this.maxY = maxY;
    }

    public void setMaxZ(double maxZ) {
        this.maxZ = maxZ;
    }


    public double getMinX() {
        return this.minX;
    }

    public double getMinY() {
        return this.minY;
    }

    public double getMinZ() {
        return this.minZ;
    }

    public double getMaxX() {
        return this.maxX;
    }

    public double getMaxY() {
        return this.maxY;
    }

    public double getMaxZ() {
        return this.maxZ;
    }

    public AxisAlignedBB clone() {
        try {
            return (AxisAlignedBB) super.clone();
        } catch (CloneNotSupportedException cloneNotSupportedException) {
            Server.getInstance().getLogger().logException(cloneNotSupportedException);
            return null;
        }
    }

    public void forEach(BBConsumer action) {
        int minX = NukkitMath.floorDouble(this.getMinX());
        int minY = NukkitMath.floorDouble(this.getMinY());
        int minZ = NukkitMath.floorDouble(this.getMinZ());

        int maxX = NukkitMath.floorDouble(this.getMaxX());
        int maxY = NukkitMath.floorDouble(this.getMaxY());
        int maxZ = NukkitMath.floorDouble(this.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    action.accept(x, y, z);
                }
            }
        }
    }


    public interface BBConsumer<T> {

        void accept(int x, int y, int z);

        default T get() {
            return null;
        }
    }
}
