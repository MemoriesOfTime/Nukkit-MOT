package cn.nukkit.entity.custom;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.AvailableEntityIdentifiersPacket;
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
    private byte[] networkTagCached;
    private byte[] networkTagCachedOld;

    public static EntityManager get() {
        return ENTITY_MANAGER;
    }

    public EntityManager() {
        for (Map.Entry<Integer, String> entry : Entity.getEntityRuntimeMapping(ProtocolInfo.CURRENT_PROTOCOL).entrySet()) {
            legacy_ids.put(entry.getValue(), entry.getKey());
        }
    }

    public void registerDefinition(EntityDefinition entityDefinition) {
        if (!Server.getInstance().enableExperimentMode) {
            Server.getInstance().getLogger().warning("The server does not have the experiment mode feature enabled. Unable to register custom entity!");
            return;
        }

        if (this.identifierToDefinition.containsKey(entityDefinition.getIdentifier())) {
            throw new IllegalArgumentException("Custom entity " + entityDefinition.getIdentifier() + " was already registered");
        }
        this.identifierToDefinition.put(entityDefinition.getIdentifier(), entityDefinition);
        this.runtimeIdToDefinition.put(entityDefinition.getRuntimeId(), entityDefinition);
        if (entityDefinition.getAlternateName() != null && !entityDefinition.getAlternateName().trim().isEmpty()) {
            this.alternateNameToDefinition.put(entityDefinition.getAlternateName(), entityDefinition);
        }

        this.networkTagCachedOld = null;
        this.networkTagCached = null;
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

    private void createNetworkTagCached(int protocol) {
        try {
            CompoundTag compoundTag = (CompoundTag)NBTIO.readNetwork(new ByteArrayInputStream(AvailableEntityIdentifiersPacket.TAG));
            ListTag<CompoundTag> listTag = compoundTag.getList("idlist", CompoundTag.class);
            for (EntityDefinition entityDefinition : this.identifierToDefinition.values()) {
                listTag.add(protocol <= 407 ? entityDefinition.getNetworkTagOld() : entityDefinition.getNetworkTag());
            }
            compoundTag.putList(listTag);
            if (protocol > 407) {
                this.networkTagCached = NBTIO.writeNetwork(compoundTag);
            } else {
                this.networkTagCachedOld = NBTIO.writeNetwork(compoundTag);
            }
        }catch (Exception e) {
            throw new RuntimeException("Unable to init entityIdentifiers", e);
        }
    }

    public byte[] getNetworkTagCached() {
        if (this.networkTagCached == null) {
            this.createNetworkTagCached(ProtocolInfo.CURRENT_PROTOCOL);
        }
        return this.networkTagCached;
    }

    public byte[] getNetworkTagCachedOld() {
        if (this.networkTagCachedOld == null) {
            this.createNetworkTagCached(407);
        }
        return this.networkTagCachedOld;
    }

    public boolean hasCustomEntities() {
        return !this.identifierToDefinition.isEmpty();
    }

}

