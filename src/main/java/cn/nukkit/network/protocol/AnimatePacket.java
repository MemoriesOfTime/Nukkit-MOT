package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.SwingSource;
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
    public float data;
    public float rowingTime;
    /**
     * @since 1.21.130
     */
    public SwingSource swingSource = SwingSource.NONE;

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.action = Action.fromId(this.getByte());
            if (this.action == null) {
                this.action = Action.NO_ACTION;
            }
        } else {
            this.action = Action.fromId(this.getVarInt());
        }
        this.eid = getEntityRuntimeId();
        if (this.protocol >= ProtocolInfo.v1_21_120) {
            this.data = this.getLFloat();
        }
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            if (this.getBoolean()) {
                this.swingSource = SwingSource.from(this.getString()); // Swing source
            }
        } else {
            if (this.action == Action.ROW_RIGHT || this.action == Action.ROW_LEFT) {
                this.rowingTime = this.getLFloat();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.putByte((byte) this.action.getId());
        } else {
            this.putVarInt(this.action.getId());
        }
        this.putEntityRuntimeId(this.eid);
        if (this.protocol >= ProtocolInfo.v1_21_120) {
            this.putLFloat(this.data);
        }
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.putBoolean(this.swingSource != SwingSource.NONE); // Swing source (optional)
            if (this.swingSource != SwingSource.NONE) {
                this.putString(this.swingSource.getName());
            }
        } else {
            if (this.action == Action.ROW_RIGHT || this.action == Action.ROW_LEFT) {
                this.putLFloat(this.rowingTime);
            }
        }
    }

    @Override
    public byte pid() {
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
