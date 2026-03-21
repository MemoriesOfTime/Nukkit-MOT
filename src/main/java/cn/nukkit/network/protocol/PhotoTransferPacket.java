package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class PhotoTransferPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PHOTO_TRANSFER_PACKET;
    public String photoName;
    public String photoData;
    //photos are stored in a sibling directory to the game's folder (screenshots/(some UUID)/bookID/example.png)
    public String bookId;
    /**
     * @since v465
     */
    public int type;
    /**
     * @since v465
     */
    public int sourceType;
    /**
     * @since v465
     */
    public long ownerActorUniqueId;
    /**
     * @since v465
     */
    public String newPhotoName;

    @Override
    public byte pid() {
        return ProtocolInfo.PHOTO_TRANSFER_PACKET;
    }

    @Override
    public void decode() {
        this.photoName = this.getString();
        this.photoData = this.getString();
        this.bookId = this.getString();
        if (this.protocol >= ProtocolInfo.v1_17_30) {
            this.type = this.getByte();
            this.sourceType = this.getByte();
            this.ownerActorUniqueId = this.getLLong();
            this.newPhotoName = this.getString();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(photoName);
        this.putString(photoData);
        this.putString(bookId);
        if (this.protocol >= ProtocolInfo.v1_17_30) {
            this.putByte((byte) type);
            this.putByte((byte) sourceType);
            this.putLLong(this.ownerActorUniqueId);
            this.putString(newPhotoName);
        }
    }
}
