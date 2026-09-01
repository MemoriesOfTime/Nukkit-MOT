package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.GatheringsConfigurationJoinInfo;
import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

@ToString
public class TransferPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.TRANSFER_PACKET;

    public String address;
    public int port = 19132;
    /**
     * @since v729
     */
    public boolean reloadWorld;
    /**
     * v2168 (1.26.40) 新增的聚会/集合配置；服务端可不设置（为 null）。
     * <p>
     * Gatherings configuration added in v2168 (1.26.40); the server may leave it unset (null).
     */
    public GatheringsConfigurationJoinInfo gatheringsConfiguration;

    @Override
    public void decode() {
        this.address = this.getString();
        this.port = (short) this.getLShort();
        if (this.protocol >= ProtocolInfo.v1_21_30) {
            this.reloadWorld = this.getBoolean();
        }
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.gatheringsConfiguration = this.getOptional(null, bs -> this.readGatheringsConfiguration());
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(address);
        this.putLShort(port);
        if (this.protocol >= ProtocolInfo.v1_21_30) {
            this.putBoolean(this.reloadWorld);
        }
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putOptionalNull(this.gatheringsConfiguration, this::writeGatheringsConfiguration);
        }
    }

    /**
     * 读取 v2168 GatheringsConfiguration（字段布局见 {@link GatheringsConfigurationJoinInfo}）。
     * <p>
     * Reads the v2168 GatheringsConfiguration (field layout in {@link GatheringsConfigurationJoinInfo}).
     */
    private GatheringsConfigurationJoinInfo readGatheringsConfiguration() {
        GatheringsConfigurationJoinInfo info = new GatheringsConfigurationJoinInfo();
        info.experienceId = this.getUUID();
        info.experienceName = this.getString();
        info.worldId = this.getOptional(null, BinaryStream::getUUID);
        info.worldName = this.getOptional(null, BinaryStream::getString);
        info.creatorId = this.getString();
        info.targetId = this.getOptional(null, BinaryStream::getUUID);
        info.scenarioId = this.getOptional(null, BinaryStream::getString);
        info.serverId = this.getOptional(null, BinaryStream::getString);
        return info;
    }

    /**
     * 写入 v2168 GatheringsConfiguration。
     * <p>
     * Writes the v2168 GatheringsConfiguration.
     */
    private void writeGatheringsConfiguration(GatheringsConfigurationJoinInfo info) {
        this.putUUID(info.experienceId);
        this.putString(info.experienceName);
        this.putOptionalNull(info.worldId, this::putUUID);
        this.putOptionalNull(info.worldName, this::putString);
        this.putString(info.creatorId);
        this.putOptionalNull(info.targetId, this::putUUID);
        this.putOptionalNull(info.scenarioId, this::putString);
        this.putOptionalNull(info.serverId, this::putString);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
