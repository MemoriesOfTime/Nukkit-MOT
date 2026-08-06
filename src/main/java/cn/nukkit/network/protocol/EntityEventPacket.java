package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class EntityEventPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ENTITY_EVENT_PACKET;

    public static final int NONE = 0;
    public static final int JUMP = 1;
    public static final int HURT_ANIMATION = 2;
    public static final int DEATH_ANIMATION = 3;
    public static final int ARM_SWING = 4;

    public static final int STOP_ATTACK = 5;
    public static final int TAME_FAIL = 6;
    public static final int TAME_SUCCESS = 7;
    public static final int SHAKE_WET = 8;
    public static final int USE_ITEM = 9;
    public static final int EAT_GRASS_ANIMATION = 10;
    public static final int FISH_HOOK_BUBBLE = 11;
    public static final int FISH_HOOK_POSITION = 12;
    public static final int FISH_HOOK_HOOK = 13;
    public static final int FISH_HOOK_TEASE = 14;
    public static final int SQUID_INK_CLOUD = 15;
    public static final int ZOMBIE_VILLAGER_CURE = 16;
    public static final int AMBIENT_SOUND = 17;
    public static final int RESPAWN = 18;
    public static final int IRON_GOLEM_OFFER_FLOWER = 19;
    public static final int IRON_GOLEM_WITHDRAW_FLOWER = 20;
    public static final int LOVE_PARTICLES = 21;

    public static final int VILLAGER_ANGRY = 22;
    public static final int VILLAGER_HAPPY = 23;
    public static final int WITCH_SPELL_PARTICLES = 24;
    public static final int FIREWORK_EXPLOSION = 25;
    public static final int IN_LOVE_PARTICLES = 26;
    public static final int SILVERFISH_SPAWN_ANIMATION = 27;
    public static final int GUARDIAN_ATTACK = 28;
    public static final int WITCH_DRINK_POTION = 29;
    public static final int WITCH_THROW_POTION = 30;
    public static final int MINECART_TNT_PRIME_FUSE = 31;
    public static final int CREEPER_PRIME_FUSE = 32;
    public static final int AIR_SUPPLY_EXPIRED = 33;
    public static final int ENCHANT = 34;
    public static final int ELDER_GUARDIAN_CURSE = 35;
    public static final int AGENT_ARM_SWING = 36;
    public static final int ENDER_DRAGON_DEATH = 37;
    public static final int DUST_PARTICLES = 38;
    public static final int ARROW_SHAKE = 39;

    public static final int EATING_ITEM = 57;

    public static final int BABY_ANIMAL_FEED = 60;
    public static final int DEATH_SMOKE_CLOUD = 61;
    public static final int COMPLETE_TRADE = 62;
    public static final int REMOVE_LEASH = 63;
    public static final int CARAVAN_UPDATED = 64;
    public static final int CONSUME_TOTEM = 65;
    public static final int PLAYER_CHECK_TREASURE_HUNTER_ACHIEVEMENT = 66;
    public static final int ENTITY_SPAWN = 67;
    public static final int DRAGON_PUKE = 68;
    public static final int MERGE_ITEMS = 69;
    public static final int START_SWIM = 70;
    public static final int BALLOON_POP = 71;
    public static final int TREASURE_HUNT = 72;
    public static final int AGENT_SUMMON = 73;
    public static final int CHARGED_CROSSBOW = 74;
    public static final int FALL = 75;
    public static final int GROW_UP = 76;
    public static final int VIBRATION_DETECTED = 77;
    public static final int DRINK_MILK = 78;





    public static final byte HURT_ANIMATION_014 = 2;
    public static final byte DEATH_ANIMATION_014 = 3;

    public static final byte TAME_FAIL_014 = 6;
    public static final byte TAME_SUCCESS_014 = 7;
    public static final byte SHAKE_WET_014 = 8;
    public static final byte USE_ITEM_014 = 9;
    public static final byte EAT_GRASS_ANIMATION_014 = 10;
    public static final byte FISH_HOOK_BUBBLE_014 = 11;
    public static final byte FISH_HOOK_POSITION_014 = 12;
    public static final byte FISH_HOOK_HOOK_014 = 13;
    public static final byte FISH_HOOK_TEASE_014 = 14;
    public static final byte SQUID_INK_CLOUD_014 = 15;
    public static final byte AMBIENT_SOUND_014 = 16;
    public static final byte RESPAWN_014 = 17;


    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.ENTITY_EVENT_PACKET;
        }
        return NETWORK_ID;
    }

    public long eid;
    public int event;
    public int event_014;
    public int data = 0;

    public int originProtocol = -1;

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.eid = this.getLong();
            this.event = (byte) this.getByte();
            this.event_014 = this.event;
            return;
        }
        this.eid = this.getEntityRuntimeId();
        this.event = this.getByte();
        this.data = this.getVarInt();
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.tryReset();
            this.putLong(this.eid);
            switch(event){
                case AMBIENT_SOUND:
                    event_014 = AMBIENT_SOUND_014;
                    break;
                case RESPAWN:
                    event_014 = RESPAWN_014;
                    break;
                default:
                    event_014 = event;
                    break;
            }
            this.putByte((byte) (event_014 & 0xff));
            return;
        }
        this.reset();
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.eid);
        }else {
            this.putEntityUniqueId(this.eid);
        }
        this.putByte((byte) this.event);
        if (this.event == EATING_ITEM && this.originProtocol > 0 && this.originProtocol != this.protocol) {
            // 1.19.63-=  <=> 1.19.70+= 喝药水声音转换
            // 1.20.40-= <=> 1.20.50+= 喝药水声音转换
            if (this.originProtocol > this.protocol) {
                if (this.originProtocol >= ProtocolInfo.v1_20_50 && this.protocol < ProtocolInfo.v1_20_50) {
                    if (this.data >= 28114944) {
                        this.data -= 65536;
                    }
                }
                if (this.originProtocol >= ProtocolInfo.v1_19_70_24 && this.protocol < ProtocolInfo.v1_19_70_24) {
                    if (this.data >= 27983872) {
                        this.data -= 65536;
                    }
                }
            } else {
                if (this.originProtocol < ProtocolInfo.v1_19_70_24 && this.protocol >= ProtocolInfo.v1_19_70_24) {
                    if (this.data >= 27918336) {
                        this.data += 65536;
                    }
                }
                if (this.originProtocol < ProtocolInfo.v1_20_50 && this.protocol >= ProtocolInfo.v1_20_50) {
                    if (this.data >= 28049408) {
                        this.data += 65536;
                    }
                }
            }
        }
        this.putVarInt(this.data);
    }
}
