package cn.nukkit.block.custom.container;

import cn.nukkit.block.Block;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.block.custom.properties.RegisteredBlockProperty;
import cn.nukkit.level.format.leveldb.LevelDBConstants;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

import java.io.Serializable;

public interface BlockStorageContainer extends BlockContainer {

    int getStorage();

    void setStorage(int damage);

    BlockProperties getBlockProperties();

    @Override
    default int getNukkitDamage() {
        return getStorage() & Block.DATA_MASK;
    }

    default void setBooleanValue(BlockProperty<Boolean> property, boolean value) {
        this.setBooleanValue(property.getName(), value);
    }

    default void setBooleanValue(String propertyName, boolean value) {
        this.setStorage(this.getBlockProperties().setBooleanValue(this.getStorage(), propertyName, value));
    }

    default void setPropertyValue(String propertyName, Serializable value) {
        this.setStorage(this.getBlockProperties().setValue(this.getStorage(), propertyName, value));
    }

    default <T extends Serializable> void setPropertyValue(BlockProperty<T> property, T value) {
        this.setPropertyValue(property.getName(), value);
    }

    default void setIntValue(BlockProperty<Integer> property, int value) {
        this.setIntValue(property.getName(), value);
    }

    default void setIntValue(String propertyName, int value) {
        this.setStorage(this.getBlockProperties().setIntValue(this.getStorage(), propertyName, value));
    }

    default Serializable getPropertyValue(String propertyName) {
        return this.getBlockProperties().getValue(this.getStorage(), propertyName);
    }

    default <V extends Serializable> V getPropertyValue(BlockProperty<V> property) {
        return this.getCheckedPropertyValue(property.getName(), property.getValueClass());
    }

    default <T> T getCheckedPropertyValue(String propertyName, Class<T> tClass) {
        return tClass.cast(this.getPropertyValue(propertyName));
    }

    default int getIntValue(BlockProperty<Integer> property) {
        return this.getIntValue(property.getName());
    }

    default int getIntValue(String propertyName) {
        return this.getBlockProperties().getIntValue(this.getStorage(), propertyName);
    }

    default boolean getBooleanValue(BlockProperty<Boolean> property) {
        return this.getBooleanValue(property.getName());
    }

    default boolean getBooleanValue(String propertyName) {
        return this.getBlockProperties().getBooleanValue(this.getStorage(), propertyName);
    }

    default void setStorageFromItem(int itemMeta) {
        BlockProperties properties = this.getBlockProperties();
        BlockProperties itemProperties = properties.getItemBlockProperties();
        if (itemProperties.equals(properties)) {
            this.setStorage(itemMeta);
            return;
        }

        int damage = 0;
        for (String propertyName : itemProperties.getItemPropertyNames()) {
            damage = properties.setValue(damage, propertyName, itemProperties.getValue(itemMeta, propertyName));
        }
        this.setStorage(damage);
    }

    default NbtMap getStateNbt() {
        NbtMapBuilder states = NbtMap.builder();
        for (RegisteredBlockProperty property : this.getBlockProperties().getAllProperties()) {
            if (property.getProperty() instanceof EnumBlockProperty<?>) {
                states.put(property.getProperty().getName(), this.getPropertyValue(property.getProperty()).toString());
            } else {
                states.put(property.getProperty().getName(), this.getPropertyValue(property.getProperty()));
            }
        }
        return NbtMap.builder()
                .putString("name", this.getIdentifier())
                .putCompound("states", states.build())
                .putInt("version", LevelDBConstants.STATE_VERSION)
                .build();
    }
}
