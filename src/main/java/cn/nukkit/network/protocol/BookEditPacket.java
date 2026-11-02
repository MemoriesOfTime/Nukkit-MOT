package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class BookEditPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BOOK_EDIT_PACKET;

    private static final int MAX_PAGE_CHARS = 256;
    private static final int MAX_PHOTO_NAME_CHARS = 256;
    private static final int MAX_SIGNED_FIELD_CHARS = 64;

    public Action action;
    public int inventorySlot;
    public int pageNumber;
    public int secondaryPageNumber;

    public String text;
    public String photoName;

    public String title;
    public String author;
    public String xuid;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_26_0) {
            // v924: inventorySlot moved before action, uses VarInt for slot and action
            this.inventorySlot = this.getVarInt();
            this.action = Action.values()[(int) this.getUnsignedVarInt()];
        } else {
            this.action = Action.values()[this.getByte()];
            this.inventorySlot = this.getByte();
        }

        switch (this.action) {
            case REPLACE_PAGE:
            case ADD_PAGE:
                this.pageNumber = this.protocol >= ProtocolInfo.v1_26_0 ? this.getVarInt() : this.getByte();
                this.text = this.getString(MAX_PAGE_CHARS);
                this.photoName = this.getString(MAX_PHOTO_NAME_CHARS);
                break;
            case DELETE_PAGE:
                this.pageNumber = this.protocol >= ProtocolInfo.v1_26_0 ? this.getVarInt() : this.getByte();
                break;
            case SWAP_PAGES:
                this.pageNumber = this.protocol >= ProtocolInfo.v1_26_0 ? this.getVarInt() : this.getByte();
                this.secondaryPageNumber = this.protocol >= ProtocolInfo.v1_26_0 ? this.getVarInt() : this.getByte();
                break;
            case SIGN_BOOK:
                this.title = this.getString(MAX_SIGNED_FIELD_CHARS);
                this.author = this.getString(MAX_SIGNED_FIELD_CHARS);
                this.xuid = this.getString(MAX_SIGNED_FIELD_CHARS);
                break;
        }
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    public enum Action {
        REPLACE_PAGE,
        ADD_PAGE,
        DELETE_PAGE,
        SWAP_PAGES,
        SIGN_BOOK
    }
}
