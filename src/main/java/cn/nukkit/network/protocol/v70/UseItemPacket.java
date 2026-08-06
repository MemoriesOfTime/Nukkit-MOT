package cn.nukkit.network.protocol.v70;

import cn.nukkit.item.Item;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class UseItemPacket  extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public int x;
    public int y;
    public int z;

    public long eid;

    public int face;

    public float fx;
    public float fy;
    public float fz;

    public float posX;
    public float posY;
    public float posZ;

    public int unknown;

    public int slot;

    public Item item;

    public short meta;


    @Override
    public void decode() {
        if(this.protocol >= ProtocolInfo.v_0_16_0){
            BlockVector3 v = this.getBlockVector3();
            this.x = v.x;
            this.y = v.y;
            this.z = v.z;
            this.face = this.getVarInt();
            Vector3f faceVector3 = this.getVector3f();
            this.fx = faceVector3.x;
            this.fy = faceVector3.y;
            this.fz = faceVector3.z;
            Vector3f playerPos = this.getVector3f();
            this.posX = playerPos.x;
            this.posY = playerPos.y;
            this.posZ = playerPos.z;
            this.unknown = this.getByte();
            this.item = this.getSlotV113(this.protocol);
            return;
        }
        this.x = this.getInt();
        this.y = this.getInt();
        this.z = this.getInt();
        if(this.protocol >= ProtocolInfo.v_0_10_0){
            this.face = this.getByte();
        }else{
            this.face = this.getInt();
        }
        if(this.protocol <= ProtocolInfo.v_0_11_0){
            this.item=Item.get(this.getShort());
            this.meta = (short) this.getShort();
            this.item.setDamage((int)this.meta);
            this.eid = this.getLong();
        }
        this.fx = this.getFloat();
        this.fy = this.getFloat();
        this.fz = this.getFloat();
        this.posX = this.getFloat();
        this.posY = this.getFloat();
        this.posZ = this.getFloat();
        if(this.protocol <= ProtocolInfo.v_0_11_0){
            return;
        }

        if(this.protocol > ProtocolInfo.v_0_14_1){// 0.14.1无
            this.slot = this.getInt();
        }

        this.item = this.getSlot_old(this.protocol);
    }

    @Override
    public void encode() {
        this.tryReset();
    }
}
