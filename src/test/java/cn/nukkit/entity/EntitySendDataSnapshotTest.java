package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.SetEntityDataPacket;
import cn.nukkit.network.protocol.types.PropertySyncData;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EntitySendDataSnapshotTest {

    @Test
    void viewersShareOneSnapshotDetachedFromEntityMetadata() throws Exception {
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        entity.id = 42;
        EntityMetadata source = new EntityMetadata()
                .putLong(Entity.DATA_FLAGS, 7L)
                .putString(Entity.DATA_NAMETAG, "name");
        Field field = Entity.class.getDeclaredField("dataProperties");
        field.setAccessible(true);
        field.set(entity, source);
        doReturn(new PropertySyncData(new int[0], new float[0])).when(entity).propertySyncData();

        List<SetEntityDataPacket> sent = new ArrayList<>();
        Player[] viewers = new Player[3];
        for (int i = 0; i < viewers.length; i++) {
            viewers[i] = mock(Player.class);
            doAnswer(a -> {
                sent.add((SetEntityDataPacket) a.getArgument(0, DataPacket.class));
                return true;
            }).when(viewers[i]).dataPacket(any(DataPacket.class));
        }

        entity.sendData(viewers);

        assertEquals(3, sent.size());
        EntityMetadata snapshot = sent.get(0).metadata;
        assertNotSame(source, snapshot);
        for (SetEntityDataPacket pk : sent) {
            assertEquals(42, pk.eid);
            assertSame(snapshot, pk.metadata);
            assertEquals(7L, pk.metadata.getLong(Entity.DATA_FLAGS));
            assertEquals("name", pk.metadata.getString(Entity.DATA_NAMETAG));
        }

        // Later entity changes must not leak into the already queued packets, and vice versa
        source.putString(Entity.DATA_NAMETAG, "renamed");
        assertEquals("name", snapshot.getString(Entity.DATA_NAMETAG));
        snapshot.putLong(Entity.DATA_FLAGS, 0L);
        assertEquals(7L, source.getLong(Entity.DATA_FLAGS));
    }
}
