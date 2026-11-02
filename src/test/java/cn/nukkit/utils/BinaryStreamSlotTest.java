package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises heap-buffer ownership and real item wire round trips on public APIs. */
class BinaryStreamSlotTest {
    private static final String[] ITEMS = {"null", "air", "plain", "nbt", "long128",
            "long16384", "long65537", "block", "durable", "damage-conflict", "shield", "lists"};

    private static final GameVersion V2168 = GameVersion.byProtocol(ProtocolInfo.v1_26_40, false);

    private static final List<GameVersion> VERSIONS = Arrays.stream(GameVersion.values())
            .filter(v -> !v.isNetEase() && v.getProtocol() >= ProtocolInfo.v1_20_0
                    && v.getProtocol() <= ProtocolInfo.v1_26_50).toList();
    private static Method craftingSlotReader;

    private static int diamondSwordRuntimeId;

    @BeforeAll
    static void init() throws Exception {
        MockServer.init();
        diamondSwordRuntimeId = RuntimeItems.getMapping(V2168)
                .toRuntime(Item.DIAMOND_SWORD, 0)
                .getRuntimeId();
        craftingSlotReader = BinaryStream.class.getDeclaredMethod("getSlotNew", GameVersion.class, boolean.class);
        craftingSlotReader.setAccessible(true);
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

    /**
     * v2168 槽位解码器 CanPlaceOn/CanDestroy 数量上限回归测试：恶意客户端可在极小 payload 中
     * 声明 Integer.MAX_VALUE 条目，使 new String[count] 直接抛出 OutOfMemoryError（Error，
     * 不被 catch (IOException) 拦截）。解码器必须执行与旧格式一致的 0..4096 校验。
     * <p>
     * Regression test for the CanPlaceOn/CanDestroy count cap in the v2168 slot decoder
     * (getSlotNewV2168): a malicious client could declare Integer.MAX_VALUE entries in a tiny
     * payload, making new String[count] throw OutOfMemoryError (an Error, not caught by the
     * surrounding catch (IOException)). The decoder must enforce the same 0..4096 validation
     * as the legacy format.
     */
    @Test
    void rejectsHugeCanPlaceOnCount() {
        BinaryStream userData = new BinaryStream();
        userData.putLShort(0); // 无 NBT / no NBT
        userData.putLInt(Integer.MAX_VALUE);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> decodeSlot(userData));
        assertTrue(ex.getMessage().contains("Too many CanPlaceOn"));
    }

    @Test
    void rejectsNegativeCanPlaceOnCount() {
        BinaryStream userData = new BinaryStream();
        userData.putLShort(0);
        userData.putLInt(-1);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> decodeSlot(userData));
        assertTrue(ex.getMessage().contains("Too many CanPlaceOn"));
    }

    @Test
    void rejectsHugeCanDestroyCount() {
        BinaryStream userData = new BinaryStream();
        userData.putLShort(0);
        userData.putLInt(0);
        userData.putLInt(Integer.MAX_VALUE);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> decodeSlot(userData));
        assertTrue(ex.getMessage().contains("Too many CanDestroy"));
    }

    @Test
    void acceptsSmallCanPlaceOnAndCanDestroyLists() {
        BinaryStream userData = new BinaryStream();
        userData.putLShort(0);
        userData.putLInt(1);
        putUtf(userData, "minecraft:stone");
        userData.putLInt(1);
        putUtf(userData, "minecraft:dirt");

        Item item = decodeSlot(userData);
        assertEquals(Item.DIAMOND_SWORD, item.getId());
        assertEquals(1, item.getCount());
    }

    // ==================== CB Protocol cross-validation ====================

    /**
     * v2168 ItemInstance（putSlot crafting=true）必须与 CB {@code BedrockCodecHelper_v2168.readItemInstance}
     * 兼容：blockRuntimeId 是 zigzag VarInt（非 VarUInt）。方块物品（blockRuntimeId != 0）此前编码错误，
     * 导致 1.26.40 客户端解析 CreativeContent/CraftingData 错位崩溃。
     * <p>
     * v2168 ItemInstance (putSlot crafting=true) must round-trip through the CB
     * {@code BedrockCodecHelper_v2168.readItemInstance}: blockRuntimeId is a zigzag VarInt (not VarUInt).
     * Block items (blockRuntimeId != 0) used to be encoded wrong, making 1.26.40 clients
     * desynchronize while parsing CreativeContent/CraftingData.
     */
    @Test
    void itemInstanceBlockRuntimeIdCrossDecodesWithCbProtocol() {
        Item input = Item.get(Block.STONE);
        input.setCount(3);
        int expectedBlockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(V2168, Block.STONE, 0);
        assertTrue(expectedBlockRuntimeId != 0, "stone should have a non-zero block runtime id in v2168");

        BinaryStream encoded = new BinaryStream();
        encoded.putSlot(V2168, input, true);

        BedrockCodecHelper helper = Bedrock_v2168.CODEC.createHelper();
        registerCbDefinitions(helper, expectedBlockRuntimeId);

        ItemData decoded = helper.readItemInstance(Unpooled.wrappedBuffer(encoded.getBuffer()));
        assertEquals(3, decoded.getCount());
        assertEquals("minecraft:stone", decoded.getDefinition().getIdentifier());
        assertEquals(expectedBlockRuntimeId, decoded.getBlockDefinition().getRuntimeId());
    }

    /**
     * v2168 ItemInstance 的 AIR 分支（blockRuntimeId=0）与 CB 兼容。
     * <p>
     * The v2168 ItemInstance AIR branch (blockRuntimeId=0) stays CB compatible.
     */
    @Test
    void itemInstanceAirCrossDecodesWithCbProtocol() {
        BinaryStream encoded = new BinaryStream();
        encoded.putSlot(V2168, Item.AIR_ITEM, true);

        BedrockCodecHelper helper = Bedrock_v2168.CODEC.createHelper();
        registerCbDefinitions(helper, 0);

        ItemData decoded = helper.readItemInstance(Unpooled.wrappedBuffer(encoded.getBuffer()));
        assertEquals(ItemDefinition.AIR, decoded.getDefinition());
        assertEquals(0, decoded.getCount());
    }

    /**
     * v2168 描述符（putSlot crafting=false）的 blockRuntimeId 是 VarUInt，与 CB
     * {@code readNetworkItemStackDescriptor} 兼容。回归保护：确保描述符路径不被 ItemInstance 修复误伤。
     * <p>
     * The v2168 descriptor (putSlot crafting=false) blockRuntimeId stays a VarUInt and must remain
     * CB compatible with {@code readNetworkItemStackDescriptor}. Guards the descriptor path against
     * regressions from the ItemInstance fix.
     */
    @Test
    void descriptorBlockRuntimeIdCrossDecodesWithCbProtocol() {
        Item input = Item.get(Block.STONE);
        input.setCount(3);
        int expectedBlockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(V2168, Block.STONE, 0);

        BinaryStream encoded = new BinaryStream();
        encoded.putSlot(V2168, input);

        BedrockCodecHelper helper = Bedrock_v2168.CODEC.createHelper();
        registerCbDefinitions(helper, expectedBlockRuntimeId);

        ItemData decoded = helper.readNetworkItemStackDescriptor(Unpooled.wrappedBuffer(encoded.getBuffer()));
        assertEquals(3, decoded.getCount());
        assertEquals(expectedBlockRuntimeId, decoded.getBlockDefinition().getRuntimeId());
        assertFalse(decoded.isUsingNetId());
    }

    private static void registerCbDefinitions(BedrockCodecHelper helper, int blockRuntimeId) {
        var itemDefinitions = SimpleDefinitionRegistry.<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition>builder();
        Set<Integer> seen = new HashSet<>();
        for (var entry : RuntimeItems.getMapping(V2168).getItemPaletteEntries()) {
            if (seen.add(entry.getRuntimeId())) {
                itemDefinitions.add(new SimpleItemDefinition(entry.getIdentifier(), entry.getRuntimeId(), false));
            }
        }
        helper.setItemDefinitions(itemDefinitions.build());
        helper.setBlockDefinitions(SimpleDefinitionRegistry
                .<org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition>builder()
                .add(new SimpleBlockDefinition("minecraft:stone", blockRuntimeId, NbtMap.EMPTY))
                .build());
    }

    private static Item decodeSlot(BinaryStream userData) {
        BinaryStream stream = new BinaryStream();
        stream.putLShort(diamondSwordRuntimeId);
        stream.putLShort(1);         // count
        stream.putUnsignedVarInt(0); // aux
        stream.putBoolean(false);    // hasStackNetId
        stream.putUnsignedVarInt(0); // blockRuntimeId
        stream.putByteArray(userData.getBuffer());
        return new BinaryStream(stream.getBuffer()).getSlot(V2168);
    }

    private static void putUtf(BinaryStream stream, String value) {
        // readUTF 线上格式：小端 unsigned short 字节长度 + 字节 / readUTF wire shape: LE unsigned short byte length + bytes
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        stream.putLShort(bytes.length);
        stream.put(bytes);
    }

    /** Native inbound decode regression: mushroom state must survive inbound item decoding. */
    @Test void ordinarySlotsPreserveMushroomState() throws Exception { roundTrips(Mode.SLOT); }
    @Test void craftingSlotsPreserveMushroomState() throws Exception { roundTrips(Mode.CRAFTING); }
    @Test void stackDescriptorsPreserveMushroomState() throws Exception { roundTrips(Mode.DESCRIPTOR); }
    @Test void instanceDescriptorsPreserveMushroomState() throws Exception { roundTrips(Mode.INSTANCE); }

    @Test
    void naturalCapsAndStemsRetainExactItemEquality() throws Exception {
        Checks checks = new Checks("natural-drops");
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) for (Mode mode : Mode.values()) {
                    if (!mode.supports(version)) continue;
                    for (int[] drop : new int[][]{{Block.BROWN_MUSHROOM_BLOCK, 14},
                            {Block.RED_MUSHROOM_BLOCK, 14}, {Block.BROWN_MUSHROOM_BLOCK, 15}}) {
                        for (boolean tagged : new boolean[]{false, true}) {
                            Item original = item(drop[0], drop[1], tagged);
                            Item decoded = mode.decode(version, mode.encode(version, original));
                            String at = version + " " + mode + " hash=" + hash + " natural=" + original;
                            // Independent player-facing invariant: no palette-derived expected value.
                            checks.expect(original.equals(decoded), at + " actual=" + decoded);
                            checks.expect(decoded.getDamage() == drop[1], at + " changed cap/stem variant");
                            verifyPayload(checks, original, decoded, at, tagged);
                            checks.cases++;
                        }
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    private static void roundTrips(Mode mode) throws Exception {
        Checks checks = new Checks(mode.name());
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) {
                    if (!mode.supports(version)) continue;
                    for (int id : new int[]{Block.BROWN_MUSHROOM_BLOCK, Block.RED_MUSHROOM_BLOCK}) {
                        for (int meta = 0; meta < 16; meta++) {
                            for (boolean tagged : new boolean[]{false, true}) {
                                Item original = item(id, meta, tagged);
                                int runtime = GlobalBlockPalette.getOrCreateRuntimeId(version, id, meta);
                                int full = inverse(version, runtime);
                                String at = version + " hash=" + hash + " " + id + ":" + meta + " tag=" + tagged;
                                checks.expect(full != -1, at + " palette must reverse its own runtime=" + runtime);
                                // Palette aliases may collapse raw variants. Never graft state from another block identity.
                                int expectedDamage = full != -1 && (full >> Block.DATA_BITS) == id
                                        ? full & Block.DATA_MASK : 0;
                                if (expectedDamage != meta) checks.aliases++;
                                Item decoded = mode.decode(version, mode.encode(version, original));
                                checks.expect(decoded.getId() == id, at + " item identity=" + decoded.getId());
                                checks.expect(decoded.getDamage() == expectedDamage,
                                        at + " expected damage=" + expectedDamage + " actual=" + decoded.getDamage());
                                verifyPayload(checks, original, decoded, at, tagged);
                                checks.cases++;
                            }
                        }
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    @Test
    void unrelatedAndUnknownRuntimeIdsCannotGraftDamage() throws Exception {
        Checks checks = new Checks("hostile-runtime");
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) for (Mode mode : Mode.values()) {
                    if (!mode.supports(version)) continue;
                    int foreign = GlobalBlockPalette.getOrCreateRuntimeId(version, Block.WOOL, 3);
                    int unknown = Integer.MAX_VALUE;
                    while (inverse(version, unknown) != -1) unknown--;
                    for (int id : new int[]{Block.BROWN_MUSHROOM_BLOCK, Block.RED_MUSHROOM_BLOCK}) {
                        int otherMushroom = GlobalBlockPalette.getOrCreateRuntimeId(version,
                                id == Block.BROWN_MUSHROOM_BLOCK ? Block.RED_MUSHROOM_BLOCK : Block.BROWN_MUSHROOM_BLOCK, 14);
                        for (int forged : new int[]{foreign, otherMushroom, unknown}) {
                            Item original = item(id, 0, true);
                            String at = version + " " + mode + " hash=" + hash + " id=" + id + " runtime=" + forged;
                            Item decoded = mode.decode(version, replaceRuntime(mode.encode(version, original), version, mode, forged));
                            checks.expect(decoded.getId() == id, at + " changed identity");
                            checks.expect(decoded.getDamage() == 0, at + " grafted damage=" + decoded.getDamage());
                            verifyPayload(checks, original, decoded, at, true);
                            checks.cases++;
                        }
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    @Test
    void neighboringItemsRemainUnaffected() throws Exception {
        Checks checks = new Checks("neighbors");
        boolean oldHash = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (GameVersion version : VERSIONS) for (Mode mode : Mode.values()) {
                    if (!mode.supports(version)) continue;
                    for (int[] neighbor : new int[][]{{Block.STONE, 0}, {Block.WOOL, 3}, {Item.DIAMOND_SWORD, 0}}) {
                        Item original = item(neighbor[0], neighbor[1], true);
                        Item decoded = mode.decode(version, mode.encode(version, original));
                        String at = version + " " + mode + " hash=" + hash + " neighbor=" + original;
                        checks.expect(original.equals(decoded), at + " actual=" + decoded);
                        verifyPayload(checks, original, decoded, at, true);
                        checks.cases++;
                    }
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(oldHash); }
        checks.finish();
    }

    private static Item item(int id, int meta, boolean tagged) {
        Item item = Item.get(id, meta, 4);
        if (tagged) item.setNamedTag(new CompoundTag().putString("qa_marker", "mushroom-state").putInt("qa_number", 1031));
        return item;
    }

    private static void verifyPayload(Checks checks, Item original, Item actual, String at, boolean tagged) {
        checks.expect(actual.getCount() == original.getCount(), at + " count=" + actual.getCount());
        if (tagged) {
            checks.expect(actual.getNamedTag().getString("qa_marker").equals("mushroom-state"), at + " lost string tag");
            checks.expect(actual.getNamedTag().getInt("qa_number") == 1031, at + " lost integer tag");
        } else checks.expect(!actual.hasCompoundTag(), at + " added NBT");
    }

    private static int inverse(GameVersion version, int runtime) {
        return GlobalBlockPalette.shouldUseHashedBlockNetworkIds(version)
                ? GlobalBlockPalette.getLegacyFullIdFromHashId(version, runtime)
                : GlobalBlockPalette.getLegacyFullId(version, runtime);
    }

    private static byte[] replaceRuntime(byte[] encoded, GameVersion version, Mode mode, int runtime) {
        BinaryStream reader = new BinaryStream(encoded);
        boolean descriptor = mode.descriptor || version.getProtocol() >= ProtocolInfo.v1_26_40;
        if (descriptor && !mode.instance) reader.getLShort(); else reader.getVarInt();
        reader.getLShort();
        reader.getUnsignedVarInt();
        if (!mode.instance && reader.getBoolean()) {
            if (descriptor && version.getProtocol() < ProtocolInfo.v1_26_40) reader.getUnsignedVarInt();
            reader.getVarInt();
        }
        int start = reader.getOffset();
        if (descriptor && !mode.instance) reader.getUnsignedVarInt(); else reader.getVarInt();
        int end = reader.getOffset();
        BinaryStream result = new BinaryStream();
        result.put(Arrays.copyOfRange(encoded, 0, start));
        if (descriptor && !mode.instance) result.putUnsignedVarInt(runtime); else result.putVarInt(runtime);
        result.put(Arrays.copyOfRange(encoded, end, encoded.length));
        return result.getBuffer();
    }

    private enum Mode {
        SLOT(false, false), CRAFTING(false, true), DESCRIPTOR(true, false), INSTANCE(true, true);
        final boolean descriptor;
        final boolean instance;
        Mode(boolean descriptor, boolean instance) { this.descriptor = descriptor; this.instance = instance; }
        boolean supports(GameVersion version) { return !descriptor || version.getProtocol() >= ProtocolInfo.v1_26_20_26; }
        byte[] encode(GameVersion version, Item item) {
            BinaryStream stream = new BinaryStream();
            if (descriptor) stream.putNetworkItemStackDescriptor(version, item, instance);
            else stream.putSlot(version, item, instance);
            return stream.getBuffer();
        }
        Item decode(GameVersion version, byte[] data) throws Exception {
            BinaryStream stream = new BinaryStream(data);
            if (descriptor) return stream.getNetworkItemStackDescriptor(version, instance);
            if (instance) return (Item) craftingSlotReader.invoke(stream, version, true);
            return stream.getSlot(version);
        }
    }

    private static final class Checks {
        final String name;
        final List<String> firstFailures = new ArrayList<>();
        int failures;
        int cases;
        int aliases;
        Checks(String name) { this.name = name; }
        void expect(boolean ok, String context) {
            if (!ok) { failures++; if (firstFailures.size() < 12) firstFailures.add(context); }
        }
        void finish() {
            System.out.println(name + ": cases=" + cases + " aliases=" + aliases + " mismatches=" + failures);
            assertEquals(0, failures, () -> String.join("\n", firstFailures));
        }
    }
}
