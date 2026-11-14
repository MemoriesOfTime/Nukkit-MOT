package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MoveEntityDeltaPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntityMovementDeltaYTest {
    private final ObjenesisStd objects = new ObjenesisStd();

    @Test
    void horizontalMoveWithBaseOffsetOmitsY() {
        Viewer viewer = objects.newInstance(Viewer.class);
        viewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
        viewer.packets = new ArrayList<>();
        OffsetEntity entity = entity(viewer, 64);

        entity.x += 1;
        entity.updateMovement();

        MoveEntityDeltaPacket packet = (MoveEntityDeltaPacket) viewer.packets.get(0);
        assertNotEquals(0, packet.flags & MoveEntityDeltaPacket.FLAG_HAS_X);
        assertEquals(0, packet.flags & MoveEntityDeltaPacket.FLAG_HAS_Y);
    }

    @Test
    void verticalMoveWithBaseOffsetSendsOffsetY() {
        Viewer viewer = objects.newInstance(Viewer.class);
        viewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
        viewer.packets = new ArrayList<>();
        OffsetEntity entity = entity(viewer, 64);

        entity.y += 1;
        entity.updateMovement();

        MoveEntityDeltaPacket packet = (MoveEntityDeltaPacket) viewer.packets.get(0);
        assertNotEquals(0, packet.flags & MoveEntityDeltaPacket.FLAG_HAS_Y);
        assertEquals((float) (65 + OffsetEntity.OFFSET), packet.y);
    }

    private OffsetEntity entity(Viewer viewer, double y) {
        OffsetEntity entity = objects.newInstance(OffsetEntity.class);
        entity.level = objects.newInstance(Level.class);
        entity.viewers = new HashMap<>();
        entity.viewers.put(1, viewer);
        entity.y = y;
        entity.lastY = y;
        return entity;
    }

    private static final class OffsetEntity extends Entity {
        static final float OFFSET = 0.35f;
        private Map<Integer, Player> viewers;

        private OffsetEntity() {
            super(null, null);
        }

        @Override
        public int getNetworkId() {
            return 64;
        }

        @Override
        protected float getBaseOffset() {
            return OFFSET;
        }

        @Override
        public Map<Integer, Player> getViewers() {
            return viewers;
        }
    }

    private static final class Viewer extends Player {
        private List<DataPacket> packets;

        private Viewer() {
            super(null, 0L, null);
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            packets.add(packet);
            return true;
        }
    }
}
