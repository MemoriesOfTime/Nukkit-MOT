package cn.nukkit.network;

import cn.nukkit.Server;
import cn.nukkit.network.protocol.DataPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

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

    public static Builder builder() {
        return new Builder();
    }

    public DataPacket getPacket(int id) {
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

    public Builder toBuilder() {
        Builder builder = new Builder();

        builder.packets.putAll(this.packets);
        builder.protocolVersion = this.protocolVersion;
        builder.minecraftVersion = this.minecraftVersion;

        return builder;
    }

    @SuppressWarnings({"unchecked", "ConstantConditions"})
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {
        private final Int2ObjectOpenHashMap<Class<? extends DataPacket>> packets = new Int2ObjectOpenHashMap<>(256);
        private int protocolVersion = -1;
        private String minecraftVersion = null;

        public <T extends DataPacket> Builder registerPacket(@NonNegative int id, @NonNull Class<T> packetClass) {
            checkArgument(id >= 0, "id " + id + " cannot be negative");

            packets.put(id, packetClass);

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
            return new PacketPool(protocolVersion, minecraftVersion, packets, packetsById);
        }
    }
}
