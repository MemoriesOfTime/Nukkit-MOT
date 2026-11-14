package cn.nukkit.entity.data;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SetEntityDataPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("rawtypes")
class EntityMetadataStringTest {
    @Test
    void playerSelfOnlyNameChangesKeepOriginalPacketBytesAndRecipients() throws Exception {
        MockServer.init();
        SourceInterface source = mock(SourceInterface.class);
        NetworkPlayerSession transport = mock(NetworkPlayerSession.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(transport);
        Player player = new Player(source, 1L, new InetSocketAddress("127.0.0.1", 1));
        Player viewer = mock(Player.class);
        player.hasSpawned.put(1, viewer);
        List<DataPacket> sent = new ArrayList<>();
        doAnswer(call -> { sent.add(call.getArgument(0)); return null; })
                .when(transport).sendPacket(any(DataPacket.class));
        Field gameVersion = Player.class.getDeclaredField("gameVersion");
        gameVersion.setAccessible(true);

        int versions = 0;
        for (GameVersion version : GameVersion.getValues()) {
            if (version.isNetEase() || version.getProtocol() < ProtocolInfo.v1_20_0
                    || version.getProtocol() > ProtocolInfo.v1_26_50) continue;
            versions++;
            player.protocol = version.getProtocol();
            gameVersion.set(player, version);
            for (String name : new String[]{"Player 🐷", "", "renamed"}) {
                int before = sent.size();
                assertTrue(player.setDataPropertyAndSendOnlyToSelf(new StringEntityData(Entity.DATA_NAMETAG, name)));
                assertEquals(before + 1, sent.size());
                assertSame(player.getDataProperty(Entity.DATA_NAMETAG).getData(), player.getNameTag());
                assertEquals(!name.isEmpty(), player.hasCustomName());

                SetEntityDataPacket actual = assertInstanceOf(SetEntityDataPacket.class, sent.get(before));
                assertEquals(1, actual.metadata.getMap().size());
                assertSame(player.getDataProperty(Entity.DATA_NAMETAG), actual.metadata.get(Entity.DATA_NAMETAG));
                SetEntityDataPacket original = new SetEntityDataPacket();
                original.eid = player.getId();
                original.metadata = new EntityMetadata().putString(Entity.DATA_NAMETAG, name);
                original.protocol = version.getProtocol();
                original.gameVersion = version;
                actual.encode();
                original.encode();
                assertArrayEquals(original.getBuffer(), actual.getBuffer(), version.toString());

                assertFalse(player.setDataPropertyAndSendOnlyToSelf(
                        new StringEntityData(Entity.DATA_NAMETAG, new String(name))));
                assertEquals(before + 1, sent.size(), "an equal value must not send another packet");
            }
        }
        assertTrue(versions > 0);
        verifyNoInteractions(viewer);
    }

    @Test
    void repeatedNamesAvoidMapLookupsAndMatchOriginalStringLogic() throws Exception {
        EntityMetadata metadata = new EntityMetadata();
        CountingMap map = new CountingMap();
        Field storage = EntityMetadata.class.getDeclaredField("map");
        storage.setAccessible(true);
        storage.set(metadata, map);
        int id = Entity.DATA_NAMETAG;
        for (String name : new String[]{"", "Piggy", "renamed", ""}) {
            StringEntityData entry = new StringEntityData(id, name);
            metadata.put(entry);
            for (int tick = 0; tick < 1000; tick++) {
                // The old exact-class fast path read the map before repairing the entry id.
                EntityData value = map.get(id);
                String original = value == null ? "" : (String) value.setId(id).getData();
                int reads = map.reads;
                assertSame(original, metadata.getString(id));
                assertEquals(reads, map.reads, "the cached name must not probe the map each tick");
            }
            entry.data = "direct plugin edit";
            assertSame(entry.data, metadata.getString(id));
            entry.setData("setter edit");
            assertSame(entry.data, metadata.clone().getString(id));
            metadata.remove(id);
            assertEquals("", metadata.getString(id));
        }
        assertEquals(4000, map.reads, "only the original reference logic should read the map");
    }

    @Test
    void nameCacheKeepsPutIdCallbacksAndCloneReplacementSemantics() {
        EntityMetadata metadata = new EntityMetadata();
        int[] calls = {0};
        StringEntityData entry = new StringEntityData(Entity.DATA_NAMETAG, "shared") {
            @Override
            public int getId() {
                calls[0]++;
                return super.getId();
            }
        };
        metadata.put(entry);
        assertEquals(1, calls[0], "put must capture the map key without extra virtual callbacks");
        EntityMetadata copy = metadata.clone();
        copy.putString(Entity.DATA_NAMETAG, "copy");
        assertEquals("shared", metadata.getString(Entity.DATA_NAMETAG));
        assertEquals("copy", copy.getString(Entity.DATA_NAMETAG));
        metadata.putInt(Entity.DATA_NAMETAG, 1);
        assertThrows(ClassCastException.class, () -> metadata.getString(Entity.DATA_NAMETAG));
        metadata.remove(Entity.DATA_NAMETAG);
        assertEquals("", metadata.getString(Entity.DATA_NAMETAG));
        assertEquals("copy", copy.getString(Entity.DATA_NAMETAG));
    }

    private static final class CountingMap extends Int2ObjectOpenHashMap<EntityData> {
        int reads;

        @Override
        public EntityData get(int key) {
            reads++;
            return super.get(key);
        }
    }

    @Test
    void nativeStringsKeepPayloadIdentityNullAndIdRepair() {
        EntityMetadata metadata = new EntityMetadata();
        for (int id : new int[]{0, 4, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (String payload : new String[]{"", new String("Mob 🐷"), null}) {
                StringEntityData data = new StringEntityData(id, payload);
                metadata.put(data);
                data.setId(id ^ 1234);
                assertSame(payload, metadata.getString(id));
                assertEquals(id, data.getId());
                data.setData(payload == null ? "changed" : null);
                assertSame(data.data, metadata.getString(id));
            }
        }
    }

    @Test
    void missingAndRemovedStringsUseEmptyDefaultWithoutInsertion() {
        EntityMetadata metadata = new EntityMetadata();
        for (int id : new int[]{0, 4, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals("", metadata.getString(id));
            assertFalse(metadata.exists(id));
            metadata.putString(id, "name");
            metadata.remove(id);
            assertEquals("", metadata.getString(id));
            assertFalse(metadata.exists(id));
        }
    }

    @Test
    void wrongPayloadTypeStillThrows() {
        EntityMetadata metadata = new EntityMetadata().putInt(4, 123);
        assertThrows(ClassCastException.class, () -> metadata.getString(4));
    }

    @Test
    void customDataReturningStringKeepsVirtualCallbacks() {
        EntityMetadata metadata = new EntityMetadata();
        CustomData data = new CustomData(4, new String("custom"));
        metadata.put(data);
        assertSame(data.payload, metadata.getString(4));
        assertEquals(1, data.sets);
        assertEquals(1, data.reads);
    }

    @Test
    void stringSubclassKeepsHooksAndReentrantReads() {
        EntityMetadata metadata = new EntityMetadata();
        List<String> calls = new ArrayList<>();
        StringEntityData data = new StringEntityData(4, "subclass") {
            @Override
            public EntityData setId(int id) {
                calls.add("set");
                return super.setId(id);
            }

            @Override
            public String getData() {
                calls.add("read");
                assertEquals("", metadata.getString(9));
                return super.getData();
            }
        };
        metadata.put(data);
        assertSame(data.data, metadata.getString(4));
        assertEquals(List.of("set", "read"), calls);
    }

    @Test
    void setIdFailuresKeepOriginalFallbackAndPropagation() {
        EntityMetadata metadata = new EntityMetadata();
        CustomData data = new CustomData(4, "custom");
        metadata.put(data);
        data.mode = 1;
        assertEquals("", metadata.getString(4));
        assertEquals(0, data.reads);
        data.mode = 2;
        assertThrows(NullPointerException.class, () -> metadata.getString(4));
        data.mode = 3;
        assertSame(data.error, assertThrows(AssertionError.class, () -> metadata.getString(4)));
        assertEquals(3, data.sets);
        assertEquals(0, data.reads);
    }

    @Test
    void dataReadFailureStillEscapesGetOrDefaultCatch() {
        EntityMetadata metadata = new EntityMetadata();
        CustomData data = new CustomData(4, "custom");
        data.failRead = true;
        metadata.put(data);
        assertSame(data.failure, assertThrows(IllegalStateException.class, () -> metadata.getString(4)));
        assertEquals(1, data.sets);
        assertEquals(1, data.reads);
    }

    @Test
    void metadataSubclassReceivesFreshDefaultsThroughOriginalHook() {
        List<EntityData> defaults = new ArrayList<>();
        EntityMetadata metadata = new EntityMetadata() {
            @Override
            public EntityData get(int id) {
                throw new AssertionError("get must not replace getOrDefault");
            }

            @Override
            public EntityData getOrDefault(int id, EntityData fallback) {
                defaults.add(fallback);
                assertEquals(StringEntityData.class, fallback.getClass());
                assertEquals(id, fallback.getId());
                assertEquals("", fallback.getData());
                return new StringEntityData(id, "hooked");
            }
        };
        metadata.putString(4, "stored");
        assertEquals("hooked", metadata.getString(4));
        assertEquals("hooked", metadata.getString(4));
        assertEquals(2, defaults.size());
        assertNotSame(defaults.get(0), defaults.get(1));
    }

    @Test
    void cloneKeepsIndependentMapAndSharedMutableData() {
        EntityMetadata metadata = new EntityMetadata();
        StringEntityData shared = new StringEntityData(4, "first");
        metadata.put(shared);
        EntityMetadata copy = metadata.clone();
        shared.setData(new String("second"));
        assertSame(shared.data, copy.getString(4));
        copy.remove(4);
        metadata.getMap().clear();
        assertSame(shared.data, metadata.getString(4));
        assertEquals("", copy.getString(4));
    }

    private static final class CustomData extends EntityData<String> {
        private String payload;
        private int sets;
        private int reads;
        private int mode;
        private boolean failRead;
        private final IllegalStateException failure = new IllegalStateException("fixture");
        private final AssertionError error = new AssertionError("fixture");

        private CustomData(int id, String payload) {
            super(id);
            this.payload = payload;
        }

        @Override public int getType() { return 4; }
        @Override public void setData(String value) { payload = value; }
        @Override public String getData() {
            reads++;
            if (failRead) throw failure;
            return payload;
        }
        @Override public EntityData setId(int id) {
            sets++;
            if (mode == 1) throw failure;
            if (mode == 2) return null;
            if (mode == 3) throw error;
            return super.setId(id);
        }
    }
}
