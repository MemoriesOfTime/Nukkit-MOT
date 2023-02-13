package cn.nukkit.entity.custom;

import cn.nukkit.entity.Entity;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.ToString;

import java.util.concurrent.atomic.AtomicInteger;

@ToString
public class EntityDefinition {
    public static final AtomicInteger ID_ALLOCATOR = new AtomicInteger(10000);
    private final String identifier;
    private final String parentEntity;
    private final boolean spawnEgg;
    private final String alternateName;
    private final Class<? extends Entity> clazz;
    private final int runtimeId;
    private CompoundTag networkTag;
    private CompoundTag networkTagOld;

    public EntityDefinition(String identifier, String parentEntity, boolean spawnEgg, String alternateName, Class<? extends Entity> clazz) {
        if (!CustomEntity.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Implementation class must implement CustomEntity interface");
        }
        this.identifier = identifier;
        this.parentEntity = parentEntity;
        this.spawnEgg = spawnEgg;
        this.alternateName = alternateName;
        this.clazz = clazz;
        this.runtimeId = ID_ALLOCATOR.getAndIncrement();
    }

    private CompoundTag createNetworkTag() {
        CompoundTag compoundTag = new CompoundTag("");
        compoundTag.putBoolean("hasspawnegg", this.spawnEgg);
        compoundTag.putBoolean("summonable", true);
        compoundTag.putString("id", this.identifier);
        compoundTag.putString("bid", this.parentEntity == null ? "" : this.parentEntity);
        compoundTag.putInt("rid", this.runtimeId);
        return compoundTag;
    }

    public CompoundTag getNetworkTag() {
        if (this.networkTag == null) {
            this.networkTag = this.createNetworkTag();
        }
        return this.networkTag;
    }

    public CompoundTag getNetworkTagOld() {
        if (this.networkTagOld == null) {
            this.networkTagOld = this.createNetworkTag();
            this.networkTagOld.putBoolean("experimental", false);
        }
        return this.networkTagOld;
    }

    public static EntityDefinitionBuilder builder() {
        return new EntityDefinitionBuilder();
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public String getParentEntity() {
        return this.parentEntity;
    }

    public boolean isSpawnEgg() {
        return this.spawnEgg;
    }

    public String getAlternateName() {
        return this.alternateName;
    }

    public Class<? extends Entity> getImplementation() {
        return this.clazz;
    }

    public int getRuntimeId() {
        return this.runtimeId;
    }

    private static IllegalArgumentException a(IllegalArgumentException illegalArgumentException) {
        return illegalArgumentException;
    }

    @ToString
    public static class EntityDefinitionBuilder {
        private String identifier;
        private String parentEntity;
        private boolean spawnEgg;
        private String alternateName;
        private Class<? extends Entity> clazz;

        public EntityDefinitionBuilder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public EntityDefinitionBuilder parentEntity(String parentEntity) {
            this.parentEntity = parentEntity;
            return this;
        }

        public EntityDefinitionBuilder spawnEgg(boolean spawnEgg) {
            this.spawnEgg = spawnEgg;
            return this;
        }

        public EntityDefinitionBuilder alternateName(String alternateName) {
            this.alternateName = alternateName;
            return this;
        }

        public EntityDefinitionBuilder implementation(Class<? extends Entity> clazz) {
            this.clazz = clazz;
            return this;
        }

        public EntityDefinition build() {
            return new EntityDefinition(this.identifier, this.parentEntity, this.spawnEgg, this.alternateName, this.clazz);
        }
    }
}

