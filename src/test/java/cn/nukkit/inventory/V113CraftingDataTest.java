package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.CraftingDataPacket;
import cn.nukkit.network.protocol.v113.ProtocolInfo_v113;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.1.5 (protocol 113) 完整配方包回归：独立解析生产包并断言恰好结束于 cleanRecipes。
 * 客户端对 id &lt; 1 只消费 id varint，负 id 的剩余物品栈字段会导致错位。
 * Independently parses protocol 113 packets through cleanRecipes using the client's item rules:
 * IDs below one consume only the ID varint, exposing negative-ID stream misalignment.
 */
class V113CraftingDataTest {

    private static final int CRAFTING_DATA_PACKET_ID = ProtocolInfo_v113.CRAFTING_DATA_PACKET;

    private static CraftingManager manager;

    @BeforeAll
    static void init() {
        MockServer.init();
        // protocol < 137 的 DataPacket#reset() 需要 v113 包池；只放行 CraftingDataPacket 的真实 id，
        // 其余包类回退 0x6a，下面的包 id 断言即可发现编码了错误的包类。
        // Below protocol 137 DataPacket#reset() needs a v113 pool; unmapped classes fall back to 0x6a.
        MockServer.installLegacyPacketPool(CraftingDataPacket.class, CRAFTING_DATA_PACKET_ID);
        manager = new CraftingManager();
        // packet113 是 static 缓存：同 JVM 里其它测试可能已构建过，不清掉会断言到别人的包。
        // packet113 is a static cache; rebuild so this test does not assert another manager's packet.
        manager.rebuildPacket();
    }

    @AfterAll
    static void cleanup() {
        // 包池桩挂在共享 mock Server 上，清掉避免泄漏给同一 fork 的后续测试类。
        // The stub lives on the shared mock Server; reset it for later test classes in the same fork.
        MockServer.reset();
    }

    @Test
    void v113ReceivesRecipeList() {
        BatchPacket packet = manager.getCachedPacket(GameVersion.V1_1_0);
        assertNotNull(packet, "1.1.5 客户端收不到 CraftingDataPacket，配方表为空");
    }

    @Test
    void v113PacketParsesLikeTheClient() throws Exception {
        BatchPacket packet = manager.getCachedPacket(GameVersion.V1_1_0);
        assertNotNull(packet);
        assertPacketParsesLikeTheClient(packet);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void negativeResultIdCannotCorruptPacketEvenWhenCountIsNonPositive(int count) throws Exception {
        Item result = Item.get(255 - BlockID.BAMBOO, 0, count);
        assertTrue(result.getId() < 0);
        assertFalse(result.isSupportedOn(GameVersion.V1_1_0), "负 id 不属于 1.1.5 物品表，应被过滤");
        ShapedRecipe recipe = new ShapedRecipe(result, new String[]{"L"},
                Map.of('L', Item.get(BlockID.LOG)), List.of());
        manager.registerRecipe(recipe);
        try {
            manager.rebuildPacket();
            // 配方被 isSupportedOn 过滤；即使漏过，putSlotV113 也会把负 id 写成空槽。
            // The recipe is filtered by isSupportedOn; if it slipped through, putSlotV113
            // would still write the negative id as an empty slot.
            assertPacketParsesLikeTheClient(manager.getCachedPacket(GameVersion.V1_1_0));
        } finally {
            manager.unregisterRecipe(recipe);
        }
    }

    private static void assertPacketParsesLikeTheClient(BatchPacket packet) throws Exception {
        byte[] body = Zlib.inflate(packet.payload, 64 * 1024 * 1024);
        int[] off = {0};
        int entryLength = readUnsignedVarInt(body, off);
        assertEquals(body.length - off[0], entryLength, "batch entry length 与包体不一致");
        assertEquals(CRAFTING_DATA_PACKET_ID, body[off[0]] & 0xff,
                "113 的 CraftingDataPacket id 应为 " + CRAFTING_DATA_PACKET_ID);
        off[0]++;

        int count = readUnsignedVarInt(body, off);
        assertTrue(count > 0, "配方列表不能为空");

        int shaped = 0;
        int shapeless = 0;
        boolean logToPlanks = false;
        // 1.1.x 的下发顺序：熔炉配方 → 有序变体 → 有序基础 → 无序变体 → 无序基础。
        // 交错会触发客户端打开背包崩溃，此断言锁定该顺序。
        // 1.1.x order is furnace entries, then shaped variants/basics, then shapeless variants/basics.
        // Interleaving crashes the client when opening the inventory, so lock the order here.
        boolean seenCraftingRecipe = false;
        boolean seenShapedBasicRecipe = false;
        boolean seenShapelessRecipe = false;
        boolean seenShapelessBasicRecipe = false;
        for (int i = 0; i < count; i++) {
            int type = readVarInt(body, off);
            boolean recipeVariant = false;
            switch (type) {
                case 0 -> {
                    shapeless++;
                    int ingredients = readUnsignedVarInt(body, off);
                    int[] ingredientIds = new int[ingredients];
                    for (int s = 0; s < ingredients; s++) {
                        int[] item = readItemWithCount(body, off);
                        ingredientIds[s] = item[0];
                        recipeVariant |= isAuxVariant(item[2]);
                    }
                    int results = readUnsignedVarInt(body, off);
                    int[] resultIds = new int[results];
                    int resultCount = 0;
                    for (int r = 0; r < results; r++) {
                        int[] item = readItemWithCount(body, off);
                        resultIds[r] = item[0];
                        resultCount = item[1];
                        recipeVariant |= isAuxVariant(item[2]);
                        // 1.1.5 渲染烟花结果时会读 Fireworks NBT，配方结果没有 NBT 会崩，必须过滤。
                        // 1.1.5 reads Fireworks NBT when rendering fireworks; recipe results have none.
                        assertFalse(resultIds[r] == 401 || resultIds[r] == 402,
                                "1.1.5 客户端无法安全渲染烟花配方结果: " + resultIds[r]);
                        // 1.1.5 用结果 damage 查药水表，查不到会空指针，药箭配方必须过滤。
                        // 1.1.5 resolves arrow results through its potion table; missing potions crash.
                        assertFalse(resultIds[r] == 262 && item[2] > 0,
                                "1.1.5 客户端无法安全渲染药箭配方结果: " + item[2]);
                    }
                    off[0] += 16;
                    if (ingredients == 1 && ingredientIds[0] == BlockID.LOG
                            && results == 1 && resultIds[0] == BlockID.PLANKS && resultCount == 4) {
                        logToPlanks = true;
                    }
                }
                case 1 -> {
                    shaped++;
                    int width = readVarInt(body, off);
                    int height = readVarInt(body, off);
                    assertTrue(width > 0 && width <= 3 && height > 0 && height <= 3,
                            "shaped 配方尺寸越界: " + width + "x" + height);
                    int[] ingredientIds = new int[width * height];
                    for (int s = 0; s < width * height; s++) {
                        int[] item = readItemWithCount(body, off);
                        ingredientIds[s] = item[0];
                        recipeVariant |= isAuxVariant(item[2]);
                    }
                    int results = readUnsignedVarInt(body, off);
                    int[] resultIds = new int[results];
                    int resultCount = 0;
                    for (int r = 0; r < results; r++) {
                        int[] item = readItemWithCount(body, off);
                        resultIds[r] = item[0];
                        resultCount = item[1];
                        recipeVariant |= isAuxVariant(item[2]);
                        // 1.1.5 渲染烟花结果时会读 Fireworks NBT，配方结果没有 NBT 会崩，必须过滤。
                        // 1.1.5 reads Fireworks NBT when rendering fireworks; recipe results have none.
                        assertFalse(resultIds[r] == 401 || resultIds[r] == 402,
                                "1.1.5 客户端无法安全渲染烟花配方结果: " + resultIds[r]);
                        // 1.1.5 用结果 damage 查药水表，查不到会空指针，药箭配方必须过滤。
                        // 1.1.5 resolves arrow results through its potion table; missing potions crash.
                        assertFalse(resultIds[r] == 262 && item[2] > 0,
                                "1.1.5 客户端无法安全渲染药箭配方结果: " + item[2]);
                    }
                    off[0] += 16;
                    // 1.1.5 里 原木 -> 4 木板 是 1x1 有序配方
                    if (width == 1 && height == 1 && ingredientIds[0] == BlockID.LOG
                            && results == 1 && resultIds[0] == BlockID.PLANKS && resultCount == 4) {
                        logToPlanks = true;
                    }
                }
                case 2 -> {
                    assertFalse(seenCraftingRecipe, "1.1.x 熔炉配方必须在合成配方之前下发");
                    readVarInt(body, off);
                    readItem(body, off);
                }
                case 3 -> {
                    assertFalse(seenCraftingRecipe, "1.1.x 熔炉配方必须在合成配方之前下发");
                    readVarInt(body, off);
                    readVarInt(body, off);
                    readItem(body, off);
                }
                default -> fail("客户端不认识的配方类型 " + type);
            }
            if (type <= 1) {
                seenCraftingRecipe = true;
                if (type == 1) {
                    assertFalse(seenShapelessRecipe, "1.1.x 必须先下发有序配方再下发无序配方");
                    if (recipeVariant) {
                        assertFalse(seenShapedBasicRecipe,
                                "1.1.x 有序配方必须变体在前、基础在后，交错会崩客户端");
                    } else {
                        seenShapedBasicRecipe = true;
                    }
                } else {
                    seenShapelessRecipe = true;
                    if (recipeVariant) {
                        assertFalse(seenShapelessBasicRecipe,
                                "1.1.x 无序配方必须变体在前、基础在后，交错会崩客户端");
                    } else {
                        seenShapelessBasicRecipe = true;
                    }
                }
            }
        }

        boolean cleanRecipes = body[off[0]] != 0;
        // 1.1.x 参考实现（PocketMine/Retro）发 false：保留客户端内置配方，清空后合成 UI 会崩。
        // 1.1.x reference servers send false so the client keeps its built-in recipes.
        assertFalse(cleanRecipes, "1.1.x 的 cleanRecipes 必须为 false");
        assertEquals(body.length, off[0] + 1,
                "包体未恰好结束于 cleanRecipes，说明存在负物品 id 等导致的流错位");
        assertTrue(shaped > 0, "应包含有序配方");
        assertTrue(shapeless > 0, "应包含无序配方");
        assertTrue(logToPlanks, "应包含 原木 -> 4 木板 的基础配方");
    }

    private static int readItem(byte[] buf, int[] off) {
        return readItemWithCount(buf, off)[0];
    }

    /**
     * 按 1.1.5 客户端规则读取一个物品栈，返回 {id, count, damage}；id &lt; 1 时只消费 id varint。
     * Reads one stack by 1.1.5 client rules; IDs below one consume only the ID varint.
     */
    private static int[] readItemWithCount(byte[] buf, int[] off) {
        int id = readVarInt(buf, off);
        if (id < 1) {
            return new int[]{id, 0, 0};
        }
        // 未知正 id 会让客户端读完 aux 就停，服务端却继续写 NBT：必须在编码前就过滤掉。
        // Unknown positive ids stop the client after aux while the server keeps writing NBT;
        // such ids must never reach the packet.
        assertTrue(isKnownTo113(id), "1.1.5 客户端不认识的配方物品 id: " + id);
        int aux = readVarInt(buf, off);
        int count = aux & 0xff;
        int damage = (aux >> 8) & 0x7fff;
        int nbtLength = (buf[off[0]] & 0xff) | ((buf[off[0] + 1] & 0xff) << 8);
        off[0] += 2 + nbtLength;
        int canPlaceOn = readVarInt(buf, off);
        for (int i = 0; i < canPlaceOn; i++) {
            off[0] += readUnsignedVarInt(buf, off);
        }
        int canDestroy = readVarInt(buf, off);
        for (int i = 0; i < canDestroy; i++) {
            off[0] += readUnsignedVarInt(buf, off);
        }
        return new int[]{id, count, damage};
    }

    private static boolean isAuxVariant(int damage) {
        return damage > 0 && damage != 0x7fff;
    }

    private static int readUnsignedVarInt(byte[] buf, int[] off) {
        int value = 0;
        int size = 0;
        while (true) {
            byte b = buf[off[0]++];
            value |= (b & 0x7f) << (size * 7);
            if ((b & 0x80) == 0) {
                return value;
            }
            size++;
        }
    }

    private static int readVarInt(byte[] buf, int[] off) {
        int raw = readUnsignedVarInt(buf, off);
        return (raw >>> 1) ^ -(raw & 1);
    }

    /**
     * 1.1.5 实际存在的 legacy id 段，与生产端 Item.isSupportedOn 的 1.1.x 门一致。
     * Legacy id ranges that exist in 1.1.5, mirroring Item.isSupportedOn's 1.1.x gate.
     */
    private static boolean isKnownTo113(int id) {
        if (id >= 1 && id <= 255) {
            return true;
        }
        if (id >= 256 && id <= 424) {
            return true;
        }
        if (id >= 426 && id <= 445) {
            return true;
        }
        if (id >= 447 && id <= 463) {
            return true;
        }
        return id == 466 || (id >= 500 && id <= 511);
    }
}
