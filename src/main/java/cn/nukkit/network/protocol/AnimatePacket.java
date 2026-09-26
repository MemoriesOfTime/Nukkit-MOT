package cn.nukkit.network.protocol;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;

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
     * @since v897
     */
    public SwingSource swingSource = SwingSource.NONE;

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_21_130_28) {
            this.action = Action.fromId(this.getByte());
        } else {
            this.action = Action.fromId(this.getVarInt());
        }
        if (this.action == null) {
            this.action = Action.NO_ACTION;
        }
        this.eid = getEntityRuntimeId();
        if (this.protocol >= ProtocolInfo.v1_21_120) {
            this.data = this.getLFloat();
        }
        if (protocol < ProtocolInfo.v1_21_130_28 && (this.action == Action.ROW_RIGHT || this.action == Action.ROW_LEFT)) {
            this.rowingTime = this.getLFloat();
        }
        if (protocol >= ProtocolInfo.v1_21_130_28) {
            this.swingSource = this.getOptional(SwingSource.NONE, stream -> SwingSource.from(stream.getString()));
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_21_130_28) {
            this.putByte((byte) this.action.getId());
        } else {
            this.putVarInt(this.action.getId());
        }
        this.putEntityRuntimeId(this.eid);
        if (this.protocol >= ProtocolInfo.v1_21_120) {
            this.putLFloat(this.data);
        }
        if (protocol < ProtocolInfo.v1_21_130_28 && (this.action == Action.ROW_RIGHT || this.action == Action.ROW_LEFT)) {
            this.putLFloat(this.rowingTime);
        }
        if (protocol >= ProtocolInfo.v1_21_130_28) {
            this.putOptional(o -> o != SwingSource.NONE, this.swingSource, o -> this.putString(o.getName()));
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

    public enum SwingSource {
        NONE("none"),
        BUILD("build"),
        MINE("mine"),
        INTERACT("interact"),
        ATTACK("attack"),
        USE_ITEM("useitem"),
        THROW_ITEM("throwitem"),
        DROP_ITEM("dropitem"),
        EVENT("event");

        private static final HashMap<String, SwingSource> BY_NAME = new HashMap<>();

        static {
            for (SwingSource value : values()) {
                BY_NAME.put(value.name, value);
            }
        }

        @Getter
        private final String name;

        SwingSource(String name) {
            this.name = name;
        }

        public static SwingSource from(String name) {
            return BY_NAME.get(name);
        }
    }
}
