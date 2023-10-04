package cn.nukkit.block.blockstate;

import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.BlockProperty;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * @author joserobjr
 */
@Value
@AllArgsConstructor
@Getter
public class BlockStateRepair {
    /**
     * The block ID of the block state that is being repaired.
     */
    int blockId;

    /**
     * The block properties of the block stat that is being repaired.
     */
    @NotNull
    BlockProperties properties;
    
    /**
     * The state that was originally received when the repair started.
     */
    @NotNull
    Number originalState;

    /**
     * The current state that is being repaired.
     */
    @NotNull
    Number currentState;

    /**
     * The state after the repair. It does not consider {@link #getProposedPropertyValue()}.
     */
    @NotNull
    Number nextState;

    /**
     * How many repairs was applied to the original state.
     */
    int repairs;

    /**
     * The property that reported the invalid state, {@code null} if all the properties
     * was validated but the state have more bits to validate.
     */
    @Nullable
    BlockProperty<?> property;

    /**
     * The bit position of the invalid property value, when {@link #getProperty()} is {@code null} this indicates
     * the start index of the {@link #getBrokenPropertyMeta()}.
     */
    int propertyOffset;

    /**
     * The current invalid int value that is in the property bit space. 
     * If the {@link #getProperty()} is {@code null} than it will hold all remaining data that can be stored in an integer
     */
    int brokenPropertyMeta;

    /**
     * The property value that can be set to fix the current block state. It's usually the default property value.
     */
    @NotNull
    Serializable fixedPropertyValue;

    /**
     * The proposed property int value to fix the current block state, 
     * if the proposed value is not valid {@link #getFixedPropertyValue()} will be used.
     */
    @NonFinal
    @NotNull
    Serializable proposedPropertyValue;

    /**
     * The exception that was thrown when trying to validate the {@link #getCurrentState()} and resulted in this repair.
     */
    @Nullable
    InvalidBlockPropertyException validationException;
}
