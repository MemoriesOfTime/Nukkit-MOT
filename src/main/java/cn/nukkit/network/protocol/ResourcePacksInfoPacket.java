package cn.nukkit.network.protocol;

import cn.nukkit.resourcepacks.ResourcePack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;
import lombok.Value;

import java.util.List;

@ToString
public class ResourcePacksInfoPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RESOURCE_PACKS_INFO_PACKET;

    public boolean mustAccept;
    public boolean scripting;
    public boolean forceServerPacks;
    public ResourcePack[] behaviourPackEntries = ResourcePack.EMPTY_ARRAY;
    public ResourcePack[] resourcePackEntries = ResourcePack.EMPTY_ARRAY;
    /**
     * @since v618
     */
    private List<CDNEntry> CDNEntries = new ObjectArrayList<>();

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putBoolean(this.mustAccept);
        if (protocol >= ProtocolInfo.v1_9_0) {
            this.putBoolean(this.scripting);
            if (protocol >= ProtocolInfo.v1_17_10) {
                this.putBoolean(this.forceServerPacks);
            }
        }

        this.encodeBehaviourPacks(this.behaviourPackEntries);
        this.encodeResourcePacks(this.resourcePackEntries);
        putUnsignedVarInt(getCDNEntries().size());

        if (protocol >= ProtocolInfo.v1_20_30_24) {
            for (var cdn : getCDNEntries()) {
                putString(cdn.packId);
                putString(cdn.remoteUrl);
            }
        }
    }

    private void encodeBehaviourPacks(ResourcePack[] packs) {
        this.putLShort(packs.length);
        for (ResourcePack entry : packs) {
            this.putString(entry.getPackId().toString());
            this.putString(entry.getPackVersion());
            this.putLLong(entry.getPackSize());
            this.putString(entry.getEncryptionKey()); // encryption key
            this.putString(""); // sub-pack name
            this.putString(!"".equals(entry.getEncryptionKey()) ? entry.getPackId().toString() : ""); // content identity
            this.putBoolean(false); // scripting
        }
    }

    private void encodeResourcePacks(ResourcePack[] packs) {
        this.putLShort(packs.length);
        for (ResourcePack entry : packs) {
            this.putString(entry.getPackId().toString());
            this.putString(entry.getPackVersion());
            this.putLLong(entry.getPackSize());
            this.putString(entry.getEncryptionKey()); // encryption key
            this.putString(""); // sub-pack name
            if (protocol > ProtocolInfo.v1_5_0) {
                this.putString(!"".equals(entry.getEncryptionKey()) ? entry.getPackId().toString() : ""); // content identity
                if (protocol >= ProtocolInfo.v1_9_0) {
                    this.putBoolean(false); // scripting
                    if (protocol >= ProtocolInfo.v1_16_200) {
                        this.putBoolean(false); // raytracing capable
                    }
                }
            }
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public void setCDNEntries(List<CDNEntry> CDNEntries) {
        this.CDNEntries = CDNEntries;
    }
    public List<CDNEntry> getCDNEntries() {
        return CDNEntries;
    }
    @Value
    public static class CDNEntry {
        private final String packId;
        private final String remoteUrl;
    }
}
