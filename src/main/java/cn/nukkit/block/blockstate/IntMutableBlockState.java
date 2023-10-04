package cn.nukkit.block.blockstate;

import cn.nukkit.api.API;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.BlockProperty;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.utils.Validation;
import cn.nukkit.utils.exception.InvalidBlockPropertyException;
import cn.nukkit.utils.exception.InvalidBlockStateException;
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

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@ParametersAreNonnullByDefault
public class IntMutableBlockState extends MutableBlockState {
    private int storage;

    public IntMutableBlockState(int blockId, BlockProperties properties, int state) {
        super(blockId, properties);
        this.storage = state;
    }

    public IntMutableBlockState(int blockId, BlockProperties properties) {
        this(blockId, properties, 0);
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
    public Integer getDataStorage() {
        return storage;
    }

    @Override
    public boolean isDefaultState() {
        return storage == 0;
    }

    @Override
    public void setDataStorage(@Nonnegative Number storage) {
        Class<? extends Number> c = storage.getClass();
        int state;
        if (c == Integer.class || c == Short.class || c == Byte.class) {
            state = storage.intValue();
        } else {
            try {
                state = new BigDecimal(storage.toString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw handleUnsupportedStorageType(getBlockId(), storage, e);
            }
        }
        setDataStorageFromInt(state);
    }

    @Override
    public void setDataStorageFromInt(@Nonnegative int storage) {
        validate(storage);
        this.storage = storage;
    }

    @Override
    @API(definition = INTERNAL, usage = INCUBATING)
    void setDataStorageWithoutValidation(Number storage) {
        this.storage = storage.intValue();
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
        storage = properties.setBooleanValue(storage, propertyName, value);
    }

    @Override
    public void setPropertyValue(String propertyName, @Nullable Serializable value) {
        storage = properties.setValue(storage, propertyName, value);
    }

    @Override
    public void setIntValue(String propertyName, int value) {
        storage = properties.setIntValue(storage, propertyName, value);
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
    public IntMutableBlockState copy() {
        return new IntMutableBlockState(blockId, properties, storage);
    }
}
