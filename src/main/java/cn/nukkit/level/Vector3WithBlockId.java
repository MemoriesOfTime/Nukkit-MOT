package cn.nukkit.level;

import cn.nukkit.math.Vector3;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Vector3WithBlockId extends Vector3 {
    private int blockIdLayer0;
    private int blockIdLayer1;

    public Vector3WithBlockId(double x, double y, double z, int blockIdLayer0, int blockIdLayer1) {
        super(x, y, z);
        this.blockIdLayer0 = blockIdLayer0;
        this.blockIdLayer1 = blockIdLayer1;
    }
}