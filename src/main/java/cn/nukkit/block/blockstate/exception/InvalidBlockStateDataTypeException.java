package cn.nukkit.block.blockstate.exception;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * @author joserobjr
 */
public class InvalidBlockStateDataTypeException extends IllegalArgumentException {
    private static final long serialVersionUID = 6883758182474914542L;

    public InvalidBlockStateDataTypeException(@NotNull Number blockData) {
        super("The block data " + blockData + " has an unsupported type " + blockData.getClass());
    }

    public InvalidBlockStateDataTypeException(@NotNull Number blockData, @Nullable Throwable cause) {
        super("The block data " + blockData + " has an unsupported type " + blockData.getClass(), cause);
    }
}
