package cn.nukkit.level.structure;

import cn.nukkit.math.BlockVector3;

/**
 * 结构区域：最小角坐标 + 尺寸。
 * <p>
 * A structure region: minimum corner plus size.
 */
public final class StructureBox {

    public final int x;
    public final int y;
    public final int z;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;

    public StructureBox(BlockVector3 origin, BlockVector3 size) {
        if (size.x <= 0 || size.y <= 0 || size.z <= 0) {
            throw new IllegalArgumentException("Invalid structure size " + size.x + "x" + size.y + "x" + size.z);
        }
        this.x = origin.x;
        this.y = origin.y;
        this.z = origin.z;
        this.sizeX = size.x;
        this.sizeY = size.y;
        this.sizeZ = size.z;
    }

    public boolean contains(int bx, int by, int bz) {
        return bx >= this.x && bx < this.x + this.sizeX
                && by >= this.y && by < this.y + this.sizeY
                && bz >= this.z && bz < this.z + this.sizeZ;
    }

    public int index(int x, int y, int z) {
        return x * this.sizeY * this.sizeZ + y * this.sizeZ + z;
    }

    public BlockVector3 getOrigin() {
        return new BlockVector3(this.x, this.y, this.z);
    }
}
