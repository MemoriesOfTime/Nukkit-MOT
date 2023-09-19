package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

import javax.annotation.Nullable;

/**
 * @author daoge_cmd
 * @date 2023/6/11
 * PowerNukkitX Project
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public final class CameraPreset implements NamedDefinition {

    private String identifier;
    private String parentPreset;
    @Nullable
    private Vector3f pos;
    @Nullable
    private Float yaw;
    @Nullable
    private Float pitch;
    @Nullable
    private CameraAudioListener listener;
    private OptionalBoolean playEffect;

    private int runtimeId;

    public CameraPreset(String identifier, String parentPreset, @Nullable Vector3f pos, @Nullable Float yaw, @Nullable Float pitch, @Nullable CameraAudioListener listener, OptionalBoolean playEffect) {
        this.identifier = identifier;
        this.parentPreset = parentPreset;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.listener = listener;
        this.playEffect = playEffect;
    }
}
