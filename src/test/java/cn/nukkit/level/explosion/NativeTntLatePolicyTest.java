package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityPrimedTNT;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.entity.EntityPrimeAcceptedEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.WeakExplosion;
import cn.nukkit.level.StrongExplosion;
import cn.nukkit.level.Position;
import cn.nukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import java.util.ArrayList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Actual Level setter, raw LevelDB write and sealed native actor; explicit two-cell selection. */
@Isolated("native Server.instance, item registries and plugin handler lists")
class NativeTntLatePolicyTest {
    enum Model { BASE, WEAK, STRONG }
    enum Mode { ALLOWED, AFTER_SPAWN_FALSE, AFTER_RAW_FALSE, VALIDATOR_ERROR, RESTORE_ERROR, ACCEPTED_ERROR, RESTORE_REFUSED, RESTORE_STONE, RESTORE_WRONG_TNT, MUTATED_DESCRIPTOR }

    @BeforeAll static void bootstrap() throws Exception { NativeExplosionFixture.boot(); }
    @AfterAll static void restoreServer() throws Exception { NativeExplosionFixture.restoreServer(); }

    static Stream<Arguments> cases() {
        return Stream.of(Model.values()).flatMap(model -> Stream.of(Mode.values()).map(mode -> Arguments.of(model, mode)));
    }

    @ParameterizedTest @MethodSource("cases")
    void independentCellContinuesOnlyAfterCleanPolicyRefusal(Model model, Mode mode) throws Exception {
        var world = NativeActualLevelSetterTest.world(Block.TNT);
        world.gameRules = cn.nukkit.level.GameRules.getDefault();
        world.chunk.setBlock(0, 64, 1, Block.PLANKS, 0);
        world.chunk.refuseTntRestoration = mode == Mode.RESTORE_REFUSED;
        if (mode == Mode.RESTORE_STONE) world.chunk.restoredBlockOverride = Block.get(Block.STONE);
        if (mode == Mode.RESTORE_WRONG_TNT) world.chunk.restoredTntMetadata = 1;
        Block source = world.getBlock(0, 64, 0);
        Position center = new Position(.5, 64, .5, world);
        Explosion blast = switch (model) {
            case BASE -> new Explosion(center, 1, (Entity) null);
            case WEAK -> new WeakExplosion(center, 1, (Entity) null);
            case STRONG -> new StrongExplosion(center, 1, (Entity) null);
        };
        List<Block> selected = List.of(source, world.getBlock(0, 64, 1));
        NativeExplosionFixture.set(blast.getClass(), blast, "affectedBlocks",
                model == Model.BASE ? new LinkedHashSet<>(selected) : new ArrayList<>(selected));
        AtomicBoolean allowed = new AtomicBoolean(true);
        AtomicInteger rawWrites = new AtomicInteger(), accepted = new AtomicInteger();
        AtomicReference<EntityPrimedTNT> prepared = new AtomicReference<>();
        AtomicReference<EntityExplodeEvent> event = new AtomicReference<>();
        RuntimeException validationFailure = new IllegalArgumentException("actual validator failure");
        AssertionError restorationFailure = new AssertionError("actual restoration notification failure");
        AssertionError acceptedFailure = new AssertionError("accepted notification failure");
        world.chunk.afterSourceRawAir = () -> {
            rawWrites.incrementAndGet();
            assertTrue(world.getBlock(source).isAir());
            if (mode == Mode.AFTER_RAW_FALSE) allowed.set(false);
        };
        @SuppressWarnings("unchecked")
        var callbacks = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<java.util.function.BiConsumer<Block, Block>>)
                NativeTntLatePolicyTest.field(world, "callbackBlockSet");
        callbacks.put(1, (before, after) -> {
            if (mode == Mode.RESTORE_ERROR && after.getId() == Block.TNT) throw restorationFailure;
        });
        RegisteredListener plan = NativeExplosionDeferredPrimeTest.listen(EntityExplodeEvent.getHandlers(),
                EventPriority.HIGH, raw -> {
                    var current = (EntityExplodeEvent) raw;
                    assertNull(event.getAndSet(current));
                    current.setYield(100);
                    current.addBlockProcessor(new NativeExplosionBlockProcessor() {
                        @Override public Result process(Block block) { return Result.NATIVE; }
                        @Override public boolean canRemove(List<Block> originals) {
                            if (originals.stream().noneMatch(block -> block.getId() == Block.TNT)) return true;
                            if (mode == Mode.VALIDATOR_ERROR && rawWrites.get() > 0) throw validationFailure;
                            if (mode == Mode.MUTATED_DESCRIPTOR && rawWrites.get() > 0) { originals.get(0).x = 1; return false; }
                            return allowed.get();
                        }
                    });
                });
        RegisteredListener spawn = NativeExplosionDeferredPrimeTest.listen(EntitySpawnEvent.getHandlers(),
                EventPriority.MONITOR, raw -> {
                    if (!(((EntitySpawnEvent) raw).getEntity() instanceof EntityPrimedTNT tnt)) return;
                    assertNull(prepared.getAndSet(tnt));
                    assertTrue(tnt.isPublicationDeferred());
                    assertEquals(Block.TNT, world.getBlock(source).getId());
                    if (mode == Mode.AFTER_SPAWN_FALSE || mode == Mode.RESTORE_ERROR || mode == Mode.RESTORE_REFUSED || mode == Mode.RESTORE_STONE || mode == Mode.RESTORE_WRONG_TNT) allowed.set(false);
                });
        RegisteredListener publication = NativeExplosionDeferredPrimeTest.listen(EntityPrimeAcceptedEvent.getHandlers(),
                EventPriority.NORMAL, raw -> {
                    var current = (EntityPrimeAcceptedEvent) raw;
                    accepted.incrementAndGet();
                    assertSame(prepared.get(), current.getEntity());
                    assertTrue(world.getBlock(source).isAir());
                    assertTrue(current.getEntity().isPublicationDeferred());
                    if (mode == Mode.ACCEPTED_ERROR) throw acceptedFailure;
                });
        Throwable failure = null;
        boolean returned = false;
        try {
            try { returned = blast.explodeB(); } catch (RuntimeException | Error error) { failure = error; }
        } finally {
            EntityExplodeEvent.getHandlers().unregister(plan);
            EntitySpawnEvent.getHandlers().unregister(spawn);
            EntityPrimeAcceptedEvent.getHandlers().unregister(publication);
        }
        if (mode == Mode.ALLOWED) assertNull(failure, "qualify actual accepted native setter: " + failure);
        boolean committed = mode == Mode.ALLOWED || mode == Mode.ACCEPTED_ERROR;
        boolean interrupted = mode == Mode.VALIDATOR_ERROR || mode == Mode.RESTORE_ERROR || mode == Mode.ACCEPTED_ERROR || mode == Mode.RESTORE_REFUSED || mode == Mode.RESTORE_STONE || mode == Mode.RESTORE_WRONG_TNT || mode == Mode.MUTATED_DESCRIPTOR;
        assertNotNull(prepared.get(), "actual constructor callback must run");
        assertEquals(1, rawWrites.get());
        assertEquals(mode == Mode.RESTORE_STONE ? Block.STONE : committed || mode == Mode.RESTORE_REFUSED || mode == Mode.MUTATED_DESCRIPTOR ? Block.AIR : Block.TNT, world.getBlock(source).getId());
        if (mode == Mode.RESTORE_WRONG_TNT) assertEquals((Block.TNT << Block.DATA_BITS) | 1, world.chunk.getFullBlock(0, 64, 0, 0), "qualify physical metadata divergence");
        if (mode == Mode.MUTATED_DESCRIPTOR) assertEquals(Block.TNT, world.getBlock(1, 64, 0).getId());
        assertEquals(interrupted ? Block.PLANKS : Block.AIR, world.getBlock(0, 64, 1).getId());
        assertEquals(committed ? 1 : 0, accepted.get());
        assertEquals(committed ? 1 : 0, world.actors.values().stream()
                .filter(actor -> actor instanceof EntityPrimedTNT && !actor.closed && !actor.isPublicationDeferred()).count());
        assertEquals(0, world.actors.values().stream().filter(Entity::isPublicationDeferred).count());
        assertEquals(interrupted ? 0 : 1, world.actors.values().stream()
                .filter(actor -> actor instanceof EntityItem && !actor.closed && !actor.isPublicationDeferred())
                .mapToInt(actor -> ((EntityItem) actor).getItem().getCount()).sum());
        assertEquals(committed ? 1 : 0, event.get().getBlockProcessing().getRemovedBlocks().stream()
                .filter(block -> block.getId() == Block.TNT).count());
        assertEquals(interrupted ? 0 : 1, event.get().getBlockProcessing().getRemovedBlocks().stream()
                .filter(block -> block.getId() == Block.PLANKS).count());
        if (mode == Mode.RESTORE_REFUSED || mode == Mode.RESTORE_STONE || mode == Mode.RESTORE_WRONG_TNT || mode == Mode.MUTATED_DESCRIPTOR) assertInstanceOf(IllegalStateException.class, failure, "unrecovered storage source must remain an error");
        else if (mode == Mode.VALIDATOR_ERROR) assertSame(validationFailure, failure);
        else if (mode == Mode.RESTORE_ERROR) assertSame(restorationFailure, failure);
        else if (mode == Mode.ACCEPTED_ERROR) assertSame(acceptedFailure, failure);
        else { assertNull(failure, "a clean policy false must not manufacture an exception"); assertTrue(returned); }
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = cn.nukkit.level.Level.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
