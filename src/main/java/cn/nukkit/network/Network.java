package cn.nukkit.network;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.process.DataPacketManager;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.v113.*;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Utils;
import cn.nukkit.utils.VarInt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import javax.annotation.Nonnegative;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public class Network {

    public static final byte CHANNEL_NONE = 0;
    public static final byte CHANNEL_PRIORITY = 1; //Priority channel, only to be used when it matters
    public static final byte CHANNEL_WORLD_CHUNKS = 2; //Chunk sending
    public static final byte CHANNEL_MOVEMENT = 3; //Movement sending
    public static final byte CHANNEL_BLOCKS = 4; //Block updates or explosions
    public static final byte CHANNEL_WORLD_EVENTS = 5; //Entity, level or blockentity entity events
    public static final byte CHANNEL_ENTITY_SPAWNING = 6; //Entity spawn/despawn channel
    public static final byte CHANNEL_TEXT = 7; //Chat and other text stuff
    public static final byte CHANNEL_END = 31;

    private PacketPool packetPool113;
    private PacketPool packetPool137;
    private PacketPool packetPoolCurrent;

    private final Server server;

    private final Set<SourceInterface> interfaces = new HashSet<>();

    private final Set<AdvancedSourceInterface> advancedInterfaces = new HashSet<>();

    /*private double upload = 0;
    private double download = 0;*/

    private String name;
    private String subName;

    private final List<NetworkIF> hardWareNetworkInterfaces;
    private final LinkedList<NetWorkStatisticData> netWorkStatisticDataList = new LinkedList<>();

    public Network(Server server) {
        this.registerPackets();
        DataPacketManager.registerDefaultProcessors();
        this.server = server;
        List<NetworkIF> tmpIfs = null;
        try {
            tmpIfs = new SystemInfo().getHardware().getNetworkIFs();
        } catch (Throwable t) {
            log.warn(Server.getInstance().getLanguage().get("nukkit.start.hardwareMonitorDisabled"));
        } finally {
            this.hardWareNetworkInterfaces = tmpIfs;
        }
    }

    @Deprecated
    public void addStatistics(double upload, double download) {
        /*this.upload += upload;
        this.download += download;*/
    }

    public double getUpload() {
        //return upload;
        if (netWorkStatisticDataList.size() < 2) {
            return 0;
        }
        return netWorkStatisticDataList.get(1).upload - netWorkStatisticDataList.get(0).upload;
    }

    public double getDownload() {
        //return download;
        if (netWorkStatisticDataList.size() < 2) {
            return 0;
        }
        return netWorkStatisticDataList.get(1).download - netWorkStatisticDataList.get(0).download;
    }

    public void resetStatistics() {
        /*this.upload = 0;
        this.download = 0;*/
        long upload = 0;
        long download = 0;
        if (netWorkStatisticDataList.size() > 1) {
            netWorkStatisticDataList.removeFirst();
        }
        if (this.hardWareNetworkInterfaces != null) {
            for (NetworkIF networkIF : this.hardWareNetworkInterfaces) {
                networkIF.updateAttributes();
                upload += networkIF.getBytesSent();
                download += networkIF.getBytesRecv();
            }
        }
        netWorkStatisticDataList.add(new NetWorkStatisticData(upload, download));
    }

    public Set<SourceInterface> getInterfaces() {
        return interfaces;
    }

    public void startProcessInterfaces() {
        new Thread(() -> {
            while (this.getServer().isRunning()) {
                try {
                    for (SourceInterface interfaz : this.interfaces) {
                        try {
                            interfaz.process();
                        } catch (Exception e) {
                            if (Nukkit.DEBUG > 1) {
                                this.server.getLogger().logException(e);
                            }

                            interfaz.emergencyShutdown();
                            this.unregisterInterface(interfaz);
                            log.fatal(this.server.getLanguage().translateString("nukkit.server.networkError", new String[]{interfaz.getClass().getName(), Utils.getExceptionMessage(e)}));
                        }
                    }

                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    log.warn(e);
                }
            }
        }).start();
    }

    public void registerInterface(SourceInterface interfaz) {
        this.interfaces.add(interfaz);
        if (interfaz instanceof AdvancedSourceInterface) {
            this.advancedInterfaces.add((AdvancedSourceInterface) interfaz);
            ((AdvancedSourceInterface) interfaz).setNetwork(this);
        }
        interfaz.setName(this.name + "!@#" + this.subName);
    }

    public void unregisterInterface(SourceInterface sourceInterface) {
        this.interfaces.remove(sourceInterface);
        if (sourceInterface instanceof AdvancedSourceInterface) {
            this.advancedInterfaces.remove(sourceInterface);
        }
    }

    public void setName(String name) {
        this.name = name;
        this.updateName();
    }

    public String getName() {
        return name;
    }

    public String getSubName() {
        return subName;
    }

    public void setSubName(String subName) {
        this.subName = subName;
    }

    public void updateName() {
        for (SourceInterface interfaz : this.interfaces) {
            interfaz.setName(this.name + "!@#" + this.subName);
        }
    }

    @Deprecated
    public void registerPacket(byte id, Class<? extends DataPacket> clazz, Supplier<DataPacket> supplier) {
        this.registerPacket(ProtocolInfo.CURRENT_PROTOCOL, id, clazz, supplier);
    }

    public void registerPacket(@Nonnegative int protocol, byte id, Class<? extends DataPacket> clazz, Supplier<DataPacket> supplier) {
        PacketPool pool = this.getPacketPool(protocol);
        if (pool != null) {
            this.setPacketPool(protocol, pool.toBuilder()
                    .registerPacket(id, clazz, supplier)
                    .build()
            );
        }
    }

    @Deprecated
    public void registerPacketNew(@Nonnegative int id, @NotNull Class<? extends DataPacket> clazz, Supplier<DataPacket> supplier) {
        this.registerPacketNew(ProtocolInfo.CURRENT_PROTOCOL, id, clazz, supplier);
    }

    public void registerPacketNew(@Nonnegative int protocol, @Nonnegative int id, @NotNull Class<? extends DataPacket> clazz, Supplier<DataPacket> supplier) {
        PacketPool pool = this.getPacketPool(protocol);
        if (pool != null) {
            this.setPacketPool(protocol, pool.toBuilder()
                    .registerPacket(id, clazz, supplier)
                    .build()
            );
        }
    }

    public Server getServer() {
        return server;
    }

    public List<NetworkIF> getHardWareNetworkInterfaces() {
        return hardWareNetworkInterfaces;
    }

    public void processBatch(byte[] payload, Collection<DataPacket> packets, CompressionProvider compression, int raknetProtocol, Player player) {
        int maxSize = 3145728; // 3 * 1024 * 1024
        if (player != null && player.getSkin() == null) {
            maxSize = 6291456; // 6 * 1024 * 1024
        }
        byte[] data;
        try {
            data = compression.decompress(payload, maxSize);
        } catch (Exception e) {
            log.debug("Exception while inflating batch packet", e);
            return;
        }

        BinaryStream stream = new BinaryStream(data);
        try {
            int count = 0;
            while (!stream.feof()) {
                count++;
                if (count >= 1000) {
                    throw new ProtocolException("Illegal batch with " + count + " packets");
                }
                byte[] buf = stream.getByteArray();

                ByteArrayInputStream bais = new ByteArrayInputStream(buf);

                int packetId;
                switch (raknetProtocol) {
                    case 7:
                        packetId = bais.read();
                        break;
                    case 8:
                        packetId = bais.read();
                        bais.skip(2L);
                        break;
                    default:
                        int header = (int) VarInt.readUnsignedVarInt(bais);
                        // | Client ID | Sender ID | Packet ID |
                        // |   2 bits  |   2 bits  |  10 bits  |
                        packetId = header & 0x3FF;
                        break;
                }

                DataPacket pk = this.getPacket(packetId, player == null ? ProtocolInfo.CURRENT_PROTOCOL : player.protocol);

                if (pk != null) {
                    pk.protocol = player == null ? Integer.MAX_VALUE : player.protocol;
                    pk.setBuffer(buf, buf.length - bais.available());
                    try {
                        if (raknetProtocol > 8) {
                            pk.decode();
                        } else { // version < 1.6
                            pk.setBuffer(buf, pk.protocol < ProtocolInfo.v1_2_0 ? 1 : 3);
                            pk.decode();
                        }
                    } catch (Exception e) {
                        if (log.isTraceEnabled()) {
                            log.trace("Dumping Packet\n{}", ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(buf)));
                        }
                        log.error("Unable to decode packet", e);
                        throw new IllegalStateException("Unable to decode " + pk.getClass().getSimpleName());
                    }

                    packets.add(pk);
                } else {
                    log.debug("Received unknown packet with ID: {}", Integer.toHexString(packetId));
                }
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Error whilst decoding batch packet", e);
            }
        }
    }

    /**
     * Process packets obtained from batch packets
     * Required to perform additional analyses and filter unnecessary packets
     *
     * @param packets packets
     */
    @Deprecated
    public void processPackets(Player player, List<DataPacket> packets) {
        if (packets.isEmpty()) return;
        packets.forEach(player::handleDataPacket);
    }

    @Deprecated
    public DataPacket getPacket(byte id) {
        return getPacket(id & 0xff);
    }

    @Deprecated
    public DataPacket getPacket(int id) {
        Server.mvw("Network#getPacket(int id)");
        return getPacket(id, ProtocolInfo.CURRENT_PROTOCOL);
    }

    public DataPacket getPacket(int id, int protocol) {
        return getPacketPool(protocol).getPacket(id);
    }

    public PacketPool getPacketPool(int protocol) {
        if (protocol >= ProtocolInfo.v1_21_80) {
            return this.packetPoolCurrent;
        } else if (protocol >= ProtocolInfo.v1_2_0) {
            return this.packetPool137;
        }
        return this.packetPool113;
    }

    public void setPacketPool(int protocol, PacketPool packetPool) {
        if (protocol >= ProtocolInfo.v1_21_80) {
            this.packetPoolCurrent = packetPool;
        } else if (protocol >= ProtocolInfo.v1_2_0) {
            this.packetPool137 = packetPool;
        } else {
            this.packetPool113 = packetPool;
        }
    }

    public void sendPacket(InetSocketAddress socketAddress, ByteBuf payload) {
        for (AdvancedSourceInterface sourceInterface : this.advancedInterfaces) {
            sourceInterface.sendRawPacket(socketAddress, payload);
        }
    }

    public void blockAddress(InetAddress address) {
        for (AdvancedSourceInterface sourceInterface : this.advancedInterfaces) {
            sourceInterface.blockAddress(address);
        }
    }

    public void blockAddress(InetAddress address, int timeout) {
        for (AdvancedSourceInterface sourceInterface : this.advancedInterfaces) {
            sourceInterface.blockAddress(address, timeout);
        }
    }

    public void unblockAddress(InetAddress address) {
        for (AdvancedSourceInterface sourceInterface : this.advancedInterfaces) {
            sourceInterface.unblockAddress(address);
        }
    }

    private void registerPackets() {
        this.packetPool113 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v1_1_0)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v1_1_0))
                .registerPacket(ProtocolInfoV113.ADD_ENTITY_PACKET, AddEntityPacket.class, AddEntityPacket::new)
                .registerPacket(ProtocolInfoV113.ADD_HANGING_ENTITY_PACKET, AddHangingEntityPacketV113.class, AddHangingEntityPacketV113::new)
                .registerPacket(ProtocolInfoV113.ADD_ITEM_ENTITY_PACKET, AddItemEntityPacket.class, AddItemEntityPacket::new)
                .registerPacket(ProtocolInfoV113.ADD_ITEM_PACKET, AddItemPacketV113.class, AddItemPacketV113::new)
                .registerPacket(ProtocolInfoV113.ADD_PAINTING_PACKET, AddPaintingPacket.class, AddPaintingPacket::new)
                .registerPacket(ProtocolInfoV113.ADD_PLAYER_PACKET, AddPlayerPacket.class, AddPlayerPacket::new)
                .registerPacket(ProtocolInfoV113.ADVENTURE_SETTINGS_PACKET, AdventureSettingsPacket.class, AdventureSettingsPacket::new)
                .registerPacket(ProtocolInfoV113.ANIMATE_PACKET, AnimatePacket.class, AnimatePacket::new)
                .registerPacket(ProtocolInfoV113.AVAILABLE_COMMANDS_PACKET, AvailableCommandsPacket.class, AvailableCommandsPacket::new)
                .registerPacket(ProtocolInfoV113.BATCH_PACKET, BatchPacket.class, BatchPacket::new)
                .registerPacket(ProtocolInfoV113.BLOCK_ENTITY_DATA_PACKET, BlockEntityDataPacket.class, BlockEntityDataPacket::new)
                .registerPacket(ProtocolInfoV113.BLOCK_EVENT_PACKET, BlockEventPacket.class, BlockEventPacket::new)
                .registerPacket(ProtocolInfoV113.BLOCK_PICK_REQUEST_PACKET, BlockPickRequestPacket.class, BlockPickRequestPacket::new)
                .registerPacket(ProtocolInfoV113.BOSS_EVENT_PACKET, BossEventPacket.class, BossEventPacket::new)
                .registerPacket(ProtocolInfoV113.CHANGE_DIMENSION_PACKET, ChangeDimensionPacket.class, ChangeDimensionPacket::new)
                .registerPacket(ProtocolInfoV113.CHUNK_RADIUS_UPDATED_PACKET, ChunkRadiusUpdatedPacket.class, ChunkRadiusUpdatedPacket::new)
                .registerPacket(ProtocolInfoV113.CLIENTBOUND_MAP_ITEM_DATA_PACKET, ClientboundMapItemDataPacket.class, ClientboundMapItemDataPacket::new)
                .registerPacket(ProtocolInfoV113.COMMAND_STEP_PACKET, CommandStepPacketV113.class, CommandStepPacketV113::new)
                .registerPacket(ProtocolInfoV113.CONTAINER_CLOSE_PACKET, ContainerClosePacket.class, ContainerClosePacket::new)
                .registerPacket(ProtocolInfoV113.CONTAINER_OPEN_PACKET, ContainerOpenPacket.class, ContainerOpenPacket::new)
                .registerPacket(ProtocolInfoV113.CONTAINER_SET_CONTENT_PACKET, ContainerSetContentPacketV113.class, ContainerSetContentPacketV113::new)
                .registerPacket(ProtocolInfoV113.CONTAINER_SET_DATA_PACKET, ContainerSetDataPacket.class, ContainerSetDataPacket::new)
                .registerPacket(ProtocolInfoV113.CONTAINER_SET_SLOT_PACKET, ContainerSetSlotPacketV113.class, ContainerSetSlotPacketV113::new)
                .registerPacket(ProtocolInfoV113.CRAFTING_DATA_PACKET, CraftingDataPacket.class, CraftingDataPacket::new)
                .registerPacket(ProtocolInfoV113.CRAFTING_EVENT_PACKET, CraftingEventPacket.class, CraftingEventPacket::new)
                .registerPacket(ProtocolInfoV113.DISCONNECT_PACKET, DisconnectPacket.class, DisconnectPacket::new)
                .registerPacket(ProtocolInfoV113.DROP_ITEM_PACKET, DropItemPacketV113.class, DropItemPacketV113::new)
                .registerPacket(ProtocolInfoV113.ENTITY_EVENT_PACKET, EntityEventPacket.class, EntityEventPacket::new)
                .registerPacket(ProtocolInfoV113.UPDATE_ATTRIBUTES_PACKET, UpdateAttributesPacket.class, UpdateAttributesPacket::new)
                .registerPacket(ProtocolInfoV113.ENTITY_FALL_PACKET, EntityFallPacket.class, EntityFallPacket::new)
                .registerPacket(ProtocolInfoV113.EXPLODE_PACKET, ExplodePacketV113.class, ExplodePacketV113::new)
                .registerPacket(ProtocolInfoV113.FULL_CHUNK_DATA_PACKET, LevelChunkPacket.class, LevelChunkPacket::new)
                .registerPacket(ProtocolInfoV113.GAME_RULES_CHANGED_PACKET, GameRulesChangedPacket.class, GameRulesChangedPacket::new)
                .registerPacket(ProtocolInfoV113.HURT_ARMOR_PACKET, HurtArmorPacket.class, HurtArmorPacket::new)
                .registerPacket(ProtocolInfoV113.INTERACT_PACKET, InteractPacket.class, InteractPacket::new)
                .registerPacket(ProtocolInfoV113.INVENTORY_ACTION_PACKET, InventoryActionPacketV113.class, InventoryActionPacketV113::new)
                .registerPacket(ProtocolInfoV113.ITEM_FRAME_DROP_ITEM_PACKET, ItemFrameDropItemPacket.class, ItemFrameDropItemPacket::new)
                .registerPacket(ProtocolInfoV113.LEVEL_EVENT_PACKET, LevelEventPacket.class, LevelEventPacket::new)
                .registerPacket(ProtocolInfoV113.LEVEL_SOUND_EVENT_PACKET, LevelSoundEventPacketV1.class, LevelSoundEventPacketV1::new)
                .registerPacket(ProtocolInfoV113.LOGIN_PACKET, LoginPacket.class, LoginPacket::new)
                .registerPacket(ProtocolInfoV113.MAP_INFO_REQUEST_PACKET, MapInfoRequestPacket.class, MapInfoRequestPacket::new)
                .registerPacket(ProtocolInfoV113.MOB_ARMOR_EQUIPMENT_PACKET, MobArmorEquipmentPacket.class, MobArmorEquipmentPacket::new)
                .registerPacket(ProtocolInfoV113.MOB_EQUIPMENT_PACKET, MobEquipmentPacket.class, MobEquipmentPacket::new)
                .registerPacket(ProtocolInfoV113.MOVE_ENTITY_PACKET, MoveEntityAbsolutePacket.class, MoveEntityAbsolutePacket::new)
                .registerPacket(ProtocolInfoV113.MOVE_PLAYER_PACKET, MovePlayerPacket.class, MovePlayerPacket::new)
                .registerPacket(ProtocolInfoV113.PLAYER_ACTION_PACKET, PlayerActionPacket.class, PlayerActionPacket::new)
                .registerPacket(ProtocolInfoV113.PLAYER_INPUT_PACKET, PlayerInputPacket.class, PlayerInputPacket::new)
                .registerPacket(ProtocolInfoV113.PLAYER_LIST_PACKET, PlayerListPacket.class, PlayerListPacket::new)
                .registerPacket(ProtocolInfoV113.PLAY_SOUND_PACKET, PlaySoundPacket.class, PlaySoundPacket::new)
                .registerPacket(ProtocolInfoV113.PLAY_STATUS_PACKET, PlayStatusPacket.class, PlayStatusPacket::new)
                .registerPacket(ProtocolInfoV113.REMOVE_BLOCK_PACKET, RemoveBlockPacketV113.class, RemoveBlockPacketV113::new)
                .registerPacket(ProtocolInfoV113.REMOVE_ENTITY_PACKET, RemoveEntityPacket.class, RemoveEntityPacket::new)
                .registerPacket(ProtocolInfoV113.REPLACE_ITEM_IN_SLOT_PACKET, ReplaceItemInSlotPacketV113.class, ReplaceItemInSlotPacketV113::new)
                .registerPacket(ProtocolInfoV113.REQUEST_CHUNK_RADIUS_PACKET, RequestChunkRadiusPacket.class, RequestChunkRadiusPacket::new)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACKS_INFO_PACKET, ResourcePacksInfoPacket.class, ResourcePacksInfoPacket::new)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_STACK_PACKET, ResourcePackStackPacket.class, ResourcePackStackPacket::new)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_CLIENT_RESPONSE_PACKET, ResourcePackClientResponsePacket.class, ResourcePackClientResponsePacket::new)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_DATA_INFO_PACKET, ResourcePackDataInfoPacket.class, ResourcePackDataInfoPacket::new)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_CHUNK_DATA_PACKET, ResourcePackChunkDataPacket.class, ResourcePackChunkDataPacket::new)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_CHUNK_REQUEST_PACKET, ResourcePackChunkRequestPacket.class, ResourcePackChunkRequestPacket::new)
                .registerPacket(ProtocolInfoV113.RESPAWN_PACKET, RespawnPacket.class, RespawnPacket::new)
                .registerPacket(ProtocolInfoV113.RIDER_JUMP_PACKET, RiderJumpPacket.class, RiderJumpPacket::new)
                .registerPacket(ProtocolInfoV113.SET_COMMANDS_ENABLED_PACKET, SetCommandsEnabledPacket.class, SetCommandsEnabledPacket::new)
                .registerPacket(ProtocolInfoV113.SET_DIFFICULTY_PACKET, SetDifficultyPacket.class, SetDifficultyPacket::new)
                .registerPacket(ProtocolInfoV113.SET_ENTITY_DATA_PACKET, SetEntityDataPacket.class, SetEntityDataPacket::new)
                .registerPacket(ProtocolInfoV113.SET_ENTITY_LINK_PACKET, SetEntityLinkPacket.class, SetEntityLinkPacket::new)
                .registerPacket(ProtocolInfoV113.SET_ENTITY_MOTION_PACKET, SetEntityMotionPacket.class, SetEntityMotionPacket::new)
                .registerPacket(ProtocolInfoV113.SET_HEALTH_PACKET, SetHealthPacket.class, SetHealthPacket::new)
                .registerPacket(ProtocolInfoV113.SET_PLAYER_GAME_TYPE_PACKET, SetPlayerGameTypePacket.class, SetPlayerGameTypePacket::new)
                .registerPacket(ProtocolInfoV113.SET_SPAWN_POSITION_PACKET, SetSpawnPositionPacket.class, SetSpawnPositionPacket::new)
                .registerPacket(ProtocolInfoV113.SET_TITLE_PACKET, SetTitlePacket.class, SetTitlePacket::new)
                .registerPacket(ProtocolInfoV113.SET_TIME_PACKET, SetTimePacket.class, SetTimePacket::new)
                .registerPacket(ProtocolInfoV113.SHOW_CREDITS_PACKET, ShowCreditsPacket.class, ShowCreditsPacket::new)
                .registerPacket(ProtocolInfoV113.SPAWN_EXPERIENCE_ORB_PACKET, SpawnExperienceOrbPacket.class, SpawnExperienceOrbPacket::new)
                .registerPacket(ProtocolInfoV113.START_GAME_PACKET, StartGamePacket.class, StartGamePacket::new)
                .registerPacket(ProtocolInfoV113.TAKE_ITEM_ENTITY_PACKET, TakeItemEntityPacket.class, TakeItemEntityPacket::new)
                .registerPacket(ProtocolInfoV113.TEXT_PACKET, TextPacket.class, TextPacket::new)
                .registerPacket(ProtocolInfoV113.UPDATE_BLOCK_PACKET, UpdateBlockPacket.class, UpdateBlockPacket::new)
                .registerPacket(ProtocolInfoV113.USE_ITEM_PACKET, UseItemPacketV113.class, UseItemPacketV113::new)
                .registerPacket(ProtocolInfoV113.UPDATE_TRADE_PACKET, UpdateTradePacket.class, UpdateTradePacket::new)
                .build();

        this.packetPool137 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v1_2_0)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v1_2_0))
                .registerPacket(ProtocolInfo.SERVER_TO_CLIENT_HANDSHAKE_PACKET, ServerToClientHandshakePacket.class, ServerToClientHandshakePacket::new)
                .registerPacket(ProtocolInfo.CLIENT_TO_SERVER_HANDSHAKE_PACKET, ClientToServerHandshakePacket.class, ClientToServerHandshakePacket::new)
                .registerPacket(ProtocolInfo.ADD_ENTITY_PACKET, AddEntityPacket.class, AddEntityPacket::new)
                .registerPacket(ProtocolInfo.ADD_ITEM_ENTITY_PACKET, AddItemEntityPacket.class, AddItemEntityPacket::new)
                .registerPacket(ProtocolInfo.ADD_PAINTING_PACKET, AddPaintingPacket.class, AddPaintingPacket::new)
                .registerPacket(ProtocolInfo.TICK_SYNC_PACKET, TickSyncPacket.class, TickSyncPacket::new)
                .registerPacket(ProtocolInfo.ADD_PLAYER_PACKET, AddPlayerPacket.class, AddPlayerPacket::new)
                .registerPacket(ProtocolInfo.ADVENTURE_SETTINGS_PACKET, AdventureSettingsPacket.class, AdventureSettingsPacket::new)
                .registerPacket(ProtocolInfo.ANIMATE_PACKET, AnimatePacket.class, AnimatePacket::new)
                .registerPacket(ProtocolInfo.ANVIL_DAMAGE_PACKET, AnvilDamagePacket.class, AnvilDamagePacket::new)
                .registerPacket(ProtocolInfo.AVAILABLE_COMMANDS_PACKET, AvailableCommandsPacket.class, AvailableCommandsPacket::new)
                .registerPacket(ProtocolInfo.BATCH_PACKET, BatchPacket.class, BatchPacket::new)
                .registerPacket(ProtocolInfo.BLOCK_ENTITY_DATA_PACKET, BlockEntityDataPacket.class, BlockEntityDataPacket::new)
                .registerPacket(ProtocolInfo.BLOCK_EVENT_PACKET, BlockEventPacket.class, BlockEventPacket::new)
                .registerPacket(ProtocolInfo.BLOCK_PICK_REQUEST_PACKET, BlockPickRequestPacket.class, BlockPickRequestPacket::new)
                .registerPacket(ProtocolInfo.BOOK_EDIT_PACKET, BookEditPacket.class, BookEditPacket::new)
                .registerPacket(ProtocolInfo.BOSS_EVENT_PACKET, BossEventPacket.class, BossEventPacket::new)
                .registerPacket(ProtocolInfo.CHANGE_DIMENSION_PACKET, ChangeDimensionPacket.class, ChangeDimensionPacket::new)
                .registerPacket(ProtocolInfo.CHUNK_RADIUS_UPDATED_PACKET, ChunkRadiusUpdatedPacket.class, ChunkRadiusUpdatedPacket::new)
                .registerPacket(ProtocolInfo.CLIENTBOUND_MAP_ITEM_DATA_PACKET, ClientboundMapItemDataPacket.class, ClientboundMapItemDataPacket::new)
                .registerPacket(ProtocolInfo.COMMAND_REQUEST_PACKET, CommandRequestPacket.class, CommandRequestPacket::new)
                .registerPacket(ProtocolInfo.CONTAINER_CLOSE_PACKET, ContainerClosePacket.class, ContainerClosePacket::new)
                .registerPacket(ProtocolInfo.CONTAINER_OPEN_PACKET, ContainerOpenPacket.class, ContainerOpenPacket::new)
                .registerPacket(ProtocolInfo.CONTAINER_SET_DATA_PACKET, ContainerSetDataPacket.class, ContainerSetDataPacket::new)
                .registerPacket(ProtocolInfo.CRAFTING_DATA_PACKET, CraftingDataPacket.class, CraftingDataPacket::new)
                .registerPacket(ProtocolInfo.CRAFTING_EVENT_PACKET, CraftingEventPacket.class, CraftingEventPacket::new)
                .registerPacket(ProtocolInfo.DISCONNECT_PACKET, DisconnectPacket.class, DisconnectPacket::new)
                .registerPacket(ProtocolInfo.ENTITY_EVENT_PACKET, EntityEventPacket.class, EntityEventPacket::new)
                .registerPacket(ProtocolInfo.ENTITY_FALL_PACKET, EntityFallPacket.class, EntityFallPacket::new)
                .registerPacket(ProtocolInfo.FULL_CHUNK_DATA_PACKET, LevelChunkPacket.class, LevelChunkPacket::new)
                .registerPacket(ProtocolInfo.GAME_RULES_CHANGED_PACKET, GameRulesChangedPacket.class, GameRulesChangedPacket::new)
                .registerPacket(ProtocolInfo.HURT_ARMOR_PACKET, HurtArmorPacket.class, HurtArmorPacket::new)
                .registerPacket(ProtocolInfo.INTERACT_PACKET, InteractPacket.class, InteractPacket::new)
                .registerPacket(ProtocolInfo.INVENTORY_CONTENT_PACKET, InventoryContentPacket.class, InventoryContentPacket::new)
                .registerPacket(ProtocolInfo.INVENTORY_SLOT_PACKET, InventorySlotPacket.class, InventorySlotPacket::new)
                .registerPacket(ProtocolInfo.INVENTORY_TRANSACTION_PACKET, InventoryTransactionPacket.class, InventoryTransactionPacket::new)
                .registerPacket(ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET, ItemFrameDropItemPacket.class, ItemFrameDropItemPacket::new)
                .registerPacket(ProtocolInfo.LEVEL_EVENT_PACKET, LevelEventPacket.class, LevelEventPacket::new)
                .registerPacket(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V1, LevelSoundEventPacketV1.class, LevelSoundEventPacketV1::new)
                .registerPacket(ProtocolInfo.LOGIN_PACKET, LoginPacket.class, LoginPacket::new)
                .registerPacket(ProtocolInfo.MAP_INFO_REQUEST_PACKET, MapInfoRequestPacket.class, MapInfoRequestPacket::new)
                .registerPacket(ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET, MobArmorEquipmentPacket.class, MobArmorEquipmentPacket::new)
                .registerPacket(ProtocolInfo.MOB_EQUIPMENT_PACKET, MobEquipmentPacket.class, MobEquipmentPacket::new)
                .registerPacket(ProtocolInfo.MODAL_FORM_REQUEST_PACKET, ModalFormRequestPacket.class, ModalFormRequestPacket::new)
                .registerPacket(ProtocolInfo.MODAL_FORM_RESPONSE_PACKET, ModalFormResponsePacket.class, ModalFormResponsePacket::new)
                .registerPacket(ProtocolInfo.MOVE_ENTITY_ABSOLUTE_PACKET, MoveEntityAbsolutePacket.class, MoveEntityAbsolutePacket::new)
                .registerPacket(ProtocolInfo.MOVE_PLAYER_PACKET, MovePlayerPacket.class, MovePlayerPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_ACTION_PACKET, PlayerActionPacket.class, PlayerActionPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_INPUT_PACKET, PlayerInputPacket.class, PlayerInputPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_LIST_PACKET, PlayerListPacket.class, PlayerListPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_HOTBAR_PACKET, PlayerHotbarPacket.class, PlayerHotbarPacket::new)
                .registerPacket(ProtocolInfo.PLAY_SOUND_PACKET, PlaySoundPacket.class, PlaySoundPacket::new)
                .registerPacket(ProtocolInfo.PLAY_STATUS_PACKET, PlayStatusPacket.class, PlayStatusPacket::new)
                .registerPacket(ProtocolInfo.REMOVE_ENTITY_PACKET, RemoveEntityPacket.class, RemoveEntityPacket::new)
                .registerPacket(ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET, RequestChunkRadiusPacket.class, RequestChunkRadiusPacket::new)
                .registerPacket(ProtocolInfo.RESOURCE_PACKS_INFO_PACKET, ResourcePacksInfoPacket.class, ResourcePacksInfoPacket::new)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_STACK_PACKET, ResourcePackStackPacket.class, ResourcePackStackPacket::new)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET, ResourcePackClientResponsePacket.class, ResourcePackClientResponsePacket::new)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_DATA_INFO_PACKET, ResourcePackDataInfoPacket.class, ResourcePackDataInfoPacket::new)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_CHUNK_DATA_PACKET, ResourcePackChunkDataPacket.class, ResourcePackChunkDataPacket::new)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET, ResourcePackChunkRequestPacket.class, ResourcePackChunkRequestPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_SKIN_PACKET, PlayerSkinPacket.class, PlayerSkinPacket::new)
                .registerPacket(ProtocolInfo.RESPAWN_PACKET, RespawnPacket.class, RespawnPacket::new)
                .registerPacket(ProtocolInfo.RIDER_JUMP_PACKET, RiderJumpPacket.class, RiderJumpPacket::new)
                .registerPacket(ProtocolInfo.SET_COMMANDS_ENABLED_PACKET, SetCommandsEnabledPacket.class, SetCommandsEnabledPacket::new)
                .registerPacket(ProtocolInfo.SET_DIFFICULTY_PACKET, SetDifficultyPacket.class, SetDifficultyPacket::new)
                .registerPacket(ProtocolInfo.SET_ENTITY_DATA_PACKET, SetEntityDataPacket.class, SetEntityDataPacket::new)
                .registerPacket(ProtocolInfo.SET_ENTITY_LINK_PACKET, SetEntityLinkPacket.class, SetEntityLinkPacket::new)
                .registerPacket(ProtocolInfo.SET_ENTITY_MOTION_PACKET, SetEntityMotionPacket.class, SetEntityMotionPacket::new)
                .registerPacket(ProtocolInfo.SET_HEALTH_PACKET, SetHealthPacket.class, SetHealthPacket::new)
                .registerPacket(ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET, SetPlayerGameTypePacket.class, SetPlayerGameTypePacket::new)
                .registerPacket(ProtocolInfo.SET_SPAWN_POSITION_PACKET, SetSpawnPositionPacket.class, SetSpawnPositionPacket::new)
                .registerPacket(ProtocolInfo.SET_TITLE_PACKET, SetTitlePacket.class, SetTitlePacket::new)
                .registerPacket(ProtocolInfo.SET_TIME_PACKET, SetTimePacket.class, SetTimePacket::new)
                .registerPacket(ProtocolInfo.SERVER_SETTINGS_REQUEST_PACKET, ServerSettingsRequestPacket.class, ServerSettingsRequestPacket::new)
                .registerPacket(ProtocolInfo.SERVER_SETTINGS_RESPONSE_PACKET, ServerSettingsResponsePacket.class, ServerSettingsResponsePacket::new)
                .registerPacket(ProtocolInfo.SHOW_CREDITS_PACKET, ShowCreditsPacket.class, ShowCreditsPacket::new)
                .registerPacket(ProtocolInfo.SPAWN_EXPERIENCE_ORB_PACKET, SpawnExperienceOrbPacket.class, SpawnExperienceOrbPacket::new)
                .registerPacket(ProtocolInfo.START_GAME_PACKET, StartGamePacket.class, StartGamePacket::new)
                .registerPacket(ProtocolInfo.TAKE_ITEM_ENTITY_PACKET, TakeItemEntityPacket.class, TakeItemEntityPacket::new)
                .registerPacket(ProtocolInfo.TEXT_PACKET, TextPacket.class, TextPacket::new)
                .registerPacket(ProtocolInfo.SERVER_POST_MOVE_POSITION, ServerPostMovePositionPacket.class, ServerPostMovePositionPacket::new)
                .registerPacket(ProtocolInfo.TRANSFER_PACKET, TransferPacket.class, TransferPacket::new)
                .registerPacket(ProtocolInfo.UPDATE_ATTRIBUTES_PACKET, UpdateAttributesPacket.class, UpdateAttributesPacket::new)
                .registerPacket(ProtocolInfo.UPDATE_BLOCK_PACKET, UpdateBlockPacket.class, UpdateBlockPacket::new)
                .registerPacket(ProtocolInfo.COMMAND_BLOCK_UPDATE_PACKET, CommandBlockUpdatePacket.class, CommandBlockUpdatePacket::new)
                .registerPacket(ProtocolInfo.COMMAND_OUTPUT_PACKET, CommandOutputPacket.class, CommandOutputPacket::new)
                .registerPacket(ProtocolInfo.UPDATE_TRADE_PACKET, UpdateTradePacket.class, UpdateTradePacket::new)
                .registerPacket(ProtocolInfo.SET_DISPLAY_OBJECTIVE_PACKET, SetDisplayObjectivePacket.class, SetDisplayObjectivePacket::new)
                .registerPacket(ProtocolInfo.SET_SCORE_PACKET, SetScorePacket.class, SetScorePacket::new)
                .registerPacket(ProtocolInfo.MOVE_ENTITY_DELTA_PACKET, MoveEntityDeltaPacket.class, MoveEntityDeltaPacket::new)
                .registerPacket(ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET, SetLocalPlayerAsInitializedPacket.class, SetLocalPlayerAsInitializedPacket::new)
                .registerPacket(ProtocolInfo.NETWORK_STACK_LATENCY_PACKET, NetworkStackLatencyPacket.class, NetworkStackLatencyPacket::new)
                .registerPacket(ProtocolInfo.UPDATE_SOFT_ENUM_PACKET, UpdateSoftEnumPacket.class, UpdateSoftEnumPacket::new)
                .registerPacket(ProtocolInfo.NETWORK_CHUNK_PUBLISHER_UPDATE_PACKET, NetworkChunkPublisherUpdatePacket.class, NetworkChunkPublisherUpdatePacket::new)
                .registerPacket(ProtocolInfo.AVAILABLE_ENTITY_IDENTIFIERS_PACKET, AvailableEntityIdentifiersPacket.class, AvailableEntityIdentifiersPacket::new)
                .registerPacket(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V2, LevelSoundEventPacket.class, LevelSoundEventPacket::new)
                .registerPacket(ProtocolInfo.SCRIPT_CUSTOM_EVENT_PACKET, ScriptCustomEventPacket.class, ScriptCustomEventPacket::new)
                .registerPacket(ProtocolInfo.SPAWN_PARTICLE_EFFECT_PACKET, SpawnParticleEffectPacket.class, SpawnParticleEffectPacket::new)
                .registerPacket(ProtocolInfo.BIOME_DEFINITION_LIST_PACKET, BiomeDefinitionListPacket.class, BiomeDefinitionListPacket::new)
                .registerPacket(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET, LevelSoundEventPacket.class, LevelSoundEventPacket::new)
                .registerPacket(ProtocolInfo.LEVEL_EVENT_GENERIC_PACKET, LevelEventGenericPacket.class, LevelEventGenericPacket::new)
                .registerPacket(ProtocolInfo.LECTERN_UPDATE_PACKET, LecternUpdatePacket.class, LecternUpdatePacket::new)
                .registerPacket(ProtocolInfo.VIDEO_STREAM_CONNECT_PACKET, VideoStreamConnectPacket.class, VideoStreamConnectPacket::new)
                .registerPacket(ProtocolInfo.CLIENT_CACHE_STATUS_PACKET, ClientCacheStatusPacket.class, ClientCacheStatusPacket::new)
                .registerPacket(ProtocolInfo.MAP_CREATE_LOCKED_COPY_PACKET, MapCreateLockedCopyPacket.class, MapCreateLockedCopyPacket::new)
                .registerPacket(ProtocolInfo.ON_SCREEN_TEXTURE_ANIMATION_PACKET, OnScreenTextureAnimationPacket.class, OnScreenTextureAnimationPacket::new)
                .registerPacket(ProtocolInfo.COMPLETED_USING_ITEM_PACKET, CompletedUsingItemPacket.class, CompletedUsingItemPacket::new)
                .registerPacket(ProtocolInfo.NETWORK_SETTINGS_PACKET, NetworkSettingsPacket.class, NetworkSettingsPacket::new)
                .registerPacket(ProtocolInfo.CODE_BUILDER_PACKET, CodeBuilderPacket.class, CodeBuilderPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_AUTH_INPUT_PACKET, PlayerAuthInputPacket.class, PlayerAuthInputPacket::new)
                .registerPacket(ProtocolInfo.CREATIVE_CONTENT_PACKET, CreativeContentPacket.class, CreativeContentPacket::new)
                .registerPacket(ProtocolInfo.DEBUG_INFO_PACKET, DebugInfoPacket.class, DebugInfoPacket::new)
                .registerPacket(ProtocolInfo.EMOTE_LIST_PACKET, EmoteListPacket.class, EmoteListPacket::new)
                .registerPacket(ProtocolInfo.PACKET_VIOLATION_WARNING_PACKET, PacketViolationWarningPacket.class, PacketViolationWarningPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_ARMOR_DAMAGE_PACKET, PlayerArmorDamagePacket.class, PlayerArmorDamagePacket::new)
                .registerPacket(ProtocolInfo.PLAYER_ENCHANT_OPTIONS_PACKET, PlayerEnchantOptionsPacket.class, PlayerEnchantOptionsPacket::new)
                .registerPacket(ProtocolInfo.UPDATE_PLAYER_GAME_TYPE_PACKET, UpdatePlayerGameTypePacket.class, UpdatePlayerGameTypePacket::new)
                .registerPacket(ProtocolInfo.UPDATE_ABILITIES_PACKET, UpdateAbilitiesPacket.class, UpdateAbilitiesPacket::new)
                .registerPacket(ProtocolInfo.REQUEST_ABILITY_PACKET, RequestAbilityPacket.class, RequestAbilityPacket::new)
                .registerPacket(ProtocolInfo.UPDATE_ADVENTURE_SETTINGS_PACKET, UpdateAdventureSettingsPacket.class, UpdateAdventureSettingsPacket::new)
                .registerPacket(ProtocolInfo.DEATH_INFO_PACKET, DeathInfoPacket.class, DeathInfoPacket::new)
                .registerPacket(ProtocolInfo.EMOTE_PACKET, EmotePacket.class, EmotePacket::new)
                .registerPacket(ProtocolInfo.ANIMATE_ENTITY_PACKET, AnimateEntityPacket.class, AnimateEntityPacket::new)
                .registerPacket(ProtocolInfo.PLAYER_FOG_PACKET, PlayerFogPacket.class, PlayerFogPacket::new)
                .registerPacket(ProtocolInfo.ITEM_COMPONENT_PACKET, ItemComponentPacket.class, ItemComponentPacket::new)
                .registerPacket(ProtocolInfo.FILTER_TEXT_PACKET, FilterTextPacket.class, FilterTextPacket::new)
                .registerPacket(ProtocolInfo.SYNC_ENTITY_PROPERTY_PACKET, SyncEntityPropertyPacket.class, SyncEntityPropertyPacket::new)
                .registerPacket(ProtocolInfo.NPC_DIALOGUE_PACKET, NPCDialoguePacket.class, NPCDialoguePacket::new)
                .registerPacket(ProtocolInfo.TOAST_REQUEST_PACKET, ToastRequestPacket.class, ToastRequestPacket::new)
                .registerPacket(ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET, RequestNetworkSettingsPacket.class, RequestNetworkSettingsPacket::new)
                .registerPacket(ProtocolInfo.UPDATE_CLIENT_INPUT_LOCKS, UpdateClientInputLocksPacket.class, UpdateClientInputLocksPacket::new)
                .registerPacket(ProtocolInfo.CAMERA_PRESETS_PACKET, CameraPresetsPacket.class, CameraPresetsPacket::new)
                .registerPacket(ProtocolInfo.CAMERA_INSTRUCTION_PACKET, CameraInstructionPacket.class, CameraInstructionPacket::new)
                .registerPacket(ProtocolInfo.TRIM_DATA_PACKET, TrimDataPacket.class, TrimDataPacket::new)
                .registerPacket(ProtocolInfo.OPEN_SIGN_PACKET, OpenSignPacket.class, OpenSignPacket::new)
                .registerPacket(ProtocolInfo.AGENT_ANIMATION_PACKET, AgentAnimationPacket.class, AgentAnimationPacket::new)
                .registerPacket(ProtocolInfo.REFRESH_ENTITLEMENTS_PACKET, RefreshEntitlementsPacket.class, RefreshEntitlementsPacket::new)
                .registerPacket(ProtocolInfo.TOGGLE_CRAFTER_SLOT_REQUEST_PACKET, ToggleCrafterSlotRequestPacket.class, ToggleCrafterSlotRequestPacket::new)
                .registerPacket(ProtocolInfo.SET_PLAYER_INVENTORY_OPTIONS_PACKET, SetPlayerInventoryOptionsPacket.class, SetPlayerInventoryOptionsPacket::new)
                .registerPacket(ProtocolInfo.SET_HUD_PACKET, SetHudPacket.class, SetHudPacket::new)
                .registerPacket(ProtocolInfo.AWARD_ACHIEVEMENT_PACKET, AwardAchievementPacket.class, AwardAchievementPacket::new)
                .registerPacket(ProtocolInfo.CLIENTBOUND_CLOSE_FORM_PACKET, ClientboundCloseFormPacket.class, ClientboundCloseFormPacket::new)
                .registerPacket(ProtocolInfo.SERVERBOUND_LOADING_SCREEN_PACKET, ServerboundLoadingScreenPacket.class, ServerboundLoadingScreenPacket::new)
                .registerPacket(ProtocolInfo.JIGSAW_STRUCTURE_DATA_PACKET, JigsawStructureDataPacket.class, JigsawStructureDataPacket::new)
                .registerPacket(ProtocolInfo.CURRENT_STRUCTURE_FEATURE_PACKET, CurrentStructureFeaturePacket.class, CurrentStructureFeaturePacket::new)
                .registerPacket(ProtocolInfo.SERVERBOUND_DIAGNOSTICS_PACKET, ServerboundDiagnosticsPacket.class, ServerboundDiagnosticsPacket::new)
                .registerPacket(ProtocolInfo.CAMERA_AIM_ASSIST_PACKET, CameraAimAssistPacket.class, CameraAimAssistPacket::new)
                .registerPacket(ProtocolInfo.CONTAINER_REGISTRY_CLEANUP_PACKET, ContainerRegistryCleanupPacket.class, ContainerRegistryCleanupPacket::new)
                .registerPacket(ProtocolInfo.MOVEMENT_EFFECT_PACKET, MovementEffectPacket.class, MovementEffectPacket::new)
                .registerPacket(ProtocolInfo.SET_MOVEMENT_AUTHORITY_PACKET, SetMovementAuthorityPacket.class, SetMovementAuthorityPacket::new)
                .registerPacket(ProtocolInfo.CAMERA_AIM_ASSIST_PRESETS_PACKET, CameraAimAssistPresetsPacket.class, CameraAimAssistPresetsPacket::new)
                .build();

        this.packetPoolCurrent = this.packetPool137.toBuilder()
                .protocolVersion(ProtocolInfo.CURRENT_PROTOCOL)
                .minecraftVersion(ProtocolInfo.MINECRAFT_VERSION)
                .deregisterPacket(ProtocolInfo.PLAYER_INPUT_PACKET)
                .deregisterPacket(ProtocolInfo.RIDER_JUMP_PACKET)
                .registerPacket(ProtocolInfo.PLAYER_LOCATIONS_PACKET, PlayerLocationPacket.class, PlayerLocationPacket::new)
                .build();
    }

    @AllArgsConstructor
    @Data
    public static class NetWorkStatisticData {
        private long upload;
        private long download;
    }
}
