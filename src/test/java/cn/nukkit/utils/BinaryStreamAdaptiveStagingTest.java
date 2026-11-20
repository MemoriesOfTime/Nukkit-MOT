package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDiamond;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftResultsDeprecatedAction;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ThreadExecutorMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual selected-core item/NBT decoders; only runtime mapping and a one-entry item type table are fixtures. */
@Isolated("Temporarily replaces the item type table and the global adaptive direct pool")
@Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SAME_THREAD)
public class BinaryStreamAdaptiveStagingTest {
    private static final int RUNTIME_ID = 55, COUNT = 17, NET_ID = 1234, TAIL = 0x12345678;
    private static final List<String> PLACE = List.of("minecraft:stone", "minecraft:glass");
    private static final List<String> BREAK = List.of("minecraft:dirt", "");
    private static final List<Case> CASES = List.of(
            new Case("slot-589", GameVersion.V1_20_0, Kind.SLOT),
            new Case("slot-1001", GameVersion.V1_26_30, Kind.SLOT),
            new Case("descriptor-974", GameVersion.V1_26_20_26, Kind.DESCRIPTOR),
            new Case("descriptor-2168", GameVersion.V1_26_40, Kind.DESCRIPTOR),
            new Case("descriptor-2193", GameVersion.V1_26_50, Kind.DESCRIPTOR),
            new Case("craft-2168", GameVersion.V1_26_40, Kind.CRAFT),
            new Case("craft-2193", GameVersion.V1_26_50, Kind.CRAFT));

    public static List<String> caseNames() { return CASES.stream().map(Case::name).toList(); }

    /** Caller must serialize invocations because Item.list is a shared fixture boundary. */
    public static void runCase(String name, int payloadBytes, int iterations) throws Exception {
        Case selected = CASES.stream().filter(value -> value.name.equals(name)).findFirst().orElseThrow();
        run(List.of(selected), new int[] { payloadBytes }, iterations, false);
    }

    public static void runMatrix(int iterations) throws Exception {
        run(CASES, new int[] { 0, 65536 }, iterations, true);
    }

    @Test void byteMatrixAndExistingMalformedChecks() throws Exception { runMatrix(1); }
    @Test void legacySlotLargeNbtDoesNotWaitOnSharedMagazine() throws Exception { ioLoopRegression("slot-589"); }
    @Test void descriptorLargeNbtDoesNotWaitOnSharedMagazine() throws Exception { ioLoopRegression("descriptor-2193"); }
    @Test void craftResultLargeNbtDoesNotWaitOnSharedMagazine() throws Exception { ioLoopRegression("craft-2193"); }

    private static void run(List<Case> cases, int[] payloads, int iterations, boolean malformed) throws Exception {
        if (iterations < 1 || iterations > 128) throw new IllegalArgumentException("iterations must be 1..128");
        for (int bytes : payloads) if (bytes < 0 || bytes > 131072) throw new IllegalArgumentException("payload must be 0..128KiB");
        Class<?>[] previousItemTypes = Item.list;
        Class<?>[] fixtureItemTypes = new Class<?>[65535];
        fixtureItemTypes[Item.DIAMOND] = ItemDiamond.class;
        Item.list = fixtureItemTypes;
        int validated = 0, malformedCases = 0;
        // Mockito's static mock is intentionally installed in the actual decode operation thread.
        try (MockedStatic<RuntimeItems> runtimeItems = mockStatic(RuntimeItems.class, CALLS_REAL_METHODS)) {
            RuntimeItemMapping mapping = mock(RuntimeItemMapping.class, invocation -> switch (invocation.getMethod().getName()) {
                case "toRuntime" -> new RuntimeItemMapping.RuntimeEntry("minecraft:diamond", RUNTIME_ID, false);
                case "fromRuntime" -> new RuntimeItemMapping.LegacyEntry(Item.DIAMOND, false, 0);
                case "isRegistered" -> true;
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
            runtimeItems.when(() -> RuntimeItems.getMapping(any(GameVersion.class))).thenReturn(mapping);
            List<Fixture> fixtures = new ArrayList<>();
            for (Case value : cases) for (int bytes : payloads) fixtures.add(fixture(value, bytes));
            for (int iteration = 0; iteration < iterations; iteration++) {
                for (Fixture fixture : fixtures) {
                    verify(fixture, iteration == 0);
                    validated++;
                    if (iteration == 0) System.out.println("ITEM_CASE " + fixture.value.name
                            + " protocol=" + fixture.value.version.getProtocol() + " payload=" + fixture.payload
                            + " wire=" + fixture.wire.length + " nbt=" + fixture.nbt.length
                            + " sha256=" + sha256(fixture.wire));
                }
            }
            if (malformed) malformedCases = malformedChecks();
        } finally { Item.list = previousItemTypes; }
        System.out.println("ITEM_RESULT validated=" + validated + " malformed=" + malformedCases);
    }

    private static Fixture fixture(Case value, int payload) throws Exception {
        byte[] nbt = itemNbt(payload);
        BinaryStream wire = prefix();
        if (value.kind == Kind.CRAFT) {
            craftHeader(wire, value.version);
            wire.putUnsignedVarInt(1); wire.putByte((byte) 1); wire.putString("264");
            wire.putVarInt(0); wire.putLShort(COUNT); wire.putUnsignedVarInt(0);
            wire.putByteArray(userData(nbt, 1, false));
            craftFooter(wire);
        } else {
            Item item = new ItemDiamond(0, COUNT);
            item.setCompoundTag(nbt); item.setStackNetId(NET_ID);
            if (value.kind == Kind.SLOT) wire.putSlot(value.version, item);
            else wire.putNetworkItemStackDescriptor(value.version, item);
        }
        int consumed = wire.getCount(); wire.putLInt(TAIL);
        return new Fixture(value, payload, wire.getBuffer(), nbt, consumed);
    }

    private static void verify(Fixture fixture, boolean captureCorpus) throws Exception {
        byte[] input = fixture.wire.clone();
        BinaryStream stream = new BinaryStream(input, 1);
        Item decoded = decode(fixture.value, stream);
        assertEquals(Item.DIAMOND, decoded.getId()); assertEquals(COUNT, decoded.getCount()); assertEquals(0, decoded.getDamage());
        assertEquals(fixture.value.kind == Kind.CRAFT ? 0 : NET_ID, decoded.getStackNetId());
        CompoundTag expected = NBTIO.read(fixture.nbt, ByteOrder.LITTLE_ENDIAN);
        CompoundTag actual = decoded.getNamedTag();
        assertEquals(expected.getAllTags().stream().map(tag -> tag.getName()).collect(java.util.stream.Collectors.toSet()),
                actual.getAllTags().stream().map(tag -> tag.getName()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(expected.getString("label"), actual.getString("label"));
        assertEquals(expected.getInt("value"), actual.getInt("value"));
        assertArrayEquals(expected.getByteArray("payload"), actual.getByteArray("payload"), "every opaque payload byte");
        assertEquals(PLACE, strings(actual.getList("CanPlaceOn", StringTag.class)));
        assertEquals(BREAK, strings(actual.getList("CanDestroy", StringTag.class)));
        assertEquals(fixture.payload, actual.getByteArray("payload").length);
        assertEquals(fixture.consumed, stream.getOffset(), "decoder consumed exactly its framed item/request");
        assertEquals(TAIL, stream.getLInt()); assertEquals(input.length, stream.getOffset());
        assertArrayEquals(fixture.wire, input, "the caller's input byte[] must stay unchanged");
        if (captureCorpus) {
            String key = fixture.value.name + "-" + fixture.payload;
            corpus(key + "-wire", input);
            corpus(key + "-decoded-nbt", decoded.getCompoundTag());
            corpus(key + "-cursor-tail", ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(fixture.consumed).putInt(stream.getOffset()).putInt(TAIL).array());
        }
    }

    private static Item decode(Case value, BinaryStream stream) {
        if (value.kind == Kind.SLOT) return stream.getSlot(value.version);
        if (value.kind == Kind.DESCRIPTOR) return stream.getNetworkItemStackDescriptor(value.version);
        var request = stream.readItemStackRequest(value.version);
        assertEquals(17, request.getRequestId()); assertArrayEquals(new String[] { "fixture" }, request.getFilterStrings());
        assertNull(request.getTextProcessingEventOrigin()); assertEquals(1, request.getActions().length);
        CraftResultsDeprecatedAction action = assertInstanceOf(CraftResultsDeprecatedAction.class, request.getActions()[0]);
        assertEquals(3, action.getTimesCrafted()); assertEquals(1, action.getResultItems().length);
        return action.getResultItems()[0];
    }

    private static int malformedChecks() throws Exception {
        int count = 0;
        for (String name : List.of("slot-589", "descriptor-2193", "craft-2193")) {
            Case value = CASES.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
            byte[] nbt = itemNbt(0);
            rejection(value, userData(nbt, 2, false), IllegalArgumentException.class); count++;
            // A complete named-compound/byte-array header declaring 100 bytes, with only 8 trailing list-count bytes.
            byte[] truncatedNbt = new byte[] { 10, 0, 0, 7, 1, 0, 'a', 100, 0, 0, 0 };
            rejection(value, userData(truncatedNbt, 1, false), IllegalStateException.class); count++;
            if (value.kind != Kind.CRAFT) {
                rejection(value, userData(nbt, 1, true), RuntimeException.class); count++;
            }
        }
        return count;
    }

    private static void rejection(Case value, byte[] userData, Class<? extends Throwable> expected) {
        BinaryStream wire = prefix();
        if (value.kind == Kind.CRAFT) {
            craftHeader(wire, value.version);
            wire.putUnsignedVarInt(1); wire.putByte((byte) 1); wire.putString("264");
            wire.putVarInt(0); wire.putLShort(COUNT); wire.putUnsignedVarInt(0); wire.putByteArray(userData);
            craftFooter(wire);
        } else if (value.kind == Kind.SLOT) {
            wire.putVarInt(RUNTIME_ID); wire.putLShort(COUNT); wire.putUnsignedVarInt(0);
            wire.putBoolean(false); wire.putVarInt(0); wire.putByteArray(userData);
        } else {
            wire.putLShort(RUNTIME_ID); wire.putLShort(COUNT); wire.putUnsignedVarInt(0);
            wire.putBoolean(false); wire.putUnsignedVarInt(0); wire.putByteArray(userData);
        }
        byte[] input = wire.getBuffer(); byte[] original = input.clone();
        assertThrows(expected, () -> decode(value, new BinaryStream(input, 1)));
        assertArrayEquals(original, input, "malformed decoder must not alter input bytes");
    }

    private static byte[] itemNbt(int payload) throws Exception {
        byte[] bytes = new byte[payload]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31 + i / 256);
        CompoundTag tag = new CompoundTag("", new LinkedHashMap<>()).putString("label", "item\u0000Ж水😀").putInt("value", -1234567).putByteArray("payload", bytes);
        ListTag<StringTag> place = new ListTag<>("CanPlaceOn"); for (String entry : PLACE) place.add(new StringTag("", entry));
        ListTag<StringTag> destroy = new ListTag<>("CanDestroy"); for (String entry : BREAK) destroy.add(new StringTag("", entry));
        tag.putList(place).putList(destroy);
        return NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN);
    }
    private static byte[] userData(byte[] nbt, int tagCount, boolean overcount) {
        BinaryStream bytes = new BinaryStream(); bytes.putLShort(-1); bytes.putByte((byte) tagCount); bytes.put(nbt);
        bytes.putLInt(overcount ? 4097 : 0); bytes.putLInt(0); return bytes.getBuffer();
    }
    private static BinaryStream prefix() { BinaryStream wire = new BinaryStream(); wire.putByte((byte) 0x5a); return wire; }
    private static void craftHeader(BinaryStream wire, GameVersion version) {
        wire.putVarInt(17); wire.putUnsignedVarInt(1);
        assertTrue(version.getProtocol() >= 2168); wire.putUnsignedVarInt(17); wire.putByte((byte) 19); wire.putUnsignedVarInt(1);
    }
    private static void craftFooter(BinaryStream wire) {
        wire.putByte((byte) 3); wire.putUnsignedVarInt(1); wire.putString("fixture"); wire.putLInt(-1);
    }
    private static List<String> strings(ListTag<StringTag> list) { return list.getAll().stream().map(value -> value.data).toList(); }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void corpus(String key, byte[] bytes) throws Exception {
        String expected = System.getProperty("p08.expectedCorpus");
        if (expected != null) assertArrayEquals(Files.readAllBytes(Path.of(expected, key + ".bin")), bytes, "baseline byte equality: " + key);
        String output = System.getProperty("p08.corpus");
        if (output != null) {
            Path directory = Path.of(output); Files.createDirectories(directory);
            Files.write(directory.resolve(key + ".bin"), bytes);
        }
    }

    private static void ioLoopRegression(String name) throws Exception {
        Assumptions.assumeTrue(ByteBufAllocator.DEFAULT instanceof AdaptiveByteBufAllocator);
        Assumptions.assumeTrue(Runtime.getRuntime().maxMemory() > 512L * 1024 * 1024);
        runCase(name, 65536, 1); // class/mapping/NBT warm-up on main before the targeted IO call.
        AdaptiveByteBufAllocator global = (AdaptiveByteBufAllocator) ByteBufAllocator.DEFAULT;
        AdaptiveByteBufAllocator fresh = new AdaptiveByteBufAllocator();
        Field directField = field(AdaptiveByteBufAllocator.class, "direct");
        Object oldDirect = directField.get(global), freshDirect = directField.get(fresh);
        Object group = field(freshDirect.getClass(), "largeBufferMagazineGroup").get(freshDirect);
        Object[] magazines = (Object[]) field(group.getClass(), "magazines").get(group);
        assertEquals(1, magazines.length);
        StampedLock lock = (StampedLock) field(magazines[0].getClass(), "allocationLock").get(magazines[0]);
        MultiThreadIoEventLoopGroup eventLoops = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventExecutor loop = eventLoops.next(); AtomicReference<Thread> worker = new AtomicReference<>();
        Future<?> future = null; long stamp = 0; boolean completed = false; boolean exactWait = false;
        try {
            assertEquals("io.netty.channel.SingleThreadIoEventLoop", loop.getClass().getName());
            directField.set(global, freshDirect);
            loop.submit(() -> {
                worker.set(Thread.currentThread());
                Thread.currentThread().setName("p08-item-" + name);
                assertTrue(FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals());
                assertSame(loop, ThreadExecutorMap.currentExecutor());
                runCase(name, 0, 1); return null;
            }).get(5, TimeUnit.SECONDS);
            stamp = lock.writeLock();
            loop.submit(() -> { runCase(name, 48, 1); return null; }).get(5, TimeUnit.SECONDS);
            System.out.println("SMALL_CONTROL " + name + " completedBeforeUnlock=true");
            future = loop.submit(() -> { runCase(name, 65536, 1); return null; });
            try { future.get(200, TimeUnit.MILLISECONDS); completed = true; }
            catch (TimeoutException expectedBeforeFix) {
                StackTraceElement[] stack = worker.get().getStackTrace();
                exactWait = Arrays.stream(stack).anyMatch(frame -> frame.getClassName().equals("io.netty.buffer.AdaptivePoolingAllocator$Magazine") && frame.getMethodName().equals("free"));
                System.out.println("ITEM_WAIT_STACK " + name + " " + Arrays.toString(stack));
            }
        } finally {
            if (stamp != 0) lock.unlockWrite(stamp);
            try { if (future != null) future.get(5, TimeUnit.SECONDS); }
            finally {
                directField.set(global, oldDirect);
                eventLoops.shutdownGracefully(0, 5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS);
                List<Object> allGroups = new ArrayList<>(Arrays.asList((Object[]) field(freshDirect.getClass(), "sizeClassedMagazineGroups").get(freshDirect)));
                allGroups.add(group);
                for (Object ownedGroup : allGroups) { Method free = ownedGroup.getClass().getDeclaredMethod("free"); free.setAccessible(true); free.invoke(ownedGroup); }
                assertEquals(0, fresh.usedDirectMemory(), "temporary pool memory released after IO loop cleanup");
            }
        }
        System.out.println("ITEM_IO_WAIT " + name + " completedBeforeUnlock=" + completed + " nativeRetirementStack=" + exactWait);
        if (!completed) assertTrue(exactWait, "timeout must be the native adaptive shared-magazine retirement");
        assertTrue(completed, "IO-loop item byte[] decode must finish while unrelated shared magazine is held");
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
    }
    private enum Kind { SLOT, DESCRIPTOR, CRAFT }
    private record Case(String name, GameVersion version, Kind kind) { }
    private record Fixture(Case value, int payload, byte[] wire, byte[] nbt, int consumed) { }
}
