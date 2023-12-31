package cn.nukkit.utils.exception;

import cn.nukkit.block.blockproperty.BlockProperty;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class InvalidBlockPropertyMetaException extends InvalidBlockPropertyException {
    private static final long serialVersionUID = -8493494844859767053L;

    @NotNull
    private final Number currentMeta;

    @NotNull
    private final Number invalidMeta;

    public InvalidBlockPropertyMetaException(BlockProperty<?> property, Number currentMeta, Number invalidMeta) {
        super(property, buildMessage(currentMeta, invalidMeta));
        this.currentMeta = currentMeta;
        this.invalidMeta = invalidMeta;
    }

    public InvalidBlockPropertyMetaException(BlockProperty<?> property, Number currentMeta, Number invalidMeta, String message) {
        super(property, buildMessage(currentMeta, invalidMeta)+". "+message);
        this.currentMeta = currentMeta;
        this.invalidMeta = invalidMeta;
    }

    public InvalidBlockPropertyMetaException(BlockProperty<?> property, Number currentMeta, Number invalidMeta, String message, Throwable cause) {
        super(property, buildMessage(currentMeta, invalidMeta)+". "+message, cause);
        this.currentMeta = currentMeta;
        this.invalidMeta = invalidMeta;
    }

    public InvalidBlockPropertyMetaException(BlockProperty<?> property, Number currentMeta, Number invalidMeta, Throwable cause) {
        super(property, buildMessage(currentMeta, invalidMeta), cause);
        this.currentMeta = currentMeta;
        this.invalidMeta = invalidMeta;
    }
    
    private static String buildMessage(Object currentValue, Object invalidValue) {
        return "Current Meta: "+currentValue+", Invalid Meta: "+invalidValue;
    }

    @NotNull
    public Number getCurrentMeta() {
        return this.currentMeta;
    }

    @NotNull
    public Number getInvalidMeta() {
        return this.invalidMeta;
    }
}
