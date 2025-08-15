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
    /**
     * @since v776 1.21.60
     */
    private Float minYawLimit;
    /**
     * @since v776 1.21.60
     */
    private Float maxYawLimit;
    @Nullable
    private CameraAudioListener listener;
    private OptionalBoolean playEffect;
    /**
     * @since v729
     */
    private Float rotationSpeed;
    /**
     * @since v729
     */
    private OptionalBoolean snapToTarget;
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
    private OptionalBoolean continueTargeting;
    /**
     * @since v748
     * @deprecated v818
     */
    @SuppressWarnings("dep-ann")
    private OptionalBoolean alignTargetAndCameraForward;
    /**
     * @since v766
     */
    private Float blockListeningRadius;
    /**
     * @since v766
     */
    private CameraAimAssistPreset aimAssistPreset;
    /**
     * @since v800
     */
    @Nullable
    private ControlScheme controlScheme;

    private int runtimeId;

    @Deprecated
    public CameraPreset(
            String identifier,
            String parentPreset,
            @Nullable Vector3f pos,
            @Nullable Float yaw,
            @Nullable Float pitch,
            Vector2f viewOffset,
            Float radius,
            Float minYawLimit,
            Float maxYawLimit,
            @Nullable CameraAudioListener listener,
            OptionalBoolean playEffect, Float rotationSpeed,
            OptionalBoolean snapToTarget, Vector3f entityOffset,
            Vector2f horizontalRotationLimit,
            Vector2f verticalRotationLimit,
            OptionalBoolean continueTargeting,
            OptionalBoolean alignTargetAndCameraForward,
            Float blockListeningRadius,
            CameraAimAssistPreset aimAssistPreset
    ) {
        this.identifier = identifier;
        this.parentPreset = parentPreset;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.viewOffset = viewOffset;
        this.radius = radius;
        this.minYawLimit = minYawLimit;
        this.maxYawLimit = maxYawLimit;
        this.listener = listener;
        this.playEffect = playEffect;
        this.rotationSpeed = rotationSpeed;
        this.snapToTarget = snapToTarget;
        this.entityOffset = entityOffset;
        this.horizontalRotationLimit = horizontalRotationLimit;
        this.verticalRotationLimit = verticalRotationLimit;
        this.continueTargeting = continueTargeting;
        this.alignTargetAndCameraForward = alignTargetAndCameraForward;
        this.blockListeningRadius = blockListeningRadius;
        this.aimAssistPreset = aimAssistPreset;
    }

    public CameraPreset(
            String identifier,
            String parentPreset,
            @Nullable Vector3f pos,
            @Nullable Float yaw,
            @Nullable Float pitch,
            Vector2f viewOffset,
            Float radius,
            Float minYawLimit,
            Float maxYawLimit,
            @Nullable CameraAudioListener listener,
            OptionalBoolean playEffect, Float rotationSpeed,
            OptionalBoolean snapToTarget, Vector3f entityOffset,
            Vector2f horizontalRotationLimit,
            Vector2f verticalRotationLimit,
            OptionalBoolean continueTargeting,
            OptionalBoolean alignTargetAndCameraForward,
            Float blockListeningRadius,
            CameraAimAssistPreset aimAssistPreset,
            ControlScheme controlScheme
    ) {
        this.identifier = identifier;
        this.parentPreset = parentPreset;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.viewOffset = viewOffset;
        this.radius = radius;
        this.minYawLimit = minYawLimit;
        this.maxYawLimit = maxYawLimit;
        this.listener = listener;
        this.playEffect = playEffect;
        this.rotationSpeed = rotationSpeed;
        this.snapToTarget = snapToTarget;
        this.entityOffset = entityOffset;
        this.horizontalRotationLimit = horizontalRotationLimit;
        this.verticalRotationLimit = verticalRotationLimit;
        this.continueTargeting = continueTargeting;
        this.alignTargetAndCameraForward = alignTargetAndCameraForward;
        this.blockListeningRadius = blockListeningRadius;
        this.aimAssistPreset = aimAssistPreset;
        this.controlScheme = controlScheme;
    }

    public String getParentPreset() {
        return parentPreset == null ? "" : parentPreset;
    }
}