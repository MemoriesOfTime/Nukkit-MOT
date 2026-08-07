package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import cn.nukkit.math.BlockVector3;
import lombok.ToString;

/**
 * Created by Pub4Game on 03.07.2016.
 */
@ToString
public class ItemFrameDropItemPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET;

    public int x;
    public int y;
    public int z;

    public Item dropItem;// 0.14.3

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.z = getInt();
            this.y = getInt();
            this.x = getInt();
            this.dropItem = getSlot_old(this.protocol);
            return;
        }
        BlockVector3 v = this.getBlockVector3();
        this.z = v.z;
        this.y = v.y;
        this.x = v.x;
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }
}
