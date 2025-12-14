package cn.nukkit.entity;

import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.EntityLlama;
import cn.nukkit.entity.passive.EntityPolarBear;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.math.AxisAlignedBB;

import java.util.HashMap;
import java.util.Map;

public class EntityRanges {
    private static final Map<Class<?>, Integer> DETECTION_RANGES = new HashMap<>();
    private static final Map<Class<?>, Integer> FOLLOW_RANGES = new HashMap<>();

    /*
        follow_range from bedrock-samples
        [https://github.com/Mojang/bedrock-samples/tree/preview/behavior_pack/entities]
    */
    static {
        DETECTION_RANGES.put(EntityZombie.class, 32);
        DETECTION_RANGES.put(EntitySkeleton.class, 16);
        DETECTION_RANGES.put(EntityCreeper.class, 16);
        DETECTION_RANGES.put(EntityEnderman.class, 64);
        DETECTION_RANGES.put(EntityBlaze.class, 48);
        DETECTION_RANGES.put(EntityGhast.class, 100);
        DETECTION_RANGES.put(EntityGuardian.class, 16);
        DETECTION_RANGES.put(EntityElderGuardian.class, 16);
        DETECTION_RANGES.put(EntityWarden.class, 30);
        DETECTION_RANGES.put(EntityDrowned.class, 32);
        DETECTION_RANGES.put(EntityHusk.class, 32);
        DETECTION_RANGES.put(EntityStray.class, 16);
        DETECTION_RANGES.put(EntitySpider.class, 16);
        DETECTION_RANGES.put(EntityEvoker.class, 64);
        DETECTION_RANGES.put(EntityVindicator.class, 64);
        DETECTION_RANGES.put(EntityPillager.class, 64);
        DETECTION_RANGES.put(EntityCreaking.class, 32);
        DETECTION_RANGES.put(EntityPolarBear.class, 48);
        DETECTION_RANGES.put(EntityLlama.class, 40);
        DETECTION_RANGES.put(EntityVillager.class, 128);
        DETECTION_RANGES.put(EntityWitch.class, 16);
        DETECTION_RANGES.put(EntityRavager.class, 32);
        DETECTION_RANGES.put(EntityPhantom.class, 64);
        DETECTION_RANGES.put(EntityShulker.class, 16);
        DETECTION_RANGES.put(EntityPiglin.class, 16);
        DETECTION_RANGES.put(EntityPiglinBrute.class, 16);
        DETECTION_RANGES.put(EntityHoglin.class, 16);
        DETECTION_RANGES.put(EntityZoglin.class, 16);
        DETECTION_RANGES.put(EntityVex.class, 16);
        DETECTION_RANGES.put(EntityCaveSpider.class, 16);
        DETECTION_RANGES.put(EntitySilverfish.class, 16);
        DETECTION_RANGES.put(EntityWitherSkeleton.class, 16);
    }


    /**
     * Gets the target detection radius for mobs
     * @param entity mob
     * @return radius (default 16)
     */
    public static int getDetectionRange(Entity entity) {
        return getAttribute(entity, DETECTION_RANGES, 16);
    }

    /**
     * Gets the maximum range of detection
     * @param entity mob
     * @return max radius
     */
    public static int getFollowRange(Entity entity) {
        return getAttribute(entity, FOLLOW_RANGES, getDetectionRange(entity));
    }

    /**
     * Checks if the target is within the mob's detection range
     * @param entity mob
     * @param target target
     * @return true if within the radius
     */
    public static boolean isTargetInDetectionRange(Entity entity, Entity target) {
        double distance = entity.distance(target);
        return distance <= getDetectionRange(entity);
    }

    /**
     * Creating a bounding box for detection
     * @param entity mob
     * @return AxisAlignedBB
     */
    public static AxisAlignedBB createTargetSearchBox(Entity entity) {
        int radius = getDetectionRange(entity);
        return entity.boundingBox.clone().grow(radius, radius, radius);
    }

    private static int getAttribute(Entity entity, Map<Class<?>, Integer> attributes, int defaultValue) {
        Class<?> entityClass = entity.getClass();

        Integer value = attributes.get(entityClass);
        if (value != null) {
            return value;
        }

        Class<?> currentClass = entityClass.getSuperclass();
        while (currentClass != null && currentClass != Object.class) {
            value = attributes.get(currentClass);
            if (value != null) {
                return value;
            }
            currentClass = currentClass.getSuperclass();
        }

        return defaultValue;
    }
}

