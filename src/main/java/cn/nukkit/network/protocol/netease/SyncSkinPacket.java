package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@OnlyNetEase
@ToString
public class SyncSkinPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PACKET_SYNC_SKIN;

    public List<SyncSkinEntry> entries = new ObjectArrayList<>();

    /**
     * 尾部 SerializedSkin，始终存在（即使 entries 为空）。
     * <p>Trailing SerializedSkin, always present even when entries is empty.
     */
    public Skin skin = new Skin();

    public void addEntry(SyncSkinEntry entry) {
        this.entries.add(entry);
    }

    public List<SyncSkinEntry> getEntries() {
        return this.entries;
    }

    public void setEntries(List<SyncSkinEntry> entries) {
        this.entries = entries != null ? entries : new ObjectArrayList<>();
    }

    public Skin getSkin() {
        return this.skin;
    }

    public void setSkin(Skin skin) {
        this.skin = skin != null ? skin : new Skin();
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        int count = (int) this.getUnsignedVarInt();
        this.entries = new ObjectArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.entries.add(new SyncSkinEntry());
        }
        // GROUP 1: flag + uuid + string1
        for (int i = 0; i < count; i++) {
            SyncSkinEntry entry = this.entries.get(i);
            entry.flag = this.getBoolean();
            entry.uuid = this.getUUID();
            entry.string1 = this.getString();
        }
        // GROUP 2: string2
        for (int i = 0; i < count; i++) {
            this.entries.get(i).string2 = this.getString();
        }
        // GROUP 3: string3 (at least one string is item_id)
        for (int i = 0; i < count; i++) {
            this.entries.get(i).string3 = this.getString();
        }
        // GROUP 4: string4
        for (int i = 0; i < count; i++) {
            this.entries.get(i).string4 = this.getString();
        }
        // 尾部 SerializedSkin
        this.skin = this.getSkin(this.protocol);
    }

    @Override
    public void encode() {
        this.reset();
        int count = this.entries.size();
        this.putUnsignedVarInt(count);
        // GROUP 1: flag + uuid + string1
        for (SyncSkinEntry entry : this.entries) {
            this.putBoolean(entry.flag);
            this.putUUID(entry.uuid);
            this.putString(entry.string1 != null ? entry.string1 : "");
        }
        // GROUP 2: string2
        for (SyncSkinEntry entry : this.entries) {
            this.putString(entry.string2 != null ? entry.string2 : "");
        }
        // GROUP 3: string3
        for (SyncSkinEntry entry : this.entries) {
            this.putString(entry.string3 != null ? entry.string3 : "");
        }
        // GROUP 4: string4
        for (SyncSkinEntry entry : this.entries) {
            this.putString(entry.string4 != null ? entry.string4 : "");
        }
        // 尾部 SerializedSkin
        this.putSkin(this.gameVersion, this.skin);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class SyncSkinEntry {
        public boolean flag;
        public UUID uuid;
        public String string1 = "";
        public String string2 = "";
        public String string3 = "";
        public String string4 = "";
    }
}
