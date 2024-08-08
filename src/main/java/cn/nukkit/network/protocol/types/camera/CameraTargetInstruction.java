package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CameraTargetInstruction {
    private Vector3f targetCenterOffset;
    private long uniqueEntityId;
}