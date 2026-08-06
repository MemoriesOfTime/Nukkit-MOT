package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

import java.util.UUID;

/**
 * @author Nukkit Project Team
 */
@ToString
public class PlayerListPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.PLAYER_LIST_PACKET;

    public static final byte TYPE_ADD = 0;
    public static final byte TYPE_REMOVE = 1;

    public byte type;
    public Entry[] entries = new Entry[0];

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            this.putByte(this.type);
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putUnsignedVarInt(this.entries.length);
                for (Entry entry : this.entries) {
                    if (type == TYPE_ADD) {
                        this.putUUID(entry.uuid);
                        this.putVarLong(entry.entityId);
                        this.putString(entry.name);
                        this.putSkin(protocol, entry.skin);
                    } else {
                        this.putSkin(protocol, entry.skin);
                    }
                }
                return;
            }
            this.putInt(this.entries.length);
            for (Entry entry : this.entries) {
                if (type == TYPE_ADD) {
                    this.putUUID(entry.uuid);
                    this.putLong(entry.entityId);
                    this.putString_old(entry.name);
                    if(this.protocol <= ProtocolInfo.v_0_12_1){
                        this.putBoolean( Skin.MODEL_ALEX.equals(entry.skin.getSkinId()) );// is slim?
                        this.putShort( entry.skin.getSkinData().data.length );
                        this.put( entry.skin.getSkinData().data );
                    }else{
                        this.putSkin(protocol, entry.skin);
                    }
                } else {
                    this.putUUID(entry.uuid);
                }
            }
            return;
        }

        this.reset();
        this.putByte(this.type);
        this.putUnsignedVarInt(this.entries.length);
        if(this.protocol >= ProtocolInfo.v1_2_0){
            switch (type) {
                case TYPE_ADD:
                    for (Entry entry : this.entries) {
                        if (protocol >= 223) {
                            this.putUUID(entry.uuid);
                        }
                        this.putVarLong(entry.entityId);
                        this.putString(entry.name);
                        if (protocol >= 223 && protocol <= 282) {
                            this.putString("");
                            this.putVarInt(0);
                        }
                        if (protocol < 388) {
                            this.putSkin(protocol, entry.skin);
                            if (protocol < 223) {
                                this.putByteArray(new byte[0]);
                            }
                        }
                        this.putString(entry.xboxUserId);
                        if (protocol >= 223) {
                            this.putString(entry.platformChatId);
                            if (protocol >= 388) {
                                this.putLInt(entry.buildPlatform);
                                this.putSkin(protocol, entry.skin);
                                this.putBoolean(entry.isTeacher);
                                this.putBoolean(entry.isHost);
                                if (protocol >= ProtocolInfo.v1_20_60) {
                                    this.putBoolean(entry.isSubClient);
                                }
                            }
                        }
                    }
                    if (protocol >= ProtocolInfo.v1_14_60) {
                        for (Entry entry : this.entries) { // WTF Mojang
                            this.putBoolean(entry.skin != null && entry.skin.isTrusted());
                        }
                    }
                    break;
                case TYPE_REMOVE:
                    for (Entry entry : this.entries) {
                        if (protocol >= 223) {
                            this.putUUID(entry.uuid);
                        }
                    }
            }
        }else{
            for (Entry entry : this.entries) {
                if (type == TYPE_ADD) {
                    this.putUUID(entry.uuid);
                    this.putVarLong(entry.entityId);
                    this.putString(entry.name);
                    this.putSkin(protocol, entry.skin);
                } else {
                    this.putUUID(entry.uuid);
                }
            }
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).getOrDefault(this.getClass(),(byte) 0xc3);
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.PLAYER_LIST_PACKET;
        }
        return NETWORK_ID;
    }

    @ToString
    public static class Entry {

        public final UUID uuid;
        public long entityId = 0;
        public String name = "";
        public Skin skin;
        public String xboxUserId = "";
        public String platformChatId = "";
        public int buildPlatform = -1;
        public boolean isTeacher;
        public boolean isHost;
        public boolean isSubClient;

        public Entry(UUID uuid) {
            this.uuid = uuid;
        }

        public Entry(UUID uuid, long entityId, String name, Skin skin) {
            this(uuid, entityId, name, skin, "");
        }

        public Entry(UUID uuid, long entityId, String name, Skin skin, String xboxUserId) {
            this.uuid = uuid;
            this.entityId = entityId;
            this.name = name;
            this.skin = skin;
            this.xboxUserId = xboxUserId == null ? "" : xboxUserId;
        }
    }
}
