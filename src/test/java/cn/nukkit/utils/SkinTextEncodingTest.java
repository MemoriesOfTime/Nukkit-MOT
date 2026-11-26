package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.data.Skin;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SkinTextEncodingTest {
    private static final String PATCH = "patch-ß\uDC00";
    private static final String GEOMETRY = "geom-é-😀-\uD800!";

    @BeforeAll static void init() { MockServer.init(); }
    @AfterAll static void cleanup() { MockServer.reset(); }

    private static Skin fixture() {
        var skin = new Skin();
        skin.setSkinId("id");
        skin.setFullSkinId("full");
        skin.setPlayFabId("pf");
        skin.setSkinResourcePatch(PATCH);
        skin.setSkinData(new SerializedImage(1, 1, new byte[]{1, 2, 3, 4}));
        skin.setCapeData(new SerializedImage(1, 1, new byte[]{5, 6, 7, 8}));
        skin.setGeometryData(GEOMETRY);
        skin.setGeometryDataEngineVersion("engine");
        skin.setAnimationData("anim");
        skin.setCapeId("cape");
        skin.setTrusted(true);
        skin.setPrimaryUser(true);
        skin.setSkinIID("iid");
        skin.setUdid("ud");
        return skin;
    }

    @Test
    void encodedFramesMatchPreOptimizationBytesAcrossProtocolBoundaries() throws Exception {
        Map<GameVersion, String> digests = Map.of(
                GameVersion.V1_2_0, "c2d4d39a6039e674164d15543b369a465dbd8795910c335312a6926d78870e34",
                GameVersion.V1_12_0, "d3c27fec5b74870b68659dce5da1b9254f1a232fc4af8c9fb67fbc0dd730953b",
                GameVersion.V1_13_0, "57d5d0dc9df5b5762715dbdca56d9bde5bdcc5c95121b22830d13a2959544940",
                GameVersion.V1_16_210, "10efff39cecca935197ea57e2c578e441f99a74b59830b44d07e33d8a4015fdc",
                GameVersion.V1_17_30, "8771f99689ceb552e6e9880620e2750e9d9280ae9485d00877d8c17872d847a6",
                GameVersion.V1_21_50_NETEASE, "dadd4b7a656695744ca024d8bf2d9c8b69f5416c6ba46c9a478fb4e4114cbd78",
                GameVersion.V1_26_40, "0d8507a7d745ae2b3dfdd1e2fd4b9146eb698b61b95edfad46c148c24c0fb657",
                GameVersion.V1_26_60, "f72ae64736575bef2a024c79436edb51427da0548e5b107879e9ebebfe490473");
        Skin skin = fixture();
        for (var expected : digests.entrySet()) {
            var stream = new BinaryStream();
            for (int encode = 0; encode < 2; encode++) {
                stream.reset();
                stream.putSkin(expected.getKey(), skin);
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(stream.getBuffer());
                assertEquals(expected.getValue(), HexFormat.of().formatHex(hash), expected.getKey().name());
                Arrays.fill(stream.getBufferUnsafe(), (byte) 0x7e);
            }
        }
    }

    @Test
    void cachedBytesFollowTheEffectiveGetterAndRemainPrivate() {
        Skin skin = fixture();
        Skin.EncodedText first = skin.encodedGeometryData();
        assertSame(first, skin.encodedGeometryData());
        assertSame(skin.encodedSkinResourcePatch(), skin.encodedSkinResourcePatch());
        assertArrayEquals(GEOMETRY.getBytes(StandardCharsets.UTF_8), bytes(first));
        byte[] copy = bytes(first);
        Arrays.fill(copy, (byte) 0x7e);
        assertArrayEquals(GEOMETRY.getBytes(StandardCharsets.UTF_8), bytes(first));
        skin.setGeometryData(" ");
        assertArrayEquals(Skin.STEVE_GEOMETRY.getBytes(StandardCharsets.UTF_8), bytes(skin.encodedGeometryData()));
        skin.setSkinResourcePatch(null);
        assertArrayEquals(Skin.GEOMETRY_CUSTOM.getBytes(StandardCharsets.UTF_8), bytes(skin.encodedSkinResourcePatch()));
        String oversized = "x".repeat(Skin.SKIN_PERSONA_SIZE + 1);
        Skin dynamic = new Skin() {
            @Override public String getGeometryData() { return oversized; }
            @Override public String getSkinResourcePatch() { return oversized; }
        };
        assertNotSame(dynamic.encodedGeometryData(), dynamic.encodedGeometryData());
        assertNotSame(dynamic.encodedSkinResourcePatch(), dynamic.encodedSkinResourcePatch());
        assertArrayEquals(oversized.getBytes(StandardCharsets.UTF_8), bytes(dynamic.encodedGeometryData()));
    }

    private static byte[] bytes(Skin.EncodedText text) {
        byte[] bytes = new byte[text.byteLength()];
        text.copyTo(bytes, 0);
        return bytes;
    }

    @Test
    void customStringAndByteWritersReceiveTheOriginalCallbacksAndFreshArrays() {
        Skin skin = fixture();
        byte[] geometryBytes = GEOMETRY.getBytes(StandardCharsets.UTF_8);
        class ArrayWitness {
            byte[] first;
            int seen;
            void capture(byte[] value) {
                if (!Arrays.equals(geometryBytes, value)) return;
                if (seen++ == 0) first = value;
                else assertNotSame(first, value);
                value[0] = (byte) '#';
            }
        }
        var byteArray = new ArrayWitness();
        var raw = new ArrayWitness();
        var throughByteArray = new BinaryStream() {
            @Override public void putByteArray(byte[] data) {
                byteArray.capture(data);
                super.putByteArray(data);
            }
        };
        var throughPut = new BinaryStream() {
            @Override public void put(byte[] data) {
                raw.capture(data);
                super.put(data);
            }
        };
        var strings = new BinaryStream() {
            int writes;
            @Override public void putString(String value) {
                if (value == PATCH || value == GEOMETRY) writes++;
                super.putString(value);
            }
        };
        for (int i = 0; i < 2; i++) {
            throughByteArray.putSkin(GameVersion.V1_26_60, skin);
            throughPut.putSkin(GameVersion.V1_26_60, skin);
            strings.putSkin(GameVersion.V1_26_60, skin);
        }
        assertEquals(2, byteArray.seen);
        assertEquals(2, raw.seen);
        assertEquals(4, strings.writes);
        assertArrayEquals(geometryBytes, bytes(skin.encodedGeometryData()));
    }

    @Test
    void aPrefixCallbackCanChangeTheLaterFieldWithoutChangingTheCapturedOne() {
        Skin skin = fixture();
        var stream = new BinaryStream() {
            boolean fired;
            @Override public void putUnsignedVarInt(long length) {
                if (!fired && length == PATCH.getBytes(StandardCharsets.UTF_8).length) {
                    fired = true;
                    skin.setSkinResourcePatch("later patch");
                    skin.setGeometryData("later geometry");
                }
                super.putUnsignedVarInt(length);
            }
        };
        stream.putSkin(GameVersion.V1_13_0, skin);
        assertTrue(stream.fired);
        Skin expected = fixture();
        expected.setGeometryData("later geometry");
        var reference = new BinaryStream() {
            @Override public void putString(String value) { super.putString(value); }
        };
        reference.putSkin(GameVersion.V1_13_0, expected);
        assertArrayEquals(reference.getBuffer(), stream.getBuffer());
    }

    @Test
    void repeatedEncodingAvoidsBothUtf8Arrays() {
        Skin skin = fixture();
        String geometry = "geometry_😀".repeat(1500);
        String patch = "resource-é".repeat(60);
        skin.setGeometryData(geometry);
        skin.setSkinResourcePatch(patch);
        var nativeStream = new BinaryStream(64 * 1024);
        var reference = new BinaryStream(64 * 1024) {
            @Override public void putString(String value) { super.putString(value); }
        };
        for (int i = 0; i < 600; i++) {
            encode(nativeStream, skin);
            encode(reference, skin);
        }
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        int repeats = 128;
        long before = mx.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < repeats; i++) encode(nativeStream, skin);
        long nativeBytes = mx.getCurrentThreadAllocatedBytes() - before;
        before = mx.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < repeats; i++) encode(reference, skin);
        long referenceBytes = mx.getCurrentThreadAllocatedBytes() - before;
        int fieldBytes = geometry.getBytes(StandardCharsets.UTF_8).length + patch.getBytes(StandardCharsets.UTF_8).length;
        assertArrayEquals(reference.getBuffer(), nativeStream.getBuffer());
        assertTrue(referenceBytes - nativeBytes >= (long) repeats * (fieldBytes - 128),
                () -> "native=" + nativeBytes + ", reference=" + referenceBytes);
    }

    private static void encode(BinaryStream stream, Skin skin) {
        stream.reset();
        stream.putSkin(GameVersion.V1_21_50_NETEASE, skin);
    }

    @Test
    void competingGettersCannotPairOneStringWithAnotherStringsBytes() throws Exception {
        var selected = new ThreadLocal<String>();
        Skin shared = new Skin() {
            @Override public String getGeometryData() { return selected.get(); }
            @Override public String getSkinResourcePatch() { return selected.get(); }
        };
        var start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = new ArrayList<Future<?>>();
            for (String value : new String[]{"long-😀-geometry", "é"}) {
                tasks.add(pool.submit(() -> {
                    selected.set(value);
                    byte[] expected = value.getBytes(StandardCharsets.UTF_8);
                    start.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < 10_000; i++) {
                        assertArrayEquals(expected, bytes(shared.encodedGeometryData()));
                        assertArrayEquals(expected, bytes(shared.encodedSkinResourcePatch()));
                    }
                    return null;
                }));
            }
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
