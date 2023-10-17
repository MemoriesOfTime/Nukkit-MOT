package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import lombok.ToString;

/**
 * @author Nukkit Project Team
 */
@ToString
public class PlayerActionPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_ACTION_PACKET;

    public static final int ACTION_START_BREAK = 0;
    public static final int ACTION_ABORT_BREAK = 1;
    public static final int ACTION_STOP_BREAK = 2;
    public static final int ACTION_GET_UPDATED_BLOCK = 3;
    public static final int ACTION_DROP_ITEM = 4;
    public static final int ACTION_START_SLEEPING = 5;
    public static final int ACTION_STOP_SLEEPING = 6;
    public static final int ACTION_RESPAWN = 7;
    public static final int ACTION_JUMP = 8;
    public static final int ACTION_START_SPRINT = 9;
    public static final int ACTION_STOP_SPRINT = 10;
    public static final int ACTION_START_SNEAK = 11;
    public static final int ACTION_STOP_SNEAK = 12;
    public static final int ACTION_CREATIVE_PLAYER_DESTROY_BLOCK = 13;
    public static final int ACTION_DIMENSION_CHANGE_SUCCESS = 14;
    @Deprecated
    public static final int ACTION_DIMENSION_CHANGE_ACK = ACTION_DIMENSION_CHANGE_SUCCESS; //sent when spawning in a different dimension to tell the server we spawned
    public static final int ACTION_START_GLIDE = 15;
    public static final int ACTION_STOP_GLIDE = 16;
    public static final int ACTION_BUILD_DENIED = 17;
    public static final int ACTION_CONTINUE_BREAK = 18;
    public static final int ACTION_CHANGE_SKIN = 19;
    public static final int ACTION_SET_ENCHANTMENT_SEED = 20;
    public static final int ACTION_START_SWIMMING = 21;
    public static final int ACTION_STOP_SWIMMING = 22;
    public static final int ACTION_START_SPIN_ATTACK = 23;
    public static final int ACTION_STOP_SPIN_ATTACK = 24;
    public static final int ACTION_INTERACT_BLOCK = 25;
    public static final int ACTION_PREDICT_DESTROY_BLOCK = 26;
    public static final int ACTION_CONTINUE_DESTROY_BLOCK = 27;
    public static final int ACTION_START_ITEM_USE_ON = 28;
    public static final int ACTION_STOP_ITEM_USE_ON = 29;
    /**
     * @since 1.19.60
     */
    public static final int ACTION_HANDLED_TELEPORT = 30;
    /**
     * @since 1.20.10
     */
    public static final int ACTION_MISSED_SWING = 31;
    /**
     * @since 1.20.10
     */
    public static final int ACTION_START_CRAWLING = 32;
    /**
     * @since 1.20.10
     */
    public static final int ACTION_STOP_CRAWLING = 33;
    /**
     * @since v618 1.20.30
     */
    public static final int ACTION_START_FLYING = 34;
    /**
     * @since v618 1.20.30
     */
    public static final int ACTION_STOP_FLYING = 35;
    /**
     * @since v622 1.20.40
     */
    public static final int ACTION_RECEIVED_SERVER_DATA = 36;

    public long entityId;
    public int action;
    public int x;
    public int y;
    public int z;
    public BlockVector3 resultPosition; //1.19.0开始
    public int face;

    @Override
    public void decode() {
        this.entityId = this.getEntityRuntimeId();
        this.action = this.getVarInt();
        BlockVector3 v = this.getBlockVector3();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            this.resultPosition = this.getBlockVector3();
        }
        this.face = this.getVarInt();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.entityId);
        this.putVarInt(this.action);
        this.putBlockVector3(this.x, this.y, this.z);
        if (protocol >= ProtocolInfo.v1_19_0_29) {
            this.putBlockVector3(this.resultPosition != null ? this.resultPosition : new BlockVector3());
        }
        this.putVarInt(this.face);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
