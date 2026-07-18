package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.inventory.special.FireworkRecipe;
import cn.nukkit.inventory.transaction.action.CraftingTakeResultAction;
import cn.nukkit.inventory.transaction.action.CraftingTransferMaterialAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.item.ItemID;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link CraftingTransaction#applyAuthoritativeOutput(Item)} replaces the
 * client-authored output NBT with the server-authoritative one during firework crafting (#798).
 */
class CraftingTransactionFireworkTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void authoritativeOutputOverwritesPrimaryOutputAndResultActions() {
        ItemFirework clientOutput = clientAuthoredOutput();
        ItemFirework serverOutput = serverAuthoritativeOutput();
        assertNotEquals(clientOutput, serverOutput, "precondition: client and server output NBT must differ");

        CraftingTakeResultAction takeResultAction = new CraftingTakeResultAction(clientOutput.clone(), Item.get(Item.AIR));
        SlotChangeAction placeAction = new SlotChangeAction(mockInventory(), 0, Item.get(Item.AIR), clientOutput.clone());

        List<InventoryAction> actions = new ArrayList<>();
        actions.add(takeResultAction);
        actions.add(placeAction);
        CraftingTransaction transaction = new CraftingTransaction(mockPlayer(), actions);

        transaction.applyAuthoritativeOutput(serverOutput);

        assertTrue(transaction.getPrimaryOutput().equalsExact(serverOutput));
        assertTrue(takeResultAction.getSourceItem().equalsExact(serverOutput));
        assertTrue(placeAction.getTargetItem().equalsExact(serverOutput));
    }

    @Test
    void authoritativeFireworkOutputCarriesStarExplosion() {
        FireworkRecipe recipe = new FireworkRecipe();
        List<Item> inputs = List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER),
                fireworkStar((byte) 1)
        );
        ItemFirework clientOutput = new ItemFirework(0, 3);
        clientOutput.setFlight(1);

        assertTrue(recipe.canExecute(null, clientOutput, inputs));

        ItemFirework result = (ItemFirework) recipe.toRecipe(clientOutput, inputs).getResult();
        ListTag<CompoundTag> explosions = result.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        assertEquals(1, explosions.size());
        assertTrue(explosions.get(0).contains("FireworkFlicker"));
        assertTrue(explosions.get(0).contains("FireworkTrail"));
    }

    @Test
    void canExecuteAcceptsCraftAndAppliesAuthoritativeOutput() {
        // End-to-end: mock the CraftingManager to take the multi-recipe path and verify canExecute
        // passes while the output is overridden by the server.
        // Note: matchItems' SlotChangeAction.isValid requires a real inventory, so consumed inputs
        // use a BalanceAction whose isValid is always true.
        FireworkRecipe realFireworkRecipe = new FireworkRecipe();
        Server server = Server.getInstance();
        CraftingManager craftingManager = Mockito.mock(CraftingManager.class);
        Mockito.lenient().when(craftingManager.matchRecipe(Mockito.anyList(), Mockito.any(), Mockito.anyList()))
                .thenReturn(null);
        Mockito.lenient().when(craftingManager.getMultiRecipe(Mockito.any(), Mockito.any(), Mockito.anyList()))
                .thenAnswer(inv -> {
                    Item output = inv.getArgument(1);
                    @SuppressWarnings("unchecked")
                    List<Item> inputs = inv.getArgument(2);
                    return realFireworkRecipe.canExecute(null, output, inputs) ? realFireworkRecipe : null;
                });
        Mockito.lenient().when(server.getCraftingManager()).thenReturn(craftingManager);

        Player player = Mockito.mock(Player.class);
        player.craftingType = Player.CRAFTING_BIG;
        Mockito.lenient().when(player.getServer()).thenReturn(server);

        ItemFirework clientOutput = clientAuthoredOutput();
        BaseInventory inventory = mockInventory();
        List<InventoryAction> actions = new ArrayList<>();
        actions.add(new CraftingTakeResultAction(clientOutput.clone(), Item.get(Item.AIR)));
        actions.add(new SlotChangeAction(inventory, 0, Item.get(Item.AIR), clientOutput.clone()));
        for (Item input : List.of(Item.get(ItemID.PAPER), Item.get(ItemID.GUNPOWDER), fireworkStar((byte) 1))) {
            actions.add(new CraftingTransferMaterialAction(Item.get(Item.AIR), input.clone(), 0));
            actions.add(new BalanceAction(input.clone(), Item.get(Item.AIR)));
        }

        CraftingTransaction transaction = new CraftingTransaction(player, actions);
        assertTrue(transaction.canExecute());

        Item primary = transaction.getPrimaryOutput();
        ListTag<CompoundTag> explosions = primary.getNamedTag()
                .getCompound("Fireworks")
                .getList("Explosions", CompoundTag.class);
        assertEquals(1, explosions.size());
        assertTrue(explosions.get(0).contains("FireworkFlicker"));
    }

    private static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        player.craftingType = Player.CRAFTING_BIG;
        return player;
    }

    private static BaseInventory mockInventory() {
        BaseInventory inventory = Mockito.mock(BaseInventory.class);
        Mockito.lenient().when(inventory.allowedToAdd(Mockito.any())).thenReturn(true);
        Mockito.lenient().when(inventory.getItem(Mockito.anyInt())).thenReturn(Item.get(Item.AIR));
        return inventory;
    }

    private static Item fireworkStar(byte color) {
        Item star = Item.get(ItemID.FIREWORKSCHARGE, 0, 1);
        CompoundTag fireworksItem = new CompoundTag("FireworksItem")
                .putByteArray("FireworkColor", new byte[]{color})
                .putBoolean("FireworkFlicker", false)
                .putBoolean("FireworkTrail", false)
                .putByte("FireworkType", 0);
        star.setNamedTag(new CompoundTag().putCompound("FireworksItem", fireworksItem));
        return star;
    }

    /** Client-authored output: minimal explosion (omits false Flicker/Trail fields). */
    private static ItemFirework clientAuthoredOutput() {
        ItemFirework output = new ItemFirework(0, 3);
        output.setFlight(1);
        output.getNamedTag().getCompound("Fireworks").getList("Explosions", CompoundTag.class).add(
                new CompoundTag()
                        .putByteArray("FireworkColor", new byte[]{1})
                        .putByte("FireworkType", 0));
        output.setNamedTag(output.getNamedTag());
        return output;
    }

    /** Server-authoritative output: createResult fills in Flicker/Trail and other false fields. */
    private static ItemFirework serverAuthoritativeOutput() {
        ItemFirework output = new ItemFirework(0, 3);
        output.setFlight(1);
        output.getNamedTag().getCompound("Fireworks").getList("Explosions", CompoundTag.class).add(
                new CompoundTag()
                        .putByteArray("FireworkColor", new byte[]{1})
                        .putBoolean("FireworkFlicker", false)
                        .putBoolean("FireworkTrail", false)
                        .putByte("FireworkType", 0));
        output.setNamedTag(output.getNamedTag());
        return output;
    }

    /** Balance action whose isValid is always true, supplying haveItems to cancel needItems. */
    private static final class BalanceAction extends InventoryAction {
        private BalanceAction(Item sourceItem, Item targetItem) {
            super(sourceItem, targetItem);
        }

        @Override
        public boolean isValid(Player source) {
            return true;
        }

        @Override
        public boolean execute(Player source) {
            return true;
        }

        @Override
        public void onExecuteSuccess(Player source) {
        }

        @Override
        public void onExecuteFail(Player source) {
        }
    }
}
