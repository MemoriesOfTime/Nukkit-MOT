package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.*;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.block.properties.enums.DripstoneThickness;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.AutomaticBlockDropsEvent;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Hash;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.serverconfig.ServerConfig;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutomaticBlockDropsEvent 归因回归：普通 dropItem 不做坐标归因，同格重放后的新掉落仍派发事件。
 * <p>
 * Regression guards for automatic-drop attribution: plain dropItem never infers a source from
 * coordinates, and drops from a re-placed cell still fire a cancellable event.
 */
class AutomaticBlockDropsAttributionTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void unrelatedPluginDropsDuringAnActiveChainAreNotAttributed() {
        int dripstoneItem = dripstone(DripstoneThickness.BASE, false).toItem().getId();

        Fixture fixture = new Fixture(event -> {
            for (Item drop : event.getDrops()) {
                if (drop.getId() == Item.DIAMOND) {
                    // 假装反刷屏插件：取消一切「自动方块掉落」名义下的钻石
                    event.setCancelled();
                }
            }
        });
        // 旁边一格是完好的石头，作为无关掉落的目标
        fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 64, 0);
        fixture.put(dripstone(DripstoneThickness.MIDDLE, false), 5, 65, 0);
        fixture.put(dripstone(DripstoneThickness.TIP, false), 5, 66, 0);
        fixture.put(Block.get(BlockID.STONE), 6, 66, 8);

        // 链内第一次 setBlock 时，同步回调里的插件往石头格子上丢一颗钻石
        AtomicBoolean pluginDropFired = new AtomicBoolean();
        fixture.onFirstSetBlock = () -> {
            if (pluginDropFired.compareAndSet(false, true)) {
                fixture.level.dropItem(new Vector3(6.5, 66.5, 8.5), Item.get(Item.DIAMOND));
            }
        };

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class,
                (mock, context) -> fixture.spawned.add(
                        NBTIO.getItemHelper(((CompoundTag) context.arguments().get(1)).getCompound("Item"))))) {
            fixture.level.useBreakOn(new Vector3(5, 64, 0), BlockFace.UP, null, null, false);
        }

        assertTrue(pluginDropFired.get(), "the plugin drop must have been injected mid-chain");
        assertTrue(
                fixture.events.stream().noneMatch(event -> event.getBlock().getId() == BlockID.STONE),
                "an unrelated drop must never be attributed to the intact stone");
        assertTrue(
                fixture.spawned.stream().anyMatch(item -> item.getId() == Item.DIAMOND),
                "the plugin's diamond must spawn even though the listener cancels automatic diamond drops");
        assertTrue(
                fixture.spawned.stream().anyMatch(item -> item.getId() == dripstoneItem),
                "the dripstone column's own automatic drops still spawn");
    }

    @Test
    void aReplacedCellsNewDropStillFiresACancellableEvent() {
        int dripstoneItem = dripstone(DripstoneThickness.BASE, false).toItem().getId();
        int sunflowerItem = doublePlant(0).toItem().getId();

        Fixture fixture = new Fixture(event -> {
            if (event.getBlock().getId() == BlockID.POINTED_DRIPSTONE) {
                event.setCancelled();
            }
        });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);

        AtomicBoolean replayed = new AtomicBoolean();
        AtomicReference<List<AutomaticBlockDropsEvent>> eventsAtReplay = new AtomicReference<>();
        fixture.onFirstSpawn = () -> {
            if (replayed.compareAndSet(false, true)) {
                eventsAtReplay.set(List.copyOf(fixture.events));
                // 内层终批开始生成时同格重放新滴水石，再破坏其下方一格触发逐段直接掉落
                fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 64, 0);
                fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 63, 0);
                fixture.level.useBreakOn(new Vector3(5, 63, 0), BlockFace.UP, null, null, false);
            }
        };

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            fixture.level.useBreakOn(new Vector3(5, 65, 0), BlockFace.UP, null, null, false);
        }

        assertTrue(replayed.get(), "the re-placed break must have been injected");
        assertEquals(
                1,
                eventsAtReplay.get().size(),
                "the inner completion event must fire before its first drop starts spawning");
        assertFalse(
                eventsAtReplay.get().get(0).isOutermostBreak(),
                "the replay must start after the inner scope closes but before the outer scope completes");
        AutomaticBlockDropsEvent replacedDropEvent = fixture.events.stream()
                .filter(event -> event.getBlock().getId() == BlockID.POINTED_DRIPSTONE
                        && event.getBlock().getFloorY() == 64)
                .findFirst()
                .orElse(null);
        assertNotNull(replacedDropEvent, "the direct drop of the re-placed block must fire an event");
        assertTrue(replacedDropEvent.isCancelled(), "the listener must have been able to cancel that event");
        assertFalse(
                fixture.spawned.stream().anyMatch(item -> item.getId() == dripstoneItem),
                "the cancelled re-placed drop must not spawn");
        assertTrue(
                fixture.spawned.stream().anyMatch(item -> item.getId() == sunflowerItem),
                "the uncancelled inner drop still spawns");
    }

    @Test
    void listenerReplacementControlsTheSpawnedAutomaticDrop() {
        int dirtItem = Block.get(BlockID.DIRT).toItem().getId();
        Fixture fixture = new Fixture(event -> {
            if (event.getBlock().getId() == BlockID.DIRT) {
                event.setDrops(new Item[] {Item.get(Item.DIAMOND)});
            }
        });
        fixture.put(Block.get(BlockID.DIRT), 5, 64, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(
                    new Vector3(5, 64, 0), BlockFace.UP, null, null, false));
        }

        assertEquals(1, fixture.events.size());
        assertEquals(1, fixture.spawned.size(), "exactly the listener's replacement batch must spawn");
        assertEquals(Item.DIAMOND, fixture.spawned.get(0).getId());
        assertTrue(fixture.spawned.stream().noneMatch(item -> item.getId() == dirtItem),
                "the replaced original drop must not spawn");
    }

    @Test
    void pumpkinShearingEmitsCancellableInteractionDrop() {
        Fixture fixture = new Fixture(event -> event.setCancelled());
        Block pumpkin = Block.get(BlockID.PUMPKIN);
        fixture.put(pumpkin, 5, 64, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertTrue(pumpkin.onActivate(Item.get(Item.SHEARS), null));
        }

        assertEquals(BlockID.CARVED_PUMPKIN, fixture.cells.get(Hash.hashBlock(5, 64, 0)).getId());
        assertEquals(1, fixture.events.size(), "shearing must emit one interaction output event");
        AutomaticBlockDropsEvent event = fixture.events.get(0);
        assertSame(pumpkin, event.getBlock());
        assertEquals(AutomaticBlockDropsEvent.Cause.BLOCK_INTERACTION, event.getCause());
        assertEquals(0, event.getChangedBlocks().length);
        assertFalse(event.isOutermostBreak());
        assertEquals(1, event.getDrops().length);
        assertEquals(Item.PUMPKIN_SEEDS, event.getDrops()[0].getId());
        assertTrue(event.isCancelled());
        assertTrue(fixture.spawned.isEmpty(), "cancelling the interaction event must suppress its seeds");
    }

    @Test
    void disabledTileDropsStillEmitTerminalBookkeepingWithoutSpawning() {
        int dirtItem = Block.get(BlockID.DIRT).toItem().getId();
        Fixture fixture = new Fixture(event -> { });
        fixture.gameRules.setGameRule(GameRule.DO_TILE_DROPS, false);
        fixture.put(Block.get(BlockID.DIRT), 5, 64, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(
                    new Vector3(5, 64, 0), BlockFace.UP, null, null, false));
        }

        assertTrue(fixture.spawned.isEmpty(), "disabled tile drops must not spawn items");
        assertEquals(1, fixture.events.size(), "drop suppression must not skip terminal bookkeeping");
        AutomaticBlockDropsEvent terminal = fixture.events.get(0);
        assertTrue(terminal.isOutermostBreak());
        assertEquals(1, terminal.getDrops().length, "the event still exposes the suppressed batch");
        assertEquals(dirtItem, terminal.getDrops()[0].getId());
        assertEquals(1, terminal.getChangedBlocks().length);
        Block changed = terminal.getChangedBlocks()[0];
        assertEquals(BlockID.DIRT, changed.getId());
        assertEquals(5, changed.getFloorX());
        assertEquals(64, changed.getFloorY());
        assertEquals(0, changed.getFloorZ());
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void shovelActivationUsesPlayerBreakEvent(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;
        Item shovel = Item.get(Item.DIAMOND_SHOVEL);
        shovel.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(1));

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertTrue(block.onActivate(shovel, player));
        }

        assertEquals(1, shovel.getDamage(), "a successful break must consume durability exactly once");
        assertEquals(1, fixture.playerBreakEvents.size(), "the activation must enter the player break path");
        BlockBreakEvent breakEvent = fixture.playerBreakEvents.get(0);
        assertSame(player, breakEvent.getPlayer());
        Enchantment eventEfficiency = breakEvent.getItem().getEnchantment(Enchantment.ID_EFFICIENCY);
        assertNotNull(eventEfficiency, "the player event must preserve tool enchantments");
        assertEquals(1, eventEfficiency.getLevel(), "the event tool must retain the original enchantment level");
        assertTrue(breakEvent.getDrops().length > 0, "the player event must expose the primary drop batch");

        assertEquals(1, fixture.events.size(), "only the terminal bookkeeping event should remain");
        AutomaticBlockDropsEvent terminal = fixture.events.get(0);
        assertTrue(terminal.isOutermostBreak());
        assertEquals(0, terminal.getDrops().length, "the automatic event must not duplicate the player batch");
        assertEquals(1, terminal.getChangedBlocks().length, "the player terminal event must retain provenance");
        Block changed = terminal.getChangedBlocks()[0];
        assertEquals(blockId, changed.getId());
        assertEquals(5, changed.getFloorX());
        assertEquals(64, changed.getFloorY());
        assertEquals(0, changed.getFloorZ());
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void shovelActivationIsInstantEvenWithMiningFatigue(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        Effect fatigue = Mockito.mock(Effect.class);
        Mockito.when(fatigue.getAmplifier()).thenReturn(2);
        Mockito.when(player.getEffect(Effect.MINING_FATIGUE)).thenReturn(fatigue);
        Item shovel = Item.get(Item.WOODEN_SHOVEL);
        player.lastBreak = System.currentTimeMillis();

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertTrue(block.onActivate(shovel, player));
        }

        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId(),
                "using a shovel must remove snow instantly without waiting for mining progress");
        assertEquals(1, shovel.getDamage());
        assertEquals(1, fixture.playerBreakEvents.size());
        BlockBreakEvent event = fixture.playerBreakEvents.get(0);
        assertTrue(event.getInstaBreak(), "the interaction must be exposed as an instant break");
        assertFalse(event.isFastBreak(), "instant interactions have no mining progress to validate");
        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void silkTouchShovelKeepsSeparateInteractionAndMiningDrops(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;
        Item shovel = Item.get(Item.DIAMOND_SHOVEL);
        shovel.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_SILK_TOUCH));
        shovel.setCustomName("snow-clearing shovel");

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertTrue(block.onActivate(shovel, player));
        }

        assertEquals(1, fixture.spawned.size());
        Item drop = fixture.spawned.get(0);
        assertEquals(Item.SNOWBALL, drop.getId(), "using a silk-touch shovel must still yield snowballs");
        assertEquals(blockId == BlockID.SNOW_BLOCK ? 4 : 1, drop.getCount());
        assertEquals(1, fixture.playerBreakEvents.size());
        Item eventTool = fixture.playerBreakEvents.get(0).getItem();
        assertTrue(eventTool.hasEnchantment(Enchantment.ID_SILK_TOUCH));
        assertEquals("snow-clearing shovel", eventTool.getCustomName());
        assertTrue(shovel.hasEnchantment(Enchantment.ID_SILK_TOUCH));
        assertEquals("snow-clearing shovel", shovel.getCustomName());

        fixture.put(Block.get(blockId), 5, 64, 0);
        fixture.spawned.clear();
        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0), BlockFace.UP,
                    shovel.clone(), player, false));
        }
        assertEquals(1, fixture.spawned.size());
        assertEquals(block.toItem().getId(), fixture.spawned.get(0).getId(),
                "mining with silk touch must keep yielding the original block");
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void cancelledShovelInteractionReturnsFailureWithoutConsumingDurability(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;
        PluginManager pluginManager = fixture.server.getPluginManager();
        Mockito.doAnswer(invocation -> {
                    Event event = invocation.getArgument(0);
                    if (event instanceof BlockBreakEvent breakEvent) {
                        breakEvent.setCancelled();
                    }
                    return null;
                })
                .when(pluginManager)
                .callEvent(Mockito.any(Event.class));
        Item shovel = Item.get(Item.DIAMOND_SHOVEL);
        int oldDamage = shovel.getDamage();

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertNull(fixture.level.useItemOn(block, shovel, BlockFace.UP, 0, 0, 0, player, true),
                    "a denied interaction must return failure so the caller resends the predicted block");
        }

        assertEquals(blockId, fixture.cells.get(Hash.hashBlock(5, 64, 0)).getId());
        assertEquals(oldDamage, shovel.getDamage(), "a denied break must not damage the shovel");
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void shovelActivationStillRespectsSpawnProtection(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Mockito.when(fixture.level.isInSpawnRadius(Mockito.any(Vector3.class))).thenReturn(true);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;
        Item shovel = Item.get(Item.DIAMOND_SHOVEL);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertFalse(block.onActivate(shovel, player));
        }

        assertEquals(blockId, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertEquals(0, shovel.getDamage());
        assertEquals(1, fixture.playerBreakEvents.size());
        assertTrue(fixture.playerBreakEvents.get(0).isCancelled());
        assertTrue(fixture.events.isEmpty(), "a protected block must not enter a break chain");
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void creativeShovelActivationDoesNotSpawnSilkTouchDrops(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        player.gamemode = Player.CREATIVE;
        Mockito.when(player.getGamemode()).thenReturn(Player.CREATIVE);
        Mockito.when(player.isCreative()).thenReturn(true);
        player.lastBreak = -1;
        Item shovel = Item.get(Item.DIAMOND_SHOVEL);
        shovel.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_SILK_TOUCH));

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertTrue(block.onActivate(shovel, player));
        }

        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertEquals(1, fixture.playerBreakEvents.size());
        assertEquals(0, fixture.playerBreakEvents.get(0).getDrops().length);
        assertTrue(fixture.spawned.isEmpty(), "creative interaction must not spawn block drops");
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void shovelActivationKeepsTheExistingMiningTimer(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        long miningStarted = System.currentTimeMillis() - 180;
        player.lastBreak = miningStarted;
        Item shovel = Item.get(Item.WOODEN_SHOVEL);
        shovel.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(1));

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertTrue(block.onActivate(shovel, player));
        }

        assertEquals(1, fixture.playerBreakEvents.size());
        assertFalse(
                fixture.playerBreakEvents.get(0).isCancelled(),
                "the shovel interaction must succeed");
        assertEquals(BlockID.AIR, fixture.cells.get(Hash.hashBlock(5, 64, 0)).getId());
        assertEquals(miningStarted, player.lastBreak,
                "using a shovel must not reset the timer of an existing mining action");
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void lastDurabilityShovelInteractionEmitsOneToolBreakEffect(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;
        Item shovel = Item.get(Item.WOODEN_SHOVEL);
        shovel.setDamage(shovel.getMaxDurability() - 1);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            Item result = fixture.level.useItemOn(block, shovel, BlockFace.UP, 0, 0, 0, player, true);
            assertNotNull(result);
            assertTrue(result.isNull(), "the exhausted shovel must be removed from the hand");
        }

        Mockito.verify(fixture.level, Mockito.times(1)).addSoundToViewers(block, Sound.RANDOM_BREAK);
        Mockito.verify(fixture.level, Mockito.times(1)).addParticle(Mockito.any(ItemBreakParticle.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void unbreakingShovelEffectsMatchTheActualHeldToolWear(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            for (int attempt = 0; attempt < 32; attempt++) {
                Block block = Block.get(blockId);
                fixture.put(block, 5, 64, 0);
                Item shovel = Item.get(Item.WOODEN_SHOVEL);
                shovel.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_DURABILITY).setLevel(3));
                shovel.setDamage(shovel.getMaxDurability() - 1);
                Mockito.clearInvocations(fixture.level);

                Item result = fixture.level.useItemOn(block, shovel, BlockFace.UP, 0, 0, 0, player, true);
                assertNotNull(result);
                assertEquals(result.isNull(), shovel.getDamage() == shovel.getMaxDurability());
                int expectedEffects = result.isNull() ? 1 : 0;
                Mockito.verify(fixture.level, Mockito.times(expectedEffects))
                        .addSoundToViewers(block, Sound.RANDOM_BREAK);
                Mockito.verify(fixture.level, Mockito.times(expectedEffects))
                        .addParticle(Mockito.any(ItemBreakParticle.class));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.SNOW_BLOCK, BlockID.SNOW_LAYER})
    void shovelInteractionSupportsTheNoPlayerItemUseEntry(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(blockId);
        fixture.put(block, 5, 64, 0);
        Item shovel = Item.get(Item.WOODEN_SHOVEL);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertSame(shovel, fixture.level.useItemOn(block, shovel, BlockFace.UP, 0, 0, 0, null, true));
        }

        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertEquals(1, shovel.getDamage());
        assertTrue(fixture.playerBreakEvents.isEmpty());
        assertEquals(1, fixture.events.size());
        assertTrue(fixture.events.get(0).isOutermostBreak());
        assertEquals(1, fixture.spawned.size());
        assertEquals(Item.SNOWBALL, fixture.spawned.get(0).getId());
        assertEquals(blockId == BlockID.SNOW_BLOCK ? 4 : 1, fixture.spawned.get(0).getCount());
    }

    @Test
    void snowLayerStackingSupportsTheNoPlayerItemUseEntry() {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(BlockID.SNOW_LAYER);
        fixture.put(block, 5, 64, 0);
        fixture.put(Block.get(BlockID.STONE), 5, 63, 0);
        Item snow = block.toItem();
        snow.setCount(2);

        assertSame(snow, fixture.level.useItemOn(block, snow, BlockFace.UP, 0, 0, 0, null, true));
        Block stacked = fixture.level.getBlock(new Vector3(5, 64, 0));
        assertEquals(BlockID.SNOW_LAYER, stacked.getId());
        assertEquals(1, stacked.getDamage());
        assertEquals(1, snow.getCount());
        assertTrue(fixture.events.isEmpty(), "stacking snow must not emit a break or drop event");
    }

    @Test
    void adventureShovelRequiresAndRetainsCanDestroy() {
        Fixture fixture = new Fixture(event -> { });
        Block block = Block.get(BlockID.SNOW_BLOCK);
        fixture.put(block, 5, 64, 0);
        Player player = Mockito.mock(Player.class);
        player.gamemode = Player.ADVENTURE;
        Mockito.when(player.getGamemode()).thenReturn(Player.ADVENTURE);
        Mockito.when(player.isAdventure()).thenReturn(true);
        player.lastBreak = -1;
        Item shovel = Item.get(Item.WOODEN_SHOVEL);
        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertFalse(block.onActivate(shovel, player), "adventure interaction requires CanDestroy");
            assertEquals(BlockID.SNOW_BLOCK, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
            assertEquals(0, shovel.getDamage());
            shovel.setNamedTag(new CompoundTag().putList(
                    new ListTag<StringTag>("CanDestroy").add(new StringTag("", "minecraft:snow"))));
            assertTrue(block.onActivate(shovel, player));
        }

        assertEquals(1, fixture.playerBreakEvents.size());
        assertFalse(fixture.playerBreakEvents.get(0).isCancelled());
        assertEquals(
                BlockID.AIR,
                fixture.cells.get(Hash.hashBlock(5, 64, 0)).getId(),
                "CanDestroy must remain visible to adventure validation");
    }

    @Test
    void snowLayerDirectDropFiresBeforeItsBreakCompletionEvent() {
        long snowCell = Hash.hashBlock(5, 64, 0);

        // 插件模型：受限格子上取消一切掉落；outermost 收尾事件完成后清理本链变更格的标记
        // Plugin model: cancel every drop from restricted cells; the outermost completion event
        // clears the markers of all cells changed by the finished chain
        Set<Long> restricted = new HashSet<>();
        restricted.add(snowCell);

        Fixture fixture = new Fixture(event -> {
            if (event.isOutermostBreak()) {
                for (Block changed : event.getChangedBlocks()) {
                    restricted.remove(Hash.hashBlock(
                            changed.getFloorX(), changed.getFloorY(), changed.getFloorZ()));
                }
            }
            Block source = event.getBlock();
            if (restricted.contains(Hash.hashBlock(
                    source.getFloorX(), source.getFloorY(), source.getFloorZ()))) {
                event.setCancelled();
            }
        });

        // 雪层放在冰上，真实 onUpdate 在 useBreakOn 内派发直接掉落，随后派发收尾事件
        // A snow layer on ice: real onUpdate fires the direct drop inside useBreakOn, before completion
        fixture.put(Block.get(BlockID.SNOW_LAYER), 5, 64, 0);
        fixture.put(Block.get(BlockID.ICE), 5, 63, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class,
                (mock, context) -> fixture.spawned.add(
                        NBTIO.getItemHelper(((CompoundTag) context.arguments().get(1)).getCompound("Item"))))) {
            fixture.level.getBlock(new Vector3(5, 64, 0)).onUpdate(Level.BLOCK_UPDATE_NORMAL);
        }

        assertEquals(2, fixture.events.size(), "exactly the direct drop and the completion event");
        AutomaticBlockDropsEvent directDrop = fixture.events.get(0);
        AutomaticBlockDropsEvent completion = fixture.events.get(1);

        assertFalse(directDrop.isOutermostBreak());
        assertEquals(1, directDrop.getDrops().length);
        assertEquals(Item.SNOWBALL, directDrop.getDrops()[0].getId());
        assertTrue(directDrop.isCancelled(), "the direct drop must still be attributable when it fires");
        assertEquals(AutomaticBlockDropsEvent.Cause.AUTOMATIC_BREAK, directDrop.getCause());
        assertTrue(completion.isOutermostBreak(), "the completion event must be the chain's last event");
        assertEquals(AutomaticBlockDropsEvent.Cause.AUTOMATIC_BREAK, completion.getCause());

        assertTrue(fixture.spawned.isEmpty(), "the cancelled snowball must not spawn");
        assertEquals(
                BlockID.AIR,
                fixture.cells.get(snowCell).getId(),
                "the snow layer must still be removed");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void snowLayerDropListenerReplacementSurvivesBreakCompletion(boolean cancelDrop) {
        AtomicBoolean replaced = new AtomicBoolean();
        AtomicReference<Fixture> holder = new AtomicReference<>();
        Fixture fixture = new Fixture(event -> {
            if (event.getBlock().getId() == BlockID.SNOW_LAYER && event.getDrops().length > 0
                    && replaced.compareAndSet(false, true)) {
                holder.get().level.setBlock(event.getBlock(), Block.get(BlockID.STONE), true, false);
                if (cancelDrop) {
                    event.setCancelled();
                }
            }
        });
        holder.set(fixture);
        fixture.put(Block.get(BlockID.SNOW_LAYER), 5, 64, 0);
        fixture.put(Block.get(BlockID.ICE), 5, 63, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            fixture.level.getBlock(new Vector3(5, 64, 0)).onUpdate(Level.BLOCK_UPDATE_NORMAL);
        }

        assertTrue(replaced.get(), "the direct-drop listener must have replaced the source cell");
        assertEquals(BlockID.STONE, fixture.level.getBlock(new Vector3(5, 64, 0)).getId(),
                "a block placed during the drop event must survive break completion");
        assertEquals(2, fixture.events.size(), "the original snow still needs a completion event");
        AutomaticBlockDropsEvent direct = fixture.events.get(0);
        AutomaticBlockDropsEvent completion = fixture.events.get(1);
        assertFalse(direct.isOutermostBreak());
        assertEquals(cancelDrop, direct.isCancelled());
        assertTrue(completion.isOutermostBreak());
        assertEquals(BlockID.SNOW_LAYER, completion.getBlock().getId());
        assertEquals(1, completion.getChangedBlocks().length);
        assertEquals(BlockID.SNOW_LAYER, completion.getChangedBlocks()[0].getId(),
                "completion must report the removed snow, not the replacement stone");
        assertEquals(cancelDrop ? 0 : 1, fixture.spawned.size());
        if (!cancelDrop) {
            assertEquals(Item.SNOWBALL, fixture.spawned.get(0).getId());
        }
    }

    @Test
    void unsupportedSnowStillCompletesBookkeepingWhenTileDropsAreDisabled() {
        Fixture fixture = new Fixture(event -> { });
        fixture.gameRules.setGameRule(GameRule.DO_TILE_DROPS, false);
        fixture.put(Block.get(BlockID.SNOW_LAYER), 5, 64, 0);
        fixture.put(Block.get(BlockID.ICE), 5, 63, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            fixture.level.getBlock(new Vector3(5, 64, 0)).onUpdate(Level.BLOCK_UPDATE_NORMAL);
        }

        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertTrue(fixture.spawned.isEmpty());
        assertEquals(1, fixture.events.size(), "tile-drop suppression must preserve break completion");
        AutomaticBlockDropsEvent completion = fixture.events.get(0);
        assertTrue(completion.isOutermostBreak());
        assertEquals(0, completion.getDrops().length);
        assertEquals(1, completion.getChangedBlocks().length);
        assertEquals(BlockID.SNOW_LAYER, completion.getChangedBlocks()[0].getId());
    }

    @Test
    void doublePlantBothHalvesAreTracedInTheBreakCompletionEvent() {
        long bottomCell = Hash.hashBlock(5, 64, 0);
        long topCell = Hash.hashBlock(5, 65, 0);

        // outermost 收尾事件上报本链全部变更格，插件据此清理来源标记；上半格拖到下一 tick
        // 移除会让标记残留并误伤后续掉落
        // The outermost completion event reports all cells changed by the chain; a top half
        // deferred to a later tick leaves a stale marker that can hit future drops on that cell
        Set<Long> reportedCells = new HashSet<>();

        Fixture fixture = new Fixture(event -> {
            if (event.isOutermostBreak()) {
                for (Block changed : event.getChangedBlocks()) {
                    reportedCells.add(Hash.hashBlock(
                            changed.getFloorX(), changed.getFloorY(), changed.getFloorZ()));
                }
            }
        });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            fixture.level.useBreakOn(new Vector3(5, 64, 0), BlockFace.UP, null, null, false);
        }

        assertEquals(BlockID.AIR, fixture.cells.get(bottomCell).getId());
        assertEquals(
                BlockID.AIR,
                fixture.cells.get(topCell).getId(),
                "the top half must be removed by the same break, not a later normal update");
        assertTrue(reportedCells.contains(bottomCell));
        assertTrue(
                reportedCells.contains(topCell),
                "the completion event must report the top cell so source markers can be cleaned");
    }

    @Test
    void playerDoublePlantBreakReportsBothHalvesWithoutDuplicatingDrops() {
        long bottomCell = Hash.hashBlock(5, 64, 0);
        long topCell = Hash.hashBlock(5, 65, 0);
        int sunflowerItem = doublePlant(0).toItem().getId();

        Fixture fixture = new Fixture(event -> { });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(
                    new Vector3(5, 64, 0), BlockFace.UP, Item.get(Item.AIR), player, false));
        }

        assertEquals(1, fixture.playerBreakEvents.size());
        assertEquals(1, fixture.events.size(), "the player break needs only its terminal bookkeeping event");
        AutomaticBlockDropsEvent terminal = fixture.events.get(0);
        assertTrue(terminal.isOutermostBreak());
        assertEquals(0, terminal.getDrops().length, "the primary batch belongs to BlockBreakEvent");
        Set<Long> changedCells = new HashSet<>();
        for (Block changed : terminal.getChangedBlocks()) {
            changedCells.add(Hash.hashBlock(
                    changed.getFloorX(), changed.getFloorY(), changed.getFloorZ()));
        }
        assertEquals(Set.of(bottomCell, topCell), changedCells);
        assertEquals(1, fixture.spawned.size(), "the primary drop must be spawned exactly once");
        assertEquals(sunflowerItem, fixture.spawned.get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockID.DOUBLE_PLANT, BlockID.PITCHER_PLANT, BlockID.PITCHER_CROP})
    void playerTopHalfBreakKeepsTheHarvestInThePlayerChain(int blockId) {
        Fixture fixture = new Fixture(event -> { });
        Block lower = lowerHalf(blockId);
        Block upper = upperHalf(blockId);
        fixture.put(lower, 5, 64, 0);
        fixture.put(upper, 5, 65, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;
        int harvestItem = lower.getDrops(Item.get(Item.AIR))[0].getId();

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(
                    new Vector3(5, 65, 0), BlockFace.DOWN, Item.get(Item.AIR), player, false));
        }

        assertEquals(1, fixture.playerBreakEvents.size());
        assertEquals(0, fixture.playerBreakEvents.get(0).getDrops().length,
                "the upper half never owns the harvest batch");
        assertEquals(2, fixture.events.size(), "the nested batch and the closing event");
        AutomaticBlockDropsEvent harvest = fixture.events.get(0);
        assertFalse(harvest.isOutermostBreak(), "the nested batch runs before the chain closes");
        assertEquals(AutomaticBlockDropsEvent.Cause.PLAYER_BREAK, harvest.getCause(),
                "a player-started chain must not look like an automatic break");
        assertEquals(1, harvest.getDrops().length);
        AutomaticBlockDropsEvent terminal = fixture.events.get(1);
        assertTrue(terminal.isOutermostBreak());
        assertEquals(AutomaticBlockDropsEvent.Cause.PLAYER_BREAK, terminal.getCause());
        assertEquals(0, terminal.getDrops().length, "the primary batch belongs to BlockBreakEvent");
        assertEquals(1, fixture.spawned.size());
        assertEquals(harvestItem, fixture.spawned.get(0).getId());
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 65, 0)).getId());
    }

    @Test
    void outermostPlayerEventIsBookkeepingAndCannotRollBackTheHarvest() {
        Fixture fixture = new Fixture(event -> {
            if (event.isOutermostBreak()) {
                event.setCancelled();
            }
        });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            fixture.level.useBreakOn(
                    new Vector3(5, 65, 0), BlockFace.DOWN, Item.get(Item.AIR), player, false);
        }

        assertEquals(1, fixture.spawned.size(),
                "the nested batch is already created before the closing event fires");
        assertEquals(AutomaticBlockDropsEvent.Cause.PLAYER_BREAK, fixture.events.get(0).getCause());
        assertFalse(fixture.events.get(0).isOutermostBreak());
        assertTrue(fixture.events.get(1).isOutermostBreak());
        assertTrue(fixture.events.get(1).isCancelled());
    }

    @Test
    void automaticDropPolicyDoesNotSwallowPlayerHarvest() {
        Fixture fixture = new Fixture(event -> {
            if (event.getCause() == AutomaticBlockDropsEvent.Cause.AUTOMATIC_BREAK) {
                event.setCancelled();
            }
        });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            fixture.level.useBreakOn(
                    new Vector3(5, 65, 0), BlockFace.DOWN, Item.get(Item.AIR), player, false);
        }

        assertEquals(1, fixture.spawned.size(),
                "an automatic-drop policy must not suppress a player harvest");
        assertEquals(doublePlant(0).toItem().getId(), fixture.spawned.get(0).getId());
    }

    @Test
    void playerChainNestedBatchStaysCancellable() {
        Fixture fixture = new Fixture(event -> {
            if (event.getCause() == AutomaticBlockDropsEvent.Cause.PLAYER_BREAK
                    && !event.isOutermostBreak()) {
                event.setCancelled();
            }
        });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            fixture.level.useBreakOn(
                    new Vector3(5, 65, 0), BlockFace.DOWN, Item.get(Item.AIR), player, false);
        }

        assertTrue(fixture.spawned.isEmpty(), "nested player batches must remain cancellable");
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 65, 0)).getId());
    }

    @Test
    void creativeTopHalfBreakYieldsNoHarvest() {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);
        Player player = Mockito.mock(Player.class);
        player.gamemode = Player.CREATIVE;
        Mockito.when(player.getGamemode()).thenReturn(Player.CREATIVE);
        Mockito.when(player.isCreative()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            fixture.level.useBreakOn(
                    new Vector3(5, 65, 0), BlockFace.DOWN, Item.get(Item.AIR), player, false);
        }

        assertTrue(fixture.spawned.isEmpty(), "creative breaks must not produce harvest items");
        assertEquals(2, fixture.events.size());
        assertEquals(AutomaticBlockDropsEvent.Cause.PLAYER_BREAK, fixture.events.get(0).getCause());
        assertEquals(0, fixture.events.get(0).getDrops().length);
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 65, 0)).getId());
    }

    @Test
    void playerBookkeepingBatchIsDistinguishableFromAutomaticBreaks() {
        Fixture playerFixture = new Fixture(event -> { });
        playerFixture.put(Block.get(BlockID.SNOW_LAYER), 5, 64, 0);
        playerFixture.put(Block.get(BlockID.STONE), 5, 63, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;
        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            playerFixture.level.useBreakOn(
                    new Vector3(5, 64, 0), BlockFace.UP, Item.get(Item.AIR), player, false);
        }
        assertEquals(1, playerFixture.events.size());
        assertEquals(AutomaticBlockDropsEvent.Cause.PLAYER_BREAK,
                playerFixture.events.get(0).getCause());
        assertTrue(playerFixture.events.get(0).isOutermostBreak());
        assertEquals(0, playerFixture.events.get(0).getDrops().length);

        Fixture automaticFixture = new Fixture(event -> { });
        automaticFixture.put(Block.get(BlockID.SNOW_LAYER), 5, 64, 0);
        automaticFixture.put(Block.get(BlockID.STONE), 5, 63, 0);
        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            automaticFixture.level.useBreakOn(
                    new Vector3(5, 64, 0), BlockFace.UP, Item.get(Item.STONE), null, true);
        }
        assertEquals(1, automaticFixture.events.size());
        assertEquals(AutomaticBlockDropsEvent.Cause.AUTOMATIC_BREAK,
                automaticFixture.events.get(0).getCause());
        assertTrue(automaticFixture.events.get(0).isOutermostBreak());
        assertEquals(0, automaticFixture.events.get(0).getDrops().length);
    }

    @ParameterizedTest
    @CsvSource({"false, 64", "false, 65", "true, 64", "true, 65"})
    void bottomHalfBreakKeepsReplacementPlacedDuringTheOtherHalfRemovalCallback(
            boolean playerBreak, int removedY) {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);
        int replacedY = removedY == 64 ? 65 : 64;
        AtomicBoolean replaced = new AtomicBoolean();
        fixture.level.addCallbackBlockSet((previous, current) -> {
            if (previous.getId() == BlockID.DOUBLE_PLANT && previous.getFloorY() == removedY
                    && current.getId() == BlockID.AIR && replaced.compareAndSet(false, true)) {
                fixture.level.setBlock(new Vector3(5, replacedY, 0), Block.get(BlockID.STONE), true, false);
            }
        });
        Player player = playerBreak ? Mockito.mock(Player.class) : null;
        if (player != null) {
            Mockito.when(player.isSurvival()).thenReturn(true);
            player.lastBreak = -1;
        }

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0), null, null, player, false));
        }

        assertTrue(replaced.get(), "the callback must run while removing the original half");
        assertEquals(BlockID.STONE, fixture.level.getBlock(new Vector3(5, replacedY, 0)).getId(),
                "the old plant break must not erase the callback's replacement of the other half");
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, removedY, 0)).getId());
        assertEquals(1, fixture.events.size());
        AutomaticBlockDropsEvent completion = fixture.events.get(0);
        assertTrue(completion.isOutermostBreak());
        assertEquals(2, completion.getChangedBlocks().length);
        assertTrue(Arrays.stream(completion.getChangedBlocks())
                .allMatch(block -> block.getId() == BlockID.DOUBLE_PLANT));
        assertEquals(1, fixture.spawned.size());
        assertEquals(doublePlant(0).toItem().getId(), fixture.spawned.get(0).getId());
    }

    @Test
    void bottomHalfBreakKeepsADifferentPlantPlacedByTheRemovalCallback() {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(doublePlant(BlockDoublePlant.SUNFLOWER), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.SUNFLOWER | BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);
        AtomicBoolean replaced = new AtomicBoolean();
        fixture.level.addCallbackBlockSet((previous, current) -> {
            if (previous.getId() == BlockID.DOUBLE_PLANT && previous.getFloorY() == 64
                    && current.getId() == BlockID.AIR && replaced.compareAndSet(false, true)) {
                fixture.level.setBlock(new Vector3(5, 64, 0), doublePlant(BlockDoublePlant.LILAC), true, false);
                fixture.level.setBlock(new Vector3(5, 65, 0),
                        doublePlant(BlockDoublePlant.LILAC | BlockDoublePlant.TOP_HALF_BITMASK), true, false);
            }
        });

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0)));
        }

        assertTrue(replaced.get());
        Block bottom = fixture.level.getBlock(new Vector3(5, 64, 0));
        Block top = fixture.level.getBlock(new Vector3(5, 65, 0));
        assertEquals(BlockID.DOUBLE_PLANT, bottom.getId());
        assertEquals(BlockDoublePlant.LILAC, bottom.getDamage());
        assertEquals(BlockID.DOUBLE_PLANT, top.getId(), "the newly placed plant must keep its top half");
        assertEquals(BlockDoublePlant.LILAC | BlockDoublePlant.TOP_HALF_BITMASK, top.getDamage());
        assertEquals(1, fixture.events.size());
        assertEquals(1, fixture.events.get(0).getChangedBlocks().length,
                "changing the top half's state in place must not be reported as another removal");
        assertEquals(1, fixture.spawned.size());
        assertEquals(BlockDoublePlant.SUNFLOWER, fixture.spawned.get(0).getDamage());
    }

    @Test
    void callbackReuseOfThePreviousBlockKeepsTheOriginalRemovalSnapshot() {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(Block.get(BlockID.DIRT), 5, 64, 0);
        AtomicBoolean moved = new AtomicBoolean();
        fixture.level.addCallbackBlockSet((previous, current) -> {
            if (previous.getId() == BlockID.DIRT && current.getId() == BlockID.AIR
                    && moved.compareAndSet(false, true)) {
                fixture.level.setBlock(new Vector3(7, 64, 0), previous, true, false);
            }
        });

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0)));
        }

        assertTrue(moved.get());
        assertEquals(BlockID.DIRT, fixture.level.getBlock(new Vector3(7, 64, 0)).getId());
        assertEquals(1, fixture.events.size());
        Block[] changed = fixture.events.get(0).getChangedBlocks();
        assertEquals(1, changed.length);
        assertEquals(BlockID.DIRT, changed[0].getId());
        assertEquals(5, changed[0].getFloorX(),
                "reusing a callback block at another position must not move the recorded removal");
        assertEquals(64, changed[0].getFloorY());
    }

    @Test
    void nestedCompletionCannotRewriteTheOuterBreakSnapshots() {
        AtomicBoolean reused = new AtomicBoolean();
        AtomicReference<Fixture> holder = new AtomicReference<>();
        Fixture fixture = new Fixture(event -> {
            if (!event.isOutermostBreak() && reused.compareAndSet(false, true)) {
                for (Block changed : event.getChangedBlocks()) {
                    changed.setDamage(BlockDoublePlant.PEONY);
                    holder.get().level.setBlock(new Vector3(7, changed.getFloorY(), 0), changed, true, false);
                }
            }
        });
        holder.set(fixture);
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 65, 0)));
        }

        assertTrue(reused.get());
        AutomaticBlockDropsEvent outer = fixture.events.stream()
                .filter(AutomaticBlockDropsEvent::isOutermostBreak).findFirst().orElseThrow();
        Block[] changed = outer.getChangedBlocks();
        assertEquals(2, changed.length);
        for (Block original : changed) {
            assertEquals(5, original.getFloorX(),
                    "an inner event must not move snapshots still needed by its outer scope");
            assertEquals(original.getFloorY() == 64 ? 0 : BlockDoublePlant.TOP_HALF_BITMASK,
                    original.getDamage(), "the outer event must retain each half's original state");
        }
    }

    @ParameterizedTest
    @CsvSource({"id, false", "state, false", "id, true", "state, true"})
    void rawWritesOnlyReportMainLayerIdChanges(String entry, boolean retainId) {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(Block.get(BlockID.DIRT), 5, 64, 0);
        fixture.put(Block.get(BlockID.STONE, 1), 6, 64, 0);
        fixture.put(Block.get(BlockID.WATER), 6, 64, 0, 1);
        AtomicBoolean wrote = new AtomicBoolean();
        fixture.level.addCallbackBlockSet((previous, current) -> {
            if (previous.getId() == BlockID.DIRT && current.getId() == BlockID.AIR
                    && wrote.compareAndSet(false, true)) {
                int id = retainId ? BlockID.STONE : BlockID.AIR;
                if (entry.equals("id")) {
                    fixture.level.setBlockIdAt(6, 64, 0, id);
                    fixture.level.setBlockIdAt(6, 64, 0, 1, BlockID.AIR);
                } else {
                    assertTrue(fixture.level.setBlockAtLayer(6, 64, 0, 0, id, 2));
                    assertTrue(fixture.level.setBlockAtLayer(6, 64, 0, 1, BlockID.AIR, 0));
                }
            }
        });

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0)));
        }

        assertTrue(wrote.get());
        assertEquals(retainId ? BlockID.STONE : BlockID.AIR, fixture.level.getBlock(6, 64, 0).getId());
        assertEquals(BlockID.AIR, fixture.level.getBlock(6, 64, 0, 1).getId());
        assertEquals(1, fixture.events.size());
        Block[] changed = fixture.events.get(0).getChangedBlocks();
        assertEquals(retainId ? 1 : 2, changed.length,
                "secondary-layer writes and same-ID state changes must not add removal snapshots");
        assertTrue(Arrays.stream(changed).allMatch(block -> block.layer == 0));
        if (!retainId) {
            Block neighbor = Arrays.stream(changed).filter(block -> block.getFloorX() == 6)
                    .findFirst().orElseThrow();
            assertEquals(BlockID.STONE, neighbor.getId());
            assertEquals(1, neighbor.getDamage(), "raw writes must retain the removed block's old state");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "state"})
    void rawWriteAttributionUsesTheMaskedStoredId(String entry) {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(Block.get(BlockID.DIRT), 5, 64, 0);
        fixture.put(Block.get(BlockID.STONE, 1), 6, 64, 0);
        AtomicBoolean wrote = new AtomicBoolean();
        fixture.level.addCallbackBlockSet((previous, current) -> {
            if (previous.getId() == BlockID.DIRT && current.getId() == BlockID.AIR
                    && wrote.compareAndSet(false, true)) {
                int requestedId = Block.MAX_BLOCK_ID | BlockID.STONE;
                if (entry.equals("id")) {
                    fixture.level.setBlockIdAt(6, 64, 0, requestedId);
                } else {
                    assertTrue(fixture.level.setBlockAtLayer(6, 64, 0, 0, requestedId, Block.DATA_SIZE | 2));
                }
            }
        });

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(EntityItem.class)) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0)));
        }

        assertTrue(wrote.get());
        assertEquals(BlockID.STONE, fixture.level.getBlock(6, 64, 0).getId());
        assertEquals(entry.equals("id") ? 1 : 2, fixture.level.getBlock(6, 64, 0).getDamage());
        assertEquals(1, fixture.events.size());
        Block[] changed = fixture.events.get(0).getChangedBlocks();
        assertEquals(1, changed.length, "truncated high ID bits must not turn a same-ID write into a removal");
        assertEquals(BlockID.DIRT, changed[0].getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledAutomaticDropsDoNotSuppressChestContents(boolean tileDrops) {
        Fixture fixture = new Fixture(event -> event.setCancelled());
        fixture.gameRules.setGameRule(GameRule.DO_TILE_DROPS, tileDrops);
        fixture.put(Block.get(BlockID.CHEST), 5, 64, 0);
        Mockito.when(fixture.chunk.getProvider()).thenReturn(fixture.provider);
        Mockito.when(fixture.provider.getLevel()).thenReturn(fixture.level);
        BlockEntityChest chest = new BlockEntityChest(fixture.chunk, new CompoundTag()
                .putString("id", BlockEntity.CHEST).putInt("x", 5).putInt("y", 64).putInt("z", 0)
                .putList(new ListTag<CompoundTag>("Items")
                        .add(NBTIO.putItemHelper(Item.get(Item.DIAMOND), 0))));
        Mockito.when(fixture.level.getBlockEntity(Mockito.any(Vector3.class))).thenReturn(chest);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0)));
        }

        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertTrue(chest.closed);
        assertTrue(chest.getInventory().getContents().isEmpty());
        assertEquals(1, fixture.events.size(), "container contents must not emit automatic-drop events");
        assertTrue(fixture.events.get(0).isCancelled());
        assertEquals(1, fixture.spawned.size(), "only the contents must spawn, regardless of tile drops");
        assertEquals(Item.DIAMOND, fixture.spawned.get(0).getId());
    }

    @Test
    void topHalfBreakKeepsPluginBlockPlacedDuringTheInnerEvent() {
        long bottomCell = Hash.hashBlock(5, 64, 0);
        long topCell = Hash.hashBlock(5, 65, 0);
        int sunflowerItem = doublePlant(0).toItem().getId();

        // 内层下半格掉落事件是插件重入点；插件在原上半格放入的新方块必须在整条破坏结束后存活
        // The inner bottom-half drop event is a plugin reentry point; a block placed into
        // the former top cell there must survive the whole break
        AtomicBoolean injected = new AtomicBoolean();
        AtomicReference<Fixture> holder = new AtomicReference<>();
        Fixture fixture = new Fixture(event -> {
            if (!event.isOutermostBreak() && injected.compareAndSet(false, true)) {
                holder.get().level.setBlock(new Vector3(5, 65, 0), Block.get(BlockID.STONE));
            }
        });
        holder.set(fixture);
        fixture.put(doublePlant(0), 5, 64, 0);
        fixture.put(doublePlant(BlockDoublePlant.TOP_HALF_BITMASK), 5, 65, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class,
                (mock, context) -> fixture.spawned.add(
                        NBTIO.getItemHelper(((CompoundTag) context.arguments().get(1)).getCompound("Item"))))) {
            fixture.level.useBreakOn(new Vector3(5, 65, 0), BlockFace.UP, null, null, false);
        }

        assertTrue(injected.get(), "the plugin placement must have been injected mid-chain");
        assertEquals(BlockID.AIR, fixture.cells.get(bottomCell).getId());
        assertEquals(
                BlockID.STONE,
                fixture.cells.get(topCell).getId(),
                "a block placed during the inner event must survive the outer break");
        assertTrue(
                fixture.spawned.stream().anyMatch(item -> item.getId() == sunflowerItem),
                "the bottom half's own drop still spawns");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dripstoneDropListenerReplacementSurvivesBreakCompletion(boolean cancelDrop) {
        AtomicBoolean replaced = new AtomicBoolean();
        AtomicReference<Fixture> holder = new AtomicReference<>();
        Fixture fixture = new Fixture(event -> {
            if (event.getBlock().getFloorY() == 65 && event.getDrops().length > 0
                    && replaced.compareAndSet(false, true)) {
                holder.get().level.setBlock(event.getBlock(), Block.get(BlockID.STONE), true, false);
                event.setCancelled(cancelDrop);
            }
        });
        holder.set(fixture);
        fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 64, 0);
        fixture.put(dripstone(DripstoneThickness.MIDDLE, false), 5, 65, 0);
        fixture.put(dripstone(DripstoneThickness.TIP, false), 5, 66, 0);

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(new Vector3(5, 64, 0), null, null, null, false));
        }

        assertTrue(replaced.get());
        assertEquals(BlockID.STONE, fixture.level.getBlock(new Vector3(5, 65, 0)).getId(),
                "a block placed during a secondary dripstone drop must survive the original break");
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 64, 0)).getId());
        assertEquals(BlockID.AIR, fixture.level.getBlock(new Vector3(5, 66, 0)).getId());
        AutomaticBlockDropsEvent completion = fixture.events.get(fixture.events.size() - 1);
        assertTrue(completion.isOutermostBreak());
        assertEquals(3, completion.getChangedBlocks().length);
        assertTrue(Arrays.stream(completion.getChangedBlocks())
                .allMatch(block -> block.getId() == BlockID.POINTED_DRIPSTONE));
        assertEquals(cancelDrop ? 2 : 3, fixture.spawned.size());
    }

    @Test
    void playerDripstoneColumnHarvestKeepsPlayerAttribution() {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 64, 0);
        fixture.put(dripstone(DripstoneThickness.MIDDLE, false), 5, 65, 0);
        fixture.put(dripstone(DripstoneThickness.TIP, false), 5, 66, 0);
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSurvival()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(
                    new Vector3(5, 64, 0), BlockFace.UP, Item.get(Item.AIR), player, false));
        }

        List<AutomaticBlockDropsEvent> direct = new ArrayList<>();
        for (AutomaticBlockDropsEvent event : fixture.events) {
            if (!event.isOutermostBreak()) {
                direct.add(event);
            }
        }
        assertEquals(2, direct.size(), "the two remaining segments drop directly");
        assertTrue(direct.stream().allMatch(
                        event -> event.getCause() == AutomaticBlockDropsEvent.Cause.PLAYER_BREAK),
                "direct drops inside a player chain are player-authored");
        AutomaticBlockDropsEvent terminal = fixture.events.get(fixture.events.size() - 1);
        assertTrue(terminal.isOutermostBreak());
        assertEquals(AutomaticBlockDropsEvent.Cause.PLAYER_BREAK, terminal.getCause());
        assertEquals(3, fixture.spawned.size(), "the player harvest keeps all three segments");
    }

    @Test
    void creativeDripstoneColumnHarvestYieldsNoItems() {
        Fixture fixture = new Fixture(event -> { });
        fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 64, 0);
        fixture.put(dripstone(DripstoneThickness.MIDDLE, false), 5, 65, 0);
        fixture.put(dripstone(DripstoneThickness.TIP, false), 5, 66, 0);
        Player player = Mockito.mock(Player.class);
        player.gamemode = Player.CREATIVE;
        Mockito.when(player.getGamemode()).thenReturn(Player.CREATIVE);
        Mockito.when(player.isCreative()).thenReturn(true);
        player.lastBreak = -1;

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class, (mock, context) -> fixture.recordSpawn(context))) {
            assertNotNull(fixture.level.useBreakOn(
                    new Vector3(5, 64, 0), BlockFace.UP, Item.get(Item.AIR), player, false));
        }

        assertTrue(fixture.spawned.isEmpty(), "creative breaks must not produce direct drops");
        assertTrue(fixture.events.stream()
                        .filter(event -> !event.isOutermostBreak())
                        .allMatch(event -> event.getCause() == AutomaticBlockDropsEvent.Cause.PLAYER_BREAK
                                && event.getDrops().length == 0),
                "creative direct drops stay player-authored but empty");
    }

    private static BlockPointedDripstone dripstone(DripstoneThickness thickness, boolean hanging) {
        for (int meta = 0; meta < 16; meta++) {
            Block block = Block.get(BlockID.POINTED_DRIPSTONE, meta);
            if (block instanceof BlockPointedDripstone dripstone
                    && dripstone.getThickness() == thickness
                    && dripstone.isHanging() == hanging) {
                return dripstone;
            }
        }
        throw new AssertionError("no pointed dripstone meta for " + thickness + " hanging=" + hanging);
    }

    private static BlockDoublePlant doublePlant(int meta) {
        return (BlockDoublePlant) Block.get(BlockID.DOUBLE_PLANT, meta);
    }

    private static Block lowerHalf(int blockId) {
        switch (blockId) {
            case BlockID.DOUBLE_PLANT:
                return doublePlant(0);
            case BlockID.PITCHER_PLANT: {
                BlockPitcherPlant plant = (BlockPitcherPlant) Block.get(BlockID.PITCHER_PLANT);
                plant.setPropertyValue(VanillaProperties.UPPER_BLOCK, false);
                return plant;
            }
            case BlockID.PITCHER_CROP: {
                BlockPitcherCrop crop = (BlockPitcherCrop) Block.get(BlockID.PITCHER_CROP);
                crop.setPropertyValue(VanillaProperties.UPPER_BLOCK, false);
                return crop;
            }
            default:
                throw new IllegalArgumentException("unsupported block " + blockId);
        }
    }

    private static Block upperHalf(int blockId) {
        switch (blockId) {
            case BlockID.DOUBLE_PLANT:
                return doublePlant(BlockDoublePlant.TOP_HALF_BITMASK);
            case BlockID.PITCHER_PLANT: {
                BlockPitcherPlant plant = (BlockPitcherPlant) Block.get(BlockID.PITCHER_PLANT);
                plant.setPropertyValue(VanillaProperties.UPPER_BLOCK, true);
                return plant;
            }
            case BlockID.PITCHER_CROP: {
                BlockPitcherCrop crop = (BlockPitcherCrop) Block.get(BlockID.PITCHER_CROP);
                crop.setPropertyValue(VanillaProperties.UPPER_BLOCK, true);
                return crop;
            }
            default:
                throw new IllegalArgumentException("unsupported block " + blockId);
        }
    }

    /**
     * 真实破坏路径夹具：真实构造的 Level（簿记/服务器/振动管理器都是真实实例）+ 内存方块表 +
     * 可派发事件的假插件总线 + 拦截 EntityItem 构造。
     * <p>
     * Real break-path fixture: a really constructed Level (real bookkeeping, server and vibration
     * manager) plus an in-memory block world, a fake plugin bus that dispatches events, and
     * EntityItem construction interception.
     */
    private static final class Fixture {

        final Server server = Mockito.mock(Server.class);
        final Level level;
        final BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        // setBlock 会向 requireProvider().getMaximumLayer() 询问层上限；构造器内部创建的 provider 不可达
        // setBlock asks requireProvider().getMaximumLayer(); the provider built inside the constructor
        // is unreachable, so this double answers that world-config question
        final LevelProvider provider = Mockito.mock(LevelProvider.class);
        final GameRules gameRules = GameRules.getDefault();
        final Map<Long, Block> cells = new HashMap<>();
        final Map<Long, Block> secondaryCells = new HashMap<>();
        final List<AutomaticBlockDropsEvent> events = new ArrayList<>();
        final List<BlockBreakEvent> playerBreakEvents = new ArrayList<>();
        final List<Item> spawned = new ArrayList<>();

        Runnable onFirstSetBlock;
        Runnable onFirstSpawn;

        private final AtomicBoolean setBlockTriggered = new AtomicBoolean();
        private final AtomicBoolean spawnTriggered = new AtomicBoolean();

        Fixture(Consumer<AutomaticBlockDropsEvent> listener) {
            PluginManager pluginManager = Mockito.mock(PluginManager.class);
            Mockito.doAnswer(invocation -> {
                        Event event = invocation.getArgument(0);
                        if (event instanceof AutomaticBlockDropsEvent drops) {
                            events.add(drops);
                            listener.accept(drops);
                        } else if (event instanceof BlockBreakEvent breakEvent) {
                            playerBreakEvents.add(breakEvent);
                        }
                        return null;
                    })
                    .when(pluginManager)
                    .callEvent(Mockito.any(Event.class));
            Mockito.when(server.getPluginManager()).thenReturn(pluginManager);
            Mockito.when(server.getLogger()).thenReturn(Mockito.mock(MainLogger.class));
            Mockito.when(server.getLanguage()).thenReturn(Mockito.mock(BaseLang.class));
            Mockito.when(server.getServerConfig()).thenReturn(new ServerConfig());

            this.level = constructedLevel(server);
            Mockito.when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            Mockito.when(level.getMinBlockY()).thenReturn(0);
            Mockito.when(level.getMaxBlockY()).thenReturn(255);
            level.gameRules = gameRules;
            Mockito.when(level.getServer()).thenReturn(server);

            realMethods();
            stubs();
        }

        /**
         * 真实构造的世界对象：字段初始化与构造器都执行，破坏链簿记、服务器、振动管理器都是真实实例；
         * 构造完成后未 stub 的调用仍按 mock 默认值返回，夹具只伪造世界数据访问。
         * <p>
         * A really constructed world: field initializers and the constructor run, so the break-chain
         * bookkeeping, server and vibration manager are real; afterwards un-stubbed calls fall back
         * to mock defaults, so the fixture only fakes world-data access.
         */
        private static Level constructedLevel(Server server) {
            AtomicBoolean constructing = new AtomicBoolean(true);
            Level level = Mockito.mock(Level.class, Mockito.withSettings()
                    .useConstructor(server, "automatic-block-drops", "automatic-block-drops",
                            TestLevelProvider.class)
                    .defaultAnswer(invocation -> constructing.get()
                            ? invocation.callRealMethod()
                            : Mockito.RETURNS_DEFAULTS.answer(invocation)));
            constructing.set(false);
            return level;
        }

        void put(Block block, int x, int y, int z) {
            put(block, x, y, z, 0);
        }

        void put(Block block, int x, int y, int z, int layer) {
            block.x = x;
            block.y = y;
            block.z = z;
            block.level = level;
            block.layer = layer;
            (layer == 0 ? cells : secondaryCells).put(Hash.hashBlock(x, y, z), block);
        }

        void recordSpawn(MockedConstruction.Context context) {
            if (spawnTriggered.compareAndSet(false, true) && onFirstSpawn != null) {
                onFirstSpawn.run();
            }
            spawned.add(NBTIO.getItemHelper(
                    ((CompoundTag) context.arguments().get(1)).getCompound("Item")));
        }

        private Block cellAt(int x, int y, int z) {
            return cellAt(x, y, z, 0);
        }

        private Block cellAt(int x, int y, int z, int layer) {
            Block block = (layer == 0 ? cells : secondaryCells).get(Hash.hashBlock(x, y, z));
            if (block != null) {
                return block;
            }
            Block air = Block.get(BlockID.AIR);
            air.x = x;
            air.y = y;
            air.z = z;
            air.level = level;
            air.layer = layer;
            return air;
        }

        private void realMethods() {
            Mockito.doCallRealMethod().when(level).addCallbackBlockSet(Mockito.<BiConsumer<Block, Block>>any());
            Mockito.doCallRealMethod().when(level).setBlockIdAt(Mockito.anyInt(), Mockito.anyInt(),
                    Mockito.anyInt(), Mockito.anyInt());
            Mockito.doCallRealMethod().when(level).setBlockIdAt(Mockito.anyInt(), Mockito.anyInt(),
                    Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
            Mockito.doCallRealMethod().when(level).setBlockAtLayer(Mockito.anyInt(), Mockito.anyInt(),
                    Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).useBreakOnFromBlockInteraction(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).useItemOn(Mockito.any(), Mockito.any(), Mockito.any(),
                    Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.any(), Mockito.anyBoolean());

            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt());
            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyInt());

            Mockito.doCallRealMethod().when(level).dropItemFromAutomaticBlock(Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).dropItemFromBlockInteraction(Mockito.any(), Mockito.any());

            // 真实 setBlock：破坏链簿记只在这里发生，不能再用测试替身绕开
            // Real setBlock: break-chain bookkeeping only happens here, so the fixture must not fake it
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.any(BlockLayer.class), Mockito.any());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.anyInt(), Mockito.any());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.any(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.any(BlockLayer.class), Mockito.any(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.any(BlockLayer.class), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(BlockLayer.class), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).setBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        }

        private void stubs() {
            Mockito.when(level.getBlock(Mockito.any(Vector3.class)))
                    .thenAnswer(invocation -> {
                        Vector3 pos = invocation.getArgument(0);
                        return cellAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
                    });
            Mockito.when(level.getBlock(Mockito.any(Vector3.class), Mockito.anyInt()))
                    .thenAnswer(invocation -> {
                        Vector3 pos = invocation.getArgument(0);
                        return cellAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), invocation.getArgument(1));
                    });
            Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                            invocation.getArgument(3)));

            // 雪层 onUpdate 走 getBlockIdAt / getGameRules() 公共入口
            // Snow's onUpdate uses the public getBlockIdAt / getGameRules() entries
            Mockito.when(level.getBlockIdAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)).getId());
            Mockito.when(level.getGameRules()).thenReturn(gameRules);

            // 真实 setBlock 的边界与区块入口；世界数据仍由内存方块表提供
            // Bounds and chunk entry points the real setBlock needs; world data stays in the cell map
            Mockito.when(level.isYInRange(Mockito.anyInt())).thenReturn(true);
            Mockito.when(level.requireProvider()).thenReturn(provider);
            Mockito.when(provider.getMaximumLayer()).thenReturn(1);
            Mockito.when(level.getChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()))
                    .thenReturn(chunk);
            Mockito.when(level.getChunkPlayers(Mockito.anyInt(), Mockito.anyInt())).thenReturn(new HashMap<>());
            Mockito.when(level.getChunkLoaders(Mockito.anyInt(), Mockito.anyInt())).thenReturn(new ChunkLoader[0]);
            Mockito.when(level.getNearbyEntities(Mockito.any())).thenReturn(new Entity[0]);

            Mockito.when(chunk.getAndSetBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                            Mockito.anyInt(), Mockito.any(Block.class)))
                    .thenAnswer(invocation -> {
                        int x = invocation.getArgument(0);
                        int y = invocation.getArgument(1);
                        int z = invocation.getArgument(2);
                        int layer = invocation.getArgument(3);
                        Block next = invocation.getArgument(4);
                        Block previous = cellAt(x, y, z, layer).clone();
                        put(next, x, y, z, layer);
                        if (setBlockTriggered.compareAndSet(false, true) && onFirstSetBlock != null) {
                            onFirstSetBlock.run();
                        }
                        return previous;
                    });
            Mockito.doAnswer(invocation -> {
                        int x = invocation.getArgument(0);
                        int y = invocation.getArgument(1);
                        int z = invocation.getArgument(2);
                        int layer = invocation.getArgument(3);
                        int id = invocation.getArgument(4);
                        put(Block.get(id, cellAt(x, y, z, layer).getDamage()), x, y, z, layer);
                        return null;
                    }).when(chunk).setBlockId(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                            Mockito.anyInt(), Mockito.anyInt());
            Mockito.when(chunk.setBlockAtLayer(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                            Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> {
                        int x = invocation.getArgument(0);
                        int y = invocation.getArgument(1);
                        int z = invocation.getArgument(2);
                        int layer = invocation.getArgument(3);
                        int id = invocation.getArgument(4);
                        int data = invocation.getArgument(5);
                        Block next = Block.get(id, data);
                        boolean changed = cellAt(x, y, z, layer).getFullId() != next.getFullId();
                        put(next, x, y, z, layer);
                        return changed;
                    });
        }
    }

    /**
     * 只提供构造 Level 时读取的时间/天气/规则元数据，存储操作一律不支持：方块世界由夹具的内存表负责。
     * <p>
     * Supplies only the time/weather/rule metadata the Level constructor reads; every storage
     * operation is unsupported because the fixture keeps the block world in memory.
     */
    public static final class TestLevelProvider implements LevelProvider {

        private final Level level;

        public TestLevelProvider(Level level, String path) {
            this.level = level;
        }

        public static boolean usesChunkSection() {
            return false;
        }

        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public String getPath() {
            return "automatic-block-drops";
        }

        @Override
        public String getName() {
            return "automatic-block-drops";
        }

        @Override
        public String getGenerator() {
            return "flat";
        }

        @Override
        public Map<String, Object> getGeneratorOptions() {
            return Map.of();
        }

        @Override
        public void updateLevelName(String name) {
        }

        @Override
        public long getTime() {
            return 0;
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public int getRainTime() {
            return 1;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public int getThunderTime() {
            return 1;
        }

        @Override
        public long getCurrentTick() {
            return 0;
        }

        @Override
        public long getSeed() {
            return 0;
        }

        @Override
        public GameRules getGamerules() {
            return GameRules.getDefault();
        }

        @Override
        public void requestChunkTask(ObjectSet<GameVersion> protocols, int chunkX, int chunkZ) {
            throw new UnsupportedOperationException();
        }

        @Override public BaseFullChunk getLoadedChunk(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public BaseFullChunk getLoadedChunk(long hash) { throw new UnsupportedOperationException(); }
        @Override public BaseFullChunk getChunk(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public BaseFullChunk getChunk(int chunkX, int chunkZ, boolean create) { throw new UnsupportedOperationException(); }
        @Override public BaseFullChunk getEmptyChunk(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public void saveChunks() { throw new UnsupportedOperationException(); }
        @Override public void saveChunk(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public void saveChunk(int chunkX, int chunkZ, FullChunk chunk) { throw new UnsupportedOperationException(); }
        @Override public void unloadChunks() { throw new UnsupportedOperationException(); }
        @Override public boolean loadChunk(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public boolean loadChunk(int chunkX, int chunkZ, boolean create) { throw new UnsupportedOperationException(); }
        @Override public boolean unloadChunk(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public boolean unloadChunk(int chunkX, int chunkZ, boolean safe) { throw new UnsupportedOperationException(); }
        @Override public boolean isChunkGenerated(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public boolean isChunkPopulated(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public boolean isChunkLoaded(int chunkX, int chunkZ) { throw new UnsupportedOperationException(); }
        @Override public boolean isChunkLoaded(long hash) { throw new UnsupportedOperationException(); }
        @Override public void setChunk(int chunkX, int chunkZ, FullChunk chunk) { throw new UnsupportedOperationException(); }
        @Override public void setRaining(boolean raining) { throw new UnsupportedOperationException(); }
        @Override public void setRainTime(int rainTime) { throw new UnsupportedOperationException(); }
        @Override public void setThundering(boolean thundering) { throw new UnsupportedOperationException(); }
        @Override public void setThunderTime(int thunderTime) { throw new UnsupportedOperationException(); }
        @Override public void setCurrentTick(long currentTick) { throw new UnsupportedOperationException(); }
        @Override public void setTime(long value) { throw new UnsupportedOperationException(); }
        @Override public void setSeed(long value) { throw new UnsupportedOperationException(); }
        @Override public Vector3 getSpawn() { throw new UnsupportedOperationException(); }
        @Override public void setSpawn(Vector3 pos) { throw new UnsupportedOperationException(); }
        @Override public Map<Long, ? extends FullChunk> getLoadedChunks() { throw new UnsupportedOperationException(); }
        @Override public void doGarbageCollection() { throw new UnsupportedOperationException(); }
        @Override public void close() { throw new UnsupportedOperationException(); }
        @Override public void saveLevelData() { throw new UnsupportedOperationException(); }
        @Override public void setGameRules(GameRules rules) { throw new UnsupportedOperationException(); }
    }
}
