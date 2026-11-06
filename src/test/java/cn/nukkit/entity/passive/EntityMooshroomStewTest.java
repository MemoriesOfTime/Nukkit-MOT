package cn.nukkit.entity.passive;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises real entity construction, interaction, inventory transactions, and NBT reload. */
class EntityMooshroomStewTest {
    private Level level;
    private cn.nukkit.plugin.PluginManager plugins;
    private FullChunk chunk;
    private Player player;
    private PlayerInventory inventory;
    private EntityMooshroom cow;

    @BeforeEach
    void setUp() {
        MockServer.init();
        MockServer.reset();
        plugins = MockServer.get().getPluginManager();
        level = mock(Level.class);
        when(level.getServer()).thenReturn(MockServer.get());
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(0, 0)).thenReturn(Map.of());
        level.isBeingConverted = true;
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        chunk = mock(FullChunk.class);
        when(chunk.getProvider()).thenReturn(provider);
        cow = new EntityMooshroom(chunk, nbt(1, null));
        player = mock(Player.class);
        player.level = level;
        player.protocol = GameVersion.V1_26_50.getProtocol();
        when(player.getLevel()).thenReturn(level);
        when(player.getServer()).thenReturn(MockServer.get());
        when(player.getViewers()).thenReturn(Map.of());
        when(player.getGamemode()).thenReturn(Player.SURVIVAL);
        when(player.isSurvival()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);
        when(player.isConnected()).thenReturn(true);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
        inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(inventory);
    }

    @ParameterizedTest
    @CsvSource({
            "poppy,0", "blue_orchid,6", "allium,7", "azure_bluet,3", "red_tulip,2",
            "orange_tulip,2", "white_tulip,2", "pink_tulip,2", "oxeye_daisy,8",
            "cornflower,1", "lily_of_the_valley,4", "dandelion,5", "wither_rose,9",
            "torchflower,10", "open_eyeblossom,11", "closed_eyeblossom,12"
    })
    void eachBedrockFlowerConsumesOnceAndProducesItsOneShotStew(String flower, int meta) {
        Item offered = Item.fromString("minecraft:" + flower);
        assertFalse(offered.isNull(), flower + " must resolve in the test registry");
        offered.setCount(2);
        hand(offered);

        assertFalse(interact(), "manual exchange must suppress Player's second decrement");
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(meta, mark(cow));

        hand(Item.get(Item.BOWL, 0, 2));
        assertFalse(interact());
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(1, count(Item.SUSPICIOUS_STEW, meta));
        assertEquals(-1, mark(cow));

        hand(Item.get(Item.BOWL, 0, 1));
        assertFalse(interact());
        assertEquals(Item.MUSHROOM_STEW, inventory.getItemInHand().getId());
        assertEquals(1, count(Item.SUSPICIOUS_STEW, meta), "pending effect lasts exactly one milking");
    }

    @Test
    void repeatedSameFlowerIsRejectedButDifferentFlowerReplacesPendingEffect() {
        Item poppy = Item.fromString("minecraft:poppy");
        poppy.setCount(2);
        hand(poppy);
        interact();
        assertEquals(0, mark(cow));
        assertEquals(1, inventory.getItemInHand().getCount());
        interact();
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(0, mark(cow));

        hand(Item.fromString("minecraft:allium"));
        interact();
        assertTrue(inventory.getItemInHand().isNull());
        assertEquals(7, mark(cow));
    }

    @Test
    void redCowRejectsFlowersAndProducesOrdinaryStew() {
        cow.setBrown(false);
        hand(Item.fromString("minecraft:poppy"));
        interact();
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(-1, mark(cow));
        hand(Item.get(Item.BOWL, 0, 1));
        interact();
        assertEquals(Item.MUSHROOM_STEW, inventory.getItemInHand().getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:poppy", "minecraft:bowl", "minecraft:bucket"})
    void babyCannotBeFedFlowersOrMilked(String name) {
        cow.setBaby(true);
        Item item = Item.fromString(name);
        hand(item);
        interact();
        assertTrue(item.equalsExact(inventory.getItemInHand()));
        assertEquals(-1, mark(cow));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void emptyBucketProducesOneMilkWithoutConsumingStoredStewEffect(int count) {
        setMark(4);
        hand(Item.get(Item.BUCKET, 0, count));
        assertFalse(interact());
        assertEquals(1, count(Item.BUCKET, 1));
        assertEquals(count - 1, count(Item.BUCKET, 0));
        assertEquals(4, mark(cow));
    }

    @Test
    void filledBucketIsNotConsumedOrConverted() {
        hand(Item.get(Item.BUCKET, 1, 1));
        interact();
        assertEquals(Item.BUCKET, inventory.getItemInHand().getId());
        assertEquals(1, inventory.getItemInHand().getDamage());
        assertEquals(1, inventory.getItemInHand().getCount());
    }

    @Test
    void fullInventoryStillReplacesSingleBowlButRejectsStackedBowlWithoutLosingState() {
        setMark(8);
        fill();
        hand(Item.get(Item.BOWL, 0, 2));
        interact();
        assertEquals(2, inventory.getItemInHand().getCount());
        assertEquals(8, mark(cow));
        assertEquals(0, count(Item.SUSPICIOUS_STEW, 8));
        verify(level, never()).dropItem(any(), any());

        hand(Item.get(Item.BOWL, 0, 1));
        interact();
        assertEquals(Item.SUSPICIOUS_STEW, inventory.getItemInHand().getId());
        assertEquals(8, inventory.getItemInHand().getDamage());
        assertEquals(-1, mark(cow));
    }

    @Test
    void creativeKeepsBowlAndFlowerButRequiresOutputCapacity() {
        when(player.isCreative()).thenReturn(true);
        when(player.isSurvival()).thenReturn(false);
        when(player.getGamemode()).thenReturn(Player.CREATIVE);
        hand(Item.fromString("minecraft:poppy"));
        interact();
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(0, mark(cow));
        hand(Item.get(Item.BOWL, 0, 1));
        interact();
        assertEquals(Item.BOWL, inventory.getItemInHand().getId());
        assertEquals(1, count(Item.SUSPICIOUS_STEW, 0));
        assertEquals(-1, mark(cow));

        setMark(0);
        fill();
        hand(Item.get(Item.BOWL, 0, 1));
        interact();
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(0, mark(cow));
    }

    @ParameterizedTest
    @ValueSource(ints = {11, 12})
    void oldClientCannotDestroyPendingEyeblossomByMilking(int meta) {
        setMark(meta);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_20_0);
        player.protocol = GameVersion.V1_20_0.getProtocol();
        hand(Item.get(Item.BOWL, 0, 1));
        interact();
        assertEquals(Item.BOWL, inventory.getItemInHand().getId());
        assertEquals(meta, mark(cow));

        when(player.getGameVersion()).thenReturn(GameVersion.V1_21_50);
        player.protocol = GameVersion.V1_21_50.getProtocol();
        interact();
        assertEquals(Item.SUSPICIOUS_STEW, inventory.getItemInHand().getId());
        assertEquals(meta, inventory.getItemInHand().getDamage());
        assertEquals(-1, mark(cow));
    }

    @Test
    void savedPendingEffectSurvivesConstructorAndIsConsumedOnceAfterReload() {
        hand(Item.fromString("minecraft:blue_orchid"));
        interact();
        assertEquals(6, mark(cow));
        cow.saveNBT();
        assertEquals(6, cow.namedTag.getInt("MarkVariant"));
        cow = new EntityMooshroom(chunk, cow.namedTag.clone());
        assertTrue(cow.isBrown());
        assertEquals(6, mark(cow));
        hand(Item.get(Item.BOWL, 0, 1));
        interact();
        assertEquals(6, inventory.getItemInHand().getDamage());
        assertEquals(-1, mark(cow));
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, 13, Integer.MAX_VALUE})
    void invalidSavedMarkIsDiscarded(int meta) {
        assertEquals(-1, mark(new EntityMooshroom(chunk, nbt(1, meta))));
    }

    @Test
    void variantChangeClearsMarkButIdempotentSetBrownPreservesIt() {
        setMark(4);
        cow.setBrown(true);
        assertEquals(4, mark(cow));
        cow.setBrown(false);
        assertEquals(-1, mark(cow));
        cow.setBrown(true);
        assertEquals(-1, mark(cow));
        assertEquals(-1, mark(new EntityMooshroom(chunk, nbt(0, 4))), "red NBT cannot retain a stew mark");
    }

    @Test
    void cancelledFlowerExchangeKeepsInputAndMark() {
        hand(Item.fromString("minecraft:poppy"));
        cancelInventoryEvents();
        interact();
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(-1, mark(cow));
    }

    @Test
    void cancelledOutputPreflightDoesNotConsumeBowlOrPendingEffect() {
        setMark(9);
        hand(Item.get(Item.BOWL, 0, 2));
        doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (event instanceof EntityInventoryChangeEvent change
                    && change.getNewItem().getId() == Item.SUSPICIOUS_STEW) change.setCancelled();
            return null;
        }).when(plugins).callEvent(any());
        interact();
        assertEquals(2, inventory.getItemInHand().getCount());
        assertEquals(0, count(Item.SUSPICIOUS_STEW, 9));
        assertEquals(9, mark(cow));
    }

    @Test
    void reentrantMilkingCannotIssueSecondStew() {
        setMark(9);
        hand(Item.get(Item.BOWL, 0, 2));
        AtomicBoolean reentered = new AtomicBoolean();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent
                    && reentered.compareAndSet(false, true)) {
                assertFalse(cow.onInteract(player, inventory.getItemInHand(), new Vector3()));
            }
            return null;
        }).when(plugins).callEvent(any());
        interact();
        assertTrue(reentered.get());
        assertEquals(1, inventory.getItemInHand().getCount());
        assertEquals(1, count(Item.SUSPICIOUS_STEW, 9));
        assertEquals(-1, mark(cow));
    }

    @Test
    void entityMutationDuringEventAbortsMilking() {
        setMark(9);
        hand(Item.get(Item.BOWL, 0, 2));
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent) cow.setBrown(false);
            return null;
        }).when(plugins).callEvent(any());
        interact();
        assertEquals(2, inventory.getItemInHand().getCount());
        assertEquals(0, count(Item.SUSPICIOUS_STEW, 9));
        assertEquals(-1, mark(cow), "external variant change is retained");
    }

    private boolean interact() {
        return cow.onInteract(player, inventory.getItemInHand(), new Vector3());
    }

    private void hand(Item item) {
        assertTrue(inventory.setItemInHand(item));
    }

    private void fill() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            assertTrue(inventory.setItem(slot, Item.get(Item.STONE, 0, 64), false));
        }
    }

    private int count(int id, int meta) {
        return inventory.getContents().values().stream().filter(item -> item.getId() == id
                && item.getDamage() == meta).mapToInt(Item::getCount).sum();
    }

    private void setMark(int mark) {
        cow.setDataProperty(new IntEntityData(Entity.DATA_MARK_VARIANT, mark));
    }

    private static int mark(EntityMooshroom mooshroom) {
        return mooshroom.getDataPropertyInt(Entity.DATA_MARK_VARIANT);
    }

    private static CompoundTag nbt(int variant, Integer mark) {
        CompoundTag tag = Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5)).putInt("Variant", variant);
        if (mark != null) tag.putInt("MarkVariant", mark);
        return tag;
    }

    private void cancelInventoryEvents() {
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent change) change.setCancelled();
            return null;
        }).when(plugins).callEvent(any());
    }
}
