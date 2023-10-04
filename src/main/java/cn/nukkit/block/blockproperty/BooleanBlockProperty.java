package cn.nukkit.block.blockproperty;

import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyMetaException;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyPersistenceValueException;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.math.BigInteger;

public final class BooleanBlockProperty extends BlockProperty<Boolean> {
    private static final long serialVersionUID = 8249827149092664486L;
    
    public BooleanBlockProperty(String name, boolean exportedToItem, String persistenceName) {
        super(name, exportedToItem, 1, persistenceName);
    }

    public BooleanBlockProperty(String name, boolean exportedToItem) {
        super(name, exportedToItem, 1, name);
    }

    @Override
    public BooleanBlockProperty copy() {
        return new BooleanBlockProperty(getName(), isExportedToItem(), getPersistenceName());
    }

    @Override
    public BooleanBlockProperty exportingToItems(boolean exportedToItem) {
        return new BooleanBlockProperty(getName(), exportedToItem, getPersistenceName());
    }

    @Override
    public int setValue(int currentMeta, int bitOffset, @Nullable Boolean newValue) {
        boolean value = newValue != null && newValue;
        return setValue(currentMeta, bitOffset, value);
    }

    @Override
    public long setValue(long currentBigMeta, int bitOffset, @Nullable Boolean newValue) {
        boolean value = newValue != null && newValue;
        return setValue(currentBigMeta, bitOffset, value);
    }

    public int setValue(int currentMeta, int bitOffset, boolean newValue) {
        int mask = 1 << bitOffset;
        return newValue? (currentMeta | mask) : (currentMeta & ~mask);
    }

    public long setValue(long currentMeta, int bitOffset, boolean newValue) {
        long mask = 1L << bitOffset;
        return newValue? (currentMeta | mask) : (currentMeta & ~mask);
    }

    @NotNull
    @Override
    public Boolean getValue(int currentMeta, int bitOffset) {
        return getBooleanValue(currentMeta, bitOffset);
    }

    @NotNull
    @Override
    public Boolean getValue(long currentBigMeta, int bitOffset) {
        return getBooleanValue(currentBigMeta, bitOffset);
    }

    public boolean getBooleanValue(int currentMeta, int bitOffset) {
        int mask = 1 << bitOffset;
        return (currentMeta & mask) == mask;
    }

    public boolean getBooleanValue(long currentBigMeta, int bitOffset) {
        long mask = 1L << bitOffset;
        return (currentBigMeta & mask) == mask;
    }

    public boolean getBooleanValue(BigInteger currentHugeData, int bitOffset) {
        BigInteger mask = BigInteger.ONE.shiftLeft(bitOffset);
        return mask.equals(currentHugeData.and(mask));
    }

    @Override
    public int getIntValue(int currentMeta, int bitOffset) {
        return getBooleanValue(currentMeta, bitOffset)? 1 : 0;
    }

    /**
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
    @Override
    public int getIntValueForMeta(int meta) {
        if (meta == 1 || meta == 0) {
            return meta;
        }
        throw new InvalidBlockPropertyMetaException(this, meta, meta, "Only 1 or 0 was expected");
    }

    @Override
    public int getMetaForValue(@Nullable Boolean value) {
        return Boolean.TRUE.equals(value)? 1 : 0;
    }

    /**
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
    @NotNull
    @Override
    public Boolean getValueForMeta(int meta) {
        return getBooleanValueForMeta(meta);
    }

    /**
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
    public boolean getBooleanValueForMeta(int meta) {
        if (meta == 0) {
            return false;
        } else if (meta == 1) {
            return true;
        } else {
            throw new InvalidBlockPropertyMetaException(this, meta, meta, "Only 1 or 0 was expected");
        }
    }

    @Override
    @NotNull
    public Boolean getDefaultValue() {
        return Boolean.FALSE;
    }

    @Override
    public boolean isDefaultValue(@Nullable Boolean value) {
        return value == null || Boolean.FALSE.equals(value);
    }

    @Override
    protected void validateMetaDirectly(int meta) {
        Preconditions.checkArgument(meta == 1 || meta == 0, "Must be 1 or 0");
    }

    @NotNull
    @Override
    public Class<Boolean> getValueClass() {
        return Boolean.class;
    }

    @Override
    public String getPersistenceValueForMeta(int meta) {
        if (meta == 1) {
            return "1";
        } else if (meta == 0) {
            return "0";
        } else {
            throw new InvalidBlockPropertyMetaException(this, meta, meta, "Only 1 or 0 was expected");
        }
    }

    @Override
    public int getMetaForPersistenceValue(@NotNull String persistenceValue) {
        if ("1".equals(persistenceValue)) {
            return 1;
        } else if ("0".equals(persistenceValue)) {
            return 0;
        } else {
            throw new InvalidBlockPropertyPersistenceValueException(this, null, persistenceValue, "Only 1 or 0 was expected");
        }
    }
}
