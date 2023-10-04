package cn.nukkit.block.blockstate;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.BlockProperty;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyException;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyMetaException;
import cn.nukkit.block.blockproperty.exception.InvalidBlockPropertyValueException;
import cn.nukkit.block.blockstate.exception.InvalidBlockStateException;
import cn.nukkit.event.blockstate.BlockStateRepairEvent;
import cn.nukkit.event.blockstate.BlockStateRepairFinishEvent;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.HumanStringComparator;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.function.Consumer;

import static cn.nukkit.block.blockstate.Loggers.logIBlockState;

@ParametersAreNonnullByDefault
public interface IBlockState {
    @Nonnegative
    int getBlockId();

    @NotNull
    @Nonnegative
    Number getDataStorage();

    boolean isDefaultState();

    @NotNull
    BlockProperties getProperties();

    @NotNull
    @Nonnegative
    BigInteger getHugeDamage();

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyValueException If the new value is not accepted by the property
     */
    @NotNull
    Serializable getPropertyValue(String propertyName);

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyValueException If the new value is not accepted by the property
     * @throws ClassCastException If the actual property value don't match the type of the given property 
     */
    @NotNull
    default <V extends Serializable> V getPropertyValue(BlockProperty<V> property) {
        return getCheckedPropertyValue(property.getName(), property.getValueClass());
    }

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyValueException If the new value is not accepted by the property
     */
    @NotNull
    default <V extends Serializable> V getUncheckedPropertyValue(BlockProperty<V> property) {
        return getUncheckedPropertyValue(property.getName());
    }

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
    int getIntValue(String propertyName);

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
    default int getIntValue(BlockProperty<?> property) {
        return getIntValue(property.getName());
    }

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     * @throws ClassCastException If the property don't hold boolean values
     */
    boolean getBooleanValue(String propertyName);

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     * @throws ClassCastException If the property don't hold boolean values
     */
    default boolean getBooleanValue(BlockProperty<?> property) {
        return getBooleanValue(property.getName());
    }

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
    @NotNull
    String getPersistenceValue(String propertyName);

    /**
     * @throws NoSuchElementException If the property is not registered
     * @throws InvalidBlockPropertyMetaException If the meta contains invalid data
     */
    @NotNull
    default String getPersistenceValue(BlockProperty<?> property) {
        return getPersistenceValue(property.getName());
    }

    @NotNull
    default String getPersistenceName() {
        return BlockStateRegistry.getMapping(ProtocolInfo.CURRENT_PROTOCOL).getPersistenceName(getBlockId());
    }

    /**
     * Gets a unique persistence identification for this state based on the block properties.
     * <p>If the state holds an invalid meta, the result of {@link #getLegacyStateId()} is returned.</p>
     */
    @NotNull
    default String getStateId() {
        BlockProperties properties = getProperties();
        Map<String, String> propertyMap = new TreeMap<>(HumanStringComparator.getInstance());
        try {
            properties.getNames().forEach(name -> propertyMap.put(properties.getBlockProperty(name).getPersistenceName(), getPersistenceValue(name)));
        } catch (InvalidBlockPropertyException e) {
            logIBlockState.debug("Attempted to get the stateId of an invalid state {}:{}\nProperties: {}", getBlockId(), getDataStorage(), properties, e);
            return getLegacyStateId();
        }

        StringBuilder stateId = new StringBuilder(getPersistenceName());
        propertyMap.forEach((name, value) -> stateId.append(';').append(name).append('=').append(value));
        return stateId.toString();
    }

    default String getMinimalistStateId() {
        if (isDefaultState()) {
            return getPersistenceName();
        }
        BlockProperties properties = getProperties();
        Map<String, String> propertyMap = new TreeMap<>(HumanStringComparator.getInstance());
        try {
            properties.getNames().stream()
                    .map(name -> new SimpleEntry<>(properties.getBlockProperty(name), getPersistenceValue(name)))
                    .filter(entry -> !entry.getKey().isDefaultPersistentValue(entry.getValue()))
                    .forEach(entry -> propertyMap.put(entry.getKey().getPersistenceName(), entry.getValue()));
        } catch (InvalidBlockPropertyException e) {
            logIBlockState.debug("Attempted to get the stateId of an invalid state {}:{}\nProperties: {}", getBlockId(), getDataStorage(), properties, e);
            return getLegacyStateId();
        }

        StringBuilder stateId = new StringBuilder(getPersistenceName());
        propertyMap.forEach((name, value) -> stateId.append(';').append(name).append('=').append(value));
        return stateId.toString();
    }

    @NotNull
    default String getLegacyStateId() {
        return getPersistenceName()+";nukkit-unknown="+getDataStorage();
    }

    @NotNull
    BlockState getCurrentState();

    /**
     * @throws InvalidBlockStateException if the state contains invalid property values
     */
    @NotNull
    default Block getBlock() {
        Block block = Block.get(getBlockId());
        return block.forState(this);
    }

    /**
     * @throws InvalidBlockStateException if the state contains invalid property values
     */
    @NotNull
    default Block getBlock(@Nullable Level level, int x, int y, int z) {
        return getBlock(level, x, y, z, 0, false, null);
    }

    /**
     * @throws InvalidBlockStateException if the state contains invalid property values
     */
    @NotNull
    default Block getBlock(@Nullable Level level, int x, int y, int z, int layer) {
        return getBlock(level, x, y, z, layer, false, null);
    }

    /**
     * @throws InvalidBlockStateException if repair is false and the state contains invalid property values
     */
    @NotNull
    default Block getBlock(@Nullable Level level, int x, int y, int z, int layer, boolean repair) {
        return getBlock(level, x, y, z, layer, repair, null);
    }

    /**
     * @throws InvalidBlockStateException if repair is false and the state contains invalid property values
     */
    @NotNull
    default Block getBlock(@Nullable Level level, int x, int y, int z, int layer, boolean repair, @Nullable Consumer<BlockStateRepair> callback) {
        Block block = Block.get(getBlockId());
        block.level = level;
        block.x = x;
        block.y = y;
        block.z = z;
        block.layer = layer;
        BlockState currentState = getCurrentState();
        try {
            if (currentState.isCachedValidationValid()) {
                return block.forState(currentState);
            }
        } catch (Exception e) {
            logIBlockState.error("Unexpected error while trying to set the cached valid state to the block. State: {}, Block: {}", currentState, block, e);
        }
        
        try {
            block.setDataStorage(currentState.getDataStorage(), repair, callback);
        } catch (InvalidBlockStateException e) {
            throw new InvalidBlockStateException(getCurrentState(), "Invalid block state in layer "+layer+" at: "+new Position(x, y, z, level), e);
        }
        return block;
    }

    /**
     * @throws InvalidBlockStateException if the state contains invalid property values
     */
    @NotNull
    default Block getBlock(Position position) {
        return getBlock(position, 0);
    }

    /**
     * @throws InvalidBlockStateException if the state contains invalid property values
     */
    @NotNull
    default Block getBlock(Block position) {
        return getBlock(position, position.layer);
    }

    /**
     * @throws InvalidBlockStateException if the state contains invalid property values
     */
    @NotNull
    default Block getBlock(Position position, int layer) {
        return getBlock(position.getLevel(), position.getFloorX(), position.getFloorY(), position.getFloorZ(), layer);
    }


    @NotNull
    default Block getBlockRepairing(Block pos) {
        return getBlockRepairing(pos, pos.layer);
    }

    @NotNull
    default Block getBlockRepairing(Position position, int layer) {
        return getBlockRepairing(position.level, position, layer);
    }

    @NotNull
    default Block getBlockRepairing(@Nullable Level level, BlockVector3 pos, int layer) {
        return getBlockRepairing(level, pos.x, pos.y, pos.z, layer);
    }

    @NotNull
    default Block getBlockRepairing(@Nullable Level level, Vector3 pos) {
        return getBlockRepairing(level, pos, 0);
    }

    @NotNull
    default Block getBlockRepairing(@Nullable Level level, Vector3 pos, int layer) {
        return getBlockRepairing(level, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
    }

    @NotNull
    default Block getBlockRepairing(@Nullable Level level, int x, int y, int z) {
        return getBlockRepairing(level, x, y, z, 0);
    }

    @NotNull
    default Block getBlockRepairing(@Nullable Level level, int x, int y, int z, int layer) {
        return getBlockRepairing(level, x, y, z, layer, null);
    }


    @NotNull
    default Block getBlockRepairing(@Nullable Level level, int x, int y, int z, int layer, @Nullable Consumer<BlockStateRepair> callback) {
        List<BlockStateRepair> repairs = new ArrayList<>(0);
        
        Consumer<BlockStateRepair> callbackChain = repairs::add;

        if (!BlockStateRepairEvent.getHandlers().isEmpty()) {
            PluginManager manager = Server.getInstance().getPluginManager();
            callbackChain = callbackChain.andThen(repair -> manager.callEvent(new BlockStateRepairEvent(repair)));
        }
        
        if (callback != null) {
            callbackChain = callbackChain.andThen(callback);
        }
        
        Block block = getBlock(level, x, y, z, layer, true, callbackChain);
        
        if (!BlockStateRepairFinishEvent.getHandlers().isEmpty()) {
            BlockStateRepairFinishEvent event = new BlockStateRepairFinishEvent(repairs, block);
            Server.getInstance().getPluginManager().callEvent(event);
            block = event.getResult();
        }

        if (!repairs.isEmpty() && logIBlockState.isDebugEnabled()) {
            logIBlockState.debug("The block that at Level:{}, X:{}, Y:{}, Z:{}, L:{} was repaired. Result: {}, Repairs: {}",
                    level, x, y, z, layer, block, repairs,
                    new Exception("Stacktrace")
            );
        }
        
        return block;
    }

    default int getRuntimeId() {
        Server.mvw("IBlockState#getRuntimeId()");
        return this.getRuntimeId(ProtocolInfo.CURRENT_PROTOCOL);
    }

    default int getRuntimeId(int protocolId) {
        return BlockStateRegistry.getMapping(protocolId).getRuntimeId(getCurrentState());
    }

    @SuppressWarnings("rawtypes")
    @NotNull
    default BlockProperty getProperty(String propertyName) {
        return getProperties().getBlockProperty(propertyName);
    }

    @NotNull
    default <T extends BlockProperty<?>> T getCheckedProperty(String propertyName, Class<T> tClass) {
        return getProperties().getBlockProperty(propertyName, tClass);
    }

    @NotNull
    default Set<String> getPropertyNames() {
        return getProperties().getNames();
    }

    @NotNull
    default <T> T getCheckedPropertyValue(String propertyName, Class<T> tClass) {
        return tClass.cast(getPropertyValue(propertyName));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    default <T> T getUncheckedPropertyValue(String propertyName) {
        return (T) getPropertyValue(propertyName);
    }

    default int getBitSize() {
        return getProperties().getBitSize();
    }

    @Nonnegative
    int getExactIntStorage();

    @NotNull
    default ItemBlock asItemBlock() {
        return asItemBlock(1);
    }

    @NotNull
    default ItemBlock asItemBlock(int count) {
        return getCurrentState().asItemBlock(count);
    }
}
