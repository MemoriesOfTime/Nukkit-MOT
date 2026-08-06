package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class TextPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.TEXT_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
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
    public String message = "";
    public String[] parameters = new String[0];
    public boolean isLocalized = false;
    public String xboxUserId = "";
    public String platformChatId = "";
    /**
     * @since v685
     */
    public String filteredMessage = "";

    @Override
    public void decode() {
        if(this.protocol > ProtocolInfo.v_0_10_0) {
            this.type = (byte) getByte();
        }else{
            this.type = 0;
            this.source = this.getString_old();
            this.message = this.getString_old();
            return;
        }
        if(this.protocol < ProtocolInfo.v1_2_0){
            if(this.protocol <= ProtocolInfo.v_0_11_0){
                switch (type) {
                    case TYPE_CHAT:
                        this.source = this.getString_old();
                    case TYPE_RAW:
                    case TYPE_POPUP:
                    case TYPE_TIP:
                        this.message = this.getString_old();
                        break;

                    case TYPE_TRANSLATION:
                        this.message = this.getString_old();
                        int count = this.getByte();
                        parameters = new String[count];
                        for (int i = 0; i < count; i++) {
                            parameters[i] = getString_old();
                        }
                }
                return;
            }else if(this.protocol >= ProtocolInfo.v_0_16_0 && this.protocol < ProtocolInfo.v_1_0_0){
                switch (type) {
                    case TYPE_POPUP:
                    case TYPE_CHAT:
                        this.source = this.getString();
                    case TYPE_RAW:
                    case TYPE_TIP:
                    case TYPE_SYSTEM:
                        this.message = this.getString();
                        break;

                    case TYPE_TRANSLATION:
                        this.message = this.getString();
                        int count = (int) this.getUnsignedVarInt();
                        this.parameters = new String[count];
                        for (int i = 0; i < count; i++) {
                            this.parameters[i] = this.getString();
                        }
                }
                return;
            }else if(this.protocol >= ProtocolInfo.v_1_0_0){
                switch (type) {
                    case TYPE_POPUP:
                    case TYPE_CHAT:
                    case TYPE_WHISPER:
                    case TYPE_ANNOUNCEMENT:
                        this.source = this.getString();
                    case TYPE_RAW:
                    case TYPE_TIP:
                    case TYPE_SYSTEM:
                        this.message = this.getString();
                        break;

                    case TYPE_TRANSLATION:
                        this.message = this.getString();
                        int count = (int) this.getUnsignedVarInt();
                        this.parameters = new String[count];
                        for (int i = 0; i < count; i++) {
                            this.parameters[i] = this.getString();
                        }
                }
                return;
            }
            switch (type) {
                case TYPE_POPUP:
                case TYPE_CHAT:
                    this.source = this.getString_old();
                case TYPE_RAW:
                case TYPE_TIP:
                case TYPE_SYSTEM:
                    this.message = this.getString_old();
                    break;

                case TYPE_TRANSLATION:
                    this.message = this.getString_old();
                    int count = this.getByte();
                    parameters = new String[count];
                    for (int i = 0; i < count; i++) {
                        parameters[i] = getString_old();
                    }
            }

            return;
        }

        if (protocol >= ProtocolInfo.v1_2_0) {
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

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putByte(this.type);
            }
            if(this.protocol <= ProtocolInfo.v_0_11_0){
                if(this.protocol <= ProtocolInfo.v_0_10_0){
                    this.putString_old(this.source);
                    this.putString_old(this.message);
                    return;
                }
                switch (type) {
                    case TYPE_CHAT:
                        this.putString_old(this.source);
                    case TYPE_RAW:
                    case TYPE_POPUP:
                    case TYPE_TIP:
                        this.putString_old(this.message);
                        break;

                    case TYPE_TRANSLATION:
                        this.putString_old(this.message);
                        this.putByte((byte) this.parameters.length);
                        for (String parameter : this.parameters) {
                            this.putString_old(parameter);
                        }
                }
                return;
            }else if(this.protocol >= ProtocolInfo.v_0_16_0){
                switch (this.type) {
                    case TYPE_POPUP:
                    case TYPE_CHAT:
                        this.putString(this.source);
                    case TYPE_RAW:
                    case TYPE_TIP:
                    case TYPE_SYSTEM:
                        this.putString(this.message);
                        break;

                    case TYPE_TRANSLATION:
                        this.putString(this.message);
                        this.putUnsignedVarInt(this.parameters.length);
                        for (String parameter : this.parameters) {
                            this.putString(parameter);
                        }
                }
                return;
            }
            switch (this.type) {
                case TYPE_POPUP:
                case TYPE_CHAT:
                    this.putString_old(this.source);
                case TYPE_RAW:
                case TYPE_TIP:
                case TYPE_SYSTEM:
                    this.putString_old(this.message);
                    break;

                case TYPE_TRANSLATION:
                    this.putString_old(this.message);
                    this.putByte((byte) this.parameters.length);
                    for (String parameter : this.parameters) {
                        this.putString_old(parameter);
                    }
            }

            return;
        }

        this.reset();
        if (this.protocol < ProtocolInfo.v1_2_0 && this.type > 4) {
            this.putByte((byte) (this.type - 1));
        } else {
            this.putByte(this.type);
        }
        if (protocol >= ProtocolInfo.v1_2_0) {
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
        if (protocol >= 223) {
            this.putString(this.xboxUserId);
            this.putString(this.platformChatId);
            if (protocol >= ProtocolInfo.v1_21_0) {
                this.putString(this.filteredMessage);
            }
        }
    }

    private byte convertNewMessageTypesToOldMessageType(int protocolId, byte messageType){
        if(protocolId >= ProtocolInfo.v1_2_0){
            return messageType;
        } else if (protocolId >= ProtocolInfo.v_1_0_0) {
            switch (messageType) {
                case TYPE_TIP:
                    return TYPE_JUKEBOX_POPUP;
                case TYPE_JUKEBOX_POPUP:
                    return TYPE_POPUP;
                case TYPE_SYSTEM:
                    return TYPE_TIP;

            }
        } else if (protocolId >= ProtocolInfo.v_0_16_0) {

        } else if (protocolId >= ProtocolInfo.v_0_12_1) {

        } else if (protocolId >= ProtocolInfo.v_0_11_0) {

        }
        return messageType;
    }
}
