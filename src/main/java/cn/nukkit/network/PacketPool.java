package cn.nukkit.network;

import cn.nukkit.Server;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.cloudburstmc.protocol.common.util.Preconditions.checkArgument;
import static org.cloudburstmc.protocol.common.util.Preconditions.checkNotNull;

/**
 * @author LT_Name
 */
@Log4j2
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PacketPool {
    @Getter
    private final int protocolVersion;
    @Getter
    private final String minecraftVersion;
    private final Int2ObjectOpenHashMap<Class<? extends DataPacket>> packets;
    private final Class<? extends DataPacket>[] packetsById;
    private final Map<Class<? extends DataPacket>, Integer> packetsByClass;

    public static Builder builder() {
        return new Builder();
    }

    public DataPacket getPacket(int id) {
        if (id >= packetsById.length) {
            Server.getInstance().getLogger().debug("Packet exceeds limits, id: " + id);
            return null;
        }
        Class<? extends DataPacket> clazz = packetsById[id];
        if (clazz != null) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                Server.getInstance().getLogger().logException(e);
            }
        }
        return null;
    }

    public int getPacketId(@NonNull Class<? extends DataPacket> clazz) {
        checkNotNull(clazz, "clazz");
        Integer id = packetsByClass.get(clazz);
        if (id == null) {
            throw new IllegalArgumentException("Unknown packet class " + clazz);
        }
        return id;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();

        builder.packets.putAll(this.packets);
        builder.packetsByClass.putAll(this.packetsByClass);
        builder.protocolVersion = this.protocolVersion;
        builder.minecraftVersion = this.minecraftVersion;

        return builder;
    }

    @SuppressWarnings({"unchecked", "ConstantConditions"})
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {
        private final Int2ObjectOpenHashMap<Class<? extends DataPacket>> packets = new Int2ObjectOpenHashMap<>(256);
        private final Map<Class<? extends DataPacket>, Integer> packetsByClass = new IdentityHashMap<>();
        private int protocolVersion = -1;
        private String minecraftVersion = null;

        public <T extends DataPacket> Builder registerPacket(byte id, @NonNull Class<T> packetClass) {
            return this.registerPacket(ProtocolInfo.toNewProtocolID(id), packetClass);
        }

        public <T extends DataPacket> Builder registerPacket(@NonNegative int id, @NonNull Class<T> packetClass) {
            checkArgument(id >= 0, "id " + id + " cannot be negative");

            packets.put(id, packetClass);
            packetsByClass.put(packetClass, id);

            return this;
        }

        public Builder deregisterPacket(int id) {
            packets.remove(id);
            return this;
        }

        public Builder protocolVersion(@NonNegative int protocolVersion) {
            checkArgument(protocolVersion >= 0, "protocolVersion cannot be negative");
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Builder minecraftVersion(@NonNull String minecraftVersion) {
            checkNotNull(minecraftVersion, "minecraftVersion");
            checkArgument(!minecraftVersion.isEmpty() && minecraftVersion.split("\\.").length > 2, "Invalid minecraftVersion");
            this.minecraftVersion = minecraftVersion;
            return this;
        }

        public PacketPool build() {
            checkArgument(protocolVersion >= 0, "No protocol version defined");
            checkNotNull(minecraftVersion, "No Minecraft version defined");
            int largestId = -1;
            for (int id : packets.keySet()) {
                if (id > largestId) {
                    largestId = id;
                }
            }
            checkArgument(largestId > -1, "Must have at least one packet registered");
            Class<? extends DataPacket>[] packetsById = new Class[largestId + 1];

            for (Int2ObjectMap.Entry<Class<? extends DataPacket>> entry : packets.int2ObjectEntrySet()) {
                packetsById[entry.getIntKey()] = entry.getValue();
            }
            return new PacketPool(protocolVersion, minecraftVersion, packets, packetsById, packetsByClass);
        }
    }
}
