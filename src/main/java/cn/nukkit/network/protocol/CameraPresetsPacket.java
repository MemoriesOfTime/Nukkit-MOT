package cn.nukkit.network.protocol;

import cn.nukkit.camera.data.CameraAudioListener;
import cn.nukkit.camera.data.CameraPreset;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.OptionalBoolean;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

@Getter
@Setter
public class CameraPresetsPacket extends DataPacket {

    private final List<CameraPreset> presets = new ObjectArrayList<>();

    @Override
    public byte pid() {
        return ProtocolInfo.CAMERA_PRESETS_PACKET;
    }

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_20_30) {
            this.getArray(this.presets, value -> this.getPreset());
        } else {
            CompoundTag data = this.getTag();

            ListTag<CompoundTag> presetListTag = data.getList("presets", CompoundTag.class);
            for (CompoundTag presetTag : presetListTag.getAll()) {
                CameraPreset preset = new CameraPreset();
                preset.setIdentifier(presetTag.getString("identifier"));
                preset.setParentPreset(presetTag.getString("inherit_from"));

                if (presetTag.contains("pos_x", FloatTag.class) || presetTag.contains("pos_y") || presetTag.contains("pos_z")) {
                    float x = presetTag.contains("pos_x") ? presetTag.getFloat("pos_x") : 0;
                    float y = presetTag.contains("pos_y") ? presetTag.getFloat("pos_y") : 0;
                    float z = presetTag.contains("pos_z") ? presetTag.getFloat("pos_z") : 0;
                    preset.setPos(new Vector3f(x, y, z));
                }

                if (presetTag.contains("rot_y")) {
                    preset.setYaw(presetTag.getFloat("rot_y"));
                }

                if (presetTag.contains("rot_x")) {
                    preset.setPitch(presetTag.getFloat("rot_x"));
                }
                this.presets.add(preset);
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_20_30) {
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
        this.putOptionalNull(preset.getListener(), (listener) -> this.putByte((byte) listener.ordinal()));
        this.putOptional(OptionalBoolean::isPresent, preset.getPlayEffect(), (optional) -> this.putBoolean(optional.getAsBoolean()));
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

        CameraAudioListener listener = this.getOptional(null, binaryStream -> CameraAudioListener.values()[binaryStream.getByte()]);
        OptionalBoolean effects = this.getOptional(OptionalBoolean.empty(), binaryStream -> OptionalBoolean.of(binaryStream.getBoolean()));
        return new CameraPreset(identifier, parentPreset, pos, pitch, yaw, listener, effects);
    }
}
