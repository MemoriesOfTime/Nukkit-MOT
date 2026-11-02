package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.Hash;
import com.google.gson.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Typed states and runtime ordinals come from version-pinned independent native palette exports. */
class VersionedNativePaletteRestorationTest {
    private static final Map<Integer, ListTag<?>> RAW = new HashMap<>();
    private static final Map<Integer, BlockPalette> NATIVE = new HashMap<>();

    @BeforeAll static void boot() { MockServer.init(); }

    @TestFactory Stream<DynamicTest> exactIndependentTuplesReachNativeForwardAndHashLookups() throws Exception {
        return json("tuples.json").asList().stream().map(value -> {
            JsonObject tuple = value.getAsJsonObject();
            int protocol = tuple.get("protocol").getAsInt();
            int id = tuple.get("id").getAsInt(), meta = tuple.get("meta").getAsInt();
            return DynamicTest.dynamicTest(protocol + ":" + id + ":" + meta, () -> {
                ListTag<?> rows = RAW.computeIfAbsent(protocol, VersionedNativePaletteRestorationTest::rawUnchecked);
                List<CompoundTag> matches = rows.getAll().stream().map(CompoundTag.class::cast)
                        .filter(row -> row.getInt("id") == id && row.getShort("data") == meta).toList();
                assertEquals(1, matches.size(), "Exactly one native legacy tuple, no fallback alias");
                CompoundTag row = matches.get(0);
                assertTrue(row.containsInt("id") && row.containsShort("data") && row.containsInt("runtimeId"));
                int runtimeId = tuple.get("runtimeId").getAsInt();
                assertEquals(runtimeId, row.getInt("runtimeId"));
                assertEquals(tuple.get("name").getAsString(), row.getString("name"));
                assertEquals(tuple.get("version").getAsInt(), row.getInt("version"));
                assertFalse(row.getBoolean("stateOverload"));
                assertTrue(row.containsCompound("states"));
                CompoundTag states = row.getCompound("states");
                JsonObject expectedStates = tuple.getAsJsonObject("states");
                assertEquals(expectedStates.keySet(), states.getTags().keySet());
                for (var property : expectedStates.entrySet()) {
                    JsonObject expected = property.getValue().getAsJsonObject();
                    Tag actual = states.get(property.getKey());
                    switch (expected.get("type").getAsString()) {
                        case "byte" -> assertEquals(expected.get("value").getAsInt(), assertInstanceOf(ByteTag.class, actual).data);
                        case "int" -> assertEquals(expected.get("value").getAsInt(), assertInstanceOf(IntTag.class, actual).data);
                        case "string" -> assertEquals(expected.get("value").getAsString(), assertInstanceOf(StringTag.class, actual).data);
                        default -> fail("Unreviewed property type");
                    }
                }
                BlockPalette palette = NATIVE.computeIfAbsent(protocol,
                        p -> new BlockPalette(GameVersion.byProtocol(p, false)));
                int fullId = (id << Block.DATA_BITS) | meta;
                assertEquals(runtimeId, palette.getLegacyToRuntimeIdMap().get(fullId), "Exact native registration, no getRuntimeId fallback");
                CompoundTag nativeState = row.copy().remove("id").remove("data").remove("runtimeId").remove("stateOverload");
                assertEquals(Hash.hashBlock(nativeState), palette.getHashId(id, meta), "Forward hash uses the exact typed state");
            });
        });
    }

    @TestFactory Stream<DynamicTest> everyUnaffectedResourceIsByteIdenticalAndModernNbtRoundTrips() throws Exception {
        return json("unaffected-resources.json").asList().stream().map(value -> {
            JsonObject resource = value.getAsJsonObject();
            String name = resource.get("resource").getAsString();
            return DynamicTest.dynamicTest(name, () -> {
                byte[] bytes;
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
                    assertNotNull(input, name); bytes = input.readAllBytes();
                }
                assertEquals(resource.get("sha256").getAsString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                int protocol = Integer.parseInt(name.substring(name.lastIndexOf('_') + 1, name.length() - 4));
                // Earlier resources use their own historical network/little-endian formats.
                if (protocol >= 407) roundTrip(read(bytes));
            });
        });
    }

    @TestFactory Stream<DynamicTest> affectedResourcesRetainNativeNbtRoundTrips() throws Exception {
        return json("tuples.json").asList().stream().map(v -> v.getAsJsonObject().get("protocol").getAsInt()).distinct()
                .map(protocol -> DynamicTest.dynamicTest("NBT round trip " + protocol, () -> roundTrip(rawUnchecked(protocol))));
    }

    private static void roundTrip(ListTag<?> original) throws Exception {
        byte[] encoded = NBTIO.write(original, ByteOrder.BIG_ENDIAN, false);
        assertEquals(original, NBTIO.readTag(new ByteArrayInputStream(encoded), ByteOrder.BIG_ENDIAN, false));
    }

    private static ListTag<?> rawUnchecked(int protocol) {
        try (InputStream input = VersionedNativePaletteRestorationTest.class.getClassLoader()
                .getResourceAsStream("runtime_block_states_" + protocol + ".dat")) {
            assertNotNull(input); return read(input.readAllBytes());
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static ListTag<?> read(byte[] bytes) throws IOException {
        try (var stream = new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            // Native NBTIO owns its child; the external root stream remains available for exact EOF.
            var child = new FilterInputStream(stream) { @Override public void close() {} };
            ListTag<?> root = assertInstanceOf(ListTag.class, NBTIO.readTag(child, ByteOrder.BIG_ENDIAN, false));
            assertEquals(-1, stream.read(), "Exactly one complete root"); return root;
        }
    }

    private static JsonArray json(String name) throws IOException {
        try (InputStream input = VersionedNativePaletteRestorationTest.class.getClassLoader()
                .getResourceAsStream("native-palette-restoration/" + name)) {
            assertNotNull(input); return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }
}
