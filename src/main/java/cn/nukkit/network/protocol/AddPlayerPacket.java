package cn.nukkit.network.protocol;

import cn.nukkit.Server;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.EntityMetadataController;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.utils.Binary;
import lombok.ToString;

import java.util.UUID;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class AddPlayerPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ADD_PLAYER_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    public UUID uuid;
    public long clientID;
    public String username;
    public long entityUniqueId;
    public long entityRuntimeId;
    public String platformChatId = "";
    public float x;
    public float y;
    public float z;
    public float speedX;
    public float speedY;
    public float speedZ;
    public float pitch;
    public float yaw;
    public float headYaw = -1;
    public Item item;
    public boolean slim = false;
    public Skin skin;
    /**
     * v1.18.30 and above
     */
    public int gameType = Server.getInstance().getGamemode();
    public EntityMetadata metadata = new EntityMetadata();
    public String deviceId = "";
    public int buildPlatform = -1;

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol <= ProtocolInfo.v_0_11_0) {
                this.putLong(this.clientID);
            }else{
                this.putUUID(this.uuid);
            }
            if(this.protocol >= ProtocolInfo.v_0_16_0) {
                this.putString(this.username);
                this.putEntityUniqueId(this.entityUniqueId);
                this.putEntityUniqueId(this.entityRuntimeId);
                this.putVector3f(this.x, this.y, this.z);
                this.putVector3f(this.speedX, this.speedY, this.speedZ);
                this.putLFloat(this.pitch);
                this.putLFloat(this.yaw); //TODO headrot
                this.putLFloat(this.yaw);
                this.putSlot(this.protocol, this.item);
                this.put(Binary.writeMetadata_016(this.metadata));
                return;
            }
            this.putString_old(this.username);
            if (this.protocol <= ProtocolInfo.v_0_10_0){
                this.putInt((int) this.entityRuntimeId);
            }else{
                this.putLong(this.entityRuntimeId);
            }
            this.putFloat(this.x);
            this.putFloat(this.y);
            this.putFloat(this.z);
            if (this.protocol > ProtocolInfo.v_0_10_0){
                this.putFloat(this.speedX);
                this.putFloat(this.speedY);
                this.putFloat(this.speedZ);
                this.putFloat(this.yaw);
                this.putFloat(this.yaw); //TODO headrot
                this.putFloat(this.pitch);
            }else{
                this.putByte((byte) this.yaw);
                this.putByte((byte) this.pitch);
            }
            if(this.protocol <= ProtocolInfo.v_0_11_0) {
                this.putShort(this.item.getId());
                this.putShort(this.item.getDamage());
                if(this.protocol >= ProtocolInfo.v_0_11_0) {
                    this.putBoolean( Skin.MODEL_ALEX.equals(this.skin.getSkinId()) );// is slim?
                    this.putShort( skin.getSkinData().data.length );
                    this.put( skin.getSkinData().data );
                }
            }else {
                this.putSlot_old(this.protocol, this.item);
            }

            // 测试, 只发送默认metadata
            // this.metadata = EntityMetadataController.getDefaultEntityMetadata(this.protocol);

            this.put(Binary.writeMetadata_old(this.metadata));

            return;
        }

        this.reset();
        this.putUUID(this.uuid);
        this.putString(this.username);
        if (protocol < ProtocolInfo.v1_2_0) {
            this.putEntityUniqueId(this.entityUniqueId);
            this.putEntityUniqueId(this.entityRuntimeId);
            this.putVector3f(this.x, this.y, this.z);
            this.putVector3f(this.speedX, this.speedY, this.speedZ);
            this.putLFloat(this.pitch);
            this.putLFloat(this.headYaw == -1 ? this.yaw : this.headYaw);
            this.putLFloat(this.yaw);
            this.putSlot(gameVersion, this.item);
            this.put(Binary.writeMetadata(gameVersion, this.metadata));
            return;
        }

        if (protocol >= 223 && protocol <= 282) {
            this.putString("");
            this.putVarInt(0);
        }
        if (protocol < ProtocolInfo.v1_19_10) {
            this.putEntityUniqueId(this.entityUniqueId);
        }
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.entityRuntimeId);
        }else{
            this.putEntityUniqueId(this.entityRuntimeId);
        }
        if (protocol >= 223) {
            this.putString(this.platformChatId);
        }
        this.putVector3f(this.x, this.y, this.z);
        this.putVector3f(this.speedX, this.speedY, this.speedZ);
        this.putLFloat(this.pitch);
        this.putLFloat(this.yaw);
        this.putLFloat(this.headYaw == -1 ? this.yaw : this.headYaw);
        this.putSlot(gameVersion, this.item);
        if (protocol >= ProtocolInfo.v1_18_30) {
            this.putVarInt(this.gameType);
        }
        this.put(Binary.writeMetadata(gameVersion, this.metadata));
        if (protocol > 274) {
            if (protocol < ProtocolInfo.v1_19_10) {
                this.putUnsignedVarInt(0);
                this.putUnsignedVarInt(0);
                this.putUnsignedVarInt(0);
                this.putUnsignedVarInt(0);
                this.putUnsignedVarInt(0);
            }else if (protocol >= ProtocolInfo.v1_19_40) {
                this.putUnsignedVarInt(0); // Entity properties int
                this.putUnsignedVarInt(0); // Entity properties float
            }
            this.putLLong(entityUniqueId);
            if (protocol >= ProtocolInfo.v1_19_10) {
                this.putUnsignedVarInt(0); // playerPermission
                this.putUnsignedVarInt(0); // commandPermission
                this.putUnsignedVarInt(1); // abilitiesLayer size
                this.putLShort(1); // BASE layer type
                this.putLInt(262143); // abilitiesSet - all abilities
                this.putLInt(63); // abilityValues - survival abilities
                this.putLFloat(0.1f); // flySpeed
                this.putLFloat(0.05f); // walkSpeed
                if (this.protocol >= ProtocolInfo.v1_21_60) {
                    this.putLFloat(1.0f); // getVerticalFlySpeed()
                }
            }
            this.putUnsignedVarInt(0);
            this.putString(deviceId);
            if (protocol >= 388) {
                this.putLInt(buildPlatform);
            }
        }
    }
}
