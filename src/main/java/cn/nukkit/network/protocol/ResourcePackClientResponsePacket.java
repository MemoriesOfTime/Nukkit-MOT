package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.UUID;

@ToString
public class ResourcePackClientResponsePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET;

    /**
     * v2168 起新增的 type-name 字面值，按 (status - 1) 索引
     * NEW v2168 type-name literals indexed by (status - 1)
     */
    private static final String[] STATUS_NAMES = {"cancel", "downloading", "downloadingfinished", "resourcepackstackfinished"};

    public static final byte STATUS_REFUSED = 1;
    public static final byte STATUS_SEND_PACKS = 2;
    public static final byte STATUS_HAVE_ALL_PACKS = 3;
    public static final byte STATUS_COMPLETED = 4;

    public byte responseStatus;
    public Entry[] packEntries;

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.responseStatus = (byte) (this.getUnsignedVarInt() + 1);
            this.getString(); // type-name 字符串：读取并丢弃（仅推进缓冲区） / type-name string: read and discard
            this.packEntries = new Entry[0];
            if (this.responseStatus == STATUS_SEND_PACKS) {
                this.packEntries = new Entry[Math.min((int) this.getUnsignedVarInt(), 1024)];
                for (int i = 0; i < this.packEntries.length; i++) {
                    String[] entry = this.getString().split("_", 3);
                    String version = "1.2.0";
                    if (protocol >= ProtocolInfo.v1_6_0_5) {
                        version = entry[1];
                    }
                    this.packEntries[i] = new Entry(UUID.fromString(entry[0]), version);
                }
            }
        } else {
            this.responseStatus = (byte) this.getByte();
            this.packEntries = new Entry[Math.min(this.getLShort(), 1024)];
            for (int i = 0; i < this.packEntries.length; i++) {
                String[] entry = this.getString().split("_", 3);
                String version = "1.2.0";
                if (protocol >= ProtocolInfo.v1_6_0_5) {
                    version = entry[1];
                }
                this.packEntries[i] = new Entry(UUID.fromString(entry[0]), version);
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putUnsignedVarInt(this.responseStatus - 1);
            this.putString(STATUS_NAMES[Math.min(this.responseStatus - 1, STATUS_NAMES.length - 1)]);
            if (this.responseStatus == STATUS_SEND_PACKS) {
                this.putUnsignedVarInt(this.packEntries.length);
                for (Entry entry : this.packEntries) {
                    this.putString(entry.uuid.toString() + '_' + entry.version);
                }
            }
        } else {
            this.putByte(this.responseStatus);
            this.putLShort(this.packEntries.length);
            for (Entry entry : this.packEntries) {
                this.putString(entry.uuid.toString() + '_' + entry.version);
            }
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @ToString
    public static class Entry {

        public final UUID uuid;
        public final String version;

        public Entry(UUID uuid, String version) {
            this.uuid = uuid;
            this.version = version;
        }
    }
}
