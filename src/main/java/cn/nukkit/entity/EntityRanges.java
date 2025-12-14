package cn.nukkit.entity;

import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.math.AxisAlignedBB;

import java.util.HashMap;
import java.util.Map;

public class EntityRanges {
    private static final Map<Class<?>, Integer> FOLLOW_RANGES = new HashMap<>();

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
        int radius = getFollowRange(entity);
        return entity.boundingBox.clone().grow(radius, radius, radius);
    }

    private static int getAttribute(Entity entity) {
        Class<?> entityClass = entity.getClass();

        Integer value = EntityRanges.FOLLOW_RANGES.get(entityClass);
        if (value != null) {
            return value;
        }

        Class<?> currentClass = entityClass.getSuperclass();
        while (currentClass != null && currentClass != Object.class) {
            value = EntityRanges.FOLLOW_RANGES.get(currentClass);
            if (value != null) {
                return value;
            }
            currentClass = currentClass.getSuperclass();
        }

        return 16;
    }
}

