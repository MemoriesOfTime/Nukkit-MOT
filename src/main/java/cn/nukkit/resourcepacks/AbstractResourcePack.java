package cn.nukkit.resourcepacks;

import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

public abstract class AbstractResourcePack implements ResourcePack {

    protected JsonObject manifest;
    private UUID id = null;

    private int protocol = 0;
    private SupportType supportType = SupportType.UNIVERSAL;
    private String cdnUrl = "";
    protected String encryptionKey = "";

    /**
     * 除了字段存在性，还校验后续解析无条件读取的字段类型（getPackId、getPackVersion、
     * ZippedBehaviourPack 的 modules 扫描）：构造成功之后的抛异常路径没有清理入口，
     * 会泄漏快照 channel，所以凡是能通过本校验的 manifest 后续解析绝不能抛异常。
     * <p>
     * Beyond presence, validates the types of fields that later parsing reads
     * unconditionally: there is no cleanup path once construction has succeeded
     * (an escaping exception would leak the snapshot channel), so any manifest
     * accepted here must parse without throwing afterwards.
     */
    protected boolean verifyManifest() {
        if (!this.manifest.has("format_version") || !this.manifest.has("header") || !this.manifest.has("modules")) {
            return false;
        }
        if (!this.manifest.get("header").isJsonObject() || !this.manifest.get("modules").isJsonArray()) {
            return false;
        }
        JsonObject header = this.manifest.getAsJsonObject("header");
        JsonElement uuid = header.get("uuid");
        if (uuid == null || !uuid.isJsonPrimitive()) {
            return false;
        }
        try {
            UUID.fromString(uuid.getAsString());
        } catch (IllegalArgumentException e) {
            return false;
        }
        JsonElement version = header.get("version");
        if (version == null || !version.isJsonArray() || version.getAsJsonArray().size() != 3) {
            return false;
        }
        for (JsonElement part : version.getAsJsonArray()) {
            if (!part.isJsonPrimitive()) {
                return false;
            }
        }
        return supportType == SupportType.NETEASE || (header.has("description") && header.has("name"));
    }

    @Override
    public String getPackName() {
        return this.manifest.getAsJsonObject("header")
                .get("name").getAsString();
    }

    @Override
    public UUID getPackId() {
        if (id == null) {
            id = UUID.fromString(this.manifest.getAsJsonObject("header").get("uuid").getAsString());
        }
        return id;
    }

    @Override
    public int getPackProtocol() {
        if (protocol == 0) {
            var header = this.manifest.getAsJsonObject("header");
            protocol = header.has("min_engine_version") ?
                    ResourcePackManager.ProtocolConverter.convertToProtocol(header.get("min_engine_version").getAsJsonArray())
                    : ProtocolInfo.SUPPORTED_PROTOCOLS.get(0);
        }
        return protocol;
    }

    @Override
    public String getPackVersion() {
        JsonArray version = this.manifest.getAsJsonObject("header")
                .get("version").getAsJsonArray();

        return String.join(".", version.get(0).getAsString(),
                version.get(1).getAsString(),
                version.get(2).getAsString());
    }

    @Override
    public void setSupportType(SupportType type) {
        this.supportType = type;
    }

    @Override
    public SupportType getSupportType() {
        return this.supportType;
    }

    @Override
    public String getCDNUrl() {
        return this.cdnUrl;
    }

    @Override
    public void setCDNUrl(String cdnUrl) {
        this.cdnUrl = cdnUrl != null ? cdnUrl : "";
    }

    @Override
    public String getEncryptionKey() {
        return this.encryptionKey;
    }

    @Override
    public void setEncryptionKey(String key) {
        this.encryptionKey = key != null ? key : "";
    }

    @Override
    @Deprecated
    public void setNetEase(boolean isNetEase) {
        this.supportType = isNetEase ? SupportType.NETEASE : SupportType.MICROSOFT;
    }

    @Override
    @Deprecated
    public boolean isNetEase() {
        return this.supportType == SupportType.NETEASE;
    }

    @Override
    public int hashCode() {
        return this.getPackId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ResourcePack anotherPack)) return false;
        return this.getPackId().equals(anotherPack.getPackId());
    }
}
