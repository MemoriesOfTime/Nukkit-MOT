package cn.nukkit.block.blockstate;

import cn.nukkit.api.API;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.BlockProperty;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyException;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyMetaException;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import static cn.nukkit.api.API.Definition.INTERNAL;
import static cn.nukkit.api.API.Usage.INCUBATING;
import static cn.nukkit.block.blockstate.IMutableBlockState.handleUnsupportedStorageType;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@ParametersAreNonnullByDefault
public class BigIntegerMutableBlockState extends MutableBlockState {
    private static final Set<Class<?>> LONG_COMPATIBLE_CLASSES = new HashSet<>(Arrays.asList(
            Long.class, Integer.class, Short.class, Byte.class));
    private BigInteger storage;

    public BigIntegerMutableBlockState(int blockId, BlockProperties properties, BigInteger state) {
        super(blockId, properties);
        this.storage = state;
    }

    public BigIntegerMutableBlockState(int blockId, BlockProperties properties) {
        this(blockId, properties, BigInteger.ZERO);
    }

    @Override
    public void setDataStorage(@Nonnegative Number storage) {
        BigInteger state;
        if (storage instanceof BigInteger) {
            state = (BigInteger) storage;
        } else if (LONG_COMPATIBLE_CLASSES.contains(storage.getClass())) {
            state = BigInteger.valueOf(storage.longValue());
        } else {
            try {
                state = new BigDecimal(storage.toString()).toBigIntegerExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw handleUnsupportedStorageType(getBlockId(), storage, e);
            }
        }
        validate(state);
        this.storage = state;
    }

    @Override
    public void setDataStorageFromInt(@Nonnegative int storage) {
        BigInteger state = BigInteger.valueOf(storage);
        validate(state);
        this.storage = state;
    }

    @Override
    @API(definition = INTERNAL, usage = INCUBATING)
    void setDataStorageWithoutValidation(Number storage) {
        if (storage instanceof BigInteger) {
            this.storage = (BigInteger) storage;
        } else {
            this.storage = new BigInteger(storage.toString());
        }
    }

    @Override
    public void validate() {
        validate(storage);
    }
    
    private void validate(BigInteger state) {
        if (BigInteger.ZERO.equals(state)) {
            return;
        }
        Validation.checkPositive("state", state);
        BlockProperties properties = this.properties;
        int bitLength = NukkitMath.bitLength(state);
        if (bitLength > properties.getBitSize()) {
            throw new InvalidBlockStateException(
                    BlockState.of(getBlockId(), state),
                    "The state have more data bits than specified in the properties. Bits: " + bitLength + ", Max: " + properties.getBitSize()
            );
        }
        
        try {
            for (String name : properties.getNames()) {
                BlockProperty<?> property = properties.getBlockProperty(name);
                property.validateMeta(state, properties.getOffset(name));
            }
        } catch (InvalidBlockPropertyException e) {
            throw new InvalidBlockStateException(BlockState.of(getBlockId(), state), e);
        }
    }

    @Nonnegative
    @NotNull
    @Override
    public BigInteger getHugeDamage() {
        return storage;
    }

    @Nonnegative
    @NotNull
    @Override
    public Number getDataStorage() {
        return getHugeDamage();
    }

    @Override
    public boolean isDefaultState() {
        return storage.equals(BigInteger.ONE);
    }

    @Override
    public void setPropertyValue(String propertyName, @Nullable Serializable value) {
        storage = properties.setValue(storage, propertyName, value);
    }

    @Override
    public void setBooleanValue(String propertyName, boolean value) {
        storage = properties.setValue(storage, propertyName, value);
    }

    @Override
    public void setIntValue(String propertyName, int value) {
        storage = properties.setValue(storage, propertyName, value);
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

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
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
        return storage.intValueExact();
    }

    @NotNull
    @Override
    public BigIntegerMutableBlockState copy() {
        return new BigIntegerMutableBlockState(getBlockId(), properties, storage);
    }
}
