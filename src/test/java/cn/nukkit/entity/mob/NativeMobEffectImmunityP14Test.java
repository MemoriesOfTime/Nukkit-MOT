package cn.nukkit.entity.mob;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit slime/silverfish immunities in Mojang bedrock-samples 46ba6ea9. */
class NativeMobEffectImmunityP14Test {
    private MockedStatic<Server> singleton;
    @BeforeAll static void effects() { Effect.init(); }

    @BeforeEach void serverScope() {
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        singleton = mockStatic(Server.class);
        singleton.when(Server::getInstance).thenReturn(server);
    }

    @AfterEach void restoreServer() { singleton.close(); }

    @ParameterizedTest
    @CsvSource({"37,34", "39,35"})
    void explicitImmunityRejectsItsOwnEffect(int mob, int effect) throws Exception {
        assertFalse(target(mob).canBeAffected(effect));
    }

    @ParameterizedTest
    @CsvSource({"37,34", "39,35"})
    void nativeAddEffectCannotStoreAnImmuneEffect(int mob, int effect) throws Exception {
        Entity target = target(mob);
        target.addEffect(Effect.getEffect(effect).setDuration(3_600), null);
        assertFalse(target.hasEffect(effect));
    }

    @ParameterizedTest
    @CsvSource({"37,34", "39,35"})
    void splashEventRouteKeepsTheImmunity(int mob, int effect) throws Exception {
        Entity target = target(mob);
        target.addEffect(Effect.getEffect(effect).setDuration(3_600), EntityPotionEffectEvent.Cause.POTION_SPLASH);
        assertFalse(target.hasEffect(effect));
    }

    @ParameterizedTest
    @CsvSource({"37,35", "39,34", "37,19", "39,19", "37,1", "39,1"})
    void otherEffectsKeepTheirNativeGateAndDuration(int mob, int effect) throws Exception {
        Entity target = target(mob);
        assertTrue(target.canBeAffected(effect));
        target.addEffect(Effect.getEffect(effect).setDuration(3_600), null);
        assertTrue(target.hasEffect(effect));
        assertEquals(3_600, target.getEffect(effect).getDuration());
        assertEquals(0, target.getEffect(effect).getAmplifier());
    }

    private static Entity target(int mob) throws Exception {
        var settings = withSettings().defaultAnswer(call -> switch (call.getMethod().getName()) {
            case "canBeAffected", "addEffect", "getEffect", "hasEffect" -> call.callRealMethod();
            default -> RETURNS_DEFAULTS.answer(call);
        });
        Entity target = mob == 37 ? mock(EntitySlime.class, settings) : mock(EntitySilverfish.class, settings);
        Field effects = Entity.class.getDeclaredField("effects");
        effects.setAccessible(true);
        effects.set(target, new Int2ObjectOpenHashMap<Effect>());
        return target;
    }
}
