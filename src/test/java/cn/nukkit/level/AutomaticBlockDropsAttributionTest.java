package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.*;
import cn.nukkit.block.properties.enums.DripstoneThickness;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.AutomaticBlockDropsEvent;
import cn.nukkit.item.Item;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.Hash;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.serverconfig.ServerConfig;
import it.unimi.dsi.fastutil.objects.ObjectSet;
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
                        return cellAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
                    });
            Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(invocation -> cellAt(
                            invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

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
                    .thenAnswer(invocation -> {
                        if (spawnTriggered.compareAndSet(false, true) && onFirstSpawn != null) {
                            onFirstSpawn.run();
                        }
                        return chunk;
                    });
            Mockito.when(level.getChunkPlayers(Mockito.anyInt(), Mockito.anyInt())).thenReturn(new HashMap<>());
            Mockito.when(level.getChunkLoaders(Mockito.anyInt(), Mockito.anyInt())).thenReturn(new ChunkLoader[0]);
            Mockito.when(level.getNearbyEntities(Mockito.any())).thenReturn(new Entity[0]);

            Mockito.when(chunk.getAndSetBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                            Mockito.anyInt(), Mockito.any(Block.class)))
                    .thenAnswer(invocation -> {
                        int x = invocation.getArgument(0);
                        int y = invocation.getArgument(1);
                        int z = invocation.getArgument(2);
                        Block next = invocation.getArgument(4);
                        Block previous = cellAt(x, y, z);
                        put(next, x, y, z);
                        if (setBlockTriggered.compareAndSet(false, true) && onFirstSetBlock != null) {
                            onFirstSetBlock.run();
                        }
                        return previous;
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
