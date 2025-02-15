package cn.nukkit.network.protocol.types.camera;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CameraAimAssistItemSettings {
    private final String itemId;
    private final String category;
}