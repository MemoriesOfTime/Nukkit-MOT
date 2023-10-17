
package cn.nukkit.utils.exception;

import cn.nukkit.block.blockproperty.BlockProperty;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;

/**
 * @author joserobjr
 * @since 2020-01-12
 */
@ParametersAreNullableByDefault
public class InvalidBlockPropertyPersistenceValueException extends InvalidBlockPropertyException {
    private static final long serialVersionUID = 1L;
    
    @Nullable
    private final String currentValue;
    
    @Nullable
    private final String invalidValue;

    public InvalidBlockPropertyPersistenceValueException(@NotNull BlockProperty<?> property, String currentValue, String invalidValue) {
        super(property, buildMessage(currentValue, invalidValue));
        this.currentValue = currentValue;
        this.invalidValue = invalidValue;
    }

    public InvalidBlockPropertyPersistenceValueException(@NotNull BlockProperty<?> property, String currentValue, String invalidValue, String message) {
        super(property, buildMessage(currentValue, invalidValue) + ". " + message);
        this.currentValue = currentValue;
        this.invalidValue = invalidValue;
    }

    public InvalidBlockPropertyPersistenceValueException(@NotNull BlockProperty<?> property, String currentValue, String invalidValue, String message, Throwable cause) {
        super(property, buildMessage(currentValue, invalidValue) + ". " + message, cause);
        this.currentValue = currentValue;
        this.invalidValue = invalidValue;
    }

    public InvalidBlockPropertyPersistenceValueException(@NotNull BlockProperty<?> property, String currentValue, String invalidValue, Throwable cause) {
        super(property, buildMessage(currentValue, invalidValue), cause);
        this.currentValue = currentValue;
        this.invalidValue = invalidValue;
    }
    
    private static String buildMessage(Object currentValue, Object invalidValue) {
        return "Current Value: "+currentValue+", Invalid Value: "+invalidValue;
    }


    @Nullable
    public String getCurrentValue() {
        return this.currentValue;
    }

    @Nullable
    public String getInvalidValue() {
        return this.invalidValue;
    }
}
