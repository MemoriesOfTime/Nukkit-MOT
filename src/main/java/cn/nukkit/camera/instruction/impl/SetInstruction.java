package cn.nukkit.camera.instruction.impl;

import cn.nukkit.camera.data.CameraPreset;
import cn.nukkit.camera.data.Ease;
import cn.nukkit.camera.instruction.CameraInstruction;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;

@Builder
@Getter
public class SetInstruction implements CameraInstruction {
    @Nullable
    private final Ease ease;
    @Nullable
    private final Vector3f pos;
    @Nullable
    private final Vector2f rot;
    @Nullable
    private final Vector3f facing;
    @NotNull
    private final CameraPreset preset;
    private final Optional<Boolean> defaultPreset = Optional.empty();
}
