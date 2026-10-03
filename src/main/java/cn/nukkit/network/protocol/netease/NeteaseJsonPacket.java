package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

@OnlyNetEase
@ToString
public class NeteaseJsonPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.NETEASE_JSON_PACKET;

    public static final String EVENT_ON_PLAYER_DEATH = "ON_PLAYER_DEATH";
    public static final String EVENT_PLAY_RIDING_ANIMATION = "PLAY_RIDING_ANIMATION";
    public static final String EVENT_CAN_PLAYER_MOVE = "CAN_PLAYER_MOVE";
    public static final String EVENT_SET_PLAYER_SIZE = "SET_PLAYER_SIZE";
    public static final String EVENT_CAN_PLAYER_ATTACK = "CAN_PLAYER_ATTACK";
    public static final String EVENT_CAN_PLAYER_JUMP = "CAN_PLAYER_JUMP";
    public static final String EVENT_SET_PLAYER_POSITION = "SET_PLAYER_POSITION";
    public static final String EVENT_SET_ENTITY_GRAVITY = "SET_ENTITY_GRAVITY";
    public static final String EVENT_SET_LEVEL_GRAVITY = "SET_LEVEL_GRAVITY";
    public static final String EVENT_SET_PLAYER_SIZE_AABB = "SET_PLAYER_SIZE_AABB";
    public static final String EVENT_DISABLE_HUNGER = "DISABLE_HUNGER";
    public static final String EVENT_DISABLE_DROPITEM = "DISABLE_DROPITEM";
    public static final String EVENT_DISABLE_CONTAINERS = "DISABLE_CONTAINERS";
    public static final String EVENT_DISABLE_SOLIDIFY = "DISABLE_SOLIDIFY";
    public static final String EVENT_DISABLE_GRAVITY_IN_LIQUID = "DISABLE_GRAVITY_IN_LIQUID";
    public static final String EVENT_SET_HURTSHADER = "SET_HURTSHADER";
    public static final String EVENT_REMOTE_PLAYER_GAME_TYPE = "RemotePlayerGameType";
    public static final String EVENT_CONTROL_ENTITY_AI = "CONTROL_ENTITY_AI";
    public static final String EVENT_SET_PLAYER_READY_POS_DELTA = "SET_PLAYER_READY_POS_DELTA";
    public static final String EVENT_UPDATE_BANNED_ITEM = "UPDATE_BANNED_ITEM";
    public static final String EVENT_LOCK_DIFFICULTY = "LOCK_DIFFICULTY";
    public static final String EVENT_LOCK_GAME_TYPE = "LOCK_GAME_TYPE";
    public static final String EVENT_LOCK_GAME_RULES_INFO = "LOCK_GAME_RULES_INFO";
    public static final String EVENT_ENABLE_PLAYER_KEEP_INVENTORY = "ENABLE_PLAYER_KEEP_INVENTORY";
    public static final String EVENT_SET_NAME_TAG_INFO = "SetNameTagInfo";
    public static final String EVENT_SET_VIP_NAME_TAG_INFO = "SetVipNameTagInfo";
    public static final String EVENT_SET_MAX_AUTO_STEP = "SET_MAX_AUTO_STEP";
    public static final String EVENT_ADD_PLAYER_BANNED_ITEM = "ADD_PLAYER_BANNED_ITEM";
    public static final String EVENT_SET_HEALTH_LEVEL = "SET_HEALTH_LEVEL";
    public static final String EVENT_SET_HEALTH_TICK = "SET_HEALTH_TICK";
    public static final String EVENT_SET_NATURAL_REGEN = "SET_NATURAL_REGEN";
    public static final String EVENT_SET_STARVE_LEVEL = "SET_STARVE_LEVEL";
    public static final String EVENT_SET_JUMP_POWER = "SET_JUMP_POWER";
    public static final String EVENT_SET_BLOCK_DICT = "SET_BLOCK_DICT";
    public static final String EVENT_SET_NATURAL_STARVE = "SET_NATURAL_STARVE";
    public static final String EVENT_SET_SHEARS_DESTORY_SPEED = "SET_SHEARS_DESTORY_SPEED";
    public static final String EVENT_SET_ENTITY_ATTACK_SPEED = "SET_ENTITY_ATTACK_SPEED";
    public static final String EVENT_ADD_CONTAINER_MIX = "ADD_CONTAINER_MIX";
    public static final String EVENT_ADD_POTION_MIX = "ADD_POTION_MIX";
    public static final String EVENT_SET_SPECIFIC_PASSENGER_INDEX = "SET_SPECIFIC_PASSENGER_INDEX";
    public static final String EVENT_SET_LOCK_PASSENGER = "SET_LOCK_PASSENGER";
    public static final String EVENT_SET_PASSENGER_INDEX = "SET_PASSENGER_INDEX";
    public static final String EVENT_UPDATE_PASSENGER = "UPDATE_PASSENGER";
    public static final String EVENT_CUSTOMAPPEARANCE_UUID = "CUSTOMAPPEARANCE_UUID";
    public static final String EVENT_FURNACE_LIT = "FURNACE_LIT";
    public static final String EVENT_SYNC_OWNER_ID = "SYNC_OWNER_ID";
    public static final String EVENT_SYNC_FISHING_LINE_MAX = "SYNC_FISHING_LINE_MAX";
    public static final String EVENT_SYNC_FISHING_LINE_COLOR = "SYNC_FISHING_LINE_COLOR";

    public String json = "{}";

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        return (byte) NETWORK_ID;
    }

    @Override
    public void decode() {
        this.json = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.json != null ? this.json : "{}");
    }
}
