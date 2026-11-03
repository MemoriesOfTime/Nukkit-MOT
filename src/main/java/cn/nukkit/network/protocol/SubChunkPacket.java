package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static final byte[] NO_DATA = new byte[0];

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
        this.reservePayload();

        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.encodeV2168();
        } else if (this.protocol >= ProtocolInfo.v1_18_10) {
            this.encodeV486();
        } else {
            this.encodeV471();
        }
    }

    private void reservePayload() {
        long size = varIntSize((this.dimension << 1) ^ (this.dimension >> 31));
        boolean optionalFields = this.protocol >= ProtocolInfo.v1_26_40;
        if (this.protocol >= ProtocolInfo.v1_18_10) {
            size += 1 + (optionalFields ? 12 : positionSize(this.centerPosition))
                    + (optionalFields ? varIntSize(this.subChunks.size()) : Integer.BYTES);
        } else if (this.protocol >= ProtocolInfo.v1_18_0) {
            size++;
        }
        for (SubChunkData subChunk : this.subChunks) {
            if (this.protocol < ProtocolInfo.v1_18_10) {
                size += positionSize(subChunk.position) + varIntSize(subChunk.result.ordinal() << 1) + 1;
            } else {
                size += optionalFields ? 10 : 5; // Offsets, result, map types and presence flags.
                if (!optionalFields && this.protocol >= ProtocolInfo.v1_21_90) size++;
            }
            if (optionalFields ? subChunk.data != null : this.protocol < ProtocolInfo.v1_18_10
                    || subChunk.result != SubChunkRequestResult.SUCCESS_ALL_AIR || !this.cacheEnabled) {
                int length = subChunk.data == null ? 0 : subChunk.data.length;
                size += (long) varIntSize(length) + length;
            }
            if (subChunk.heightMapType == HeightMapDataType.HAS_DATA) {
                size += this.heightMapSize(subChunk.heightMapData);
            } else if (this.protocol < ProtocolInfo.v1_18_0) {
                size += HEIGHT_MAP_LENGTH;
            }
            if (this.protocol >= ProtocolInfo.v1_21_90
                    && subChunk.renderHeightMapType == HeightMapDataType.HAS_DATA) {
                size += this.heightMapSize(subChunk.renderHeightMapData);
            }
            if (optionalFields ? subChunk.hasBlobId : this.protocol >= ProtocolInfo.v1_18_0 && this.cacheEnabled) {
                size += Long.BYTES;
            }
            if (this.protocol < ProtocolInfo.v1_18_10) break;
        }
        if (size > Integer.MAX_VALUE - (long) this.getCount()) {
            throw new OutOfMemoryError("Sub-chunk packet is too large");
        }
        this.reserve((int) size);
    }

    private static int positionSize(BlockVector3 position) {
        return varIntSize((position.x << 1) ^ (position.x >> 31))
                + varIntSize((position.y << 1) ^ (position.y >> 31))
                + varIntSize((position.z << 1) ^ (position.z >> 31));
    }

    private static int varIntSize(int value) {
        int size = 1;
        for (int remaining = value >>> 7; remaining != 0; remaining >>>= 7) size++;
        return size;
    }

    private int heightMapSize(byte[] data) {
        return this.protocol >= ProtocolInfo.v1_26_50_27
                ? HEIGHT_MAP_LENGTH + HEIGHT_MAP_LENGTH / 16 : data == null ? 0 : data.length;
    }

    private void encodeV2168() {
        this.putBoolean(this.cacheEnabled);
        this.putVarInt(this.dimension);
        // v2168: centerPosition 改为 3 个 LInt / centerPosition changed to three LInts
        this.putLInt(this.centerPosition.x);
        this.putLInt(this.centerPosition.y);
        this.putLInt(this.centerPosition.z);

        this.putUnsignedVarInt(this.subChunks.size());
        for (SubChunkData subChunk : this.subChunks) {
            this.putByte((byte) subChunk.offset.x);
            this.putByte((byte) subChunk.offset.y);
            this.putByte((byte) subChunk.offset.z);

            this.putByte((byte) subChunk.result.ordinal());

            boolean hasData = subChunk.data != null;
            this.putBoolean(hasData);
            if (hasData) {
                this.putByteArray(subChunk.data);
            }

            this.putByte((byte) subChunk.heightMapType.ordinal());
            boolean hasHeightMap = subChunk.heightMapType == HeightMapDataType.HAS_DATA;
            this.putBoolean(hasHeightMap);
            if (hasHeightMap) {
                this.putHeightMapData(subChunk.heightMapData);
            }

            this.putByte((byte) subChunk.renderHeightMapType.ordinal());
            boolean hasRenderHeightMap = subChunk.renderHeightMapType == HeightMapDataType.HAS_DATA;
            this.putBoolean(hasRenderHeightMap);
            if (hasRenderHeightMap) {
                this.putHeightMapData(subChunk.renderHeightMapData);
            }

            this.putBoolean(subChunk.hasBlobId);
            if (subChunk.hasBlobId) {
                this.putLLong(subChunk.blobId);
            }
        }
    }

    /**
     * v2192 起高度图按 16 字节分段，每段前置 uvarint 段长；此前为整块写入。
     * <p>
     * Since v2192 the height map is written in 16-byte segments prefixed with a uvarint length;
     * previously it was one raw block.
     */
    private void putHeightMapData(byte[] heightMapData) {
        if (this.protocol < ProtocolInfo.v1_26_50_27) {
            this.put(heightMapData);
            return;
        }
        for (int offset = 0; offset < HEIGHT_MAP_LENGTH; offset += 16) {
            this.putUnsignedVarInt(16);
            this.put(Arrays.copyOfRange(heightMapData, offset, offset + 16));
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
                this.putByteArray(subChunk.data == null ? NO_DATA : subChunk.data);
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
            this.putByteArray(subChunk.data == null ? NO_DATA : subChunk.data);
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
        /**
         * Companion presence flag for v2168 blobId optionalNull serialization.
         * When false, blobId is omitted on the wire.
         *
         * v2168 blobId optionalNull 序列化的伴随存在标志。
         * 为 false 时 blobId 不写入数据包。
         *
         * @since v1_26_40 (2168)
         */
        public boolean hasBlobId;
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
