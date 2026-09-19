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

/**
 * 网易 V860 皮肤同步包。条目按轮交错序列化（非逐条），轮 1 的第三字段是皮肤纹理
 * 字节数组而非字符串；布局写错会让客户端解析失败、皮肤回退史蒂夫。
 * <p>
 * NetEase V860 skin sync packet. Entries are serialized in interleaved rounds (not
 * per-entry), and round 1's third field is a skin-texture byte array, not a string;
 * a wrong layout makes the client fail to parse and fall back to Steve.
 */
@OnlyNetEase
@ToString
public class SyncSkinPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PACKET_SYNC_SKIN;

    public List<SyncSkinEntry> entries = new ObjectArrayList<>();

    /**
     * 尾部 SerializedSkin，始终存在（即使 entries 为空）。
     * <p>Trailing SerializedSkin, always present even when entries are empty.
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
        // 轮 1: flag + uuid + skinBytes（字节数组，不是字符串）
        for (int i = 0; i < count; i++) {
            SyncSkinEntry entry = this.entries.get(i);
            entry.flag = this.getBoolean();
            entry.uuid = this.getUUID();
            entry.skinBytes = this.getByteArray();
        }
        // 轮 2: udid
        for (int i = 0; i < count; i++) {
            this.entries.get(i).string1 = this.getString();
        }
        // 轮 3: extraData
        for (int i = 0; i < count; i++) {
            this.entries.get(i).string2 = this.getString();
        }
        // 轮 4: itemId（商城商品引用）
        for (int i = 0; i < count; i++) {
            this.entries.get(i).string3 = this.getString();
        }
        // 尾部 SerializedSkin
        this.skin = this.getSkin(this.protocol);
    }

    @Override
    public void encode() {
        this.reset();
        int count = this.entries.size();
        this.putUnsignedVarInt(count);
        // 轮 1: flag + uuid + skinBytes（字节数组，不是字符串）
        for (SyncSkinEntry entry : this.entries) {
            this.putBoolean(entry.flag);
            this.putUUID(entry.uuid);
            this.putByteArray(entry.skinBytes != null ? entry.skinBytes : new byte[0]);
        }
        // 轮 2: udid
        for (SyncSkinEntry entry : this.entries) {
            this.putString(entry.string1 != null ? entry.string1 : "");
        }
        // 轮 3: extraData
        for (SyncSkinEntry entry : this.entries) {
            this.putString(entry.string2 != null ? entry.string2 : "");
        }
        // 轮 4: itemId（商城商品引用）
        for (SyncSkinEntry entry : this.entries) {
            this.putString(entry.string3 != null ? entry.string3 : "");
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
        /** 轮 1 的第三字段：皮肤纹理字节。 */
        public byte[] skinBytes = new byte[0];
        /** 轮 2 udid。 */
        public String string1 = "";
        /** 轮 3 extraData。 */
        public String string2 = "";
        /** 轮 4 itemId（商城商品引用）。 */
        public String string3 = "";
        /** 旧格式遗留槽位，已不再上线。 */
        public String string4 = "";
    }
}
