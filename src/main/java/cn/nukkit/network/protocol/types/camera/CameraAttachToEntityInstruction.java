package cn.nukkit.network.protocol.types.camera;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CameraAttachToEntityInstruction {

    private long uniqueEntityId;
}
