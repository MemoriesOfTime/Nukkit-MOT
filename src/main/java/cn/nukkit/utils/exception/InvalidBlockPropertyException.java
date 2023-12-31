package cn.nukkit.utils.exception;

import cn.nukkit.block.blockproperty.BlockProperty;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNullableByDefault;

@ParametersAreNullableByDefault
public class InvalidBlockPropertyException extends IllegalArgumentException {
    private static final long serialVersionUID = -6934630506175381230L;
    
    private final BlockProperty<?> property;

    public InvalidBlockPropertyException(@NotNull BlockProperty<?> property) {
        super(buildMessage(property));
        this.property = property;
    }

    public InvalidBlockPropertyException(@NotNull BlockProperty<?> property, String message) {
        super(buildMessage(property) + ". " + message);
        this.property = property;
    }

    public InvalidBlockPropertyException(@NotNull BlockProperty<?> property, String message, Throwable cause) {
        super(buildMessage(property) + ". " + message, cause);
        this.property = property;
    }

    public InvalidBlockPropertyException(@NotNull BlockProperty<?> property, Throwable cause) {
        super(buildMessage(property), cause);
        this.property = property;
    }

    private static String buildMessage(@NotNull BlockProperty<?> property) {
        return "Property: " + property.getName();
    }

    @NotNull
    public BlockProperty<?> getProperty() {
        return property;
    }
}
