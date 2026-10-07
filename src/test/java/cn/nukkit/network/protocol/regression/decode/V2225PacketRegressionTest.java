package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.MockServer;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.DimensionDefinition;
import cn.nukkit.network.protocol.types.PassengerOfBlockArguments;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftReservedAction;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2225 (1.26.60) 线格式变更验证：皮肤移除 playFabId（移至 PlayerList 条目）、
 * AddEntity/AddPlayer 可选 PassengerOfBlockArguments、Animate/InventoryTransaction hand 字节、
 * LevelChunk clientBiomeUpdate、DimensionData 云层字段、UpdateSoundData 单事件化、
 * ItemStackRequest 新增 CraftReserved（紧凑 id 16）。
 * <p>
 * Wire-change validation for v2225 (1.26.60): skin drops playFabId (moved to the PlayerList
 * entry), optional PassengerOfBlockArguments on AddEntity/AddPlayer, Animate/InventoryTransaction
 * hand bytes, LevelChunk clientBiomeUpdate, DimensionData cloud fields, UpdateSoundData single
 * event, and the new CraftReserved ItemStackRequest action (compact id 16).
 * <p>
 * 注意：UpdateSoundData 不做 CB 交叉——CB v2225 codec 尚未跟进 .25(2211) 起的单事件化，
 * 此处以 protocol-docs（权威）为准做字节级断言。
 * <p>
 * Note: UpdateSoundData is not cross-checked against CB — CB's v2225 codec has not caught up with
 * the single-event format (since preview .25/2211); byte-level assertions follow protocol-docs
 * (authoritative).
 */
public class V2225PacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    private static final int V2225 = cn.nukkit.network.protocol.ProtocolInfo.v1_26_60;

    // ==================== AnimatePacket：尾部 hand 字节 ====================

    @Test
    void animatePacketHandByte() {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AnimatePacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action.SWING_ARM);
        cb.setRuntimeEntityId(7L);
        cb.setData(0f);
        cb.setHand(org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot.OFFHAND);

        AnimatePacket nk = crossEncode(cb, AnimatePacket::new, V2225);

        assertEquals(AnimatePacket.Action.SWING_ARM, nk.action);
        assertEquals(1, nk.hand, "off-hand must decode as 1");
    }

    @Test
    void animatePacketHandByteEncode() {
        var nk = new AnimatePacket();
        nk.protocol = V2225;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        nk.action = AnimatePacket.Action.SWING_ARM;
        nk.eid = 7;
        nk.data = 0f;
        nk.hand = 1;
        nk.encode();

        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.class);
        assertEquals(org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot.OFFHAND, cb.getHand());
    }

    // ==================== AddPlayerPacket：可选 PassengerOfBlockArguments ====================

    /**
     * AddPlayer 的 CB 交叉验证在 v2168+ 因 CB abilities helper WIP 被跳过，此处按
     * protocol-docs 做尾部结构化断言。passenger 段位于 deviceId/buildPlatform 之前，
     * 尾部布局 = present bool + 结构体(26B) + deviceId 空串(1B) + buildPlatform LInt(4B)。
     * <p>
     * CB cross-decode of AddPlayer is skipped for v2168+ (CB abilities helper WIP); assert
     * the tail structurally per protocol-docs. The passenger section sits before
     * deviceId/buildPlatform: tail = present bool + struct(26B) + empty deviceId(1B) +
     * buildPlatform LInt(4B).
     */
    @Test
    void addPlayerPassengerOfBlockTailBytes() {
        var nk = new cn.nukkit.network.protocol.AddPlayerPacket();
        nk.protocol = V2225;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        nk.uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        nk.username = "TailTest";
        nk.entityUniqueId = 1;
        nk.entityRuntimeId = 1;
        nk.x = 1f;
        nk.y = 64f;
        nk.z = 2f;
        nk.pitch = 0f;
        nk.yaw = 0f;
        nk.headYaw = 0f;
        nk.item = Item.AIR_ITEM;
        nk.metadata = new EntityMetadata();
        nk.deviceId = "";
        nk.buildPlatform = -1;
        nk.encode();
        byte[] absent = nk.getBuffer();
        // absent: present=0 + devId 空串 + LInt(-1)；present 字节之后不得再有结构体
        // absent: present=0 + empty devId + LInt(-1); no struct may follow the present byte
        int absentTailStart = absent.length - 6;
        assertEquals(0, absent[absentTailStart], "absent passenger data must write a single 0x00 present byte");
        assertEquals(0, absent[absentTailStart + 1], "deviceId encoded as empty string");

        nk.passengerOfBlockArguments = new PassengerOfBlockArguments();
        nk.passengerOfBlockArguments.blockPos = new BlockVector3(10, 64, -20);
        nk.passengerOfBlockArguments.offset = new Vector3f(0.5f, 0f, -0.5f);
        nk.passengerOfBlockArguments.rotation = 90f;
        nk.passengerOfBlockArguments.rotationLimit = 180f;
        nk.passengerOfBlockArguments.emoteType = PassengerOfBlockArguments.EmoteType.RIDING;
        nk.encode();
        byte[] present = nk.getBuffer();
        assertEquals(25, present.length - absent.length, "struct adds 25 bytes (blockPos 4 + offset 12 + floats 8 + emote 1)");
        // present 尾段 = bool(1) + blockPos zigzag varint×3(4) + offset(12) + rotation(4) + limit(4) + emote(1)
        BinaryStream tail = new BinaryStream(present);
        tail.setOffset(present.length - 26 - 6 + 1);
        assertTrue(tail.getBoolean(), "present byte must be true when data is set");
        PassengerOfBlockArguments read = PassengerOfBlockArguments.get(tail);
        assertEquals(new BlockVector3(10, 64, -20), read.blockPos);
        assertEquals(0.5f, read.offset.x, 0.0001f);
        assertEquals(90f, read.rotation, 0.0001f);
        assertEquals(180f, read.rotationLimit, 0.0001f);
        assertEquals(PassengerOfBlockArguments.EmoteType.RIDING, read.emoteType);
        assertEquals(0, tail.getByte(), "deviceId follows the struct as an empty string");
    }

    // ==================== LevelChunkPacket：clientBiomeUpdate ====================

    @Test
    void levelChunkClientBiomeUpdate() {
        var nk = new LevelChunkPacket();
        nk.protocol = V2225;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        nk.chunkX = 3;
        nk.chunkZ = 4;
        nk.dimension = 0;
        nk.subChunkCount = 1;
        nk.cacheEnabled = false;
        nk.data = new byte[]{9};
        nk.clientBiomeUpdate = true;
        nk.encode();

        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket.class);
        assertTrue(cb.isClientBiomeUpdate(), "v2225 must carry the trailing clientBiomeUpdate flag");
    }

    // ==================== InventoryTransactionPacket：hand 字节 ====================

    @Test
    void inventoryTransactionUseOnEntityHandByte() {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket();
        cb.setLegacyRequestId(-1);
        cb.setTransactionType(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.ITEM_USE_ON_ENTITY);
        cb.setRuntimeEntityId(55L);
        cb.setActionType(0);
        cb.setHotbarSlot(2);
        cb.setHand(org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot.OFFHAND);
        cb.setItemInHand(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.setPlayerPosition(org.cloudburstmc.math.vector.Vector3f.from(1.5f, 65f, 2.5f));
        cb.setClickPosition(org.cloudburstmc.math.vector.Vector3f.from(0.5f, 0.5f, 0.5f));

        InventoryTransactionPacket nk = crossEncode(cb, InventoryTransactionPacket::new, V2225);

        var data = (cn.nukkit.inventory.transaction.data.UseItemOnEntityData) nk.transactionData;
        assertNotNull(data);
        assertEquals(1, data.hand, "ITEM_USE_ON_ENTITY off-hand must decode as 1");
    }

    @Test
    void inventoryTransactionReleaseHandByte() {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket();
        cb.setLegacyRequestId(-1);
        cb.setTransactionType(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.ITEM_RELEASE);
        cb.setActionType(0);
        cb.setHotbarSlot(2);
        cb.setHand(org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot.MAINHAND);
        cb.setItemInHand(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.setHeadPosition(org.cloudburstmc.math.vector.Vector3f.from(0f, 0f, 0f));

        InventoryTransactionPacket nk = crossEncode(cb, InventoryTransactionPacket::new, V2225);

        var data = (cn.nukkit.inventory.transaction.data.ReleaseItemData) nk.transactionData;
        assertNotNull(data);
        assertEquals(0, data.hand, "ITEM_RELEASE main-hand must decode as 0");
    }

    // ==================== PlayerListPacket：playFabId 移至条目 ====================

    /**
     * 皮肤结构不再携带 playFabId（若仍在皮肤内，CB v2225 的 readSkin 会错位解析并抛出）；
     * playFabId 必须从条目 xuid 之后读回。
     * <p>
     * The skin struct no longer carries playFabId (it would desync CB v2225 readSkin and throw);
     * playFabId must be read back from the entry after xuid.
     */
    @Test
    void playerListPlayFabIdMovedToEntry() {
        var nk = new PlayerListPacket();
        nk.protocol = V2225;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        nk.type = PlayerListPacket.TYPE_ADD;
        var skin = new Skin();
        skin.setSkinId("test_skin_id");
        skin.setSkinData(new byte[64 * 32 * 4]);
        skin.setCapeData(new byte[0]);
        skin.setGeometryName("geometry.humanoid.custom");
        skin.setGeometryData(Skin.STEVE_GEOMETRY);
        skin.setSkinResourcePatch("{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}");
        skin.setTrusted(true);
        skin.setPlayFabId("testfab123");
        nk.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(UUID.fromString("12345678-1234-1234-1234-123456789abc"), 42, "TestPlayer", skin, "xuid")
        };
        nk.encode();

        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.class);
        assertEquals(1, cb.getEntries().size());
        assertEquals("testfab123", cb.getEntries().get(0).getSkin().getPlayFabId(),
                "playFabId must round-trip through the v2225 entry, not the skin struct");
    }

    // ==================== DimensionDataPacket：云层字段 ====================

    @Test
    void dimensionDataCloudFields() {
        var nk = new DimensionDataPacket();
        nk.protocol = V2225;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        nk.definitions.add(new DimensionDefinition("minecraft:test_dim", 320, -64, 0, 0,
                new UUID(0, 0), "plains", 192, false));
        nk.encode();

        var cb = crossDecode(nk, org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket.class);
        assertEquals(1, cb.getDefinitions().size());
        var def = cb.getDefinitions().get(0);
        assertNotNull(def);
        assertEquals("minecraft:test_dim", def.getId());
        assertEquals(192, def.getCloudHeight());
        assertFalse(def.isRenderClouds());
    }

    // ==================== ClientboundUpdateSoundDataPacket：单事件 ====================

    /**
     * v1_26_60 起仅写一个事件槽位（2193 前仍为 7 个）。长度含 reset() 写入的 2 字节包
     * 头 uvarint(348)。CB v2225 codec 尚未跟进单事件化，故按 protocol-docs 做字节级断言。
     * <p>
     * Only one event slot since v1_26_60 (still 7 before 2193). Lengths include the 2-byte
     * packet-id uvarint(348) written by reset(). CB's v2225 codec has not caught up with the
     * single-event layout, so assert bytes per protocol-docs.
     */
    @Test
    void updateSoundDataSingleEvent() {
        var nk = new ClientboundUpdateSoundDataPacket();
        nk.protocol = V2225;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        nk.serverSoundHandle = 99L;
        nk.volume = 0.5f;
        nk.encode();
        byte[] encoded = nk.getBuffer();
        // header(2) + handle(8) + tag uvarint(1) + float(4) => 15B；7 槽位旧格式是 2+8+7*5=45B
        assertEquals(15, encoded.length, "v2225 must encode exactly one sound event slot");
        assertEquals(1, encoded[10], "SOUND_SET_VOLUME tag after header+handle");

        var decoded = new ClientboundUpdateSoundDataPacket();
        decoded.protocol = V2225;
        decoded.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        decoded.setBuffer(encoded);
        decoded.getUnsignedVarInt(); // 跳过 reset() 写入的包头 / skip the header written by reset()
        decoded.decode();
        assertEquals(0.5f, decoded.volume, 0.0001f);

        var old = new ClientboundUpdateSoundDataPacket();
        old.protocol = cn.nukkit.network.protocol.ProtocolInfo.v1_26_50;
        old.gameVersion = cn.nukkit.GameVersion.V1_26_50;
        old.serverSoundHandle = 99L;
        old.volume = 0.5f;
        old.encode();
        assertEquals(45, old.getBuffer().length, "v2193 keeps the 7-slot layout");
    }

    // ==================== ItemStackRequest：CraftReserved（紧凑 16） ====================

    @Test
    void itemStackRequestCraftReservedAction() {
        BinaryStream stream = new BinaryStream();
        stream.putUnsignedVarInt(1); // requests array count
        stream.putVarInt(5); // requestId
        stream.putUnsignedVarInt(1); // action count
        stream.putUnsignedVarInt(16); // compact primary id: CRAFT_RESERVED
        stream.putByte((byte) 18); // duplicate type byte (full-enum 18), discarded
        stream.putString("minecraft:disc_press_test");
        stream.putByte((byte) 2); // numCrafts
        stream.putUnsignedVarInt(0); // filteredStrings
        stream.putLInt(-1); // origin

        var packet = new ItemStackRequestPacket();
        packet.protocol = V2225;
        packet.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        packet.setBuffer(stream.getBuffer());
        packet.decode();

        assertEquals(1, packet.getRequests().size());
        ItemStackRequest request = packet.getRequests().get(0);
        assertEquals(1, request.getActions().length);
        var action = assertInstanceOf(CraftReservedAction.class, request.getActions()[0]);
        assertEquals("minecraft:disc_press_test", action.getReservedId());
        assertEquals(2, action.getNumCrafts());
    }

    /**
     * 同一紧凑 id 16 在 v1_26_50 仍映射 CRAFT_NON_IMPLEMENTED（id 未让位）。
     * <p>
     * The same compact id 16 still maps to CRAFT_NON_IMPLEMENTED on v1_26_50.
     */
    @Test
    void itemStackRequestCompactId16StillNonImplementedOnV2193() {
        assertEquals(cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType.CRAFT_NON_IMPLEMENTED_DEPRECATED,
                cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType.fromId(16, cn.nukkit.GameVersion.V1_26_50));
        assertEquals(cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType.CRAFT_RESERVED,
                cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType.fromId(16, cn.nukkit.GameVersion.V1_26_60));
    }

    // ==================== SetPassengerOfBlockPacket（357）往返 ====================

    @Test
    void setPassengerOfBlockRoundTrip() {
        var nk = new SetPassengerOfBlockPacket();
        nk.protocol = V2225;
        nk.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        nk.passengerUniqueId = 123L;
        nk.passengerOfBlockArguments = new PassengerOfBlockArguments();
        nk.passengerOfBlockArguments.blockPos = new BlockVector3(-10, 32, 40);
        nk.passengerOfBlockArguments.offset = new Vector3f(0.25f, 0.5f, 0.75f);
        nk.passengerOfBlockArguments.rotation = 45f;
        nk.passengerOfBlockArguments.rotationLimit = 270f;
        nk.passengerOfBlockArguments.emoteType = PassengerOfBlockArguments.EmoteType.LAYING;
        nk.encode();

        var decoded = new SetPassengerOfBlockPacket();
        decoded.protocol = V2225;
        decoded.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        decoded.setBuffer(nk.getBuffer());
        decoded.getUnsignedVarInt(); // 跳过 reset() 写入的包头 / skip the header written by reset()
        decoded.decode();

        assertEquals(123L, decoded.passengerUniqueId);
        assertNotNull(decoded.passengerOfBlockArguments);
        assertEquals(-10, decoded.passengerOfBlockArguments.blockPos.x);
        assertEquals(32, decoded.passengerOfBlockArguments.blockPos.y);
        assertEquals(40, decoded.passengerOfBlockArguments.blockPos.z);
        assertEquals(0.25f, decoded.passengerOfBlockArguments.offset.x, 0.0001f);
        assertEquals(45f, decoded.passengerOfBlockArguments.rotation, 0.0001f);
        assertEquals(270f, decoded.passengerOfBlockArguments.rotationLimit, 0.0001f);
        assertEquals(PassengerOfBlockArguments.EmoteType.LAYING, decoded.passengerOfBlockArguments.emoteType);

        nk.passengerOfBlockArguments = null;
        nk.encode();
        var dismount = new SetPassengerOfBlockPacket();
        dismount.protocol = V2225;
        dismount.gameVersion = cn.nukkit.GameVersion.byProtocol(V2225, false);
        dismount.setBuffer(nk.getBuffer());
        dismount.getUnsignedVarInt(); // 跳过 reset() 写入的包头 / skip the header written by reset()
        dismount.decode();
        assertNull(dismount.passengerOfBlockArguments);
    }
}
