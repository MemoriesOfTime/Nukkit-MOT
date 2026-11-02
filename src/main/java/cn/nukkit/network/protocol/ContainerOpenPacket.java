package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.math.BlockVector3;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class ContainerOpenPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CONTAINER_OPEN_PACKET;

    public static final int TYPE_LECTERN = 25;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public int windowId;
    public int type;
    public int x;
    public int y;
    public int z;
    public long entityId = -1;
    @OnlyNetEase
    public boolean isIgnoreBlock;
    @OnlyNetEase
    public String screenName = "";
    @OnlyNetEase
    public String customDescription = "";
    @OnlyNetEase
    public boolean isClosedDropItem;

    @Override
    public void decode() {
        this.windowId = this.getByte();
        this.type = this.getByte();
        BlockVector3 v = this.getBlockVector3();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        this.entityId = this.getEntityUniqueId();
        if (this.gameVersion.isNetEase()) {
            if (this.gameVersion.getProtocol() >= GameVersion.V1_21_124_NETEASE.getProtocol()) {
                this.isIgnoreBlock = this.getBoolean();
            }
            if (this.gameVersion.getProtocol() >= GameVersion.V1_21_130_NETEASE.getProtocol() && !this.feof()) {
                this.screenName = this.getString();
                this.customDescription = this.getString();
                this.isClosedDropItem = this.getBoolean();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte((byte) this.windowId);
        this.putByte((byte) this.type);
        this.putBlockVector3(this.x, this.y, this.z);
        this.putEntityUniqueId(this.entityId);
        if (this.gameVersion.isNetEase()) {
            if (this.gameVersion.getProtocol() >= GameVersion.V1_21_124_NETEASE.getProtocol()) {
                this.putBoolean(this.isIgnoreBlock);
            }
            if (this.gameVersion.getProtocol() >= GameVersion.V1_21_130_NETEASE.getProtocol()) {
                this.putString(this.screenName == null ? "" : this.screenName);
                this.putString(this.customDescription == null ? "" : this.customDescription);
                this.putBoolean(this.isClosedDropItem);
            }
        }
    }
}
