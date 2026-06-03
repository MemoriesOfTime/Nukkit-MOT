package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@OnlyNetEase
@ToString
public class ConfirmSkinPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PACKET_CONFIRM_SKIN;

    public List<SkinEntry> entries = new ObjectArrayList<>();

    /**
     * @deprecated use {@link #entries} or {@link #addEntry(SkinEntry)} so skin bytes and geometry strings
     * are encoded.
     */
    @Deprecated(forRemoval = true)
    public List<UUID> uuids = new ObjectArrayList<>();

    public void addEntry(SkinEntry skinEntry) {
        this.entries.add(skinEntry);
    }

    public List<SkinEntry> getEntries() {
        return this.entries;
    }

    public void setEntries(List<SkinEntry> entries) {
        this.entries = entries != null ? entries : new ObjectArrayList<>();
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        return (byte) NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        List<SkinEntry> entries = (this.entries == null || this.entries.isEmpty()) && !this.uuids.isEmpty()
                ? this.uuids.stream().map(SkinEntry::fromUuid).toList()
                : this.entries != null ? this.entries : List.of();

        this.putArray(entries, (stream, entry) -> {
            stream.putBoolean(entry.valid);
            stream.putUUID(entry.uuid);
            stream.putByteArray(entry.skinBytes != null ? entry.skinBytes : new byte[0]);
        });

        for (SkinEntry entry : entries) {
            this.putString(entry.uidStr != null ? entry.uidStr : "");
        }

        for (SkinEntry entry : entries) {
            this.putString(entry.geoStr != null ? entry.geoStr : "");
        }
    }

    @ToString
    public static class SkinEntry {
        public boolean valid;
        public UUID uuid;
        public byte[] skinBytes = new byte[0];
        public String uidStr;
        public String geoStr;

        public SkinEntry() {
        }

        public SkinEntry(boolean valid, UUID uuid, byte[] skinBytes, String uidStr, String geoStr) {
            this.valid = valid;
            this.uuid = uuid;
            this.skinBytes = skinBytes != null ? skinBytes : new byte[0];
            this.uidStr = uidStr;
            this.geoStr = geoStr;
        }

        private static SkinEntry fromUuid(UUID uuid) {
            return new SkinEntry(true, uuid, new byte[0], "", "");
        }

        public boolean isValid() {
            return this.valid;
        }

        public UUID getUuid() {
            return this.uuid;
        }

        public byte[] getSkinBytes() {
            return this.skinBytes;
        }

        public String getUidStr() {
            return this.uidStr;
        }

        public String getGeoStr() {
            return this.geoStr;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public void setUuid(UUID uuid) {
            this.uuid = uuid;
        }

        public void setSkinBytes(byte[] skinBytes) {
            this.skinBytes = skinBytes != null ? skinBytes : new byte[0];
        }

        public void setUidStr(String uidStr) {
            this.uidStr = uidStr;
        }

        public void setGeoStr(String geoStr) {
            this.geoStr = geoStr;
        }
    }
}
