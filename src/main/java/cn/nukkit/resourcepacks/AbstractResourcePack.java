package cn.nukkit.resourcepacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.UUID;

import cn.nukkit.network.protocol.ProtocolInfo;

public abstract class AbstractResourcePack implements ResourcePack {

    protected JsonObject manifest;
    private UUID id = null;
    private int protocol = 0;

    protected boolean verifyManifest() {
        if (this.manifest.has("format_version") && this.manifest.has("header") && this.manifest.has("modules")) {
            JsonObject header = this.manifest.getAsJsonObject("header");
            return header.has("description") &&
                    header.has("name") &&
                    header.has("uuid") &&
                    header.has("version") &&
                    header.getAsJsonArray("version").size() == 3;
        } else {
            return false;
        }
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
    public int hashCode() {
        return this.getPackId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ResourcePack anotherPack)) return false;
        return this.getPackId().equals(anotherPack.getPackId());
    }
}
