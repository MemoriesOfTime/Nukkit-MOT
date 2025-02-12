package cn.nukkit.network.protocol.types.camera.aimassist;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class CameraAimAssistPreset {
    public String identifier;
    public String categories;
    public final List<String> exclusionList = new ObjectArrayList<>();
    public final List<String> liquidTargetingList = new ObjectArrayList<>();
    public final Map<String, String> itemSettings = new Object2ObjectArrayMap<>();
    @Nullable
    public String defaultItemSettings;
    @Nullable
    public String handSettings;
}