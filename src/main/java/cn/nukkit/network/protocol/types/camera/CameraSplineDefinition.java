package cn.nukkit.network.protocol.types.camera;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Definition for a camera spline animation.
 * @since v924
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CameraSplineDefinition {

    private String name;
    private CameraSplineInstruction instruction;
}
