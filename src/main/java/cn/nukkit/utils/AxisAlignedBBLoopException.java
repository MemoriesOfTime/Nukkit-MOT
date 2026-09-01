package cn.nukkit.utils;

/**
 * Tag exception for runaway bounding-box collision sweeps (malformed/oversized {@code AxisAlignedBB}
 * that would iterate millions of blocks and freeze the main thread).
 * {@link CollisionHelper} short-circuits such sweeps with a warning; this stable type lets
 * plugins/operators filter these events.
 * <p>
 * Adapted from EaseCation/Nukkit (<a href="https://github.com/EaseCation/Nukkit">EaseCation/Nukkit</a>)
 */
public class AxisAlignedBBLoopException extends RuntimeException {

    public AxisAlignedBBLoopException() {
    }

    public AxisAlignedBBLoopException(String message) {
        super(message);
    }

    public AxisAlignedBBLoopException(String message, Throwable cause) {
        super(message, cause);
    }

    public AxisAlignedBBLoopException(Throwable cause) {
        super(cause);
    }

    public AxisAlignedBBLoopException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
