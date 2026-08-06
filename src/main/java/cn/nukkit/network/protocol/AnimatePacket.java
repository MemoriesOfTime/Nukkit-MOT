package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.ToString;

/**
 * @author Nukkit Project Team
 */
@ToString
public class AnimatePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ANIMATE_PACKET;

    public long eid;
    public Action action;
    public float rowingTime;

    @Override
    public void decode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.action = Action.fromId((int) this.getUnsignedVarInt());
                this.eid = getVarLong();
                return;
            }
            this.action = Action.fromId(this.getByte() & 0xff);
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.eid = this.getLong();
            }else{
                this.eid = this.getInt();
            }
            return;
        }
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.action = Action.fromId(this.getVarInt());
            this.eid = getEntityRuntimeId();
        }else{
            this.action = Action.fromId((int) this.getUnsignedVarInt());
            this.eid = getEntityUniqueId();
        }
        if (this.action == Action.ROW_RIGHT || this.action == Action.ROW_LEFT) {
            this.rowingTime = this.getLFloat();
        }
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putUnsignedVarInt((byte) action.id);
                this.putVarLong(this.eid);
                return;
            }
            this.putByte((byte) action.id);
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(eid);
            }else{
                this.putInt((int) this.eid);
            }
            return;
        }

        this.reset();
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putVarInt(this.action.getId());
            this.putEntityRuntimeId(this.eid);
        }else {
            this.putUnsignedVarInt(this.action.getId());
            this.putEntityUniqueId(this.eid);
        }
        if (this.action == Action.ROW_RIGHT || this.action == Action.ROW_LEFT) {
            this.putLFloat(this.rowingTime);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.ANIMATE_PACKET;
        }
        return NETWORK_ID;
    }

    public enum Action {
        NO_ACTION(0),
        SWING_ARM(1),
        WAKE_UP(3),
        CRITICAL_HIT(4),
        MAGIC_CRITICAL_HIT(5),
        ROW_RIGHT(128),
        ROW_LEFT(129);

        private static final Int2ObjectMap<Action> ID_LOOKUP = new Int2ObjectOpenHashMap<>();

        static {
            for (Action value : values()) {
                ID_LOOKUP.put(value.id, value);
            }
        }

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static Action fromId(int id) {
            return ID_LOOKUP.get(id);
        }
    }
}
