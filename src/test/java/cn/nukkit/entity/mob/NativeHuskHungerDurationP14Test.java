package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native husk attack; hunger duration from Mojang bedrock-samples 46ba6ea9 husk.json. */
class NativeHuskHungerDurationP14Test {
    @BeforeAll static void effects() { Effect.init(); }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void hungerOnlyFollowsAcceptedDamage(boolean player, boolean accepts) throws Exception {
        List<Effect> effects = new ArrayList<>();
        Entity target = target(player, accepts, effects);
        EntityHusk husk = husk(24);
        husk.attackEntity(target);
        verify(target).attack(any(EntityDamageEvent.class));
        if (accepts) {
            assertEquals(1, effects.size());
            assertEquals(Effect.HUNGER, effects.get(0).getId());
            assertEquals(600, effects.get(0).getDuration());
            assertEquals(0, effects.get(0).getAmplifier());
        } else {
            assertTrue(effects.isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"23,0.5", "24,1.01"})
    void cooldownAndRangeStillPreventTheAttack(int delay, double distance) throws Exception {
        List<Effect> effects = new ArrayList<>();
        Entity target = target(true, true, effects);
        when(target.distanceSquared(any(Entity.class))).thenReturn(distance);
        husk(delay).attackEntity(target);
        assertTrue(effects.isEmpty());
        verify(target, never()).attack(any(EntityDamageEvent.class));
    }

    private static EntityHusk husk(int delay) throws Exception {
        EntityHusk husk = mock(EntityHusk.class);
        when(husk.getDamage()).thenReturn(3);
        doCallRealMethod().when(husk).attackEntity(any(Entity.class));
        for (Class<?> type = EntityHusk.class; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("attackDelay");
                field.setAccessible(true);
                field.set(husk, delay);
                return husk;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException("attackDelay");
    }

    private static Entity target(boolean player, boolean accepts, List<Effect> effects) {
        Entity target = player ? mock(Player.class) : mock(EntityZombie.class);
        if (target instanceof Player p) {
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(p.getInventory()).thenReturn(inventory);
            when(inventory.getArmorContents()).thenReturn(Item.EMPTY_ARRAY);
        }
        when(target.attack(any(EntityDamageEvent.class))).thenReturn(accepts);
        doAnswer(call -> { effects.add(call.getArgument(0)); return null; })
                .when(target).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ATTACK));
        return target;
    }
}
