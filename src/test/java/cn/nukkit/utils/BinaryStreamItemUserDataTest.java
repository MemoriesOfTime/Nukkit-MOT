package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises heap-buffer ownership and real item wire round trips on public APIs. */
class BinaryStreamItemUserDataTest {
    private static final String[] ITEMS = {"null", "air", "plain", "nbt", "long128",
            "long16384", "long65537", "block", "durable", "damage-conflict", "shield", "lists"};

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    record Case(GameVersion version, boolean descriptor, boolean instance, boolean netId, String item) {
        String key() {
            return version.name() + "/" + descriptor + "/" + instance + "/" + netId + "/" + item;
        }

        void write(BinaryStream stream, Item value) {
            if (descriptor) stream.putNetworkItemStackDescriptor(version, value, instance);
            else stream.putSlot(version, value, instance);
        }
    }

    static Stream<Case> cases() {
        List<Case> result = new ArrayList<>();
        for (GameVersion version : GameVersion.values()) {
            if (version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
            for (boolean descriptor : new boolean[]{false, true}) {
                for (boolean instance : new boolean[]{false, true}) {
                    for (boolean netId : new boolean[]{false, true}) {
                        for (String item : ITEMS) result.add(new Case(version, descriptor, instance, netId, item));
                    }
                }
            }
        }
        return result.stream();
    }

    static Stream<Case> majorPaths() {
        return cases().filter(c -> !c.netId && c.item.equals("nbt") && !c.version.isNetEase()
                && (c.version == GameVersion.V1_20_0 || c.version == GameVersion.V1_26_40
                || c.version == GameVersion.getLastVersion()));
    }

    static Item item(Case c) {
        if (c.item.equals("null")) return null;
        Item item = switch (c.item) {
            case "air" -> Item.get(Item.AIR, 0, 0);
            case "block" -> Item.get(Item.STONE, 1, 17);
            case "durable", "damage-conflict" -> Item.get(Item.DIAMOND_SWORD, 73, 1);
            case "shield" -> Item.get(Item.SHIELD, 29, 1);
            default -> Item.get(Item.DIAMOND, 0, 17);
        };
        CompoundTag tag = new CompoundTag();
        switch (c.item) {
            case "nbt" -> tag.putString("label", "item \u0000 \u0416 \u6c34 \ud83d\ude80").putInt("value", -1234567);
            case "long128", "long16384", "long65537" -> {
                byte[] data = new byte[Integer.parseInt(c.item.substring(4))];
                for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31 + i / 256);
                tag.putByteArray("payload", data);
            }
            case "damage-conflict" -> tag.putString("Damage", "original").putInt("extra", 7);
            case "shield", "lists" -> {
                tag.putList(new ListTag<StringTag>("CanPlaceOn")
                        .add(new StringTag("", "minecraft:stone"))
                        .add(new StringTag("", "\u0416\u6c34\ud83d\ude80\u0000")));
                tag.putList(new ListTag<StringTag>("CanDestroy")
                        .add(new StringTag("", "minecraft:dirt"))
                        .add(new StringTag("", "")));
            }
            default -> { }
        }
        if (!tag.isEmpty()) item.setNamedTag(tag);
        item.setStackNetId(c.netId ? 12345 : 0);
        return item;
    }

    static byte[] encode(Case c, Item item) {
        BinaryStream stream = new BinaryStream();
        stream.putByte(0x5a); // append offset and following field must survive too
        c.write(stream, item);
        stream.putLInt(0x12345678);
        return stream.getBuffer();
    }

    @TestFactory
    Stream<DynamicTest> ordinaryItemsRoundTripAcrossTheSupportedWindow() {
        return cases().filter(c -> !c.descriptor && !c.instance && !c.netId
                        && !c.version.isNetEase()
                        && Set.of("plain", "nbt", "long128", "long16384", "long65537").contains(c.item))
                .map(c -> DynamicTest.dynamicTest(c.key(), () -> {
                    Item original = item(c);
                    BinaryStream stream = new BinaryStream(encode(c, original));
                    assertEquals(0x5a, stream.getByte());
                    Item restored = stream.getSlot(c.version);
                    assertEquals(original.getId(), restored.getId());
                    assertEquals(original.getCount(), restored.getCount());
                    if (c.item.equals("nbt")) {
                        assertEquals(original.getNamedTag().getString("label"), restored.getNamedTag().getString("label"));
                        assertEquals(-1234567, restored.getNamedTag().getInt("value"));
                    } else if (c.item.startsWith("long")) {
                        assertArrayEquals(original.getNamedTag().getByteArray("payload"), restored.getNamedTag().getByteArray("payload"));
                    }
                    assertEquals(0x12345678, stream.getLInt());
                    assertTrue(stream.feof());
                }));
    }

    private static MockedStatic<Unpooled> capture(List<ByteBuf> buffers) {
        return mockStatic(Unpooled.class, call -> {
            if (call.getMethod().getName().equals("buffer") && call.getArguments().length == 1
                    && Integer.valueOf(64).equals(call.getArgument(0))) {
                ByteBuf buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(64);
                buffers.add(buffer);
                return buffer;
            }
            return call.callRealMethod();
        });
    }

    private static void assertReleasedHeap(List<ByteBuf> buffers) {
        assertEquals(1, buffers.size(), "exactly one invocation-owned heap staging buffer");
        assertFalse(buffers.get(0).isDirect());
        assertEquals(0, buffers.get(0).refCnt(), "staging buffer must be released on every exit");
    }

    @ParameterizedTest
    @MethodSource("majorPaths")
    void releasesHeapAfterSuccess(Case c) {
        byte[] expected = encode(c, item(c));
        List<ByteBuf> buffers = new ArrayList<>();
        try (var ignored = capture(buffers)) {
            assertArrayEquals(expected, encode(c, item(c)));
        }
        assertReleasedHeap(buffers);
    }

    @ParameterizedTest
    @MethodSource("majorPaths")
    void releasesHeapAfterFailureHalfwayThroughUserData(Case c) {
        Item item = spy(item(c));
        IllegalStateException failure = new IllegalStateException("injected after NBT bytes");
        doThrow(failure).when(item).getNamedTag();
        List<ByteBuf> buffers = new ArrayList<>();
        try (var ignored = capture(buffers)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> encode(c, item)));
        }
        assertReleasedHeap(buffers);
        assertDoesNotThrow(() -> encode(c, item(c)), "next call cannot inherit partial userData");
    }

    @ParameterizedTest
    @MethodSource("majorPaths")
    void releasesHeapWhenDestinationThrows(Case c) {
        IllegalStateException failure = new IllegalStateException("injected destination failure");
        BinaryStream stream = new BinaryStream() {
            @Override public void putByteArray(byte[] bytes) { throw failure; }
        };
        List<ByteBuf> buffers = new ArrayList<>();
        try (var ignored = capture(buffers)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> c.write(stream, item(c))));
        }
        assertReleasedHeap(buffers);
    }

    @ParameterizedTest
    @MethodSource("majorPaths")
    void releasesHeapAfterMalformedDurableNbt(Case c) {
        Case durable = new Case(c.version, c.descriptor, false, false, "durable");
        Item item = spy(item(durable));
        // Bypass Item's own validation to exercise the serializer's IOException path.
        doReturn(new byte[]{10, 0, 0, 8}).when(item).getCompoundTag();
        List<ByteBuf> buffers = new ArrayList<>();
        try (var ignored = capture(buffers)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> encode(durable, item));
            assertInstanceOf(IOException.class, failure.getCause());
        }
        assertReleasedHeap(buffers);
    }

    @Test
    void independentConcurrentWriters() throws Exception {
        Map<String, byte[]> expected = new LinkedHashMap<>();
        cases().filter(c -> !c.item.equals("long65537")).forEach(c -> expected.put(c.key(), encode(c, item(c))));
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int thread = 0; thread < 3; thread++) results.add(executor.submit(() -> {
                start.await();
                for (Case c : cases().filter(c -> !c.item.equals("long65537")).toList()) {
                    assertArrayEquals(expected.get(c.key()), encode(c, item(c)), c.key());
                }
                return null;
            }));
            start.countDown();
            for (Future<?> result : results) result.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

}
