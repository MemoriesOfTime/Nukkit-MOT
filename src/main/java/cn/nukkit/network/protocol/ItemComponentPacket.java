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
            for (Entry entry : this.entries) {
                this.putString(entry.getName());
                this.put(NBTIO.write(entry.getData(), ByteOrder.LITTLE_ENDIAN, true));
            }
        } catch (IOException e) {
            MainLogger.getLogger().error("Error while encoding NBT data of ItemComponentPacket", e);
        }
    }

    @ToString
    public static class Entry {

        public static final Entry[] EMPTY_ARRAY = new Entry[0];

        private final String name;
        private final CompoundTag data;

        public Entry(String name, CompoundTag data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public CompoundTag getData() {
            return data;
        }

    }

}
