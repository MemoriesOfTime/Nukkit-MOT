package cn.nukkit.network.protocol;

/**
 * @author glorydark
 */
public class PhotoTransferPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PHOTO_TRANSFER_PACKET;
    public String photoName;
    public String photoData;
    public String bookId; //photos are stored in a sibling directory to the game's folder (screenshots/(some UUID)/bookID/example.png)
    public int type;
    public int sourceType;
    public long ownerActorUniqueId;
    public String newPhotoName; //???

    @Override
    public byte pid() {
        return ProtocolInfo.PHOTO_TRANSFER_PACKET;
    }

    @Override
    public void decode() {
        this.photoName = this.getString();
        this.photoData = this.getString();
        this.bookId = this.getString();
        this.type = this.getByte();
        this.sourceType = this.getByte();
        this.ownerActorUniqueId = this.getLLong();
        this.newPhotoName = this.getString();
    }

    @Override
    public void encode() {
        this.putString(photoName);
        this.putString(photoData);
        this.putString(bookId);
        this.putByte((byte) type);
        this.putByte((byte) sourceType);
        this.putLLong(this.ownerActorUniqueId);
        this.putString(newPhotoName);
    }
}
