package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.*;
import cn.nukkit.network.protocol.types.datastore.DataStoreChange;
import cn.nukkit.network.protocol.types.datastore.DataStoreRemoval;
import cn.nukkit.network.protocol.types.datastore.DataStoreUpdate;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2259（1.26.70）线格式验证：NetworkSettings SCTP 配置、ResourcePackStack EditorPackTypes、
 * PlaySound 新增可选字段、Text 排队标志、CommandBlock/CommandOutput 原文消息、
 * PassengerOfBlockArguments BlockType/PackUUID、DataStore 变量名布局，以及新增的
 * Chat/虚拟容器/在线音乐/盔甲可见性数据包。
 * <p>
 * Wire-format validation for v2259 (1.26.70): NetworkSettings SCTP profile,
 * ResourcePackStack EditorPackTypes, new optional PlaySound fields, Text queue flag,
 * CommandBlock/CommandOutput raw message, PassengerOfBlockArguments BlockType/PackUUID,
 * the DataStore variable-name layout, and the new Chat / virtual container / online music /
 * armor visibility packets.
 * <p>
 * 注意：CloudburstMC/Protocol 尚无 v2259 codec，因此这里按 protocol-docs（2259）做字节级断言，
 * 不做 CB 交叉验证。
 * <p>
 * Note: CloudburstMC/Protocol has no v2259 codec yet, so these are byte-level assertions
 * against protocol-docs (2259) instead of CB cross-checks.
 */
public class V2259PacketRegressionTest {

    private static final int V2225 = ProtocolInfo.v1_26_60;
    private static final int V2259 = ProtocolInfo.v1_26_70;

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    private static GameVersion version(int protocol) {
        return GameVersion.byProtocol(protocol, false);
    }

    private static <T extends DataPacket> T roundTrip(T packet, int protocol, Supplier<T> factory) {
        packet.protocol = protocol;
        packet.gameVersion = version(protocol);
        packet.encode();
        byte[] raw = packet.getBuffer();
        BinaryStream header = new BinaryStream(raw);
        header.getUnsignedVarInt();
        int offset = header.getOffset();

        T decoded = factory.get();
        decoded.protocol = protocol;
        decoded.gameVersion = version(protocol);
        decoded.setBuffer(raw);
        decoded.setOffset(offset);
        decoded.decode();
        return decoded;
    }

    private static BinaryStream body(DataPacket packet) {
        byte[] raw = packet.getBuffer();
        BinaryStream stream = new BinaryStream(raw);
        stream.getUnsignedVarInt();
        return stream;
    }

    // ==================== NetworkSettings：SCTP Recovery Profile ====================

    @Test
    void networkSettingsWritesSctpProfileByte() {
        NetworkSettingsPacket packet = new NetworkSettingsPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.compressionThreshold = 1;
        packet.compressionAlgorithm = PacketCompressionAlgorithm.ZLIB;
        packet.encode();

        byte[] buffer = packet.getBuffer();
        assertEquals(0, buffer[buffer.length - 1], "SCTP recovery profile must default to Default(0)");
        assertEquals(11, body(packet).getBuffer().length - body(packet).getOffset(),
                "2259 body is 11 bytes: threshold(2)+algorithm(2)+bool+byte+float+profile");
    }

    @Test
    void networkSettingsOmitsSctpProfileBefore2259() {
        NetworkSettingsPacket packet = new NetworkSettingsPacket();
        packet.protocol = V2225;
        packet.gameVersion = version(V2225);
        packet.compressionAlgorithm = PacketCompressionAlgorithm.ZLIB;
        packet.encode();
        assertEquals(10, body(packet).getBuffer().length - body(packet).getOffset(),
                "2225 body has no SCTP profile byte");
    }

    // ==================== ResourcePackStack：Editor Pack Types ====================

    @Test
    void resourcePackStackUsesEditorPackTypeFlags() {
        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.protocol = V2259;
        packet.hasEditorPacks = false;
        packet.editorPackTypes = 0x2;
        packet.encode();
        byte[] buffer = packet.getBuffer();
        assertEquals(2, buffer[buffer.length - 1], "EditorPackTypes must carry 0x2 (Annotation)");

        ResourcePackStackPacket editor = new ResourcePackStackPacket();
        editor.protocol = V2259;
        editor.hasEditorPacks = true;
        editor.encode();
        byte[] editorBuffer = editor.getBuffer();
        assertEquals(1, editorBuffer[editorBuffer.length - 1],
                "hasEditorPacks must map to EditorPackType.Core (0x1)");
    }

    @Test
    void resourcePackStackKeepsBooleanBefore2259() {
        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.protocol = V2225;
        packet.hasEditorPacks = true;
        packet.editorPackTypes = 0x2;
        packet.encode();
        byte[] buffer = packet.getBuffer();
        assertEquals(1, buffer[buffer.length - 1], "2225 still writes the Include Editor Packs boolean");
    }

    // ==================== PlaySound：Start Delay / Attach Options ====================

    @Test
    void playSoundWritesOptionalStartDelayAndAttachOptions() {
        PlaySoundPacket packet = new PlaySoundPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.name = "mob.frostbite.convert";
        packet.volume = 1f;
        packet.pitch = 1f;
        packet.startDelaySeconds = 1.5f;
        packet.attachOptions = new SoundAttachOptions(7);
        packet.encode();

        byte[] buffer = packet.getBuffer();
        int length = buffer.length;
        // 尾部：[start 存在=1][float 1.5f = 00 00 C0 3F][attach 存在=1][varuint 7]
        assertEquals(7, buffer[length - 1], "attach runtime id 7 is a one-byte varuint");
        assertEquals(1, buffer[length - 2], "attach options presence marker");
        assertEquals(0x3F, buffer[length - 3] & 0xff);
        assertEquals(0xC0, buffer[length - 4] & 0xff);
        assertEquals(0x00, buffer[length - 5] & 0xff);
        assertEquals(0x00, buffer[length - 6] & 0xff);
        assertEquals(1, buffer[length - 7], "start delay presence marker");
    }

    @Test
    void playSoundOmitsNewOptionalsBefore2259() {
        PlaySoundPacket packet = new PlaySoundPacket();
        packet.protocol = V2225;
        packet.gameVersion = version(V2225);
        packet.name = "mob.frostbite.convert";
        packet.volume = 1f;
        packet.pitch = 1f;
        packet.startDelaySeconds = 1.5f;
        packet.attachOptions = new SoundAttachOptions(7);
        packet.encode();

        byte[] buffer = packet.getBuffer();
        assertEquals(0, buffer[buffer.length - 1],
                "2225 must end after Playback Position Seconds (absent presence byte)");
    }

    // ==================== Text：Queue? ====================

    @Test
    void textQueueFlagRoundTripsOn2259() {
        TextPacket packet = new TextPacket();
        packet.type = TextPacket.TYPE_JUKEBOX_POPUP;
        packet.message = "record.flurry";
        packet.parameters = new String[0];
        packet.queue = true;

        TextPacket decoded = roundTrip(packet, V2259, TextPacket::new);
        assertEquals(TextPacket.TYPE_JUKEBOX_POPUP, decoded.type);
        assertEquals("record.flurry", decoded.message);
        assertTrue(decoded.queue, "Queue? must survive the 2259 round trip");
    }

    @Test
    void textHasNoQueueByteBefore2259() {
        TextPacket packet = new TextPacket();
        packet.protocol = V2225;
        packet.gameVersion = version(V2225);
        packet.type = TextPacket.TYPE_POPUP;
        packet.message = "m";
        packet.queue = true;
        packet.encode();

        TextPacket decoded = new TextPacket();
        decoded.protocol = V2225;
        decoded.gameVersion = version(V2225);
        BinaryStream stream = body(packet);
        decoded.setBuffer(stream.getBuffer());
        decoded.setOffset(stream.getOffset());
        decoded.decode();
        assertFalse(decoded.queue, "2225 has no Queue? field");
    }

    // ==================== CommandBlockUpdate：Last Output Is Raw Message ====================

    @Test
    void commandBlockUpdateRawMessageFlagRoundTrips() {
        CommandBlockUpdatePacket packet = new CommandBlockUpdatePacket();
        packet.isBlock = true;
        packet.x = 1;
        packet.y = 2;
        packet.z = 3;
        packet.commandBlockMode = 1;
        packet.command = "say hi";
        packet.lastOutput = "{\"rawtext\":[]}";
        packet.name = "";
        packet.executingOnFirstTick = true;
        packet.lastOutputIsRawMessage = true;

        CommandBlockUpdatePacket decoded = roundTrip(packet, V2259, CommandBlockUpdatePacket::new);
        assertTrue(decoded.lastOutputIsRawMessage);
        assertEquals("{\"rawtext\":[]}", decoded.lastOutput);
    }

    @Test
    void commandBlockUpdateHasNoRawMessageFlagBefore2259() {
        CommandBlockUpdatePacket packet = new CommandBlockUpdatePacket();
        packet.protocol = V2225;
        packet.gameVersion = version(V2225);
        packet.isBlock = false;
        packet.minecartEid = 5;
        packet.command = "";
        packet.lastOutput = "";
        packet.name = "";
        packet.executingOnFirstTick = true;
        packet.encode();

        CommandBlockUpdatePacket decoded = roundTrip(packet, V2225, CommandBlockUpdatePacket::new);
        assertFalse(decoded.lastOutputIsRawMessage);
    }

    // ==================== Transfer：Server Transfer Settings ====================

    @Test
    void transferWritesOptionalServerTransferSettings() {
        TransferPacket packet = new TransferPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.address = "example.com";
        packet.port = 19132;
        packet.serverTransferSettings = new TransferPlayerUIOptions();
        packet.serverTransferSettings.titleText = "Transferring";
        packet.encode();

        BinaryStream stream = body(packet);
        assertEquals("example.com", stream.getString());
        assertEquals(19132, stream.getLShort());
        stream.getBoolean(); // reloadWorld
        assertEquals(0, stream.getByte(), "gatherings configuration absent");
        assertEquals(1, stream.getByte(), "server transfer settings present");
        assertEquals(1, stream.getByte(), "TitleText present");
        assertEquals("Transferring", stream.getString());
        assertEquals(0, stream.getByte(), "BodyText absent");
        assertEquals(0, stream.getByte(), "TitleVisible absent");
        assertEquals(0, stream.getByte(), "BodyVisible absent");
        assertEquals(0, stream.getByte(), "CancelButtonText absent");
        assertEquals(0, stream.getByte(), "CancelButtonVisible absent");
        assertEquals(0, stream.getByte(), "CancelButtonDelaySeconds absent");
        assertEquals(0, stream.getBuffer().length - stream.getOffset());
    }

    // ==================== 环境属性：StringAttributeData ====================

    @Test
    void attributeLayerStringVariantRoundTripsOn2259() {
        cn.nukkit.network.protocol.types.attributelayer.EnvironmentAttributeData attribute =
                new cn.nukkit.network.protocol.types.attributelayer.EnvironmentAttributeData(
                        "test:name", null,
                        new cn.nukkit.network.protocol.types.attributelayer.StringAttributeData(
                                "value",
                                cn.nukkit.network.protocol.types.attributelayer.StringAttributeData.Operation.OVERRIDE),
                        null, 0, 0,
                        cn.nukkit.network.protocol.types.attributelayer.EnvironmentAttributeData.CameraEase.LINEAR);

        ClientboundAttributeLayerSyncPacket packet = new ClientboundAttributeLayerSyncPacket();
        packet.data = new cn.nukkit.network.protocol.types.attributelayer.UpdateEnvironmentAttributesData(
                "layer", 0, List.of(attribute));

        ClientboundAttributeLayerSyncPacket decoded = roundTrip(packet, V2259, ClientboundAttributeLayerSyncPacket::new);
        assertTrue(decoded.data instanceof cn.nukkit.network.protocol.types.attributelayer.UpdateEnvironmentAttributesData);
        var attrs = ((cn.nukkit.network.protocol.types.attributelayer.UpdateEnvironmentAttributesData) decoded.data).attributes;
        assertEquals(1, attrs.size());
        assertTrue(attrs.get(0).attribute instanceof cn.nukkit.network.protocol.types.attributelayer.StringAttributeData);
        assertEquals("value", ((cn.nukkit.network.protocol.types.attributelayer.StringAttributeData) attrs.get(0).attribute).value);
    }

    @Test
    void attributeLayerStringVariantIsRejectedBefore2259() {
        ClientboundAttributeLayerSyncPacket packet = new ClientboundAttributeLayerSyncPacket();
        packet.protocol = V2225;
        packet.gameVersion = version(V2225);
        packet.data = new cn.nukkit.network.protocol.types.attributelayer.UpdateEnvironmentAttributesData(
                "layer", 0,
                List.of(new cn.nukkit.network.protocol.types.attributelayer.EnvironmentAttributeData(
                        "test:name", null,
                        new cn.nukkit.network.protocol.types.attributelayer.StringAttributeData(
                                "value",
                                cn.nukkit.network.protocol.types.attributelayer.StringAttributeData.Operation.OVERRIDE),
                        null, 0, 0,
                        cn.nukkit.network.protocol.types.attributelayer.EnvironmentAttributeData.CameraEase.LINEAR)));
        assertThrows(IllegalArgumentException.class, packet::encode);
    }

    // ==================== CommandOutput：Raw Message ====================

    @Test
    void commandOutputWritesOptionalRawMessage() {
        CommandOutputPacket packet = new CommandOutputPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.commandOriginData = new CommandOriginData(CommandOriginData.Origin.PLAYER,
                UUID.fromString("12345678-1234-1234-1234-123456789abc"), "", null);
        packet.type = cn.nukkit.network.protocol.types.CommandOutputType.ALL_OUTPUT;
        packet.successCount = 1;
        packet.messages.add(new cn.nukkit.network.protocol.types.CommandOutputMessage(
                false, "commands.say.success", new String[0], "{}"));
        packet.encode();

        byte[] buffer = packet.getBuffer();
        // 尾部：rawMessage 存在(1) + 长度(2) + "{}" + DataSet 缺省(0)
        assertEquals(0, buffer[buffer.length - 1], "DataSet is absent at the tail");
        assertEquals('}', buffer[buffer.length - 2]);
        assertEquals('{', buffer[buffer.length - 3]);
        assertEquals(2, buffer[buffer.length - 4], "raw message byte length");
        assertEquals(1, buffer[buffer.length - 5], "raw message presence marker");
    }

    @Test
    void commandOutputOmitsRawMessageWhenNull() {
        CommandOutputPacket packet = new CommandOutputPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.commandOriginData = new CommandOriginData(CommandOriginData.Origin.PLAYER,
                UUID.fromString("12345678-1234-1234-1234-123456789abc"), "", null);
        packet.type = cn.nukkit.network.protocol.types.CommandOutputType.ALL_OUTPUT;
        packet.successCount = 1;
        packet.messages.add(new cn.nukkit.network.protocol.types.CommandOutputMessage("commands.say.success"));
        packet.encode();

        byte[] buffer = packet.getBuffer();
        assertEquals(0, buffer[buffer.length - 1], "absent DataSet");
        assertEquals(0, buffer[buffer.length - 2], "absent Raw Message presence marker");
    }

    // ==================== PassengerOfBlockArguments：BlockType / PackUUID ====================

    @Test
    void passengerOfBlockArgumentsCarryBlockTypeAndPackUuid() {
        UUID pack = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        PassengerOfBlockArguments args = new PassengerOfBlockArguments();
        args.blockPos = new BlockVector3(1, 2, 3);
        args.offset = new Vector3f();
        args.rotation = 0f;
        args.rotationLimit = 0f;
        args.emoteType = PassengerOfBlockArguments.EmoteType.LAYING;
        args.blockType = 42;
        args.packUuid = pack;

        SetPassengerOfBlockPacket packet = new SetPassengerOfBlockPacket();
        packet.passengerUniqueId = 9;
        packet.passengerOfBlockArguments = args;

        SetPassengerOfBlockPacket decoded = roundTrip(packet, V2259, SetPassengerOfBlockPacket::new);
        assertNotNull(decoded.passengerOfBlockArguments);
        assertEquals(42, decoded.passengerOfBlockArguments.blockType);
        assertEquals(pack, decoded.passengerOfBlockArguments.packUuid);
        assertEquals(PassengerOfBlockArguments.EmoteType.LAYING, decoded.passengerOfBlockArguments.emoteType);
    }

    @Test
    void passengerOfBlockArgumentsStopAfterEmoteBefore2259() {
        PassengerOfBlockArguments args = new PassengerOfBlockArguments();
        args.blockPos = new BlockVector3(0, 0, 0);
        args.offset = new Vector3f();
        args.blockType = 42;
        args.packUuid = UUID.randomUUID();

        SetPassengerOfBlockPacket packet = new SetPassengerOfBlockPacket();
        packet.passengerUniqueId = 9;
        packet.passengerOfBlockArguments = args;

        SetPassengerOfBlockPacket decoded = roundTrip(packet, V2225, SetPassengerOfBlockPacket::new);
        assertNotNull(decoded.passengerOfBlockArguments);
        assertEquals(0, decoded.passengerOfBlockArguments.blockType);
        assertNull(decoded.passengerOfBlockArguments.packUuid);
    }

    // ==================== LevelSoundEvent 新声音 ====================

    @Test
    void levelSoundEventIdsMatchProtocolDocs2259() {
        assertEquals(614, LevelSoundEventPacket.SOUND_CONVERT_TO_FROSTBITE);
        assertEquals(615, LevelSoundEventPacket.SOUND_CONVERT_TO_ZOMBIE);
        assertEquals(616, LevelSoundEventPacket.SOUND_CRACK);
        assertEquals(617, LevelSoundEventPacket.SOUND_ICE_BALL_THROW);
        assertEquals(618, LevelSoundEventPacket.SOUND_RECORD_FLURRY);
        assertEquals(619, LevelSoundEventPacket.SOUND_IMITATE_FROSTBITE);
        assertEquals(620, LevelSoundEventPacket.SOUND_UNDEFINED);

        assertEquals("convert_to_frostbite", LevelSoundEventPacket.getSoundName(614));
        assertEquals("record.flurry", LevelSoundEventPacket.getSoundName(618));
        assertEquals("imitate.frostbite", LevelSoundEventPacket.getSoundName(619));
        assertEquals(614, cn.nukkit.network.protocol.mapping.LevelSoundEventMap.getId("convert_to_frostbite"));
        assertEquals(618, cn.nukkit.network.protocol.mapping.LevelSoundEventMap.getId("record.flurry"));
    }

    // ==================== InputMode ====================

    @Test
    void inputModeWireValuesDifferAt2259() {
        assertEquals(InputMode.GAME_PAD_AND_MOUSE, InputMode.fromOrdinal(5, V2259));
        assertEquals(InputMode.COUNT, InputMode.fromOrdinal(5, V2225));
        assertEquals(InputMode.COUNT, InputMode.fromOrdinal(6, V2259));
        assertEquals(InputMode.UNDEFINED, InputMode.fromOrdinal(4, V2259),
                "MotionController was removed in 2259");
        assertEquals(InputMode.MOTION_CONTROLLER, InputMode.fromOrdinal(4, V2225));
    }

    // ==================== Container 枚举 ====================

    @Test
    void containerSlotTypeAndTypeFollow2259Names() {
        assertEquals(71, ContainerSlotType.VIRTUAL_CONTAINER.getId(version(V2259)));
        assertThrows(IllegalArgumentException.class,
                () -> ContainerSlotType.VIRTUAL_CONTAINER.getId(version(V2225)));
        assertEquals(67, ContainerSlotType.DISC_PRESS_BLOCK_CONTAINER.getId(version(V2259)));
        assertEquals(71, ContainerSlotType.fromId(71, version(V2259)).getId(version(V2259)));
        assertNull(ContainerSlotType.fromId(71, version(V2225)));

        assertEquals(38, ContainerType.DISC_PRESS.getId());
        assertEquals(39, ContainerType.VIRTUAL_CONTAINER.getId());
        assertSame(ContainerType.DISC_PRESS, ContainerType.from(38));
        assertSame(ContainerType.VIRTUAL_CONTAINER, ContainerType.from(39));
    }

    // ==================== DataStore 变量名布局 ====================

    @Test
    void clientboundDataStoreUpdateUsesVariableNameOn2259() {
        DataStoreUpdate update = new DataStoreUpdate();
        update.setDataStoreName("store");
        update.setProperty("prop");
        update.setPath("path");
        update.setData(1.25d);
        update.setPropertyUpdateCount(3);
        update.setPathUpdateCount(7);

        ClientboundDataStorePacket packet = new ClientboundDataStorePacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.setUpdates(List.of(update));
        packet.encode();

        BinaryStream stream = body(packet);
        assertEquals(1, stream.getUnsignedVarInt());
        assertEquals(0, stream.getUnsignedVarInt(), "UPDATE action");
        assertEquals("store:prop/path", stream.getString());
        assertEquals(0, stream.getUnsignedVarInt(), "double control");
        assertEquals(1.25d, Double.longBitsToDouble(stream.getLLong()));
        assertEquals(3, stream.getLInt(), "single Update Count");
        assertEquals(0, stream.getBuffer().length - stream.getOffset(), "no trailing path count");
    }

    @Test
    void clientboundDataStoreChangeAndRemovalUseVariableNameOn2259() {
        DataStoreChange change = new DataStoreChange();
        change.setDataStoreName("store");
        change.setProperty("prop");
        change.setUpdateCount(2);
        change.setNewValue("v");

        ClientboundDataStorePacket changePacket = new ClientboundDataStorePacket();
        changePacket.protocol = V2259;
        changePacket.gameVersion = version(V2259);
        changePacket.setUpdates(List.of(change));
        changePacket.encode();

        BinaryStream changeStream = body(changePacket);
        assertEquals(1, changeStream.getUnsignedVarInt());
        assertEquals(1, changeStream.getUnsignedVarInt(), "CHANGE action");
        assertEquals("store:prop", changeStream.getString());
        assertEquals(2, changeStream.getLInt());
        assertEquals(4, changeStream.getLInt(), "DynamicValue string tag");
        assertEquals("v", changeStream.getString());
        assertEquals(0, changeStream.getBuffer().length - changeStream.getOffset());

        DataStoreRemoval removal = new DataStoreRemoval();
        removal.setDataStoreName("store");
        ClientboundDataStorePacket removalPacket = new ClientboundDataStorePacket();
        removalPacket.protocol = V2259;
        removalPacket.gameVersion = version(V2259);
        removalPacket.setUpdates(List.of(removal));
        removalPacket.encode();

        BinaryStream removalStream = body(removalPacket);
        assertEquals(1, removalStream.getUnsignedVarInt());
        assertEquals(2, removalStream.getUnsignedVarInt(), "REMOVAL action");
        assertEquals("store", removalStream.getString());
        assertEquals(0, removalStream.getBuffer().length - removalStream.getOffset());
    }

    @Test
    void serverboundDataStoreDecodesVariableNameOn2259() {
        BinaryStream stream = new BinaryStream();
        stream.putString("store:prop/path");
        stream.putUnsignedVarInt(2); // string control
        stream.putString("value");
        stream.putLInt(5);

        ServerboundDataStorePacket packet = new ServerboundDataStorePacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.setBuffer(stream.getBuffer());
        packet.decode();

        DataStoreUpdate update = packet.getUpdate();
        assertNotNull(update);
        assertEquals("store:prop/path", update.getVariableName());
        assertEquals("store", update.getDataStoreName());
        assertEquals("prop", update.getProperty());
        assertEquals("path", update.getPath());
        assertEquals("value", update.getData());
        assertEquals(5, update.getPropertyUpdateCount());
        assertEquals(5, update.getPathUpdateCount());
    }

    // ==================== 新增 ChatPacket ====================

    @Test
    void chatPacketRoundTripsWithFormatting() {
        ChatPacket packet = new ChatPacket();
        packet.signedChat = new SignedChat();
        packet.signedChat.keyId = "key";
        packet.signedChat.senderId = "sender";
        packet.signedChat.messageId = "msg";
        packet.signedChat.scanText = "hello";
        packet.signedChat.redactedText = "hello";
        packet.signedChat.authorName = "Steve";
        packet.signedChat.signature = "sig";
        packet.roomId = "room";
        packet.formatting = new ChatFormatting();
        packet.formatting.namePrefix = "[";
        packet.formatting.messagePostfix = "]";

        ChatPacket decoded = roundTrip(packet, V2259, ChatPacket::new);
        assertEquals("key", decoded.signedChat.keyId);
        assertEquals("Steve", decoded.signedChat.authorName);
        assertEquals("room", decoded.roomId);
        assertEquals("[", decoded.formatting.namePrefix);
        assertNull(decoded.formatting.namePostfix);
        assertEquals("]", decoded.formatting.messagePostfix);
    }

    // ==================== 虚拟容器 ====================

    @Test
    void virtualContainerCloseRoundTrips() {
        VirtualContainerClosePacket packet = new VirtualContainerClosePacket();
        packet.sessionId = 123;
        packet.kind = VirtualContainerCloseKind.REQUEST;
        packet.cause = VirtualContainerCloseCause.SERVER_REQUESTED;

        VirtualContainerClosePacket decoded = roundTrip(packet, V2259, VirtualContainerClosePacket::new);
        assertEquals(123, decoded.sessionId);
        assertEquals(VirtualContainerCloseKind.REQUEST, decoded.kind);
        assertEquals(VirtualContainerCloseCause.SERVER_REQUESTED, decoded.cause);
    }

    @Test
    void virtualContainerOpenWritesHeaderAndSlots() {
        VirtualContainerOpenPacket packet = new VirtualContainerOpenPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.containerId = 4;
        packet.sessionId = 77;
        packet.title = "Disc Press";
        packet.slots.add(Item.AIR_ITEM);
        packet.slots.add(Item.AIR_ITEM);
        packet.encode();

        BinaryStream stream = body(packet);
        assertEquals(4, stream.getByte() & 0xff);
        assertEquals(77, stream.getUnsignedVarLong());
        assertEquals("Disc Press", stream.getString());
        assertEquals(2, stream.getUnsignedVarInt(), "slot count sets the container capacity");
    }

    // ==================== 在线音乐 ====================

    @Test
    void serverboundOnlineMusicRequestDecodes() {
        BinaryStream stream = new BinaryStream();
        stream.putLInt(9);
        stream.putString("record.flurry");

        ServerboundRequestOnlineMusicPlaybackPacket packet = new ServerboundRequestOnlineMusicPlaybackPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.setBuffer(stream.getBuffer());
        packet.decode();

        assertEquals(9, packet.requestId);
        assertEquals("record.flurry", packet.soundEventName);
    }

    @Test
    void clientboundOnlineMusicResponseEncodesStateAsString() {
        ClientboundOnlineMusicPlaybackResponsePacket packet = new ClientboundOnlineMusicPlaybackResponsePacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.requestId = 9;
        packet.state = MusicPlaybackState.NOT_FOUND;
        packet.sharedMetadata = "a";
        packet.playbackContent = "b";
        packet.encode();

        BinaryStream stream = body(packet);
        assertEquals(9, stream.getLInt());
        assertEquals("NotFound", stream.getString());
        assertEquals("a", stream.getString());
        assertEquals("b", stream.getString());
        assertEquals(0, stream.getBuffer().length - stream.getOffset());
    }

    @Test
    void clientboundMusicPlaybackFailureEncodesEntries() {
        ClientboundMusicPlaybackFailurePacket packet = new ClientboundMusicPlaybackFailurePacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.failures.add(new MusicPlaybackFailureEntry(MusicPlaybackState.PENDING, "record.flurry"));
        packet.encode();

        BinaryStream stream = body(packet);
        assertEquals(1, stream.getUnsignedVarInt());
        assertEquals("Pending", stream.getString());
        assertEquals("record.flurry", stream.getString());
        assertEquals(0, stream.getBuffer().length - stream.getOffset());
    }

    // ==================== 盔甲可见性 ====================

    @Test
    void armorVisibilityRequestDecodes() {
        BinaryStream stream = new BinaryStream();
        stream.putBoolean(true);

        PlayerToggleArmorVisibilityRequestPacket packet = new PlayerToggleArmorVisibilityRequestPacket();
        packet.protocol = V2259;
        packet.gameVersion = version(V2259);
        packet.setBuffer(stream.getBuffer());
        packet.decode();

        assertTrue(packet.hideArmor);
    }

    // ==================== 数据包 ID ====================

    @Test
    void newPacketIdsMatchProtocolDocs2259() {
        assertEquals(361, ProtocolInfo.CHAT_PACKET);
        assertEquals(362, ProtocolInfo.SERVERBOUND_REQUEST_ONLINE_MUSIC_PLAYBACK_PACKET);
        assertEquals(363, ProtocolInfo.CLIENTBOUND_ONLINE_MUSIC_PLAYBACK_RESPONSE_PACKET);
        assertEquals(364, ProtocolInfo.PLAYER_TOGGLE_ARMOR_VISIBILITY_REQUEST_PACKET);
        assertEquals(365, ProtocolInfo.CLIENTBOUND_MUSIC_PLAYBACK_FAILURE_PACKET);
        assertEquals(366, ProtocolInfo.VIRTUAL_CONTAINER_OPEN_PACKET);
        assertEquals(367, ProtocolInfo.VIRTUAL_CONTAINER_CLOSE_PACKET);
    }
}
