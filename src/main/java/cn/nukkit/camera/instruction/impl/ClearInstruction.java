package cn.nukkit.camera.instruction.impl;

import cn.nukkit.camera.instruction.CameraInstruction;
import cn.nukkit.nbt.tag.ByteTag;

/**
 * @author daoge_cmd
 * @date 2023/6/11
 * PowerNukkitX Project
 */
public class ClearInstruction implements CameraInstruction {

    private static final ClearInstruction INSTANCE = new ClearInstruction();
    private static final ByteTag TAG = new ByteTag("clear", 1);

    private ClearInstruction() {}

    public static ClearInstruction get() {
        return INSTANCE;
    }

    @Override
    public ByteTag serialize() {
        return TAG;
    }
}
