package cn.nukkit.inventory.transaction.action;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemMace;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CreativeInventoryActionValidationTest {
    private Map<Item, CreativeItemGroup> previousItems;
    private List<CreativeItemGroup> previousGroups;
    @BeforeAll static void initialize() { MockServer.init(); }
    @BeforeEach void addCatalogFixtures() {
        Item.CreativeItems catalog = Item.getCreativeItemsAndGroups();
        previousItems = new LinkedHashMap<>(catalog.getContents());
        previousGroups = new ArrayList<>(catalog.getGroups());
        Item.addCreativeItem(Item.get(Item.EMERALD));
        Item.addCreativeItem(new ItemMace());
    }
    @AfterEach void cleanup() {
        reset(Server.getInstance().getPluginManager());
        Item.CreativeItems catalog = Item.getCreativeItemsAndGroups();
        catalog.clear();
        catalog.getContents().putAll(previousItems);
        catalog.getGroups().addAll(previousGroups);
    }

    @Test void createConstructorRetainsItsActualType() {
        assertEquals(CreativeInventoryAction.TYPE_CREATE_ITEM,
                new CreativeInventoryAction(Item.get(Item.EMERALD), Item.get(Item.AIR),
                        CreativeInventoryAction.TYPE_CREATE_ITEM).getActionType());
    }
    @Test void fakeCatalogNbtCannotBecomeACreativeSource() throws Exception {
        Harness h = new Harness();
        Item forged = forged();
        assertFalse(Item.isCreativeItem(GameVersion.getLastVersion(), forged));
        assertFalse(new CreativeInventoryAction(forged, Item.get(Item.AIR),
                CreativeInventoryAction.TYPE_CREATE_ITEM).isValid(h.player));
    }
    @Test void deleteCannotPretendToBeAnUnvalidatedItemSource() throws Exception {
        Harness h = new Harness();
        assertFalse(new CreativeInventoryAction(forged(), Item.get(Item.AIR),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
    }
    @Test void createCannotPretendToBeAnItemSink() throws Exception {
        Harness h = new Harness();
        assertFalse(new CreativeInventoryAction(Item.get(Item.AIR), Item.get(Item.EMERALD),
                CreativeInventoryAction.TYPE_CREATE_ITEM).isValid(h.player));
    }
    @Test void unknownVirtualTypeCannotContributeItems() throws Exception {
        Harness h = new Harness();
        assertFalse(new CreativeInventoryAction(Item.get(Item.EMERALD), Item.get(Item.AIR), 47).isValid(h.player));
    }
    @Test void survivalRejectsBothVirtualDirections() throws Exception {
        Harness h = new Harness(); h.creative.set(false);
        assertFalse(create(Item.get(Item.EMERALD)).isValid(h.player));
        assertFalse(new CreativeInventoryAction(Item.get(Item.AIR), forged(),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
    }
    @Test void catalogCreateAndIntentionalMarkedDeletionKeepTheirNativeSemantics() throws Exception {
        Harness h = new Harness();
        Item item = Item.get(Item.EMERALD, 0, 4);
        assertTrue(Item.isCreativeItem(GameVersion.getLastVersion(), item));
        assertTrue(create(item).isValid(h.player));
        assertTrue(new CreativeInventoryAction(Item.get(Item.AIR), forged(),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
        assertTrue(new CreativeInventoryAction(Item.get(Item.AIR), Item.get(Item.AIR),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
    }
    @Test void negativeAndOverstackCreativeSourcesCannotPassCatalogValidation() throws Exception {
        Harness h = new Harness();
        assertFalse(create(Item.get(Item.EMERALD, 0, -1)).isValid(h.player));
        assertFalse(create(Item.get(Item.EMERALD, 0, 0)).isValid(h.player));
        assertFalse(create(Item.get(Item.EMERALD, 0, 65)).isValid(h.player));
        assertFalse(new CreativeInventoryAction(Item.get(Item.EMERALD, 0, -1), Item.get(Item.AIR),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
    }
    @Test void transactionEventGamemodeChangeRefusesVirtualCreationBeforeNativeSlotWrite() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(create(target),
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { h.creative.set(false); return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
        tx.execute();
        assertTrue(h.inventory.getItem(0).isNull());
    }
    @Test void transactionEventCannotReplaceTheVirtualCatalogSourceWithForgedNbt() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        CreativeInventoryAction virtual = create(target);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(virtual,
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { virtual.setSourceItem(forged()); return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
        tx.execute();
        assertTrue(h.inventory.getItem(0).isNull());
    }
    @Test void catalogCreationWorksAcrossTheWholeSupportedSessionWindow() throws Exception {
        Harness h = new Harness();
        for (GameVersion version : GameVersion.values()) {
            if (version.isNetEase() || version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                    || version.getProtocol() > GameVersion.V1_26_50.getProtocol()) continue;
            doReturn(version).when(h.player).getGameVersion();
            assertTrue(create(Item.get(Item.EMERALD, 0, 64)).isValid(h.player), version.name());
        }
    }
    @Test void catalogCreationUsesThePlayersSessionVersion() throws Exception {
        Harness h = new Harness();
        Item mace = new ItemMace();
        assertTrue(Item.isCreativeItem(GameVersion.getLastVersion(), mace));
        assertFalse(Item.isCreativeItem(GameVersion.V1_20_0, mace));
        doReturn(GameVersion.V1_20_0).when(h.player).getGameVersion();
        assertFalse(create(mace).isValid(h.player));
        doReturn(GameVersion.V1_21_0).when(h.player).getGameVersion();
        assertTrue(create(mace).isValid(h.player));
    }
    @Test void catalogEntryRemovedAfterInitialValidationCannotBeCreated() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(create(target),
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { Item.removeCreativeItem(Item.get(Item.EMERALD)); return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
        tx.execute();
        verify(Server.getInstance().getPluginManager()).callEvent(any(InventoryTransactionEvent.class));
        assertTrue(h.inventory.getItem(0).isNull());
    }
    @Test void sessionVersionChangedAfterInitialValidationCannotReceiveUnavailableItem() throws Exception {
        Harness h = new Harness();
        Item target = new ItemMace();
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(create(target),
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { doReturn(GameVersion.V1_20_0).when(h.player).getGameVersion(); return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
        tx.execute();
        verify(Server.getInstance().getPluginManager()).callEvent(any(InventoryTransactionEvent.class));
        assertTrue(h.inventory.getItem(0).isNull());
    }
    @Test void itemToItemVirtualExchangesCannotHideUnvalidatedCredit() throws Exception {
        Harness h = new Harness();
        assertFalse(new CreativeInventoryAction(Item.get(Item.EMERALD), Item.get(Item.STONE),
                CreativeInventoryAction.TYPE_CREATE_ITEM).isValid(h.player));
        assertFalse(new CreativeInventoryAction(Item.get(Item.EMERALD), Item.get(Item.STONE),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
    }
    @Test void zeroCountNonAirCannotMasqueradeAsTheEmptyVirtualSide() throws Exception {
        Harness h = new Harness();
        assertTrue(Item.get(Item.STONE, 0, 0).isNull());
        assertFalse(new CreativeInventoryAction(Item.get(Item.STONE, 0, 0), forged(),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
        assertFalse(new CreativeInventoryAction(Item.get(Item.EMERALD), Item.get(Item.STONE, 0, 0),
                CreativeInventoryAction.TYPE_CREATE_ITEM).isValid(h.player));
    }
    @Test void deleteKeepsIntentionalMarkedAndLegacyOverstackDisposal() throws Exception {
        Harness h = new Harness();
        Item old = forged(); old.setCount(128);
        assertTrue(new CreativeInventoryAction(Item.get(Item.AIR), old,
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
        assertFalse(new CreativeInventoryAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, -1),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
        assertFalse(new CreativeInventoryAction(Item.get(Item.AIR), Item.get(Item.EMERALD, 0, 0),
                CreativeInventoryAction.TYPE_DELETE_ITEM).isValid(h.player));
    }
    @Test void serverActionMappingKeepsNativeCreationAndDeletionDirections() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        CreativeInventoryAction create = mapped(h, target, Item.get(Item.AIR),
                InventoryTransactionPacket.ACTION_MAGIC_SLOT_CREATIVE_CREATE_ITEM);
        assertEquals(CreativeInventoryAction.TYPE_CREATE_ITEM, create.getActionType());
        assertTrue(create.isValid(h.player));
        CreativeInventoryAction delete = mapped(h, Item.get(Item.AIR), forged(),
                InventoryTransactionPacket.ACTION_MAGIC_SLOT_CREATIVE_DELETE_ITEM);
        assertEquals(CreativeInventoryAction.TYPE_DELETE_ITEM, delete.getActionType());
        assertTrue(delete.isValid(h.player));
    }
    @Test void legalVirtualCreationStillWritesTheNativeSlotOnce() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(create(target),
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        assertTrue(tx.execute());
        assertEquals(4, h.inventory.getItem(0).getCount());
        assertFalse(tx.execute());
        assertEquals(4, h.inventory.getItem(0).getCount());
    }
    @Test void legalVirtualDeletionStillConsumesTheMarkedNativeSlot() throws Exception {
        Harness h = new Harness();
        Item old = forged();
        h.inventory.setItemForce(0, old);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(
                new CreativeInventoryAction(Item.get(Item.AIR), old, CreativeInventoryAction.TYPE_DELETE_ITEM),
                new SlotChangeAction(h.inventory, 0, old, Item.get(Item.AIR))));
        assertTrue(tx.canExecute());
        assertTrue(tx.execute());
        assertTrue(h.inventory.getItem(0).isNull());
    }
    @Test void creationMatchesToolDamageAndVariantAsWellAsFullNbt() throws Exception {
        Harness h = new Harness();
        Item.addCreativeItem(Item.get(Item.DIAMOND_PICKAXE));
        Item damaged = Item.get(Item.DIAMOND_PICKAXE, 17, 1);
        assertTrue(Item.isCreativeItem(GameVersion.getLastVersion(), damaged),
                "the upstream helper ignores tool damage, so its result alone is insufficient");
        assertFalse(create(damaged).isValid(h.player));
        Item custom = Item.get(Item.DIAMOND_PICKAXE, 17, 1)
                .setNamedTag(new CompoundTag().putString("km_authoritative_variant", "catalog"));
        Item.addCreativeItem(custom);
        assertTrue(create(custom.clone()).isValid(h.player), "registered host variants remain authoritative");
        Item differentVariant = custom.clone(); differentVariant.setDamage(18);
        assertFalse(create(differentVariant).isValid(h.player));
        Item differentNbt = custom.clone();
        differentNbt.setNamedTag(new CompoundTag().putString("km_authoritative_variant", "changed"));
        assertFalse(create(differentNbt).isValid(h.player));
    }
    @Test void transactionEventCannotTurnTheVirtualSourceIntoAnOversizedStack() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        CreativeInventoryAction virtual = create(target);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(virtual,
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { virtual.setSourceItem(Item.get(Item.EMERALD, 0, 65)); return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
        tx.execute();
        verify(plugins).callEvent(any(InventoryTransactionEvent.class));
        assertTrue(h.inventory.getItem(0).isNull());
    }
    @Test void transactionEventCannotChangeTheVirtualCreationIntoAnItemExchange() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        CreativeInventoryAction virtual = create(target);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(virtual,
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { virtual.setTargetItem(Item.get(Item.STONE)); return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
        tx.execute();
        verify(plugins).callEvent(any(InventoryTransactionEvent.class));
        assertTrue(h.inventory.getItem(0).isNull());
    }
    @Test void transactionEventCannotSwitchTheVirtualActionToAnUnknownType() throws Exception {
        Harness h = new Harness();
        Item target = Item.get(Item.EMERALD, 0, 4);
        CreativeInventoryAction virtual = create(target);
        InventoryTransaction tx = new InventoryTransaction(h.player, List.of(virtual,
                new SlotChangeAction(h.inventory, 0, Item.get(Item.AIR), target)));
        assertTrue(tx.canExecute());
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { virtual.actionType = 47; return null; })
                .when(plugins).callEvent(any(InventoryTransactionEvent.class));
        tx.execute();
        verify(plugins).callEvent(any(InventoryTransactionEvent.class));
        assertTrue(h.inventory.getItem(0).isNull());
    }
    private static CreativeInventoryAction mapped(Harness h, Item old, Item next, int slot) {
        NetworkInventoryAction action = new NetworkInventoryAction();
        action.sourceType = NetworkInventoryAction.SOURCE_CREATIVE;
        action.inventorySlot = slot;
        action.oldItem = old;
        action.newItem = next;
        return (CreativeInventoryAction) action.createInventoryAction(h.player);
    }
    private static CreativeInventoryAction create(Item item) {
        return new CreativeInventoryAction(item, Item.get(Item.AIR), CreativeInventoryAction.TYPE_CREATE_ITEM);
    }
    private static Item forged() {
        return Item.get(Item.EMERALD, 0, 4).setNamedTag(new CompoundTag().putString("forged_catalog_tag", "injected"));
    }
    private static final class Harness {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory = new PlayerInventory(player);
        final AtomicBoolean creative = new AtomicBoolean(true);
        Harness() throws Exception {
            Field field = Entity.class.getDeclaredField("server"); field.setAccessible(true);
            field.set(player, Server.getInstance());
            doAnswer(call -> creative.get()).when(player).isCreative();
            doReturn(GameVersion.getLastVersion()).when(player).getGameVersion();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            doReturn(0).when(player).getWindowId(any());
            player.protocol = ProtocolInfo.v1_21_40;
        }
    }
}
