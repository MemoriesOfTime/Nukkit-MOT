package cn.nukkit.network.protocol;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.MainLogger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

@ToString
public class ItemComponentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ITEM_COMPONENT_PACKET;

    public List<ItemDefinition> entries;

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

                    if (itemDefinition.isComponentBased()) {
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

    @AllArgsConstructor
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
        private final CompoundTag networkData;

    }

}
