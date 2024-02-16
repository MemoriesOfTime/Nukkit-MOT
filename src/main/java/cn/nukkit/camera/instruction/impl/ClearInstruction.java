package cn.nukkit.camera.instruction.impl;

import cn.nukkit.camera.instruction.CameraInstruction;

public class ClearInstruction implements CameraInstruction {
    private static final ClearInstruction INSTANCE = new ClearInstruction();

    private ClearInstruction() {}

    public static ClearInstruction get() {
        return INSTANCE;
    }
}
