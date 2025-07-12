package cn.nukkit.network;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.server.BatchPacketsEvent;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.SnappyCompression;
import cn.nukkit.utils.Zlib;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主要处理服务器广播数据包
 * 因为每个玩家的协议版本不同，所以在这个线程提前根据玩家协议进行编码
 */
public class BatchingHelper {

    private final ExecutorService threadedExecutor;

    public BatchingHelper() {
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        builder.setNameFormat("Batching Executor");
        this.threadedExecutor = Executors.newSingleThreadExecutor(builder.build());
    }

    public void batchPackets(Player[] players, DataPacket[] packets) {
        if (players == null || packets == null || players.length == 0 || packets.length == 0) {
            return;
        }

        BatchPacketsEvent ev = new BatchPacketsEvent(players, packets);
        ev.call();
        if (ev.isCancelled()) {
            return;
        }

        this.threadedExecutor.execute(() -> this.batchAndSendPackets(players, packets));
    }

    private void batchAndSendPackets(Player[] players, DataPacket[] packets) {
        //只有一个玩家时直接发送
        //未知原因 注释掉会导致客户端容易闪退
        if (players.length == 1) {
            for (DataPacket packet : packets) {
                packet.protocol = players[0].protocol;
                packet.gameVersion = players[0].getGameVersion();
                players[0].getNetworkSession().sendPacket(packet);
           }
           return;
        }

        Object2ObjectMap<GameVersion, ObjectList<Player>> targets = new Object2ObjectOpenHashMap<>();
        for (Player player : players) {
            targets.computeIfAbsent(player.getGameVersion(), i -> new ObjectArrayList<>()).add(player);
        }

        // Encoded packets by encoding protocol
        Object2ObjectMap<GameVersion, ObjectList<DataPacket>> encodedPackets = new Object2ObjectOpenHashMap<>();

        for (DataPacket packet : packets) {
            Object2ObjectMap<GameVersion, GameVersion> encodingProtocols = new Object2ObjectOpenHashMap<>();
            for (GameVersion gameVersion : targets.keySet()) {
                // TODO: encode only by encoding protocols
                // No need to have all versions here
                encodingProtocols.put(gameVersion, gameVersion);
            }

            Object2ObjectMap<GameVersion, DataPacket> encodedPacket = new Object2ObjectOpenHashMap<>();
            for (GameVersion encodingProtocol : encodingProtocols.values()) {
                if (!encodedPacket.containsKey(encodingProtocol)) {
                    DataPacket pk = packet.clone();
                    pk.protocol = encodingProtocol.getProtocol();
                    pk.gameVersion = encodingProtocol;
                    pk.tryEncode();
                    encodedPacket.put(encodingProtocol, pk);
                }
            }

            for (GameVersion gameVersion : encodingProtocols.values()) {
                GameVersion encodingGameVersion = encodingProtocols.get(gameVersion);
                encodedPackets.computeIfAbsent(gameVersion, i -> new ObjectArrayList<>()).add(encodedPacket.get(encodingGameVersion));
            }
        }

        for (GameVersion gameVersion : targets.keySet()) {
            ObjectList<DataPacket> packetList = encodedPackets.get(gameVersion);
            ObjectList<Player> finalTargets = targets.get(gameVersion);

            BinaryStream batched = new BinaryStream();
            for (DataPacket packet : packetList) {
                if (packet instanceof BatchPacket) {
                    throw new RuntimeException("Cannot batch BatchPacket");
                }
                packet.tryEncode();
                byte[] buf = packet.getBuffer();
                batched.putUnsignedVarInt(buf.length);
                batched.put(buf);
            }

            try {
                byte[] bytes = Binary.appendBytes(batched.getBuffer());
                BatchPacket pk = new BatchPacket();
                if (Server.getInstance().useSnappy && gameVersion.getProtocol() >= ProtocolInfo.v1_19_30_23) {
                    pk.payload = SnappyCompression.compress(bytes);
                } else if (gameVersion.getProtocol() >= ProtocolInfo.v1_16_0) {
                    pk.payload = Zlib.deflateRaw(bytes, Server.getInstance().networkCompressionLevel);
                } else {
                    pk.payload = Zlib.deflatePre16Packet(bytes, Server.getInstance().networkCompressionLevel);
                }
                for (Player player : finalTargets) {
                    CompressionProvider compressionProvider = player.getNetworkSession().getCompression();
                    if (compressionProvider == CompressionProvider.NONE) {
                        BatchPacket batchPacket = new BatchPacket();
                        batchPacket.payload = bytes;
                        player.dataPacket(batchPacket);
                    }else {
                        player.dataPacket(pk);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void shutdown() {
        this.threadedExecutor.shutdownNow();
    }
}
