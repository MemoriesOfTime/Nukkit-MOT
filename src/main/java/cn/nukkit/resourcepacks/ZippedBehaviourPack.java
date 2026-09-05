package cn.nukkit.resourcepacks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;

@Log4j2
public class ZippedBehaviourPack extends ZippedResourcePack {

    private boolean isBehaviourPack = false;

    public ZippedBehaviourPack(File file) {
        this(file, SupportType.UNIVERSAL);
    }

    /**
     * @deprecated Use {@link #ZippedBehaviourPack(File, SupportType)} instead
     */
    @Deprecated
    public ZippedBehaviourPack(File file, boolean isNetEase) {
        this(file, isNetEase ? SupportType.NETEASE : SupportType.UNIVERSAL);
    }

    public ZippedBehaviourPack(File file, SupportType supportType) {
        this(file, supportType, false);
    }

    /**
     * @see ZippedResourcePack#ZippedResourcePack(File, SupportType, boolean)
     */
    @ApiStatus.Internal
    public ZippedBehaviourPack(File file, SupportType supportType, boolean alreadySnapshot) {
        super(file, supportType, alreadySnapshot);
        if (this.manifest.has("modules"))
            for (JsonElement moduleElement : this.manifest.getAsJsonArray("modules")) {
                try {
                    if (moduleElement.isJsonObject()) {
                        JsonObject module = moduleElement.getAsJsonObject();
                        if (module.has("type")) {
                            JsonElement typeElement = module.get("type");
                            if (typeElement.isJsonPrimitive() && typeElement.getAsJsonPrimitive().isString()) {
                                String typeValue = typeElement.getAsString();
                                if ("data".equals(typeValue)) {
                                    this.isBehaviourPack = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // getPackId（uuid 已通过 verifyManifest 校验）而非 getPackName：
                    // NetEase 包可无 name，后者会 NPE 并掩盖原始异常。
                    // getPackId (uuid validated by verifyManifest), not getPackName:
                    // NetEase packs may lack a name and getPackName would NPE,
                    // masking the original error.
                    log.error("Error while loading behaviour pack manifest: {}", this.getPackId(), ignored);
                }
            }
    }

    @Override
    public boolean isBehaviourPack() {
        return this.isBehaviourPack;
    }

    @Override
    public boolean isAddonPack() {
        return this.isBehaviourPack;
    }
}
