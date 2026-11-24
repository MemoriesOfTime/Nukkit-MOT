package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSwordDiamond;
import cn.nukkit.item.ItemSwordIron;
import cn.nukkit.item.ItemSwordStone;
import cn.nukkit.item.ItemSwordWood;
import cn.nukkit.level.Level;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.potion.Effect;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Recorded BDS item extras plus Mojang's player base, through the actual player damage chain. */
class VanillaMeleeWeaponDamageTest {
    private record WeaponReference(String identifier, String javaClass, int attackDamageExtra) {}
    private record Fixture(int playerBaseDamage, List<WeaponReference> entries) {}

    private static Fixture reference() {
        var input = VanillaMeleeWeaponDamageTest.class
                .getResourceAsStream("/vanilla/p12-melee-weapon-damage.json");
        assertNotNull(input, "the recorded independent BDS reference must be packaged with the test");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Fixture.class);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Stream<WeaponReference> weapons() {
        return reference().entries().stream();
    }

    @BeforeEach
    void initialize() {
        MockServer.init();
        Effect.init();
        reset(MockServer.get().getPluginManager());
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(false);
    }

    @ParameterizedTest
    @MethodSource("weapons")
    void playerOverloadMatchesRecordedVanillaDamage(WeaponReference reference) throws Exception {
        Item item = item(reference);
        assertEquals(reference.identifier(), item.getNamespaceId());
        assertEquals(reference().playerBaseDamage() + reference.attackDamageExtra(),
                item.getAttackDamage(player()), reference.identifier());
    }

    @ParameterizedTest
    @MethodSource("weapons")
    void ordinaryMeleeSettlesTheFullVanillaBaseOnce(WeaponReference reference) throws Exception {
        Player attacker = player();
        Player victim = player();
        Item weapon = item(reference);
        // The same player overload and BASE assembly as Player's ordinary melee path.
        EntityDamageEvent hit = new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, weapon.getAttackDamage(attacker));
        assertTrue(victim.attack(hit));
        assertEquals(20f - reference().playerBaseDamage() - reference.attackDamageExtra(),
                victim.getHealth(), 0.00001f, reference.identifier());
        assertEquals(reference().playerBaseDamage() + reference.attackDamageExtra(), hit.getDamage());
    }

    @Test
    void explicitCustomWeaponDamageIsNotReplacedByTheVanillaTier() {
        Item weapon = new ItemSwordDiamond() {
            @Override
            public int getAttackDamage() {
                return 23;
            }
        };
        assertEquals(23, weapon.getAttackDamage(player()));
        assertEquals(1, new Item(Item.DIRT).getAttackDamage(player()), "generic items retain the fist base");
    }

    private static Item item(WeaponReference reference) throws Exception {
        if (reference.javaClass() == null) {
            return Item.fromString(reference.identifier());
        }
        return Class.forName(reference.javaClass()).asSubclass(Item.class)
                .getDeclaredConstructor().newInstance();
    }

    private static Player player() {
        Player player = mock(Player.class);
        Entity entity = player;
        entity.server = MockServer.get();
        entity.health = 20;
        entity.absorption = 0;
        field(player, "hasSpawned", new HashMap<>());
        field(player, "effects", new HashMap<>());
        player.protocol = 2193;
        Level level = mock(Level.class);
        entity.level = level;
        when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(player.getLevel()).thenReturn(level);
        entity.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0.6, 1.8, 0.6);
        when(player.getBoundingBox()).thenReturn(entity.boundingBox);
        when(player.isAlive()).thenAnswer(invocation -> entity.health > 0);
        when(player.getHealth()).thenAnswer(invocation -> entity.health);
        doAnswer(invocation -> { entity.health = invocation.getArgument(0); return null; })
                .when(player).setHealth(anyFloat());
        ((EntityHumanType) player).inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(((EntityHumanType) player).inventory);
        doCallRealMethod().when(player).damageArmor(any(Item.class), nullable(Entity.class),
                anyFloat(), anyBoolean(), any(EntityDamageEvent.DamageCause.class));
        doCallRealMethod().when(player).attack(any(EntityDamageEvent.class));
        doCallRealMethod().when(player).setLastDamageCause(any());
        when(player.getLastDamageCause()).thenCallRealMethod();
        return player;
    }

    private static void field(Object target, String name, Object value) {
        for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                var field = owner.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // The native owner may sit above Mockito's generated class.
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
        }
        throw new AssertionError(name);
    }
}
