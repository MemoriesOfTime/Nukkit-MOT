package cn.nukkit.entity;

import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.math.AxisAlignedBB;

import java.util.HashMap;
import java.util.Map;

public class EntityRanges {
    private static final Map<Class<?>, Integer> FOLLOW_RANGES = new HashMap<>();

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
        return getAttribute(entity);
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

    private static int getAttribute(Entity entity) {
        Class<?> entityClass = entity.getClass();

        Integer value = FOLLOW_RANGES.get(entityClass);
        if (value != null) {
            return value;
        }

        Class<?> currentClass = entityClass.getSuperclass();
        while (currentClass != null && currentClass != Object.class) {
            value = FOLLOW_RANGES.get(currentClass);
            if (value != null) {
                return value;
            }
            currentClass = currentClass.getSuperclass();
        }

        return 16;
    }
}

