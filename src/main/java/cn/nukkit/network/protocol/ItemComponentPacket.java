package cn.nukkit.network.protocol;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.MainLogger;
import lombok.ToString;

import java.io.IOException;
import java.nio.ByteOrder;

@ToString
public class ItemComponentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ITEM_COMPONENT_PACKET;

    public Entry[] entries = Entry.EMPTY_ARRAY;

    private static final byte[] EMPTY_COMPOUND_TAG;

    static {
        try {
            EMPTY_COMPOUND_TAG = NBTIO.writeNetwork(new CompoundTag(""));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setEntries(Entry[] entries) {
        this.entries = entries == null? null : entries.length == 0? Entry.EMPTY_ARRAY : entries.clone();
    }

    public Entry[] getEntries() {
        return entries == null? null : entries.length == 0? Entry.EMPTY_ARRAY : entries.clone();
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.entries.length);
        try {
            if (this.protocol >= ProtocolInfo.v1_21_60) {
                for (Entry entry : this.entries) {
                    this.putString(entry.getName());
                    this.putLShort(entry.getRuntimeId());
                    this.putBoolean(entry.isComponentBased());
                    this.putVarInt(entry.getVersion());

                    if (entry.isComponentBased()) {
                        this.put(NBTIO.write(entry.getData(), ByteOrder.LITTLE_ENDIAN, true));
                    } else {
                        this.put(EMPTY_COMPOUND_TAG);
                    }
                }
            } else {
                for (Entry entry : this.entries) {
                    this.putString(entry.getName());
                    this.put(NBTIO.write(entry.getData(), ByteOrder.LITTLE_ENDIAN, true));
                }
            }
        } catch (IOException e) {
            MainLogger.getLogger().error("Error while encoding NBT data of ItemComponentPacket", e);
        }
    }

    @ToString
    public static class Entry {

        public static final Entry[] EMPTY_ARRAY = new Entry[0];

        private final String name;
        private final int runtimeId;
        private final boolean componentBased;
        private final int version;
        private final CompoundTag data;

        @Deprecated
        public Entry(String name, CompoundTag data) {
            this.name = name;
            this.data = data;

            this.runtimeId = 0;
            this.componentBased = false;
            this.version = 0;
        }

        public Entry(String name, int runtimeId, boolean componentBased, int version, CompoundTag data) {
            this.name = name;
            this.runtimeId = runtimeId;
            this.componentBased = componentBased;
            this.version = version;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public int getRuntimeId() {
            return runtimeId;
        }

        public int getVersion() {
            return version;
        }

        public boolean isComponentBased() {
            return componentBased;
        }

        public CompoundTag getData() {
            return data;
        }

    }

}
