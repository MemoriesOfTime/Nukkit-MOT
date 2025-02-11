package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.types.camera.aimassist.CameraAimAssist;
import cn.nukkit.network.protocol.types.camera.CameraAudioListener;
import cn.nukkit.network.protocol.types.camera.CameraPreset;
import cn.nukkit.network.protocol.types.camera.aimassist.CameraPresetAimAssist;
import cn.nukkit.utils.BinaryStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

@Getter
@Setter
@ToString
public class CameraPresetsPacket extends DataPacket {

    private final List<CameraPreset> presets = new ObjectArrayList<>();

    @Override
    public byte pid() {
        return ProtocolInfo.CAMERA_PRESETS_PACKET;
    }

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_20_30_24) {
            this.getArray(this.presets, value -> this.getPreset());
        } else {
            CompoundTag data = this.getTag();

            ListTag<CompoundTag> presetListTag = data.getList("presets", CompoundTag.class);
            for (CompoundTag presetTag : presetListTag.getAll()) {
                CameraPreset preset = new CameraPreset();
                preset.setIdentifier(presetTag.getString("identifier"));
                preset.setParentPreset(presetTag.getString("inherit_from"));

                if (presetTag.contains("pos_x", FloatTag.class) || presetTag.contains("pos_y", FloatTag.class) || presetTag.contains("pos_z", FloatTag.class)) {
                    float x = presetTag.contains("pos_x") ? presetTag.getFloat("pos_x") : 0;
                    float y = presetTag.contains("pos_y") ? presetTag.getFloat("pos_y") : 0;
                    float z = presetTag.contains("pos_z") ? presetTag.getFloat("pos_z") : 0;
                    preset.setPos(new Vector3f(x, y, z));
                }

                if (presetTag.contains("rot_y", FloatTag.class)) {
                    preset.setYaw(presetTag.getFloat("rot_y"));
                }

                if (presetTag.contains("rot_x", FloatTag.class)) {
                    preset.setPitch(presetTag.getFloat("rot_x"));
                }
                this.presets.add(preset);
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_20_30_24) {
            this.putArray(this.presets, this::putPreset);
        } else {
            try {
                CompoundTag data = new CompoundTag();

                ListTag<CompoundTag> presetListTag = new ListTag<>("presets");
                for (CameraPreset preset : this.presets) {
                    CompoundTag presetTag = new CompoundTag()
                            .putString("identifier", preset.getIdentifier())
                            .putString("inherit_from", preset.getParentPreset());
                    if (preset.getPos() != null) {
                        presetTag.putFloat("pos_x", preset.getPos().getX())
                                .putFloat("pos_y", preset.getPos().getY())
                                .putFloat("pos_z", preset.getPos().getZ());
                    }
                    if (preset.getYaw() != null) {
                        presetTag.putFloat("rot_y", preset.getYaw());
                    }
                    if (preset.getPitch() != null) {
                        presetTag.putFloat("rot_x", preset.getPitch());
                    }
                    presetListTag.add(presetTag);
                }
                data.putList(presetListTag);

                this.put(NBTIO.write(data, ByteOrder.LITTLE_ENDIAN, true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void putPreset(CameraPreset preset) {
        this.putString(preset.getIdentifier());
        this.putString(preset.getParentPreset());
        this.putOptionalNull(preset.getPos(), (pos) -> this.putLFloat(pos.getX()));
        this.putOptionalNull(preset.getPos(), (pos) -> this.putLFloat(pos.getY()));
        this.putOptionalNull(preset.getPos(), (pos) -> this.putLFloat(pos.getZ()));
        this.putOptionalNull(preset.getPitch(), this::putLFloat);
        this.putOptionalNull(preset.getYaw(), this::putLFloat);
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            if (this.protocol >= ProtocolInfo.v1_21_30) {
                this.putOptionalNull(preset.getRotationSpeed(), this::putLFloat);
                this.putOptionalNull(preset.getSnapToTarget(), (snapToTarget) -> this.putBoolean(snapToTarget.getAsBoolean()));
                if (this.protocol >= ProtocolInfo.v1_21_40) {
                    this.putOptionalNull(preset.getHorizontalRotationLimit(), vector2f -> this.putVector2f(vector2f));
                    this.putOptionalNull(preset.getVerticalRotationLimit(), vector2f -> this.putVector2f(vector2f));
                    this.putOptional(o -> o != null && o.isPresent(), preset.getContinueTargeting(), (optional) -> this.putBoolean(optional.getAsBoolean()));
                }
            }
            if (this.protocol >= ProtocolInfo.v1_21_50) {
                this.putOptionalNull(preset.getBlockListeningRadius(), this::putLFloat);
            }
            this.putOptionalNull(preset.getViewOffset(), (viewOffset) -> {
                this.putLFloat(viewOffset.getX());
                this.putLFloat(viewOffset.getY());
            });
            if (this.protocol >= ProtocolInfo.v1_21_30) {
                this.putOptionalNull(preset.getEntityOffset(), this::putVector3f);
            }
            this.putOptionalNull(preset.getRadius(), this::putLFloat);
        }
        this.putOptionalNull(preset.getListener(), (listener) -> this.putByte((byte) listener.ordinal()));
        this.putOptional(o -> o != null && o.isPresent(), preset.getPlayEffect(), (optional) -> this.putBoolean(optional.getAsBoolean()));
        if (this.protocol >= ProtocolInfo.v1_21_40) {
            this.putOptional(o -> o != null && o.isPresent(), preset.getAlignTargetAndCameraForward(), (optional) -> this.putBoolean(optional.getAsBoolean()));
        }
        if (this.protocol >= ProtocolInfo.v1_21_50) {
            this.putOptionalNull(preset.getAimAssistPreset(), cameraAimAssistPreset -> {
                this.putCameraAimAssist(cameraAimAssistPreset);
            });
        }
    }

    protected void putCameraAimAssist(CameraPresetAimAssist aimAssist) {
        this.putOptionalNull(aimAssist.getPresetId(), this::putString);
        this.putOptionalNull(aimAssist.getTargetMode() == null? null: aimAssist.getTargetMode().ordinal(), this::putLInt);
        this.putOptionalNull(aimAssist.getAngle(), vector2f -> this.putVector2f(vector2f));
        this.putOptionalNull(aimAssist.getDistance(), this::putLFloat);
    }

    protected CameraPreset getPreset() {
        String identifier = this.getString();
        String parentPreset = this.getString();

        Float x = this.getOptional(null, BinaryStream::getLFloat);
        Float y = this.getOptional(null, BinaryStream::getLFloat);
        Float z = this.getOptional(null, BinaryStream::getLFloat);
        Vector3f pos = x == null || y == null || z == null ? null : new Vector3f(x, y, z);

        Float pitch = this.getOptional(null, BinaryStream::getLFloat);
        Float yaw = this.getOptional(null, BinaryStream::getLFloat);

        Float blockListeningRadius = null;
        Vector2f viewOffset = null;
        Float radius = null;
        Float rotationSpeed = null;
        OptionalBoolean snapToTarget = OptionalBoolean.empty();
        Vector2f horizontalRotationLimit = null;
        Vector2f verticalRotationLimit = null;
        OptionalBoolean continueTargeting = OptionalBoolean.empty();
        Vector3f entityOffset = null;
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            if (this.protocol >= ProtocolInfo.v1_21_30) {
                rotationSpeed = this.getOptional(null, BinaryStream::getLFloat);
                snapToTarget = this.getOptional(OptionalBoolean.empty(), b -> OptionalBoolean.of(b.getBoolean()));
                if (this.protocol >= ProtocolInfo.v1_21_40) {
                    horizontalRotationLimit = this.getOptional(null, b -> this.getVector2f());
                    verticalRotationLimit = this.getOptional(null, b -> this.getVector2f());
                    continueTargeting = this.getOptional(OptionalBoolean.empty(), b -> OptionalBoolean.of(b.getBoolean()));
                }
            }
            if (this.protocol >= ProtocolInfo.v1_21_50) {
                blockListeningRadius = this.getOptional(null, b -> this.getLFloat());
            }
            viewOffset = this.getOptional(null, b -> this.getVector2f());
            if (this.protocol >= ProtocolInfo.v1_21_30) {
                entityOffset = this.getOptional(null, BinaryStream::getVector3f);
            }
            radius = this.getOptional(null, BinaryStream::getLFloat);
        }

        CameraAudioListener listener = this.getOptional(null, b -> CameraAudioListener.values()[b.getByte()]);
        OptionalBoolean effects = this.getOptional(OptionalBoolean.empty(), b -> OptionalBoolean.of(b.getBoolean()));
        OptionalBoolean alignTargetAndCameraForward = OptionalBoolean.empty();
        if (this.protocol >= ProtocolInfo.v1_21_40) {
            alignTargetAndCameraForward = this.getOptional(OptionalBoolean.empty(), b -> OptionalBoolean.of(b.getBoolean()));
        }
        CameraPresetAimAssist aimAssist = null;
        if (this.protocol >= ProtocolInfo.v1_21_50) {
            aimAssist = this.getOptional(null, b -> this.getCameraAimAssist());
        }
        return new CameraPreset(identifier, parentPreset, pos, yaw, pitch, viewOffset, radius, listener, effects, rotationSpeed, snapToTarget, entityOffset, horizontalRotationLimit, verticalRotationLimit, continueTargeting, alignTargetAndCameraForward, blockListeningRadius, aimAssist);
    }

    protected CameraPresetAimAssist getCameraAimAssist() {
        String identifier = this.getOptional(null, BinaryStream::getString);
        Integer targetMode = this.getOptional(null, BinaryStream::getLInt);
        Vector2f angle = this.getOptional(null, BinaryStream::getVector2f);
        Float distance = this.getOptional(null, BinaryStream::getLFloat);
        return new CameraPresetAimAssist(identifier, CameraAimAssist.values()[targetMode], angle, distance);
    }
}
