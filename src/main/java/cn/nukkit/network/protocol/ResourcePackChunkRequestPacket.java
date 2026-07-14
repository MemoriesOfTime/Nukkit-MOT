package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.UUID;

@ToString
public class ResourcePackChunkRequestPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET;

    public UUID packId;
    public String packVersion;
    public int chunkIndex;

    @Override
    public void decode() {
        String[] packInfo = this.getString().split("_", 3);
        this.packId = UUID.fromString(packInfo[0]);
        this.packVersion = packInfo.length > 1 ? packInfo[1] : null;
        this.chunkIndex = this.getLInt();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.packId + (this.protocol >= ProtocolInfo.v1_6_0_5 && this.packVersion != null ? '_' + this.packVersion : ""));
        this.putLInt(this.chunkIndex);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
