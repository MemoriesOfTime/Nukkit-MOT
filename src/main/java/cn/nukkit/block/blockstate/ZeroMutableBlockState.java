
package cn.nukkit.block.blockstate;

import cn.nukkit.block.blockproperty.BlockProperties;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.NoSuchElementException;

import static cn.nukkit.block.blockstate.IMutableBlockState.handleUnsupportedStorageType;

/**
 * @author joserobjr
 * @since 2020-10-03
 */
@ParametersAreNonnullByDefault
public class ZeroMutableBlockState extends MutableBlockState {
    private final BlockState state;

    public ZeroMutableBlockState(int blockId, BlockProperties properties) {
        super(blockId, properties);
        state = BlockState.of(blockId);
    }

    @Override
    public void validate() {
    }

    @NotNull
    @Override
    public ZeroMutableBlockState copy() {
        return this;
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
        if (state != 0) {
            throw handleUnsupportedStorageType(getBlockId(), storage, new ArithmeticException("ZeroMutableBlockState only accepts zero"));
        }
    }

    @Override
    public void setDataStorageFromInt(@Nonnegative int storage) {
        if (storage != 0) {
            throw handleUnsupportedStorageType(getBlockId(), storage, new ArithmeticException("ZeroMutableBlockState only accepts zero"));
        }
    }

    @Override
    public void setPropertyValue(String propertyName, @Nullable Serializable value) {
        throw new NoSuchElementException("ZeroMutableBlockState can't have properties. Attempted to set "+propertyName+" to "+value);
    }

    @Override
    public void setBooleanValue(String propertyName, boolean value) {
        throw new NoSuchElementException("ZeroMutableBlockState can't have properties. Attempted to set "+propertyName+" to "+value);
    }

    @Override
    public void setIntValue(String propertyName, int value) {
        throw new NoSuchElementException("ZeroMutableBlockState can't have properties. Attempted to set "+propertyName+" to "+value);
    }

    @Nonnegative
    @NotNull
    @Override
    public Number getDataStorage() {
        return 0;
    }

    @Override
    public boolean isDefaultState() {
        return true;
    }

    @Nonnegative
    @NotNull
    @Override
    public BigInteger getHugeDamage() {
        return BigInteger.ZERO;
    }

    @NotNull
    @Override
    public Serializable getPropertyValue(String propertyName) {
        throw new NoSuchElementException("ZeroMutableBlockState can't have properties. Attempted get property "+propertyName);
    }

    @Override
    public int getIntValue(String propertyName) {
        throw new NoSuchElementException("ZeroMutableBlockState can't have properties. Attempted get property "+propertyName);
    }

    @Override
    public boolean getBooleanValue(String propertyName) {
        throw new NoSuchElementException("ZeroMutableBlockState can't have properties. Attempted get property "+propertyName);
    }

    @NotNull
    @Override
    public String getPersistenceValue(String propertyName) {
        throw new NoSuchElementException("ZeroMutableBlockState can't have properties. Attempted get property "+propertyName);
    }

    @NotNull
    @Override
    public BlockState getCurrentState() {
        return state;
    }

    @Override
    public int getExactIntStorage() {
        return 0;
    }
}
