package cn.nukkit.network;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.process.DataPacketManager;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.v113.*;
import cn.nukkit.network.protocol.v20.AddMobPacket;
import cn.nukkit.network.protocol.v20.MoveEntityPosRotPacket;
import cn.nukkit.network.protocol.v20.RotateHeadPacket;
import cn.nukkit.network.protocol.v20.UnloadChunkPacket;
import cn.nukkit.network.protocol.v70.*;
import cn.nukkit.utils.*;
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

    private PacketPool packetPool18;// 0.9.5 0.10.5
    private PacketPool packetPool27;// 0.11
    private PacketPool packetPool70;// 0.12 0.13 0.14
    private PacketPool packetPool84;// 0.15
    private PacketPool packetPool90;// 0.16
    private PacketPool packetPool113;
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

    public void processInterfaces() {
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
    public void registerPacket(byte id, Class<? extends DataPacket> clazz) {
        this.registerPacket(ProtocolInfo.CURRENT_PROTOCOL, id, clazz);
    }

    public void registerPacket(@Nonnegative int protocol, byte id, Class<? extends DataPacket> clazz) {
        PacketPool pool = this.getPacketPool(protocol);
        if (pool != null) {
            this.setPacketPool(protocol, pool.toBuilder()
                    .registerPacket(id, clazz)
                    .build()
            );
        }
    }

    @Deprecated
    public void registerPacketNew(@Nonnegative int id, @NotNull Class<? extends DataPacket> clazz) {
        this.registerPacketNew(ProtocolInfo.CURRENT_PROTOCOL, id, clazz);
    }

    public void registerPacketNew(@Nonnegative int protocol, @Nonnegative int id, @NotNull Class<? extends DataPacket> clazz) {
        PacketPool pool = this.getPacketPool(protocol);
        if (pool != null) {
            this.setPacketPool(protocol, pool.toBuilder()
                    .registerPacket(id, clazz)
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
            // log.info("0x" + Integer.toHexString(compression.getPrefix() & 0xff));
//            if(compression.getPrefix() == (byte) 0x00){
//                log.info(compression == CompressionProvider.ZLIB_RAW ?  "ZLIB_RAW" : "ZLIB");
//            }else if(compression.getPrefix() == (byte) 0xff){
//                log.info("NONE");
//            }
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
     * 处理0.12 - 1.0.0的batchpacket
     * @param packet
     * @param player
     */
    public List<DataPacket> processBatch(BatchPacket packet, Player player){
        byte[] data;
        try {
            data = Zlib.inflate(packet.payload, 64 * 1024 * 1024);
        } catch (Exception e) {
            Server.getInstance().getLogger().logException(e);
            return null;
        }

        if(packet.protocol <= ProtocolInfo.v_0_15_10){
            int len = data.length;
            int offset = 0;
            try {
                List<DataPacket> packets = new ArrayList<>();
                List<Byte> filter = new ArrayList<>();
                while (offset < len) {

                    DataPacket pk;

                    if(ProtocolInfo.v_0_13_2 < packet.protocol && packet.protocol <= ProtocolInfo.v_0_14_3){//0.14

                        int pkLen = Binary.readInt(Binary.subBytes(data, offset, 4));
                        offset += 4;

                        byte[] buf = Binary.subBytes(data, offset, pkLen);
                        offset += pkLen;

                        if ((pk = this.getPacket(buf[1], packet.protocol)) != null) {
                            pk.protocol = packet.protocol;

                            if (pk.pid() == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(BatchPacket.class)) {
                                throw new IllegalStateException("Invalid BatchPacket inside BatchPacket");
                            }

                            pk.setBuffer(buf, 2);
                            pk.decode();

                            if(pk.pid() == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(UseItemPacket.class)){
                                if (!filter.contains(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(UseItemPacket.class))) {
                                    player.handleDataPacket(packet);
                                    filter.add(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(UseItemPacket.class));
                                }
                            }else{
                                player.handleDataPacket(pk);
                            }

                            if (pk.getOffset() <= 0) {
                                return null;
                            }
                        }
                    }else if(packet.protocol > ProtocolInfo.v_0_14_3){ // 0.15

                        int pkLen = Binary.readInt(Binary.subBytes(data, offset, 4));
                        offset += 4;

                        byte[] buf = Binary.subBytes(data, offset, pkLen);
                        offset += pkLen;

                        if ((pk = this.getPacket(buf[0], packet.protocol)) != null) {
                            pk.protocol = packet.protocol;

                            if (pk.protocol > ProtocolInfo.v_0_14_3 && pk.pid() == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(BatchPacket.class)) {
                                throw new IllegalStateException("Invalid BatchPacket inside BatchPacket");
                            }

                            pk.setBuffer(buf, 1);
                            pk.decode();
                            packets.add(pk);

                            if (pk.getOffset() <= 0) {
                                return null;
                            }
                        }
                    }else if(ProtocolInfo.v_0_11_0 < packet.protocol && packet.protocol <= ProtocolInfo.v_0_13_2){ // 0.12-0.13

                        int pkLen = Binary.readInt(Binary.subBytes(data, offset, 4));
                        offset += 4;

                        byte[] buf = Binary.subBytes(data, offset, pkLen);
                        offset += pkLen;

                        if((pk = this.getPacket(buf[0], packet.protocol)) != null){
                            pk.protocol = packet.protocol;

                            if (pk.pid() == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(BatchPacket.class)) {
                                throw new IllegalStateException("Invalid BatchPacket inside BatchPacket");
                            }

                            pk.setBuffer(buf, 1);
                            pk.decode();
                            player.handleDataPacket(pk);

                            if (pk.getOffset() <= 0) {
                                return null;
                            }
                        }
                    }else {

                        if((pk = this.getPacket(data[offset++], packet.protocol)) != null){
                            pk.protocol = packet.protocol;

                            if (pk.pid() == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(BatchPacket.class)) {
                                throw new IllegalStateException("Invalid BatchPacket inside BatchPacket");
                            }

                            pk.setBuffer(data, offset);
                            pk.decode();
                            player.handleDataPacket(pk);
                            offset += pk.getOffset();

                            if (pk.getOffset() <= 0) {
                                return null;
                            }
                        }
                    }
                }

                if(player == null){
                    return packets;
                }else{
                    for(DataPacket pk: packets){
                        player.handleDataPacket(pk);
                    }
                    return null;
                }
            } catch (Exception e) {
                if (Nukkit.DEBUG > 0) {
                    this.server.getLogger().debug("BatchPacket 0x" + Binary.bytesToHexString(packet.payload));
                    this.server.getLogger().logException(e);
                }
            }

        }else {// 0.16以其以上的操作
            int len = data.length;
            BinaryStream stream = new BinaryStream(data);
            try {
                List<DataPacket> packets = new ArrayList<>();
                while (stream.offset < len) {
                    byte[] buf = stream.getByteArray();

                    DataPacket pk;

                    if ((pk = this.getPacket(buf[0], packet.protocol)) != null) {

                        pk.protocol = packet.protocol;

                        if (pk.pid() == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(BatchPacket.class)) {
                            throw new IllegalStateException("Invalid BatchPacket inside BatchPacket");
                        }

                        pk.setBuffer(buf, 1);

                        pk.decode();

                        packets.add(pk);
                    }
                }

                processPackets(player, packets);

            } catch (Exception e) {
                if (Nukkit.DEBUG > 0) {
                    this.server.getLogger().debug("BatchPacket 0x" + Binary.bytesToHexString(packet.payload));
                    this.server.getLogger().logException(e);
                }
            }
        }
        return null;
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
        return getPacketPool(protocol).getPacket(id & 0xff);
    }

    public PacketPool getPacketPool(int protocol) {
        if (protocol > ProtocolInfo.v1_1_0) {
            return this.packetPoolCurrent;
        }else if(protocol <= ProtocolInfo.v_0_10_0){// 0.11
            return this.packetPool18;
        }else if(protocol <= ProtocolInfo.v_0_11_0){// 0.11
            return this.packetPool27;
        } else if(protocol <= ProtocolInfo.v_0_14_3){// 0.14
            return this.packetPool70;
        }else if(protocol <= ProtocolInfo.v_0_15_10){// 0.15
            return this.packetPool84;
        }else if(protocol < ProtocolInfo.v1_1_0){// 0.16
            return this.packetPool90;
        }
        return this.packetPool113;
    }

    public void setPacketPool(int protocol, PacketPool packetPool) {
        if (protocol > ProtocolInfo.v1_1_0) {
            this.packetPoolCurrent = packetPool;
        } else if(protocol <= ProtocolInfo.v_0_10_0){// 0.11
            this.packetPool18 = packetPool;
        } else if(protocol <= ProtocolInfo.v_0_11_0){// 0.11
             this.packetPool27 = packetPool;
        } else if(protocol <= ProtocolInfo.v_0_14_3){// 0.14
            this.packetPool70 = packetPool;
        }else if(protocol <= ProtocolInfo.v_0_15_10){// 0.15
            this.packetPool84 = packetPool;
        }else if(protocol < ProtocolInfo.v1_1_0){// 0.16
            this.packetPool90 = packetPool;
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
        /**
         * 0.9.0 - 0.10.5
         */
        this.packetPool18 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v_0_9_5)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v_0_9_5))
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(LoginPacket.class), LoginPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(PlayStatusPacket.class), PlayStatusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(DisconnectPacket.class), DisconnectPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(TextPacket.class), TextPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(SetTimePacket.class), SetTimePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(StartGamePacket.class), StartGamePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(AddPlayerPacket.class), AddPlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(RemovePlayerPacket.class), RemovePlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(AddEntityPacket.class), AddEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(RemoveEntityPacket.class), RemoveEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(AddItemEntityPacket.class), AddItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(TakeItemEntityPacket.class), TakeItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(MoveEntityAbsolutePacket.class), MoveEntityAbsolutePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(MovePlayerPacket.class), MovePlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(RemoveBlockPacket.class), RemoveBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(UpdateBlockPacket.class), UpdateBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(AddPaintingPacket.class), AddPaintingPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(ExplodePacket.class), ExplodePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(LevelEventPacket.class), LevelEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(BlockEventPacket.class), BlockEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(EntityEventPacket.class), EntityEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(MobEquipmentPacket.class), MobEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(MobArmorEquipmentPacket.class), MobArmorEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(InteractPacket.class), InteractPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(UseItemPacket.class), UseItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(PlayerActionPacket.class), PlayerActionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(HurtArmorPacket.class), HurtArmorPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(SetEntityDataPacket.class), SetEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(SetEntityMotionPacket.class), SetEntityMotionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(SetEntityLinkPacket.class), SetEntityLinkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(SetHealthPacket.class), SetHealthPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(SetSpawnPositionPacket.class), SetSpawnPositionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(AnimatePacket.class), AnimatePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(RespawnPacket.class), RespawnPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(DropItemPacket.class), DropItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(ContainerOpenPacket.class), ContainerOpenPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(ContainerClosePacket.class), ContainerClosePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(ContainerSetSlotPacket.class), ContainerSetSlotPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(ContainerSetDataPacket.class), ContainerSetDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(ContainerSetContentPacket.class), ContainerSetContentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(AdventureSettingsPacket.class), AdventureSettingsPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(BlockEntityDataPacket.class), BlockEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(PlayerInputPacket.class), PlayerInputPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(LevelChunkPacket.class), LevelChunkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(UnloadChunkPacket.class), UnloadChunkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(MoveEntityPosRotPacket.class), MoveEntityPosRotPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(RotateHeadPacket.class), RotateHeadPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(AddMobPacket.class), AddMobPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_9_5).get(SetDifficultyPacket.class), SetDifficultyPacket.class)
                .build();
        /**
         * 0.11.0 - 0.11.1
         */
        this.packetPool27 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v_0_11_0)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v_0_11_0))
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(LoginPacket.class), LoginPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(PlayStatusPacket.class), PlayStatusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(DisconnectPacket.class), DisconnectPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(BatchPacket.class), BatchPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(TextPacket.class), TextPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(SetTimePacket.class), SetTimePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(StartGamePacket.class), StartGamePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(AddPlayerPacket.class), AddPlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(AddEntityPacket.class), AddEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(RemoveEntityPacket.class), RemoveEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(AddItemEntityPacket.class), AddItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(TakeItemEntityPacket.class), TakeItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(MoveEntityAbsolutePacket.class), MoveEntityAbsolutePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(MovePlayerPacket.class), MovePlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(RemoveBlockPacket.class), RemoveBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(UpdateBlockPacket.class), UpdateBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(AddPaintingPacket.class), AddPaintingPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(ExplodePacket.class), ExplodePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(LevelEventPacket.class), LevelEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(BlockEventPacket.class), BlockEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(EntityEventPacket.class), EntityEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(MobEffectPacket.class), EntityEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(MobEquipmentPacket.class), MobEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(MobArmorEquipmentPacket.class), MobArmorEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(InteractPacket.class), InteractPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(UseItemPacket.class), UseItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(PlayerActionPacket.class), PlayerActionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(HurtArmorPacket.class), HurtArmorPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(SetEntityDataPacket.class), SetEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(SetEntityMotionPacket.class), SetEntityMotionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(SetEntityLinkPacket.class), SetEntityLinkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(SetHealthPacket.class), SetHealthPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(SetSpawnPositionPacket.class), SetSpawnPositionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(AnimatePacket.class), AnimatePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(RespawnPacket.class), RespawnPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(DropItemPacket.class), DropItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(ContainerOpenPacket.class), ContainerOpenPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(ContainerClosePacket.class), ContainerClosePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(ContainerSetSlotPacket.class), ContainerSetSlotPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(ContainerSetDataPacket.class), ContainerSetDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(ContainerSetContentPacket.class), ContainerSetContentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(AdventureSettingsPacket.class), AdventureSettingsPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(BlockEntityDataPacket.class), BlockEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(PlayerInputPacket.class), PlayerInputPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(LevelChunkPacket.class), LevelChunkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(SetDifficultyPacket.class), SetDifficultyPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(TransferPacket.class), TransferPacket.class)
                .build();

        /**
         * 0.14.1 - 0.14.3
         */
        this.packetPool70 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v_0_14_3)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v_0_14_3))
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(LoginPacket.class), LoginPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(PlayStatusPacket.class), PlayStatusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(DisconnectPacket.class), DisconnectPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(BatchPacket.class), BatchPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(TextPacket.class), TextPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetTimePacket.class), SetTimePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(StartGamePacket.class), StartGamePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(AddPlayerPacket.class), AddPlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(AddEntityPacket.class), AddEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(RemoveEntityPacket.class), RemoveEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(AddItemEntityPacket.class), AddItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(TakeItemEntityPacket.class), TakeItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(MoveEntityAbsolutePacket.class), MoveEntityAbsolutePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(MovePlayerPacket.class), MovePlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(RemoveBlockPacket.class), RemoveBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(UpdateBlockPacket.class), UpdateBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(AddPaintingPacket.class), AddPaintingPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ExplodePacket.class), ExplodePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(LevelEventPacket.class), LevelEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(BlockEventPacket.class), BlockEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(EntityEventPacket.class), EntityEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(MobEquipmentPacket.class), MobEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(MobArmorEquipmentPacket.class), MobArmorEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(InteractPacket.class), InteractPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(UseItemPacket.class), UseItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(PlayerActionPacket.class), PlayerActionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(HurtArmorPacket.class), HurtArmorPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetEntityDataPacket.class), SetEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetEntityMotionPacket.class), SetEntityMotionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetEntityLinkPacket.class), SetEntityLinkPacket.class)
                //.registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetHealthPacket.class.class), SetHealthPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetSpawnPositionPacket.class), SetSpawnPositionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(AnimatePacket.class), AnimatePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(RespawnPacket.class), RespawnPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(DropItemPacket.class), DropItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ContainerOpenPacket.class), ContainerOpenPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ContainerClosePacket.class), ContainerClosePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ContainerSetSlotPacket.class), ContainerSetSlotPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ContainerSetDataPacket.class), ContainerSetDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ContainerSetContentPacket.class), ContainerSetContentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(CraftingDataPacket.class), CraftingDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(CraftingEventPacket.class), CraftingEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(AdventureSettingsPacket.class), AdventureSettingsPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(BlockEntityDataPacket.class), BlockEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(PlayerInputPacket.class), PlayerInputPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(LevelChunkPacket.class), LevelChunkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetDifficultyPacket.class), SetDifficultyPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ChangeDimensionPacket.class), ChangeDimensionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(SetPlayerGameTypePacket.class), SetPlayerGameTypePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(PlayerListPacket.class), PlayerListPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(TelemetryEventPacket.class), TelemetryEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(RequestChunkRadiusPacket.class), RequestChunkRadiusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ReplaceSelectedItemPacket.class), ReplaceSelectedItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(ItemFrameDropItemPacket.class), ItemFrameDropItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_14_3).get(RemovePlayerPacket.class), RemovePlayerPacket.class)
                .build();

        /**
         * 0.15.0 - 0.15.10
         */
        this.packetPool84 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v_0_15_10)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v_0_15_10))
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(LoginPacket.class), LoginPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(PlayStatusPacket.class), PlayStatusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(DisconnectPacket.class), DisconnectPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(BatchPacket.class), BatchPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(TextPacket.class), TextPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetTimePacket.class), SetTimePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(StartGamePacket.class), StartGamePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(AddPlayerPacket.class), AddPlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(AddEntityPacket.class), AddEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(RemoveEntityPacket.class), RemoveEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(AddItemEntityPacket.class), AddItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(TakeItemEntityPacket.class), TakeItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(MoveEntityAbsolutePacket.class), MoveEntityAbsolutePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(MovePlayerPacket.class), MovePlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(RemoveBlockPacket.class), RemoveBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(UpdateBlockPacket.class), UpdateBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(AddPaintingPacket.class), AddPaintingPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ExplodePacket.class), ExplodePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(LevelEventPacket.class), LevelEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(BlockEventPacket.class), BlockEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(EntityEventPacket.class), EntityEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(MobEquipmentPacket.class), MobEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(MobArmorEquipmentPacket.class), MobArmorEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(InteractPacket.class), InteractPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(UseItemPacket.class), UseItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(PlayerActionPacket.class), PlayerActionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(HurtArmorPacket.class), HurtArmorPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetEntityDataPacket.class), SetEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetEntityMotionPacket.class), SetEntityMotionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetEntityLinkPacket.class), SetEntityLinkPacket.class)
                //.registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetHealthPacket.class), SetHealthPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetSpawnPositionPacket.class), SetSpawnPositionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(AnimatePacket.class), AnimatePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(RespawnPacket.class), RespawnPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(DropItemPacket.class), DropItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ContainerOpenPacket.class), ContainerOpenPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ContainerClosePacket.class), ContainerClosePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ContainerSetSlotPacket.class), ContainerSetSlotPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ContainerSetDataPacket.class), ContainerSetDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ContainerSetContentPacket.class), ContainerSetContentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(CraftingDataPacket.class), CraftingDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(CraftingEventPacket.class), CraftingEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(AdventureSettingsPacket.class), AdventureSettingsPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(BlockEntityDataPacket.class), BlockEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(PlayerInputPacket.class), PlayerInputPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(LevelChunkPacket.class), LevelChunkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetDifficultyPacket.class), SetDifficultyPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ChangeDimensionPacket.class), ChangeDimensionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(SetPlayerGameTypePacket.class), SetPlayerGameTypePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(PlayerListPacket.class), PlayerListPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(TelemetryEventPacket.class), TelemetryEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(RequestChunkRadiusPacket.class), RequestChunkRadiusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ReplaceSelectedItemPacket.class), ReplaceSelectedItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(ItemFrameDropItemPacket.class), ItemFrameDropItemPacket.class)
                //.registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(RemovePlayerPacket.class), RemovePlayerPacket.class)
                .build();

        /**
         * 0.16
         */
        this.packetPool90 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v_0_16_0)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v_0_16_0))
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(LoginPacket.class), LoginPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(PlayStatusPacket.class), PlayStatusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(DisconnectPacket.class), DisconnectPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(BatchPacket.class), BatchPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(TextPacket.class), TextPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetTimePacket.class), SetTimePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(StartGamePacket.class), StartGamePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(AddPlayerPacket.class), AddPlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(AddEntityPacket.class), AddEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(RemoveEntityPacket.class), RemoveEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(AddItemEntityPacket.class), AddItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(TakeItemEntityPacket.class), TakeItemEntityPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(MoveEntityAbsolutePacket.class), MoveEntityAbsolutePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(MovePlayerPacket.class), MovePlayerPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(RemoveBlockPacket.class), RemoveBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(UpdateBlockPacket.class), UpdateBlockPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(AddPaintingPacket.class), AddPaintingPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ExplodePacket.class), ExplodePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(LevelEventPacket.class), LevelEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(BlockEventPacket.class), BlockEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(EntityEventPacket.class), EntityEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(MobEquipmentPacket.class), MobEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(MobArmorEquipmentPacket.class), MobArmorEquipmentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(InteractPacket.class), InteractPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(UseItemPacket.class), UseItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(PlayerActionPacket.class), PlayerActionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(HurtArmorPacket.class), HurtArmorPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetEntityDataPacket.class), SetEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetEntityMotionPacket.class), SetEntityMotionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetEntityLinkPacket.class), SetEntityLinkPacket.class)
                //.registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetHealthPacket.class), SetHealthPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetSpawnPositionPacket.class), SetSpawnPositionPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(AnimatePacket.class), AnimatePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(RespawnPacket.class), RespawnPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(DropItemPacket.class), DropItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ContainerOpenPacket.class), ContainerOpenPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ContainerClosePacket.class), ContainerClosePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ContainerSetSlotPacket.class), ContainerSetSlotPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ContainerSetDataPacket.class), ContainerSetDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ContainerSetContentPacket.class), ContainerSetContentPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(CraftingDataPacket.class), CraftingDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(CraftingEventPacket.class), CraftingEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(AdventureSettingsPacket.class), AdventureSettingsPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(BlockEntityDataPacket.class), BlockEntityDataPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(PlayerInputPacket.class), PlayerInputPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(LevelChunkPacket.class), LevelChunkPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetDifficultyPacket.class), SetDifficultyPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ChangeDimensionPacket.class), ChangeDimensionPacket.class)
                //.registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SetPlayerGameTypePacket.class), SetPlayerGameTypePacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(PlayerListPacket.class), PlayerListPacket.class)
                //.registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(TelemetryEventPacket.class), TelemetryEventPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(RequestChunkRadiusPacket.class), RequestChunkRadiusPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ReplaceSelectedItemPacket.class), ReplaceSelectedItemPacket.class)
                .registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(ItemFrameDropItemPacket.class), ItemFrameDropItemPacket.class)
                //.registerPacket(ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(RemovePlayerPacket.class), RemovePlayerPacket.class)
                .build();

        this.packetPool113 = PacketPool.builder()
                .protocolVersion(ProtocolInfo.v1_1_0)
                .minecraftVersion(Utils.getVersionByProtocol(ProtocolInfo.v1_1_0))
                .registerPacket(ProtocolInfoV113.ADD_ENTITY_PACKET, AddEntityPacket.class)
                .registerPacket(ProtocolInfoV113.ADD_HANGING_ENTITY_PACKET, AddHangingEntityPacketV113.class)
                .registerPacket(ProtocolInfoV113.ADD_ITEM_ENTITY_PACKET, AddItemEntityPacket.class)
                .registerPacket(ProtocolInfoV113.ADD_ITEM_PACKET, AddItemPacketV113.class)
                .registerPacket(ProtocolInfoV113.ADD_PAINTING_PACKET, AddPaintingPacket.class)
                .registerPacket(ProtocolInfoV113.ADD_PLAYER_PACKET, AddPlayerPacket.class)
                .registerPacket(ProtocolInfoV113.ADVENTURE_SETTINGS_PACKET, AdventureSettingsPacket.class)
                .registerPacket(ProtocolInfoV113.ANIMATE_PACKET, AnimatePacket.class)
                .registerPacket(ProtocolInfoV113.AVAILABLE_COMMANDS_PACKET, AvailableCommandsPacket.class)
                .registerPacket(ProtocolInfoV113.BATCH_PACKET, BatchPacket.class)
                .registerPacket(ProtocolInfoV113.BLOCK_ENTITY_DATA_PACKET, BlockEntityDataPacket.class)
                .registerPacket(ProtocolInfoV113.BLOCK_EVENT_PACKET, BlockEventPacket.class)
                .registerPacket(ProtocolInfoV113.BLOCK_PICK_REQUEST_PACKET, BlockPickRequestPacket.class)
                .registerPacket(ProtocolInfoV113.BOSS_EVENT_PACKET, BossEventPacket.class)
                .registerPacket(ProtocolInfoV113.CHANGE_DIMENSION_PACKET, ChangeDimensionPacket.class)
                .registerPacket(ProtocolInfoV113.CHUNK_RADIUS_UPDATED_PACKET, ChunkRadiusUpdatedPacket.class)
                .registerPacket(ProtocolInfoV113.CLIENTBOUND_MAP_ITEM_DATA_PACKET, ClientboundMapItemDataPacket.class)
                .registerPacket(ProtocolInfoV113.COMMAND_STEP_PACKET, CommandStepPacketV113.class)
                .registerPacket(ProtocolInfoV113.CONTAINER_CLOSE_PACKET, ContainerClosePacket.class)
                .registerPacket(ProtocolInfoV113.CONTAINER_OPEN_PACKET, ContainerOpenPacket.class)
                .registerPacket(ProtocolInfoV113.CONTAINER_SET_CONTENT_PACKET, ContainerSetContentPacketV113.class)
                .registerPacket(ProtocolInfoV113.CONTAINER_SET_DATA_PACKET, ContainerSetDataPacket.class)
                .registerPacket(ProtocolInfoV113.CONTAINER_SET_SLOT_PACKET, ContainerSetSlotPacketV113.class)
                .registerPacket(ProtocolInfoV113.CRAFTING_DATA_PACKET, CraftingDataPacket.class)
                .registerPacket(ProtocolInfoV113.CRAFTING_EVENT_PACKET, CraftingEventPacket.class)
                .registerPacket(ProtocolInfoV113.DISCONNECT_PACKET, DisconnectPacket.class)
                .registerPacket(ProtocolInfoV113.DROP_ITEM_PACKET, DropItemPacketV113.class)
                .registerPacket(ProtocolInfoV113.ENTITY_EVENT_PACKET, EntityEventPacket.class)
                .registerPacket(ProtocolInfoV113.UPDATE_ATTRIBUTES_PACKET, UpdateAttributesPacket.class)
                .registerPacket(ProtocolInfoV113.ENTITY_FALL_PACKET, EntityFallPacket.class)
                .registerPacket(ProtocolInfoV113.EXPLODE_PACKET, ExplodePacketV113.class)
                .registerPacket(ProtocolInfoV113.FULL_CHUNK_DATA_PACKET, LevelChunkPacket.class)
                .registerPacket(ProtocolInfoV113.GAME_RULES_CHANGED_PACKET, GameRulesChangedPacket.class)
                .registerPacket(ProtocolInfoV113.HURT_ARMOR_PACKET, HurtArmorPacket.class)
                .registerPacket(ProtocolInfoV113.INTERACT_PACKET, InteractPacket.class)
                .registerPacket(ProtocolInfoV113.INVENTORY_ACTION_PACKET, InventoryActionPacketV113.class)
                .registerPacket(ProtocolInfoV113.ITEM_FRAME_DROP_ITEM_PACKET, ItemFrameDropItemPacket.class)
                .registerPacket(ProtocolInfoV113.LEVEL_EVENT_PACKET, LevelEventPacket.class)
                .registerPacket(ProtocolInfoV113.LEVEL_SOUND_EVENT_PACKET, LevelSoundEventPacketV1.class)
                .registerPacket(ProtocolInfoV113.LOGIN_PACKET, LoginPacket.class)
                .registerPacket(ProtocolInfoV113.MAP_INFO_REQUEST_PACKET, MapInfoRequestPacket.class)
                .registerPacket(ProtocolInfoV113.MOB_ARMOR_EQUIPMENT_PACKET, MobArmorEquipmentPacket.class)
                .registerPacket(ProtocolInfoV113.MOB_EQUIPMENT_PACKET, MobEquipmentPacket.class)
                .registerPacket(ProtocolInfoV113.MOVE_ENTITY_PACKET, MoveEntityAbsolutePacket.class)
                .registerPacket(ProtocolInfoV113.MOVE_PLAYER_PACKET, MovePlayerPacket.class)
                .registerPacket(ProtocolInfoV113.PLAYER_ACTION_PACKET, PlayerActionPacket.class)
                .registerPacket(ProtocolInfoV113.PLAYER_INPUT_PACKET, PlayerInputPacket.class)
                .registerPacket(ProtocolInfoV113.PLAYER_LIST_PACKET, PlayerListPacket.class)
                .registerPacket(ProtocolInfoV113.PLAY_SOUND_PACKET, PlaySoundPacket.class)
                .registerPacket(ProtocolInfoV113.PLAY_STATUS_PACKET, PlayStatusPacket.class)
                .registerPacket(ProtocolInfoV113.REMOVE_BLOCK_PACKET, RemoveBlockPacketV113.class)
                .registerPacket(ProtocolInfoV113.REMOVE_ENTITY_PACKET, RemoveEntityPacket.class)
                .registerPacket(ProtocolInfoV113.REPLACE_ITEM_IN_SLOT_PACKET, ReplaceItemInSlotPacketV113.class)
                .registerPacket(ProtocolInfoV113.REQUEST_CHUNK_RADIUS_PACKET, RequestChunkRadiusPacket.class)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACKS_INFO_PACKET, ResourcePacksInfoPacket.class)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_STACK_PACKET, ResourcePackStackPacket.class)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_CLIENT_RESPONSE_PACKET, ResourcePackClientResponsePacket.class)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_DATA_INFO_PACKET, ResourcePackDataInfoPacket.class)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_CHUNK_DATA_PACKET, ResourcePackChunkDataPacket.class)
                .registerPacket(ProtocolInfoV113.RESOURCE_PACK_CHUNK_REQUEST_PACKET, ResourcePackChunkRequestPacket.class)
                .registerPacket(ProtocolInfoV113.RESPAWN_PACKET, RespawnPacket.class)
                .registerPacket(ProtocolInfoV113.RIDER_JUMP_PACKET, RiderJumpPacket.class)
                .registerPacket(ProtocolInfoV113.SET_COMMANDS_ENABLED_PACKET, SetCommandsEnabledPacket.class)
                .registerPacket(ProtocolInfoV113.SET_DIFFICULTY_PACKET, SetDifficultyPacket.class)
                .registerPacket(ProtocolInfoV113.SET_ENTITY_DATA_PACKET, SetEntityDataPacket.class)
                .registerPacket(ProtocolInfoV113.SET_ENTITY_LINK_PACKET, SetEntityLinkPacket.class)
                .registerPacket(ProtocolInfoV113.SET_ENTITY_MOTION_PACKET, SetEntityMotionPacket.class)
                .registerPacket(ProtocolInfoV113.SET_HEALTH_PACKET, SetHealthPacket.class)
                .registerPacket(ProtocolInfoV113.SET_PLAYER_GAME_TYPE_PACKET, SetPlayerGameTypePacket.class)
                .registerPacket(ProtocolInfoV113.SET_SPAWN_POSITION_PACKET, SetSpawnPositionPacket.class)
                .registerPacket(ProtocolInfoV113.SET_TITLE_PACKET, SetTitlePacket.class)
                .registerPacket(ProtocolInfoV113.SET_TIME_PACKET, SetTimePacket.class)
                .registerPacket(ProtocolInfoV113.SHOW_CREDITS_PACKET, ShowCreditsPacket.class)
                .registerPacket(ProtocolInfoV113.SPAWN_EXPERIENCE_ORB_PACKET, SpawnExperienceOrbPacket.class)
                .registerPacket(ProtocolInfoV113.START_GAME_PACKET, StartGamePacket.class)
                .registerPacket(ProtocolInfoV113.TAKE_ITEM_ENTITY_PACKET, TakeItemEntityPacket.class)
                .registerPacket(ProtocolInfoV113.TEXT_PACKET, TextPacket.class)
                .registerPacket(ProtocolInfoV113.UPDATE_BLOCK_PACKET, UpdateBlockPacket.class)
                .registerPacket(ProtocolInfoV113.USE_ITEM_PACKET, UseItemPacketV113.class)
                .registerPacket(ProtocolInfoV113.UPDATE_TRADE_PACKET, UpdateTradePacket.class)
                .build();

        this.packetPoolCurrent = PacketPool.builder()
                .protocolVersion(ProtocolInfo.CURRENT_PROTOCOL)
                .minecraftVersion(ProtocolInfo.MINECRAFT_VERSION)
                .registerPacket(ProtocolInfo.SERVER_TO_CLIENT_HANDSHAKE_PACKET, ServerToClientHandshakePacket.class)
                .registerPacket(ProtocolInfo.CLIENT_TO_SERVER_HANDSHAKE_PACKET, ClientToServerHandshakePacket.class)
                .registerPacket(ProtocolInfo.ADD_ENTITY_PACKET, AddEntityPacket.class)
                .registerPacket(ProtocolInfo.ADD_ITEM_ENTITY_PACKET, AddItemEntityPacket.class)
                .registerPacket(ProtocolInfo.ADD_PAINTING_PACKET, AddPaintingPacket.class)
                .registerPacket(ProtocolInfo.TICK_SYNC_PACKET, TickSyncPacket.class)
                .registerPacket(ProtocolInfo.ADD_PLAYER_PACKET, AddPlayerPacket.class)
                .registerPacket(ProtocolInfo.ADVENTURE_SETTINGS_PACKET, AdventureSettingsPacket.class)
                .registerPacket(ProtocolInfo.ANIMATE_PACKET, AnimatePacket.class)
                .registerPacket(ProtocolInfo.ANVIL_DAMAGE_PACKET, AnvilDamagePacket.class)
                .registerPacket(ProtocolInfo.AVAILABLE_COMMANDS_PACKET, AvailableCommandsPacket.class)
                .registerPacket(ProtocolInfo.BATCH_PACKET, BatchPacket.class)
                .registerPacket(ProtocolInfo.BLOCK_ENTITY_DATA_PACKET, BlockEntityDataPacket.class)
                .registerPacket(ProtocolInfo.BLOCK_EVENT_PACKET, BlockEventPacket.class)
                .registerPacket(ProtocolInfo.BLOCK_PICK_REQUEST_PACKET, BlockPickRequestPacket.class)
                .registerPacket(ProtocolInfo.BOOK_EDIT_PACKET, BookEditPacket.class)
                .registerPacket(ProtocolInfo.BOSS_EVENT_PACKET, BossEventPacket.class)
                .registerPacket(ProtocolInfo.CHANGE_DIMENSION_PACKET, ChangeDimensionPacket.class)
                .registerPacket(ProtocolInfo.CHUNK_RADIUS_UPDATED_PACKET, ChunkRadiusUpdatedPacket.class)
                .registerPacket(ProtocolInfo.CLIENTBOUND_MAP_ITEM_DATA_PACKET, ClientboundMapItemDataPacket.class)
                .registerPacket(ProtocolInfo.COMMAND_REQUEST_PACKET, CommandRequestPacket.class)
                .registerPacket(ProtocolInfo.CONTAINER_CLOSE_PACKET, ContainerClosePacket.class)
                .registerPacket(ProtocolInfo.CONTAINER_OPEN_PACKET, ContainerOpenPacket.class)
                .registerPacket(ProtocolInfo.CONTAINER_SET_DATA_PACKET, ContainerSetDataPacket.class)
                .registerPacket(ProtocolInfo.CRAFTING_DATA_PACKET, CraftingDataPacket.class)
                .registerPacket(ProtocolInfo.CRAFTING_EVENT_PACKET, CraftingEventPacket.class)
                .registerPacket(ProtocolInfo.DISCONNECT_PACKET, DisconnectPacket.class)
                .registerPacket(ProtocolInfo.ENTITY_EVENT_PACKET, EntityEventPacket.class)
                .registerPacket(ProtocolInfo.ENTITY_FALL_PACKET, EntityFallPacket.class)
                .registerPacket(ProtocolInfo.FULL_CHUNK_DATA_PACKET, LevelChunkPacket.class)
                .registerPacket(ProtocolInfo.GAME_RULES_CHANGED_PACKET, GameRulesChangedPacket.class)
                .registerPacket(ProtocolInfo.HURT_ARMOR_PACKET, HurtArmorPacket.class)
                .registerPacket(ProtocolInfo.INTERACT_PACKET, InteractPacket.class)
                .registerPacket(ProtocolInfo.INVENTORY_CONTENT_PACKET, InventoryContentPacket.class)
                .registerPacket(ProtocolInfo.INVENTORY_SLOT_PACKET, InventorySlotPacket.class)
                .registerPacket(ProtocolInfo.INVENTORY_TRANSACTION_PACKET, InventoryTransactionPacket.class)
                .registerPacket(ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET, ItemFrameDropItemPacket.class)
                .registerPacket(ProtocolInfo.LEVEL_EVENT_PACKET, LevelEventPacket.class)
                .registerPacket(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V1, LevelSoundEventPacketV1.class)
                .registerPacket(ProtocolInfo.LOGIN_PACKET, LoginPacket.class)
                .registerPacket(ProtocolInfo.MAP_INFO_REQUEST_PACKET, MapInfoRequestPacket.class)
                .registerPacket(ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET, MobArmorEquipmentPacket.class)
                .registerPacket(ProtocolInfo.MOB_EQUIPMENT_PACKET, MobEquipmentPacket.class)
                .registerPacket(ProtocolInfo.MODAL_FORM_REQUEST_PACKET, ModalFormRequestPacket.class)
                .registerPacket(ProtocolInfo.MODAL_FORM_RESPONSE_PACKET, ModalFormResponsePacket.class)
                .registerPacket(ProtocolInfo.MOVE_ENTITY_ABSOLUTE_PACKET, MoveEntityAbsolutePacket.class)
                .registerPacket(ProtocolInfo.MOVE_PLAYER_PACKET, MovePlayerPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_ACTION_PACKET, PlayerActionPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_INPUT_PACKET, PlayerInputPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_LIST_PACKET, PlayerListPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_HOTBAR_PACKET, PlayerHotbarPacket.class)
                .registerPacket(ProtocolInfo.PLAY_SOUND_PACKET, PlaySoundPacket.class)
                .registerPacket(ProtocolInfo.PLAY_STATUS_PACKET, PlayStatusPacket.class)
                .registerPacket(ProtocolInfo.REMOVE_ENTITY_PACKET, RemoveEntityPacket.class)
                .registerPacket(ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET, RequestChunkRadiusPacket.class)
                .registerPacket(ProtocolInfo.RESOURCE_PACKS_INFO_PACKET, ResourcePacksInfoPacket.class)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_STACK_PACKET, ResourcePackStackPacket.class)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET, ResourcePackClientResponsePacket.class)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_DATA_INFO_PACKET, ResourcePackDataInfoPacket.class)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_CHUNK_DATA_PACKET, ResourcePackChunkDataPacket.class)
                .registerPacket(ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET, ResourcePackChunkRequestPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_SKIN_PACKET, PlayerSkinPacket.class)
                .registerPacket(ProtocolInfo.RESPAWN_PACKET, RespawnPacket.class)
                .registerPacket(ProtocolInfo.RIDER_JUMP_PACKET, RiderJumpPacket.class)
                .registerPacket(ProtocolInfo.SET_COMMANDS_ENABLED_PACKET, SetCommandsEnabledPacket.class)
                .registerPacket(ProtocolInfo.SET_DIFFICULTY_PACKET, SetDifficultyPacket.class)
                .registerPacket(ProtocolInfo.SET_ENTITY_DATA_PACKET, SetEntityDataPacket.class)
                .registerPacket(ProtocolInfo.SET_ENTITY_LINK_PACKET, SetEntityLinkPacket.class)
                .registerPacket(ProtocolInfo.SET_ENTITY_MOTION_PACKET, SetEntityMotionPacket.class)
                .registerPacket(ProtocolInfo.SET_HEALTH_PACKET, SetHealthPacket.class)
                .registerPacket(ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET, SetPlayerGameTypePacket.class)
                .registerPacket(ProtocolInfo.SET_SPAWN_POSITION_PACKET, SetSpawnPositionPacket.class)
                .registerPacket(ProtocolInfo.SET_TITLE_PACKET, SetTitlePacket.class)
                .registerPacket(ProtocolInfo.SET_TIME_PACKET, SetTimePacket.class)
                .registerPacket(ProtocolInfo.SERVER_SETTINGS_REQUEST_PACKET, ServerSettingsRequestPacket.class)
                .registerPacket(ProtocolInfo.SERVER_SETTINGS_RESPONSE_PACKET, ServerSettingsResponsePacket.class)
                .registerPacket(ProtocolInfo.SHOW_CREDITS_PACKET, ShowCreditsPacket.class)
                .registerPacket(ProtocolInfo.SPAWN_EXPERIENCE_ORB_PACKET, SpawnExperienceOrbPacket.class)
                .registerPacket(ProtocolInfo.START_GAME_PACKET, StartGamePacket.class)
                .registerPacket(ProtocolInfo.TAKE_ITEM_ENTITY_PACKET, TakeItemEntityPacket.class)
                .registerPacket(ProtocolInfo.TEXT_PACKET, TextPacket.class)
                .registerPacket(ProtocolInfo.SERVER_POST_MOVE_POSITION, ServerPostMovePositionPacket.class)
                .registerPacket(ProtocolInfo.TRANSFER_PACKET, TransferPacket.class)
                .registerPacket(ProtocolInfo.UPDATE_ATTRIBUTES_PACKET, UpdateAttributesPacket.class)
                .registerPacket(ProtocolInfo.UPDATE_BLOCK_PACKET, UpdateBlockPacket.class)
                .registerPacket(ProtocolInfo.COMMAND_BLOCK_UPDATE_PACKET, CommandBlockUpdatePacket.class)
                .registerPacket(ProtocolInfo.COMMAND_OUTPUT_PACKET, CommandOutputPacket.class)
                .registerPacket(ProtocolInfo.UPDATE_TRADE_PACKET, UpdateTradePacket.class)
                .registerPacket(ProtocolInfo.SET_DISPLAY_OBJECTIVE_PACKET, SetDisplayObjectivePacket.class)
                .registerPacket(ProtocolInfo.SET_SCORE_PACKET, SetScorePacket.class)
                .registerPacket(ProtocolInfo.MOVE_ENTITY_DELTA_PACKET, MoveEntityDeltaPacket.class)
                .registerPacket(ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET, SetLocalPlayerAsInitializedPacket.class)
                .registerPacket(ProtocolInfo.NETWORK_STACK_LATENCY_PACKET, NetworkStackLatencyPacket.class)
                .registerPacket(ProtocolInfo.UPDATE_SOFT_ENUM_PACKET, UpdateSoftEnumPacket.class)
                .registerPacket(ProtocolInfo.NETWORK_CHUNK_PUBLISHER_UPDATE_PACKET, NetworkChunkPublisherUpdatePacket.class)
                .registerPacket(ProtocolInfo.AVAILABLE_ENTITY_IDENTIFIERS_PACKET, AvailableEntityIdentifiersPacket.class)
                .registerPacket(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V2, LevelSoundEventPacket.class)
                .registerPacket(ProtocolInfo.SCRIPT_CUSTOM_EVENT_PACKET, ScriptCustomEventPacket.class)
                .registerPacket(ProtocolInfo.SPAWN_PARTICLE_EFFECT_PACKET, SpawnParticleEffectPacket.class)
                .registerPacket(ProtocolInfo.BIOME_DEFINITION_LIST_PACKET, BiomeDefinitionListPacket.class)
                .registerPacket(ProtocolInfo.LEVEL_SOUND_EVENT_PACKET, LevelSoundEventPacket.class)
                .registerPacket(ProtocolInfo.LEVEL_EVENT_GENERIC_PACKET, LevelEventGenericPacket.class)
                .registerPacket(ProtocolInfo.LECTERN_UPDATE_PACKET, LecternUpdatePacket.class)
                .registerPacket(ProtocolInfo.VIDEO_STREAM_CONNECT_PACKET, VideoStreamConnectPacket.class)
                .registerPacket(ProtocolInfo.CLIENT_CACHE_STATUS_PACKET, ClientCacheStatusPacket.class)
                .registerPacket(ProtocolInfo.MAP_CREATE_LOCKED_COPY_PACKET, MapCreateLockedCopyPacket.class)
                .registerPacket(ProtocolInfo.ON_SCREEN_TEXTURE_ANIMATION_PACKET, OnScreenTextureAnimationPacket.class)
                .registerPacket(ProtocolInfo.COMPLETED_USING_ITEM_PACKET, CompletedUsingItemPacket.class)
                .registerPacket(ProtocolInfo.NETWORK_SETTINGS_PACKET, NetworkSettingsPacket.class)
                .registerPacket(ProtocolInfo.CODE_BUILDER_PACKET, CodeBuilderPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_AUTH_INPUT_PACKET, PlayerAuthInputPacket.class)
                .registerPacket(ProtocolInfo.CREATIVE_CONTENT_PACKET, CreativeContentPacket.class)
                .registerPacket(ProtocolInfo.DEBUG_INFO_PACKET, DebugInfoPacket.class)
                .registerPacket(ProtocolInfo.EMOTE_LIST_PACKET, EmoteListPacket.class)
                .registerPacket(ProtocolInfo.PACKET_VIOLATION_WARNING_PACKET, PacketViolationWarningPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_ARMOR_DAMAGE_PACKET, PlayerArmorDamagePacket.class)
                .registerPacket(ProtocolInfo.PLAYER_ENCHANT_OPTIONS_PACKET, PlayerEnchantOptionsPacket.class)
                .registerPacket(ProtocolInfo.UPDATE_PLAYER_GAME_TYPE_PACKET, UpdatePlayerGameTypePacket.class)
                .registerPacket(ProtocolInfo.UPDATE_ABILITIES_PACKET, UpdateAbilitiesPacket.class)
                .registerPacket(ProtocolInfo.REQUEST_ABILITY_PACKET, RequestAbilityPacket.class)
                .registerPacket(ProtocolInfo.UPDATE_ADVENTURE_SETTINGS_PACKET, UpdateAdventureSettingsPacket.class)
                .registerPacket(ProtocolInfo.DEATH_INFO_PACKET, DeathInfoPacket.class)
                .registerPacket(ProtocolInfo.EMOTE_PACKET, EmotePacket.class)
                .registerPacket(ProtocolInfo.ANIMATE_ENTITY_PACKET, AnimateEntityPacket.class)
                .registerPacket(ProtocolInfo.PLAYER_FOG_PACKET, PlayerFogPacket.class)
                .registerPacket(ProtocolInfo.ITEM_COMPONENT_PACKET, ItemComponentPacket.class)
                .registerPacket(ProtocolInfo.FILTER_TEXT_PACKET, FilterTextPacket.class)
                .registerPacket(ProtocolInfo.SYNC_ENTITY_PROPERTY_PACKET, SyncEntityPropertyPacket.class)
                .registerPacket(ProtocolInfo.NPC_DIALOGUE_PACKET, NPCDialoguePacket.class)
                .registerPacket(ProtocolInfo.TOAST_REQUEST_PACKET, ToastRequestPacket.class)
                .registerPacket(ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET, RequestNetworkSettingsPacket.class)
                .registerPacket(ProtocolInfo.UPDATE_CLIENT_INPUT_LOCKS, UpdateClientInputLocksPacket.class)
                .registerPacket(ProtocolInfo.CAMERA_PRESETS_PACKET, CameraPresetsPacket.class)

                .registerPacket(ProtocolInfo.CAMERA_INSTRUCTION_PACKET, CameraInstructionPacket.class)
                .registerPacket(ProtocolInfo.TRIM_DATA_PACKET, TrimDataPacket.class)
                .registerPacket(ProtocolInfo.OPEN_SIGN_PACKET, OpenSignPacket.class)
                .registerPacket(ProtocolInfo.AGENT_ANIMATION_PACKET, AgentAnimationPacket.class)
                .registerPacket(ProtocolInfo.REFRESH_ENTITLEMENTS_PACKET, RefreshEntitlementsPacket.class)
                .registerPacket(ProtocolInfo.TOGGLE_CRAFTER_SLOT_REQUEST_PACKET, ToggleCrafterSlotRequestPacket.class)
                .registerPacket(ProtocolInfo.SET_PLAYER_INVENTORY_OPTIONS_PACKET, SetPlayerInventoryOptionsPacket.class)
                .registerPacket(ProtocolInfo.SET_HUD_PACKET, SetHudPacket.class)
                .registerPacket(ProtocolInfo.AWARD_ACHIEVEMENT_PACKET, AwardAchievementPacket.class)
                .registerPacket(ProtocolInfo.CLIENTBOUND_CLOSE_FORM_PACKET, ClientboundCloseFormPacket.class)
                .registerPacket(ProtocolInfo.SERVERBOUND_LOADING_SCREEN_PACKET, ServerboundLoadingScreenPacket.class)
                .registerPacket(ProtocolInfo.JIGSAW_STRUCTURE_DATA_PACKET, JigsawStructureDataPacket.class)
                .registerPacket(ProtocolInfo.CURRENT_STRUCTURE_FEATURE_PACKET, CurrentStructureFeaturePacket.class)
                .registerPacket(ProtocolInfo.SERVERBOUND_DIAGNOSTICS_PACKET, ServerboundDiagnosticsPacket.class)
                .build();
    }

    @AllArgsConstructor
    @Data
    public static class NetWorkStatisticData {
        private long upload;
        private long download;
    }
}
