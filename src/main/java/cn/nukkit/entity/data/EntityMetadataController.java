package cn.nukkit.entity.data;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * 因为引入了0.14, 因此用来方便控制metadata设置, 维护两个版本的生物属性
 */
public class EntityMetadataController {

    protected final EntityMetadata dataProperties = new EntityMetadata()
            .putLong(Entity.DATA_FLAGS, 0)
            .putByte(Entity.DATA_COLOR, 0)
            .putShort(Entity.DATA_AIR, 400)
            .putShort(Entity.DATA_MAX_AIR, 400)
            .putString(Entity.DATA_NAMETAG, "")
            .putLong(Entity.DATA_LEAD_HOLDER_EID, -1)
            .putFloat(Entity.DATA_SCALE, 1f);

    protected final EntityMetadata dataProperties_014 = new EntityMetadata()
            .putByte(Entity.DATA_FLAGS_014, 0)
            .putShort(Entity.DATA_AIR_014, 300)
            .putString(Entity.DATA_NAMETAG_014, "")
            .putBoolean(Entity.DATA_SHOW_NAMETAG_014, true)
            .putBoolean(Entity.DATA_SILENT_014, false)
            .putBoolean(Entity.DATA_NO_AI_014, false)
            .putLong(Entity.DATA_LEAD_HOLDER_014, -1)
            .putByte(Entity.DATA_LEAD_014, 0);

    protected final EntityMetadata dataProperties_016 = new EntityMetadata()
            .putLong(Entity.DATA_FLAGS, 0)
            .putShort(Entity.DATA_AIR, 400)
            .putShort(Entity.DATA_MAX_AIR_016, 400)
            .putString(Entity.DATA_NAMETAG, "")
            .putFloat(Entity.DATA_LEAD_HOLDER_EID_016, 1f)
            .putFloat(Entity.DATA_SCALE_016, 1f);

    public EntityMetadata getDataProperties(int protocol){
        if(protocol <= ProtocolInfo.v_0_15_10) {
            return dataProperties_014;
        }else if(protocol <= ProtocolInfo.v_1_0_0) {
            return dataProperties_016;
        }else{
            return dataProperties;
        }
    }

    public static EntityMetadata getDefaultEntityMetadata(int protocol){
        if(protocol <= ProtocolInfo.v_0_15_10){
            return new EntityMetadata()
                    .putByte(Entity.DATA_FLAGS_014, 0)
                    .putShort(Entity.DATA_AIR_014, 300)
                    .putString(Entity.DATA_NAMETAG_014, "")
                    .putBoolean(Entity.DATA_SHOW_NAMETAG_014, true)
                    .putBoolean(Entity.DATA_SILENT_014, false)
                    .putBoolean(Entity.DATA_NO_AI_014, false)
                    .putLong(Entity.DATA_LEAD_HOLDER_014, -1)
                    .putByte(Entity.DATA_LEAD_014, 0);
        } else if (protocol <= ProtocolInfo.v_1_0_0) {
            return new EntityMetadata()
                    .putLong(Entity.DATA_FLAGS, 0)
                    .putShort(Entity.DATA_AIR, 400)
                    .putShort(Entity.DATA_MAX_AIR, 400)
                    .putString(Entity.DATA_NAMETAG, "")
                    .putFloat(Entity.DATA_LEAD_HOLDER_EID, 1f)
                    .putFloat(Entity.DATA_SCALE, 1f);
        }else{
            return new EntityMetadata()
                    .putLong(Entity.DATA_FLAGS, 0)
                    .putByte(Entity.DATA_COLOR, 0)
                    .putShort(Entity.DATA_AIR, 400)
                    .putShort(Entity.DATA_MAX_AIR, 400)
                    .putString(Entity.DATA_NAMETAG, "")
                    .putLong(Entity.DATA_LEAD_HOLDER_EID, -1)
                    .putFloat(Entity.DATA_SCALE, 1f);
        }
    }

    public static int highDataFlagToOldDataFlag(int highPropertyId, int highId, int protocolId){
        if (protocolId <= ProtocolInfo.v_0_15_10){
            if(highPropertyId == Entity.DATA_FLAGS){
                switch(highId){
                    case Entity.DATA_FLAG_RIDING:
                        return Entity.DATA_FLAG_RIDING_014;
                    case Entity.DATA_FLAG_ACTION:
                        return Entity.DATA_FLAG_ACTION_014;
                    case Entity.DATA_FLAG_INVISIBLE:
                        return Entity.DATA_FLAG_INVISIBLE_014;
                    case Entity.DATA_FLAG_SNEAKING:
                        return Entity.DATA_FLAG_SNEAKING_014;
                    case Entity.DATA_FLAG_ONFIRE:
                        return Entity.DATA_FLAG_ONFIRE_014;
                    case Entity.DATA_FLAG_SPRINTING:
                        return Entity.DATA_FLAG_SPRINTING_014;
                    default:
                        break;
                }
            }else if(highPropertyId == Entity.DATA_PLAYER_FLAGS){
                switch (highId){
                    case EntityHuman.DATA_PLAYER_BED_POSITION:
                        return EntityHuman.DATA_PLAYER_BED_POSITION_014;
                    case EntityHuman.DATA_PLAYER_FLAG_DEAD:
                        return EntityHuman.DATA_PLAYER_FLAG_DEAD;
                    case EntityHuman.DATA_PLAYER_FLAG_SLEEP:
                        return EntityHuman.DATA_PLAYER_FLAG_SLEEP;
                    default:
                        break;
                }
            }
        } else if (protocolId <= ProtocolInfo.v_1_0_0){
            if(highPropertyId == Entity.DATA_FLAGS){
                switch(highId){
                    case Entity.DATA_FLAG_RESTING:
                        return Entity.DATA_FLAG_RESTING_016;
                    case Entity.DATA_FLAG_SITTING:
                        return Entity.DATA_FLAG_SITTING_016;
                    case Entity.DATA_FLAG_ANGRY:
                        return Entity.DATA_FLAG_ANGRY_016;
                    case Entity.DATA_FLAG_INTERESTED:
                        return Entity.DATA_FLAG_INTERESTED_016;
                    case Entity.DATA_FLAG_CHARGED:
                        return Entity.DATA_FLAG_CHARGED_016;
                    case Entity.DATA_FLAG_TAMED:
                        return Entity.DATA_FLAG_TAMED_016;
                    case Entity.DATA_FLAG_LEASHED:
                        return Entity.DATA_FLAG_LEASHED_016;
                    case Entity.DATA_FLAG_SHEARED:
                        return Entity.DATA_FLAG_SHEARED_016;
                    case Entity.DATA_FLAG_GLIDING:
                        return Entity.DATA_FLAG_FALL_FLYING_016;
                    case Entity.DATA_FLAG_ELDER:
                        return Entity.DATA_FLAG_ELDER_016;
                    case Entity.DATA_FLAG_MOVING:
                        return Entity.DATA_FLAG_MOVING_016;
                    case Entity.DATA_FLAG_BREATHING:
                        return Entity.DATA_FLAG_BREATHING_016;
                    case Entity.DATA_FLAG_CHESTED:
                        return Entity.DATA_FLAG_CHESTED_016;
                    case Entity.DATA_FLAG_STACKABLE:
                        return Entity.DATA_FLAG_STACKABLE_016;
                    default:
                        break;
                }
                if(highId >= Entity.DATA_FLAG_ONFIRE && highId <= Entity.DATA_FLAG_WALLCLIMBING){
                    return highId;
                }
            }else if(highPropertyId == Entity.DATA_PLAYER_FLAGS){
                switch (highId){
                    case EntityHuman.DATA_PLAYER_BED_POSITION:
                        return EntityHuman.DATA_PLAYER_BED_POSITION_016;
                    case EntityHuman.DATA_PLAYER_FLAG_DEAD:
                        return EntityHuman.DATA_PLAYER_FLAG_DEAD;
                    case EntityHuman.DATA_PLAYER_FLAG_SLEEP:
                        return EntityHuman.DATA_PLAYER_FLAG_SLEEP;
                    case EntityHuman.DATA_PLAYER_BUTTON_TEXT:
                        return EntityHuman.DATA_PLAYER_BUTTON_TEXT;
                    default:
                        break;
                }
            }
        }
        return -1;
    }

    public static EntityData highMetadataToOldMetadata(EntityData highMetadata, int protocolId){
        Object heightData = highMetadata.getData();
        if (protocolId <= ProtocolInfo.v_0_15_10){
            switch(highMetadata.getId()){
                case EntityHuman.DATA_PLAYER_BED_POSITION:
                    BlockVector3 pos = ((BlockVector3) heightData);
                    return new IntPositionEntityData(EntityHuman.DATA_PLAYER_BED_POSITION_014, pos.x, pos.y, pos.z);
                case Entity.DATA_NAMETAG:
                    return new StringEntityData(Entity.DATA_NAMETAG_014, (String) heightData);
                case Entity.DATA_AIR:
                    return new ShortEntityData(Player.DATA_AIR_014, (int) heightData);
                case Entity.DATA_POTION_COLOR:
                    return new IntEntityData(Entity.DATA_POTION_COLOR_014, (int) heightData);
                case Entity.DATA_POTION_AMBIENT:
                    return new ByteEntityData(Entity.DATA_POTION_AMBIENT_014, (int) heightData);
            }
        } else if (protocolId <= ProtocolInfo.v_1_0_0){
            switch(highMetadata.getId()){
                case Entity.DATA_PLAYER_FLAGS:
                    return new ByteEntityData(Entity.DATA_PLAYER_FLAGS_016, (int) heightData);
                case Entity.DATA_PLAYER_INDEX:
                    return new IntEntityData(Entity.DATA_PLAYER_INDEX_016, (int) heightData);
                case Entity.DATA_PLAYER_BED_POSITION:
                    BlockVector3 pos = ((BlockVector3) heightData);
                    return new IntPositionEntityData(Entity.DATA_PLAYER_BED_POSITION_016, pos.x, pos.y, pos.z);
                case Entity.DATA_LEAD_HOLDER_EID:
                    return new LongEntityData(Player.DATA_LEAD_HOLDER_EID_016, (int) heightData);
                case Entity.DATA_SCALE:
                    return new FloatEntityData(Entity.DATA_SCALE_016, (float) heightData);
                case Entity.DATA_INTERACTIVE_TAG:
                    return new StringEntityData(Entity.DATA_INTERACTIVE_TAG_016, (String) heightData);
                case 41: // DATA_URL_TAG
                    return new StringEntityData(Entity.DATA_URL_TAG_016, (String) heightData);
                case Entity.DATA_MAX_AIR:
                    return new ShortEntityData(Entity.DATA_MAX_AIR_016, (int) heightData);
                case Entity.DATA_MARK_VARIANT:
                    return new IntEntityData(Entity.DATA_MARK_VARIANT_016, (int) heightData);
                case Entity.DATA_CONTAINER_TYPE:
                    return new ByteEntityData(Entity.DATA_CONTAINER_TYPE_016, (int) heightData);
                case Entity.DATA_CONTAINER_BASE_SIZE:
                    return new IntEntityData(Entity.DATA_CONTAINER_BASE_SIZE_016, (int) heightData);
                case Entity.DATA_CONTAINER_EXTRA_SLOTS_PER_STRENGTH:
                    return new IntEntityData(Entity.DATA_CONTAINER_EXTRA_SLOTS_PER_STRENGTH_016, (int) heightData);
            }
            if (highMetadata.getId() >= Entity.DATA_FLAG_ONFIRE && highMetadata.getId() <= Entity.DATA_FLAG_POWERED){
                return highMetadata;
            } else if (highMetadata.getId() >= Entity.DATA_FLAG_GRAVITY && highMetadata.getId() <= Entity.DATA_FLAG_PREGNANT) {
                return highMetadata;
            }
        }
        return null;
    }

}





















