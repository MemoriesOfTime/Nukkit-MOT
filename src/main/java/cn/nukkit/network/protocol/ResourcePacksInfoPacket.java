package cn.nukkit.network.protocol;

import cn.nukkit.resourcepacks.ResourcePack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ToString
public class ResourcePacksInfoPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RESOURCE_PACKS_INFO_PACKET;

    public boolean mustAccept;
    /**
     * @since v662 1.20.70
     */
    public boolean hasAddonPacks;
    public boolean scripting;
    public boolean forceServerPacks;
    public ResourcePack[] behaviourPackEntries = ResourcePack.EMPTY_ARRAY;
    public ResourcePack[] resourcePackEntries = ResourcePack.EMPTY_ARRAY;
    /**
     * @since v618
     * @deprecated since v748 1.21.40
     */
    @Getter
    @Setter
    private List<CDNEntry> CDNEntries = new ObjectArrayList<>();
    /**
     * @since v766
     */
    public UUID worldTemplateId = new UUID(0, 0);
    /**
     * @since v766
     */
    public String worldTemplateVersion = "";

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putBoolean(this.mustAccept);
        if (this.protocol >= ProtocolInfo.v1_20_70) {
            this.putBoolean(this.hasAddonPacks);
        }
        if (this.protocol >= ProtocolInfo.v1_9_0) {
            this.putBoolean(this.scripting);
            if (this.protocol >= ProtocolInfo.v1_17_10 && this.protocol < ProtocolInfo.v1_21_30) {
                this.putBoolean(this.forceServerPacks);
            }
        }
        if (this.protocol >= ProtocolInfo.v1_21_50) {
            this.putUUID(this.worldTemplateId);
            this.putString(this.worldTemplateVersion);
        }

        if (this.protocol < ProtocolInfo.v1_21_30) {
            this.encodeBehaviourPacks(this.behaviourPackEntries);
        }
        this.encodeResourcePacks(this.resourcePackEntries);

        if (this.protocol >= ProtocolInfo.v1_20_30_24 && this.protocol < ProtocolInfo.v1_21_40) {
            List<CDNEntry> cacheCDNEntries = new ObjectArrayList<>(this.CDNEntries);
            for (ResourcePack entry : this.resourcePackEntries) {
                CDNEntry cdnEntry = new CDNEntry(entry.getPackId().toString(), entry.getCDNUrl());
                if (!"".equals(entry.getCDNUrl()) && !cacheCDNEntries.contains(cdnEntry)) {
                    cacheCDNEntries.add(cdnEntry);
                }
            }
            this.putArray(cacheCDNEntries, (entry) -> {
                this.putString(entry.getPackId());
                this.putString(entry.getRemoteUrl());
            });
        }
    }

    private void encodeBehaviourPacks(ResourcePack[] packs) {
        this.putLShort(packs.length);
        for (ResourcePack entry : packs) {
            this.putString(entry.getPackId().toString());
            this.putString(entry.getPackVersion());
            this.putLLong(entry.getPackSize());
            this.putString(entry.getEncryptionKey());
            this.putString(entry.getSubPackName());
            this.putString(!"".equals(entry.getEncryptionKey()) ? entry.getPackId().toString() : ""); // content identity
            this.putBoolean(entry.usesScripting());
        }
    }

    private void encodeResourcePacks(ResourcePack[] packs) {
        this.putLShort(packs.length);
        for (ResourcePack entry : packs) {
            if (this.protocol >= ProtocolInfo.v1_21_50) {
                this.putUUID(entry.getPackId());
            } else {
                this.putString(entry.getPackId().toString());
            }
            this.putString(entry.getPackVersion());
            this.putLLong(entry.getPackSize());
            this.putString(entry.getEncryptionKey()); // encryption key
            if (protocol >= ProtocolInfo.v1_2_0) {
                this.putString(""); // sub-pack name
            }
            if (protocol > ProtocolInfo.v1_5_0) {
                this.putString(!"".equals(entry.getEncryptionKey()) ? entry.getPackId().toString() : ""); // content identity
                if (protocol >= ProtocolInfo.v1_9_0) {
                    this.putBoolean(false); // scripting
                    if (protocol >= ProtocolInfo.v1_16_200) {
                        if (protocol >= ProtocolInfo.v1_21_20) {
                            this.putBoolean(entry.isAddonPack());
                        }
                        this.putBoolean(false); // raytracing capable
                        if (protocol >= ProtocolInfo.v1_21_40) {
                            this.putString(entry.getCDNUrl());
                        }
                    }
                }
            }
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Value
    public static class CDNEntry {
        String packId;
        String remoteUrl;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CDNEntry cdnEntry)) return false;
            return Objects.equals(packId, cdnEntry.packId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(packId);
        }
    }
}
