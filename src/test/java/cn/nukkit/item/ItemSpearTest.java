package cn.nukkit.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityMotionEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ItemSpear} 的蓄力戳刺、连击耐久与疾跳事件测试。
 * <p>
 * Tests for {@link ItemSpear}: charge stab, jab durability, and lunge events.
 */
class ItemSpearTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    /**
     * Regression: the spear charge (hold and release, vanilla {@code minecraft:kinetic_weapon}) wore
     * the spear down on every release, a miss included, and struck at any hold length and any speed.
     * Vanilla strikes only after the tier's warm-up, inside its damage window, and only a target the
     * spear closes in on at 4.6 blocks per second; nothing hit costs nothing.
     */
    @Nested
    class Charge {

        private static ItemSpear spear(boolean hits, AtomicInteger attempts) {
            return new ItemSpearIron() {
                @Override
                boolean chargeStab(Player player) {
                    attempts.incrementAndGet();
                    return hits;
                }
            };
        }

        private static Player player() {
            Player player = Mockito.mock(Player.class);
            PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
            Mockito.when(player.getInventory()).thenReturn(inventory);
            Mockito.when(player.getLevel()).thenReturn(Mockito.mock(Level.class));
            Mockito.when(inventory.getItemInHandFast()).thenReturn(Item.get(Item.AIR));
            return player;
        }

        @Test
        void chargeThatMissesCostsNothing() {
            AtomicInteger attempts = new AtomicInteger();
            ItemSpear spear = spear(false, attempts);
            assertTrue(spear.onRelease(player(), 40));
            assertEquals(1, attempts.get());
            assertEquals(0, spear.getDamage());
        }

        @Test
        void chargeThatHitsWearsTheSpearDown() {
            AtomicInteger attempts = new AtomicInteger();
            ItemSpear spear = spear(true, attempts);
            spear.onRelease(player(), 40);
            assertEquals(1, spear.getDamage());
        }

        @Test
        void releaseBeforeTheWarmUpDoesNotStrike() {
            AtomicInteger attempts = new AtomicInteger();
            ItemSpear spear = spear(true, attempts);
            spear.onRelease(player(), 2);
            assertEquals(0, attempts.get());
            assertEquals(0, spear.getDamage());
        }

        @Test
        void releaseAfterTheDamageWindowDoesNotStrike() {
            AtomicInteger attempts = new AtomicInteger();
            ItemSpear spear = spear(true, attempts);
            spear.onRelease(player(), 12 + 225 + 10);
            assertEquals(0, attempts.get());
            assertEquals(0, spear.getDamage());
        }

        @Test
        void chargeWindowFollowsVanillaKineticWeapon() {
            assertEquals(15, new ItemSpearWood().getChargeDelayTicks());
            assertEquals(300, new ItemSpearWood().getChargeDamageTicks());
            assertEquals(14, new ItemSpearGold().getChargeDelayTicks());
            assertEquals(12, new ItemSpearIron().getChargeDelayTicks());
            assertEquals(225, new ItemSpearIron().getChargeDamageTicks());
            assertEquals(8, new ItemSpearNetherite().getChargeDelayTicks());
            assertEquals(175, new ItemSpearNetherite().getChargeDamageTicks());

            assertFalse(ItemSpear.isChargeInWindow(8, 12, 225));
            assertTrue(ItemSpear.isChargeInWindow(9, 12, 225));
            assertTrue(ItemSpear.isChargeInWindow(240, 12, 225));
            assertFalse(ItemSpear.isChargeInWindow(241, 12, 225));
        }

        @Test
        void chargeNeedsVanillaClosingSpeed() {
            Vector3 look = new Vector3(0, 0, 1);
            Vector3 still = new Vector3(0, 0, 0);
            // Sprinting on the ground, 5.6 blocks per second, at a standing target.
            assertTrue(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.28), still, look)));
            // Walking, 4.3 blocks per second, is too slow.
            assertFalse(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.215), still, look)));
            // Standing still.
            assertFalse(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(still, still, look)));
            // Chasing a target that runs away just as fast closes nothing.
            assertFalse(ItemSpear.isChargeFastEnough(
                    ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.28), new Vector3(0, 0, 0.28), look)));
            // Walking into a target that walks towards the spear closes fast enough.
            assertTrue(ItemSpear.isChargeFastEnough(
                    ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.215), new Vector3(0, 0, -0.1), look)));
            // Running sideways to the look does not count.
            assertFalse(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(new Vector3(0.28, 0, 0), still, look)));
        }
    }

    /**
     * Regression: 1.21.110+ clients jab with a spear through the attack button
     * ({@code USE_ITEM_ACTION_USE_AS_ATTACK}). The core ignored that action, so a jab neither hit nor
     * wore the spear down. A jab that hits must cost one durability point like any melee hit.
     */
    @Nested
    class JabDurability {

        private static ItemSpear spear(boolean hits) {
            return new ItemSpearIron() {
                @Override
                boolean stab(Player player) {
                    return hits;
                }
            };
        }

        private static Player player(boolean creative) {
            Player player = Mockito.mock(Player.class);
            PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
            Mockito.when(player.getInventory()).thenReturn(inventory);
            Mockito.when(inventory.getItemInHandFast()).thenReturn(Item.get(Item.AIR));
            Mockito.when(player.isCreative()).thenReturn(creative);
            return player;
        }

        @Test
        void jabThatHitsWearsTheSpearDown() {
            ItemSpear spear = spear(true);
            assertTrue(spear.onJab(player(false)));
            assertEquals(1, spear.getDamage());
            assertTrue(spear.onJab(player(false)));
            assertEquals(2, spear.getDamage());
        }

        @Test
        void missedJabCostsNothing() {
            ItemSpear spear = spear(false);
            assertFalse(spear.onJab(player(false)));
            assertEquals(0, spear.getDamage());
        }

        @Test
        void creativeJabKeepsTheSpear() {
            ItemSpear spear = spear(true);
            assertTrue(spear.onJab(player(true)));
            assertEquals(0, spear.getDamage());
        }

        @Test
        void brokenSpearDoesNotJab() {
            ItemSpear spear = spear(true);
            spear.setDamage(spear.getMaxDurability());
            assertFalse(spear.onJab(player(false)));
            assertEquals(spear.getMaxDurability(), spear.getDamage());
        }

        @Test
        void jabCoolDownFollowsVanillaSwingDuration() {
            assertEquals(13, new ItemSpearWood().getJabCooldownTicks());
            assertEquals(19, new ItemSpearIron().getJabCooldownTicks());
            assertEquals(23, new ItemSpearNetherite().getJabCooldownTicks());
        }
    }

    @Nested
    class LungeEvent {
        @Test
        void cancellationLeavesMotionSoundAndFoodUntouched() throws Exception {
            Fixture fixture = new Fixture();
            doAnswer(call -> {
                Event event = call.getArgument(0);
                assertEquals("cn.nukkit.event.player.PlayerSpearLungeEvent", event.getClass().getName());
                assertSame(fixture.player, ((cn.nukkit.event.player.PlayerEvent) event).getPlayer());
                assertSame(fixture.spear, event.getClass().getMethod("getSpear").invoke(event));
                ((Cancellable) event).setCancelled(true);
                return null;
            }).when(fixture.events).callEvent(any(Event.class));

            fixture.lunge();

            verify(fixture.player, never()).setMotion(any(Vector3.class));
            verify(fixture.level, never()).addSound(any(), eq(Sound.ITEM_SPEAR_LUNGE));
            verify(fixture.food, never()).updateFoodExpLevel(anyDouble());
        }

        @Test
        void allowedLungeKeepsVanillaImpulseSoundAndFood() throws Exception {
            Fixture fixture = new Fixture();

            fixture.lunge();

            verify(fixture.events).callEvent(any(Event.class));
            verify(fixture.player).setMotion(new Vector3(0.2, 0.3, 1.3));
            verify(fixture.level).addSound(fixture.player, Sound.ITEM_SPEAR_LUNGE);
            verify(fixture.food).updateFoodExpLevel(4);
        }

        @Test
        void chargedHitEntersCombatBeforeLungeCancellationAndStillWearsSpear() throws Exception {
            Fixture fixture = new Fixture();
            java.util.concurrent.atomic.AtomicBoolean inCombat = new java.util.concurrent.atomic.AtomicBoolean();
            when(fixture.spear.getMaxDurability()).thenReturn(100);
            when(fixture.spear.getChargeDamageTicks()).thenReturn(100);
            when(fixture.spear.chargeStab(fixture.player)).thenAnswer(ignored -> {
                inCombat.set(true);
                return true;
            });
            doCallRealMethod().when(fixture.spear).onRelease(fixture.player, 10);
            doAnswer(call -> {
                assertTrue(inCombat.get(), "charged damage must precede the lunge decision");
                ((Cancellable) call.getArgument(0)).setCancelled(true);
                return null;
            }).when(fixture.events).callEvent(any(Event.class));

            assertTrue(fixture.spear.onRelease(fixture.player, 10));

            verify(fixture.spear).chargeStab(fixture.player);
            verify(fixture.spear).damageSpear(fixture.player);
            verify(fixture.player, never()).setMotion(any(Vector3.class));
            verify(fixture.food, never()).updateFoodExpLevel(anyDouble());
        }

        @Test
        void unenchantedSpearDoesNotEmitLungeOrMotion() throws Exception {
            Fixture fixture = new Fixture();
            when(fixture.spear.getEnchantmentLevel(Enchantment.ID_LUNGE)).thenReturn(0);

            fixture.lunge();

            verifyNoInteractions(fixture.events);
            verify(fixture.player, never()).setMotion(any(Vector3.class));
        }

        @Test
        void lungeHandlersAreSeparateFromGenericMotionHandlers() throws Exception {
            assertNotSame(EntityMotionEvent.getHandlers(), assertDoesNotThrow(() -> Class.forName("cn.nukkit.event.player.PlayerSpearLungeEvent")).getDeclaredMethod("getHandlers").invoke(null));
        }

        private static final class Fixture {
            final ItemSpear spear = mock(ItemSpear.class);
            final Player player = mock(Player.class);
            final PlayerFood food = mock(PlayerFood.class);
            final Level level = mock(Level.class);
            final PluginManager events = mock(PluginManager.class);

            Fixture() {
                Server server = mock(Server.class);
                when(server.getPluginManager()).thenReturn(events);
                when(player.getServer()).thenReturn(server);
                when(player.getLevel()).thenReturn(level);
                when(player.getFoodData()).thenReturn(food);
                when(player.isSurvival()).thenReturn(true);
                when(food.getLevel()).thenReturn(20);
                when(player.getDirectionVector()).thenAnswer(ignored -> new Vector3(0, 0, 1));
                when(player.getMotion()).thenReturn(new Vector3(0.2, 0.3, 0.4));
                when(spear.getEnchantmentLevel(Enchantment.ID_LUNGE)).thenReturn(1);
            }

            void lunge() throws Exception {
                Method lunge = ItemSpear.class.getDeclaredMethod("applyLunge", Player.class);
                lunge.setAccessible(true);
                lunge.invoke(spear, player);
            }
        }
    }
}
