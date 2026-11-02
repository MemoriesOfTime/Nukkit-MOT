package cn.nukkit;

import cn.nukkit.block.BlockID;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.data.EntityMovementSpeedModifier;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise real movement enchantment handling and named modifier writes. */
class PlayerSoulSpeedEquipmentTest {
    private static final String SOUL = EntityMovementSpeedModifier.SOUL_SPEED_ENCHANTMENT;

    @BeforeAll static void initialize() { MockServer.init(); }

    @ParameterizedTest
    @CsvSource({"786,0", "2193,0", "786,-1", "2193,-1"})
    void removingOrReplacingBootsClearsOnlySoulSpeed(int protocol, int replacement) throws Exception {
        Fixture f = new Fixture(protocol);
        EntityMovementSpeedModifier sprint = EntityMovementSpeedModifier.of(
                EntityMovementSpeedModifier.SPRINTING, 1.3f, EntityMovementSpeedModifier.Operation.MULTIPLY);
        f.player.addMovementSpeedModifier(sprint);
        f.wear(3);
        f.move();
        assertEquals(1.615f, f.modifier().getValue(), 0.00001f);

        f.wear(replacement);
        f.move();
        assertNull(f.modifier(), "Soul Speed must belong to the currently equipped boots");
        f.ground.set(BlockID.STONE);
        f.move();
        assertNull(f.modifier(), "removed boots cannot keep their boost on stone");
        assertSame(sprint, f.player.getMovementSpeedModifiers().get(EntityMovementSpeedModifier.SPRINTING));
    }

    @ParameterizedTest
    @ValueSource(ints = {786, 2193})
    void changingLevelOnSameSandReplacesRatherThanStacksTheModifier(int protocol) throws Exception {
        Fixture f = new Fixture(protocol);
        f.wear(3);
        f.move();
        for (int i = 0; i < 10; i++) {
            f.wear(1);
            f.move();
            assertEquals(1.405f, f.modifier().getValue(), 0.00001f);
            f.wear(3);
            f.move();
            assertEquals(1.615f, f.modifier().getValue(), 0.00001f);
            assertEquals(1, f.player.getMovementSpeedModifiers().size());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {786, 2193})
    void unchangedEquipmentDoesNotRepublishMovementAndLeavingSandClearsIt(int protocol) throws Exception {
        Fixture f = new Fixture(protocol);
        f.wear(3);
        f.move();
        EntityMovementSpeedModifier first = f.modifier();
        assertNotNull(first);
        for (int i = 0; i < 10; i++) f.move();
        assertSame(first, f.modifier(), "steady movement must not allocate or resend a modifier");
        f.ground.set(BlockID.STONE);
        f.move();
        assertNull(f.modifier());
        f.ground.set(BlockID.SOUL_SAND);
        f.move();
        assertEquals(1.615f, f.modifier().getValue(), 0.00001f);
    }

    @ParameterizedTest
    @ValueSource(ints = {786, 2193})
    void reequippingAfterRemovalOnSameSandReactivatesTheCurrentLevel(int protocol) throws Exception {
        Fixture f = new Fixture(protocol);
        f.wear(3);
        f.move();
        f.wear(-1);
        f.move();
        assertNull(f.modifier());
        f.wear(1);
        f.move();
        assertEquals(1.405f, f.modifier().getValue(), 0.00001f);
    }

    private static Item boots(int level) {
        if (level < 0) return Item.get(Item.AIR);
        Item item = Item.get(Item.DIAMOND_BOOTS);
        if (level > 0) item.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_SOUL_SPEED).setLevel(level));
        return item;
    }

    static class Harness extends Player {
        Harness() { super(null, 0L, null); }
        @Override public void recalculateMovementSpeed() { }
        void setup(Level world, PlayerInventory items, int version) {
            level = world;
            inventory = items;
            protocol = version;
            y = 64;
        }
        void move() { handleEnchantmentInMove(); }
    }

    private static final class Fixture {
        final Harness player = mock(Harness.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final AtomicInteger ground = new AtomicInteger(BlockID.SOUL_SAND);

        Fixture(int protocol) throws Exception {
            Field modifiers = EntityLiving.class.getDeclaredField("movementSpeedModifiers");
            modifiers.setAccessible(true);
            modifiers.set(player, new HashMap<String, EntityMovementSpeedModifier>());
            Level world = mock(Level.class);
            when(world.getBlockIdAt(any(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> ground.get());
            player.setup(world, inventory, protocol);
        }

        void wear(int level) { when(inventory.getBootsFast()).thenReturn(boots(level)); }
        void move() { player.move(); }
        EntityMovementSpeedModifier modifier() { return player.getMovementSpeedModifiers().get(SOUL); }
    }
}
