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
 * FAPIXEL fap2 core patch：修正线格式。
 * <p>
 * 旧实现（第 1 轮 bool+uuid+string，之后 4 轮 string）与网易客户端实际布局不符。
 * 依据对网易 V860 客户端实际线格式的比对分析：
 * <pre>
 *   count(varuint)
 *   轮 1: valid(bool) + uuid + skinBytes(byteArray)     ← 第三字段是字节数组
 *   轮 2: udid(string)
 *   轮 3: extraData(string)
 *   轮 4: itemId(string)                                 ← 商城商品引用
 *   尾部 SerializedSkin
 * </pre>
 * 旧 string1..string4 映射：string1=udid、string2=extraData、string3=itemId、
 * string4 保留字段不再上线。错误的布局会让客户端解析失败，皮肤回退史蒂夫。
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
        /** 轮 1 的第三字段：皮肤纹理字节（fap2 前被错误地序列化为字符串）。 */
        public byte[] skinBytes = new byte[0];
        /** 轮 2 udid。 */
        public String string1 = "";
        /** 轮 3 extraData。 */
        public String string2 = "";
        /** 轮 4 itemId（商城商品引用）。 */
        public String string3 = "";
        /** 旧格式遗留的第 4 个字符串槽位，fap2 起不再上线。 */
        public String string4 = "";
    }
}
