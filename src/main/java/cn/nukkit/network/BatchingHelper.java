package cn.nukkit.network;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.concurrent.*;

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
        //this.threadedExecutor = new ThreadPoolExecutor(
        //        1, 100, 100L, TimeUnit.MILLISECONDS,
        //        new LinkedBlockingDeque<>(),
        //        builder.build(),
        //        new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public void batchPackets(Player[] players, DataPacket[] packets) {
        if (players.length > 0 && packets.length > 0) {
            this.threadedExecutor.execute(() -> this.batchAndSendPackets(players, packets));
        }
    }

    private void batchAndSendPackets(Player[] players, DataPacket[] packets) {
        //只有一个玩家时直接发送
        //if (players.length == 1) {
        //    for (DataPacket packet : packets) {
        //        packet.protocol = players[0].protocol;
        //        players[0].getNetworkSession().sendPacket(packet);
        //    }
        //    return;
        //}

        Int2ObjectMap<ObjectList<Player>> targets = new Int2ObjectOpenHashMap<>();
        for (Player player : players) {
            targets.computeIfAbsent(player.protocol, i -> new ObjectArrayList<>()).add(player);
        }

        // Encoded packets by encoding protocol
        Int2ObjectMap<ObjectList<DataPacket>> encodedPackets = new Int2ObjectOpenHashMap<>();

        for (DataPacket packet : packets) {
            Int2IntMap encodingProtocols = new Int2IntOpenHashMap();
            for (int protocolId : targets.keySet()) {
                // TODO: encode only by encoding protocols
                // No need to have all versions here
                encodingProtocols.put(protocolId, protocolId);
            }

            Int2ObjectMap<DataPacket> encodedPacket = new Int2ObjectOpenHashMap<>();
            for (int encodingProtocol : encodingProtocols.values()) {
                if (!encodedPacket.containsKey(encodingProtocol)) {
                    DataPacket pk = packet.clone();
                    pk.protocol = encodingProtocol;
                    pk.tryEncode();
                    encodedPacket.put(encodingProtocol, pk);
                }
            }

            for (int protocolId : encodingProtocols.values()) {
                int encodingProtocol = encodingProtocols.get(protocolId);
                encodedPackets.computeIfAbsent(protocolId, i -> new ObjectArrayList<>()).add(encodedPacket.get(encodingProtocol));
            }
        }

        for (int protocolId : targets.keySet()) {
            ObjectList<DataPacket> packetList = encodedPackets.get(protocolId);
            ObjectList<Player> finalTargets = targets.get(protocolId);

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
                if (protocolId >= ProtocolInfo.v1_16_0) {
                    pk.payload = Zlib.deflateRaw(bytes, Server.getInstance().networkCompressionLevel);
                } else {
                    pk.payload = Zlib.deflate(bytes, Server.getInstance().networkCompressionLevel);
                }
                for (Player pl : finalTargets) {
                    CompressionProvider compressionProvider = pl.getNetworkSession().getCompression();
                    if (compressionProvider == CompressionProvider.NONE) {
                        BatchPacket batchPacket = new BatchPacket();
                        batchPacket.payload = bytes;
                        pl.dataPacket(batchPacket);
                        continue;
                    }
                    pl.dataPacket(pk);
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
