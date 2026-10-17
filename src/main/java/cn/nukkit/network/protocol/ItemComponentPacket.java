package cn.nukkit.network.protocol;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.MainLogger;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@ToString
public class ItemComponentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ITEM_COMPONENT_PACKET;

    public List<ItemDefinition> entries = new ArrayList<>();

    private static final byte[] EMPTY_COMPOUND_TAG;

    static {
        try {
            EMPTY_COMPOUND_TAG = NBTIO.writeNetwork(new CompoundTag(""));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setEntries(List<ItemDefinition> entries) {
        this.entries = entries;
    }

    public List<ItemDefinition> getEntries() {
        return entries;
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.entries.size());
        try {
            if (this.protocol >= ProtocolInfo.v1_21_60) {
                for (ItemDefinition itemDefinition : this.entries) {
                    this.putString(itemDefinition.getIdentifier());
                    this.putLShort(itemDefinition.getRuntimeId());
                    this.putBoolean(itemDefinition.isComponentBased());
                    this.putVarInt(itemDefinition.getVersion());

                    if (itemDefinition.cachedNetworkData != null && !itemDefinition.networkDataExposed) {
                        this.put(itemDefinition.cachedNetworkData);
                    } else if (itemDefinition.getNetworkData() != null) {
                        this.put(NBTIO.write(itemDefinition.getNetworkData(), ByteOrder.LITTLE_ENDIAN, true));
                    } else {
                        this.put(EMPTY_COMPOUND_TAG);
                    }
                }
            } else {
                for (ItemDefinition itemDefinition : this.entries) {
                    this.putString(itemDefinition.getIdentifier());
                    this.put(NBTIO.write(itemDefinition.getNetworkData(), ByteOrder.LITTLE_ENDIAN, true));
                }
            }
        } catch (IOException e) {
            MainLogger.getLogger().error("Error while encoding NBT data of ItemComponentPacket", e);
        }
    }

    @Getter
    @ToString
    public static class ItemDefinition {

        private final String identifier;
        /**
         * @since v776 1.21.60
         */
        private final int runtimeId;
        /**
         * @since v776 1.21.60
         */
        private final boolean componentBased;
        /**
         * @since v776 1.21.60
         */
        private final int version;
        @ToString.Exclude
        private final CompoundTag networkData;

        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        private final byte[] cachedNetworkData;
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        private volatile boolean networkDataExposed;

        // Keep the existing custom-definition constructor and its input alias semantics.
        public ItemDefinition(String identifier, int runtimeId, boolean componentBased, int version,
                              CompoundTag networkData) {
            this(identifier, runtimeId, componentBased, version, networkData, null);
        }

        private ItemDefinition(String identifier, int runtimeId, boolean componentBased, int version,
                               CompoundTag networkData, byte[] cachedNetworkData) {
            this.identifier = identifier;
            this.runtimeId = runtimeId;
            this.componentBased = componentBased;
            this.version = version;
            this.networkData = networkData;
            this.cachedNetworkData = cachedNetworkData;
        }

        /** Own the vanilla tag; external access permanently restores dynamic serialization. */
        public static ItemDefinition vanilla(String identifier, int runtimeId, boolean componentBased,
                                             int version, CompoundTag networkData) {
            CompoundTag owned = networkData == null ? null : networkData.copy();
            byte[] encoded = null;
            try {
                encoded = owned == null ? EMPTY_COMPOUND_TAG
                        : NBTIO.write(owned, ByteOrder.LITTLE_ENDIAN, true);
            } catch (IOException ignored) {
                // Preserve the existing encode-time logging/error path if precompilation fails.
            }
            return new ItemDefinition(identifier, runtimeId, componentBased, version, owned, encoded);
        }

        @ToString.Include(name = "networkData")
        private CompoundTag networkDataForToString() {
            return cachedNetworkData != null ? networkData : getNetworkData();
        }

        public CompoundTag getNetworkData() {
            if (cachedNetworkData != null) networkDataExposed = true;
            return networkData;
        }

    }

}
