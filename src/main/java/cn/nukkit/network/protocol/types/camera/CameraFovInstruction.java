package cn.nukkit.network.protocol.types.camera;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CameraFovInstruction {
    private float fov;
    private float easeTime;
    private CameraEase easeType;
    private boolean clear;
}

