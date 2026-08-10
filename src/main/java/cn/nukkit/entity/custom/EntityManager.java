package cn.nukkit.entity.custom;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public class EntityManager {
    private static final EntityManager ENTITY_MANAGER = new EntityManager();
    private final Map<String, EntityDefinition> identifierToDefinition = new HashMap<>();
    private final Map<String, EntityDefinition> alternateNameToDefinition = new HashMap<>();
    private final Int2ObjectMap<EntityDefinition> runtimeIdToDefinition = new Int2ObjectOpenHashMap<>();
    private final Map<String, Integer> legacy_ids = new HashMap<>();
    private final Int2ObjectMap<byte[]> networkTagCache = new Int2ObjectOpenHashMap<>();

    public static EntityManager get() {
        return ENTITY_MANAGER;
    }

    public EntityManager() {
        for (Map.Entry<Integer, String> entry : Entity.getEntityRuntimeMapping().entrySet()) {
            legacy_ids.put(entry.getValue(), entry.getKey());
        }
    }

    public void registerDefinition(EntityDefinition entityDefinition) {
        if (this.identifierToDefinition.containsKey(entityDefinition.getIdentifier())) {
            throw new IllegalArgumentException("Custom entity " + entityDefinition.getIdentifier() + " was already registered");
        }

        if (!Entity.hasDefaultConstructor(entityDefinition.getImplementation())) {
            Server.getInstance().getLogger().error("Custom entity \"" + entityDefinition.getIdentifier()
                    + "\" (" + entityDefinition.getImplementation().getName()
                    + ") does not expose a (FullChunk, CompoundTag) constructor. "
                    + "Nukkit-MOT cannot process this entity.",
                    new RuntimeException("Custom entity without (FullChunk, CompoundTag) constructor"));
            return;
        }

        this.identifierToDefinition.put(entityDefinition.getIdentifier(), entityDefinition);
        this.runtimeIdToDefinition.put(entityDefinition.getRuntimeId(), entityDefinition);
        if (entityDefinition.getAlternateName() != null && !entityDefinition.getAlternateName().trim().isEmpty()) {
            this.alternateNameToDefinition.put(entityDefinition.getAlternateName(), entityDefinition);
        }

        this.networkTagCache.clear();
    }

    public EntityDefinition getDefinition(String string) {
        EntityDefinition entityDefinition = this.identifierToDefinition.get(string);
        if (entityDefinition == null) {
            entityDefinition = this.alternateNameToDefinition.get(string);
        }
        return entityDefinition;
    }

    public EntityDefinition getDefinition(int runtimeId) {
        return this.runtimeIdToDefinition.get(runtimeId);
    }

    public int getRuntimeId(String identifier) {
        EntityDefinition entityDefinition = this.identifierToDefinition.get(identifier);
        if (entityDefinition == null) {
            return this.legacy_ids.getOrDefault(identifier, 0);
        }
        return entityDefinition.getRuntimeId();
    }

    private byte[] createNetworkTag(int protocol) {
        try {
            CompoundTag compoundTag = (CompoundTag) NBTIO.readNetwork(
                    new ByteArrayInputStream(Entity.getEntityIdentifiersCache(protocol)));
            ListTag<CompoundTag> listTag = compoundTag.getList("idlist", CompoundTag.class);
            for (EntityDefinition entityDefinition : this.identifierToDefinition.values()) {
                listTag.add(protocol <= 407 ? entityDefinition.getNetworkTagOld() : entityDefinition.getNetworkTag());
            }
            compoundTag.putList(listTag);
            return NBTIO.writeNetwork(compoundTag);
        } catch (Exception e) {
            throw new RuntimeException("Unable to init entityIdentifiers", e);
        }
    }

    /**
     * Vanilla identifier list of that very protocol plus every registered custom entity.
     */
    public byte[] getNetworkTagCached(int protocol) {
        byte[] cached = this.networkTagCache.get(protocol);
        if (cached == null) {
            cached = this.createNetworkTag(protocol);
            this.networkTagCache.put(protocol, cached);
        }
        return cached;
    }

    public byte[] getNetworkTagCached() {
        return this.getNetworkTagCached(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public byte[] getNetworkTagCachedOld() {
        return this.getNetworkTagCached(407);
    }

    public boolean hasCustomEntities() {
        return !this.identifierToDefinition.isEmpty();
    }

}

