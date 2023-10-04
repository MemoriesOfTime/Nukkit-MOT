
package cn.nukkit.block.blockstate;

import cn.nukkit.api.API;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.BlockProperty;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyException;
import cn.nukkit.block.blockstate.exception.InvalidBlockStateException;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.utils.Validation;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;

import static cn.nukkit.api.API.Definition.INTERNAL;
import static cn.nukkit.api.API.Usage.INCUBATING;
import static cn.nukkit.block.blockstate.IMutableBlockState.handleUnsupportedStorageType;

/**
 * @author joserobjr
 * @since 2020-10-03
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@ParametersAreNonnullByDefault
public class ByteMutableBlockState extends MutableBlockState {
    private byte storage;

    public ByteMutableBlockState(int blockId, BlockProperties properties, byte state) {
        super(blockId, properties);
        this.storage = state;
    }

    public ByteMutableBlockState(int blockId, BlockProperties properties) {
        this(blockId, properties, (byte)0);
    }

    @Nonnegative
    @NotNull
    @Override
    public BigInteger getHugeDamage() {
        return BigInteger.valueOf(storage);
    }

    @Nonnegative
    @NotNull
    @Override
    public Byte getDataStorage() {
        return storage;
    }

    @Override
    public boolean isDefaultState() {
        return storage == 0;
    }

    @Override
    public void setDataStorage(@Nonnegative Number storage) {
        Class<? extends Number> c = storage.getClass();
        byte state;
        if (c == Byte.class) {
            state = storage.byteValue();
        } else {
            try {
                state = new BigDecimal(storage.toString()).byteValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw handleUnsupportedStorageType(getBlockId(), storage, e);
            }
        }
        validate();
        setDataStorageFromInt(state);
    }

    @Override
    public void setDataStorageFromInt(@Nonnegative int storage) {
        validate(storage);
        this.storage = (byte)storage;
    }

    @Override
    @API(definition = INTERNAL, usage = INCUBATING)
    void setDataStorageWithoutValidation(Number storage) {
        this.storage = storage.byteValue();
    }

    @Override
    public void validate() {
        validate(storage);
    }
    
    private void validate(int state) {
        if (state == 0) {
            return;
        }

        Validation.checkPositive("state", state);
        
        if (state < 0 || state > Byte.MAX_VALUE) {
            throw new InvalidBlockStateException(BlockState.of(getBlockId(), state), 
                    "The state have more bits than the storage space. Storage: Byte, Property Bits: "+properties.getBitSize());
        }
        
        int bitLength = NukkitMath.bitLength(state);
        if (bitLength > properties.getBitSize()) {
            throw new InvalidBlockStateException(
                    BlockState.of(getBlockId(), state),
                    "The state have more data bits than specified in the properties. Bits: " + bitLength + ", Max: " + properties.getBitSize()
            );
        }

        try {
            BlockProperties properties = this.properties;
            for (String name : properties.getNames()) {
                BlockProperty<?> property = properties.getBlockProperty(name);
                property.validateMeta(state, properties.getOffset(name));
            }
        } catch (InvalidBlockPropertyException e) {
            throw new InvalidBlockStateException(BlockState.of(getBlockId(), state), e);
        }
    }

    @Override
    public void setBooleanValue(String propertyName, boolean value) {
        storage = (byte)properties.setBooleanValue(storage, propertyName, value);
    }

    @Override
    public void setPropertyValue(String propertyName, @Nullable Serializable value) {
        storage = (byte)properties.setValue(storage, propertyName, value);
    }

    @Override
    public void setIntValue(String propertyName, int value) {
        storage = (byte)properties.setIntValue(storage, propertyName, value);
    }

    @NotNull
    @Override
    public Serializable getPropertyValue(String propertyName) {
        return properties.getValue(storage, propertyName);
    }

    @Override
    public int getIntValue(String propertyName) {
        return properties.getIntValue(storage, propertyName);
    }

    @Override
    public boolean getBooleanValue(String propertyName) {
        return properties.getBooleanValue(storage, propertyName);
    }

    @NotNull
    @Override
    public String getPersistenceValue(String propertyName) {
        return properties.getPersistenceValue(storage, propertyName);
    }

    @NotNull
    @Override
    public BlockState getCurrentState() {
        return BlockState.of(blockId, storage);
    }

    @Override
    public int getExactIntStorage() {
        return storage;
    }

    @NotNull
    @Override
    public ByteMutableBlockState copy() {
        return new ByteMutableBlockState(blockId, properties, storage);
    }
}
