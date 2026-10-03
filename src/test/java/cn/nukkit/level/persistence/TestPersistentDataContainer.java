package cn.nukkit.level.persistence;

import cn.nukkit.nbt.tag.CompoundTag;

public final class TestPersistentDataContainer implements PersistentDataContainer {

    private CompoundTag storage;

    public TestPersistentDataContainer() {
        this(new CompoundTag());
    }

    private TestPersistentDataContainer(CompoundTag storage) {
        this.storage = storage;
    }

    public TestPersistentDataContainer copy() {
        return new TestPersistentDataContainer(this.storage.clone());
    }

    @Override
    public CompoundTag getStorage() {
        return storage;
    }

    @Override
    public void setStorage(CompoundTag storage) {
        this.storage = storage;
    }
}
