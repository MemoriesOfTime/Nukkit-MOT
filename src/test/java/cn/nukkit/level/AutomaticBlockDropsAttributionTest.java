package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDoublePlant;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockPointedDripstone;
import cn.nukkit.block.properties.enums.DripstoneThickness;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.AutomaticBlockDropsEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.Hash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
        fixture.onFirstSpawn = () -> {
            if (replayed.compareAndSet(false, true)) {
                // 内层终批开始生成时同格重放新滴水石，再破坏其下方一格触发逐段直接掉落
                fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 64, 0);
                fixture.put(dripstone(DripstoneThickness.BASE, false), 5, 63, 0);
                fixture.level.useBreakOn(new Vector3(5, 63, 0), BlockFace.UP, null, null, false);
            }
        };

        try (MockedConstruction<EntityItem> ignored = Mockito.mockConstruction(
                EntityItem.class,
                (mock, context) -> fixture.spawned.add(
                        NBTIO.getItemHelper(((CompoundTag) context.arguments().get(1)).getCompound("Item"))))) {
            fixture.level.useBreakOn(new Vector3(5, 65, 0), BlockFace.UP, null, null, false);
        }

        assertTrue(replayed.get(), "the re-placed break must have been injected");
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

        // 雪层放在冰上，失支撑路径走真实 onUpdate：dropItemFromAutomaticBlock 先于 useBreakOn
        // A snow layer on ice: the lost-support path runs real onUpdate with
        // dropItemFromAutomaticBlock before useBreakOn
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
        assertTrue(completion.isOutermostBreak(), "the completion event must be the chain's last event");

        assertTrue(fixture.spawned.isEmpty(), "the cancelled snowball must not spawn");
        assertEquals(
                BlockID.AIR,
                fixture.cells.get(snowCell).getId(),
                "the snow layer must still be removed");
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

    /**
     * 真实 useBreakOn 夹具：内存方块表 + 可派发事件的假插件总线 + 拦截 EntityItem 构造。
     * <p>
     * Real-useBreakOn fixture: in-memory cell map, a fake plugin bus that dispatches events,
     * and EntityItem construction interception.
     */
    private static final class Fixture {

        final Level level = Mockito.mock(Level.class);
        final AutomaticBlockBreakTrace trace = new AutomaticBlockBreakTrace();
        final GameRules gameRules = GameRules.getDefault();
        final Map<Long, Block> cells = new HashMap<>();
        final List<AutomaticBlockDropsEvent> events = new ArrayList<>();
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
                        }
                        return null;
                    })
                    .when(pluginManager)
                    .callEvent(Mockito.any(Event.class));
            Server server = Mockito.mock(Server.class);
            Mockito.when(server.getPluginManager()).thenReturn(pluginManager);

            setField("server", server);
            setField("automaticBlockBreakTrace", trace);
            setField("gameRules", gameRules);
            setField("vibrationManager", Mockito.mock(VibrationManager.class));

            realMethods();
            stubs();
        }

        void put(Block block, int x, int y, int z) {
            block.x = x;
            block.y = y;
            block.z = z;
            block.level = level;
            block.layer = 0;
            cells.put(Hash.hashBlock(x, y, z), block);
        }

        private Block cellAt(int x, int y, int z) {
            Block block = cells.get(Hash.hashBlock(x, y, z));
            if (block != null) {
                return block;
            }
            Block air = Block.get(BlockID.AIR);
            air.x = x;
            air.y = y;
            air.z = z;
            air.level = level;
            return air;
        }

        private void setCell(Vector3 pos, Block next) {
            int x = pos.getFloorX();
            int y = pos.getFloorY();
            int z = pos.getFloorZ();
            Block previous = cells.get(Hash.hashBlock(x, y, z));
            put(next, x, y, z);
            // 真实 setBlock 会补全 previous 坐标后调 trace.record；此处手动补齐
            if (previous != null) {
                trace.record(previous, next);
            }
        }

        private void realMethods() {
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
            Mockito.doCallRealMethod().when(level).useBreakOn(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt());
            Mockito.doCallRealMethod().when(level).dropItem(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyInt());

            Mockito.doCallRealMethod().when(level).dropItemFromAutomaticBlock(Mockito.any(), Mockito.any());
        }

        private void stubs() {
            Mockito.when(level.getBlock(Mockito.any(Vector3.class)))
                    .thenAnswer(invocation -> {
                        Vector3 pos = invocation.getArgument(0);
                        return cellAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
                    });
            Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

            // 雪层 onUpdate 走 getBlockIdAt / getGameRules() 公共入口；mock 的 getter 不读字段，需显式接线
            // Snow's onUpdate uses the public getBlockIdAt / getGameRules() entries; a mock getter
            // does not read the field, so wire both explicitly
            Mockito.when(level.getBlockIdAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)).getId());
            Mockito.when(level.getGameRules()).thenReturn(gameRules);

            Mockito.when(level.setBlock(Mockito.any(Vector3.class), Mockito.any(Block.class)))
                    .thenAnswer(invocation -> {
                        setCell(invocation.getArgument(0), invocation.getArgument(1));
                        return true;
                    });
            Mockito.when(level.setBlock(Mockito.any(Vector3.class), Mockito.any(Block.class), Mockito.anyBoolean(), Mockito.anyBoolean()))
                    .thenAnswer(invocation -> {
                        setCell(invocation.getArgument(0), invocation.getArgument(1));
                        if (setBlockTriggered.compareAndSet(false, true) && onFirstSetBlock != null) {
                            onFirstSetBlock.run();
                        }
                        return true;
                    });

            Mockito.when(level.getChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()))
                    .thenAnswer(invocation -> {
                        if (spawnTriggered.compareAndSet(false, true) && onFirstSpawn != null) {
                            onFirstSpawn.run();
                        }
                        return null;
                    });
            Mockito.when(level.getChunkPlayers(Mockito.anyInt(), Mockito.anyInt())).thenReturn(new HashMap<>());
        }

        private void setField(String name, Object value) {
            try {
                var field = Level.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(level, value);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
