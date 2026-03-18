package cn.nukkit.network.protocol;

/**
 * @author glorydark
 */
public class CreatePhotoPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.CREATE_PHOTO_PACKET;
    private long actorUniqueId;
    private String photoName;
    private String photoItemName;

    public long getActorUniqueId() {
        return actorUniqueId;
    }

    public void setActorUniqueId(long actorUniqueId) {
        this.actorUniqueId = actorUniqueId;
    }

    public String getPhotoItemName() {
        return photoItemName;
    }

    public void setPhotoItemName(String photoItemName) {
        this.photoItemName = photoItemName;
    }

    public String getPhotoName() {
        return photoName;
    }

    public void setPhotoName(String photoName) {
        this.photoName = photoName;
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.actorUniqueId = this.getLLong(); //why be consistent mojang ?????
        this.photoName = this.getString();
        this.photoItemName = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putLLong(this.actorUniqueId);
        this.putString(this.photoName);
        this.putString(this.photoItemName);
    }
}
