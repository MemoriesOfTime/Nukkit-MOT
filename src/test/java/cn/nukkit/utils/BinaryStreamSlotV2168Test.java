package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.network.protocol.ProtocolInfo;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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
class BinaryStreamSlotV2168Test {

    private static final GameVersion V2168 = GameVersion.byProtocol(ProtocolInfo.v1_26_40, false);

    private static int diamondSwordRuntimeId;

    @BeforeAll
    static void init() {
        MockServer.init();
        diamondSwordRuntimeId = RuntimeItems.getMapping(V2168)
                .toRuntime(Item.DIAMOND_SWORD, 0)
                .getRuntimeId();
    }

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
}
