package cn.nukkit.network.protocol;

import cn.nukkit.Server;
import cn.nukkit.api.OnlyNetEase;
import lombok.ToString;

@ToString
public class TextPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.TEXT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public static final byte TYPE_RAW = 0;
    public static final byte TYPE_CHAT = 1;
    public static final byte TYPE_TRANSLATION = 2;
    public static final byte TYPE_POPUP = 3;
    public static final byte TYPE_JUKEBOX_POPUP = 4;
    public static final byte TYPE_TIP = 5;
    public static final byte TYPE_SYSTEM = 6;
    public static final byte TYPE_WHISPER = 7;
    public static final byte TYPE_ANNOUNCEMENT = 8;
    public static final byte TYPE_OBJECT = 9;
    public static final byte TYPE_OBJECT_WHISPER = 10;
    /**
     * @since v553
     */
    public static final byte TYPE_OBJECT_ANNOUNCEMENT = 11;

    public byte type;
    public String source = "";
    public String message = " ";
    public String[] parameters = new String[0];
    public boolean isLocalized = false;
    public String xboxUserId = "";
    public String platformChatId = "";
    /**
     * @since v685
     */
    public String filteredMessage = "";

    @OnlyNetEase
    public String unknownNE = "";

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_26_0) {
            // v924 format
            this.isLocalized = this.getBoolean();

            switch (this.getByte()) {
                case 0: // MessageOnly
                    this.type = (byte) getByte();
                    this.message = this.getString();
                    break;
                case 1: // AuthorAndMessage
                    this.type = (byte) getByte();
                    this.source = this.getString();
                    this.message = this.getString();
                    break;
                case 2: // MessageAndParams
                    this.type = (byte) getByte();
                    this.message = this.getString();
                    int paramCount = (int) this.getUnsignedVarInt();
                    this.parameters = new String[Math.min(paramCount, 128)];
                    for (int i = 0; i < this.parameters.length; i++) {
                        this.parameters[i] = this.getString();
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Not oneOf<MessageOnly, AuthorAndMessage, MessageAndParams>");
            }

            this.xboxUserId = this.getString();
            this.platformChatId = this.getString();
            if (this.getBoolean()) {
                this.filteredMessage = this.getString();
            }
        } else if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            // v898 format
            this.isLocalized = this.getBoolean();

            switch (this.getByte()) {
                case 0: // MessageOnly
                    for (int i = 0; i < 6; i++) {
                        this.getString();
                    }
                    this.type = (byte) getByte();
                    this.message = this.getString();
                    break;
                case 1: // AuthorAndMessage
                    for (int i = 0; i < 3; i++) {
                        this.getString();
                    }
                    this.type = (byte) getByte();
                    this.source = this.getString();
                    this.message = this.getString();
                    break;
                case 2: // MessageAndParams
                    for (int i = 0; i < 3; i++) {
                        this.getString();
                    }
                    this.type = (byte) getByte();
                    this.message = this.getString();
                    int paramCount = (int) this.getUnsignedVarInt();
                    if (paramCount > 4) {
                        throw new IllegalArgumentException("Parameter List maxItems is 4");
                    }
                    this.parameters = new String[paramCount];
                    for (int i = 0; i < this.parameters.length; i++) {
                        this.parameters[i] = this.getString();
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Not oneOf<MessageOnly, AuthorAndMessage, MessageAndParams>");
            }

            this.xboxUserId = this.getString();
            this.platformChatId = this.getString();
            if (this.getBoolean()) {
                this.filteredMessage = this.getString();
            }
        } else {
            // Legacy format
            this.type = (byte) getByte();

            if (this.protocol >= ProtocolInfo.v1_2_0) {
                this.isLocalized = this.getBoolean() || type == TYPE_TRANSLATION;
            }

            switch (type) {
                case TYPE_CHAT:
                case TYPE_WHISPER:
                case TYPE_ANNOUNCEMENT:
                    this.source = this.getString();
                    if (protocol > 201 && protocol <= 282) {
                        this.getString();
                        this.getVarInt();
                    }
                case TYPE_RAW:
                case TYPE_TIP:
                case TYPE_SYSTEM:
                case TYPE_OBJECT:
                case TYPE_OBJECT_WHISPER:
                case TYPE_OBJECT_ANNOUNCEMENT:
                    this.message = this.getString();
                    break;

                case TYPE_TRANSLATION:
                case TYPE_POPUP:
                case TYPE_JUKEBOX_POPUP:
                    this.message = this.getString();
                    int count = (int) this.getUnsignedVarInt();
                    this.parameters = new String[Math.min(count, 128)];
                    for (int i = 0; i < this.parameters.length; i++) {
                        this.parameters[i] = this.getString();
                    }
            }

            if (protocol >= 223) {
                this.xboxUserId = this.getString();
                this.platformChatId = this.getString();
                if (protocol >= ProtocolInfo.v1_21_0) {
                    this.filteredMessage = this.getString();
                }
            }
        }

        if (this.gameVersion.isNetEase() && this.protocol >= ProtocolInfo.v1_16_100_51
                && !Server.getInstance().useWaterdog) { // 临时兼容WDPE
            if (this.type == TYPE_CHAT || this.type == TYPE_POPUP) {
                this.unknownNE = this.getString();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();

        if (this.protocol >= ProtocolInfo.v1_26_0) {
            // v924 format
            this.putBoolean(this.isLocalized || type == TYPE_TRANSLATION);

            switch (this.type) {
                case TYPE_RAW:
                case TYPE_TIP:
                case TYPE_SYSTEM:
                case TYPE_OBJECT:
                case TYPE_OBJECT_WHISPER:
                case TYPE_OBJECT_ANNOUNCEMENT:
                    this.putByte((byte) 0); // MessageOnly
                    this.putByte(this.type);
                    this.putString(this.message.isEmpty() ? " " : this.message);
                    break;

                case TYPE_CHAT:
                case TYPE_WHISPER:
                case TYPE_ANNOUNCEMENT:
                    this.putByte((byte) 1); // AuthorAndMessage
                    this.putByte(this.type);
                    this.putString(this.source);
                    this.putString(this.message.isEmpty() ? " " : this.message);
                    break;

                case TYPE_TRANSLATION:
                case TYPE_POPUP:
                case TYPE_JUKEBOX_POPUP:
                    this.putByte((byte) 2); // MessageAndParams
                    this.putByte(this.type);
                    this.putString(this.message.isEmpty() ? " " : this.message);
                    this.putUnsignedVarInt(this.parameters.length);
                    for (String parameter : this.parameters) {
                        this.putString(parameter);
                    }
                    break;
            }

            this.putString(this.xboxUserId);
            this.putString(this.platformChatId);
            this.putBoolean(!this.filteredMessage.isEmpty());
            if (!this.filteredMessage.isEmpty()) {
                this.putString(this.filteredMessage);
            }
        } else if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            // v898 format
            this.putBoolean(this.isLocalized || type == TYPE_TRANSLATION);

            switch (this.type) {
                case TYPE_RAW:
                case TYPE_TIP:
                case TYPE_SYSTEM:
                case TYPE_OBJECT:
                case TYPE_OBJECT_WHISPER:
                case TYPE_OBJECT_ANNOUNCEMENT:
                    this.putByte((byte) 0); // MessageOnly
                    this.putString("raw");
                    this.putString("tip");
                    this.putString("systemMessage");
                    this.putString("textObjectWhisper");
                    this.putString("textObjectAnnouncement");
                    this.putString("textObject");
                    this.putByte(this.type);
                    if (this.message.isEmpty()) {
                        this.message = " ";
                    }
                    this.putString(this.message);
                    break;

                case TYPE_CHAT:
                case TYPE_WHISPER:
                case TYPE_ANNOUNCEMENT:
                    this.putByte((byte) 1); // AuthorAndMessage
                    this.putString("chat");
                    this.putString("whisper");
                    this.putString("announcement");
                    this.putByte(this.type);
                    this.putString(this.source);
                    this.putString(this.message);
                    break;

                case TYPE_TRANSLATION:
                case TYPE_POPUP:
                case TYPE_JUKEBOX_POPUP:
                    this.putByte((byte) 2); // MessageAndParams
                    this.putString("translate");
                    this.putString("popup");
                    this.putString("jukeboxPopup");
                    this.putByte(this.type);
                    this.putString(this.message);
                    this.putUnsignedVarInt(this.parameters.length);
                    for (String parameter : this.parameters) {
                        this.putString(parameter);
                    }
            }

            this.putString(this.xboxUserId);
            this.putString(this.platformChatId);
            this.putBoolean(!this.filteredMessage.isEmpty());
            if (!this.filteredMessage.isEmpty()) {
                this.putString(this.filteredMessage);
            }
        } else {
            // Legacy format
            this.putByte(this.type);

            if (this.protocol >= ProtocolInfo.v1_2_0) {
                this.putBoolean(this.isLocalized || type == TYPE_TRANSLATION);
            }

            switch (this.type) {
                case TYPE_CHAT:
                case TYPE_WHISPER:
                case TYPE_ANNOUNCEMENT:
                    this.putString(this.source);
                    if (protocol > 201 && protocol <= 282) {
                        this.putString("");
                        this.putVarInt(0);
                    }
                case TYPE_RAW:
                case TYPE_TIP:
                case TYPE_SYSTEM:
                case TYPE_OBJECT:
                case TYPE_OBJECT_WHISPER:
                case TYPE_OBJECT_ANNOUNCEMENT:
                    this.putString(this.message);
                    break;

                case TYPE_TRANSLATION:
                case TYPE_POPUP:
                case TYPE_JUKEBOX_POPUP:
                    this.putString(this.message);
                    this.putUnsignedVarInt(this.parameters.length);
                    for (String parameter : this.parameters) {
                        this.putString(parameter);
                    }
            }

            if (this.protocol >= 223) {
                this.putString(this.xboxUserId);
                this.putString(this.platformChatId);

                if (protocol >= ProtocolInfo.v1_21_0) {
                    this.putString(this.filteredMessage);
                }
            }
        }

        if (this.gameVersion.isNetEase() && this.protocol >= ProtocolInfo.v1_16_100_51) {
            if (this.type == TYPE_CHAT || this.type == TYPE_POPUP) {
                this.putString(this.unknownNE);
            }
        }
    }
}
