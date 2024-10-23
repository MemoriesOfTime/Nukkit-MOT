package cn.nukkit.network.protocol.types.camera;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.jetbrains.annotations.Nullable;

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
    /**
     * @since v712
     */
    private Vector2f viewOffset;
    /**
     * @since v712
     */
    private Float radius;
    @Nullable
    private CameraAudioListener listener;
    private OptionalBoolean playEffect = OptionalBoolean.empty();
    /**
     * @since v729
     */
    private Float rotationSpeed;
    /**
     * @since v729
     */
    private OptionalBoolean snapToTarget = OptionalBoolean.empty();
    /**
     * @since v729
     */
    private Vector3f entityOffset;
    /**
     * @since v748
     */
    private Vector2f horizontalRotationLimit;
    /**
     * @since v748
     */
    private Vector2f verticalRotationLimit;
    /**
     * @since v748
     */
    private OptionalBoolean continueTargeting = OptionalBoolean.empty();
    /**
     * @since v748
     */
    private OptionalBoolean alignTargetAndCameraForward = OptionalBoolean.empty();

    private int runtimeId;

    @Deprecated
    public CameraPreset(String identifier, String parentPreset, @Nullable Vector3f pos, @Nullable Float yaw, @Nullable Float pitch, @Nullable CameraAudioListener listener, OptionalBoolean playEffect) {
        this.identifier = identifier;
        this.parentPreset = parentPreset;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.listener = listener;
        this.playEffect = playEffect;
    }

    @Deprecated
    public CameraPreset(String identifier, String parentPreset, @Nullable Vector3f pos, @Nullable Float yaw, @Nullable Float pitch, @Nullable Vector2f viewOffset, @Nullable Float radius, @Nullable CameraAudioListener listener, OptionalBoolean playEffect) {
        this.identifier = identifier;
        this.parentPreset = parentPreset;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.viewOffset = viewOffset;
        this.radius = radius;
        this.listener = listener;
        this.playEffect = playEffect;
    }

    @Deprecated
    public CameraPreset(String identifier, String parentPreset, @Nullable Vector3f pos, @Nullable Float yaw, @Nullable Float pitch, @Nullable Vector2f viewOffset, @Nullable Float radius, @Nullable CameraAudioListener listener, OptionalBoolean playEffect, @Nullable Float rotationSpeed, OptionalBoolean snapToTarget, @Nullable Vector3f entityOffset) {
        this.identifier = identifier;
        this.parentPreset = parentPreset;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.viewOffset = viewOffset;
        this.radius = radius;
        this.listener = listener;
        this.playEffect = playEffect;
        this.rotationSpeed = rotationSpeed;
        this.snapToTarget = snapToTarget;
        this.entityOffset = entityOffset;
    }

    public CameraPreset(String identifier, String parentPreset, @Nullable Vector3f pos, @Nullable Float yaw, @Nullable Float pitch, Vector2f viewOffset, Float radius, @Nullable CameraAudioListener listener, OptionalBoolean playEffect, Float rotationSpeed, OptionalBoolean snapToTarget, Vector3f entityOffset, Vector2f horizontalRotationLimit, Vector2f verticalRotationLimit, OptionalBoolean continueTargeting, OptionalBoolean alignTargetAndCameraForward) {
        this.identifier = identifier;
        this.parentPreset = parentPreset;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.viewOffset = viewOffset;
        this.radius = radius;
        this.listener = listener;
        this.playEffect = playEffect;
        this.rotationSpeed = rotationSpeed;
        this.snapToTarget = snapToTarget;
        this.entityOffset = entityOffset;
        this.horizontalRotationLimit = horizontalRotationLimit;
        this.verticalRotationLimit = verticalRotationLimit;
        this.continueTargeting = continueTargeting;
        this.alignTargetAndCameraForward = alignTargetAndCameraForward;
    }

    public String getParentPreset() {
        return parentPreset == null ? "" : parentPreset;
    }
}
