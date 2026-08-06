package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString(exclude = "data")
public class LevelChunkPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.FULL_CHUNK_DATA_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.FULL_CHUNK_DATA_PACKET;
        }
        return NETWORK_ID;
    }

    public int chunkX;
    public int chunkZ;
    /**
     * @since v649 v1.20.60
     */
    public int dimension;
    public int subChunkCount;
    public boolean cacheEnabled;
    /**
     * v1.18.0?10 and above
     */
    public boolean requestSubChunks;
    /**
     * v1.18.0?10 and above
     */
    public int subChunkLimit;
    public long[] blobIds;
    public byte[] data;

    /**
     * v0.14.3 - 0.15.10
     */
    public static final byte ORDER_COLUMNS = 0;
    public static final byte ORDER_LAYERED = 1;

    public byte order = ORDER_LAYERED;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0) {
                this.putVarInt(this.chunkX);
                this.putVarInt(this.chunkZ);
                this.putByte(this.order);
                this.putByteArray(this.data);
                return;
            }
            if(this.protocol >= ProtocolInfo.v_0_11_0){
                this.putInt(this.chunkX);
                this.putInt(this.chunkZ);
                if(this.protocol >= ProtocolInfo.v_0_12_1){
                    this.putByte(this.order);
                }
                this.putInt(this.data.length);
            }
            this.put(this.data);
            return;
        }
        this.reset();
        this.putVarInt(this.chunkX);
        this.putVarInt(this.chunkZ);
        if (protocol >= ProtocolInfo.v1_20_60) {
            this.putVarInt(this.dimension);
        }
        if (protocol >= ProtocolInfo.v1_12_0) {
            if (protocol >= ProtocolInfo.v1_18_0) {
                if (!this.requestSubChunks) {
                    this.putUnsignedVarInt(this.subChunkCount);
                } else if (this.subChunkLimit < 0) {
                    this.putUnsignedVarInt(-1);
                } else {
                    this.putUnsignedVarInt(-2);
                    this.putUnsignedVarInt(this.subChunkLimit);
                }
            }else {
                this.putUnsignedVarInt(this.subChunkCount);
            }
            this.putBoolean(cacheEnabled);
            if (this.cacheEnabled) {
                this.putUnsignedVarInt(blobIds.length);
                for (long blobId : blobIds) {
                    this.putLLong(blobId);
                }
            }
        }
        this.putByteArray(this.data);
    }
}
