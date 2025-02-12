package cn.nukkit.network.protocol.types.camera;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Data;

import java.util.List;

@Data
public class CameraAimAssistCategories {
    public String identifier;
    public List<CameraAimAssistCategory> categories = new ObjectArrayList<>();
}