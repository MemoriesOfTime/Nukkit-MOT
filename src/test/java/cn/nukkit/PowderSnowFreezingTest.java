package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockPowderSnow;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageByBlockEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityFreezeEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowderSnowFreezingTest {

    @BeforeEach
    void setUp() {
        MockServer.reset();
    }

    @Test
    void exposureAccumulatesAndDecaysAtVanillaRates() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;

        player.entityBaseTick(1);
        player.entityBaseTick(1);
        player.entityBaseTick(1);
        assertEquals(3, player.getFreezingTicks());

        player.powderSnow = false;
        player.entityBaseTick(1);
        assertEquals(1, player.getFreezingTicks(), "leaving powder snow decays by two ticks");
        player.entityBaseTick(1);
        assertEquals(0, player.getFreezingTicks());
        player.entityBaseTick(1);
        assertEquals(0, player.getFreezingTicks(), "ticks never go below zero");
    }

    @Test
    void immuneEntityDoesNotAccumulateAndDecays() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.canFreeze = false;
        player.setFreezingTicks(10);

        player.entityBaseTick(1);
        assertEquals(8, player.getFreezingTicks());
    }

    @Test
    void setFreezingTicksClampsAndKeepsStrengthInRange() {
        TestPlayer player = new TestPlayer();

        player.setFreezingTicks(999);
        assertEquals(140, player.getFreezingTicks());
        assertEquals(1.0f, player.getFreezingEffectStrength());

        player.setFreezingTicks(-5);
        assertEquals(0, player.getFreezingTicks());
        assertEquals(0.0f, player.getFreezingEffectStrength());
    }

    @Test
    void fullyFrozenDamageUsesFreezeDamageRuleAndBlockSource() {
        TestPlayer player = new TestPlayer();
        GameRules rules = player.level.getGameRules();
        player.powderSnow = true;
        player.setFreezingTicks(140);

        Mockito.when(MockServer.get().getTick()).thenReturn(1);
        player.entityBaseTick(1);
        assertTrue(player.damages.isEmpty(), "off the 40-tick boundary does not hurt");

        Mockito.when(MockServer.get().getTick()).thenReturn(40);
        player.entityBaseTick(1);
        assertEquals(1, player.damages.size());
        EntityDamageEvent event = player.damages.get(0);
        assertInstanceOf(EntityDamageByBlockEvent.class, event);
        assertEquals(DamageCause.FREEZING, event.getCause());
        assertEquals(1.0f, event.getDamage());

        rules.setGameRule(GameRule.FREEZE_DAMAGE, false);
        player.damages.clear();
        Mockito.when(MockServer.get().getTick()).thenReturn(80);
        player.entityBaseTick(1);
        assertTrue(player.damages.isEmpty(), "FREEZE_DAMAGE=false disables freezing damage for players");
    }

    @Test
    void burningEntityMeltsPowderSnowAndExtinguishes() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.fireTicks = 10;
        // Players always melt powder snow, even with mobGriefing disabled.
        player.level.getGameRules().setGameRule(GameRule.MOB_GRIEFING, false);

        player.entityBaseTick(1);

        assertEquals(0, player.fireTicks);
        Mockito.verify(player.level).setBlock(
                Mockito.argThat((Vector3 pos) -> pos instanceof Block block && block.getId() == Block.POWDER_SNOW),
                Mockito.argThat((Block block) -> block.getId() == Block.AIR),
                Mockito.eq(true), Mockito.eq(true));
    }

    @Test
    void burningMobLeavesPowderSnowWhenMobGriefingDisabled() {
        TestPlayer fixture = new TestPlayer();
        fixture.powderSnow = true;
        fixture.level.isBeingConverted = true;
        fixture.level.getGameRules().setGameRule(GameRule.MOB_GRIEFING, false);
        StationaryLiving mob = new StationaryLiving(fixture.chunk);
        mob.fireTicks = 10;

        mob.entityBaseTick(1);

        assertEquals(0, mob.fireTicks, "burning mobs are still extinguished");
        Mockito.verify(fixture.level, Mockito.never()).setBlock(
                Mockito.argThat((Vector3 pos) -> pos instanceof Block block && block.getId() == Block.POWDER_SNOW),
                Mockito.any(Block.class), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void cancelledFreezeEventPreventsSlowdownAtAndAfterFullFreeze() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.setFreezingTicks(139);
        var pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityFreezeEvent event && event.getEntity() == player) {
                event.setCancelled();
            }
            return null;
        }).when(pluginManager).callEvent(Mockito.any());

        for (int tick = 140; tick <= 142; tick++) {
            Mockito.when(MockServer.get().getTick()).thenReturn(tick);
            player.entityBaseTick(1);
            assertEquals(140, player.getFreezingTicks());
            assertEquals(Player.DEFAULT_SPEED, player.recalculateMovementSpeedToSend(), 1e-6f,
                    "cancelling the freeze event must prevent slowdown even at full freeze");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void leatherArmorThawsCompletelyWithoutLeavingSlowdown(int armorSlot) {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.entityBaseTick(3);
        assertTrue(player.recalculateMovementSpeedToSend() < Player.DEFAULT_SPEED);
        int[] leatherArmor = {Item.LEATHER_HELMET, Item.LEATHER_CHESTPLATE,
                Item.LEATHER_LEGGINGS, Item.LEATHER_BOOTS};
        player.getInventory().setArmorItem(armorSlot, Item.get(leatherArmor[armorSlot]), false);
        assertFalse(player.canFreeze(), "each leather armor slot prevents freezing");

        player.entityBaseTick(1);
        player.entityBaseTick(1);

        assertEquals(0, player.getFreezingTicks());
        assertEquals(Player.DEFAULT_SPEED, player.recalculateMovementSpeedToSend(), 1e-6f,
                "thawing inside powder snow must remove the freezing speed modifier");
    }

    @Test
    void spectatorModeClearsFreezingSlowdown() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.entityBaseTick(3);
        assertTrue(player.recalculateMovementSpeedToSend() < Player.DEFAULT_SPEED);

        player.gamemode = Player.SPECTATOR;
        player.entityBaseTick(2);

        assertEquals(0, player.getFreezingTicks());
        assertEquals(Player.DEFAULT_SPEED, player.recalculateMovementSpeedToSend(), 1e-6f,
                "skipping spectator block collisions must not leave a freezing speed modifier");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 200})
    void startingToFreezeStopsSprintingEvenWhenTicksAreCaughtUp(int tickDiff) {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.setSprinting(true);

        player.entityBaseTick(tickDiff);

        assertFalse(player.isSprinting(), "starting to freeze must stop sprinting regardless of tickDiff");
    }

    @Test
    void cancellingFreezeEventRemovesPreviouslyAppliedSlowdown() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.entityBaseTick(20);
        assertTrue(player.recalculateMovementSpeedToSend() < Player.DEFAULT_SPEED);
        var pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityFreezeEvent event && event.getEntity() == player) {
                event.setCancelled();
            }
            return null;
        }).when(pluginManager).callEvent(Mockito.any());

        player.entityBaseTick(1);

        assertEquals(Player.DEFAULT_SPEED, player.recalculateMovementSpeedToSend(), 1e-6f,
                "cancelling freezing must also clear the previous tick's slowdown");
    }

    @Test
    void pluginCanClearFreezingBeforeTheDamageAttempt() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.setFreezingTicks(139);
        Mockito.when(MockServer.get().getTick()).thenReturn(40);
        var pluginManager = MockServer.get().getPluginManager();
        Mockito.doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityFreezeEvent event && event.getEntity() == player) {
                event.getEntity().setFreezingTicks(0);
            }
            return null;
        }).when(pluginManager).callEvent(Mockito.any());

        player.entityBaseTick(1);

        assertEquals(0, player.getFreezingTicks());
        assertTrue(player.damages.isEmpty(), "freeze listeners must run before the freezing damage attempt");
        assertEquals(Player.DEFAULT_SPEED, player.recalculateMovementSpeedToSend(), 1e-6f);
    }

    @Test
    void touchingAdjacentPowderSnowFreezesUntilTheBodyLeaves() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.powderSnowX = 1;
        assertEquals(Block.AIR, player.getLevelBlock().getId(), "the feet center is outside powder snow");

        player.entityBaseTick(1);

        assertTrue(player.isInPowderSnow(), "body contact with the adjacent cell counts as exposure");
        assertEquals(1, player.getFreezingTicks());

        player.x = 0.5;
        player.recalculateBoundingBox(false);
        player.entityBaseTick(1);

        assertFalse(player.isInPowderSnow(), "nearby powder snow without contact is not exposure");
        assertEquals(0, player.getFreezingTicks());
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 200})
    void caughtUpTicksAdvanceExposureAndThawingByElapsedTime(int tickDiff) {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;

        player.entityBaseTick(tickDiff);

        assertEquals(Math.min(140, tickDiff), player.getFreezingTicks(),
                "exposure counts every elapsed tick and stops at full freeze");

        player.setFreezingTicks(100);
        player.powderSnow = false;
        player.entityBaseTick(tickDiff);

        assertEquals(Math.max(0, 100 - tickDiff * 2), player.getFreezingTicks(),
                "thawing removes two freezing ticks per elapsed tick and stops at zero");
    }

    @ParameterizedTest
    @CsvSource({"120, 0", "139, 1", "140, 1"})
    void caughtUpDamageOnlyCountsBoundariesAfterReachingFullFreeze(int initialTicks, int expectedHits) {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.setFreezingTicks(initialTicks);
        // The update covers ticks 40..59; starting at 120 reaches full freeze only at tick 59.
        Mockito.when(MockServer.get().getTick()).thenReturn(59);

        player.entityBaseTick(20);

        assertEquals(140, player.getFreezingTicks());
        assertEquals(expectedHits, player.damages.size(), "tick 40 only hurts if already fully frozen then");
    }

    @Test
    void longCatchUpAppliesAtMostOneFreezingHit() {
        TestPlayer player = new TestPlayer();
        player.powderSnow = true;
        player.setFreezingTicks(140);
        Mockito.when(MockServer.get().getTick()).thenReturn(200);

        player.entityBaseTick(200);

        assertEquals(1, player.damages.size(), "a caught-up update must not burst all missed hits at once");
    }

    @Test
    void stationaryLivingEntityStaysScheduledUntilFullyThawed() {
        TestPlayer fixture = new TestPlayer();
        fixture.powderSnow = true;
        fixture.level.isBeingConverted = true;
        StationaryLiving entity = new StationaryLiving(fixture.chunk);

        for (int tick = 1; tick <= 142; tick++) {
            assertTrue(entity.onUpdate(tick), "freezing must keep a stationary entity scheduled at tick " + tick);
        }
        assertEquals(140, entity.getFreezingTicks());

        fixture.powderSnow = false;
        for (int tick = 143; tick < 212; tick++) {
            assertTrue(entity.onUpdate(tick), "thawing must stay scheduled until its last tick");
        }
        assertFalse(entity.onUpdate(212), "fully thawed stationary entities may leave the update list");
        assertEquals(0, entity.getFreezingTicks());
    }

    @Test
    void mobsStillTakeFreezingDamageWhenRuleDisabled() {
        TestPlayer fixture = new TestPlayer();
        fixture.powderSnow = true;
        fixture.level.isBeingConverted = true;
        fixture.level.getGameRules().setGameRule(GameRule.FREEZE_DAMAGE, false);
        StationaryLiving mob = new StationaryLiving(fixture.chunk);
        mob.setFreezingTicks(140);
        Mockito.when(MockServer.get().getTick()).thenReturn(40);

        mob.entityBaseTick(1);

        assertEquals(1, mob.damages.size(), "BE freezedamage only protects players");
        assertEquals(DamageCause.FREEZING, mob.damages.get(0).getCause());
    }

    private static final class StationaryLiving extends EntityLiving {
        private final List<EntityDamageEvent> damages = new ArrayList<>();

        private StationaryLiving(FullChunk chunk) {
            super(chunk, Entity.getDefaultNBT(new Vector3(0.9, 64, 0.5)));
        }

        @Override
        public boolean attack(EntityDamageEvent source) {
            this.damages.add(source);
            return false;
        }

        @Override
        public int getNetworkId() {
            return -1;
        }

        @Override
        public float getWidth() {
            return 0.6f;
        }

        @Override
        public float getHeight() {
            return 1.8f;
        }
    }

    private static final class TestPlayer extends Player {
        private boolean powderSnow;
        private int powderSnowX;
        private boolean canFreeze = true;
        private final List<EntityDamageEvent> damages = new ArrayList<>();

        private TestPlayer() {
            super(Mockito.mock(SourceInterface.class), 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.level = Mockito.mock(Level.class);
            Mockito.when(this.level.getServer()).thenReturn(MockServer.get());
            LevelProvider provider = Mockito.mock(LevelProvider.class);
            Mockito.when(provider.getLevel()).thenReturn(this.level);
            Mockito.when(this.level.getProvider()).thenReturn(provider);
            Mockito.when(this.level.getGameRules()).thenReturn(GameRules.getDefault());
            Mockito.when(this.level.getMaxBlockY()).thenReturn(255);
            Mockito.when(this.level.getNearbyRideables(Mockito.any(), Mockito.any())).thenReturn(new Entity[0]);
            BaseFullChunk fixtureChunk = Mockito.mock(BaseFullChunk.class);
            this.chunk = fixtureChunk;
            Mockito.when(this.chunk.getProvider()).thenReturn(provider);
            Mockito.when(this.chunk.getBlockId(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(call -> call.<Integer>getArgument(3) == 0
                            ? this.blockAt(call.getArgument(0), call.getArgument(1), call.getArgument(2)).getId()
                            : Block.AIR);
            Mockito.when(this.level.getChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()))
                    .thenReturn(fixtureChunk);
            Mockito.when(this.level.getBlock(Mockito.any(Vector3.class)))
                    .thenAnswer(call -> {
                        Vector3 pos = call.getArgument(0);
                        return this.blockAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
                    });
            Mockito.when(this.level.getBlock(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                            Mockito.anyInt(), Mockito.anyBoolean()))
                    .thenAnswer(call -> this.blockAt(call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            this.x = 0.9;
            this.y = 64;
            this.z = 0.5;
            this.temporalVector = new Vector3();
            this.boundingBox = new SimpleAxisAlignedBB(0.6, 64, 0.2, 1.2, 65.8, 0.8);
            this.inventory = new PlayerInventory(this);
        }

        private Block blockAt(int x, int y, int z) {
            Block block = this.powderSnow && x == this.powderSnowX && y == 64 && z == 0
                    ? new BlockPowderSnow() : new BlockAir();
            block.x = x;
            block.y = y;
            block.z = z;
            block.level = this.level;
            return block;
        }

        @Override
        public boolean canFreeze() {
            return this.canFreeze && super.canFreeze();
        }

        @Override
        public boolean attack(EntityDamageEvent source) {
            this.damages.add(source);
            return true;
        }

        @Override
        public boolean dataPacket(cn.nukkit.network.protocol.DataPacket packet) {
            return true;
        }

        @Override
        public void sendMovementSpeed() {
        }
    }
}
