package cn.nukkit.network.protocol.types.camera.aimassist;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import lombok.Data;

import java.util.Map;

@Data
public class CameraAimAssistCategoryPriorities {
    public Map<String, Integer> entities = new Object2IntArrayMap<>();
    public Map<String, Integer> blocks = new Object2IntArrayMap<>();
}