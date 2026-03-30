package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.MockServer;
import cn.nukkit.inventory.transaction.data.ReleaseItemData;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.protocol.types.inventory.InventoryLayout;
import cn.nukkit.network.protocol.types.inventory.InventoryTabLeft;
import cn.nukkit.network.protocol.types.inventory.InventoryTabRight;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.AdventureSetting;
import org.cloudburstmc.protocol.bedrock.data.CommandBlockMode;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.ResourcePackType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decode regression tests for miscellaneous bidirectional packets.
 * CB Protocol encodes, Nukkit-MOT decodes.
 */
public class MiscDecodeRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    static Stream<Arguments> versionsPreV594() {
        return filteredVersionsRange(291, ProtocolInfo.v1_20_10);
    }

    static Stream<Arguments> versionsFrom407() {
        return filteredVersions(ProtocolInfo.v1_16_0);
    }

    static Stream<Arguments> versionsFrom630() {
        return filteredVersions(ProtocolInfo.v1_20_50);
    }

    static Stream<Arguments> versionsFrom313() {
        return filteredVersions(313);
    }

    static Stream<Arguments> versionsFrom332() {
        return filteredVersions(332);
    }

    static Stream<Arguments> versionsFrom388() {
        return filteredVersions(388);
    }

    static Stream<Arguments> versionsFrom388ToV685() {
        return filteredVersionsRange(388, ProtocolInfo.v1_21_0);
    }

    static Stream<Arguments> versionsV428ToV671() {
        return filteredVersionsRange(ProtocolInfo.v1_16_210, ProtocolInfo.v1_20_80);
    }

    static Stream<Arguments> versionsFrom712() {
        return filteredVersions(712);
    }

    static Stream<Arguments> versionsFrom465() {
        return filteredVersions(465);
    }

    static Stream<Arguments> versionsFrom800() {
        return filteredVersions(ProtocolInfo.v1_21_80);
    }

    static Stream<Arguments> versionsFrom503() {
        return filteredVersions(ProtocolInfo.v1_18_30);
    }

    static Stream<Arguments> versionsFrom527() {
        return filteredVersions(ProtocolInfo.v1_19_0);
    }

    static Stream<Arguments> versionsFrom560() {
        return filteredVersions(ProtocolInfo.v1_19_50);
    }

    static Stream<Arguments> versionsFrom407PreV844() {
        return filteredVersionsRange(ProtocolInfo.v1_16_0, ProtocolInfo.v1_21_110);
    }

    static Stream<Arguments> versionsFrom486() {
        return filteredVersions(ProtocolInfo.v1_18_10);
    }

    static Stream<Arguments> versionsFrom575() {
        return filteredVersions(ProtocolInfo.v1_19_70);
    }

    static Stream<Arguments> versionsFrom594() {
        return filteredVersions(ProtocolInfo.v1_20_10);
    }

    static Stream<Arguments> versionsFrom554() {
        return filteredVersions(ProtocolInfo.v1_19_30);
    }

    static Stream<Arguments> versionsFrom818() {
        return filteredVersions(818);
    }

    static Stream<Arguments> versionsFrom898() {
        return filteredVersions(ProtocolInfo.v1_21_130_28);
    }

    static Stream<Arguments> versionsFrom431() {
        return filteredVersions(ProtocolInfo.v1_16_220);
    }

    static Stream<Arguments> versionsFrom428to748() {
        return filteredVersionsRange(ProtocolInfo.v1_16_210, ProtocolInfo.v1_21_40);
    }

    static Stream<Arguments> versionsV766ToV800() {
        return filteredVersionsRange(ProtocolInfo.v1_21_50, ProtocolInfo.v1_21_80);
    }

    // ==================== NetworkStackLatencyPacket ====================

    @ParameterizedTest(name = "NetworkStackLatencyPacket pre-v332 v{0}")
    @MethodSource("allVersions")
    void networkStackLatency(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket();
        cb.setTimestamp(123456789L);
        cb.setFromServer(true);

        NetworkStackLatencyPacket nk = crossEncode(cb, NetworkStackLatencyPacket::new, protocol);

        assertEquals(123456789L, nk.timestamp);
        // fromServer field only present from protocol 332 onward
        if (protocol >= 332) {
            assertTrue(nk.fromServer);
        }
    }

    // ==================== TickSyncPacket ====================

    @ParameterizedTest(name = "TickSyncPacket v{0}")
    @MethodSource("versionsFrom388ToV685")
    void tickSync(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.TickSyncPacket();
        cb.setRequestTimestamp(100L);
        cb.setResponseTimestamp(200L);

        TickSyncPacket nk = crossEncode(cb, TickSyncPacket::new, protocol);

        assertEquals(100L, nk.getRequestTimestamp());
        assertEquals(200L, nk.getResponseTimestamp());
    }

    // ==================== FilterTextPacket ====================

    @ParameterizedTest(name = "FilterTextPacket v{0}")
    @MethodSource("versionsV428ToV671")
    void filterText(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.FilterTextPacket();
        cb.setText("hello world");
        cb.setFromServer(false);

        FilterTextPacket nk = crossEncode(cb, FilterTextPacket::new, protocol);

        assertEquals("hello world", nk.text);
        assertFalse(nk.fromServer);
    }

    static Stream<Arguments> versionsPreV898() {
        return filteredVersionsRange(291, ProtocolInfo.v1_21_130_28);
    }

    // ==================== AnimatePacket ====================

    @ParameterizedTest(name = "AnimatePacket v{0}")
    @MethodSource("allVersions")
    void animateSwingArm(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AnimatePacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action.SWING_ARM);
        cb.setRuntimeEntityId(77L);
        cb.setData(0.5f); // written for v859+, ignored by older serializers

        AnimatePacket nk = crossEncode(cb, AnimatePacket::new, protocol);

        assertEquals(AnimatePacket.Action.SWING_ARM, nk.action);
        assertEquals(77L, nk.eid);
        if (protocol >= ProtocolInfo.v1_21_120) { // v859
            assertEquals(0.5f, nk.data, 0.001f);
        }
    }

    @ParameterizedTest(name = "AnimatePacket ROW_RIGHT v{0}")
    @MethodSource("versionsPreV898")
    void animateRowRight(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AnimatePacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action.ROW_RIGHT);
        cb.setRuntimeEntityId(55L);
        cb.setRowingTime(3.0f);
        cb.setData(0.0f);

        AnimatePacket nk = crossEncode(cb, AnimatePacket::new, protocol);

        assertEquals(AnimatePacket.Action.ROW_RIGHT, nk.action);
        assertEquals(55L, nk.eid);
        assertEquals(3.0f, nk.rowingTime, 0.001f);
    }

    // ==================== EmotePacket ====================

    @ParameterizedTest(name = "EmotePacket v{0}")
    @MethodSource("versionsFrom388")
    void emotePacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.EmotePacket();
        cb.setRuntimeEntityId(55L);
        cb.setEmoteId("emote-abc-123");
        cb.setXuid("xuid12345");
        cb.setPlatformId("Windows");
        cb.setEmoteDuration(20);
        // flags left empty → byte 0

        EmotePacket nk = crossEncode(cb, EmotePacket::new, protocol);

        assertEquals(55L, nk.runtimeId);
        assertEquals("emote-abc-123", nk.emoteID);
        assertEquals(0, nk.flags);
        if (protocol >= 589) { // v1_20_0 (CB EmoteSerializer_v589 adds xuid/platformId)
            assertEquals("xuid12345", nk.xuid);
            assertEquals("Windows", nk.platformId);
        }
        if (protocol >= ProtocolInfo.v1_21_30) { // v729
            assertEquals(20L, nk.emoteDuration);
        }
    }

    // ==================== ResourcePackChunkRequestPacket ====================

    @ParameterizedTest(name = "ResourcePackChunkRequestPacket v{0}")
    @MethodSource("allVersions")
    void resourcePackChunkRequest(int protocol) {
        UUID packId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket();
        cb.setPackId(packId);
        // packVersion intentionally null so CB writes just "uuid" (no _version suffix)
        cb.setChunkIndex(3);

        ResourcePackChunkRequestPacket nk = crossEncode(cb, ResourcePackChunkRequestPacket::new, protocol);

        assertEquals(packId, nk.packId);
        assertEquals(3, nk.chunkIndex);
    }

    // ==================== EntityEventPacket ====================

    @ParameterizedTest(name = "EntityEventPacket v{0}")
    @MethodSource("allVersions")
    void entityEvent(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket();
        cb.setRuntimeEntityId(88L);
        cb.setType(EntityEventType.HURT);  // ID = 2 in typeMap
        cb.setData(5);

        EntityEventPacket nk = crossEncode(cb, EntityEventPacket::new, protocol);

        assertEquals(88L, nk.eid);
        assertEquals(2, nk.event); // HURT maps to ID 2
        assertEquals(5, nk.data);
    }

    // ==================== MobEquipmentPacket ====================

    @ParameterizedTest(name = "MobEquipmentPacket v{0}")
    @MethodSource("allVersions")
    void mobEquipment(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket();
        cb.setRuntimeEntityId(42L);
        cb.setItem(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.setInventorySlot(1);
        cb.setHotbarSlot(2);
        cb.setContainerId(3);

        MobEquipmentPacket nk = crossEncode(cb, MobEquipmentPacket::new, protocol);

        assertEquals(42L, nk.eid);
        assertNotNull(nk.item);
        assertTrue(nk.item.isNull());
        assertEquals(1, nk.inventorySlot);
        assertEquals(2, nk.hotbarSlot);
        assertEquals(3, nk.windowId);
    }

    // ==================== MobArmorEquipmentPacket ====================

    @ParameterizedTest(name = "MobArmorEquipmentPacket v{0}")
    @MethodSource("allVersions")
    void mobArmorEquipment(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket();
        cb.setRuntimeEntityId(77L);
        cb.setHelmet(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.setChestplate(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.setLeggings(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.setBoots(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        if (protocol >= ProtocolInfo.v1_21_20) {
            cb.setBody(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        }

        MobArmorEquipmentPacket nk = crossEncode(cb, MobArmorEquipmentPacket::new, protocol);

        assertEquals(77L, nk.eid);
        assertEquals(4, nk.slots.length);
        assertTrue(java.util.Arrays.stream(nk.slots).allMatch(item -> item != null && item.isNull()));
        if (protocol >= ProtocolInfo.v1_21_20) {
            assertTrue(nk.body.isNull());
        }
    }

    // ==================== PlayerEnchantOptionsPacket ====================

    @ParameterizedTest(name = "PlayerEnchantOptionsPacket v{0}")
    @MethodSource("versionsFrom407")
    void playerEnchantOptions(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket();
        cb.getOptions().add(new org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData(
                5,
                2,
                java.util.List.of(new org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData(1, 3)),
                java.util.List.of(new org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData(2, 4)),
                java.util.List.of(),
                "sharpness",
                99
        ));

        PlayerEnchantOptionsPacket nk = crossEncode(cb, PlayerEnchantOptionsPacket::new, protocol);

        assertEquals(1, nk.options.size());
        PlayerEnchantOptionsPacket.EnchantOptionData option = nk.options.get(0);
        assertEquals(5, option.getMinLevel());
        assertEquals(Integer.reverseBytes(2), option.getPrimarySlot());
        assertEquals(1, option.getEnchants0().size());
        assertEquals(1, option.getEnchants1().size());
        assertEquals("sharpness", option.getEnchantName());
        assertEquals(99, option.getEnchantNetId());
    }

    // ==================== ScriptCustomEventPacket ====================

    @ParameterizedTest(name = "ScriptCustomEventPacket v{0}")
    @MethodSource("versionsPreV594")
    void scriptCustomEvent(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ScriptCustomEventPacket();
        cb.setEventName("testEvent");
        cb.setData("eventPayload");

        ScriptCustomEventPacket nk = crossEncode(cb, ScriptCustomEventPacket::new, protocol);

        assertEquals("testEvent", nk.eventName);
        assertArrayEquals("eventPayload".getBytes(StandardCharsets.UTF_8), nk.eventData);
    }

    // ==================== DebugInfoPacket ====================

    @ParameterizedTest(name = "DebugInfoPacket v{0}")
    @MethodSource("versionsFrom407")
    void debugInfo(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.DebugInfoPacket();
        cb.setUniqueEntityId(42L);
        cb.setData("debug string");

        DebugInfoPacket nk = crossEncode(cb, DebugInfoPacket::new, protocol);

        assertEquals(42L, nk.entityId);
        assertEquals("debug string", nk.data);
    }

    // ==================== ResourcePackClientResponsePacket ====================

    @ParameterizedTest(name = "ResourcePackClientResponsePacket v{0}")
    @MethodSource("allVersions")
    void resourcePackClientResponse(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket();
        cb.setStatus(org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS);
        // empty pack list → count = 0

        ResourcePackClientResponsePacket nk = crossEncode(cb, ResourcePackClientResponsePacket::new, protocol);

        // HAVE_ALL_PACKS ordinal = 2 (NONE=0, REFUSED=1, HAVE_ALL_PACKS=2, COMPLETED=3)
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS.ordinal(),
                nk.responseStatus);
        assertEquals(0, nk.packEntries.length);
    }

    // ==================== PacketViolationWarningPacket ====================

    @ParameterizedTest(name = "PacketViolationWarningPacket v{0}")
    @MethodSource("versionsFrom407")
    void packetViolationWarning(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket();
        cb.setType(org.cloudburstmc.protocol.bedrock.data.PacketViolationType.MALFORMED_PACKET);
        cb.setSeverity(org.cloudburstmc.protocol.bedrock.data.PacketViolationSeverity.WARNING);
        cb.setPacketCauseId(55);
        cb.setContext("test context");

        PacketViolationWarningPacket nk = crossEncode(cb, PacketViolationWarningPacket::new, protocol);

        assertEquals(PacketViolationWarningPacket.PacketViolationType.MALFORMED_PACKET, nk.type);
        assertEquals(PacketViolationWarningPacket.PacketViolationSeverity.WARNING, nk.severity);
        assertEquals(55, nk.packetId);
        assertEquals("test context", nk.context);
    }

    // ==================== EmoteListPacket ====================

    @ParameterizedTest(name = "EmoteListPacket v{0}")
    @MethodSource("versionsFrom407")
    void emoteList(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.EmoteListPacket();
        cb.setRuntimeEntityId(33L);
        // empty pieceIds list → count = 0

        EmoteListPacket nk = crossEncode(cb, EmoteListPacket::new, protocol);

        assertEquals(33L, nk.runtimeId);
        assertEquals(0, nk.pieceIds.size());
    }

    // ==================== ToggleCrafterSlotRequestPacket ====================

    @ParameterizedTest(name = "ToggleCrafterSlotRequestPacket v{0}")
    @MethodSource("versionsFrom630")
    void toggleCrafterSlotRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ToggleCrafterSlotRequestPacket();
        cb.setBlockPosition(Vector3i.from(5, 60, -3));
        cb.setSlot((byte) 4);
        cb.setDisabled(true);

        ToggleCrafterSlotRequestPacket nk = crossEncode(cb, ToggleCrafterSlotRequestPacket::new, protocol);

        assertEquals(5.0f, nk.blockPosition.x, 0.001f);
        assertEquals(60.0f, nk.blockPosition.y, 0.001f);
        assertEquals(-3.0f, nk.blockPosition.z, 0.001f);
        assertEquals(4, nk.slot);
        assertTrue(nk.disabled);
    }

    // ==================== SetPlayerInventoryOptionsPacket ====================

    @ParameterizedTest(name = "SetPlayerInventoryOptionsPacket v{0}")
    @MethodSource("versionsFrom630")
    void setPlayerInventoryOptions(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket();
        cb.setLeftTab(org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabLeft.SURVIVAL);
        cb.setRightTab(org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabRight.CRAFTING);
        cb.setFiltering(true);
        cb.setLayout(org.cloudburstmc.protocol.bedrock.data.inventory.InventoryLayout.DEFAULT);
        cb.setCraftingLayout(org.cloudburstmc.protocol.bedrock.data.inventory.InventoryLayout.NONE);

        SetPlayerInventoryOptionsPacket nk = crossEncode(cb, SetPlayerInventoryOptionsPacket::new, protocol);

        // CB ordinals match NK ordinals: SURVIVAL=6, CRAFTING=2, DEFAULT=2(NK:RECIPE_BOOK), NONE=0
        assertEquals(InventoryTabLeft.SURVIVAL, nk.leftTab);
        assertEquals(InventoryTabRight.CRAFTING, nk.rightTab);
        assertTrue(nk.filtering);
        assertEquals(InventoryLayout.RECIPE_BOOK, nk.layout);
        assertEquals(InventoryLayout.NONE, nk.craftingLayout);
    }

    // ==================== ServerboundDiagnosticsPacket ====================

    @ParameterizedTest(name = "ServerboundDiagnosticsPacket v{0}")
    @MethodSource("versionsFrom712")
    void serverboundDiagnostics(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ServerboundDiagnosticsPacket();
        cb.setAvgFps(60.0f);
        cb.setAvgServerSimTickTimeMS(1.5f);
        cb.setAvgClientSimTickTimeMS(2.0f);
        cb.setAvgBeginFrameTimeMS(0.1f);
        cb.setAvgInputTimeMS(0.2f);
        cb.setAvgRenderTimeMS(10.0f);
        cb.setAvgEndFrameTimeMS(0.3f);
        cb.setAvgRemainderTimePercent(80.0f);
        cb.setAvgUnaccountedTimePercent(5.0f);

        ServerboundDiagnosticsPacket nk = crossEncode(cb, ServerboundDiagnosticsPacket::new, protocol);

        assertEquals(60.0f, nk.avgFps, 0.001f);
        assertEquals(1.5f, nk.avgServerSimTickTimeMS, 0.001f);
        assertEquals(2.0f, nk.avgClientSimTickTimeMS, 0.001f);
        assertEquals(0.1f, nk.avgBeginFrameTimeMS, 0.001f);
        assertEquals(0.2f, nk.avgInputTimeMS, 0.001f);
        assertEquals(10.0f, nk.avgRenderTimeMS, 0.001f);
        assertEquals(0.3f, nk.avgEndFrameTimeMS, 0.001f);
        assertEquals(80.0f, nk.avgRemainderTimePercent, 0.001f);
        assertEquals(5.0f, nk.avgUnaccountedTimePercent, 0.001f);
    }

    // ==================== CommandBlockUpdatePacket ====================

    @ParameterizedTest(name = "CommandBlockUpdatePacket (block) v{0}")
    @MethodSource("allVersions")
    void commandBlockUpdate(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CommandBlockUpdatePacket();
        cb.setBlock(true);
        cb.setBlockPosition(Vector3i.from(10, 64, -5));
        cb.setMode(CommandBlockMode.REPEATING); // ordinal 1
        cb.setRedstoneMode(false);
        cb.setConditional(true);
        cb.setCommand("/say hello");
        cb.setLastOutput("");
        cb.setName("cmd1");
        cb.setFilteredName("cmd1");
        cb.setOutputTracked(true);
        cb.setTickDelay(5);
        cb.setExecutingOnFirstTick(false);

        CommandBlockUpdatePacket nk = crossEncode(cb, CommandBlockUpdatePacket::new, protocol);

        assertTrue(nk.isBlock);
        assertEquals(10, nk.x);
        assertEquals(64, nk.y);
        assertEquals(-5, nk.z);
        assertEquals(1, nk.commandBlockMode); // REPEATING ordinal = 1
        assertFalse(nk.isRedstoneMode);
        assertTrue(nk.isConditional);
        assertEquals("/say hello", nk.command);
        assertTrue(nk.shouldTrackOutput);
        if (protocol >= ProtocolInfo.v1_12_0) {
            assertEquals(5, nk.tickDelay);
            assertFalse(nk.executingOnFirstTick);
        }
        if (protocol >= ProtocolInfo.v1_21_60) {
            assertEquals("cmd1", nk.filteredName);
        }
    }

    // ==================== BossEventPacket ====================

    @ParameterizedTest(name = "BossEventPacket (health percent) v{0}")
    @MethodSource("allVersions")
    void bossEventHealthPercent(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.BossEventPacket();
        cb.setBossUniqueEntityId(100L);
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action.UPDATE_PERCENTAGE); // ordinal 4 = NK TYPE_HEALTH_PERCENT
        cb.setHealthPercentage(0.75f);

        BossEventPacket nk = crossEncode(cb, BossEventPacket::new, protocol);

        assertEquals(100L, nk.bossEid);
        assertEquals(BossEventPacket.TYPE_HEALTH_PERCENT, nk.type);
        assertEquals(0.75f, nk.healthPercent, 0.001f);
    }

    @ParameterizedTest(name = "BossEventPacket (hide) v{0}")
    @MethodSource("allVersions")
    void bossEventHide(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.BossEventPacket();
        cb.setBossUniqueEntityId(200L);
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action.REMOVE); // ordinal 2 = NK TYPE_HIDE

        BossEventPacket nk = crossEncode(cb, BossEventPacket::new, protocol);

        assertEquals(200L, nk.bossEid);
        assertEquals(BossEventPacket.TYPE_HIDE, nk.type);
    }

    // ==================== AdventureSettingsPacket ====================

    @ParameterizedTest(name = "AdventureSettingsPacket v{0}")
    @MethodSource("allVersions")
    void adventureSettings(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AdventureSettingsPacket();
        cb.getSettings().add(AdventureSetting.AUTO_JUMP); // bit 5 (0x20 = 32) in flags1
        cb.setCommandPermission(org.cloudburstmc.protocol.bedrock.data.command.CommandPermission.GAME_DIRECTORS); // ordinal 1
        cb.setPlayerPermission(org.cloudburstmc.protocol.bedrock.data.PlayerPermission.MEMBER); // ordinal 1
        cb.setUniqueEntityId(999L);

        AdventureSettingsPacket nk = crossEncode(cb, AdventureSettingsPacket::new, protocol);

        assertEquals(32L, nk.flags); // AUTO_JUMP = bit 5 = 0x20
        assertEquals(1L, nk.commandPermission); // GAME_DIRECTORS ordinal = 1
        assertEquals(1L, nk.playerPermission); // MEMBER ordinal = 1
        assertEquals(999L, nk.entityUniqueId);
    }

    // ==================== ContainerClosePacket ====================

    @ParameterizedTest(name = "ContainerClosePacket v{0}")
    @MethodSource("allVersions")
    void containerClose(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket();
        cb.setId((byte) 5);
        cb.setServerInitiated(false);
        cb.setType(org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType.NONE);

        ContainerClosePacket nk = crossEncode(cb, ContainerClosePacket::new, protocol);

        assertEquals(5, nk.windowId);
        if (protocol >= ProtocolInfo.v1_16_100) {
            assertFalse(nk.wasServerInitiated);
        }
        if (protocol >= ProtocolInfo.v1_21_0) {
            assertEquals(ContainerType.NONE, nk.type);
        }
    }

    // ==================== RespawnPacket ====================

    @ParameterizedTest(name = "RespawnPacket v{0}")
    @MethodSource("allVersions")
    void respawn(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.RespawnPacket();
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(128.5f, 64.0f, -48.0f));
        cb.setState(org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.State.CLIENT_READY); // ordinal 2
        cb.setRuntimeEntityId(77L);

        RespawnPacket nk = crossEncode(cb, RespawnPacket::new, protocol);

        assertEquals(128.5f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-48.0f, nk.z, 0.001f);
        if (protocol >= 388) {
            assertEquals(RespawnPacket.STATE_CLIENT_READY_TO_SPAWN, nk.respawnState); // 2
            assertEquals(77L, nk.runtimeEntityId);
        }
    }

    // ==================== PlayerActionPacket ====================

    @ParameterizedTest(name = "PlayerActionPacket v{0}")
    @MethodSource("allVersions")
    void playerAction(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket();
        cb.setRuntimeEntityId(55L);
        cb.setAction(org.cloudburstmc.protocol.bedrock.data.PlayerActionType.START_BREAK); // ordinal 0
        cb.setBlockPosition(Vector3i.from(10, 64, -5));
        cb.setResultPosition(Vector3i.from(10, 63, -5));
        cb.setFace(1);

        PlayerActionPacket nk = crossEncode(cb, PlayerActionPacket::new, protocol);

        assertEquals(55L, nk.entityId);
        assertEquals(PlayerActionPacket.ACTION_START_BREAK, nk.action); // 0
        assertEquals(10, nk.x);
        assertEquals(64, nk.y);
        assertEquals(-5, nk.z);
        assertEquals(1, nk.face);
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            assertEquals(10, nk.resultPosition.x);
            assertEquals(63, nk.resultPosition.y);
            assertEquals(-5, nk.resultPosition.z);
        }
    }

    // ==================== MovePlayerPacket ====================

    @ParameterizedTest(name = "MovePlayerPacket v{0}")
    @MethodSource("allVersions")
    void movePlayer(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket();
        cb.setRuntimeEntityId(88L);
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(10.5f, 64.0f, -20.5f));
        cb.setRotation(org.cloudburstmc.math.vector.Vector3f.from(30.0f, 180.0f, 180.0f)); // pitch, yaw, headYaw
        cb.setMode(org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode.NORMAL); // ordinal 0
        cb.setOnGround(true);
        cb.setRidingRuntimeEntityId(0L);
        cb.setTick(1000L);

        MovePlayerPacket nk = crossEncode(cb, MovePlayerPacket::new, protocol);

        assertEquals(88L, nk.eid);
        assertEquals(10.5f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-20.5f, nk.z, 0.001f);
        assertEquals(30.0f, nk.pitch, 0.001f);
        assertEquals(180.0f, nk.yaw, 0.001f);
        assertEquals(180.0f, nk.headYaw, 0.001f);
        assertEquals(MovePlayerPacket.MODE_NORMAL, nk.mode);
        assertTrue(nk.onGround);
        assertEquals(0L, nk.ridingEid);
        if (protocol >= ProtocolInfo.v1_16_100) {
            assertEquals(1000L, nk.frame);
        }
    }

    // ==================== PositionTrackingDBClientRequestPacket ====================

    @ParameterizedTest(name = "PositionTrackingDBClientRequestPacket v{0}")
    @MethodSource("versionsFrom407")
    void positionTrackingDBClientRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBClientRequestPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBClientRequestPacket.Action.QUERY); // ordinal 0
        cb.setTrackingId(42);

        PositionTrackingDBClientRequestPacket nk = crossEncode(cb, PositionTrackingDBClientRequestPacket::new, protocol);

        assertEquals(PositionTrackingDBClientRequestPacket.Action.QUERY, nk.getAction());
        assertEquals(42, nk.getTrackingId());
    }

    // ==================== PositionTrackingDBServerBroadcastPacket ====================

    @ParameterizedTest(name = "PositionTrackingDBServerBroadcastPacket v{0}")
    @MethodSource("versionsFrom428to748")
    void positionTrackingDBServerBroadcast(int protocol) {
        byte[] tagBytes;
        try {
            tagBytes = cn.nukkit.nbt.NBTIO.writeGZIPCompressed(new cn.nukkit.nbt.tag.CompoundTag(""));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        var stream = new cn.nukkit.utils.BinaryStream();
        stream.putUnsignedVarInt(ProtocolInfo.POS_TRACKING_SERVER_BROADCAST_PACKET);
        stream.putByte((byte) PositionTrackingDBServerBroadcastPacket.Action.UPDATE.ordinal());
        stream.putVarInt(42);
        stream.put(tagBytes);

        PositionTrackingDBServerBroadcastPacket nk = new PositionTrackingDBServerBroadcastPacket();
        nk.protocol = protocol;
        nk.setBuffer(stream.getBuffer());
        nk.getUnsignedVarInt();
        nk.decode();

        assertEquals(PositionTrackingDBServerBroadcastPacket.Action.UPDATE,
                readField(nk, "action", PositionTrackingDBServerBroadcastPacket.Action.class));
        assertEquals(42, readField(nk, "trackingId", Integer.class));
        assertNull(nk.getPosition());
    }

    // ==================== NPCRequestPacket ====================

    @ParameterizedTest(name = "NPCRequestPacket v{0}")
    @MethodSource("allVersions")
    void npcRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.NpcRequestPacket();
        cb.setRuntimeEntityId(77L);
        cb.setRequestType(org.cloudburstmc.protocol.bedrock.data.NpcRequestType.SET_ACTION); // ordinal 0
        cb.setCommand("/say hello");
        cb.setActionType(2);
        cb.setSceneName("scene1");

        NPCRequestPacket nk = crossEncode(cb, NPCRequestPacket::new, protocol);

        assertEquals(77L, nk.entityRuntimeId);
        assertEquals(NPCRequestPacket.RequestType.SET_ACTIONS, nk.requestType); // ordinal 0
        assertEquals("/say hello", nk.commandString);
        assertEquals(2, nk.actionType);
        if (protocol >= ProtocolInfo.v1_17_10) {
            assertEquals("scene1", nk.sceneName);
        }
    }

    // ==================== CreatePhotoPacket ====================

    @ParameterizedTest(name = "CreatePhotoPacket v{0}")
    @MethodSource("versionsFrom465")
    void createPhoto(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CreatePhotoPacket();
        cb.setId(12345L); // actorUniqueId
        cb.setPhotoName("photo_001");
        cb.setPhotoItemName("minecraft:photo");

        CreatePhotoPacket nk = crossEncode(cb, CreatePhotoPacket::new, protocol);

        assertEquals(12345L, nk.getActorUniqueId());
        assertEquals("photo_001", nk.getPhotoName());
        assertEquals("minecraft:photo", nk.getPhotoItemName());
    }

    // ==================== SetPlayerGameTypePacket ====================

    @ParameterizedTest(name = "SetPlayerGameTypePacket v{0}")
    @MethodSource("allVersions")
    void setPlayerGameType(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket();
        cb.setGamemode(1); // creative

        SetPlayerGameTypePacket nk = crossEncode(cb, SetPlayerGameTypePacket::new, protocol);

        assertEquals(1, nk.gamemode);
    }

    // ==================== PlayerHotbarPacket ====================

    @ParameterizedTest(name = "PlayerHotbarPacket v{0}")
    @MethodSource("allVersions")
    void playerHotbar(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket();
        cb.setSelectedHotbarSlot(3);
        cb.setContainerId(0); // INVENTORY
        cb.setSelectHotbarSlot(true);

        PlayerHotbarPacket nk = crossEncode(cb, PlayerHotbarPacket::new, protocol);

        assertEquals(3, nk.selectedHotbarSlot);
        assertEquals(0, nk.windowId);
        assertTrue(nk.selectHotbarSlot);
    }

    // ==================== ShowCreditsPacket ====================

    @ParameterizedTest(name = "ShowCreditsPacket v{0}")
    @MethodSource("allVersions")
    void showCredits(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket();
        cb.setRuntimeEntityId(11L);
        cb.setStatus(org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket.Status.END_CREDITS); // ordinal 1

        ShowCreditsPacket nk = crossEncode(cb, ShowCreditsPacket::new, protocol);

        assertEquals(11L, nk.eid);
        assertEquals(ShowCreditsPacket.STATUS_END_CREDITS, nk.status); // 1
    }

    // ==================== InteractPacket ====================

    @ParameterizedTest(name = "InteractPacket (open inventory) v{0}")
    @MethodSource("allVersions")
    void interactOpenInventory(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InteractPacket();
        cb.setRuntimeEntityId(99L);
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action.OPEN_INVENTORY); // ordinal 6
        cb.setMousePosition(null);

        InteractPacket nk = crossEncode(cb, InteractPacket::new, protocol);

        assertEquals(99L, nk.target);
        assertEquals(InteractPacket.ACTION_OPEN_INVENTORY, nk.action); // 6
    }

    // ==================== SetDifficultyPacket ====================

    @ParameterizedTest(name = "SetDifficultyPacket v{0}")
    @MethodSource("allVersions")
    void setDifficulty(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetDifficultyPacket();
        cb.setDifficulty(2); // NORMAL

        SetDifficultyPacket nk = crossEncode(cb, SetDifficultyPacket::new, protocol);

        assertEquals(2, nk.difficulty);
    }

    // ==================== DisconnectPacket ====================

    @ParameterizedTest(name = "DisconnectPacket v{0}")
    @MethodSource("allVersions")
    void disconnect(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket();
        cb.setReason(org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason.UNKNOWN); // ordinal 0
        cb.setMessageSkipped(false);
        cb.setKickMessage("You have been disconnected.");
        cb.setFilteredMessage("You have been disconnected.");

        DisconnectPacket nk = crossEncode(cb, DisconnectPacket::new, protocol);

        assertFalse(nk.hideDisconnectionScreen);
        assertEquals("You have been disconnected.", nk.message);
        if (protocol >= ProtocolInfo.v1_20_40) { // v622
            assertEquals(cn.nukkit.network.protocol.types.DisconnectFailReason.UNKNOWN, nk.reason);
        }
        if (protocol >= ProtocolInfo.v1_21_20) { // v712
            assertEquals("You have been disconnected.", nk.filteredMessage);
        }
    }

    // ==================== PlayerLocationPacket ====================

    @ParameterizedTest(name = "PlayerLocationPacket v{0}")
    @MethodSource("versionsFrom800")
    void playerLocation(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket();
        cb.setType(org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket.Type.COORDINATES); // ordinal 0
        cb.setTargetEntityId(55L);
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(10.5f, 64.0f, -20.5f));

        PlayerLocationPacket nk = crossEncode(cb, PlayerLocationPacket::new, protocol);

        assertEquals(PlayerLocationPacket.Type.COORDINATES, nk.getType());
        assertEquals(55L, nk.getTargetEntityId());
        assertEquals(10.5f, nk.getPosition().x, 0.001f);
        assertEquals(64.0f, nk.getPosition().y, 0.001f);
        assertEquals(-20.5f, nk.getPosition().z, 0.001f);
    }

    // ==================== TrimDataPacket ====================

    @ParameterizedTest(name = "TrimDataPacket v{0}")
    @MethodSource("versionsFrom582")
    void trimData(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket();
        cb.getPatterns().add(new org.cloudburstmc.protocol.bedrock.data.TrimPattern("minecraft:iron_ingot", "sentry"));
        cb.getMaterials().add(new org.cloudburstmc.protocol.bedrock.data.TrimMaterial(
                "gold", "yellow", "minecraft:gold_ingot"));

        TrimDataPacket nk = crossEncode(cb, TrimDataPacket::new, protocol);

        assertEquals(1, nk.getPatterns().size());
        assertEquals("minecraft:iron_ingot", nk.getPatterns().get(0).itemName());
        assertEquals("sentry", nk.getPatterns().get(0).patternId());
        assertEquals(1, nk.getMaterials().size());
        assertEquals("gold", nk.getMaterials().get(0).materialId());
        assertEquals("yellow", nk.getMaterials().get(0).color());
        assertEquals("minecraft:gold_ingot", nk.getMaterials().get(0).itemName());
    }

    // ==================== JigsawStructureDataPacket ====================

    @ParameterizedTest(name = "JigsawStructureDataPacket v{0}")
    @MethodSource("versionsFrom712")
    void jigsawStructureData(int protocol) {
        byte[] tagBytes;
        try {
            tagBytes = cn.nukkit.nbt.NBTIO.write(new cn.nukkit.nbt.tag.CompoundTag("")
                    .putString("name", "village/plains/houses")
                    .putInt("max_depth", 7), java.nio.ByteOrder.BIG_ENDIAN, false);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        var stream = new cn.nukkit.utils.BinaryStream();
        stream.putUnsignedVarInt(ProtocolInfo.JIGSAW_STRUCTURE_DATA_PACKET);
        stream.put(tagBytes);

        JigsawStructureDataPacket nk = new JigsawStructureDataPacket();
        nk.protocol = protocol;
        nk.setBuffer(stream.getBuffer());
        nk.getUnsignedVarInt();
        nk.decode();

        assertNotNull(nk.nbt);
        assertEquals("village/plains/houses", nk.nbt.getString("name"));
        assertEquals(7, nk.nbt.getInt("max_depth"));
    }

    // ==================== LevelSoundEvent1Packet ====================

    static Stream<Arguments> versionsPreV332() {
        return filteredVersionsRange(291, 332);
    }

    @ParameterizedTest(name = "LevelSoundEvent1Packet v{0}")
    @MethodSource("versionsPreV332")
    void levelSoundEvent1(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.LevelSoundEvent1Packet();
        cb.setSound(org.cloudburstmc.protocol.bedrock.data.SoundEvent.HIT); // ID 1 in v291 typeMap
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(5.5f, 64.0f, -10.5f));
        cb.setExtraData(-1);
        cb.setPitch(1);
        cb.setBabySound(false);
        cb.setRelativeVolumeDisabled(false);

        LevelSoundEventPacketV1 nk = crossEncode(cb, LevelSoundEventPacketV1::new, protocol);

        assertEquals(1, nk.sound); // HIT = ID 1
        assertEquals(5.5f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-10.5f, nk.z, 0.001f);
        assertEquals(-1, nk.extraData);
        assertEquals(1, nk.pitch);
        assertFalse(nk.isBabyMob);
        assertFalse(nk.isGlobal);
    }

    // ==================== LevelSoundEvent2Packet (v313-v766) ====================

    static Stream<Arguments> versionsFrom428() {
        return filteredVersions(ProtocolInfo.v1_16_210);
    }

    static Stream<Arguments> versionsFrom448() {
        return filteredVersions(ProtocolInfo.v1_17_10);
    }

    static Stream<Arguments> versionsFrom582() {
        return filteredVersions(ProtocolInfo.v1_19_80);
    }

    static Stream<Arguments> versionsFrom649() {
        return filteredVersions(ProtocolInfo.v1_20_60);
    }

    static Stream<Arguments> versionsFrom685() {
        return filteredVersions(ProtocolInfo.v1_21_0);
    }

    static Stream<Arguments> versionsFrom748PreV818() {
        return filteredVersionsRange(ProtocolInfo.v1_21_40, ProtocolInfo.v1_21_90);
    }

    static Stream<Arguments> versionsFrom729() {
        return filteredVersions(ProtocolInfo.v1_21_30);
    }

    static Stream<Arguments> versionsFrom924() {
        return filteredVersions(ProtocolInfo.v1_26_0);
    }

    static Stream<Arguments> versionsFrom313PreV786() {
        return filteredVersionsRange(313, ProtocolInfo.v1_21_70);
    }

    @ParameterizedTest(name = "LevelSoundEvent2Packet v{0}")
    @MethodSource("versionsFrom313PreV786")
    void levelSoundEvent2(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.LevelSoundEvent2Packet();
        cb.setSound(org.cloudburstmc.protocol.bedrock.data.SoundEvent.HIT); // ID 1 in typeMap
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(3.0f, 70.0f, -5.0f));
        cb.setExtraData(-1);
        cb.setIdentifier("");
        cb.setBabySound(false);
        cb.setRelativeVolumeDisabled(false);

        LevelSoundEventPacketV2 nk = crossEncode(cb, LevelSoundEventPacketV2::new, protocol);

        assertEquals(1, nk.sound); // HIT = ID 1
        assertEquals(3.0f, nk.x, 0.001f);
        assertEquals(70.0f, nk.y, 0.001f);
        assertEquals(-5.0f, nk.z, 0.001f);
        assertEquals(-1, nk.extraData);
        assertEquals("", nk.entityIdentifier);
        assertFalse(nk.isBabyMob);
        assertFalse(nk.isGlobal);
    }

    // ==================== LevelSoundEventPacket ====================

    @ParameterizedTest(name = "LevelSoundEventPacket v{0}")
    @MethodSource("versionsFrom332")
    void levelSoundEvent(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket();
        cb.setSound(org.cloudburstmc.protocol.bedrock.data.SoundEvent.HIT); // ID 1 in v332 typeMap
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(5.5f, 64.0f, -10.5f));
        cb.setExtraData(-1);
        cb.setIdentifier("");
        cb.setBabySound(false);
        cb.setRelativeVolumeDisabled(false);
        cb.setEntityUniqueId(-1L);

        LevelSoundEventPacket nk = crossEncode(cb, LevelSoundEventPacket::new, protocol);

        assertEquals(1, nk.sound); // HIT = ID 1
        assertEquals(5.5f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-10.5f, nk.z, 0.001f);
        assertEquals(-1, nk.extraData);
        assertEquals("", nk.entityIdentifier);
        assertFalse(nk.isBabyMob);
        assertFalse(nk.isGlobal);
        if (protocol >= ProtocolInfo.v1_21_70_24) {
            assertEquals(-1L, nk.entityUniqueId);
        }
    }

    // ==================== BlockEntityDataPacket ====================

    @ParameterizedTest(name = "BlockEntityDataPacket v{0}")
    @MethodSource("allVersions")
    void blockEntityData(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket();
        cb.setBlockPosition(Vector3i.from(10, 64, -5));
        cb.setData(org.cloudburstmc.nbt.NbtMap.EMPTY);

        BlockEntityDataPacket nk = crossEncode(cb, BlockEntityDataPacket::new, protocol);

        assertEquals(10, nk.x);
        assertEquals(64, nk.y);
        assertEquals(-5, nk.z);
        assertNotNull(nk.namedTag);
        assertTrue(nk.namedTag.length > 0);
    }

    // ==================== MapCreateLockedCopyPacket ====================

    static Stream<Arguments> versionsFrom354() {
        return filteredVersions(354);
    }

    @ParameterizedTest(name = "MapCreateLockedCopyPacket v{0}")
    @MethodSource("versionsFrom354")
    void mapCreateLockedCopy(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.MapCreateLockedCopyPacket();
        cb.setOriginalMapId(111111L);
        cb.setNewMapId(222222L);

        MapCreateLockedCopyPacket nk = crossEncode(cb, MapCreateLockedCopyPacket::new, protocol);

        assertEquals(111111L, nk.originalMapId);
        assertEquals(222222L, nk.newMapId);
    }

    // ==================== TextPacket ====================

    @ParameterizedTest(name = "TextPacket SYSTEM v{0}")
    @MethodSource("allVersions")
    void textPacketSystem(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.TextPacket();
        cb.setType(org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type.SYSTEM);
        cb.setNeedsTranslation(false);
        cb.setMessage("Hello World");
        cb.setXuid("");
        cb.setPlatformChatId("");
        cb.setSourceName("");
        cb.setFilteredMessage("");

        TextPacket nk = crossEncode(cb, TextPacket::new, protocol);

        assertEquals(TextPacket.TYPE_SYSTEM, nk.type);
        assertEquals("Hello World", nk.message);
    }

    @ParameterizedTest(name = "TextPacket CHAT v{0}")
    @MethodSource("allVersions")
    void textPacketChat(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.TextPacket();
        cb.setType(org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type.CHAT);
        cb.setNeedsTranslation(false);
        cb.setSourceName("Steve");
        cb.setMessage("Hello everyone");
        cb.setXuid("");
        cb.setPlatformChatId("");
        cb.setFilteredMessage("");

        TextPacket nk = crossEncode(cb, TextPacket::new, protocol);

        assertEquals(TextPacket.TYPE_CHAT, nk.type);
        assertEquals("Steve", nk.source);
        assertEquals("Hello everyone", nk.message);
    }

    // ==================== BookEditPacket ====================

    @ParameterizedTest(name = "BookEditPacket SIGN_BOOK v{0}")
    @MethodSource("allVersions")
    void bookEditSignBook(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.BookEditPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.BookEditPacket.Action.SIGN_BOOK);
        cb.setInventorySlot(0);
        cb.setTitle("My Book");
        cb.setAuthor("TestAuthor");
        cb.setXuid("1234567890");

        BookEditPacket nk = crossEncode(cb, BookEditPacket::new, protocol);

        assertEquals(BookEditPacket.Action.SIGN_BOOK, nk.action);
        assertEquals("My Book", nk.title);
        assertEquals("TestAuthor", nk.author);
        assertEquals("1234567890", nk.xuid);
    }

    @ParameterizedTest(name = "BookEditPacket SWAP_PAGES v{0}")
    @MethodSource("allVersions")
    void bookEditSwapPages(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.BookEditPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.BookEditPacket.Action.SWAP_PAGES);
        cb.setInventorySlot(0);
        cb.setPageNumber(2);
        cb.setSecondaryPageNumber(5);

        BookEditPacket nk = crossEncode(cb, BookEditPacket::new, protocol);

        assertEquals(BookEditPacket.Action.SWAP_PAGES, nk.action);
        assertEquals(2, nk.pageNumber);
        assertEquals(5, nk.secondaryPageNumber);
    }

    @ParameterizedTest(name = "BookEditPacket ADD_PAGE v{0}")
    @MethodSource("allVersions")
    void bookEditAddPage(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.BookEditPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.BookEditPacket.Action.ADD_PAGE);
        cb.setInventorySlot(0);
        cb.setPageNumber(1);
        cb.setText("Page content");
        cb.setPhotoName("");

        BookEditPacket nk = crossEncode(cb, BookEditPacket::new, protocol);

        assertEquals(BookEditPacket.Action.ADD_PAGE, nk.action);
        assertEquals(1, nk.pageNumber);
        assertEquals("Page content", nk.text);
        assertEquals("", nk.photoName);
    }

    // ==================== CommandRequestPacket ====================

    @ParameterizedTest(name = "CommandRequestPacket v{0}")
    @MethodSource("allVersions")
    void commandRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket();
        cb.setCommand("/say Hello");
        cb.setCommandOriginData(new org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData(
                org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType.PLAYER,
                java.util.UUID.randomUUID(),
                "",
                0L
        ));
        cb.setInternal(false);

        CommandRequestPacket nk = crossEncode(cb, CommandRequestPacket::new, protocol);

        assertEquals("/say Hello", nk.command);
        assertEquals(cn.nukkit.network.protocol.types.CommandOriginData.Origin.PLAYER, nk.data.type);
        assertFalse(nk.internal);
    }

    // ==================== MoveEntityAbsolutePacket ====================

    @ParameterizedTest(name = "MoveEntityAbsolutePacket v{0}")
    @MethodSource("allVersions")
    void moveEntityAbsolute(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket();
        cb.setRuntimeEntityId(33L);
        cb.setOnGround(true);
        cb.setTeleported(false);
        cb.setForceMove(false);
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(5.5f, 64.0f, -10.5f));
        // Use equal yaw/headYaw to avoid ordering ambiguity
        cb.setRotation(org.cloudburstmc.math.vector.Vector3f.from(30.0f, 180.0f, 180.0f)); // pitch, yaw, headYaw

        MoveEntityAbsolutePacket nk = crossEncode(cb, MoveEntityAbsolutePacket::new, protocol);

        assertEquals(33L, nk.eid);
        assertTrue(nk.onGround);
        assertFalse(nk.teleport);
        assertEquals(5.5f, (float) nk.x, 0.5f);  // byte-angle precision ~1.4 degrees
        assertEquals(64.0f, (float) nk.y, 0.5f);
        assertEquals(-10.5f, (float) nk.z, 0.5f);
        assertEquals(30.0f, nk.pitch, 2.0f); // byte-angle ~1.4 degree precision
    }

    // ==================== InteractPacket ====================

    @ParameterizedTest(name = "InteractPacket MOUSEOVER v{0}")
    @MethodSource("allVersions")
    void interactMouseover(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InteractPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action.MOUSEOVER);
        cb.setRuntimeEntityId(33L);
        cb.setMousePosition(org.cloudburstmc.math.vector.Vector3f.from(0.5f, 1.0f, 0.0f));

        InteractPacket nk = crossEncode(cb, InteractPacket::new, protocol);

        assertEquals(InteractPacket.ACTION_MOUSEOVER, nk.action);
        assertEquals(33L, nk.target);
        // Pre-v1_2_0 (protocol < 137) no position; all our supported protocols >= 291 have it
        assertEquals(0.5f, nk.x, 0.001f);
        assertEquals(1.0f, nk.y, 0.001f);
        assertEquals(0.0f, nk.z, 0.001f);
    }

    // ==================== PlayerActionPacket ====================

    @ParameterizedTest(name = "PlayerActionPacket START_BREAK v{0}")
    @MethodSource("allVersions")
    void playerActionStartBreak(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket();
        cb.setRuntimeEntityId(50L);
        cb.setAction(org.cloudburstmc.protocol.bedrock.data.PlayerActionType.START_BREAK);
        cb.setBlockPosition(Vector3i.from(10, 64, -5));
        cb.setResultPosition(Vector3i.from(10, 64, -5));
        cb.setFace(1);

        PlayerActionPacket nk = crossEncode(cb, PlayerActionPacket::new, protocol);

        assertEquals(50L, nk.entityId);
        assertEquals(PlayerActionPacket.ACTION_START_BREAK, nk.action);
        assertEquals(10, nk.x);
        assertEquals(64, nk.y);
        assertEquals(-5, nk.z);
        assertEquals(1, nk.face);
    }

    // ==================== ShowProfilePacket ====================

    @ParameterizedTest(name = "ShowProfilePacket v{0}")
    @MethodSource("allVersions")
    void showProfile(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ShowProfilePacket();
        cb.setXuid("2535454796833386");

        ShowProfilePacket nk = crossEncode(cb, ShowProfilePacket::new, protocol);

        assertEquals("2535454796833386", nk.xuid);
    }

    // ==================== TransferPacket ====================

    @ParameterizedTest(name = "TransferPacket v{0}")
    @MethodSource("allVersions")
    void transfer(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.TransferPacket();
        cb.setAddress("play.example.com");
        cb.setPort(19132);
        cb.setReloadWorld(false);

        TransferPacket nk = crossEncode(cb, TransferPacket::new, protocol);

        assertEquals("play.example.com", nk.address);
        assertEquals(19132, nk.port);
        if (protocol >= ProtocolInfo.v1_21_30) { // v729
            assertFalse(nk.reloadWorld);
        }
    }

    // ==================== UpdatePlayerGameTypePacket ====================

    @ParameterizedTest(name = "UpdatePlayerGameTypePacket v{0}")
    @MethodSource("versionsFrom407")
    void updatePlayerGameType(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket();
        cb.setGameType(org.cloudburstmc.protocol.bedrock.data.GameType.CREATIVE);
        cb.setEntityId(99L);
        cb.setTick(500L);

        UpdatePlayerGameTypePacket nk = crossEncode(cb, UpdatePlayerGameTypePacket::new, protocol);

        assertEquals(cn.nukkit.network.protocol.types.GameType.CREATIVE, nk.gameType);
        assertEquals(99L, nk.entityId);
        if (protocol >= ProtocolInfo.v1_20_80) { // v671
            assertEquals(500L, nk.tick);
        }
    }

    // ==================== ChangeMobPropertyPacket ====================

    @ParameterizedTest(name = "ChangeMobPropertyPacket v{0}")
    @MethodSource("versionsFrom503")
    void changeMobProperty(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ChangeMobPropertyPacket();
        cb.setUniqueEntityId(12L);
        cb.setProperty("minecraft:is_angry");
        cb.setBoolValue(true);
        cb.setStringValue("");
        cb.setIntValue(0);
        cb.setFloatValue(0.0f);

        ChangeMobPropertyPacket nk = crossEncode(cb, ChangeMobPropertyPacket::new, protocol);

        assertEquals(12L, nk.getUniqueEntityId());
        assertEquals("minecraft:is_angry", nk.getProperty());
        assertTrue(nk.isBoolValue());
    }

    // ==================== AgentAnimationPacket ====================

    @ParameterizedTest(name = "AgentAnimationPacket v{0}")
    @MethodSource("versionsFrom594")
    void agentAnimation(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AgentAnimationPacket();
        cb.setAnimation((byte) 0); // ARM_SWING
        cb.setRuntimeEntityId(77L);

        AgentAnimationPacket nk = crossEncode(cb, AgentAnimationPacket::new, protocol);

        assertEquals(0, nk.animation);
        assertEquals(77L, nk.runtimeEntityId);
    }

    // ==================== PhotoTransferPacket ====================

    @ParameterizedTest(name = "PhotoTransferPacket v{0}")
    @MethodSource("allVersions")
    void photoTransfer(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PhotoTransferPacket();
        cb.setName("photo_001.png");
        cb.setData("rawbytes".getBytes(StandardCharsets.UTF_8));
        cb.setBookId("book-uuid-123");
        cb.setPhotoType(org.cloudburstmc.protocol.bedrock.data.PhotoType.PORTFOLIO);
        cb.setSourceType(org.cloudburstmc.protocol.bedrock.data.PhotoType.PORTFOLIO);
        cb.setOwnerId(55L);
        cb.setNewPhotoName("photo_new.png");

        PhotoTransferPacket nk = crossEncode(cb, PhotoTransferPacket::new, protocol);

        assertEquals("photo_001.png", nk.photoName);
        assertEquals("rawbytes", nk.photoData);
        assertEquals("book-uuid-123", nk.bookId);
        if (protocol >= ProtocolInfo.v1_17_30) { // v465
            assertEquals(55L, nk.ownerActorUniqueId);
            assertEquals("photo_new.png", nk.newPhotoName);
        }
    }

    // ==================== UpdateClientInputLocksPacket ====================

    @ParameterizedTest(name = "UpdateClientInputLocksPacket v{0}")
    @MethodSource("versionsFrom560")
    void updateClientInputLocks(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.UpdateClientInputLocksPacket();
        cb.setLockComponentData(0); // no locks
        cb.setServerPosition(org.cloudburstmc.math.vector.Vector3f.from(1.5f, 64.5f, -2.5f));

        UpdateClientInputLocksPacket nk = crossEncode(cb, UpdateClientInputLocksPacket::new, protocol);

        assertEquals(0, nk.lockComponentData);
        if (protocol < ProtocolInfo.v1_26_10) {
            assertEquals(1.5f, nk.serverPosition.x, 0.001f);
            assertEquals(64.5f, nk.serverPosition.y, 0.001f);
            assertEquals(-2.5f, nk.serverPosition.z, 0.001f);
        }
    }

    // ==================== UnlockedRecipesPacket ====================

    @ParameterizedTest(name = "UnlockedRecipesPacket v{0}")
    @MethodSource("versionsFrom575")
    void unlockedRecipes(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket.ActionType.NEWLY_UNLOCKED);
        cb.getUnlockedRecipes().add("minecraft:crafting_table");
        cb.getUnlockedRecipes().add("minecraft:furnace");

        UnlockedRecipesPacket nk = crossEncode(cb, UnlockedRecipesPacket::new, protocol);

        assertEquals(UnlockedRecipesPacket.ActionType.NEWLY_UNLOCKED, nk.getAction());
        assertEquals(2, nk.getUnlockedRecipes().size());
        assertTrue(nk.getUnlockedRecipes().contains("minecraft:crafting_table"));
        assertTrue(nk.getUnlockedRecipes().contains("minecraft:furnace"));
    }

    // ==================== ContainerOpenPacket ====================

    @ParameterizedTest(name = "ContainerOpenPacket v{0}")
    @MethodSource("allVersions")
    void containerOpen(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket();
        cb.setId((byte) 3);
        cb.setType(org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType.CONTAINER); // id=0
        cb.setBlockPosition(Vector3i.from(8, 64, -4));
        cb.setUniqueEntityId(-1L);

        ContainerOpenPacket nk = crossEncode(cb, ContainerOpenPacket::new, protocol);

        assertEquals(3, nk.windowId);
        assertEquals(0, nk.type); // CONTAINER id = 0
        assertEquals(8, nk.x);
        assertEquals(64, nk.y);
        assertEquals(-4, nk.z);
        assertEquals(-1L, nk.entityId);
    }

    // ==================== PlayerArmorDamagePacket ====================

    @ParameterizedTest(name = "PlayerArmorDamagePacket v{0}")
    @MethodSource("versionsFrom407PreV844")
    void playerArmorDamage(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerArmorDamagePacket();
        cb.getFlags().add(org.cloudburstmc.protocol.bedrock.data.PlayerArmorDamageFlag.HELMET);
        cb.getFlags().add(org.cloudburstmc.protocol.bedrock.data.PlayerArmorDamageFlag.BOOTS);
        cb.getDamage()[0] = 3; // HELMET
        cb.getDamage()[3] = 2; // BOOTS

        PlayerArmorDamagePacket nk = crossEncode(cb, PlayerArmorDamagePacket::new, protocol);

        assertTrue(nk.flags.contains(PlayerArmorDamagePacket.PlayerArmorDamageFlag.HELMET));
        assertTrue(nk.flags.contains(PlayerArmorDamagePacket.PlayerArmorDamageFlag.BOOTS));
        assertEquals(3, nk.damage[0]); // HELMET
        assertEquals(2, nk.damage[3]); // BOOTS
    }

    // ==================== PlayerStartItemCoolDownPacket ====================

    @ParameterizedTest(name = "PlayerStartItemCoolDownPacket v{0}")
    @MethodSource("versionsFrom486")
    void playerStartItemCoolDown(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerStartItemCooldownPacket();
        cb.setItemCategory("minecraft:goat_horn");
        cb.setCooldownDuration(40);

        PlayerStartItemCoolDownPacket nk = crossEncode(cb, PlayerStartItemCoolDownPacket::new, protocol);

        assertEquals("minecraft:goat_horn", nk.getItemCategory());
        assertEquals(40, nk.getCoolDownDuration());
    }

    // ==================== SetTitlePacket ====================

    @ParameterizedTest(name = "SetTitlePacket TITLE v{0}")
    @MethodSource("allVersions")
    void setTitleTitle(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket();
        cb.setType(org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket.Type.TITLE);
        cb.setText("Hello World");
        cb.setFadeInTime(10);
        cb.setStayTime(70);
        cb.setFadeOutTime(20);
        cb.setXuid("");
        cb.setPlatformOnlineId("");
        cb.setFilteredTitleText("");

        SetTitlePacket nk = crossEncode(cb, SetTitlePacket::new, protocol);

        assertEquals(SetTitlePacket.TYPE_TITLE, nk.type);
        assertEquals("Hello World", nk.text);
        assertEquals(10, nk.fadeInTime);
        assertEquals(70, nk.stayTime);
        assertEquals(20, nk.fadeOutTime);
    }

    // ==================== ToastRequestPacket ====================

    @ParameterizedTest(name = "ToastRequestPacket v{0}")
    @MethodSource("versionsFrom527")
    void toastRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket();
        cb.setTitle("Server Message");
        cb.setContent("Welcome to the server!");

        ToastRequestPacket nk = crossEncode(cb, ToastRequestPacket::new, protocol);

        assertEquals("Server Message", nk.title);
        assertEquals("Welcome to the server!", nk.content);
    }

    // ==================== CodeBuilderPacket ====================

    @ParameterizedTest(name = "CodeBuilderPacket v{0}")
    @MethodSource("versionsFrom407")
    void codeBuilder(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CodeBuilderPacket();
        cb.setUrl("https://code.minecraft.net/session/abc");
        cb.setOpening(true);

        CodeBuilderPacket nk = crossEncode(cb, CodeBuilderPacket::new, protocol);

        assertEquals("https://code.minecraft.net/session/abc", nk.url);
        assertTrue(nk.isOpening);
    }

    // ==================== OnScreenTextureAnimationPacket ====================

    @ParameterizedTest(name = "OnScreenTextureAnimationPacket v{0}")
    @MethodSource("versionsFrom354")
    void onScreenTextureAnimation(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket();
        cb.setEffectId(1234L);

        OnScreenTextureAnimationPacket nk = crossEncode(cb, OnScreenTextureAnimationPacket::new, protocol);

        assertEquals(1234, nk.effectId);
    }

    // ==================== CurrentStructureFeaturePacket ====================

    @ParameterizedTest(name = "CurrentStructureFeaturePacket v{0}")
    @MethodSource("versionsFrom712")
    void currentStructureFeature(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CurrentStructureFeaturePacket();
        cb.setCurrentStructureFeature("minecraft:trial_chambers");

        CurrentStructureFeaturePacket nk = crossEncode(cb, CurrentStructureFeaturePacket::new, protocol);

        assertEquals("minecraft:trial_chambers", nk.currentStructureFeature);
    }

    // ==================== ChangeDimensionPacket ====================

    @ParameterizedTest(name = "ChangeDimensionPacket v{0}")
    @MethodSource("allVersions")
    void changeDimension(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket();
        cb.setDimension(1); // Nether
        cb.setPosition(org.cloudburstmc.math.vector.Vector3f.from(128.0f, 64.0f, -64.0f));
        cb.setRespawn(true);
        cb.setLoadingScreenId(null);

        ChangeDimensionPacket nk = crossEncode(cb, ChangeDimensionPacket::new, protocol);

        assertEquals(1, nk.dimension);
        assertEquals(128.0f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-64.0f, nk.z, 0.001f);
        assertTrue(nk.respawn);
    }

    // ==================== TakeItemEntityPacket ====================

    @ParameterizedTest(name = "TakeItemEntityPacket v{0}")
    @MethodSource("allVersions")
    void takeItemEntity(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.TakeItemEntityPacket();
        cb.setItemRuntimeEntityId(10L);
        cb.setRuntimeEntityId(20L);

        TakeItemEntityPacket nk = crossEncode(cb, TakeItemEntityPacket::new, protocol);

        assertEquals(10L, nk.target);
        assertEquals(20L, nk.entityId);
    }

    // ==================== NetworkChunkPublisherUpdatePacket ====================

    @ParameterizedTest(name = "NetworkChunkPublisherUpdatePacket v{0}")
    @MethodSource("versionsFrom313")
    void networkChunkPublisherUpdate(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket();
        cb.setPosition(Vector3i.from(100, 64, -200));
        cb.setRadius(8);

        NetworkChunkPublisherUpdatePacket nk = crossEncode(cb, NetworkChunkPublisherUpdatePacket::new, protocol);

        assertEquals(100, nk.position.x);
        assertEquals(64, nk.position.y);
        assertEquals(-200, nk.position.z);
        assertEquals(8, nk.radius);
    }

    // ==================== AwardAchievementPacket ====================

    @ParameterizedTest(name = "AwardAchievementPacket v{0}")
    @MethodSource("versionsFrom685")
    void awardAchievement(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AwardAchievementPacket();
        cb.setAchievementId(42);

        AwardAchievementPacket nk = crossEncode(cb, AwardAchievementPacket::new, protocol);

        assertEquals(42, nk.achievementId);
    }

    // ==================== CameraShakePacket ====================

    @ParameterizedTest(name = "CameraShakePacket v{0}")
    @MethodSource("versionsFrom428")
    void cameraShake(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraShakePacket();
        cb.setIntensity(0.5f);
        cb.setDuration(2.0f);
        cb.setShakeType(org.cloudburstmc.protocol.bedrock.data.CameraShakeType.ROTATIONAL); // ordinal 1
        cb.setShakeAction(org.cloudburstmc.protocol.bedrock.data.CameraShakeAction.ADD);    // ordinal 0

        CameraShakePacket nk = crossEncode(cb, CameraShakePacket::new, protocol);

        assertEquals(0.5f, nk.intensity, 0.001f);
        assertEquals(2.0f, nk.duration, 0.001f);
        assertEquals(CameraShakePacket.CameraShakeType.ROTATIONAL, nk.shakeType); // ordinal 1
        assertEquals(CameraShakePacket.CameraShakeAction.ADD, nk.shakeAction);    // ordinal 0
    }

    // ==================== ChunkRadiusUpdatedPacket ====================

    @ParameterizedTest(name = "ChunkRadiusUpdatedPacket v{0}")
    @MethodSource("allVersions")
    void chunkRadiusUpdated(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket();
        cb.setRadius(10);

        ChunkRadiusUpdatedPacket nk = crossEncode(cb, ChunkRadiusUpdatedPacket::new, protocol);

        assertEquals(10, nk.radius);
    }

    // ==================== OpenSignPacket ====================

    @ParameterizedTest(name = "OpenSignPacket v{0}")
    @MethodSource("versionsFrom582")
    void openSign(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.OpenSignPacket();
        cb.setPosition(Vector3i.from(5, 64, -10));
        cb.setFrontSide(true);

        OpenSignPacket nk = crossEncode(cb, OpenSignPacket::new, protocol);

        assertEquals(5, nk.getPosition().x);
        assertEquals(64, nk.getPosition().y);
        assertEquals(-10, nk.getPosition().z);
        assertTrue(nk.isFrontSide());
    }

    // ==================== SetHudPacket ====================

    @ParameterizedTest(name = "SetHudPacket v{0}")
    @MethodSource("versionsFrom649")
    void setHud(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetHudPacket();
        cb.getElements().add(org.cloudburstmc.protocol.bedrock.data.HudElement.HOTBAR);   // ordinal 5
        cb.getElements().add(org.cloudburstmc.protocol.bedrock.data.HudElement.HEALTH);   // ordinal 6
        cb.setVisibility(org.cloudburstmc.protocol.bedrock.data.HudVisibility.HIDE);      // ordinal 0

        SetHudPacket nk = crossEncode(cb, SetHudPacket::new, protocol);

        assertEquals(2, nk.getElements().size());
        assertTrue(nk.getElements().contains(cn.nukkit.network.protocol.types.hub.HudElement.HOTBAR));
        assertTrue(nk.getElements().contains(cn.nukkit.network.protocol.types.hub.HudElement.HEALTH));
        assertEquals(cn.nukkit.network.protocol.types.hub.HudVisibility.HIDE, nk.getVisibility());
    }

    // ==================== SetMovementAuthorityPacket ====================

    @ParameterizedTest(name = "SetMovementAuthorityPacket v{0}")
    @MethodSource("versionsFrom748PreV818")
    void setMovementAuthority(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetMovementAuthorityPacket();
        cb.setMovementMode(org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode.SERVER); // ordinal 1

        SetMovementAuthorityPacket nk = crossEncode(cb, SetMovementAuthorityPacket::new, protocol);

        // SERVER ordinal = 1 → ServerAuthMovementMode.SERVER_AUTHORITATIVE_V2
        assertEquals(cn.nukkit.network.protocol.types.ServerAuthMovementMode.SERVER_AUTHORITATIVE_V2,
                nk.serverAuthMovementMode);
    }

    // ==================== NPCDialoguePacket ====================

    @ParameterizedTest(name = "NPCDialoguePacket v{0}")
    @MethodSource("versionsFrom448")
    void npcDialogue(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.NpcDialoguePacket();
        cb.setUniqueEntityId(999L);
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.NpcDialoguePacket.Action.OPEN); // ordinal 0
        cb.setDialogue("Welcome to the village!");
        cb.setSceneName("village_intro");
        cb.setNpcName("Village Elder");
        cb.setActionJson("{}");

        NPCDialoguePacket nk = crossEncode(cb, NPCDialoguePacket::new, protocol);

        assertEquals(999L, nk.getUniqueEntityId());
        assertEquals(NPCDialoguePacket.Action.OPEN, nk.getAction());
        assertEquals("Welcome to the village!", nk.getDialogue());
        assertEquals("village_intro", nk.getSceneName());
        assertEquals("Village Elder", nk.getNpcName());
        assertEquals("{}", nk.getActionJson());
    }

    // ==================== CameraAimAssistPacket ====================

    @ParameterizedTest(name = "CameraAimAssistPacket v{0}")
    @MethodSource("versionsFrom729")
    void cameraAimAssist(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPacket();
        cb.setPresetId("default_preset");
        cb.setViewAngle(org.cloudburstmc.math.vector.Vector2f.from(45.0f, 30.0f));
        cb.setDistance(5.0f);
        cb.setTargetMode(org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPacket.TargetMode.ANGLE); // ordinal 0
        cb.setAction(org.cloudburstmc.protocol.bedrock.data.camera.AimAssistAction.SET); // ordinal 0
        cb.setShowDebugRender(false);

        CameraAimAssistPacket nk = crossEncode(cb, CameraAimAssistPacket::new, protocol);

        if (protocol >= ProtocolInfo.v1_21_50) { // v766
            assertEquals("default_preset", nk.getPresetId());
        }
        assertEquals(45.0f, nk.getViewAngle().x, 0.001f);
        assertEquals(30.0f, nk.getViewAngle().y, 0.001f);
        assertEquals(5.0f, nk.getDistance(), 0.001f);
        assertEquals(CameraAimAssistPacket.TargetMode.ANGLE, nk.getTargetMode());
        assertEquals(CameraAimAssistPacket.Action.SET, nk.getAction());
        if (protocol >= ProtocolInfo.v1_21_100) { // v827
            assertFalse(nk.isShowDebugRender());
        }
    }

    // ==================== AddBehaviorTreePacket ====================

    @ParameterizedTest(name = "AddBehaviorTreePacket v{0}")
    @MethodSource("allVersions")
    void addBehaviorTree(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AddBehaviorTreePacket();
        cb.setBehaviorTreeJson("{\"type\":\"sequence\"}");

        AddBehaviorTreePacket nk = crossEncode(cb, AddBehaviorTreePacket::new, protocol);

        assertEquals("{\"type\":\"sequence\"}", nk.behaviorTreeJson);
    }

    // ==================== ServerboundDataStorePacket ====================

    @ParameterizedTest(name = "ServerboundDataStorePacket decode v{0}")
    @MethodSource("versionsFrom898")
    void serverboundDataStoreDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ServerboundDataStorePacket();
        cb.getUpdate().setDataStoreName("screen_state");
        cb.getUpdate().setProperty("search");
        cb.getUpdate().setPath("filters.keyword");
        cb.getUpdate().setData("village");
        cb.getUpdate().setUpdateCount(4);
        if (protocol >= ProtocolInfo.v1_26_0) {
            cb.getUpdate().setPathUpdateCount(6);
        }

        ServerboundDataStorePacket nk = crossEncode(cb, ServerboundDataStorePacket::new, protocol);

        assertNotNull(nk.getUpdate());
        assertEquals("screen_state", nk.getUpdate().getDataStoreName());
        assertEquals("search", nk.getUpdate().getProperty());
        assertEquals("filters.keyword", nk.getUpdate().getPath());
        assertEquals(cn.nukkit.network.protocol.types.datastore.DataStorePropertyType.STRING, nk.getUpdate().getPropertyType());
        assertEquals("village", nk.getUpdate().getData());
        assertEquals(4, nk.getUpdate().getPropertyUpdateCount());
        if (protocol >= ProtocolInfo.v1_26_0) {
            assertEquals(6, nk.getUpdate().getPathUpdateCount());
        } else {
            assertEquals(0, nk.getUpdate().getPathUpdateCount());
        }
    }

    // ==================== ClientboundDataDrivenUIShowScreenPacket ====================

    @ParameterizedTest(name = "ClientboundDataDrivenUICloseAllScreensPacket decode v{0}")
    @MethodSource("versionsFrom924")
    void clientboundDataDrivenUICloseAllScreensDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUICloseScreenPacket();
        if (protocol >= ProtocolInfo.v1_26_10) {
            cb.setFormId(44);
        }

        ClientboundDataDrivenUICloseAllScreensPacket nk = crossEncode(cb, ClientboundDataDrivenUICloseAllScreensPacket::new, protocol);

        if (protocol >= ProtocolInfo.v1_26_10) {
            assertEquals(44, nk.formId);
        } else {
            assertNull(nk.formId);
        }
    }

    @ParameterizedTest(name = "ClientboundDataDrivenUIShowScreenPacket v{0}")
    @MethodSource("versionsFrom924")
    void clientboundDataDrivenUIShowScreen(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUIShowScreenPacket();
        cb.setScreenId("hud_screen");

        ClientboundDataDrivenUIShowScreenPacket nk = crossEncode(cb, ClientboundDataDrivenUIShowScreenPacket::new, protocol);

        assertEquals("hud_screen", nk.screenId);
    }

    // ==================== CameraPacket ====================

    @ParameterizedTest(name = "CameraPacket v{0}")
    @MethodSource("allVersions")
    void cameraPacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraPacket();
        cb.setCameraUniqueEntityId(11L);
        cb.setPlayerUniqueEntityId(22L);

        CameraPacket nk = crossEncode(cb, CameraPacket::new, protocol);

        assertEquals(11L, nk.cameraUniqueId);
        assertEquals(22L, nk.playerUniqueId);
    }

    // ==================== AnimateEntityPacket ====================

    @ParameterizedTest(name = "AnimateEntityPacket v{0}")
    @MethodSource("versionsFrom428")
    void animateEntity(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AnimateEntityPacket();
        cb.setAnimation("animation.character.walk");
        cb.setNextState("default");
        cb.setStopExpression("query.any_animation_finished");
        cb.setStopExpressionVersion(16777216);
        cb.setController("__runtime_controller");
        cb.setBlendOutTime(0.0f);
        cb.getRuntimeEntityIds().add(55L);

        AnimateEntityPacket nk = crossEncode(cb, AnimateEntityPacket::new, protocol);

        assertEquals("animation.character.walk", nk.getAnimation());
        assertEquals("default", nk.getNextState());
        assertEquals("query.any_animation_finished", nk.getStopExpression());
        if (protocol >= ProtocolInfo.v1_17_30) { // v465
            assertEquals(16777216, nk.getStopExpressionVersion());
        }
        assertEquals("__runtime_controller", nk.getController());
        assertEquals(0.0f, nk.getBlendOutTime(), 0.001f);
        assertEquals(1, nk.getEntityRuntimeIds().size());
        assertEquals(55L, nk.getEntityRuntimeIds().get(0));
    }

    // ==================== LevelEventPacket ====================

    @ParameterizedTest(name = "LevelEventPacket v{0}")
    @MethodSource("allVersions")
    void levelEvent(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket();
        cb.setType(LevelEvent.SOUND_CLICK);
        cb.setPosition(Vector3f.from(1.0f, 64.0f, -5.0f));
        cb.setData(42);

        LevelEventPacket nk = crossEncode(cb, LevelEventPacket::new, protocol);

        assertEquals(LevelEventPacket.EVENT_SOUND_CLICK, nk.evid); // TypeMap ID = 1000
        assertEquals(1.0f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-5.0f, nk.z, 0.001f);
        assertEquals(42, nk.data);
    }

    // ==================== MoveEntityDeltaPacket ====================

    @ParameterizedTest(name = "MoveEntityDeltaPacket v{0}")
    @MethodSource("allVersions")
    void moveEntityDelta(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket();
        cb.setRuntimeEntityId(99L);
        cb.getFlags().add(org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket.Flag.HAS_X);
        cb.getFlags().add(org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket.Flag.HAS_Y);
        cb.getFlags().add(org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket.Flag.HAS_Z);
        // Set both int delta (v291/v388: VarInt) and float (v419+: FloatLE)
        cb.setDeltaX(5);
        cb.setDeltaY(64);
        cb.setDeltaZ(-3);
        cb.setX(5.0f);
        cb.setY(64.0f);
        cb.setZ(-3.0f);

        MoveEntityDeltaPacket nk = crossEncode(cb, MoveEntityDeltaPacket::new, protocol);

        // v291 serializer starts flags at 0 and ORs in set flags → 0b111 = 7
        // v388+ serializer starts flags at 0xFFFF and clears unset flags → 0xFE07
        int expectedFlags = protocol < ProtocolInfo.v1_13_0
                ? MoveEntityDeltaPacket.FLAG_HAS_X | MoveEntityDeltaPacket.FLAG_HAS_Y | MoveEntityDeltaPacket.FLAG_HAS_Z
                : 0xFE07;
        assertEquals(expectedFlags, nk.flags);
        assertEquals(5.0f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-3.0f, nk.z, 0.001f);
    }

    // ==================== ClientboundTextureShiftPacket ====================

    @ParameterizedTest(name = "ClientboundTextureShiftPacket v{0}")
    @MethodSource("versionsFrom924")
    void clientboundTextureShift(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ClientboundTextureShiftPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.ClientboundTextureShiftPacket.Action.START);
        cb.setCollectionName("textures");
        cb.setFromStep("step1");
        cb.setToStep("step2");
        cb.setAllSteps(java.util.List.of("step1", "step2"));
        cb.setCurrentLengthTicks(100L);
        cb.setTotalLengthTicks(200L);
        cb.setEnabled(true);

        ClientboundTextureShiftPacket nk = crossEncode(cb, ClientboundTextureShiftPacket::new, protocol);

        assertEquals(ClientboundTextureShiftPacket.ACTION_START, nk.action);
        assertEquals("textures", nk.collectionName);
        assertEquals("step1", nk.fromStep);
        assertEquals("step2", nk.toStep);
        assertEquals(2, nk.allSteps.length);
        assertEquals("step1", nk.allSteps[0]);
        assertEquals("step2", nk.allSteps[1]);
        assertEquals(100L, nk.currentLengthInTicks);
        assertEquals(200L, nk.totalLengthInTicks);
        assertTrue(nk.enabled);
    }

    // ==================== ResourcePackChunkDataPacket ====================

    @ParameterizedTest(name = "ResourcePackChunkDataPacket v{0}")
    @MethodSource("allVersions")
    void resourcePackChunkData(int protocol) {
        UUID packId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkDataPacket();
        cb.setPackId(packId);
        cb.setChunkIndex(2);
        cb.setProgress(65536L);
        cb.setData(Unpooled.wrappedBuffer(new byte[]{1, 2, 3}));

        ResourcePackChunkDataPacket nk = crossEncode(cb, ResourcePackChunkDataPacket::new, protocol);

        assertEquals(packId, nk.packId);
        assertEquals(2, nk.chunkIndex);
        assertEquals(65536L, nk.progress);
        assertArrayEquals(new byte[]{1, 2, 3}, nk.data);
    }

    // ==================== ResourcePackDataInfoPacket ====================

    @ParameterizedTest(name = "ResourcePackDataInfoPacket v{0}")
    @MethodSource("versionsFrom388")
    void resourcePackDataInfo(int protocol) {
        UUID packId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        byte[] sha256 = new byte[]{0x1, 0x2, 0x3, 0x4};
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ResourcePackDataInfoPacket();
        cb.setPackId(packId);
        cb.setMaxChunkSize(1024L);
        cb.setChunkCount(3L);
        cb.setCompressedPackSize(100000L);
        cb.setHash(sha256);
        cb.setPremium(false);
        cb.setType(ResourcePackType.RESOURCES);

        ResourcePackDataInfoPacket nk = crossEncode(cb, ResourcePackDataInfoPacket::new, protocol);

        assertEquals(packId, nk.packId);
        assertEquals(1024, nk.maxChunkSize);
        assertEquals(3, nk.chunkCount);
        assertEquals(100000L, nk.compressedPackSize);
        assertArrayEquals(sha256, nk.sha256);
        assertFalse(nk.premium);
        // ResourcePackType.RESOURCES maps to TypeMap ID 6 in v388+ codecs
        assertEquals(6, nk.type);
    }

    // ==================== CameraAimAssistPresetsPacket ====================

    @ParameterizedTest(name = "CameraAimAssistPresetsPacket v{0}")
    @MethodSource("versionsV766ToV800")
    void cameraAimAssistPresets(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPresetsPacket();

        var categories = new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistCategories();
        categories.setIdentifier("default_group");

        var category = new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistCategory();
        category.setName("mobs");
        category.getEntityPriorities().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPriority("minecraft:zombie", 10));
        category.getBlockPriorities().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPriority("minecraft:chest", 5));
        category.setEntityDefaultPriorities(7);
        category.setBlockDefaultPriorities(8);
        categories.getCategories().add(category);
        cb.getCategories().add(categories);

        var preset = new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPresetDefinition();
        preset.setIdentifier("default_preset");
        preset.setCategories("default_group");
        preset.getExclusionList().add("minecraft:cow");
        preset.getLiquidTargetingList().add("water");
        preset.getItemSettings().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistItemSettings("minecraft:bow", "mobs"));
        preset.setDefaultItemSettings("mobs");
        preset.setHandSettings("hands");
        cb.getPresets().add(preset);
        if (protocol >= ProtocolInfo.v1_21_60) {
            cb.setOperation(org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistOperation.ADD_TO_EXISTING);
        }

        CameraAimAssistPresetsPacket nk = crossEncode(cb, CameraAimAssistPresetsPacket::new, protocol);

        var decodedCategories = readField(nk, "categories", List.class);
        assertEquals(1, decodedCategories.size());
        var decodedCategoryGroup = (cn.nukkit.network.protocol.types.camera.CameraAimAssistCategories) decodedCategories.get(0);
        assertEquals("default_group", decodedCategoryGroup.getIdentifier());
        assertEquals(1, decodedCategoryGroup.getCategories().size());

        var decodedCategory = decodedCategoryGroup.getCategories().get(0);
        assertEquals("mobs", decodedCategory.getName());
        assertEquals(1, decodedCategory.getEntityPriorities().size());
        assertEquals("minecraft:zombie", decodedCategory.getEntityPriorities().get(0).getName());
        assertEquals(10, decodedCategory.getEntityPriorities().get(0).getPriority());
        assertEquals(1, decodedCategory.getBlockPriorities().size());
        assertEquals("minecraft:chest", decodedCategory.getBlockPriorities().get(0).getName());
        assertEquals(5, decodedCategory.getBlockPriorities().get(0).getPriority());
        assertEquals(7, decodedCategory.getEntityDefaultPriorities());
        assertEquals(8, decodedCategory.getBlockDefaultPriorities());

        var decodedPresets = readField(nk, "presets", List.class);
        assertEquals(1, decodedPresets.size());
        var decodedPreset = (cn.nukkit.network.protocol.types.camera.CameraAimAssistPresetDefinition) decodedPresets.get(0);
        assertEquals("default_preset", decodedPreset.getIdentifier());
        if (protocol < ProtocolInfo.v1_21_60) {
            assertEquals("default_group", decodedPreset.getCategories());
        }
        assertEquals(List.of("minecraft:cow"), decodedPreset.getExclusionList());
        assertEquals(List.of("water"), decodedPreset.getLiquidTargetingList());
        assertEquals(1, decodedPreset.getItemSettings().size());
        assertEquals("minecraft:bow", decodedPreset.getItemSettings().get(0).getItemId());
        assertEquals("mobs", decodedPreset.getItemSettings().get(0).getCategory());
        assertEquals("mobs", decodedPreset.getDefaultItemSettings());
        assertEquals("hands", decodedPreset.getHandSettings());
        if (protocol >= ProtocolInfo.v1_21_60) {
            assertEquals(cn.nukkit.network.protocol.types.camera.CameraAimAssistOperation.ADD_TO_EXISTING, nk.getOperation());
        }
    }

    @ParameterizedTest(name = "CameraAimAssistPresetsPacket v{0} (>=924)")
    @MethodSource("versionsFrom924")
    void cameraAimAssistPresetsLatest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPresetsPacket();

        var category = new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistCategory();
        category.setName("mobs");
        category.getEntityPriorities().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPriority("minecraft:zombie", 10));
        category.getBlockPriorities().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPriority("minecraft:chest", 5));
        category.getBlockTagPriorities().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPriority("minecraft:logs", 4));
        category.getEntityTypeFamiliesPriorities().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPriority("undead", 3));
        category.setEntityDefaultPriorities(7);
        category.setBlockDefaultPriorities(8);
        cb.getCategoryDefinitions().add(category);

        var preset = new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistPresetDefinition();
        preset.setIdentifier("default_preset");
        preset.getBlockExclusionList().add("minecraft:barrel");
        preset.getEntityExclusionList().add("minecraft:cow");
        preset.getBlockTagExclusionList().add("leaves");
        preset.getEntityTypeFamiliesExclusionList().add("aquatic");
        preset.getLiquidTargetingList().add("water");
        preset.getItemSettings().add(new org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistItemSettings("minecraft:bow", "mobs"));
        preset.setDefaultItemSettings("mobs");
        preset.setHandSettings("hands");
        cb.getPresets().add(preset);
        cb.setOperation(org.cloudburstmc.protocol.bedrock.data.camera.CameraAimAssistOperation.ADD_TO_EXISTING);

        CameraAimAssistPresetsPacket nk = crossEncode(cb, CameraAimAssistPresetsPacket::new, protocol);

        var decodedCategories = readField(nk, "categories", List.class);
        assertEquals(1, decodedCategories.size());
        var decodedCategoryGroup = (cn.nukkit.network.protocol.types.camera.CameraAimAssistCategories) decodedCategories.get(0);
        assertEquals("", decodedCategoryGroup.getIdentifier());
        assertEquals(1, decodedCategoryGroup.getCategories().size());

        var decodedCategory = decodedCategoryGroup.getCategories().get(0);
        assertEquals("mobs", decodedCategory.getName());
        assertEquals("minecraft:zombie", decodedCategory.getEntityPriorities().get(0).getName());
        assertEquals("minecraft:chest", decodedCategory.getBlockPriorities().get(0).getName());
        assertEquals("minecraft:logs", decodedCategory.getBlockTagPriorities().get(0).getName());
        assertEquals("undead", decodedCategory.getEntityTypeFamiliesPriorities().get(0).getName());
        assertEquals(7, decodedCategory.getEntityDefaultPriorities());
        assertEquals(8, decodedCategory.getBlockDefaultPriorities());

        var decodedPresets = readField(nk, "presets", List.class);
        assertEquals(1, decodedPresets.size());
        var decodedPreset = (cn.nukkit.network.protocol.types.camera.CameraAimAssistPresetDefinition) decodedPresets.get(0);
        assertEquals("default_preset", decodedPreset.getIdentifier());
        assertEquals(List.of("minecraft:barrel"), decodedPreset.getBlockExclusionList());
        assertEquals(List.of("minecraft:cow"), decodedPreset.getEntityExclusionList());
        assertEquals(List.of("leaves"), decodedPreset.getBlockTagExclusionList());
        assertEquals(List.of("aquatic"), decodedPreset.getEntityTypeFamiliesExclusionList());
        assertEquals(List.of("water"), decodedPreset.getLiquidTargetingList());
        assertEquals("mobs", decodedPreset.getDefaultItemSettings());
        assertEquals("hands", decodedPreset.getHandSettings());
        assertEquals(cn.nukkit.network.protocol.types.camera.CameraAimAssistOperation.ADD_TO_EXISTING, nk.getOperation());
    }

    // ==================== InventoryTransactionPacket ====================

    @ParameterizedTest(name = "InventoryTransactionPacket v{0}")
    @MethodSource("versionsFrom431")
    void inventoryTransaction(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket();
        cb.setLegacyRequestId(0);
        cb.setTransactionType(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.ITEM_RELEASE);
        cb.getActions().add(new org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData(
                org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource.fromContainerWindowId(0),
                4,
                org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR,
                org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR
        ));
        cb.setActionType(InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE);
        cb.setHotbarSlot(2);
        cb.setItemInHand(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.setHeadPosition(Vector3f.from(1.5f, 64.0f, -2.5f));

        InventoryTransactionPacket nk = crossEncode(cb, InventoryTransactionPacket::new, protocol);

        assertEquals(0, nk.legacyRequestId);
        assertEquals(InventoryTransactionPacket.TYPE_RELEASE_ITEM, nk.transactionType);
        assertEquals(1, nk.actions.length);
        assertEquals(0, nk.actions[0].sourceType);
        assertEquals(0, nk.actions[0].windowId);
        assertEquals(4, nk.actions[0].inventorySlot);
        assertTrue(nk.actions[0].oldItem.isNull());
        assertTrue(nk.actions[0].newItem.isNull());
        assertInstanceOf(ReleaseItemData.class, nk.transactionData);

        var releaseItemData = (ReleaseItemData) nk.transactionData;
        assertEquals(InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE, releaseItemData.actionType);
        assertEquals(2, releaseItemData.hotbarSlot);
        assertTrue(releaseItemData.itemInHand.isNull());
        assertEquals(1.5f, releaseItemData.headRot.x, 0.001f);
        assertEquals(64.0f, releaseItemData.headRot.y, 0.001f);
        assertEquals(-2.5f, releaseItemData.headRot.z, 0.001f);
    }

    // ==================== PlayerAuthInputPacket ====================

    @ParameterizedTest(name = "PlayerAuthInputPacket v{0}")
    @MethodSource("versionsFrom527")
    void playerAuthInput(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket();
        cb.setRotation(Vector3f.from(10.5f, 20.5f, 30.5f));
        cb.setPosition(Vector3f.from(1.25f, 64.0f, -3.5f));
        cb.setMotion(org.cloudburstmc.math.vector.Vector2f.from(0.25f, -0.5f));
        cb.getInputData().add(org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData.UP);
        cb.getInputData().add(org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData.JUMPING);
        cb.setInputMode(org.cloudburstmc.protocol.bedrock.data.InputMode.TOUCH);
        cb.setPlayMode(org.cloudburstmc.protocol.bedrock.data.ClientPlayMode.NORMAL);
        cb.setInputInteractionModel(org.cloudburstmc.protocol.bedrock.data.InputInteractionModel.CROSSHAIR);
        cb.setTick(123L);
        cb.setDelta(Vector3f.from(0.1f, 0.2f, 0.3f));

        if (protocol >= ProtocolInfo.v1_19_70) {
            cb.setAnalogMoveVector(org.cloudburstmc.math.vector.Vector2f.from(0.6f, -0.4f));
        }
        if (protocol >= ProtocolInfo.v1_21_40) {
            cb.setInteractRotation(org.cloudburstmc.math.vector.Vector2f.from(5.0f, 6.0f));
            cb.setCameraOrientation(Vector3f.from(0.0f, 1.0f, 0.0f));
        }
        if (protocol >= ProtocolInfo.v1_21_50) {
            cb.setRawMoveVector(org.cloudburstmc.math.vector.Vector2f.from(-0.25f, 0.75f));
        }

        PlayerAuthInputPacket nk = crossEncode(cb, PlayerAuthInputPacket::new, protocol);

        assertEquals(10.5f, nk.getPitch(), 0.001f);
        assertEquals(20.5f, nk.getYaw(), 0.001f);
        assertEquals(30.5f, nk.getHeadYaw(), 0.001f);
        assertEquals(1.25f, nk.getPosition().x, 0.001f);
        assertEquals(64.0f, nk.getPosition().y, 0.001f);
        assertEquals(-3.5f, nk.getPosition().z, 0.001f);
        assertEquals(0.25d, nk.getMotion().x, 0.001d);
        assertEquals(-0.5d, nk.getMotion().y, 0.001d);
        assertTrue(nk.getInputData().contains(cn.nukkit.network.protocol.types.AuthInputAction.UP));
        assertTrue(nk.getInputData().contains(cn.nukkit.network.protocol.types.AuthInputAction.JUMPING));
        assertEquals(cn.nukkit.network.protocol.types.InputMode.TOUCH, nk.getInputMode());
        assertEquals(cn.nukkit.network.protocol.types.ClientPlayMode.NORMAL, nk.getPlayMode());
        assertEquals(cn.nukkit.network.protocol.types.AuthInteractionModel.CROSSHAIR, nk.getInteractionModel());
        assertEquals(123L, nk.getTick());
        assertEquals(0.1f, nk.getDelta().x, 0.001f);
        assertEquals(0.2f, nk.getDelta().y, 0.001f);
        assertEquals(0.3f, nk.getDelta().z, 0.001f);

        if (protocol >= ProtocolInfo.v1_19_70) {
            assertNotNull(nk.getAnalogMoveVector());
            assertEquals(0.6f, nk.getAnalogMoveVector().x, 0.001f);
            assertEquals(-0.4f, nk.getAnalogMoveVector().y, 0.001f);
        }
        if (protocol >= ProtocolInfo.v1_21_40) {
            assertNotNull(nk.getInteractRotation());
            assertEquals(5.0f, nk.getInteractRotation().x, 0.001f);
            assertEquals(6.0f, nk.getInteractRotation().y, 0.001f);
            assertNotNull(nk.getCameraOrientation());
            assertEquals(1.0f, nk.getCameraOrientation().y, 0.001f);
        }
        if (protocol >= ProtocolInfo.v1_21_50) {
            assertNotNull(nk.getRawMoveVector());
            assertEquals(-0.25f, nk.getRawMoveVector().x, 0.001f);
            assertEquals(0.75f, nk.getRawMoveVector().y, 0.001f);
        }
    }

    // ==================== SyncEntityPropertyPacket ====================

    @ParameterizedTest(name = "SyncEntityPropertyPacket v{0}")
    @MethodSource("versionsFrom486")
    void syncEntityProperty(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket();
        cb.setData(NbtMap.builder()
                .putString("name", "minecraft:zombie")
                .putInt("health", 20)
                .build());

        SyncEntityPropertyPacket nk = crossEncode(cb, SyncEntityPropertyPacket::new, protocol);

        assertNotNull(nk.getData());
        assertEquals("minecraft:zombie", nk.getData().getString("name"));
        assertEquals(20, nk.getData().getInt("health"));
    }

    // ==================== ItemStackRequestPacket ====================

    @ParameterizedTest(name = "ItemStackRequestPacket v{0}")
    @MethodSource("versionsFrom554")
    void itemStackRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket();
        cb.getRequests().add(new org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest(
                42,
                new org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction[]{
                        new org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CreateAction(3)
                },
                new String[]{"rename-me"},
                org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.TextProcessingEventOrigin.ANVIL_TEXT
        ));

        ItemStackRequestPacket nk = crossEncode(cb, ItemStackRequestPacket::new, protocol);

        assertEquals(1, nk.getRequests().size());
        var request = nk.getRequests().get(0);
        assertEquals(42, request.getRequestId());
        assertEquals(1, request.getActions().length);
        assertInstanceOf(cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CreateAction.class, request.getActions()[0]);
        assertEquals(3, ((cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CreateAction) request.getActions()[0]).getSlot());
        assertArrayEquals(new String[]{"rename-me"}, request.getFilterStrings());
        assertEquals(cn.nukkit.network.protocol.types.inventory.itemstack.request.TextProcessingEventOrigin.ANVIL_TEXT,
                request.getTextProcessingEventOrigin());
    }

    // ==================== LoginPacket ====================

    @ParameterizedTest(name = "LoginPacket v{0}")
    @MethodSource("versionsFrom594")
    void loginPacket(int protocol) {
        UUID clientUuid = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        byte[] skinBytes = new byte[64 * 32 * 4];

        var cb = new org.cloudburstmc.protocol.bedrock.packet.LoginPacket();
        cb.setProtocolVersion(protocol);
        cb.setAuthPayload(new org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload(
                List.of(fakeJwt("{\"extraData\":{\"displayName\":\"TestUser\",\"identity\":\"" + clientUuid + "\"}}")),
                org.cloudburstmc.protocol.bedrock.data.auth.AuthType.FULL
        ));
        cb.setClientJwt(fakeJwt("{\"ClientRandomId\":12345,\"SkinId\":\"custom-skin\",\"SkinData\":\""
                + Base64.getEncoder().encodeToString(skinBytes) + "\"}"));

        LoginPacket nk = crossEncode(cb, LoginPacket::new, protocol);

        assertEquals(protocol, nk.getProtocol());
        assertEquals("TestUser", nk.username);
        assertEquals(clientUuid, nk.clientUUID);
        assertEquals(12345L, nk.clientId);
        assertNotNull(nk.skin);
        assertEquals("custom-skin", nk.skin.getSkinId());
        assertEquals(64, nk.skin.getSkinData().width);
        assertEquals(32, nk.skin.getSkinData().height);
        assertEquals(skinBytes.length, nk.skin.getSkinData().data.length);
    }

    // ==================== PlayerSkinPacket ====================

    @ParameterizedTest(name = "PlayerSkinPacket v{0}")
    @MethodSource("versionsFrom388")
    void playerSkinPacket(int protocol) {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        byte[] skinBytes = new byte[64 * 32 * 4];

        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket();
        cb.setUuid(uuid);
        cb.setNewSkinName("newSkin");
        cb.setOldSkinName("oldSkin");
        cb.setTrustedSkin(true);
        cb.setSkin(org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin.builder()
                .skinId("test_skin_id")
                .skinResourcePatch("{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}")
                .skinData(org.cloudburstmc.protocol.bedrock.data.skin.ImageData.of(skinBytes))
                .capeData(org.cloudburstmc.protocol.bedrock.data.skin.ImageData.EMPTY)
                .geometryData("")
                .premium(false)
                .persona(false)
                .capeOnClassic(false)
                .build());

        PlayerSkinPacket nk = crossEncode(cb, PlayerSkinPacket::new, protocol);

        assertEquals(uuid, nk.uuid);
        assertEquals("newSkin", nk.newSkinName);
        assertEquals("oldSkin", nk.oldSkinName);
        assertNotNull(nk.skin);
        assertEquals("test_skin_id", nk.skin.getSkinId());
        assertEquals(64, nk.skin.getSkinData().width);
        assertEquals(32, nk.skin.getSkinData().height);
        assertTrue(nk.skin.isTrusted());
    }

    // ==================== AddVolumeEntityPacket ====================

    @ParameterizedTest(name = "AddVolumeEntityPacket v{0}")
    @MethodSource("versionsFrom486")
    void addVolumeEntityPacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AddVolumeEntityPacket();
        cb.setId(100);
        cb.setData(NbtMap.builder().putString("name", "test_volume").build());
        cb.setIdentifier("custom:volume");
        cb.setInstanceName("test_instance");
        cb.setEngineVersion("1.20.0");
        cb.setMinBounds(Vector3i.from(0, 1, 2));
        cb.setMaxBounds(Vector3i.from(3, 4, 5));
        cb.setDimension(1);

        AddVolumeEntityPacket nk = crossEncode(cb, AddVolumeEntityPacket::new, protocol);

        assertEquals(100L, nk.getId());
        assertNotNull(nk.getData());
        assertEquals("test_volume", nk.getData().getString("name"));
        assertEquals("custom:volume", nk.getIdentifier());
        assertEquals("test_instance", nk.getInstanceName());
        assertEquals("1.20.0", nk.getEngineVersion());
    }

    // ==================== CameraPresetsPacket ====================

    @ParameterizedTest(name = "CameraPresetsPacket v{0}")
    @MethodSource("versionsFrom818")
    void cameraPresetsPacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket();
        cb.getPresets().add(org.cloudburstmc.protocol.bedrock.data.camera.CameraPreset.builder()
                .identifier("minecraft:custom_cam")
                .parentPreset("minecraft:free")
                .pos(Vector3f.from(10.0f, 20.0f, 30.0f))
                .pitch(45.0f)
                .yaw(90.0f)
                .playEffect(org.cloudburstmc.protocol.common.util.OptionalBoolean.of(true))
                .build());

        CameraPresetsPacket nk = crossEncode(cb, CameraPresetsPacket::new, protocol);

        assertEquals(1, nk.getPresets().size());
        var preset = nk.getPresets().get(0);
        assertEquals("minecraft:custom_cam", preset.getIdentifier());
        assertEquals("minecraft:free", preset.getParentPreset());
        assertNotNull(preset.getPos());
        assertEquals(10.0f, preset.getPos().x, 0.001f);
        assertEquals(20.0f, preset.getPos().y, 0.001f);
        assertEquals(30.0f, preset.getPos().z, 0.001f);
        assertEquals(45.0f, preset.getPitch(), 0.001f);
        assertEquals(90.0f, preset.getYaw(), 0.001f);
        assertTrue(preset.getPlayEffect().isPresent());
        assertTrue(preset.getPlayEffect().getAsBoolean());
    }

    // ==================== CameraInstructionPacket ====================

    @ParameterizedTest(name = "CameraInstructionPacket v{0}")
    @MethodSource("versionsFrom630")
    void cameraInstructionPacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket();
        cb.setClear(org.cloudburstmc.protocol.common.util.OptionalBoolean.of(true));

        CameraInstructionPacket nk = crossEncode(cb, CameraInstructionPacket::new, protocol);

        assertTrue(nk.getClear().isPresent());
        assertTrue(nk.getClear().getAsBoolean());
    }

    // ==================== CameraAimAssistActorPriorityPacket ====================

    @ParameterizedTest(name = "CameraAimAssistActorPriorityPacket v{0}")
    @MethodSource("versionsFrom924")
    void cameraAimAssistActorPriorityPacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistActorPriorityPacket();
        cb.setPriorityData(List.of(
                new org.cloudburstmc.protocol.bedrock.data.camera.AimAssistActorPriorityData(0, 1, 2, 100),
                new org.cloudburstmc.protocol.bedrock.data.camera.AimAssistActorPriorityData(1, 0, 3, 50)
        ));

        CameraAimAssistActorPriorityPacket nk = crossEncode(cb, CameraAimAssistActorPriorityPacket::new, protocol);

        assertEquals(2, nk.priorities.size());
        assertEquals(0, nk.priorities.get(0).getPresetIndex());
        assertEquals(1, nk.priorities.get(0).getCategoryIndex());
        assertEquals(2, nk.priorities.get(0).getActorIndex());
        assertEquals(100, nk.priorities.get(0).getPriorityValue());
        assertEquals(1, nk.priorities.get(1).getPresetIndex());
        assertEquals(50, nk.priorities.get(1).getPriorityValue());
    }

    // ==================== CameraSplinePacket ====================

    @ParameterizedTest(name = "CameraSplinePacket v{0}")
    @MethodSource("versionsFrom924")
    void cameraSplinePacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CameraSplinePacket();
        cb.setSplines(List.of(new org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineDefinition(
                "spline1",
                new org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction(
                        5.0f,
                        org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineType.CATMULL_ROM,
                        List.of(Vector3f.from(0.0f, 0.0f, 0.0f), Vector3f.from(10.0f, 20.0f, 30.0f)),
                        List.of(new org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction.SplineProgressOption(
                                0.5f, 1.0f, org.cloudburstmc.protocol.bedrock.data.camera.CameraEase.EASE_IN_OUT_SINE
                        )),
                        List.of(new org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction.SplineRotationOption(
                                Vector3f.from(45.0f, 90.0f, 0.0f), 2.0f, org.cloudburstmc.protocol.bedrock.data.camera.CameraEase.LINEAR
                        ))
                )
        )));

        CameraSplinePacket nk = crossEncode(cb, CameraSplinePacket::new, protocol);

        assertEquals(1, nk.splines.size());
        var spline = nk.splines.get(0);
        assertEquals("spline1", spline.getName());
        assertEquals(5.0f, spline.getInstruction().getTotalTime(), 0.001f);
        assertEquals(cn.nukkit.network.protocol.types.camera.CameraSplineType.CATMULL_ROM, spline.getInstruction().getType());
        assertEquals(2, spline.getInstruction().getCurve().size());
        assertEquals(10.0f, spline.getInstruction().getCurve().get(1).x, 0.001f);
        assertEquals(1, spline.getInstruction().getProgressKeyFrames().size());
        assertEquals(0.5f, spline.getInstruction().getProgressKeyFrames().get(0).getValue(), 0.001f);
        assertEquals(cn.nukkit.network.protocol.types.camera.CameraEase.EASE_IN_OUT_SINE,
                spline.getInstruction().getProgressKeyFrames().get(0).getEasingFunc());
        assertEquals(1, spline.getInstruction().getRotationOption().size());
        assertEquals(2.0f, spline.getInstruction().getRotationOption().get(0).getKeyFrameTimes(), 0.001f);
    }

    // ==================== VoxelShapesPacket ====================

    @ParameterizedTest(name = "VoxelShapesPacket v{0}")
    @MethodSource("versionsFrom924")
    void voxelShapesPacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket();
        cb.setShapes(List.of(new org.cloudburstmc.protocol.bedrock.data.SerializableVoxelShape(
                new org.cloudburstmc.protocol.bedrock.data.SerializableVoxelShape.SerializableCells(
                        (short) 2, (short) 3, (short) 2, List.of((short) 1, (short) 0, (short) 1)
                ),
                List.of(0.0f, 0.5f, 1.0f),
                List.of(0.0f, 1.0f, 2.0f, 3.0f),
                List.of(0.0f, 0.5f, 1.0f)
        )));
        cb.setNameMap(java.util.Map.of("minecraft:stone", 1, "minecraft:dirt", 2));

        VoxelShapesPacket nk = crossEncode(cb, VoxelShapesPacket::new, protocol);

        assertEquals(1, nk.shapes.size());
        var shape = nk.shapes.get(0);
        assertEquals(2, shape.getCells().getXSize());
        assertEquals(3, shape.getCells().getYSize());
        assertEquals(2, shape.getCells().getZSize());
        assertEquals(3, shape.getCells().getStorage().size());
        assertEquals(3, shape.getXCoordinates().size());
        assertEquals(4, shape.getYCoordinates().size());
        assertEquals(3, shape.getZCoordinates().size());
        assertEquals(2, nk.nameMap.size());
        assertEquals(1, nk.nameMap.get("minecraft:stone"));
        assertEquals(2, nk.nameMap.get("minecraft:dirt"));
    }

    private static <T> T readField(Object instance, String fieldName, Class<T> type) {
        try {
            var field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(instance));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read field " + fieldName, e);
        }
    }

    private static String fakeJwt(String payloadJson) {
        return encodeBase64("{}") + "." + encodeBase64(payloadJson) + ".signature";
    }

    private static String encodeBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== v944 Packets ====================

    static Stream<Arguments> versionsFrom944() {
        return filteredVersions(ProtocolInfo.v1_26_10);
    }

    // ==================== PartyChangedPacket ====================

    @ParameterizedTest(name = "PartyChangedPacket decode v{0}")
    @MethodSource("versionsFrom944")
    void partyChangedDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PartyChangedPacket();
        cb.setPartyId("test-party-456");

        PartyChangedPacket nk = crossEncode(cb, PartyChangedPacket::new, protocol);

        assertEquals("test-party-456", nk.partyId);
    }

    // ==================== ServerboundDataDrivenScreenClosedPacket ====================

    @ParameterizedTest(name = "ServerboundDataDrivenScreenClosedPacket decode v{0}")
    @MethodSource("versionsFrom944")
    void serverboundDataDrivenScreenClosedDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket();
        cb.setFormId(99);
        cb.setCloseReason(org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket.CloseReason.CLIENT_CANCELED);

        ServerboundDataDrivenScreenClosedPacket nk = crossEncode(cb, ServerboundDataDrivenScreenClosedPacket::new, protocol);

        assertEquals(99, nk.formId);
        assertEquals(cn.nukkit.network.protocol.ServerboundDataDrivenScreenClosedPacket.CloseReason.CLIENT_CANCELED, nk.closeReason);
    }

    @ParameterizedTest(name = "ServerboundDataDrivenScreenClosedPacket INVALID_FORM decode v{0}")
    @MethodSource("versionsFrom944")
    void serverboundDataDrivenScreenClosedInvalidFormDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket();
        cb.setFormId(50);
        cb.setCloseReason(org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket.CloseReason.INVALID_FORM);

        ServerboundDataDrivenScreenClosedPacket nk = crossEncode(cb, ServerboundDataDrivenScreenClosedPacket::new, protocol);

        assertEquals(50, nk.formId);
        assertEquals(cn.nukkit.network.protocol.ServerboundDataDrivenScreenClosedPacket.CloseReason.INVALID_FORM, nk.closeReason);
    }

    // ==================== LocatorBarPacket ====================

    @ParameterizedTest(name = "LocatorBarPacket decode v{0}")
    @MethodSource("versionsFrom944")
    void locatorBarDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket();

        var cbWaypoint = new org.cloudburstmc.protocol.bedrock.data.LocatorBarWaypoint();
        cbWaypoint.setUpdateFlag(2);
        cbWaypoint.setVisible(true);
        cbWaypoint.setWorldPosition(new org.cloudburstmc.protocol.bedrock.data.LocatorBarWaypoint.WorldPosition(
                org.cloudburstmc.math.vector.Vector3f.from(50.0f, 32.0f, 100.0f), 1));
        cbWaypoint.setTextureId(10);
        cbWaypoint.setColor(java.awt.Color.BLUE);
        cbWaypoint.setClientPositionAuthority(true);
        cbWaypoint.setEntityUniqueId(12345L);

        var payload = new org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Payload(
                org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Action.UPDATE,
                UUID.fromString("87654321-4321-4321-4321-cba987654321"),
                cbWaypoint);

        cb.getWaypoints().add(payload);

        LocatorBarPacket nk = crossEncode(cb, LocatorBarPacket::new, protocol);

        assertEquals(1, nk.waypoints.size());
        var nkPayload = nk.waypoints.get(0);
        assertEquals(cn.nukkit.network.protocol.LocatorBarPacket.Action.UPDATE, nkPayload.actionFlag);
        assertEquals(UUID.fromString("87654321-4321-4321-4321-cba987654321"), nkPayload.groupHandle);
        assertEquals(2, nkPayload.waypoint.updateFlag);
        assertTrue(nkPayload.waypoint.visible);
        assertEquals(50.0f, nkPayload.waypoint.worldPosition.position.getX(), 0.001f);
        assertEquals(32.0f, nkPayload.waypoint.worldPosition.position.getY(), 0.001f);
        assertEquals(100.0f, nkPayload.waypoint.worldPosition.position.getZ(), 0.001f);
        assertEquals(1, nkPayload.waypoint.worldPosition.dimension);
        assertEquals(10, nkPayload.waypoint.textureId);
        assertEquals(java.awt.Color.BLUE.getRGB(), nkPayload.waypoint.color.getRGB());
        assertTrue(nkPayload.waypoint.clientPositionAuthority);
        assertEquals(12345L, (long) nkPayload.waypoint.entityUniqueId);
    }

    // ==================== SyncWorldClocksPacket ====================

    @ParameterizedTest(name = "SyncWorldClocksPacket SyncState decode v{0}")
    @MethodSource("versionsFrom944")
    void syncWorldClocksSyncStateDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SyncWorldClocksPacket();

        var clockData = new java.util.ArrayList<org.cloudburstmc.protocol.bedrock.data.clock.SyncWorldClockStateData>();
        clockData.add(new org.cloudburstmc.protocol.bedrock.data.clock.SyncWorldClockStateData(1, 6000, false));
        clockData.add(new org.cloudburstmc.protocol.bedrock.data.clock.SyncWorldClockStateData(2, 12000, true));

        cb.setData(new org.cloudburstmc.protocol.bedrock.data.clock.SyncStateData(clockData));

        SyncWorldClocksPacket nk = crossEncode(cb, SyncWorldClocksPacket::new, protocol);

        assertNotNull(nk.data);
        assertTrue(nk.data instanceof cn.nukkit.network.protocol.types.clock.SyncStateData);
        var syncData = (cn.nukkit.network.protocol.types.clock.SyncStateData) nk.data;
        assertEquals(2, syncData.clockData.size());
        assertEquals(1, syncData.clockData.get(0).clockId);
        assertEquals(6000, syncData.clockData.get(0).time);
        assertFalse(syncData.clockData.get(0).paused);
        assertEquals(2, syncData.clockData.get(1).clockId);
        assertEquals(12000, syncData.clockData.get(1).time);
        assertTrue(syncData.clockData.get(1).paused);
    }

    // ==================== ClientboundAttributeLayerSyncPacket ====================

    @ParameterizedTest(name = "ClientboundAttributeLayerSyncPacket RemoveEnvAttrs decode v{0}")
    @MethodSource("versionsFrom944")
    void clientboundAttributeLayerSyncRemoveEnvAttrsDecode(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ClientboundAttributeLayerSyncPacket();

        cb.setData(new org.cloudburstmc.protocol.bedrock.data.attributelayer.RemoveEnvironmentAttributesData(
                "my_layer",
                0,
                List.of("attr_a", "attr_b")
        ));

        ClientboundAttributeLayerSyncPacket nk = crossEncode(cb, ClientboundAttributeLayerSyncPacket::new, protocol);

        assertNotNull(nk.data);
        assertTrue(nk.data instanceof cn.nukkit.network.protocol.types.attributelayer.RemoveEnvironmentAttributesData);
        var removeData = (cn.nukkit.network.protocol.types.attributelayer.RemoveEnvironmentAttributesData) nk.data;
        assertEquals("my_layer", removeData.layerName);
        assertEquals(0, removeData.dimension);
        assertEquals(2, removeData.attributes.size());
        assertEquals("attr_a", removeData.attributes.get(0));
        assertEquals("attr_b", removeData.attributes.get(1));
    }

}
