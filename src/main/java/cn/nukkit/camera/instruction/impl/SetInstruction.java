package cn.nukkit.camera.instruction.impl;

import cn.nukkit.camera.data.CameraPreset;
import cn.nukkit.camera.data.Ease;
import cn.nukkit.camera.data.Pos;
import cn.nukkit.camera.data.Rot;
import cn.nukkit.camera.instruction.CameraInstruction;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

/**
 * @author daoge_cmd
 * @date 2023/6/11
 * PowerNukkitX Project
 */
@Builder
@Getter
public class SetInstruction implements CameraInstruction {

    @Nullable
    private final Ease ease;
    @Nullable
    private final Pos pos;
    @Nullable
    private final Rot rot;
    private final CameraPreset preset;

    @Override
    public Tag serialize() {
        var tag = new CompoundTag("set");
        if (ease != null)
            tag.putCompound("ease", ease.serialize());
        if (pos != null)
            tag.putCompound("pos", pos.serialize());
        if (rot != null)
            tag.putCompound("rot", rot.serialize());
        tag.putInt("preset", preset.getId());
        return tag;
    }
}
