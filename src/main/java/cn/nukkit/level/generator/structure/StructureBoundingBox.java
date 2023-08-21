package cn.nukkit.level.generator.structure;

import cn.nukkit.math.BlockVector3;

public class StructureBoundingBox {
    private BlockVector3 min;
    private BlockVector3 max;

    public StructureBoundingBox(final BlockVector3 min, final BlockVector3 max) {
        this.min = min;
        this.max = max;
    }

    public BlockVector3 getMin() {
        return min;
    }

    public BlockVector3 getMax() {
        return max;
    }

    public int getMinChunkX() {
        return min.x >> 4;
    }

    public int getMinChunkZ() {
        return min.z >> 4;
    }

    public int getMaxChunkX() {
        return max.x >> 4;
    }

    public int getMaxChunkZ() {
        return max.z >> 4;
    }

    public boolean isVectorInside(final BlockVector3 vec) {
        return vec.x >= min.x && vec.x <= max.x && vec.y >= min.y && vec.y <= max.y && vec.z >= min.z && vec.z <= max.z;
    }

    public boolean intersectsWith(final StructureBoundingBox boundingBox) {
        return boundingBox.getMin().x <= max.x && boundingBox.getMax().x >= min.x && boundingBox.getMin().y <= max.y && boundingBox.getMax().y >= min.y && boundingBox.getMin().z <= max.z && boundingBox.getMax().z >= min.z;
    }

    public boolean intersectsWith(final int minX, final int minZ, final int maxX, final int maxZ) {
        return minX <= max.x && maxX >= min.x && minZ <= max.z && maxZ >= min.z;
    }

    public void expandTo(final StructureBoundingBox boundingBox) {
        min = new BlockVector3(Math.min(min.x, boundingBox.getMin().x), Math.min(min.y, boundingBox.getMin().y), Math.min(min.z, boundingBox.getMin().z));
        max = new BlockVector3(Math.max(max.x, boundingBox.getMax().x), Math.max(max.y, boundingBox.getMax().y), Math.max(max.z, boundingBox.getMax().z));
    }

    public void offset(final BlockVector3 offset) {
        min = min.add(offset);
        max = max.add(offset);
    }
}
