package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.UUID;

@ToString(exclude = "data")
public class ResourcePackChunkDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RESOURCE_PACK_CHUNK_DATA_PACKET;

    public UUID packId;
    public String packVersion;
    public int chunkIndex;
    public long progress;
    public byte[] data;

    @Override
    public void decode() {
        String[] packInfo = this.getString().split("_", 3);
        this.packId = UUID.fromString(packInfo[0]);
        this.packVersion = packInfo.length > 1 ? packInfo[1] : null;
        this.chunkIndex = this.getLInt();
        this.progress = this.getLLong();
        if (protocol < 388) {
            this.data = this.get(this.getLInt());
        } else {
            this.data = this.getByteArray();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.packId + (this.protocol >= ProtocolInfo.v1_6_0_5 && this.packVersion != null ? '_' + this.packVersion : ""));
        this.putLInt(this.chunkIndex);
        this.putLLong(this.progress);
        if (protocol < 388) {
            this.putLInt(this.data.length);
            this.put(this.data);
        } else {
            this.putByteArray(this.data);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
