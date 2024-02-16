package cn.nukkit.camera.data;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.UpdateSoftEnumPacket;
import cn.nukkit.network.protocol.types.camera.CameraAudioListener;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Getter
public final class CameraPreset {

    private static final Map<String, CameraPreset> PRESETS = new TreeMap<>();

    public static Map<String, CameraPreset> getPresets() {
        return PRESETS;
    }

    @Nullable
    public static CameraPreset getPreset(String identifier) {
        return getPresets().get(identifier);
    }

    public static void registerCameraPresets(CameraPreset... presets) {
        for (var preset : presets) {
            if (PRESETS.containsKey(preset.getIdentifier()))
                throw new IllegalArgumentException("Camera preset " + preset.getIdentifier() + " already exists!");
            PRESETS.put(preset.getIdentifier(), preset);
            CommandEnum.CAMERA_PRESETS.updateSoftEnum(UpdateSoftEnumPacket.Type.ADD, preset.getIdentifier());
        }
        int id = 0;
        //重新分配id
        for (var preset : presets) {
            preset.id = id++;
        }
        Server.getInstance().getOnlinePlayers().values().forEach(Player::sendCameraPresets);
    }

    public static final CameraPreset FIRST_PERSON;
    public static final CameraPreset FREE;
    public static final CameraPreset THIRD_PERSON;
    public static final CameraPreset THIRD_PERSON_FRONT;

    static {
        FIRST_PERSON = CameraPreset.builder()
                .identifier("minecraft:first_person")
                .build();
        FREE = CameraPreset.builder()
                .identifier("minecraft:free")
                .pos(new Vector3f(0, 0, 0))
                .yaw(0f)
                .pitch(0f)
                .build();
        THIRD_PERSON = CameraPreset.builder()
                .identifier("minecraft:third_person")
                .build();
        THIRD_PERSON_FRONT = CameraPreset.builder()
                .identifier("minecraft:third_person_front")
                .build();

        registerCameraPresets(FIRST_PERSON, FREE, THIRD_PERSON, THIRD_PERSON_FRONT);
    }

    private final String identifier;
    private final String inheritFrom;
    @Nullable
    private final Vector3f pos;
    @Nullable
    private final Float yaw;
    @Nullable
    private final Float pitch;
    @Nullable
    private final CameraAudioListener listener;
    @NotNull
    @Builder.Default
    private Optional<Boolean> playEffect = Optional.empty();
    private int id = 0;

    /**
     * Remember to call the registerCameraPresets() method to register!
     */
    @Builder
    public CameraPreset(String identifier, String inheritFrom, @Nullable Vector3f pos, @Nullable Float yaw, @Nullable Float pitch, @Nullable CameraAudioListener listener, Optional<Boolean> playEffect) {
        this.identifier = identifier;
        this.inheritFrom = inheritFrom != null ? inheritFrom : "";
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.listener = listener;
        this.playEffect = playEffect.isEmpty() ? Optional.empty() : playEffect;
    }
}
