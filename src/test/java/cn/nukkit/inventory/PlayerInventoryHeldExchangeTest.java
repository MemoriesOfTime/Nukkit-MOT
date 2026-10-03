package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Cancellable preflight must complete before either inventory slot is committed. */
class PlayerInventoryHeldExchangeTest {
    private Player player;
    private PlayerInventory inventory;
    private Level level;
    private cn.nukkit.plugin.PluginManager plugins;

    @BeforeEach
    void setUp() {
        MockServer.init();
        MockServer.reset();
        plugins = MockServer.get().getPluginManager();
        level = mock(Level.class);
        player = mock(Player.class);
        player.level = level;
        when(player.getLevel()).thenReturn(level);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
        when(player.getGamemode()).thenReturn(Player.SURVIVAL);
        when(player.getViewers()).thenReturn(Map.of());
        when(player.isAlive()).thenReturn(true);
        when(player.isConnected()).thenReturn(true);
        inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(inventory);
    }

    @Test
    void singleBowlConvertsInPlaceEvenWithNoOtherSpace() {
        fill();
        hand(1);
        assertTrue(exchange(true));
        assertItem(0, Item.SUSPICIOUS_STEW, 9, 1);
        assertEquals(1, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void stackConsumesOneAndIssuesOneInSeparateFreeSlot() {
        hand(2);
        assertTrue(exchange(true));
        assertItem(0, Item.BOWL, 0, 1);
        assertEquals(1, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void fullInventoryRejectsStackConversionWithoutDroppingOrConsuming() {
        fill();
        hand(2);
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
        assertArmorEmpty();
        verify(level, never()).dropItem(any(), any());
    }

    @Test
    void creativeStyleExchangeKeepsBowlAndRequiresSeparateCapacity() {
        when(player.getGamemode()).thenReturn(Player.CREATIVE);
        when(player.isCreative()).thenReturn(true);
        hand(1);
        assertTrue(exchange(false));
        assertItem(0, Item.BOWL, 0, 1);
        assertEquals(1, count(Item.SUSPICIOUS_STEW));
        fill();
        hand(1);
        assertFalse(exchange(false));
        assertItem(0, Item.BOWL, 0, 1);
        assertArmorEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void flowerConsumptionNeedsNoOutputSlot(int count) {
        fill();
        inventory.setItemInHand(Item.get(Item.DANDELION, 0, count));
        assertTrue(inventory.exchangeItemInHand(inventory.getItemInHand(), null, true, () -> true));
        assertEquals(count - 1, inventory.getItemInHand().getCount());
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void staleCountOrNamedTagSnapshotCannotConsumeCurrentHand() {
        hand(2);
        Item stale = inventory.getItemInHand();
        inventory.setItemInHand(Item.get(Item.BOWL, 0, 3));
        assertFalse(inventory.exchangeItemInHand(stale, stew(), true, () -> true));
        assertItem(0, Item.BOWL, 0, 3);
        stale = inventory.getItemInHand();
        inventory.setItemInHand(inventory.getItemInHand().setNamedTag(new CompoundTag().putString("owner", "changed")));
        assertFalse(inventory.exchangeItemInHand(stale, stew(), true, () -> true));
        assertEquals("changed", inventory.getItemInHand().getNamedTag().getString("owner"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void cancellationOfEitherPreflightRejectsWholeConversion(int cancelledSlot) {
        hand(2);
        listen(event -> {
            if (event.getSlot() == cancelledSlot) event.setCancelled();
        });
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "true,true"})
    void outputTypeOrCountChangeIsRejected(boolean changeType, boolean changeCount) {
        hand(2);
        listen(event -> {
            if (event.getNewItem().getId() == Item.SUSPICIOUS_STEW) {
                if (changeType) event.setNewItem(Item.get(Item.DIAMOND, 0, changeCount ? 2 : 1));
                else {
                    Item changed = event.getNewItem().clone();
                    changed.setCount(2);
                    event.setNewItem(changed);
                }
            }
        });
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
        assertEquals(0, count(Item.DIAMOND));
    }

    @Test
    void listenerCannotChangeInputRemainderCount() {
        hand(2);
        listen(event -> {
            if (event.getSlot() == 0) {
                Item changed = event.getNewItem().clone();
                changed.setCount(5);
                event.setNewItem(changed);
            }
        });
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void listenerMayCustomizeOutputNbtWithoutChangingItemOrAmount() {
        hand(2);
        listen(event -> {
            if (event.getNewItem().getId() == Item.SUSPICIOUS_STEW) {
                event.setNewItem(event.getNewItem().clone()
                        .setNamedTag(new CompoundTag().putString("origin", "plugin")));
            }
        });
        assertTrue(exchange(true));
        assertItem(0, Item.BOWL, 0, 1);
        assertEquals("plugin", inventory.getItem(1).getNamedTag().getString("origin"));
    }

    @Test
    void switchingHeldSlotDuringEventAbortsWithoutMutatingOldHand() {
        hand(2);
        listen(event -> inventory.setHeldItemIndex(1, false));
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void switchingGameModeDuringEventAborts() {
        hand(2);
        listen(event -> when(player.getGamemode()).thenReturn(Player.CREATIVE));
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
    }

    @Test
    void switchingLevelDuringEventAborts() {
        hand(2);
        Level destination = mock(Level.class);
        listen(event -> {
            player.level = destination;
            when(player.getLevel()).thenReturn(destination);
        });
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
    }

    @Test
    void invalidEntityPredicateAfterEventAborts() {
        hand(2);
        AtomicBoolean valid = new AtomicBoolean(true);
        listen(event -> valid.set(false));
        assertFalse(inventory.exchangeItemInHand(inventory.getItemInHand(), stew(), true, valid::get));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void reentrantConversionRejectedAndOuterConversionCommitsExactlyOnce() {
        hand(3);
        AtomicBoolean attempted = new AtomicBoolean();
        listen(event -> {
            if (attempted.compareAndSet(false, true)) assertFalse(exchange(true));
        });
        assertTrue(exchange(true));
        assertTrue(attempted.get());
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(1, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void targetFilledDuringPreflightKeepsListenerChangeAndRejectsExchange() {
        hand(2);
        AtomicBoolean mutated = new AtomicBoolean();
        listen(event -> {
            if (event.getSlot() == 1 && mutated.compareAndSet(false, true)) {
                inventory.setItem(1, Item.get(Item.DIAMOND, 0, 1), false);
            }
        });
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertItem(1, Item.DIAMOND, 0, 1);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void initialFalsePredicateDispatchesNoInventoryEvents() {
        hand(2);
        clearInvocations(MockServer.get().getPluginManager());
        assertFalse(inventory.exchangeItemInHand(inventory.getItemInHand(), stew(), true, () -> false));
        verify(MockServer.get().getPluginManager(), never()).callEvent(any());
        assertItem(0, Item.BOWL, 0, 2);
    }

    @Test
    void protocolChangeDuringEventAborts() {
        hand(2);
        listen(event -> when(player.getGameVersion()).thenReturn(GameVersion.V1_20_0));
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void handChangedDuringEventKeepsPluginMutationAndRejectsExchange() {
        hand(2);
        AtomicBoolean changed = new AtomicBoolean();
        listen(event -> {
            if (changed.compareAndSet(false, true)) {
                inventory.setItemInHand(Item.get(Item.DIAMOND, 0, 3));
            }
        });
        assertFalse(exchange(true));
        assertItem(0, Item.DIAMOND, 0, 3);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void disconnectDuringPreflightCannotCommitIntoDetachedInventory() {
        hand(2);
        listen(event -> {
            player.closed = true;
            when(player.isConnected()).thenReturn(false);
        });
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    @Test
    void inventoryReplacementDuringPreflightCannotCommitIntoOldInventory() {
        hand(2);
        PlayerInventory replacement = new PlayerInventory(player);
        listen(event -> when(player.getInventory()).thenReturn(replacement));
        assertFalse(exchange(true));
        assertItem(0, Item.BOWL, 0, 2);
        assertTrue(replacement.getContents().isEmpty());
        assertEquals(0, count(Item.SUSPICIOUS_STEW));
    }

    private boolean exchange(boolean consume) {
        return inventory.exchangeItemInHand(inventory.getItemInHand(), stew(), consume, () -> true);
    }

    private static Item stew() {
        return Item.get(Item.SUSPICIOUS_STEW, 9, 1);
    }

    private void hand(int count) {
        assertTrue(inventory.setItemInHand(Item.get(Item.BOWL, 0, count)));
    }

    private void fill() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            assertTrue(inventory.setItem(slot, Item.get(Item.STONE, 0, 64), false));
        }
    }

    private int count(int id) {
        return inventory.getContents().values().stream().filter(item -> item.getId() == id)
                .mapToInt(Item::getCount).sum();
    }

    private void assertArmorEmpty() {
        for (Item armor : inventory.getArmorContents()) assertTrue(armor.isNull());
    }

    private void assertItem(int slot, int id, int meta, int count) {
        Item item = inventory.getItem(slot);
        assertEquals(id, item.getId());
        assertEquals(meta, item.getDamage());
        assertEquals(count, item.getCount());
    }

    private void listen(Consumer<EntityInventoryChangeEvent> consumer) {
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityInventoryChangeEvent change) consumer.accept(change);
            return null;
        }).when(plugins).callEvent(any());
    }
}
