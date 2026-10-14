package cn.nukkit.level.explosion;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityBeehive;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityBee;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.block.NativeExplosionBlockProcessor;
import cn.nukkit.item.Item;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.Plugin;
import java.lang.reflect.Field;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Actual Level setter, native Bee actors, real PluginManager and exact occupant ownership. */
@org.junit.jupiter.api.parallel.Isolated("native Server.instance, item registries and plugin handler lists")
final class BeeExplosionConservationTest {
    @org.junit.jupiter.api.AfterAll static void restoreFixtureServer() throws Exception { NativeExplosionFixture.restoreServer(); }
    @BeforeAll static void boot() throws Exception {
        NativeExplosionFixture.boot();
        Entity.registerEntity("Bee", EntityBee.class, true);
        Entity.registerEntity("XpOrb", cn.nukkit.entity.item.EntityXPOrb.class, true);
    }

    static final class Source {
        final BeeExplosionFixture.RealLevel world;
        final BlockEntityBeehive hive;
        final int blockId;
        Source(int blockId) throws Exception {
            this.blockId = blockId;
            world = BeeExplosionFixture.world(blockId);
            hive = hive(world, 2);
        }
        void blast() throws Exception {
            Explosion explosion = new Explosion(new Position(.5, 64.5, .5, world), 4, (Entity) null);
            NativeExplosionFixture.set(Explosion.class, explosion, "affectedBlocks",
                    new LinkedHashSet<>(List.of(world.getBlock(0, 64, 0))));
            explosion.explodeB();
        }
        long live() { return world.actors.values().stream().filter(e -> e instanceof EntityBee && !e.closed && !e.isPublicationDeferred()).count(); }
        long deferred() { return world.actors.values().stream().filter(e -> e instanceof EntityBee && !e.closed && e.isPublicationDeferred()).count(); }
        void original() {
            assertThat(world.getBlock(0, 64, 0).getId()).isEqualTo(blockId);
            assertThat(hive.closed).isFalse();
            assertThat(hive.canSaveToStorage()).isTrue();
            assertThat(hive.getOccupantsCount()).isEqualTo(2);
            assertThat(live()).isZero();
            assertThat(deferred()).isZero();
        }
        void accepted() {
            assertThat(world.getBlock(0, 64, 0).isAir()).isTrue();
            assertThat(hive.getOccupantsCount()).isZero();
            assertThat(live()).isEqualTo(2);
            assertThat(deferred()).isZero();
            assertThat(world.actors.values().stream().filter(e -> e instanceof EntityBee && !e.closed)
                    .allMatch(Entity::canBeSavedWithChunk)).isTrue();
            assertThat(world.actors.values().stream().filter(e -> e instanceof EntityBee && !e.closed)
                    .map(e -> e.namedTag.getString("CustomName"))).containsExactlyInAnyOrder("occupant-0", "occupant-1");
        }
    }
    static BlockEntityBeehive hive(BeeExplosionFixture.RealLevel world, int count) throws Exception {
        BlockEntityBeehive hive = NativeExplosionFixture.allocated(BlockEntityBeehive.class);
        hive.level=world; hive.chunk=world.chunk; hive.x=0; hive.y=64; hive.z=0;
        hive.namedTag=new CompoundTag().putList(new cn.nukkit.nbt.tag.ListTag<CompoundTag>("Occupants"));
        NativeExplosionFixture.set(BlockEntity.class, hive, "scheduledForBlockEntityUpdate", new java.util.concurrent.atomic.AtomicBoolean());
        List<BlockEntityBeehive.Occupant> occupants=new java.util.ArrayList<>();
        for (int i=0; i<count; i++) occupants.add(new BlockEntityBeehive.Occupant(600,"Bee",
                Entity.getDefaultNBT(new Position(.5,64,.5,world)).putString("CustomName","occupant-"+i)));
        NativeExplosionFixture.set(BlockEntityBeehive.class,hive,"occupants",occupants); world.tile=hive;
        NativeExplosionFixture.register(world,hive); return hive;
    }
    static Plugin plugin() { Plugin p = mock(Plugin.class); when(p.isEnabled()).thenReturn(true); return p; }
    static Listener onSpawn(Consumer<EntityBee> action) {
        Listener listener = new Listener() {};
        NativeExplosionFixture.server.getPluginManager().registerEvent(EntitySpawnEvent.class, listener,
                EventPriority.NORMAL, (l, e) -> {
                    Entity actor = ((EntitySpawnEvent) e).getEntity();
                    if (actor instanceof EntityBee bee) action.accept(bee);
                }, plugin());
        return listener;
    }
    static void notifications(Source s, Consumer<Block> callback) throws Exception {
        Field f = Level.class.getDeclaredField("callbackBlockSet"); f.setAccessible(true);
        @SuppressWarnings("unchecked") var callbacks = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<java.util.function.BiConsumer<Block, Block>>) f.get(s.world);
        callbacks.put(89, (before, after) -> { if (after.isAir()) callback.accept(after); });
    }

    static long saveableResources(Source s) {
        long actors=s.world.actors.values().stream().filter(e -> e instanceof EntityBee && !e.closed
                && !e.isPublicationDeferred() && e.canBeSavedWithChunk()).count();
        return actors + (s.world.getBlock(0,64,0).getId()==s.blockId && s.hive.canSaveToStorage()
                ? s.hive.getOccupantsCount() : 0);
    }

    /** Baseline-compatible oracle: closed tile occupants are not durable resources. */
    @Test void notificationCloseRetainsExactlyTwoSaveableResources() throws Exception {
        Source s=new Source(Block.BEEHIVE);
        notifications(s, ignored -> s.hive.close()); s.blast();
        assertThat(saveableResources(s)).isEqualTo(2);
        assertThat(s.hive.getOccupantsCount()).isZero();
        s.hive.onBreak(); assertThat(saveableResources(s)).isEqualTo(2);
    }

    /** Either complete acceptance or exact intact retention is valid; partial ownership is not. */
    @Test void secondActorInitFailureRetainsExactlyTwoSaveableResources() throws Exception {
        Source s=new Source(Block.BEEHIVE); int[] calls={0};
        Listener listener=onSpawn(bee -> { if (++calls[0]==2) throw new AssertionError("second Bee init"); });
        try { s.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
        assertThat(calls[0]).isEqualTo(2); assertThat(saveableResources(s)).isEqualTo(2);
        if (!s.world.getBlock(0,64,0).isAir()) s.original();
        else s.accepted();
    }

    @Test void notificationCloseSeesAllAcceptedBeesAndReplayCannotReleaseAgain() throws Exception {
        for (int id : List.of(Block.BEEHIVE, Block.BEE_NEST)) {
            Source s = new Source(id);
            notifications(s, ignored -> {
                assertThat(s.hive.getOccupantsCount()).isZero();
                assertThat(s.live()).isEqualTo(2);
                s.hive.close();
            });
            s.blast(); s.accepted(); s.hive.onBreak(); s.blast(); s.accepted();
        }
    }

    @Test void partialActorConstructionFailureRetainsEveryOriginalOccupant() throws Exception {
        Source s = new Source(Block.BEEHIVE);
        CompoundTag first = s.hive.getOccupants()[0].saveNBT().copy();
        int[] calls = {0};
        Listener listener = onSpawn(bee -> {
            assertThat(bee.isPublicationDeferred()).isTrue();
            assertThat(bee.canBeSavedWithChunk()).isFalse();
            assertThat(s.world.getBlock(0, 64, 0).getId()).isEqualTo(Block.BEEHIVE);
            if (++calls[0] == 2) throw new AssertionError("second bee init failure");
        });
        try { s.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
        assertThat(calls[0]).isEqualTo(2); s.original();
        assertThat(s.hive.getOccupants()[0].saveNBT()).isEqualTo(first);
        s.blast(); s.accepted();
    }

    @Test void lateCancellationOrFilteringNeverPreparesOrDebitsOccupants() throws Exception {
        for (boolean cancel : List.of(false, true)) {
            Source s = new Source(Block.BEEHIVE); int[] spawns = {0};
            Listener spawn = onSpawn(bee -> spawns[0]++);
            Listener late = new Listener() {};
            NativeExplosionFixture.server.getPluginManager().registerEvent(EntityExplodeEvent.class, late,
                    EventPriority.HIGHEST, (l,e) -> {
                        if (cancel) ((EntityExplodeEvent)e).setCancelled(true);
                        else ((EntityExplodeEvent)e).setBlockList(List.of());
                    }, plugin());
            try { s.blast(); } finally {
                EntityExplodeEvent.getHandlers().unregister(late); EntitySpawnEvent.getHandlers().unregister(spawn);
            }
            assertThat(spawns[0]).isZero(); s.original();
        }
    }

    @Test void sourceWriteRefusalOrPreacceptThrowDiscardsAllSealedActors() throws Exception {
        for (String mode : List.of("refuse", "before", "after-raw")) {
            Source s = new Source(Block.BEEHIVE);
            s.world.chunk.sourceFailure = mode;
            try { s.blast(); } catch (IllegalStateException controlled) { assertThat(controlled.getMessage()).contains("source"); }
            s.original(); s.world.chunk.sourceFailure = ""; s.blast(); s.accepted();
        }
    }

    @Test void closedKilledMovedOrRetaggedPreparedActorCannotSpendHive() throws Exception {
        for (String fault : List.of("close", "kill", "move", "nbt", "health", "chunk-detach", "level-detach", "unsaveable")) {
            Source s = new Source(Block.BEEHIVE);
            Listener listener = onSpawn(bee -> {
                switch (fault) {
                    case "close" -> bee.close();
                    case "kill" -> bee.kill();
                    case "move" -> bee.setPosition(new cn.nukkit.math.Vector3(10, 64, 0));
                    case "nbt" -> bee.namedTag.putString("CustomName", "replacement");
                    case "health" -> bee.setMaxHealth(40);
                    case "chunk-detach" -> s.world.chunk.removeEntity(bee);
                    case "level-detach" -> s.world.removeEntity(bee);
                    case "unsaveable" -> bee.setCanBeSavedWithChunk(false);
                }
            });
            try { s.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
            s.original();
        }
    }

    @Test void changedSourceOccupantIsNeverReplacedByCapturedSnapshot() throws Exception {
        Source s = new Source(Block.BEEHIVE); int[] events = {0};
        Listener listener = onSpawn(bee -> { if (++events[0] == 1) s.hive.getOccupants()[0].setTicksLeftToStay(999); });
        try { s.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
        s.original(); assertThat(s.hive.getOccupants()[0].getTicksLeftToStay()).isEqualTo(999);
    }

    @Test void acceptedMetadataOrWorldNotificationFailureNeverRefundsOccupants() throws Exception {
        for (boolean metadata : List.of(false, true)) {
            Source s = new Source(Block.BEEHIVE);
            if (!metadata) notifications(s, ignored -> { s.hive.close(); throw new AssertionError("source accepted notification"); });
            try {
                if (metadata) Explosion.removeSource(s.world.getBlock(0,64,0), Item.get(Item.DIAMOND_PICKAXE), true, List.of(),
                        new NativeExplosionBlockProcessor() {
                            public Result process(Block block) { return Result.NATIVE; }
                            public void removed(List<Block> blocks) { throw new AssertionError("source accepted metadata"); }
                        });
                else s.blast();
            } catch (AssertionError controlled) { assertThat(controlled.getMessage()).contains("source accepted"); }
            s.accepted(); s.hive.onBreak(); assertThat(s.live()).isEqualTo(2);
        }
    }

    @Test void beePublicationPacketCannotDiscardStillDeferredFundedShell() throws Exception {
        Source s = new Source(Block.BEEHIVE);
        cn.nukkit.Player viewer = mock(cn.nukkit.Player.class);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getLoaderId()).thenReturn(91);
        NativeExplosionFixture.set(cn.nukkit.Player.class, viewer, "usedChunks",
                new java.util.HashMap<>(java.util.Map.of(Level.chunkHash(0, 0), true)));
        NativeExplosionFixture.set(cn.nukkit.Player.class, viewer, "connected", true);
        NativeExplosionFixture.set(Entity.class, viewer, "server", NativeExplosionFixture.server);
        viewer.protocol = cn.nukkit.network.protocol.ProtocolInfo.CURRENT_PROTOCOL;
        doCallRealMethod().when(viewer).dataPacket(any(cn.nukkit.network.protocol.DataPacket.class));
        boolean previous = NativeExplosionFixture.server.callDataPkSendEv;
        NativeExplosionFixture.server.callDataPkSendEv = true;
        int[] packets = {0}, discarded = {0};
        Listener listener = new Listener() {};
        NativeExplosionFixture.server.getPluginManager().registerEvent(cn.nukkit.event.server.DataPacketSendEvent.class,
                listener, EventPriority.NORMAL, (l, event) -> {
                    var sent = (cn.nukkit.event.server.DataPacketSendEvent) event;
                    if (sent.getPacket() instanceof cn.nukkit.network.protocol.AddEntityPacket packet
                            && packet.type == EntityBee.NETWORK_ID) {
                        packets[0]++;
                        for (Entity actor : new java.util.ArrayList<>(s.world.actors.values())) {
                            if (actor instanceof cn.nukkit.entity.item.EntityItem && actor.isPublicationDeferred()) {
                                discarded[0]++; actor.close();
                            }
                        }
                    }
                    sent.setCancelled(true);
                }, plugin());
        s.world.observers = java.util.Map.of(91, viewer);
        try {
            assertThat(Explosion.removeSource(s.world.getBlock(0, 64, 0), Item.get(Item.AIR), true,
                    List.of(Item.get(Block.BEEHIVE)), new NativeExplosionBlockProcessor() {
                        public Result process(Block block) { return Result.NATIVE; }
                    })).isTrue();
            assertThat(packets[0]).isEqualTo(2);
            assertThat(discarded[0]).isZero();
            s.accepted();
            assertThat(s.world.all()).isEqualTo(1);
        } finally {
            cn.nukkit.event.server.DataPacketSendEvent.getHandlers().unregister(listener);
            NativeExplosionFixture.server.callDataPkSendEv = previous;
            s.world.observers = java.util.Map.of();
        }
    }

    @Test void pureCaptureAndUnfundedCloseKeepHiveExactAndReleaseNoActors() throws Exception {
        Source s = new Source(Block.BEEHIVE);
        Explosion.StoredPayload payload = Explosion.captureStoredPayload(s.world.getBlock(0,64,0));
        assertThat(payload.goods()).isEmpty(); assertThat(s.deferred()).isZero();
        assertThat(payload.prepare()).isTrue(); assertThat(s.deferred()).isEqualTo(2);
        payload.close(); payload.close(); s.original();
    }

    @Test void unknownOrRemappedIdentifierFailsClosedWithoutInventingAnActor() throws Exception {
        Source s = new Source(Block.BEEHIVE);
        s.hive.getOccupants()[0].setActorIdentifier("missing:occupant");
        s.blast(); s.original();
        Source remapped = new Source(Block.BEEHIVE);
        Entity.registerEntity("Bee", cn.nukkit.entity.passive.EntityCow.class, true);
        try { remapped.blast(); remapped.original(); }
        finally { Entity.registerEntity("Bee", EntityBee.class, true); }
    }

    @Test void closedOrRetaggedOriginalTileCannotSpendSource() throws Exception {
        for (boolean close : List.of(false, true)) {
            Source s = new Source(Block.BEEHIVE);
            Listener listener = onSpawn(bee -> {
                if (close) s.hive.close();
                else s.hive.namedTag.putString("replacement", "source changed");
            });
            try { s.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
            assertThat(s.world.getBlock(0,64,0).getId()).isEqualTo(Block.BEEHIVE);
            assertThat(s.live()).isZero(); assertThat(s.deferred()).isZero();
            assertThat(s.hive.getOccupantsCount()).isEqualTo(2);
            if (!close) assertThat(s.hive.namedTag.getString("replacement")).isEqualTo("source changed");
        }
    }

    @Test void movedOriginalTileCannotDebitCapturedOccupants() throws Exception {
        Source s = new Source(Block.BEEHIVE);
        Listener listener = onSpawn(bee -> s.hive.x=1);
        try { s.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
        assertThat(s.world.getBlock(0,64,0).getId()).isEqualTo(Block.BEEHIVE);
        assertThat(s.live()).isZero(); assertThat(s.deferred()).isZero();
        assertThat(s.hive.getOccupantsCount()).isEqualTo(2);
        assertThat(s.hive.x).isEqualTo(1);
    }

    @Test void deniedSourcePolicyDoesNotPrepareAnyActor() throws Exception {
        Source s = new Source(Block.BEEHIVE); int[] spawns={0};
        Listener listener = onSpawn(bee -> spawns[0]++);
        try {
            assertThat(Explosion.removeSource(s.world.getBlock(0,64,0), Item.get(Item.DIAMOND_PICKAXE), true, List.of(),
                    new NativeExplosionBlockProcessor() {
                        public Result process(Block block) { return Result.NATIVE; }
                        public boolean canRemove(List<Block> blocks) { return false; }
                    })).isFalse();
        } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
        assertThat(spawns[0]).isZero(); s.original();
    }

    static int experience(Source source) {
        return source.world.actors.values().stream().filter(e -> e instanceof cn.nukkit.entity.item.EntityXPOrb
                && !e.closed && !e.isPublicationDeferred()).mapToInt(e -> ((cn.nukkit.entity.item.EntityXPOrb)e).getExp()).sum();
    }
    static cn.nukkit.Player attacker(Source source) {
        cn.nukkit.Player player=mock(cn.nukkit.Player.class);
        cn.nukkit.inventory.PlayerInventory inventory=mock(cn.nukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory); when(inventory.getItemInHand()).thenReturn(Item.get(Item.AIR));
        when(player.getLevel()).thenReturn(source.world);
        when(player.getBoundingBox()).thenReturn(new cn.nukkit.math.SimpleAxisAlignedBB(.2,64,.2,.8,65.8,.8));
        return player;
    }
    static void killByPlayer(EntityBee bee, cn.nukkit.Player player, boolean attack) {
        var damage=new cn.nukkit.event.entity.EntityDamageByEntityEvent(player,bee,
                cn.nukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,100);
        if (attack) bee.attack(damage);
        else { bee.setLastDamageCause(damage); bee.kill(); }
    }

    @Test void unfundedBeeDeathCannotEmitXpOrDeathAndPublishedDeathStillDoes() throws Exception {
        for (boolean attack : List.of(false,true)) {
            int[] deaths={0};
            Listener death=new Listener(){};
            NativeExplosionFixture.server.getPluginManager().registerEvent(cn.nukkit.event.entity.EntityDeathEvent.class,death,
                    EventPriority.NORMAL,(l,e) -> { if (((cn.nukkit.event.entity.EntityDeathEvent)e).getEntity() instanceof EntityBee) deaths[0]++; },plugin());
            try {
                Source control=new Source(Block.BEEHIVE);
                EntityBee published=new EntityBee(control.world.chunk,Entity.getDefaultNBT(new Position(.5,64,.5,control.world)));
                killByPlayer(published,attacker(control),attack);
                assertThat(deaths[0]).isEqualTo(1); assertThat(experience(control)).isBetween(1,3);
                assertThat(control.world.actors.values().stream().filter(e -> e instanceof cn.nukkit.entity.item.EntityXPOrb)
                        .allMatch(Entity::canBeSavedWithChunk)).isTrue();
                deaths[0]=0; Source prepared=new Source(Block.BEEHIVE);
                cn.nukkit.Player player=attacker(prepared);
                Listener spawn=onSpawn(bee -> killByPlayer(bee,player,attack));
                try { prepared.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(spawn); }
                prepared.original(); assertThat(deaths[0]).isZero(); assertThat(experience(prepared)).isZero();
                assertThat(saveableResources(prepared)).isEqualTo(2);
            } finally { cn.nukkit.event.entity.EntityDeathEvent.getHandlers().unregister(death); }
        }
    }

    @Test void ordinaryBeeConstructorStillPublishesAndPreparedTicksDoNothing() throws Exception {
        Source s = new Source(Block.BEEHIVE);
        EntityBee ordinary = new EntityBee(s.world.chunk, Entity.getDefaultNBT(new Position(.5,64,.5,s.world)));
        assertThat(ordinary.isPublicationDeferred()).isFalse(); assertThat(ordinary.isAlive()).isTrue(); ordinary.close();
        Listener listener = onSpawn(bee -> { assertThat(bee.onUpdate(1000)).isTrue(); assertThat(bee.isPublicationDeferred()).isTrue(); });
        try { s.blast(); } finally { EntitySpawnEvent.getHandlers().unregister(listener); }
        s.accepted();
    }
}
