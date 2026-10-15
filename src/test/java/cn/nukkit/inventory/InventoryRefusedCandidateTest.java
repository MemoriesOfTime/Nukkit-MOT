package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryRefusedCandidateTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }

    @Test void refusedFirstItemDoesNotStarveAnotherPermittedItem() throws Exception {
        InventoryExactBulkWriteTest.Fixture fixture = fixture(1);
        AtomicInteger events = rejectEmeralds(fixture);
        Item emeralds = Item.get(Item.EMERALD, 0, 4);
        Item diamonds = Item.get(Item.DIAMOND, 0, 3);

        Item[] remaining = fixture.inventory.addItem(emeralds, diamonds);

        assertEquals(1, remaining.length);
        assertTrue(emeralds.equalsExact(remaining[0]));
        assertEquals(Item.DIAMOND, fixture.inventory.getItem(0).getId());
        assertEquals(3, fixture.inventory.getItem(0).getCount());
        assertEquals(2, events.get(), "each attempted candidate receives one event");
        assertEquals(4, emeralds.getCount());
        assertEquals(3, diamonds.getCount());
        assertEquals(0, fixture.dropped.get());
    }

    @Test void refusedFirstItemKeepsCustodyWhenTheSecondItemPartiallyFits() throws Exception {
        InventoryExactBulkWriteTest.Fixture fixture = fixture(2);
        AtomicInteger events = rejectEmeralds(fixture);

        Item[] remaining = fixture.inventory.addItem(Item.get(Item.EMERALD, 0, 4), Item.get(Item.DIAMOND, 0, 100));

        assertEquals(1, remaining.length);
        assertEquals(Item.EMERALD, remaining[0].getId());
        assertEquals(4, remaining[0].getCount());
        assertEquals(100, fixture.inventory.getContents().values().stream()
                .filter(item -> item.getId() == Item.DIAMOND).mapToInt(Item::getCount).sum());
        assertEquals(4, events.get(), "a refused candidate is retried once for each available slot");
    }

    @Test void acceptedFirstCandidateStillUsesOneWriteAndReportsOrdinaryRemainder() throws Exception {
        InventoryExactBulkWriteTest.Fixture fixture = fixture(1);
        AtomicInteger events = new AtomicInteger();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { events.incrementAndGet(); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));

        Item[] remaining = fixture.inventory.addItem(Item.get(Item.EMERALD, 0, 4), Item.get(Item.DIAMOND, 0, 3));

        assertEquals(1, events.get());
        assertEquals(1, remaining.length);
        assertEquals(Item.DIAMOND, remaining[0].getId());
        assertEquals(3, remaining[0].getCount());
        assertEquals(4, fixture.inventory.getItem(0).getCount());
    }

    @Test void refusedCandidateDoesNotConsumeCapacityReservedByAnAcceptedPartialStack() throws Exception {
        InventoryExactBulkWriteTest.Fixture fixture = fixture(1);
        rejectEmeralds(fixture);

        Item[] remaining = fixture.inventory.addItem(Item.get(Item.EMERALD, 0, 4), Item.get(Item.DIAMOND, 0, 100));

        assertEquals(2, remaining.length);
        assertEquals(4, remaining[0].getCount());
        assertEquals(Item.EMERALD, remaining[0].getId());
        assertEquals(36, remaining[1].getCount());
        assertEquals(Item.DIAMOND, remaining[1].getId());
        assertEquals(64, fixture.inventory.getItem(0).getCount());
        assertEquals(104, Arrays.stream(remaining).mapToInt(Item::getCount).sum() + 64);
    }

    @Test void refusedCandidateCallbackCannotNestAGrantBeforeTheNextCandidate() throws Exception {
        InventoryExactBulkWriteTest.Fixture fixture = fixture(1);
        AtomicBoolean attempted = new AtomicBoolean();
        AtomicInteger nestedRemaining = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            events.incrementAndGet();
            if (event.getNewItem().getId() == Item.EMERALD) {
                if (attempted.compareAndSet(false, true)) nestedRemaining.set(Arrays.stream(
                        fixture.inventory.addItem(Item.get(Item.DIAMOND, 0, 64))).mapToInt(Item::getCount).sum());
                event.setCancelled();
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));

        Item[] remaining = fixture.inventory.addItem(Item.get(Item.EMERALD, 0, 4), Item.get(Item.DIAMOND, 0, 3));

        assertEquals(64, nestedRemaining.get());
        assertEquals(2, events.get());
        assertEquals(1, remaining.length);
        assertEquals(Item.EMERALD, remaining[0].getId());
        assertEquals(4, remaining[0].getCount());
        assertEquals(Item.DIAMOND, fixture.inventory.getItem(0).getId());
        assertEquals(3, fixture.inventory.getItem(0).getCount());
    }

    private static InventoryExactBulkWriteTest.Fixture fixture(int size) throws Exception {
        InventoryExactBulkWriteTest.Fixture fixture = new InventoryExactBulkWriteTest.Fixture(false);
        fixture.inventory.setSize(size);
        return fixture;
    }

    private static AtomicInteger rejectEmeralds(InventoryExactBulkWriteTest.Fixture fixture) {
        AtomicInteger events = new AtomicInteger();
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getEntity() == fixture.player) {
                events.incrementAndGet();
                if (event.getNewItem().getId() == Item.EMERALD) event.setCancelled();
            }
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        return events;
    }
}
