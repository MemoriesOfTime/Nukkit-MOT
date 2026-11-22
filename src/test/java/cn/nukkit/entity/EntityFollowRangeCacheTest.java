package cn.nukkit.entity;

import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objenesis.ObjenesisStd;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityFollowRangeCacheTest {
    @ParameterizedTest
    @MethodSource("ranges")
    void preservesRegisteredInheritedAndDefaultRanges(Class<? extends Entity> type, int expected) {
        Entity first = new ObjenesisStd().newInstance(type);
        Entity second = new ObjenesisStd().newInstance(type);

        for (int i = 0; i < 10; i++) {
            assertEquals(expected, EntityRanges.getFollowRange(first));
            assertEquals(expected, EntityRanges.getFollowRange(second));
        }
    }

    static Stream<Arguments> ranges() {
        return Stream.of(
                Arguments.of(EntityZombie.class, 32), Arguments.of(EntityEnderman.class, 64),
                Arguments.of(EntityBlaze.class, 48), Arguments.of(EntityGhast.class, 100),
                Arguments.of(EntityWarden.class, 30), Arguments.of(EntityDrowned.class, 32),
                Arguments.of(EntityHusk.class, 32), Arguments.of(EntityEvoker.class, 64),
                Arguments.of(EntityVindicator.class, 64), Arguments.of(EntityPillager.class, 64),
                Arguments.of(EntityCreaking.class, 32), Arguments.of(EntityRavager.class, 32),
                Arguments.of(EntityPhantom.class, 64), Arguments.of(EntityPolarBear.class, 48),
                Arguments.of(EntityLlama.class, 40), Arguments.of(EntityIronGolem.class, 32),
                Arguments.of(EntityPig.class, 10), Arguments.of(EntitySheep.class, 10),
                Arguments.of(EntityCow.class, 10), Arguments.of(EntityChicken.class, 10),
                Arguments.of(EntityRabbit.class, 10), Arguments.of(EntityMooshroom.class, 10),
                Arguments.of(EntityVillager.class, 128), Arguments.of(EntityAllay.class, 1024),
                Arguments.of(EntityVillagerV2.class, 128), Arguments.of(EntityZombieVillagerV2.class, 32),
                Arguments.of(EntityCreeper.class, 16), Arguments.of(EntityWanderingTrader.class, 16),
                Arguments.of(CustomCow.class, 10), Arguments.of(DeepCow.class, 10),
                Arguments.of(CustomVillager.class, 128), Arguments.of(CustomDefault.class, 16));
    }

    @Test
    void preservesLocalPredicateBoundsAndUnknownOverrides() {
        ObjenesisStd instances = new ObjenesisStd();

        assertEquals(32, EntityRanges.getTargetSearchRange(instances.newInstance(EntityAllay.class)));
        assertEquals(10, EntityRanges.getTargetSearchRange(instances.newInstance(CustomCow.class)));
        assertEquals(10, EntityRanges.getTargetSearchRange(instances.newInstance(EntityVillagerV2.class)));
        assertEquals(128, EntityRanges.getTargetSearchRange(instances.newInstance(CustomVillager.class)));
        assertEquals(16, EntityRanges.getTargetSearchRange(instances.newInstance(CustomDefault.class)));
    }

    @Test
    void nullStillFails() {
        assertThrows(NullPointerException.class, () -> EntityRanges.getFollowRange(null));
    }

    private static class CustomCow extends EntityCow {
        CustomCow(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }
    }

    private static final class DeepCow extends CustomCow {
        DeepCow(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }
    }

    private static final class CustomVillager extends EntityVillager {
        CustomVillager(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }

        @Override
        public boolean targetOption(EntityCreature creature, double distance) {
            return distance <= 128 * 128;
        }
    }

    private static final class CustomDefault extends EntityCreeper {
        CustomDefault(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }

        @Override
        public boolean targetOption(EntityCreature creature, double distance) {
            return distance <= 16 * 16;
        }
    }
}
