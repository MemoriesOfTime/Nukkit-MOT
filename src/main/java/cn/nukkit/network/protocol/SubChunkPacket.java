package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class SubChunkPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SUB_CHUNK_PACKET;

    public int dimension;
    public boolean cacheEnabled;
    /**
     * @since v486 (1.18.10)
     */
    public BlockVector3 centerPosition;
    public List<SubChunkData> subChunks = new ArrayList<>();

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

        if (this.protocol >= ProtocolInfo.v1_18_10) {
            this.encodeV486();
        } else {
            this.encodeV471();
        }
    }

    private void encodeV486() {
        this.putBoolean(this.cacheEnabled);
        this.putVarInt(this.dimension);
        this.putSignedBlockPosition(this.centerPosition);

        this.putLInt(this.subChunks.size());
        for (SubChunkData subChunk : this.subChunks) {
            this.putByte((byte) subChunk.offset.x);
            this.putByte((byte) subChunk.offset.y);
            this.putByte((byte) subChunk.offset.z);

            this.putByte((byte) subChunk.result.ordinal());

            if (subChunk.result != SubChunkRequestResult.SUCCESS_ALL_AIR || !this.cacheEnabled) {
                this.putByteArray(subChunk.data);
            }

            this.putByte((byte) subChunk.heightMapType.ordinal());
            if (subChunk.heightMapType == HeightMapDataType.HAS_DATA) {
                this.put(subChunk.heightMapData);
            }

            if (this.protocol >= ProtocolInfo.v1_21_90) {
                this.putByte((byte) subChunk.renderHeightMapType.ordinal());
                if (subChunk.renderHeightMapType == HeightMapDataType.HAS_DATA) {
                    this.put(subChunk.renderHeightMapData);
                }
            }

            if (this.cacheEnabled) {
                this.putLLong(subChunk.blobId);
            }
        }
    }

    private void encodeV471() {
        this.putVarInt(this.dimension);

        if (!this.subChunks.isEmpty()) {
            SubChunkData subChunk = this.subChunks.get(0);
            this.putSignedBlockPosition(subChunk.position);
            this.putByteArray(subChunk.data);
            this.putVarInt(subChunk.result.ordinal());

            this.putByte((byte) subChunk.heightMapType.ordinal());
            if (subChunk.heightMapType == HeightMapDataType.HAS_DATA) {
                this.put(subChunk.heightMapData);
            } else if (this.protocol < ProtocolInfo.v1_18_0) {
                this.put(new byte[HEIGHT_MAP_LENGTH]);
            }
        }

        if (this.protocol >= ProtocolInfo.v1_18_0) {
            this.putBoolean(this.cacheEnabled);
            if (this.cacheEnabled && !this.subChunks.isEmpty()) {
                this.putLLong(this.subChunks.get(0).blobId);
            }
        }
    }

    public static class SubChunkData {

        public BlockVector3 position;
        public BlockVector3 offset;
        public byte[] data;
        public SubChunkRequestResult result;
        public HeightMapDataType heightMapType = HeightMapDataType.NO_DATA;
        public byte[] heightMapData;
        public HeightMapDataType renderHeightMapType = HeightMapDataType.NO_DATA;
        public byte[] renderHeightMapData;
        public long blobId;
    }

    public enum HeightMapDataType {
        NO_DATA,
        HAS_DATA,
        TOO_HIGH,
        TOO_LOW,
        COPIED
    }

    private static final int HEIGHT_MAP_LENGTH = 256;
}
