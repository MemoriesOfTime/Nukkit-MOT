package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

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
