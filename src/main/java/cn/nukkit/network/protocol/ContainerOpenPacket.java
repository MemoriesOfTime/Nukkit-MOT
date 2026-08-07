package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
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
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.CONTAINER_OPEN_PACKET;
        }
        return NETWORK_ID;
    }

    public int windowId;
    public int type;
    public int x;
    public int y;
    public int z;
    public long entityId = -1;
    public int slots;// 0.14.3

    @Override
    public void decode() {
        this.windowId = this.getByte();
        this.type = this.getByte();
        BlockVector3 v = this.getBlockVector3();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        this.entityId = this.getEntityUniqueId();
        if (this.gameVersion.isNetEase() && this.gameVersion.getProtocol() >= GameVersion.V1_21_124_NETEASE.getProtocol()) {
            this.getBoolean(); //mIsIgnoreBlock
        }
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.tryReset();
                this.putByte((byte) (this.windowId & 0xff));
                this.putByte((byte)(this.type & 0xff));
                this.putVarInt(this.slots);
                this.putBlockVector3(this.x, this.y, this.z);
                this.putVarLong(this.entityId);
                return;
            }
            if(this.protocol <= ProtocolInfo.v_0_10_0){
                this.tryReset();
            }
            this.putByte((byte) (this.windowId & 0xff));
            this.putByte((byte)(this.type & 0xff));
            this.putShort(this.slots);
            this.putInt(this.x);
            this.putInt(this.y);
            this.putInt(this.z);
            if(this.protocol <= ProtocolInfo.v_0_13_2){
                return;
            }
            this.putLong(this.entityId);
            return;
        }
        this.reset();
        this.putByte((byte) this.windowId);
        this.putByte((byte) this.type);
        this.putBlockVector3(this.x, this.y, this.z);
        this.putEntityUniqueId(this.entityId);
        if (this.gameVersion.isNetEase() && this.gameVersion.getProtocol() >= GameVersion.V1_21_124_NETEASE.getProtocol()) {
            this.putBoolean(false); //mIsIgnoreBlock
        }
    }
}
