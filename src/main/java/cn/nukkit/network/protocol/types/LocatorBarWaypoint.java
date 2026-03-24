package cn.nukkit.network.protocol.types;

import cn.nukkit.math.Vector3f;

import java.awt.*;

public class LocatorBarWaypoint {

    public int updateFlag;
    public Boolean visible;
    public WorldPosition worldPosition;
    public Integer textureId;
    public Color color;
    public Boolean clientPositionAuthority;
    public Long entityUniqueId;

    public static class WorldPosition {
        public Vector3f position;
        public int dimension;

        public WorldPosition(Vector3f position, int dimension) {
            this.position = position;
            this.dimension = dimension;
        }
    }
}
