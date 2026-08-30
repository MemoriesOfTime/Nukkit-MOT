package cn.nukkit.network.protocol;

import cn.nukkit.utils.TextFormat;
import lombok.ToString;

/**
 * Created by CreeperFace on 30. 10. 2016.
 */
@ToString
public class BossEventPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BOSS_EVENT_PACKET;

    /**
     * protocol-docs BossEventPacket: Name / FilteredName max_length 256（JSON Schema 字符/code point 上限）。
     * <p>
     * protocol-docs Name / FilteredName max_length 256 (JSON-Schema character / code-point ceiling).
     */
    private static final int MAX_TITLE_CHARS = 256;

    /** Shows the bossbar to the player. */
    public static final int TYPE_SHOW = 0;
    /** Registers a player to a boss fight. */
    public static final int TYPE_REGISTER_PLAYER = 1;
    /** Not sure on this. */
    public static final int TYPE_UPDATE = 1;
    /** Removes the bossbar from the client. */
    public static final int TYPE_HIDE = 2;
    /** Unregisters a player from a boss fight. */
    public static final int TYPE_UNREGISTER_PLAYER = 3;
    /** Sets the bar percentage. */
    public static final int TYPE_HEALTH_PERCENT = 4;
    /** Sets title of the bar. */
    public static final int TYPE_TITLE = 5;
    /** Not sure on this. Includes color and overlay fields, plus an unknown short. */
    public static final int TYPE_UPDATE_PROPERTIES  = 6;
    /** Sets color and overlay of the bar. */
    public static final int TYPE_TEXTURE = 7;
    public static final int TYPE_QUERY = 8;

    public long bossEid;
    public int type;
    public long playerEid;
    public float healthPercent;
    public String title = "";
    /**
     * @since v776 1.21.60
     */
    public String filteredTitle = "";
    public short darkenScreen;
    public int color;
    public int overlay;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.bossEid = this.getEntityUniqueId();
        if (this.protocol >= ProtocolInfo.v1_26_30) {
            if (this.protocol < ProtocolInfo.v1_26_50) {
                // v2192 起移除 playerEid / playerEid removed in v2192
                this.playerEid = this.getEntityUniqueId();
            }
            this.type = this.getByte();
            this.title = this.getString(MAX_TITLE_CHARS);
            this.filteredTitle = this.getString(MAX_TITLE_CHARS);
            this.healthPercent = this.getLFloat();
            this.color = this.getByte() & 0xff;
            this.overlay = this.getByte() & 0xff;
        } else {
            this.type = (int) this.getUnsignedVarInt();
            if (protocol >= ProtocolInfo.v1_2_0) {
                switch (this.type) {
                    case TYPE_REGISTER_PLAYER:
                    case TYPE_UNREGISTER_PLAYER:
                    case TYPE_QUERY:
                        this.playerEid = this.getEntityUniqueId();
                        break;
                    case TYPE_SHOW:
                        this.title = this.getString(MAX_TITLE_CHARS);
                        if (this.protocol >= ProtocolInfo.v1_21_60) {
                            this.filteredTitle = this.getString(MAX_TITLE_CHARS);
                        }
                        this.healthPercent = this.getLFloat();
                    case TYPE_UPDATE_PROPERTIES:
                        this.darkenScreen = (short) this.getShort();
                    case TYPE_TEXTURE:
                        this.color = (int) this.getUnsignedVarInt();
                        this.overlay = (int) this.getUnsignedVarInt();
                        break;
                    case TYPE_HEALTH_PERCENT:
                        this.healthPercent = this.getLFloat();
                        break;
                    case TYPE_TITLE:
                        this.title = this.getString(MAX_TITLE_CHARS);
                        if (this.protocol >= ProtocolInfo.v1_21_60) {
                            this.filteredTitle = this.getString(MAX_TITLE_CHARS);
                        }
                        break;
                }
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.title = TextFormat.clamp(this.title, MAX_TITLE_CHARS);
        this.filteredTitle = TextFormat.clamp(this.filteredTitle, MAX_TITLE_CHARS);
        this.putEntityUniqueId(this.bossEid);
        if (this.protocol >= ProtocolInfo.v1_26_30) {
            if (this.protocol < ProtocolInfo.v1_26_50) {
                // v2192 起移除 playerEid / playerEid removed in v2192
                this.putEntityUniqueId(this.playerEid);
            }
            this.putByte((byte) this.type);
            this.putString(this.title);
            this.putString(this.filteredTitle);
            this.putLFloat(this.healthPercent);
            this.putByte((byte) this.color);
            this.putByte((byte) this.overlay);
        } else {
            this.putUnsignedVarInt(this.type);
            if (protocol >= ProtocolInfo.v1_2_0) {
                switch (this.type) {
                    case TYPE_REGISTER_PLAYER:
                    case TYPE_UNREGISTER_PLAYER:
                    case TYPE_QUERY:
                        this.putEntityUniqueId(this.playerEid);
                        break;
                    case TYPE_SHOW:
                        this.putString(this.title);
                        if (this.protocol >= ProtocolInfo.v1_21_60) {
                            this.putString(this.filteredTitle);
                        }
                        this.putLFloat(this.healthPercent);
                    case TYPE_UPDATE_PROPERTIES:
                        this.putShort(this.darkenScreen);
                    case TYPE_TEXTURE:
                        this.putUnsignedVarInt(this.color);
                        this.putUnsignedVarInt(this.overlay);
                        break;
                    case TYPE_HEALTH_PERCENT:
                        this.putLFloat(this.healthPercent);
                        break;
                    case TYPE_TITLE:
                        this.putString(this.title);
                        if (this.protocol >= ProtocolInfo.v1_21_60) {
                            this.putString(this.filteredTitle);
                        }
                        break;
                }
            }
        }
    }
}
