package cn.nukkit.item.customitem;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemCategory;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 手持自定义物品右键方块（如末影箱）时，legacy use 事务会置 needSendInventory，
 * 下一 tick {@code sendAllInventories()} 全量重发玩家背包。本测试锁定该链路上的
 * 自定义物品 wire 保真度：
 * <ul>
 *   <li>已注册的自定义物品必须在所有现代协议上无损往返；</li>
 *   <li>无法在协议映射表中解析的物品（插件未注册/已卸载）必须降级为 INFO_UPDATE
 *       占位物品，而不是把整个 InventoryContentPacket 编码炸掉——后者会让玩家背包
 *       从此无法同步，表现为「打开容器时物品从快捷栏消失」。</li>
 * </ul>
 */
class CustomItemEnderChestResyncTest {

    private static final String NS = "testresync:case";
    private static final String GHOST_NS = "testresync:ghost";

    /** 正常注册的自定义物品（等价于插件 onEnable 时 Item.registerCustomItem） */
    private static final class CaseItem extends ItemCustom {
        CaseItem() {
            super(NS, "Case");
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition.simpleBuilder(this, CreativeItemCategory.ITEMS).build();
        }
    }

    /** 从未调用 registerCustomItem 的自定义物品：复现「映射缺失」的降级场景 */
    private static final class GhostItem extends ItemCustom {
        GhostItem() {
            super(GHOST_NS, "Ghost");
        }

        @Override
        public CustomItemDefinition getDefinition() {
            return CustomItemDefinition.simpleBuilder(this, CreativeItemCategory.ITEMS).build();
        }
    }

    @BeforeAll
    static void init() {
        MockServer.init();
        Item.initCreativeItems();
        Item.registerCustomItem(CaseItem.class);
    }

    @AfterAll
    static void cleanup() {
        Item.deleteCustomItem(NS);
    }

    // 1.16.100 存在独立的遗留占位行为（StringItem 走旧分支），不属于本回归范围
    private static final GameVersion[] VERSIONS = {
            GameVersion.V1_19_60,
            GameVersion.V1_21_0,
            GameVersion.V1_21_130,
            GameVersion.V1_26_30,
            GameVersion.V1_26_40,
    };

    private static Item newCaseItem() {
        Item item = Item.fromString(NS);
        item.setCustomName("Кейс Аксессуаров / Природа");
        item.setLore("Открыть кейс можно на спавне /warp case");
        return item;
    }

    /** 按 InventoryContentPacket.encode 的真实分发逻辑序列化一个槽位 */
    private static BinaryStream encodeSlot(GameVersion gv, Item item) {
        BinaryStream enc = new BinaryStream();
        if (gv.getProtocol() >= ProtocolInfo.v1_26_30) {
            enc.putNetworkItemStackDescriptor(gv, item);
        } else {
            enc.putSlot(gv, item);
        }
        return enc;
    }

    private static Item decodeSlot(GameVersion gv, BinaryStream enc) {
        BinaryStream dec = new BinaryStream();
        dec.setBuffer(enc.getBuffer(), 0);
        // 与包层分发一致（InventoryContentPacket.encode / InventoryTransactionPacket.decode）：
        // ≥1.26.30 双向都走 NetworkItemStackDescriptor 格式
        return gv.getProtocol() >= ProtocolInfo.v1_26_30
                ? dec.getNetworkItemStackDescriptor(gv)
                : dec.getSlot(gv);
    }

    @Test
    void registeredCustomItemSurvivesResyncRoundTripOnAllProtocols() {
        for (GameVersion gv : VERSIONS) {
            Item in = newCaseItem();
            BinaryStream enc = assertDoesNotThrow(() -> encodeSlot(gv, in), gv + ": encode");
            Item out = assertDoesNotThrow(() -> decodeSlot(gv, enc), gv + ": decode");
            assertFalse(out.isNull(), gv + ": registered custom item decoded to air");
            assertEquals(NS, out.getNamespaceId(), gv + ": namespace lost in round trip, got " + out);
            assertEquals("Кейс Аксессуаров / Природа", out.getCustomName(), gv + ": custom name lost");
        }
    }

    /**
     * 回归核心：映射缺失的物品绝不能让编码抛异常。
     * 修复前：≥1.26.30 走 putNetworkItemStackDescriptor 时 getNetworkId 直接抛
     * IllegalArgumentException，整个 InventoryContentPacket 编码失败，玩家背包无法重发。
     */
    @Test
    void unresolvableCustomItemDegradesInsteadOfKillingThePacket() {
        for (GameVersion gv : VERSIONS) {
            Item ghost = new GhostItem();
            ghost.setCustomName("Ghost");
            BinaryStream enc = assertDoesNotThrow(() -> encodeSlot(gv, ghost), gv + ": unresolvable item must not kill encode");
            assertTrue(enc.getBuffer().length > 0, gv + ": degraded item must still produce wire bytes");
        }
    }

    /** 降级后的 wire 形式必须是客户端可渲染的 INFO_UPDATE（id 248），且往返不抛异常 */
    @Test
    void degradedWireFormDecodesBackWithoutThrowing() {
        for (GameVersion gv : VERSIONS) {
            Item ghost = new GhostItem();
            ghost.setCustomName("Ghost");
            BinaryStream enc = encodeSlot(gv, ghost);
            // 未注册物品解码还原会得到 air（fromString 无 supplier），但过程必须可解码不抛异常
            assertDoesNotThrow(() -> decodeSlot(gv, enc), gv + ": degraded wire must decode");
        }
    }

    @Test
    void sendContentsSendsRegisteredCustomItemInWireForm() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_130;
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");

        PlayerInventory inventory = new PlayerInventory(player);
        inventory.setItemForce(3, newCaseItem());

        inventory.sendContents(player);

        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(player, Mockito.atLeastOnce()).dataPacket(captor.capture());
        InventoryContentPacket pk = null;
        for (DataPacket captured : captor.getAllValues()) {
            if (captured instanceof InventoryContentPacket) {
                pk = (InventoryContentPacket) captured;
            }
        }
        assertNotNull(pk, "sendContents must send an InventoryContentPacket");
        assertEquals(NS, pk.slots[3].getNamespaceId(), "server-side slot must still hold the custom item");

        pk.protocol = ProtocolInfo.v1_21_130;
        pk.gameVersion = GameVersion.V1_21_130;
        assertDoesNotThrow(pk::encode, "InventoryContentPacket with custom item must encode");
        assertTrue(pk.getBuffer().length > 0);
    }
}
