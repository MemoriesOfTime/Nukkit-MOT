package cn.nukkit.utils;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.UpdateSoftEnumPacket;
import cn.nukkit.network.protocol.types.camera.CameraPreset;
import lombok.Getter;
import lombok.var;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.TreeMap;

public class CameraPresetManager {

    @Getter
    private static DefinitionRegistry<NamedDefinition> cameraPresetDefinitions;
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
            if (PRESETS.containsKey(preset.getIdentifier())) {
                throw new IllegalArgumentException("Camera preset " + preset.getIdentifier() + " already exists!");
            }
            PRESETS.put(preset.getIdentifier(), preset);
            CommandEnum.CAMERA_PRESETS.updateSoftEnum(UpdateSoftEnumPacket.Type.ADD, preset.getIdentifier());
        }
        int id = 0;
        //重新分配id
        SimpleDefinitionRegistry.Builder<NamedDefinition> builder = SimpleDefinitionRegistry.builder();
        for (var preset : presets) {
            preset.setRuntimeId(id++);
            builder.add(preset);
        }
        cameraPresetDefinitions = builder.build();

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
                .yaw(0F)
                .pitch(0F)
                .build();
        THIRD_PERSON = CameraPreset.builder()
                .identifier("minecraft:third_person")
                .build();
        THIRD_PERSON_FRONT = CameraPreset.builder()
                .identifier("minecraft:third_person_front")
                .build();

        registerCameraPresets(FIRST_PERSON, FREE, THIRD_PERSON, THIRD_PERSON_FRONT);
    }

}
