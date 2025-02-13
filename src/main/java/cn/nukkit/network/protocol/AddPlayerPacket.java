package cn.nukkit.network.protocol;

import cn.nukkit.Server;
import cn.nukkit.entity.data.EntityMetadata;
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
        return NETWORK_ID;
    }

    public UUID uuid;
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
    public Item item;
    /**
     * v1.18.30 and above
     */
    public int gameType = Server.getInstance().getGamemode();
    public EntityMetadata metadata = new EntityMetadata();
    public String deviceId = "";
    public int buildPlatform = -1;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putUUID(this.uuid);
        this.putString(this.username);
        if (protocol >= 223 && protocol <= 282) {
            this.putString("");
            this.putVarInt(0);
        }
        if (protocol < ProtocolInfo.v1_19_10) {
            this.putEntityUniqueId(this.entityUniqueId);
        }
        this.putEntityRuntimeId(this.entityRuntimeId);
        if (protocol >= 223) {
            this.putString(this.platformChatId);
        }
        this.putVector3f(this.x, this.y, this.z);
        this.putVector3f(this.speedX, this.speedY, this.speedZ);
        this.putLFloat(this.pitch);
        this.putLFloat(this.yaw);
        this.putLFloat(this.yaw);
        this.putSlot(protocol, this.item);
        if (protocol >= ProtocolInfo.v1_18_30) {
            this.putVarInt(this.gameType);
        }
        this.put(Binary.writeMetadata(protocol, this.metadata));
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
