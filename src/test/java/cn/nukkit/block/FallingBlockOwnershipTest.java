package cn.nukkit.block;

import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.entity.item.EntityItem;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockSand;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockFallEvent;
import cn.nukkit.event.entity.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.*;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.serializer.EntitySerializer;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.iq80.leveldb.WriteBatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native sand source, native chunk/section mutation and actor NBT snapshots; no database is opened. */
class FallingBlockOwnershipTest {
    @BeforeAll static void initialize() {
        MockServer.init();
        Entity.registerEntity("FallingBlock", EntityFallingBlock.class);
        Entity.registerEntity("Item", EntityItem.class);
    }

    @AfterAll static void cleanup() { MockServer.reset(); }

    @Test void sourceThenLandingHasExactlyOneOwner() {
        Fixture f = new Fixture();
        f.start();
        assertEquals(Block.AIR, f.block(80).getId());
        assertEquals(1, f.savedOwners());
        f.land();
        assertEquals(Block.SAND, f.block(64).getId());
        assertEquals(1, f.savedOwners());
        assertTrue(f.falling.closed);
        assertFalse(f.falling.onUpdate(2));
    }

    @Test void sourceFallCancellationKeepsTheBlockWithoutConstructingAnActor() {
        Fixture f = new Fixture();
        f.events = e -> { if (e instanceof BlockFallEvent b) b.setCancelled(); };
        f.start();
        assertEquals(Block.SAND, f.block(80).getId());
        assertEquals(0, f.constructed);
        assertEquals(1, f.savedOwners());
    }

    @Test void nativeSourceWriteRefusalNeverCreatesAnotherOwner() {
        Fixture f = new Fixture();
        f.refuseY = 80;
        f.start();
        assertEquals(Block.SAND, f.block(80).getId());
        assertEquals(0, f.constructed);
        assertEquals(1, f.savedOwners());
    }

    @Test void failedNativeFactoryKeepsTheSourceBlock() {
        Fixture f = new Fixture();
        f.factoryFails = true;
        assertThrows(IllegalStateException.class, f::start);
        assertEquals(Block.SAND, f.block(80).getId());
        assertEquals(1, f.savedOwners());
    }

    @Test void nativeLandingRangeRefusalKeepsTheSavableActor() {
        Fixture f = new Fixture(); f.start();
        f.refuseY = 64;
        f.land();
        assertFalse(f.falling.closed);
        assertEquals(Block.AIR, f.block(64).getId());
        assertEquals(1, f.savedOwners());
    }

    @Test void nativeAllowedPreflightSnapshotContainsTheActor() {
        Fixture f = new Fixture(); f.start();
        List<Integer> saved = new ArrayList<>();
        f.events = e -> { if (e instanceof EntityBlockChangeEvent) saved.add(f.savedOwners()); };
        f.land();
        assertEquals(List.of(1), saved);
        assertEquals(1, f.savedOwners());
    }

    @Test void nativeSaveBeforeLandingIncludesTileDataAndTime() {
        Fixture f = new Fixture(); f.start();
        assertEquals(1, f.savedOwners());
        assertEquals(Block.SAND, f.falling.namedTag.getInt("TileID"));
        assertEquals(0, f.falling.namedTag.getInt("Time"));
    }

    @Test void independentlyPlacedSameSandDoesNotClaimTheFallingGrain() {
        Fixture f = new Fixture(); f.start();
        f.events = e -> { if (e instanceof EntityBlockChangeEvent) f.write(64, Block.SAND); };
        f.land();
        assertEquals(Block.SAND, f.block(64).getId());
        assertFalse(f.falling.closed);
        assertEquals(2, f.savedOwners());
        assertEquals(0, f.items());
    }

    @Test void nativeAfterWriteCallbacksSeeOnlyTheCommittedBlock() {
        Fixture f = new Fixture(); f.start();
        List<Integer> saved = new ArrayList<>();
        f.afterWrite = (before, after) -> { if (after.getFloorY() == 64) saved.add(f.savedOwners()); };
        f.land();
        assertEquals(List.of(1), saved);
        assertEquals(1, f.savedOwners());
    }

    @Test void suppressingPublicCloseCannotExposeAnActorBesideTheCommittedBlock() {
        Fixture f = new Fixture(); f.start();
        // Mutation guard: a close-after-setBlock shortcut would rely on this public callback boundary.
        doNothing().when(f.falling).close();
        f.afterWrite = (before, after) -> assertEquals(1, f.savedOwners());
        f.land();
        assertTrue(f.falling.closed);
        assertEquals(1, f.savedOwners());
    }

    @Test void recursiveOnUpdateDuringPreflightCannotMintAnotherGrain() {
        Fixture f = new Fixture(); f.start();
        f.events = e -> { if (e instanceof EntityBlockChangeEvent) assertFalse(f.falling.onUpdate(2)); };
        f.land();
        assertEquals(1, f.savedOwners());
        assertEquals(0, f.items());
    }

    @Test void nativeListenerErrorBeforeCommitLeavesTheSavableGrain() {
        Fixture f = new Fixture(); f.start();
        PluginManager manager = new PluginManager(Server.getInstance(), null);
        Plugin plugin = mock(Plugin.class); when(plugin.isEnabled()).thenReturn(true);
        manager.registerEvent(EntityBlockChangeEvent.class, new Listener() {}, EventPriority.NORMAL,
                (listener, event) -> { throw new AssertionError("native listener fault"); }, plugin, true);
        f.events = manager::callEvent;
        try { assertThrows(AssertionError.class, f::land); }
        finally { EntityBlockChangeEvent.getHandlers().unregister(plugin); }
        assertFalse(f.falling.closed);
        assertEquals(Block.AIR, f.block(64).getId());
        assertEquals(1, f.savedOwners());
    }

    @Test void nativeDespawnCallbackSnapshotSeesTheCommittedBlock() {
        Fixture f = new Fixture(); f.start();
        List<Integer> saved = new ArrayList<>();
        f.events = e -> { if (e instanceof EntityDespawnEvent) saved.add(f.savedOwners()); };
        f.land();
        assertEquals(List.of(1), saved);
        assertEquals(1, f.savedOwners());
    }

    @Test void callbacksAfterCommitMayBreakTheBlockWithoutRestoringItsActor() {
        Fixture f = new Fixture(); f.start();
        f.afterWrite = (before, after) -> {
            if (after.getFloorY() == 64 && after.getId() == Block.SAND) {
                f.write(64, Block.AIR);
                throw new AssertionError("postcommit notification fault");
            }
        };
        assertThrows(AssertionError.class, f::land);
        assertTrue(f.falling.closed);
        assertEquals(0, f.savedOwners()); // the callback explicitly destroyed the new owner
        assertFalse(f.falling.onUpdate(2));
    }

    @Test void sourceSpawnAndBlockCallbacksSeeOneReleasedActor() {
        Fixture f = new Fixture();
        List<Integer> saved = new ArrayList<>();
        f.events = e -> { if (e instanceof EntitySpawnEvent) saved.add(f.savedOwners()); };
        f.afterWrite = (before, after) -> saved.add(f.savedOwners());
        f.start();
        assertEquals(List.of(1, 1), saved);
        assertFalse(f.falling.isPublicationDeferred());
    }

    @Test void preflightSourceMutationInvalidatesWithoutRecreatingItsOldBlock() {
        Fixture f = new Fixture();
        f.events = e -> { if (e instanceof BlockFallEvent) f.write(80, Block.STONE); };
        f.start();
        assertEquals(Block.STONE, f.block(80).getId());
        assertEquals(0, f.constructed);
        assertEquals(0, f.savedOwners());
    }

    @Test void disabledDropsRetireWithoutCreatingAnItem() {
        Fixture f = new Fixture(); f.start(); f.write(64, Block.TORCH);
        f.rules.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        f.land();
        assertTrue(f.falling.closed);
        assertEquals(0, f.items());
        assertEquals(0, f.savedOwners());
    }

    @Test void recursiveSourceFallDoesNotConstructWhileTheOuterPreflightOwnsTheCell() {
        Fixture f = new Fixture();
        int[] calls = {0};
        f.events = e -> { if (e instanceof BlockFallEvent) {
            if (++calls[0] < 4) f.source.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        }};
        f.start();
        assertEquals(1, calls[0]);
        assertEquals(1, f.constructed);
        assertEquals(1, f.savedOwners());
    }

    @Test void sourcePolicyAndHiddenFactorySnapshotsBothRetainTheSourceBlock() {
        Fixture f = new Fixture();
        List<Integer> saved = new ArrayList<>();
        f.events = e -> { if (e instanceof BlockFallEvent) saved.add(f.savedOwners()); };
        f.factoryHook = () -> saved.add(f.savedOwners());
        f.start();
        assertEquals(List.of(1, 1), saved);
        assertEquals(1, f.savedOwners());
    }

    @Test void nativeFactoryMutationDiscardsTheHiddenActorWithoutRestoringTheOldCell() {
        Fixture f = new Fixture();
        f.factoryHook = () -> f.write(80, Block.STONE);
        f.start();
        assertEquals(Block.STONE, f.block(80).getId());
        assertTrue(f.falling.closed);
        assertEquals(0, f.savedOwners());
        assertFalse(f.falling.canBeSavedWithChunk());
    }

    @Test void sourceSpawnNotificationErrorCannotRestoreTheDebitedBlock() {
        Fixture f = new Fixture();
        f.events = e -> { if (e instanceof EntitySpawnEvent) throw new AssertionError("spawn notification fault"); };
        assertThrows(AssertionError.class, f::start);
        assertEquals(1, f.savedOwners());
        assertEquals(Block.AIR, f.block(80).getId());
        assertNotNull(f.falling);
        assertFalse(f.falling.closed);
        assertFalse(f.falling.isPublicationDeferred());
        assertEquals(1, f.savedOwners());
    }

    @Test void redSandKeepsItsNativeVariantAcrossSourceActorAndCell() {
        Fixture f = new Fixture();
        f.source.setDamage(1);
        f.chunk.setBlock(0, 80, 0, Block.SAND, 1);
        f.start();
        assertEquals(1, f.falling.getDamage());
        assertEquals(1, f.savedOwners());
        f.land();
        assertEquals(Block.SAND, f.block(64).getId());
        assertEquals(1, f.block(64).getDamage());
        assertEquals(1, f.savedOwners());
    }

    @Test void ordinaryLandingCancellationDiscardsWithoutRefund() {
        Fixture f = new Fixture(); f.start();
        f.events = e -> { if (e instanceof EntityBlockChangeEvent b) b.setCancelled(); };
        f.land();
        assertTrue(f.falling.closed);
        assertEquals(Block.AIR, f.block(64).getId());
        assertEquals(0, f.items());
        assertEquals(0, f.savedOwners());
    }

    @Test void itemPreparationSnapshotRetainsTheFallingActor() {
        Fixture f = new Fixture(); f.start(); f.write(64, Block.TORCH);
        List<Integer> saved = new ArrayList<>();
        f.events = e -> { if (e instanceof ItemSpawnEvent) saved.add(f.savedOwners()); };
        f.land();
        assertEquals(List.of(1), saved);
        assertTrue(f.falling.closed);
        assertEquals(1, f.items());
        assertEquals(1, f.savedOwners());
    }

    @Test void itemListenerErrorRetainsTheSavableFallingActor() {
        Fixture f = new Fixture(); f.start(); f.write(64, Block.TORCH);
        f.events = e -> { if (e instanceof ItemSpawnEvent) throw new AssertionError("item preparation fault"); };
        assertThrows(AssertionError.class, f::land);
        assertFalse(f.falling.closed);
        assertEquals(0, f.items());
        assertEquals(1, f.savedOwners());
    }

    @Test void closingPreparedItemIsAnIntentionalDiscard() {
        Fixture f = new Fixture(); f.start(); f.write(64, Block.TORCH);
        f.events = e -> { if (e instanceof ItemSpawnEvent item) item.getEntity().close(); };
        f.land();
        assertTrue(f.falling.closed);
        assertEquals(0, f.items());
        assertEquals(0, f.savedOwners());
    }

    @Test void itemQuantityMutationRetriesTheOriginalFallingActor() {
        Fixture f = new Fixture(); f.start(); f.write(64, Block.TORCH);
        f.events = e -> { if (e instanceof ItemSpawnEvent item) item.getEntity().getItem().setCount(2); };
        f.land();
        assertFalse(f.falling.closed);
        assertEquals(0, f.items());
        assertEquals(1, f.savedOwners());
    }

    @Test void customLevelStillReceivesTheOriginalVirtualSourceWrite() {
        Fixture f = new Fixture(CustomLevel.class);
        f.start();
        assertEquals(1, ((CustomLevel) f.level).writes);
        assertEquals(Block.SAND, f.block(80).getId());
        assertEquals(1, f.constructed);
    }

    @Test void customFactoryAndFallingSubclassKeepTheLegacyHookOrder() {
        Fixture f = new Fixture();
        BlockSand custom = new BlockSand() {
            @Override protected EntityFallingBlock createFallingEntity(CompoundTag ignored) {
                assertEquals(Block.AIR, f.block(80).getId(), "custom factory keeps its original removed-source view");
                f.falling = spy(new CustomFalling(f.chunk, Entity.getDefaultNBT(new Vector3(0.5, 80, 0.5))
                        .putInt("TileID", Block.SAND).putByte("Data", 0)));
                doReturn(false).when(f.falling).move(anyDouble(), anyDouble(), anyDouble());
                doReturn(false).when(f.falling).entityBaseTick(anyInt());
                doNothing().when(f.falling).updateMovement();
                f.live.put(f.falling.getId(), f.falling);
                f.chunk.getEntities().put(f.falling.getId(), f.falling);
                return f.falling;
            }
        };
        custom.level = f.level; custom.x = 0; custom.y = 80; custom.z = 0;
        custom.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        f.events = e -> { if (e instanceof EntityBlockChangeEvent) assertTrue(f.falling.closed); };
        f.land();
        assertEquals(2, ((CustomFalling) f.falling).closes);
        assertEquals(Block.SAND, f.block(64).getId());
    }

    @Test void sourceCallbackCannotRetargetTheNativeFactory() {
        Fixture f = new Fixture();
        f.events = e -> { if (e instanceof BlockFallEvent event) event.getBlock().x = 1; };
        f.start();
        assertEquals(0, f.constructed);
        assertEquals(Block.SAND, f.block(80).getId());
        assertEquals(1, f.savedOwners());
    }

    @Test void sourceCallbackCannotChangeTheGrainsVariant() {
        Fixture f = new Fixture();
        f.events = e -> { if (e instanceof BlockFallEvent event) event.getBlock().setDamage(1); };
        f.start();
        assertEquals(0, f.constructed);
        assertEquals(0, f.block(80).getDamage());
        assertEquals(1, f.savedOwners());
    }

    @Test void nativeSectionWithCustomStorageKeepsItsLegacyVirtualSetter() {
        Fixture f = new Fixture(); f.start();
        boolean[] armed = {false}; int[] writes = {0};
        var storage = new cn.nukkit.level.format.leveldb.structure.StateBlockStorage() {
            @Override public void set(int x, int y, int z, int fullId) {
                if (armed[0]) {
                    writes[0]++;
                    assertTrue(f.falling.closed, "custom storage preserves the original close-before-write hook");
                }
                super.set(x, y, z, fullId);
            }
        };
        storage.set(0, 15, 0, Block.STONE << Block.DATA_BITS);
        var section = new cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection(4,
                new cn.nukkit.level.format.leveldb.structure.StateBlockStorage[]{storage,
                        new cn.nukkit.level.format.leveldb.structure.StateBlockStorage()}, true);
        f.chunk.setSection(4, section);
        armed[0] = true;
        f.land();
        assertEquals(1, writes[0]);
        assertEquals(Block.SAND, f.block(64).getId());
    }

    @Test void customLegacyMapperKeepsItsOriginalClosedActorView() {
        Fixture f = new Fixture(); f.start();
        var mapping = cn.nukkit.level.format.leveldb.BlockStateMapping.get();
        var previous = mapping.getLegacyMapper();
        int[] mapped = {0};
        mapping.setLegacyMapper(new cn.nukkit.level.format.leveldb.NukkitLegacyMapper() {
            @Override public int legacyToRuntime(int id, int meta) {
                if (id == Block.SAND) {
                    mapped[0]++;
                    assertTrue(f.falling.closed, "custom mapper keeps its original close-before-write hook");
                }
                return previous.legacyToRuntime(id, meta);
            }
            @Override public int runtimeToFullId(int id) { return previous.runtimeToFullId(id); }
            @Override public int runtimeToLegacyId(int id) { return previous.runtimeToLegacyId(id); }
            @Override public int runtimeToLegacyData(int id) { return previous.runtimeToLegacyData(id); }
        });
        try { f.land(); }
        finally { mapping.setLegacyMapper(previous); }
        assertEquals(1, mapped[0]);
        assertEquals(Block.SAND, f.block(64).getId());
    }

    @Test void nativeStorageWithCustomSnapshotKeepsItsLegacyGetterOrder() {
        Fixture f = new Fixture(); f.start();
        boolean[] armed = {false}; int[] reads = {0};
        var snapshot = new cn.nukkit.level.format.leveldb.structure.FallingSnapshotProbe(() -> {
            if (armed[0]) { reads[0]++; assertTrue(f.falling.closed, "custom snapshot keeps its legacy close-before-read hook"); }
        });
        var storage = new cn.nukkit.level.format.leveldb.structure.StateBlockStorage();
        storage.set(0, 0, 0, snapshot);
        storage.set(0, 15, 0, Block.STONE << Block.DATA_BITS);
        f.chunk.setSection(4, new cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection(4,
                new cn.nukkit.level.format.leveldb.structure.StateBlockStorage[]{storage,
                        new cn.nukkit.level.format.leveldb.structure.StateBlockStorage()}, true));
        armed[0] = true;
        f.land();
        assertTrue(reads[0] > 0);
        assertEquals(Block.SAND, f.block(64).getId());
    }

    @Test void nativeSectionWithCustomParentKeepsItsLegacyCallback() {
        Fixture f = new Fixture(); f.start();
        var storage = new cn.nukkit.level.format.leveldb.structure.StateBlockStorage();
        storage.set(0, 15, 0, Block.STONE << Block.DATA_BITS);
        var section = new cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection(4,
                new cn.nukkit.level.format.leveldb.structure.StateBlockStorage[]{storage,
                        new cn.nukkit.level.format.leveldb.structure.StateBlockStorage()}, true);
        f.chunk.setSection(4, section);
        List<Boolean> closed = new ArrayList<>(); List<Integer> owners = new ArrayList<>();
        section.setParent(foreignParent(f, () -> { closed.add(f.falling.closed); owners.add(f.savedOwners()); }));
        f.land();
        assertEquals(List.of(true), closed);
        assertEquals(List.of(1), owners);
        assertEquals(Block.SAND, f.block(64).getId());
    }

    @Test void landingCallbackCannotRetargetTheNativeSectionParent() {
        Fixture f = new Fixture(); f.start();
        int[] callbacks = {0}; var parent = foreignParent(f, () -> callbacks[0]++);
        f.events = e -> { if (e instanceof EntityBlockChangeEvent)
            ((cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection) f.chunk.getSection(4)).setParent(parent);
        };
        f.land();
        assertFalse(f.falling.closed);
        assertEquals(Block.AIR, f.block(64).getId());
        assertEquals(1, f.savedOwners());
        assertEquals(0, callbacks[0]);
    }

    @Test void sourceCallbackCannotRetargetTheNativeSectionParent() {
        Fixture f = new Fixture(); int[] callbacks = {0};
        var parent = foreignParent(f, () -> callbacks[0]++);
        f.events = e -> { if (e instanceof BlockFallEvent)
            ((cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection) f.chunk.getSection(5)).setParent(parent);
        };
        f.start();
        assertEquals(0, f.constructed);
        assertEquals(Block.SAND, f.block(80).getId());
        assertEquals(1, f.savedOwners());
        assertEquals(0, callbacks[0]);
    }

    @Test void factoryCallbackCannotRetargetTheNativeSectionParent() {
        Fixture f = new Fixture(); int[] callbacks = {0};
        var parent = foreignParent(f, () -> callbacks[0]++);
        f.factoryHook = () -> ((cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection)
                f.chunk.getSection(5)).setParent(parent);
        f.start();
        assertTrue(f.falling.closed);
        assertEquals(Block.SAND, f.block(80).getId());
        assertEquals(1, f.savedOwners());
        assertEquals(0, callbacks[0]);
    }

    @Test void landingCallbackCannotReplaceTheNativeStorageWithAVirtualSetter() {
        Fixture f = new Fixture(); f.start(); int[] writes = {0};
        f.events = e -> { if (e instanceof EntityBlockChangeEvent) {
            var section = (cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection) f.chunk.getSection(4);
            section.getStorages()[0] = new cn.nukkit.level.format.leveldb.structure.StateBlockStorage() {
                @Override public void set(int x, int y, int z, int value) { writes[0]++; super.set(x, y, z, value); }
            };
        }};
        f.land();
        assertFalse(f.falling.closed);
        assertEquals(Block.AIR, f.block(64).getId());
        assertEquals(1, f.savedOwners());
        assertEquals(0, writes[0]);
    }

    private static LevelDBChunk foreignParent(Fixture f, Runnable changed) {
        return new LevelDBChunk(f.provider, 0, 0) {
            @Override public void onSubChunkBlockChanged(cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection section,
                    int x, int y, int z, int layer, int before, int after) {
                changed.run();
                super.onSubChunkBlockChanged(section, x, y, z, layer, before, after);
            }
        };
    }

    static class CustomLevel extends Level {
        int writes;
        CustomLevel() { super(null, "unused", "unused", LevelDBProvider.class); }
        @Override public boolean setBlock(Vector3 pos, Block block, boolean direct, boolean update) {
            writes++;
            return false;
        }
    }

    static class CustomFalling extends EntityFallingBlock {
        int closes;
        CustomFalling(cn.nukkit.level.format.FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }
        @Override public void close() { closes++; super.close(); }
    }

    static final class Fixture {
        final Level level;
        private Level makeLevel(Class<? extends Level> type) { return mock(type, invocation -> {
            // Baseline calls only public entry points. Future coordinator helpers delegate to native code.
            if (invocation.getMethod().getName().equals("prepareFallingCell")
                    || invocation.getMethod().getName().equals("prepareFallingItemDrop")) return invocation.callRealMethod();
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        }); }
        final PluginManager plugins = Server.getInstance().getPluginManager();
        final LevelDBProvider provider = mock(LevelDBProvider.class);
        final GameRules rules = GameRules.getDefault();
        final cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<Entity> live =
                new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<>();
        LevelDBChunk chunk;
        EntityFallingBlock falling;
        Consumer<Event> events = e -> {};
        BiConsumer<Block, Block> afterWrite = (before, after) -> {};
        int constructed;
        int refuseY = Integer.MIN_VALUE;
        boolean factoryFails;
        Runnable factoryHook = () -> {};
        final BlockSand source = spy(new BlockSand());

        Fixture() { this(Level.class); }
        Fixture(Class<? extends Level> type) {
            level = makeLevel(type);
            reset(plugins);
            assertEquals(type, level.getClass());
            assertEquals(BlockSand.class, source.getClass());
            doAnswer(invocation -> {
                constructed++;
                if (factoryFails) throw new IllegalStateException("factory fault");
                falling = spy((EntityFallingBlock) invocation.callRealMethod());
                // Deterministic tick placement; constructor, source, landing and ownership paths remain native.
                doReturn(false).when(falling).move(anyDouble(), anyDouble(), anyDouble());
                doReturn(false).when(falling).entityBaseTick(anyInt());
                doNothing().when(falling).updateMovement();
                live.put(falling.getId(), falling);
                chunk.getEntities().put(falling.getId(), falling);
                factoryHook.run();
                return falling;
            }).when(source).createFallingEntity(any());
            when(Server.getInstance().isPrimaryThread()).thenReturn(true);
            when(level.getServer()).thenReturn(Server.getInstance());
            when(level.getFolderName()).thenReturn("world");
            when(level.getGameRules()).thenReturn(rules);
            when(level.getDimensionData()).thenReturn(new DimensionData(0, 0, 255));
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.isYInRange(anyInt())).thenAnswer(i -> (int)i.getArgument(0) >= 0
                    && (int)i.getArgument(0) <= 255 && (int)i.getArgument(0) != refuseY);
            when(provider.getLevel()).thenReturn(level);
            when(provider.getMaximumLayer()).thenReturn(1);
            when(level.getProvider()).thenReturn(provider);
            set(level, Level.class, "server", Server.getInstance());
            set(level, Level.class, "provider", provider);
            set(level, Level.class, "entities", live);
            set(level, Level.class, "updateEntities", new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<Entity>());
            when(level.requireProvider()).thenCallRealMethod();
            chunk = new LevelDBChunk(provider, 0, 0);
            when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
            when(level.getChunk(0, 0, true)).thenReturn(chunk);
            when(level.getChunk(0, 0)).thenReturn(chunk);
            when(level.getEntity(anyLong())).thenAnswer(i -> live.get((long)i.getArgument(0)));
            doAnswer(i -> { Entity e = i.getArgument(0); live.put(e.getId(), e); return null; }).when(level).addEntity(any());
            doCallRealMethod().when(level).removeEntity(any());
            doCallRealMethod().when(level).discardDeferredEntityRegistration(any());
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
            when(level.getChunkLoaders(anyInt(), anyInt())).thenReturn(new ChunkLoader[0]);
            when(level.getNearbyEntities(any())).thenReturn(new Entity[0]);
            when(level.getCollisionCubes(any(), any(), anyBoolean())).thenReturn(new cn.nukkit.math.AxisAlignedBB[0]);
            when(level.getBlock(any(Vector3.class))).thenAnswer(i -> {
                Vector3 pos = i.getArgument(0); return block(pos.getFloorY());
            });
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(i -> {
                Block b = Block.get(Block.AIR); b.level = level; return b;
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i -> block(i.getArgument(1)));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenCallRealMethod();
            when(level.setBlock(any(Vector3.class), anyInt(), any(Block.class), anyBoolean(), anyBoolean())).thenCallRealMethod();
            when(level.setBlock(anyInt(), anyInt(), anyInt(), anyInt(), any(Block.class), anyBoolean(), anyBoolean())).thenCallRealMethod();
            Int2ObjectOpenHashMap<BiConsumer<Block, Block>> callbacks = new Int2ObjectOpenHashMap<>();
            callbacks.put(1, (before, after) -> afterWrite.accept(before, after));
            set(level, Level.class, "callbackBlockSet", callbacks);
            doAnswer(i -> { events.accept(i.getArgument(0)); return null; }).when(plugins).callEvent(any());
            chunk.setBlock(0, 80, 0, Block.SAND);
            chunk.setBlock(0, 63, 0, Block.STONE);
            source.level = level; source.x = 0; source.y = 80; source.z = 0;
            when(level.randomTickingEnabled()).thenReturn(true);
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class));
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), nullable(Vector3.class));
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), nullable(Vector3.class), anyInt());
            doCallRealMethod().when(level).dropItem(any(Vector3.class), any(Item.class), nullable(Vector3.class), anyBoolean(), anyInt());
        }
        void start() { source.onUpdate(Level.BLOCK_UPDATE_NORMAL); }
        void land() {
            assertNotNull(falling, "source must publish one actor");
            falling.y = 64; falling.onGround = true;
            falling.onUpdate(1);
        }
        void write(int y, int id) { level.setBlock(new Vector3(0, y, 0), Block.get(id), true, true); }
        Block block(int y) {
            Block b = Block.fromFullId(chunk.getFullBlock(0, y, 0));
            b.level = level; b.x = 0; b.y = y; b.z = 0; return b;
        }
        int items() {
            return (int) chunk.getEntities().values().stream().filter(e -> e instanceof EntityItem
                    && !e.closed && !e.isPublicationDeferred()).count();
        }
        int savedOwners() {
            WriteBatch batch = mock(WriteBatch.class);
            int[] actors = {0};
            doAnswer(i -> {
                if (new String((byte[])i.getArgument(0), StandardCharsets.ISO_8859_1).startsWith("actorprefix")) actors[0]++;
                return batch;
            }).when(batch).put(any(byte[].class), any(byte[].class));
            EntitySerializer.snapshotEntities(batch, chunk);
            return actors[0] + (block(80).getId() == Block.SAND ? 1 : 0) + (block(64).getId() == Block.SAND ? 1 : 0);
        }
        static void set(Object target, Class<?> owner, String field, Object value) {
            try { Field f = owner.getDeclaredField(field); f.setAccessible(true); f.set(target, value); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }
}
