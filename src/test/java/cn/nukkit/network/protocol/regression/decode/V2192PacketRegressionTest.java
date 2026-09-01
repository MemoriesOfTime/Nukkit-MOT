package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.BossEventPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.MoveEntityDeltaPacket;
import cn.nukkit.network.protocol.PlaySoundPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.RecordStartedPacket;
import cn.nukkit.network.protocol.SetPlayerFurnaceOptionsPacket;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponse;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2192 (1.26.50) 线格式变更的 CB 交叉验证：
 * 单 bool 化（PlayerAuthInput 可选段 / InventorySource / ItemStackResponse）、
 * 新增尾部字段（MoveEntityDelta ticks、PlaySound bypass/playbackPos）、
 * BossEvent 移除 playerEid，以及两个新包 351/352。
 * <p>
 * CB cross-validation for v2192 (1.26.50) wire changes: single-bool optional
 * sections (PlayerAuthInput / InventorySource / ItemStackResponse), trailing
 * fields (MoveEntityDelta ticks, PlaySound bypass/playbackPos), BossEvent
 * dropping playerEid, and the two new packets 351/352.
 */
public class V2192PacketRegressionTest extends AbstractPacketRegressionTest {

    @org.junit.jupiter.api.BeforeAll
    static void setUp() {
        cn.nukkit.MockServer.init();
    }

    private static final int V2192 = cn.nukkit.network.protocol.ProtocolInfo.v1_26_50;

    // ==================== PlayerAuthInputPacket：可选段单 bool 化 ====================

    /**
     * v2192 起各可选段仅一个存在性 bool（v2168~v2169 为恒 true 外层 + 内层双 bool），
     * inputData 列表前不再有外层 bool。
     */
    @ParameterizedTest(name = "PlayerAuthInputPacket v2192 optional sections v{0}")
    @ValueSource(ints = {V2192})
    void playerAuthInputV2192OptionalSections(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket();
        cb.setRotation(org.cloudburstmc.math.vector.Vector3f.from(10.5f, 20.5f, 30.5f));
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(1.25f, 64.0f, -3.5f));
        cb.setMotion(org.cloudburstmc.math.vector.Vector2f.from(0.25f, -0.5f));
        cb.getInputData().add(org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData.UP);
        cb.getInputData().add(org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
        cb.setInputMode(org.cloudburstmc.protocol.bedrock.data.InputMode.MOUSE);
        cb.setInputInteractionModel(org.cloudburstmc.protocol.bedrock.data.InputInteractionModel.CROSSHAIR);
        cb.setPlayMode(org.cloudburstmc.protocol.bedrock.data.ClientPlayMode.NORMAL);
        cb.setInteractRotation(org.cloudburstmc.math.vector.Vector2f.from(0f, 0f));
        cb.setAnalogMoveVector(org.cloudburstmc.math.vector.Vector2f.from(0f, 0f));
        cb.setCameraOrientation(org.cloudburstmc.math.vector.Vector3f.from(0f, 0f, 0f));
        cb.setRawMoveVector(org.cloudburstmc.math.vector.Vector2f.from(0f, 0f));
        cb.setTick(1234L);
        cb.setDelta(org.cloudburstmc.math.vector.Vector3f.from(0.1f, -0.2f, 0.3f));
        // 无 itemUseTransaction / itemStackRequest / vehicle 段（各写单个 false bool）

        PlayerAuthInputPacket nk = crossEncode(cb, PlayerAuthInputPacket::new, protocol);

        assertTrue(nk.getInputData().contains(cn.nukkit.network.protocol.types.AuthInputAction.UP));
        assertTrue(nk.getInputData().contains(cn.nukkit.network.protocol.types.AuthInputAction.PERFORM_BLOCK_ACTIONS));
        assertEquals(1234L, nk.getTick());
        assertEquals(0, nk.getBlockActionData().size(), "no block actions payload expected");
    }

    // ==================== InventoryTransactionPacket：InventorySource 单 bool ====================

    @Test
    void inventoryTransactionV2192SingleBoolSource() {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket();
        cb.setLegacyRequestId(-1);
        cb.setTransactionType(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.NORMAL);
        cb.getActions().add(new org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData(
                org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource.fromContainerWindowId(12),
                3, org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR, org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR));

        InventoryTransactionPacket nk = crossEncode(cb, InventoryTransactionPacket::new, V2192);

        assertEquals(1, nk.actions.length);
        assertEquals(cn.nukkit.network.protocol.types.NetworkInventoryAction.SOURCE_CONTAINER, nk.actions[0].sourceType);
        assertEquals(12, nk.actions[0].windowId);
        assertEquals(3, nk.actions[0].inventorySlot);
    }

    // ==================== ItemStackResponsePacket：容器/stackNetworkId 单 bool ====================

    @Test
    void itemStackResponseV2192SingleBoolContainers() {
        ItemStackResponsePacket nk = new ItemStackResponsePacket();
        nk.protocol = V2192;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2192, false);
        nk.entries.add(new ItemStackResponse(
                cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus.OK,
                77,
                List.of(new ItemStackResponseContainer(
                        cn.nukkit.network.protocol.types.inventory.ContainerSlotType.LEVEL_ENTITY,
                        List.of(new ItemStackResponseSlot(1, 2, 5, 99, "custom", 0, null)),
                        new FullContainerName(cn.nukkit.network.protocol.types.inventory.ContainerSlotType.LEVEL_ENTITY, null)))
        ));

        nk.encode();
        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket.class);

        assertEquals(1, cb.getEntries().size());
        var entry = cb.getEntries().get(0);
        assertEquals(77, entry.getRequestId());
        assertEquals(1, entry.getContainers().size());
        var slot = entry.getContainers().get(0).getItems().get(0);
        assertEquals(1, slot.getSlot());
        assertEquals(2, slot.getHotbarSlot());
        assertEquals(5, slot.getCount());
        assertEquals(99, slot.getStackNetworkId());
        assertEquals("custom", slot.getCustomName());
    }

    @Test
    void itemStackResponseV2192EmptyContainers() {
        ItemStackResponsePacket nk = new ItemStackResponsePacket();
        nk.protocol = V2192;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2192, false);
        nk.entries.add(new ItemStackResponse(
                cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus.ERROR,
                78,
                List.of()));

        nk.encode();
        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket.class);

        assertEquals(1, cb.getEntries().size());
        assertTrue(cb.getEntries().get(0).getContainers().isEmpty());
    }

    // ==================== BossEventPacket：移除 playerEid ====================

    @Test
    void bossEventV2192DropsPlayerEid() {
        BossEventPacket nk = new BossEventPacket();
        nk.protocol = V2192;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2192, false);
        nk.bossEid = 12345L;
        nk.playerEid = 999L; // v2192 下应被忽略 / ignored on v2192
        nk.type = BossEventPacket.TYPE_SHOW;
        nk.title = "Boss Title";
        nk.filteredTitle = "Filtered";
        nk.healthPercent = 0.75f;
        nk.color = 1;
        nk.overlay = 2;

        nk.encode();
        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.class);

        assertEquals(12345L, cb.getBossUniqueEntityId());
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action.CREATE, cb.getAction());
        assertEquals("Boss Title", cb.getTitle().toString());
        assertEquals("Filtered", cb.getFilteredTitle().toString());
    }

    // ==================== MoveEntityDeltaPacket：尾部 ticks ====================

    @Test
    void moveEntityDeltaV2192TrailingTicks() {
        MoveEntityDeltaPacket nk = new MoveEntityDeltaPacket();
        nk.protocol = V2192;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2192, false);
        nk.eid = 42L;
        nk.flags = MoveEntityDeltaPacket.FLAG_HAS_X | MoveEntityDeltaPacket.FLAG_HAS_Y;
        nk.x = 1.5f;
        nk.y = 64f;
        nk.onGround = true;

        nk.encode();
        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket.class);

        assertEquals(42L, cb.getRuntimeEntityId());
        assertEquals(1.5f, cb.getX());
        assertEquals(64f, cb.getY());
        assertEquals(0f, cb.getZ());
        assertTrue(cb.isOnGround());
        assertEquals(0L, cb.getTicks());
    }

    // ==================== PlaySoundPacket：bypass + playbackPositionSeconds ====================

    @Test
    void playSoundV2192NewTrailingFields() {
        PlaySoundPacket nk = new PlaySoundPacket();
        nk.protocol = V2192;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2192, false);
        nk.name = "mob.pig.say";
        nk.x = 10;
        nk.y = 64;
        nk.z = -20;
        nk.volume = 1f;
        nk.pitch = 0.5f;
        nk.loopCount = 3;
        nk.bypassListenerRangeCheck = true;
        nk.serverSoundHandle = 0xAABBCCDDL;
        nk.playbackPositionSeconds = 1.25f;

        nk.encode();
        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket.class);

        assertEquals("mob.pig.say", cb.getSound());
        assertEquals(3, cb.getLoopCount());
        assertTrue(cb.isBypassListenerRangeCheck());
        assertEquals(0xAABBCCDDL, cb.getServerSoundHandle());
        assertEquals(1.25f, cb.getPlaybackPositionSeconds());
    }

    // ==================== RecordStartedPacket（新包 352，S→C） ====================

    @Test
    void recordStartedPacketRoundTrip() {
        RecordStartedPacket nk = new RecordStartedPacket();
        nk.protocol = V2192;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2192, false);
        nk.blockPos = new BlockVector3(10, 64, -20);
        nk.serverSoundHandle = 0x1122334455667788L;

        nk.encode();
        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.RecordStartedPacket.class);

        assertEquals(org.cloudburstmc.math.vector.Vector3i.from(10, 64, -20), cb.getBlockPos());
        assertEquals(0x1122334455667788L, cb.getServerSoundHandle());
    }

    // ==================== SetPlayerFurnaceOptionsPacket（新包 351，C→S） ====================

    @Test
    void setPlayerFurnaceOptionsDecode() {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetPlayerFurnaceOptionsPacket();
        cb.setType(org.cloudburstmc.protocol.bedrock.packet.SetPlayerFurnaceOptionsPacket.FurnaceType.BLAST_FURNACE);
        cb.setOptions(new org.cloudburstmc.protocol.bedrock.data.FurnaceOptions(
                org.cloudburstmc.protocol.bedrock.data.FurnaceOptions.FurnaceLeftTabIndex.RECIPE_FOOD,
                true,
                org.cloudburstmc.protocol.bedrock.data.FurnaceOptions.FurnaceLayout.INVENTORY_ONLY));

        SetPlayerFurnaceOptionsPacket nk = crossEncode(cb, SetPlayerFurnaceOptionsPacket::new, V2192);

        assertEquals(SetPlayerFurnaceOptionsPacket.FurnaceType.BLAST_FURNACE, nk.type);
        assertEquals(SetPlayerFurnaceOptionsPacket.FurnaceLeftTabIndex.RECIPE_FOOD, nk.leftTabIndex);
        assertTrue(nk.filtering);
        assertEquals(SetPlayerFurnaceOptionsPacket.FurnaceLayout.INVENTORY_ONLY, nk.layout);
    }
}
