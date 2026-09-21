package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import lombok.ToString;

/**
 * 通知客户端某方块位置开始播放唱片。
 * <p>
 * Notifies the client that a record started playing at a block position.
 *
 * @since v2192
 */
@ToString
public class RecordStartedPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.RECORD_STARTED_PACKET;

    public BlockVector3 blockPos;
    public long serverSoundHandle;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        // S2C only
    }

    @Override
    public void encode() {
        this.reset();
        this.putBlockVector3(this.blockPos != null ? this.blockPos : new BlockVector3(0, 0, 0));
        this.putLLong(this.serverSoundHandle);
    }
}
