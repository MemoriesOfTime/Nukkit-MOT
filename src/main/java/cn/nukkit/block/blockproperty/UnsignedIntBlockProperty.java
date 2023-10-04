package cn.nukkit.block.blockproperty;

import cn.nukkit.math.NukkitMath;
import cn.nukkit.utils.exception.InvalidBlockPropertyMetaException;
import cn.nukkit.utils.exception.InvalidBlockPropertyPersistenceValueException;
import cn.nukkit.utils.exception.InvalidBlockPropertyValueException;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.Serial;

public class UnsignedIntBlockProperty extends BlockProperty<Integer> {
    @Serial
    private static final long serialVersionUID = 7896101036099245755L;
    
    private final long minValue;
    private final long maxValue;

    public UnsignedIntBlockProperty(String name, boolean exportedToItem, int maxValue, int minValue, int bitSize, String persistenceName) {
        super(name, exportedToItem, bitSize, persistenceName);
        long unsignedMinValue = removeSign(minValue);
        long unsignedMaxValue = removeSign(maxValue);
        long delta = unsignedMaxValue - unsignedMinValue;
        Preconditions.checkArgument(delta > 0, "maxValue must be higher than minValue. Got min:%s and max:%s", unsignedMinValue, unsignedMaxValue);
        
        long mask = removeSign(-1 >>> (32 - bitSize));
        Preconditions.checkArgument(delta <= mask, "The data range from %s to %s can't be stored in %s bits", unsignedMinValue, unsignedMaxValue, bitSize);
        
        this.minValue = unsignedMinValue;
        this.maxValue = unsignedMaxValue;
    }

    public UnsignedIntBlockProperty(String name, boolean exportedToItem, int maxValue, int minValue, int bitSize) {
        this(name, exportedToItem, maxValue, minValue, bitSize, name);
    }

    public UnsignedIntBlockProperty(String name, boolean exportedToItem, int maxValue, int minValue) {
        this(name, exportedToItem, maxValue, minValue, NukkitMath.bitLength(maxValue - minValue));
    }

    public UnsignedIntBlockProperty(String name, boolean exportedToItem, int maxValue) {
        this(name, exportedToItem, maxValue, 0);
    }

    @Override
    public UnsignedIntBlockProperty copy() {
        return new UnsignedIntBlockProperty(getName(), isExportedToItem(), (int)getMaxValue(), (int)getMinValue(), getBitSize(), getPersistenceName());
    }

    @Override
    public UnsignedIntBlockProperty exportingToItems(boolean exportedToItem) {
        return new UnsignedIntBlockProperty(getName(), exportedToItem, (int)getMaxValue(), (int)getMinValue(), getBitSize(), getPersistenceName());
    }

    private static long removeSign(int value) {
        return (long)value & 0xFFFFFFFFL;
    }
    
    private static int addSign(long value) {
        return (int)(value & 0xFFFFFFFFL);
    }

    @Override
    public int getMetaForValue(@Nullable Integer value) {
        if (value == null) {
            return 0;
        }
        long unsigned = removeSign(value);
        try {
            validateDirectly(unsigned);
        } catch (IllegalArgumentException e) {
            throw new InvalidBlockPropertyValueException(this, null, value, e);
        }
        return (int) (unsigned - minValue);
    }

    @NotNull
    @Override
    public Integer getValueForMeta(int meta) {
        return getIntValueForMeta(meta);
    }

    @Override
    public int getIntValueForMeta(int meta) {
        try {
            validateMetaDirectly(meta);
        } catch (IllegalArgumentException e) {
            throw new InvalidBlockPropertyMetaException(this, meta, meta, e);
        }
        return (int) (minValue + meta);
    }

    @Override
    public String getPersistenceValueForMeta(int meta) {
        return String.valueOf(removeSign(getIntValueForMeta(meta)));
    }

    @Override
    public int getMetaForPersistenceValue(@NotNull String persistenceValue) {
        try {
            return getMetaForValue(addSign(Long.parseLong(persistenceValue)));
        } catch (NumberFormatException | InvalidBlockPropertyValueException e) {
            throw new InvalidBlockPropertyPersistenceValueException(this, null, persistenceValue, e);
        }
    }

    @Override
    protected void validateDirectly(@Nullable Integer value) {
        if (value == null) {
            return;
        }
        validateDirectly(removeSign(value));
    }

    /**
     * @throws RuntimeException Any runtime exception to indicate an invalid value
     */
    private void validateDirectly(long unsigned) {
        Preconditions.checkArgument(unsigned >= minValue, "New value (%s) must be higher or equals to %s", unsigned, minValue);
        Preconditions.checkArgument(maxValue >= unsigned, "New value (%s) must be less or equals to %s", unsigned, maxValue);
    }

    @Override
    protected void validateMetaDirectly(int meta) {
        long max = maxValue - minValue;
        Preconditions.checkArgument(0 <= meta && meta <= max, "The meta %s is outside the range of 0 .. ", meta, max);
    }

    @NotNull
    @Override
    public Class<Integer> getValueClass() {
        return Integer.class;
    }

    public long getMaxValue() {
        return maxValue;
    }

    public long getMinValue() {
        return minValue;
    }

    @Override
    @NotNull
    public Integer getDefaultValue() {
        return (int)minValue;
    }

    @Override
    public boolean isDefaultValue(@Nullable Integer value) {
        return value == null || removeSign(value)==minValue;
    }

    @Override
    public boolean isDefaultIntValue(int value) {
        return removeSign(value)==minValue;
    }

    @Override
    public int getDefaultIntValue() {
        return (int)minValue;
    }
}
