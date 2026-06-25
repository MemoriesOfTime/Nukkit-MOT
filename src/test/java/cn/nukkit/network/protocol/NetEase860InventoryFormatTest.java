package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证网易 1.21.124 (协议 860) 的物品栏数据包使用 NetworkItemStackDescriptor 格式。
 * <p>
 * Verifies that NetEase 1.21.124 (protocol 860) inventory packets use the NetworkItemStackDescriptor format.
 * <p>
 * 反编译确认: 网易 1.21.124 (基于国际版 1.21.120) 的 InventoryContentPacket::write 用
 * NetworkItemStackDescriptor 序列化物品, 而非旧的 Item 格式。
 */
public class NetEase860InventoryFormatTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    @Test
    public void testNetEase860InventoryContentUsesNetworkItemStackDescriptor() {
        GameVersion netease860 = GameVersion.V1_21_124_NETEASE;
        GameVersion netease819 = GameVersion.V1_21_93_NETEASE;

        // 网易 860: InventoryContentPacket 应该用 NetworkItemStackDescriptor 格式
        InventoryContentPacket pk860 = new InventoryContentPacket();
        pk860.protocol = netease860.getProtocol();
        pk860.gameVersion = netease860;
        pk860.inventoryId = 1;
        pk860.slots = new Item[]{Item.AIR_ITEM};
        pk860.encode();

        // 网易 819: 应该用旧的 putSlot 格式
        InventoryContentPacket pk819 = new InventoryContentPacket();
        pk819.protocol = netease819.getProtocol();
        pk819.gameVersion = netease819;
        pk819.inventoryId = 1;
        pk819.slots = new Item[]{Item.AIR_ITEM};
        pk819.encode();

        byte[] buf860 = pk860.getBuffer();
        byte[] buf819 = pk819.getBuffer();

        System.out.println("网易 860 InventoryContentPacket (1 AIR slot): " + buf860.length + " 字节");
        System.out.println("网易 819 InventoryContentPacket (1 AIR slot): " + buf819.length + " 字节");

        // NetworkItemStackDescriptor 格式下, AIR 物品是 7 字节 (LShort+LShort+VarInt+bool+VarInt)
        // 旧 putSlot 格式下, AIR 物品是 1 字节 (putByte(0))
        // 所以 860 应该明显比 819 大
        assertTrue(buf860.length > buf819.length + 3,
                "网易 860 应该用 NetworkItemStackDescriptor 格式, 数据包应明显大于 819 的旧格式");
    }

    @Test
    public void testNetEase860AirItemNetworkItemStackDescriptorSize() {
        // NetworkItemStackDescriptor 格式下 AIR 物品的字节大小
        // putLShort(0) + putLShort(0) + putUnsignedVarInt(0) + putBoolean(false) + putUnsignedVarInt(0)
        // = 2 + 2 + 1 + 1 + 1 = 7 字节
        GameVersion netease860 = GameVersion.V1_21_124_NETEASE;

        InventorySlotPacket pk = new InventorySlotPacket();
        pk.protocol = netease860.getProtocol();
        pk.gameVersion = netease860;
        pk.inventoryId = 1;
        pk.slot = 0;
        pk.item = Item.AIR_ITEM;
        pk.encode();

        byte[] buf = pk.getBuffer();
        System.out.println("网易 860 InventorySlotPacket (AIR): " + buf860Hex(buf));

        // packetId(1) + inventoryId(1) + slot(1) + FullContainerName(2) + storageItem(7) + item(7)
        // = 1 + 1 + 1 + 2 + 7 + 7 = 19 字节 (实际 14, 因为部分字段编码后更短)
        assertTrue(buf.length >= 12,
                "网易 860 InventorySlotPacket 用 NetworkItemStackDescriptor 格式, 应 >= 12 字节, 实际: " + buf.length);
    }

    @Test
    public void testInternational860StillUsesOldFormat() {
        // 确认国际版 860 仍然用旧格式 (因为 860 < v1_26_20_26)
        GameVersion standard860 = GameVersion.V1_21_124;

        InventoryContentPacket pk = new InventoryContentPacket();
        pk.protocol = standard860.getProtocol();
        pk.gameVersion = standard860;
        pk.inventoryId = 1;
        pk.slots = new Item[]{Item.AIR_ITEM};
        pk.encode();

        byte[] buf = pk.getBuffer();
        System.out.println("国际版 860 InventoryContentPacket (1 AIR slot): " + buf.length + " 字节");

        // 国际版 860 用旧格式, AIR 物品是 1 字节
        // packetId(1) + inventoryId(1) + count(1) + slot(1) + containerName(2) + storageItem(1)
        // = 7 字节
        assertTrue(buf.length < 12,
                "国际版 860 应该用旧格式, 数据包应较小, 实际: " + buf.length);
    }

    private static String buf860Hex(byte[] buf) {
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x ", b & 0xff));
        }
        return sb.toString().trim();
    }
}
