package cn.nukkit.network.protocol.types.camera;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Data;

import java.util.List;

@Data
public class CameraAimAssistPresetDefinition {
    public String identifier;
    /**
     * @deprecated since v776
     */
    @SuppressWarnings("dep-ann")
    public String categories;
    public final List<String> exclusionList = new ObjectArrayList<>();
    public final List<String> liquidTargetingList = new ObjectArrayList<>();
    private final List<CameraAimAssistItemSettings> itemSettings = new ObjectArrayList<>();
    private String defaultItemSettings;
    private String handSettings;
}