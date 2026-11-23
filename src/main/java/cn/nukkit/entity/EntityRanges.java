package cn.nukkit.entity;

import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EntityRanges {
    private static final Map<Class<?>, Integer> FOLLOW_RANGES = new HashMap<>();

    // The registry is populated once; ClassValue also permits custom entity class unloading.
    private static final ClassValue<Integer> FOLLOW_RANGE = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            for (Class<?> current = type; current != null && current != Object.class;
                    current = current.getSuperclass()) {
                Integer value = FOLLOW_RANGES.get(current);
                if (value != null) {
                    return value;
                }
            }
            return 16;
        }
    };

    // Upper bounds of known pure/bounded MOT predicates. Skeleton, Stray,
    // Piglin and Pillager mutate state even on rejection: keep their old cuboid.
    // ClassValue resolves the declaring
    // method once: a custom override must keep its existing search cuboid.
    private static final Map<Class<?>, Integer> LOCAL_TARGET_RANGES = Map.ofEntries(
            Map.entry(BaseEntity.class, 10),
            Map.entry(EntityWalkingAnimal.class, 10),
            Map.entry(EntityEnderman.class, 32),
            Map.entry(EntityWitch.class, 16),
            Map.entry(EntityWolf.class, 16),
            Map.entry(EntityHorse.class, 10),
            Map.entry(EntityDonkey.class, 10),
            Map.entry(EntityLlama.class, 10),
            Map.entry(EntityMule.class, 7),
            Map.entry(EntityRabbit.class, 10),
            Map.entry(EntityIronGolem.class, 10),
            Map.entry(EntitySnowGolem.class, 10),
            Map.entry(EntityPolarBear.class, 10),
            Map.entry(EntitySpider.class, 10),
            Map.entry(EntityZombiePigman.class, 10),
            Map.entry(EntitySkeletonHorse.class, 0),
            Map.entry(EntityZombieHorse.class, 0));

    private static final ClassValue<Integer> LOCAL_TARGET_RANGE = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            if (!EntityWalking.class.isAssignableFrom(type)) {
                return -1;
            }
            try {
                Class<?> declaration = type.getMethod("targetOption", EntityCreature.class, double.class).getDeclaringClass();
                return LOCAL_TARGET_RANGES.getOrDefault(declaration, -1);
            } catch (NoSuchMethodException impossible) {
                throw new IllegalStateException("Walking entity lacks targetOption", impossible);
            }
        }
    };


    // Exact built-in classes only. Unknown subclasses may override target predicates,
    // friendliness or feeding behavior and must retain the complete entity query.
    private static final Set<Class<?>> PLAYER_ONLY_TARGET_TYPES = Set.of(
            EntityArmadillo.class, EntityCamel.class, EntityCat.class, EntityChicken.class,
            EntityCopperGolem.class, EntityCow.class, EntityDonkey.class, EntityFox.class,
            EntityGoat.class, EntityHorse.class, EntityHorseBase.class, EntityLlama.class,
            EntityMooshroom.class, EntityMule.class, EntityOcelot.class, EntityPanda.class,
            EntityPig.class, EntitySheep.class, EntitySkeletonHorse.class, EntitySniffer.class,
            EntityStrider.class, EntityVillager.class, EntityVillagerV2.class,
            EntityWanderingTrader.class, EntityZombieHorse.class);

    private static final ClassValue<Boolean> PLAYER_ONLY_TARGETS = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return PLAYER_ONLY_TARGET_TYPES.contains(type);
        }
    };

    private static final ClassValue<Boolean> NATIVE_NEARBY_QUERY = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getNearbyEntities", AxisAlignedBB.class, Entity.class,
                        boolean.class, boolean.class).getDeclaringClass() == Level.class
                        && type.getMethod("getNearbyEntities", AxisAlignedBB.class, Entity.class,
                        boolean.class).getDeclaringClass() == Level.class;
            } catch (NoSuchMethodException impossible) {
                throw new IllegalStateException("Level lacks nearby entity query", impossible);
            }
        }
    };

    static boolean targetsOnlyPlayersWhenNotInLove(EntityWalking entity) {
        return PLAYER_ONLY_TARGETS.get(entity.getClass()) && !entity.isInLove();
    }

    static boolean hasNativeNearbyQuery(Level level) {
        return NATIVE_NEARBY_QUERY.get(level.getClass());
    }

    /*
      follow_range from bedrock-samples
      [https://github.com/Mojang/bedrock-samples/tree/preview/behavior_pack/entities]
    */
    static {
        FOLLOW_RANGES.put(EntityZombie.class, 32);
        FOLLOW_RANGES.put(EntityEnderman.class, 64);
        FOLLOW_RANGES.put(EntityBlaze.class, 48);
        FOLLOW_RANGES.put(EntityGhast.class, 100);
        FOLLOW_RANGES.put(EntityWarden.class, 30);
        FOLLOW_RANGES.put(EntityDrowned.class, 32);
        FOLLOW_RANGES.put(EntityHusk.class, 32);
        FOLLOW_RANGES.put(EntityEvoker.class, 64);
        FOLLOW_RANGES.put(EntityVindicator.class, 64);
        FOLLOW_RANGES.put(EntityPillager.class, 64);
        FOLLOW_RANGES.put(EntityCreaking.class, 32);
        FOLLOW_RANGES.put(EntityRavager.class, 32);
        FOLLOW_RANGES.put(EntityPhantom.class, 64);

        FOLLOW_RANGES.put(EntityPolarBear.class, 48);
        FOLLOW_RANGES.put(EntityLlama.class, 40);
        FOLLOW_RANGES.put(EntityIronGolem.class, 32);

        FOLLOW_RANGES.put(EntityPig.class, 10);
        FOLLOW_RANGES.put(EntitySheep.class, 10);
        FOLLOW_RANGES.put(EntityCow.class, 10);
        FOLLOW_RANGES.put(EntityChicken.class, 10);
        FOLLOW_RANGES.put(EntityRabbit.class, 10);
        FOLLOW_RANGES.put(EntityMooshroom.class, 10);

        FOLLOW_RANGES.put(EntityVillager.class, 128);
        FOLLOW_RANGES.put(EntityAllay.class, 1024);
    }



    /**
     * Gets the target detection radius for mobs
     * @param entity mob
     * @return radius (default 16)
     */
    public static int getFollowRange(Entity entity) {
        return FOLLOW_RANGE.get(entity.getClass());
    }

    /**
     * Creating a bounding box for detection
     * @param entity mob
     * @return AxisAlignedBB
     */
    public static AxisAlignedBB createTargetSearchBox(Entity entity) {
        int radius = getTargetSearchRange(entity);
        return entity.boundingBox.clone().grow(radius, radius, radius);
    }

    /** Updates an entity-owned search box without retaining or mutating its collision box. */
    static AxisAlignedBB updateTargetSearchBox(Entity entity, SimpleAxisAlignedBB box) {
        int radius = getTargetSearchRange(entity);
        AxisAlignedBB bounds = entity.boundingBox;
        return box.setBounds(bounds.getMinX() - radius, bounds.getMinY() - radius,
                bounds.getMinZ() - radius, bounds.getMaxX() + radius,
                bounds.getMaxY() + radius, bounds.getMaxZ() + radius);
    }

    /**
     * Local candidate search is separate from retaining a known owner/target.
     * Allay's vanilla 1024-block follow_range belongs to following its owner;
     * its local pickup_items search has max_dist 32, not that navigation range.
     */
    public static int getTargetSearchRange(Entity entity) {
        if (entity instanceof EntityAllay) return 32;
        int legacyRange = getFollowRange(entity);
        int predicateRange = LOCAL_TARGET_RANGE.get(entity.getClass());
        return predicateRange < 0 ? legacyRange : Math.min(legacyRange, predicateRange);
    }

}
