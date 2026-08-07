package cn.nukkit.network.protocol.types.camera;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class CameraAimAssistPriority {
    private final String name;
    private final int priority;
}