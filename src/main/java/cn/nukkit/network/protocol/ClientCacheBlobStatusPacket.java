package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class ClientCacheBlobStatusPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CLIENT_CACHE_BLOB_STATUS_PACKET;

    private static final int MAX_HASHES_PER_ARRAY = 4095;

    public long[] missHashes = new long[0];
    public long[] hitHashes = new long[0];

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_26_30) {
            this.missHashes = readHashArray();
            this.hitHashes = readHashArray();
        } else {
            long missCountRaw = this.getUnsignedVarInt();
            long hitCountRaw = this.getUnsignedVarInt();
            if (missCountRaw < 0 || missCountRaw > MAX_HASHES_PER_ARRAY || hitCountRaw < 0 || hitCountRaw > MAX_HASHES_PER_ARRAY) {
                throw new IllegalArgumentException("Invalid blob status hash counts: miss=" + missCountRaw + ", hit=" + hitCountRaw);
            }
            this.missHashes = readHashes((int) missCountRaw);
            this.hitHashes = readHashes((int) hitCountRaw);
        }
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    private long[] readHashArray() {
        long countRaw = this.getUnsignedVarInt();
        if (countRaw < 0 || countRaw > MAX_HASHES_PER_ARRAY) {
            throw new IllegalArgumentException("Invalid blob status hash count: " + countRaw);
        }
        return readHashes((int) countRaw);
    }

    private long[] readHashes(int count) {
        long[] hashes = new long[count];
        for (int i = 0; i < count; i++) {
            hashes[i] = this.getLLong();
        }
        return hashes;
    }
}
