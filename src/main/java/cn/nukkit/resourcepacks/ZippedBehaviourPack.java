package cn.nukkit.resourcepacks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;

import java.io.File;

@Log4j2
public class ZippedBehaviourPack extends ZippedResourcePack {

    private boolean isBehaviourPack = false;

    public ZippedBehaviourPack(File file) {
        super(file);
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
                    log.error("Error while loading behaviour pack manifest: {}", this.getPackName(), ignored);
                }
            }
    }

    @Override
    public boolean isBehaviourPack() {
        return this.isBehaviourPack;
    }
}
