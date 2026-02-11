package cn.nukkit.network.protocol.types.camera;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data for camera aim assist actor priority.
 * @since v924
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AimAssistActorPriorityData {

    private int presetIndex;
    private int categoryIndex;
    private int actorIndex;
    private int priorityValue;
}
