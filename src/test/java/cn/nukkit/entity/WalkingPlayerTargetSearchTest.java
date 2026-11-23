package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.entity.passive.*;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkingPlayerTargetSearchTest {
    private static final List<LevelProvider> openedProviders = new ArrayList<>();
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void closeProviders() {
        for (LevelProvider provider : openedProviders) provider.close();
        openedProviders.clear();
    }

    @Test
    void nativeFarmKeepsLegacyLastAcceptedPlayerAndBothTargetFields(@TempDir Path directory) throws Exception {
        Fixture f = Fixture.real(directory);
        EntityCow cow = f.cow(0, 0);
        for (int i = 0; i < 150; i++) f.cow(1, 1);
        Player first = f.player(11, -2, 0, Item.WHEAT);
        Player second = f.player(1039, 2, 0, Item.WHEAT);
        f.player(61, 1, 1, Item.STONE);
        f.player(4051, 0, 0, Item.WHEAT).closed = true;
        f.player(91, 7.1, 0, Item.WHEAT);
        Player expected = legacyLastPlayer(cow, f.level.getNearbyEntities(EntityRanges.createTargetSearchBox(cow), cow, false));
        assertTrue(expected == first || expected == second);
        clearInvocations(f.level);
        cow.checkTarget();
        assertSame(expected, ((BaseEntity) cow).followTarget);
        assertSame(expected, ((BaseEntity) cow).target);
        assertEquals(0, cow.stayTime);
        assertEquals(0, ((BaseEntity) cow).moveTime);
        verify(f.level).getNearbyPlayerTargets(any(), same(cow));
        verify(f.level, never()).getNearbyEntities(any(), any(), anyBoolean(), anyBoolean());
        verify(f.level, never()).getChunkPlayers(anyInt(), anyInt());
    }

    @Test
    void negativeChunkEdgesVerticalBoundsAndPlayerLifecycleMatchFreshLegacyQuery(@TempDir Path directory) throws Exception {
        Fixture f = Fixture.real(directory);
        EntityCow cow = f.cow(-16.1, -16.1);
        Player neighbor = f.player(91, -15.5, -16.1, Item.WHEAT);
        Player high = f.player(1039, -16.1, -16.1, Item.WHEAT);
        high.boundingBox.offset(0, 30, 0);
        Player boundary = f.player(31, -23.55, -16.1, Item.WHEAT);
        assertLegacySelection(f, cow);
        assertSame(neighbor, ((BaseEntity) cow).followTarget);
        f.remove(neighbor);
        reset(cow);
        assertLegacySelection(f, cow);
        assertNull(((BaseEntity) cow).followTarget);
        f.move(neighbor, -16.3, -16.3);
        reset(cow);
        assertLegacySelection(f, cow);
        assertSame(neighbor, ((BaseEntity) cow).followTarget);
        neighbor.closed = true;
        reset(cow);
        assertLegacySelection(f, cow);
        assertNull(((BaseEntity) cow).followTarget);
        assertNotNull(boundary);
    }

    @Test
    void playerCacheMatchesWarmLegacyMembershipAndDirtyRefresh(@TempDir Path directory) throws Exception {
        Fixture f = Fixture.real(directory);
        EntityCow cow = f.cow(0, 0);
        EntityCow reference = f.cow(0, 0);
        Player entered = f.player(9001, 1, 0, Item.WHEAT);
        f.remove(entered);
        AxisAlignedBB box = EntityRanges.createTargetSearchBox(reference);
        f.level.getNearbyEntities(box, reference, false, true);
        cow.checkTarget();
        assertNull(((BaseEntity) cow).followTarget);
        f.move(entered, 1, 0);
        assertSameWarmSelection(f, cow, reference, box, null);

        refreshBoth(f, cow, reference, box);
        assertSameWarmSelection(f, cow, reference, box, entered);
        f.remove(entered);
        assertSameWarmSelection(f, cow, reference, box, entered);

        f.move(entered, 30, 0);
        assertSameWarmSelection(f, cow, reference, box, null);
        f.move(entered, 1, 0);
        entered.closed = true;
        assertSameWarmSelection(f, cow, reference, box, null);
    }

    @Test
    void enteringLoveRefreshesFullCandidatesAndRetainsThemOnExit(@TempDir Path directory) throws Exception {
        Fixture f = Fixture.real(directory);
        EntityCow cow = f.cow(0, 0);
        EntityCow mate = f.cow(1, 0);
        Player player = f.player(9001, 2, 0, Item.WHEAT);
        cow.checkTarget();
        assertSame(player, ((BaseEntity) cow).followTarget);
        reset(cow);
        cow.setInLove();
        mate.setInLove();
        cow.checkTarget();
        assertSame(mate, ((BaseEntity) cow).followTarget);
        assertSame(mate, ((BaseEntity) cow).target);

        reset(cow);
        cow.setInLove(false);
        f.remove(player);
        cow.checkTarget();
        assertSame(player, ((BaseEntity) cow).followTarget, "the warm complete cache is retained");
    }

    @Test
    void loveAndUnknownSubclassRetainCompleteQueryAndMateSelection() {
        Fixture f = new Fixture();
        EntityCow cow = f.cow(0, 0);
        EntityCow mate = f.cow(1, 0);
        cow.setInLove();
        mate.setInLove();
        when(f.level.getNearbyEntities(any(), same(cow), eq(false), eq(true))).thenReturn(new Entity[]{mate});
        cow.checkTarget();
        assertSame(mate, ((BaseEntity) cow).followTarget);
        verify(f.level).getNearbyEntities(any(), same(cow), eq(false), eq(true));
        assertFalse(EntityRanges.targetsOnlyPlayersWhenNotInLove(cow));

        CustomCow custom = new CustomCow(f.chunk(0, 0), f.nbt(0, 0));
        custom.stayTime = 999;
        when(f.level.getNearbyEntities(any(), same(custom), eq(false), eq(true))).thenReturn(new Entity[]{mate});
        custom.checkTarget();
        assertSame(mate, custom.followTarget);
        assertSame(mate, custom.target);
        assertFalse(EntityRanges.targetsOnlyPlayersWhenNotInLove(custom));
    }

    @Test
    void nativeAllowlistExcludesMobHuntersAndEveryUnknownSubclass() {
        for (Class<? extends EntityWalking> type : List.of(EntityCow.class, EntitySheep.class, EntityPig.class,
                EntityChicken.class, EntityFox.class, EntityCat.class, EntityOcelot.class, EntityGoat.class,
                EntityHorse.class, EntityDonkey.class, EntityLlama.class, EntityMule.class)) {
            EntityWalking entity = mock(type);
            assertTrue(EntityRanges.targetsOnlyPlayersWhenNotInLove(entity), type.getName());
            when(entity.isInLove()).thenReturn(true);
            assertFalse(EntityRanges.targetsOnlyPlayersWhenNotInLove(entity), type.getName());
        }
        for (Class<? extends EntityWalking> type : List.of(EntityWolf.class, EntityIronGolem.class,
                EntityPolarBear.class, CustomCow.class, PassiveCustomCow.class)) {
            assertFalse(EntityRanges.targetsOnlyPlayersWhenNotInLove(mock(type)), type.getName());
        }
        assertFalse(EntityWalking.class.isAssignableFrom(EntityAxolotl.class));
    }

    @Test
    void customLevelNearbyOverrideAndCustomEntityMapsKeepLegacyQuery(@TempDir Path directory) throws Exception {
        Fixture f = new Fixture(mock(CustomLevel.class));
        EntityCow cow = f.cow(0, 0);
        Player player = f.player(7, 1, 0, Item.WHEAT);
        when(f.level.getNearbyEntities(any(), same(cow), eq(false), eq(true))).thenReturn(new Entity[]{player});
        cow.checkTarget();
        assertSame(player, ((BaseEntity) cow).followTarget);
        verify(f.level).getNearbyEntities(any(), same(cow), eq(false), eq(true));

        Fixture nativeLevel = Fixture.real(directory);
        EntityCow secondCow = nativeLevel.cow(0, 0);
        Player secondPlayer = nativeLevel.player(7, 1, 0, Item.WHEAT);
        doReturn(Map.of(7L, secondPlayer)).when(nativeLevel.level).getChunkEntities(0, 0, false);
        secondCow.checkTarget();
        assertSame(secondPlayer, ((BaseEntity) secondCow).followTarget);
        verify(nativeLevel.level).getNearbyEntities(any(), same(secondCow), eq(false), eq(true));
    }

    @Test
    void reusedSearchBoxUpdatesBoundsWithoutChangingCollisionBox() {
        EntityCow cow = mock(EntityCow.class);
        cow.boundingBox = new SimpleAxisAlignedBB(-1, 64, -2, 1, 65, 2);
        SimpleAxisAlignedBB box = new SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);
        assertSame(box, EntityRanges.updateTargetSearchBox(cow, box));
        assertEquals(-11, box.getMinX());
        assertEquals(12, box.getMaxZ());
        cow.boundingBox.offset(32, 1, -32);
        assertSame(box, EntityRanges.updateTargetSearchBox(cow, box));
        assertEquals(21, box.getMinX());
        assertEquals(-20, box.getMaxZ());
        assertEquals(31, cow.boundingBox.getMinX());
        assertEquals(65, cow.boundingBox.getMinY());
    }

    private static void assertLegacySelection(Fixture f, EntityCow cow) {
        f.level.setDirtyNearby(cow);
        Player expected = legacyLastPlayer(cow, f.level.getNearbyEntities(EntityRanges.createTargetSearchBox(cow), cow, false));
        cow.checkTarget();
        assertSame(expected, ((BaseEntity) cow).followTarget);
        if (expected != null) assertSame(expected, ((BaseEntity) cow).target);
    }

    private static void refreshBoth(Fixture f, EntityCow cow, EntityCow reference, AxisAlignedBB box) {
        f.level.setDirtyNearby(reference);
        f.level.getNearbyEntities(box, reference, false, true);
        f.level.setDirtyNearby(cow);
        reset(cow);
        cow.checkTarget();
    }

    private static void assertSameWarmSelection(Fixture f, EntityCow cow, EntityCow reference, AxisAlignedBB box, Player expected) {
        reset(cow);
        assertSame(expected, legacyLastPlayer(reference, f.level.getNearbyEntities(box, reference, false, true)));
        cow.checkTarget();
        assertSame(expected, ((BaseEntity) cow).followTarget);
        assertSame(expected, ((BaseEntity) cow).target);
    }

    private static Player legacyLastPlayer(EntityCow cow, Entity[] candidates) {
        Player last = null;
        for (Entity entity : candidates) {
            if (entity == cow || !(entity instanceof EntityCreature creature) || entity.closed || !cow.canTarget(entity)) continue;
            if (creature instanceof BaseEntity base && base.isFriendly() == cow.isFriendly() && !cow.isInLove()) continue;
            if (cow.targetOption(creature, cow.distanceSquared(creature))) last = (Player) creature;
        }
        return last;
    }

    private static void reset(EntityCow cow) {
        ((BaseEntity) cow).followTarget = null;
        ((BaseEntity) cow).target = null;
        cow.stayTime = 999;
    }

    static class CustomLevel extends Level {
        CustomLevel() { super(null, "custom", "custom", null); }
        @Override public Entity[] getNearbyEntities(AxisAlignedBB box, Entity entity, boolean load, boolean ai) {
            return super.getNearbyEntities(box, entity, load);
        }
    }

    static class CustomCow extends EntityCow {
        CustomCow(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }
        @Override public boolean canTarget(Entity entity) { return entity.canBeFollowed(); }
        @Override public boolean targetOption(EntityCreature entity, double distance) { return entity.isAlive(); }
        @Override public boolean isFriendly() { return false; }
    }

    static class PassiveCustomCow extends EntityCow {
        PassiveCustomCow(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }
    }

    static final class Fixture {
        final Level level;
        final Map<Long, BaseFullChunk> chunks = new HashMap<>();
        Fixture() { this(mock(Level.class)); }
        static Fixture real(Path directory) throws java.io.IOException {
            when(MockServer.get().getLanguage()).thenReturn(new BaseLang("eng"));
            String path = directory.toString() + "/";
            Anvil.generate(path, "target-index-test", 0, Flat.class);
            Level real = spy(new Level(MockServer.get(), "target-index-test", path, Anvil.class));
            openedProviders.add(real.getProvider());
            return new Fixture(real);
        }
        Fixture(Level level) {
            this.level = level;
            level.isBeingConverted = true;
            doReturn(MockServer.get()).when(level).getServer();
            doReturn(-64).when(level).getMinBlockY();
            doReturn(319).when(level).getMaxBlockY();
            doReturn(GameRules.getDefault()).when(level).getGameRules();
            doReturn(DimensionEnum.OVERWORLD.getDimensionData()).when(level).getDimensionData();
            doReturn(Map.of()).when(level).getChunkPlayers(anyInt(), anyInt());
            doAnswer(i -> chunks.get(Level.chunkHash(i.getArgument(0), i.getArgument(1)))).when(level).getChunkIfLoaded(anyInt(), anyInt());
            doCallRealMethod().when(level).getChunkEntities(anyInt(), anyInt(), eq(false));
            doCallRealMethod().when(level).getNearbyEntities(any(), any(), eq(false));
        }
        BaseFullChunk chunk(int x, int z) {
            return chunks.computeIfAbsent(Level.chunkHash(x, z), key -> {
                BaseFullChunk chunk = Chunk.getEmptyChunk(x, z);
                LevelProvider provider = mock(LevelProvider.class);
                when(provider.getLevel()).thenReturn(level);
                chunk.setProvider(provider);
                return chunk;
            });
        }
        EntityCow cow(double x, double z) {
            EntityCow cow = new EntityCow(chunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4), nbt(x, z));
            cow.stayTime = 999;
            return cow;
        }
        CompoundTag nbt(double x, double z) {
            return new CompoundTag().putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", x)).add(new DoubleTag("", 64)).add(new DoubleTag("", z)))
                    .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", 0)).add(new DoubleTag("", 0)).add(new DoubleTag("", 0)))
                    .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", 0)).add(new FloatTag("", 0)));
        }
        Player player(long id, double x, double z, int item) {
            Player player = mock(Player.class);
            when(player.getId()).thenReturn(id);
            when(player.isAlive()).thenReturn(true);
            when(player.canBeFollowed()).thenReturn(true);
            player.level = level;
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItemInHandFast()).thenReturn(Item.get(item));
            move(player, x, z);
            return player;
        }
        void remove(Player player) {
            for (BaseFullChunk chunk : chunks.values()) chunk.getEntities().remove(player.getId());
        }
        void move(Player player, double x, double z) {
            remove(player);
            player.x = x; player.y = 64; player.z = z;
            player.boundingBox = new SimpleAxisAlignedBB(x - 0.3, 64, z - 0.3, x + 0.3, 65.8, z + 0.3);
            chunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4).addEntity(player);
        }
    }
}
