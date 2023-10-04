package cn.nukkit.block.blockstate;

import cn.nukkit.api.API;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockstate.exception.InvalidBlockStateException;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnegative;
import javax.annotation.ParametersAreNonnullByDefault;

import static cn.nukkit.api.API.Definition.INTERNAL;
import static cn.nukkit.api.API.Usage.INCUBATING;

@ToString
@EqualsAndHashCode
@ParametersAreNonnullByDefault
public abstract class MutableBlockState implements IMutableBlockState {
    protected final int blockId;

    protected final BlockProperties properties;

    MutableBlockState(int blockId, BlockProperties properties) {
        this.blockId = blockId;
        this.properties = properties;
    }

    @API(definition = INTERNAL, usage = INCUBATING)
    void setDataStorageWithoutValidation(Number storage) {
        setDataStorage(storage);
    }

    @Override
    public void setState(IBlockState state) throws InvalidBlockStateException {
        if (state.getBlockId() == getBlockId()) {
            if (BlockState.class == state.getClass() && ((BlockState) state).isCachedValidationValid()) {
                setDataStorageWithoutValidation(state.getDataStorage());
            } else {
                setDataStorage(state.getDataStorage());
            }
        } else {
            IMutableBlockState.super.setState(state);
        }
    }

    @NotNull
    @Override
    public final BlockProperties getProperties() {
        return properties;
    }

    @Nonnegative
    @Override
    public final int getBlockId() {
        return blockId;
    }

    @Override
    public final int getBitSize() {
        return getProperties().getBitSize();
    }

    /**
     * @throws InvalidBlockStateException if the state is invalid
     */
    public abstract void validate();

    @NotNull
    public abstract MutableBlockState copy();
}
