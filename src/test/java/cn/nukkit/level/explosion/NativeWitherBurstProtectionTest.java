package cn.nukkit.level.explosion;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntitySpawnableContainer;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityWither;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.RegisteredListener;
import cn.nukkit.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual native player-hit burst paths; sparse fixture does not prove live/BDS delivery. */
@Isolated("native Server singleton and static event lists")
public final class NativeWitherBurstProtectionTest {
    private static Server previousServer;
    private final List<Map.Entry<HandlerList, RegisteredListener>> listeners = new ArrayList<>();

    @BeforeAll static void boot() throws Exception {
        previousServer = Server.getInstance();
        NativeExplosionFixture.boot();
        NativeExplosionFixture.server.mobsFromBlocks = true;
        NativeExplosionFixture.server.explosionBreakBlocks = true;
        NativeExplosionFixture.set(Server.class, NativeExplosionFixture.server, "difficulty", 2);
    }

    @AfterAll static void restoreServer() throws Exception {
        NativeExplosionFixture.set(Server.class, null, "instance", previousServer);
    }

    @AfterEach void cleanup() {
        for (var listener : listeners) listener.getKey().unregister(listener.getValue());
    }

    @Test void refusedPlayerDamageCannotTriggerTerrainDestruction() throws Exception {
        var world = raidWorld();
        ChestInventory inventory = filledChest(world);
        on(EntityDamageEvent.getHandlers(), raw -> ((EntityDamageEvent) raw).setCancelled(true));
        hit(world);
        assertRetained(world, inventory);
    }

    @Test void cancelledPrimeKeepsObsidianAndFilledContainer() throws Exception {
        var world = raidWorld();
        ChestInventory inventory = filledChest(world);
        AtomicInteger attempts = new AtomicInteger();
        on(EntityExplosionPrimeEvent.getHandlers(), raw -> {
            EntityExplosionPrimeEvent event = (EntityExplosionPrimeEvent) raw;
            assertEquals(1, event.getForce(), 0.001);
            attempts.incrementAndGet();
            event.setCancelled(true);
        });
        hit(world);
        assertEquals(1, attempts.get());
        assertRetained(world, inventory);
    }

    @Test void cancelledBurstKeepsObsidianAndFilledContainer() throws Exception {
        var world = raidWorld();
        ChestInventory inventory = filledChest(world);
        AtomicInteger attempts = new AtomicInteger();
        on(EntityExplodeEvent.getHandlers(), raw -> {
            EntityExplodeEvent event = (EntityExplodeEvent) raw;
            assertTrue(event.getBlockList().stream().anyMatch(block -> block.getId() == Block.OBSIDIAN));
            attempts.incrementAndGet();
            event.setCancelled(true);
        });
        hit(world);
        assertEquals(1, attempts.get());
        assertRetained(world, inventory);
    }

    @Test void finalBlockListProtectsChestAtBoundaryAndStillBreaksLegalObsidian() throws Exception {
        var world = raidWorld();
        ChestInventory inventory = filledChest(world);
        AtomicInteger attempts = new AtomicInteger();
        on(EntityExplodeEvent.getHandlers(), raw -> {
            EntityExplodeEvent event = (EntityExplodeEvent) raw;
            attempts.incrementAndGet();
            event.setYield(0);
            event.setBlockList(event.getBlockList().stream().filter(block -> block.getId() != Block.CHEST).toList());
        });
        hit(world);
        assertEquals(1, attempts.get());
        assertEquals(Block.CHEST, world.cell.getId());
        assertEquals(64, inventory.getItem(0).getCount());
        assertTrue(world.getBlock(1, 64, 0).isAir());
        assertTrue(world.allDrops().isEmpty());
    }

    @Test void refusedMetadataProcessorCannotDeleteContainerOrPublishInventory() throws Exception {
        var world = raidWorld();
        ChestInventory inventory = filledChest(world);
        AtomicInteger attempts = new AtomicInteger();
        on(EntityExplodeEvent.getHandlers(), raw -> {
            EntityExplodeEvent event = (EntityExplodeEvent) raw;
            attempts.incrementAndGet();
            event.addBlockProcessor(block -> NativeExplosionBlockProcessor.Result.REFUSED);
        });
        hit(world);
        assertEquals(1, attempts.get());
        assertRetained(world, inventory);
    }

    @Test void acceptedRaidBreaksObsidianAndPublishesEachStoredItemOnce() throws Exception {
        var world = raidWorld();
        ChestInventory inventory = filledChest(world);
        AtomicInteger attempts = new AtomicInteger();
        on(EntityExplodeEvent.getHandlers(), raw -> {
            attempts.incrementAndGet();
            ((EntityExplodeEvent) raw).setYield(100);
        });
        hit(world);
        assertEquals(1, attempts.get());
        assertTrue(world.cell.isAir());
        assertTrue(world.getBlock(1, 64, 0).isAir());
        assertTrue(inventory.isEmpty());
        assertEquals(64, world.allDrops().stream().filter(item -> item.getId() == Item.DIAMOND)
                .mapToInt(Item::getCount).sum());
    }

    @Test void refusedNativeRemovalLeavesInventoryAndDropsUntouched() throws Exception {
        var world = raidWorld();
        ChestInventory inventory = filledChest(world);
        world.rejectRemoval = true;
        hit(world);
        assertRetained(world, inventory);
    }

    private static NativeExplosionFixture.MemoryLevel raidWorld() throws Exception {
        var world = NativeExplosionFixture.level(Block.CHEST);
        world.setBlock(new Vector3(1, 64, 0), Block.get(Block.OBSIDIAN));
        world.getGameRules().setGameRule(GameRule.MOB_GRIEFING, true);
        return world;
    }

    private static ChestInventory filledChest(NativeExplosionFixture.MemoryLevel world) throws Exception {
        BlockEntityChest tile = NativeExplosionFixture.allocated(BlockEntityChest.class);
        tile.namedTag = new CompoundTag();
        NativeExplosionFixture.attach(world, tile);
        ChestInventory inventory = new ChestInventory(tile);
        NativeExplosionFixture.set(BlockEntitySpawnableContainer.class, tile, "inventory", inventory);
        inventory.slots.put(0, Item.get(Item.DIAMOND, 0, 64));
        return inventory;
    }

    private static void hit(NativeExplosionFixture.MemoryLevel world) throws Exception {
        EntityWither wither = new EntityWither(world.chunk,
                Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5)).putInt("Invul", 0).putBoolean("Persistent", true));
        Player player = NativeExplosionFixture.allocated(Player.class);
        player.level = world;
        player.y = 64;
        player.onGround = true;
        player.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 1, 66, 1);
        NativeExplosionFixture.set(Entity.class, player, "effects", new java.util.HashMap<>());
        NativeExplosionFixture.set(Entity.class, player, "server", NativeExplosionFixture.server);
        EntityDamageByEntityEvent damage = new EntityDamageByEntityEvent(player, wither,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1);
        try (var random = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            random.when(Utils::rand).thenReturn(true);
            assertTrue(wither.attack(damage));
        } finally {
            wither.close();
        }
    }

    private static void assertRetained(NativeExplosionFixture.MemoryLevel world, ChestInventory inventory) {
        assertEquals(Block.CHEST, world.cell.getId());
        assertEquals(Block.OBSIDIAN, world.getBlock(1, 64, 0).getId());
        assertEquals(64, inventory.getItem(0).getCount());
        assertTrue(world.allDrops().isEmpty());
    }

    private void on(HandlerList handlers, Consumer<Event> action) {
        PluginBase plugin = new PluginBase() {};
        plugin.setEnabled(true);
        RegisteredListener listener = new RegisteredListener(new Listener() {},
                (ignored, event) -> action.accept(event), EventPriority.HIGH, plugin, false);
        handlers.register(listener);
        listeners.add(Map.entry(handlers, listener));
    }
}
