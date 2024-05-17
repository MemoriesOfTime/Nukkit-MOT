package cn.nukkit.level.generator.math;

import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;

public class BoundingBox {
    public int x0;
    public int y0;
    public int z0;
    public int x1;
    public int y1;
    public int z1;

    public BoundingBox(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
    }

    public BoundingBox(final int x0, final int z0, final int x1, final int z1) {
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        y0 = 1;
        y1 = 512;
    }

    public static BoundingBox getUnknownBox() {
        return new BoundingBox(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static BoundingBox orientBox(final int x, final int y, final int z, final int xOffset, final int yOffset, final int zOffset, final int xLength, final int yLength, final int zLength, final BlockFace orientation) {
        return switch (orientation) {
            case NORTH ->
                    new BoundingBox(x + xOffset, y + yOffset, z - zLength + 1 + zOffset, x + xLength - 1 + xOffset, y + yLength - 1 + yOffset, z + zOffset);
            case WEST ->
                    new BoundingBox(x - zLength + 1 + zOffset, y + yOffset, z + xOffset, x + zOffset, y + yLength - 1 + yOffset, z + xLength - 1 + xOffset);
            case EAST ->
                    new BoundingBox(x + zOffset, y + yOffset, z + xOffset, x + zLength - 1 + zOffset, y + yLength - 1 + yOffset, z + xLength - 1 + xOffset);
            default ->
                    new BoundingBox(x + xOffset, y + yOffset, z + zOffset, x + xLength - 1 + xOffset, y + yLength - 1 + yOffset, z + zLength - 1 + zOffset);
        };
    }

    public boolean intersects(final BoundingBox boundingBox) {
        return x1 >= boundingBox.x0 && x0 <= boundingBox.x1 && z1 >= boundingBox.z0 && z0 <= boundingBox.z1 && y1 >= boundingBox.y0 && y0 <= boundingBox.y1;
    }

    public void expand(final BoundingBox boundingBox) {
        x0 = Math.min(x0, boundingBox.x0);
        y0 = Math.min(y0, boundingBox.y0);
        z0 = Math.min(z0, boundingBox.z0);
        x1 = Math.max(x1, boundingBox.x1);
        y1 = Math.max(y1, boundingBox.y1);
        z1 = Math.max(z1, boundingBox.z1);
    }

    public void move(final int x, final int y, final int z) {
        x0 += x;
        y0 += y;
        z0 += z;
        x1 += x;
        y1 += y;
        z1 += z;
    }

    public boolean isInside(final BlockVector3 vec) {
        return vec.x >= x0 && vec.x <= x1 && vec.z >= z0 && vec.z <= z1 && vec.y >= y0 && vec.y <= y1;
    }

    public BlockVector3 getLength() {
        return new BlockVector3(x1 - x0, y1 - y0, z1 - z0);
    }

    public int getXSpan() {
        return x1 - x0 + 1;
    }

    public int getYSpan() {
        return y1 - y0 + 1;
    }

    public int getZSpan() {
        return z1 - z0 + 1;
    }
}
