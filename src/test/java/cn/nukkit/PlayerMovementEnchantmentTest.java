package cn.nukkit;

import cn.nukkit.block.*;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.data.EntityMovementSpeedModifier;
import cn.nukkit.event.block.WaterFrostEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise real movement enchantment handling and named modifier writes. */
class PlayerMovementEnchantmentTest {
    @BeforeAll static void init() { MockServer.init(); }

    static class FrostHarness extends Player {
        FrostHarness() { super(null,0L,null); }
        @Override public boolean isSpectator() { return false; }
        void setup(Level world) {
            level=world;server=Server.getInstance();y=-20;
            inventory=mock(PlayerInventory.class);
            Item boots=Item.get(Item.DIAMOND_BOOTS);
            boots.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_FROST_WALKER).setLevel(2));
            when(inventory.getBootsFast()).thenReturn(boots);
        }
    }
    static FrostHarness player(Level level) throws Exception {
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        FrostHarness p=(FrostHarness)((sun.misc.Unsafe)field.get(null)).allocateInstance(FrostHarness.class);
        p.setup(level);return p;
    }
    void verifyScan(Block below,Block above,int upperReads,int frostEvents) throws Exception {
        Level level=mock(Level.class);
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        AtomicInteger lower=new AtomicInteger(),upper=new AtomicInteger(),events=new AtomicInteger();
        when(level.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            int y=call.getArgument(1);
            if(y==-21) { lower.incrementAndGet();return below; }
            assertEquals(-20,y);upper.incrementAndGet();return above;
        });
        var manager=Server.getInstance().getPluginManager();
        doAnswer(call->{
            if(call.getArgument(0) instanceof WaterFrostEvent event) {
                events.incrementAndGet();event.setCancelled(true);
            }
            return null;
        }).when(manager).callEvent(any());
        player(level).handleEnchantmentInMove();
        assertEquals(81,lower.get());assertEquals(upperReads,upper.get());assertEquals(frostEvents,events.get());
        verify(level,never()).setBlock(any(),any(),anyBoolean(),anyBoolean());
    }
    @Test void dryGroundNeverReadsAbove() throws Exception { verifyScan(new BlockStone(),new BlockAir(),0,0); }
    @Test void flowingWaterNeverReadsAbove() throws Exception { verifyScan(new BlockWater(2),new BlockAir(),0,0); }
    @Test void coveredSourceDoesNotFreeze() throws Exception { verifyScan(new BlockWater(),new BlockStone(),81,0); }
    @Test void sourceWaterKeepsCancellableEvents() throws Exception { verifyScan(new BlockWater(),new BlockAir(),81,81); }
    @Test void acceptedEventsFreezeInScanOrderAndScheduleExactlyOnce() throws Exception {
        Level level=mock(Level.class);
        when(level.getMinBlockY()).thenReturn(-64);when(level.getMaxBlockY()).thenReturn(319);
        Map<String,Block> frozen=new HashMap<>();List<String> order=new ArrayList<>();
        when(level.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            int x=call.getArgument(0),y=call.getArgument(1),z=call.getArgument(2);
            Block block=y==-20 ? new BlockAir() : frozen.getOrDefault(x+":"+z,new BlockWater());
            block.x=x;block.y=y;block.z=z;block.level=level;return block;
        });
        when(level.getBlock(any(cn.nukkit.math.Vector3.class))).thenAnswer(call->{
            cn.nukkit.math.Vector3 pos=call.getArgument(0);
            return level.getBlock(pos.getFloorX(),pos.getFloorY(),pos.getFloorZ());
        });
        when(level.setBlock(any(),any(),eq(true),eq(false))).thenAnswer(call->{
            cn.nukkit.math.Vector3 pos=call.getArgument(0);Block ice=call.getArgument(1);
            assertEquals(Block.FROSTED_ICE,ice.getId());
            frozen.put(pos.getFloorX()+":"+pos.getFloorZ(),ice);return true;
        });
        var manager=Server.getInstance().getPluginManager();
        doAnswer(call->{
            if(call.getArgument(0) instanceof WaterFrostEvent event) {
                order.add(event.getBlock().getFloorX()+":"+event.getBlock().getFloorZ());
            }
            return null;
        }).when(manager).callEvent(any());
        FrostHarness p=player(level);p.handleEnchantmentInMove();
        List<String> expected=new ArrayList<>();
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)expected.add(x+":"+z);
        assertEquals(expected,order);assertEquals(81,frozen.size());
        verify(level,times(81)).scheduleUpdate(any(Block.class),intThat(n->n>=20&&n<40));
        p.handleEnchantmentInMove();
        assertEquals(expected,order,"Already frozen cells must not fire again");
        verify(level,times(81)).scheduleUpdate(any(Block.class),anyInt());
    }

    private static final String SOUL = EntityMovementSpeedModifier.SOUL_SPEED_ENCHANTMENT;

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
