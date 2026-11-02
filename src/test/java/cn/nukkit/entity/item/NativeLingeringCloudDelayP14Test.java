package cn.nukkit.entity.item;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real native NBT initialization; vanilla delay and explicitly configured delays. */
class NativeLingeringCloudDelayP14Test {
    private static final Set<String> REAL = Set.of(
            "initEntity", "setDataProperty", "getDataPropertyInt", "getDataPropertyShort",
            "getDataPropertyFloat", "getDataPropertyLong", "getDuration", "setDuration",
            "getRadius", "setRadius", "getRadiusPerTick", "setRadiusPerTick", "getSpawnTick", "setSpawnTick",
            "getWaitTime", "setWaitTime", "getPotionId", "setPotionId", "getParticleId", "setParticleId",
            "getPotionColor", "setPotionColor", "recalculatePotionColor", "getPickupCount", "setPickupCount",
            "getRadiusChangeOnPickup", "setRadiusChangeOnPickup");

    @BeforeAll static void effects() { Effect.init(); }

    @Test
    void vanillaDelayIsFortyTicksAndShapeDefaultsRemainUnchanged() throws Exception {
        EntityAreaEffectCloud cloud = cloud(new CompoundTag());
        assertEquals(40, cloud.reapplicationDelay);
        assertEquals(600, cloud.getDuration());
        assertEquals(3.0f, cloud.getRadius());
        assertEquals(-0.5f, cloud.radiusOnUse);
        assertEquals(-0.005f, cloud.getRadiusPerTick());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 73})
    void explicitNbtDelayOverridesVanillaDefault(int delay) throws Exception {
        assertEquals(delay, cloud(new CompoundTag().putInt("ReapplicationDelay", delay)).reapplicationDelay);
    }

    private static EntityAreaEffectCloud cloud(CompoundTag nbt) throws Exception {
        EntityAreaEffectCloud cloud = mock(EntityAreaEffectCloud.class, withSettings().defaultAnswer(call ->
                REAL.contains(call.getMethod().getName()) ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call)));
        cloud.namedTag = nbt;
        field(cloud, "dataProperties", new EntityMetadata().putLong(Entity.DATA_FLAGS, 0));
        field(cloud, "hasSpawned", new HashMap<>());
        field(cloud, "effects", new Int2ObjectOpenHashMap<Effect>());
        field(cloud, "victims", new Long2LongOpenHashMap());
        cloud.affectOwner = true;
        cloud.initEntity();
        return cloud;
    }

    private static void field(Object object, String name, Object value) throws Exception {
        for (Class<?> type = EntityAreaEffectCloud.class; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
