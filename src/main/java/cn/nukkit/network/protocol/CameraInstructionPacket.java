package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.ByteTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.types.camera.CameraEase;
import cn.nukkit.network.protocol.types.camera.CameraFadeInstruction;
import cn.nukkit.network.protocol.types.camera.CameraSetInstruction;
import cn.nukkit.network.protocol.types.camera.CameraTargetInstruction;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.CameraPresetManager;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.util.DefinitionUtils;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.cloudburstmc.protocol.common.util.Preconditions;

import java.awt.*;
import java.io.IOException;
import java.nio.ByteOrder;

@Getter
@Setter
@ToString
public class CameraInstructionPacket extends DataPacket {

    private CameraSetInstruction setInstruction;
    private CameraFadeInstruction fadeInstruction;
    private OptionalBoolean clear = OptionalBoolean.empty();
    /**
     * @since v712
     */
    private CameraTargetInstruction targetInstruction;
    /**
     * @since v712
     */
    private OptionalBoolean removeTarget = OptionalBoolean.empty();

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return ProtocolInfo.CAMERA_INSTRUCTION_PACKET;
    }

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_20_30_24) {
            CameraSetInstruction set = this.getOptional(null, b -> this.readSetInstruction());

            this.setSetInstruction(set);
            this.setClear(this.getOptional(OptionalBoolean.empty(), buf -> OptionalBoolean.of(buf.getBoolean())));

            CameraFadeInstruction fade = this.getOptional(null, buf -> {
                CameraFadeInstruction.TimeData time = buf.getOptional(null, b -> this.getTimeData());
                Color color = buf.getOptional(null, b -> this.getColor());
                return new CameraFadeInstruction(time, color);
            });

            this.setFadeInstruction(fade);

            if (this.protocol >= ProtocolInfo.v1_21_20) {
                this.setTargetInstruction(this.getOptional(null, buf -> {
                    Vector3f targetCenterOffset = this.getOptional(null, BinaryStream::getVector3f);
                    long uniqueEntityId = this.getLLong();
                    return new CameraTargetInstruction(targetCenterOffset, uniqueEntityId);
                }));
                this.setRemoveTarget(this.getOptional(OptionalBoolean.empty(), buf -> OptionalBoolean.of(buf.getBoolean())));
            }
        } else {
            CompoundTag data = this.getTag();
            if (data.contains("set", CompoundTag.class)) {
                CameraSetInstruction set = new CameraSetInstruction();
                CompoundTag setTag = data.getCompound("set");

                int runtimeId = setTag.getInt("preset");
                NamedDefinition definition = CameraPresetManager.getCameraPresetDefinitions().getDefinition(runtimeId);
                Preconditions.checkNotNull(definition, "Unknown camera preset " + runtimeId);
                set.setPreset(definition);

                if (setTag.contains("ease", CompoundTag.class)) {
                    CompoundTag easeTag = setTag.getCompound("ease");
                    CameraEase type = CameraEase.fromName(easeTag.getString("type"));
                    float time = easeTag.getFloat("time");
                    set.setEase(new CameraSetInstruction.EaseData(type, time));
                }

                if (setTag.contains("pos", CompoundTag.class)) {
                    ListTag<FloatTag> floats = setTag.getCompound("pos").getList("pos", FloatTag.class);

                    float x = floats.size() > 0 ? floats.get(0).getData() : 0;
                    float y = floats.size() > 1 ? floats.get(1).getData() : 0;
                    float z = floats.size() > 2 ? floats.get(2).getData() : 0;
                    set.setPos(new Vector3f(x, y, z));
                }

                if (setTag.contains("rot", CompoundTag.class)) {
                    CompoundTag rot = setTag.getCompound("rot");
                    float pitch = rot.contains("x", FloatTag.class) ? rot.getFloat("x") : 0;
                    float yaw = rot.contains("y", FloatTag.class) ? rot.getFloat("y") : 0;
                    set.setRot(new Vector2f(pitch, yaw));
                }

                if (setTag.contains("default", ByteTag.class)) {
                    set.setDefaultPreset(OptionalBoolean.of(setTag.getBoolean("default")));
                }
                this.setSetInstruction(set);
            }

            if (data.contains("clear", ByteTag.class)) {
                this.setClear(OptionalBoolean.of(data.getBoolean("clear")));
            }

            if (data.contains("fade", CompoundTag.class)) {
                CameraFadeInstruction fade = new CameraFadeInstruction();
                CompoundTag fadeTag = data.getCompound("fade");

                if (fadeTag.contains("time", CompoundTag.class)) {
                    CompoundTag timeTag = fadeTag.getCompound("time");
                    float fadeIn = timeTag.getFloat("fadeIn");
                    float wait = timeTag.getFloat("hold");
                    float fadeout = timeTag.getFloat("fadeOut");
                    fade.setTimeData(new CameraFadeInstruction.TimeData(fadeIn, wait, fadeout));
                }

                if (fadeTag.contains("color", CompoundTag.class)) {
                    CompoundTag colorTag = data.getCompound("color");

                    fade.setColor(new Color(
                            (int) (colorTag.getFloat("r") * 255),
                            (int) (colorTag.getFloat("b") * 255), // game is sending blue as green and green as blue
                            (int) (colorTag.getFloat("g") * 255)
                    ));
                }

                this.setFadeInstruction(fade);
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_20_30_24) {
            this.putOptionalNull(this.getSetInstruction(), this::writeSetInstruction);

            this.putOptional(OptionalBoolean::isPresent, this.getClear(),
                    (b, optional) -> b.putBoolean(optional.getAsBoolean()));

            this.putOptionalNull(this.getFadeInstruction(), (b, fade) -> {
                b.putOptionalNull(fade.getTimeData(), this::putTimeData);
                b.putOptionalNull(fade.getColor(), this::putColor);
            });

            if (this.protocol >= ProtocolInfo.v1_21_20) {
                this.putOptionalNull(this.getTargetInstruction(), (b, targetInstruction) -> {
                    b.putOptionalNull(targetInstruction.getTargetCenterOffset(), BinaryStream::putVector3f);
                    b.putLLong(targetInstruction.getUniqueEntityId());
                });
                this.putOptional(OptionalBoolean::isPresent, this.getRemoveTarget(),
                        (b, optional) -> b.putBoolean(optional.getAsBoolean()));
            }
        } else {
            CompoundTag data = new CompoundTag();
            if (this.getSetInstruction() != null) {
                CameraSetInstruction set = this.getSetInstruction();
                DefinitionUtils.checkDefinition(CameraPresetManager.getCameraPresetDefinitions(), set.getPreset());

                CompoundTag setData = new CompoundTag().putInt("preset", set.getPreset().getRuntimeId());

                if (set.getEase() != null) {
                    setData.putCompound("ease", new CompoundTag()
                            .putString("type", set.getEase().getEaseType().getSerializeName())
                            .putFloat("time", set.getEase().getTime())
                    );
                }

                if (set.getPos() != null) {
                    ListTag<FloatTag> listTag = new ListTag<>();
                    listTag.add(new FloatTag(set.getPos().getX()));
                    listTag.add(new FloatTag(set.getPos().getY()));
                    listTag.add(new FloatTag(set.getPos().getZ()));
                    setData.putCompound("pos", new CompoundTag()
                            .putList("pos", listTag)
                    );
                }

                if (set.getRot() != null) {
                    setData.putCompound("rot", new CompoundTag()
                            .putFloat("x", set.getRot().getX()) // pitch
                            .putFloat("y", set.getRot().getY()) // yaw
                    );
                }

                if (set.getDefaultPreset().isPresent()) {
                    setData.putBoolean("default", set.getDefaultPreset().getAsBoolean());
                }

                data.put("set", setData);
            }

            if (this.getClear().isPresent()) {
                data.putBoolean("clear", this.getClear().getAsBoolean());
            }

            if (this.getFadeInstruction() != null) {
                CameraFadeInstruction fade = this.getFadeInstruction();
                CompoundTag fadeData = new CompoundTag();

                if (fade.getTimeData() != null) {
                    fadeData.putCompound("time", new CompoundTag()
                            .putFloat("fadeIn", fade.getTimeData().getFadeInTime())
                            .putFloat("hold", fade.getTimeData().getWaitTime())
                            .putFloat("fadeOut", fade.getTimeData().getFadeOutTime())
                    );
                }

                if (fade.getColor() != null) {
                    fadeData.putCompound("color", new CompoundTag()
                            .putFloat("r", fade.getColor().getRed() / 255F)
                            .putFloat("g", fade.getColor().getBlue() / 255F) // game is sending blue as green and green as blue
                            .putFloat("b", fade.getColor().getGreen() / 255F)
                    );
                }

                data.put("fade", fadeData);
            }
            try {
                this.put(NBTIO.write(data, ByteOrder.LITTLE_ENDIAN, true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void writeSetInstruction(CameraSetInstruction set) {
        DefinitionUtils.checkDefinition(CameraPresetManager.getCameraPresetDefinitions(), set.getPreset());
        this.putLInt(set.getPreset().getRuntimeId());

        this.putOptionalNull(set.getEase(), this::putEase);
        this.putOptionalNull(set.getPos(), this::putVector3f);
        this.putOptionalNull(set.getRot(), vector2f -> this.putVector2f(vector2f));
        this.putOptionalNull(set.getFacing(), this::putVector3f);
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            this.putOptionalNull(set.getViewOffset(), vector2f -> this.putVector2f(vector2f));
            if (this.protocol >= ProtocolInfo.v1_21_40) {
                this.putOptionalNull(set.getEntityOffset(), this::putVector3f);
            }
        }
        this.putOptional(OptionalBoolean::isPresent, set.getDefaultPreset(),
                (b1, optional) -> b1.putBoolean(optional.getAsBoolean()));
        if (this.protocol >= ProtocolInfo.v1_21_90) {
            this.putBoolean(set.isRemoveIgnoreStartingValues());
        }
    }

    protected CameraSetInstruction readSetInstruction() {
        int runtimeId = this.getLInt();
        NamedDefinition definition = CameraPresetManager.getCameraPresetDefinitions().getDefinition(runtimeId);

        CameraSetInstruction.EaseData ease = this.getOptional(null, b1 -> this.getEase());
        Vector3f pos = this.getOptional(null, BinaryStream::getVector3f);
        Vector2f rot = this.getOptional(null, BinaryStream::getVector2f);
        Vector3f facing = this.getOptional(null, BinaryStream::getVector3f);
        Vector2f viewOffset = null;
        Vector3f entityOffset = null;
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            viewOffset = this.getOptional(null, BinaryStream::getVector2f);
            if (this.protocol >= ProtocolInfo.v1_21_40) {
                entityOffset = this.getOptional(null, BinaryStream::getVector3f);
            }
        }
        OptionalBoolean defaultPreset = this.getOptional(OptionalBoolean.empty(), b1 -> OptionalBoolean.of(b1.getBoolean()));
        boolean removeIgnoreStartingValues = false;
        if (this.protocol >= ProtocolInfo.v1_21_90) {
            removeIgnoreStartingValues = this.getBoolean();
        }
        return new CameraSetInstruction(definition, ease, pos, rot, facing, viewOffset, entityOffset, defaultPreset, removeIgnoreStartingValues);
    }

    protected void putEase(CameraSetInstruction.EaseData ease) {
        this.putByte((byte) ease.getEaseType().ordinal());
        this.putLFloat(ease.getTime());
    }

    protected CameraSetInstruction.EaseData getEase() {
        CameraEase type = CameraEase.values()[this.getByte()];
        float time = this.getLFloat();
        return new CameraSetInstruction.EaseData(type, time);
    }

    protected void putTimeData(CameraFadeInstruction.TimeData timeData) {
        this.putLFloat(timeData.getFadeInTime());
        this.putLFloat(timeData.getWaitTime());
        this.putLFloat(timeData.getFadeOutTime());
    }

    protected CameraFadeInstruction.TimeData getTimeData() {
        float fadeIn = this.getLFloat();
        float wait = this.getLFloat();
        float fadeOut = this.getLFloat();
        return new CameraFadeInstruction.TimeData(fadeIn, wait, fadeOut);
    }

    protected void putColor(Color color) {
        this.putLFloat(color.getRed() / 255F);
        this.putLFloat(color.getGreen() / 255F);
        this.putLFloat(color.getBlue() / 255F);
    }

    protected Color getColor() {
        return new Color(
                (int) (this.getLFloat() * 255),
                (int) (this.getLFloat() * 255),
                (int) (this.getLFloat() * 255)
        );
    }

}
