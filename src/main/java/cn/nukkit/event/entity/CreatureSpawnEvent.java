package cn.nukkit.event.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;
import cn.nukkit.event.HandlerList;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public class CreatureSpawnEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Getter
    private final SpawnReason reason;
    @Getter
    private final int entityNetworkId;
    @Getter
    private final Position position;
    @Getter
    private final CompoundTag compoundTag;
    @Nullable
    @Getter
    private final Entity creator;

    @Deprecated
    public CreatureSpawnEvent(int networkId, SpawnReason reason) {
        this(networkId, new Position(), new CompoundTag(), reason, null);
    }

    @Deprecated
    public CreatureSpawnEvent(int networkId, Position position, SpawnReason reason) {
        this(networkId, position, new CompoundTag(), reason, null);
    }

    @Deprecated
    public CreatureSpawnEvent(int networkId, Position position, CompoundTag nbt, SpawnReason reason) {
        this(networkId, position, nbt, reason, null);
    }

    public CreatureSpawnEvent(int networkId, SpawnReason reason, Entity creator) {
        this(networkId, new Position(), new CompoundTag(), reason, creator);
    }

    public CreatureSpawnEvent(int networkId, Position position, SpawnReason reason, Entity creator) {
        this(networkId, position, new CompoundTag(), reason, creator);
    }

    public CreatureSpawnEvent(int networkId, Position position, CompoundTag nbt, SpawnReason reason, @Nullable Entity creator) {
        this.reason = reason;
        this.entityNetworkId = networkId;
        this.position = position;
        this.compoundTag = nbt;
        this.creator = creator;
    }

    /**
     * An enum to specify the type of spawning
     */
    public enum SpawnReason {

        /**
         * When something spawns from natural means
         */
        NATURAL,
        /**
         * When an entity spawns as a jockey of another entity (mostly spider
         * jockeys)
         */
        JOCKEY,
        /**
         * When a creature spawns from a spawner
         */
        SPAWNER,
        /**
         * When a creature spawns from an egg
         */
        EGG,
        /**
         * When a creature spawns from a spawn egg
         */
        SPAWN_EGG,
        /**
         * When a creature spawns because of a lightning strike
         */
        LIGHTNING,
        /**
         * When a snowman is spawned by being built
         */
        BUILD_SNOWMAN,
        /**
         * When an iron golem is spawned by being built
         */
        BUILD_IRONGOLEM,
        /**
         * When a wither boss is spawned by being built
         */
        BUILD_WITHER,
        /**
         * When an iron golem is spawned to defend a village
         */
        VILLAGE_DEFENSE,
        /**
         * When a zombie is spawned to invade a village
         */
        VILLAGE_INVASION,
        /**
         * When an animal breeds to create a child
         */
        BREEDING,
        /**
         * When a slime splits
         */
        SLIME_SPLIT,
        /**
         * When an entity calls for reinforcements
         */
        REINFORCEMENTS,
        /**
         * When a creature is spawned by nether portal
         */
        NETHER_PORTAL,
        /**
         * When a creature is spawned by a dispenser dispensing an egg
         */
        DISPENSE_EGG,
        /**
         * When a zombie infects a villager
         */
        INFECTION,
        /**
         * When a villager is cured from infection
         */
        CURED,
        /**
         * When an ocelot has a baby spawned along with them
         */
        OCELOT_BABY,
        /**
         * When a silverfish spawns from a block
         */
        SILVERFISH_BLOCK,
        /**
         * When an entity spawns as a mount of another entity (mostly chicken
         * jockeys)
         */
        MOUNT,
        /**
         * When an entity spawns as a trap for players approaching
         */
        TRAP,
        /**
         * When an entity is spawned as a result of ender pearl usage
         */
        ENDER_PEARL,
        /**
         * When an entity is spawned as a result of the entity it is being
         * perched on jumping or being damaged
         */
        SHOULDER_ENTITY,
        /**
         * When a creature is spawned by another entity drowning
         */
        DROWNED,
        /**
         * When an cow is spawned by shearing a mushroom cow
         */
        SHEARED,
        /**
         * When a creature is spawned by plugins
         */
        CUSTOM,
        /**
         * When SpawnReason is missing
         */
        DEFAULT
    }
}
