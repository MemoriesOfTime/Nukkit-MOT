package cn.nukkit.network.protocol.types.camera;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class CameraAimAssistCategory {
    private String name;
    private List<CameraAimAssistPriority> entityPriorities = new ObjectArrayList<>();
    private List<CameraAimAssistPriority> blockPriorities = new ArrayList<>();
    private Integer entityDefaultPriorities;
    private Integer blockDefaultPriorities;
    public Map<String, Integer> blocktags = new Object2IntArrayMap<>();
}