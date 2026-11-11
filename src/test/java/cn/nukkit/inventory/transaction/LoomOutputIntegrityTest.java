package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.LoomInventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.LoomItemAction;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Timeout(10)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class LoomOutputIntegrityTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void balancedVirtualOutputCannotForgeAnotherItemId() {
        Item input = banner(1, false);
        input.setDamage(0);
        Item output = append(input, "ts");
        Item forged = Item.get(Item.DIAMOND, 0, 1).setNamedTag(output.getNamedTag().copy());
        assertEquals(input.getDamage(), forged.getDamage(), "isolate output ID from the existing damage guard");
        assertFalse(transaction(input, dye(1), forged).canExecute());
    }

    @Test
    void multipleActionsCannotHideForgedIdBehindLawfulLastOutput() {
        Item input = banner(2, false);
        input.setDamage(0);
        Item lawful = append(input, "ts");
        Item forged = Item.get(Item.DIAMOND, 0, 1).setNamedTag(lawful.getNamedTag().copy());
        assertEquals(input.getDamage(), forged.getDamage());
        assertFalse(transaction(input, dye(2), forged, lawful).canExecute());
    }

    @Test
    void multipleActionsCannotHideForgedMetadataBehindLawfulLastOutput() {
        Item input = banner(2, true);
        Item lawful = append(input, "ts");
        Item forged = append(input, "bs");
        forged.setNamedTag(forged.getNamedTag().putString("PluginMarker", "forged"));
        assertFalse(transaction(input, dye(2), forged, lawful).canExecute());
    }

    @Test
    void multipleActionTotalsCannotExceedBannerCount() {
        Item input = banner(1, false);
        assertFalse(transaction(input, dye(2), append(input, "ts"), append(input, "bs")).canExecute());
    }

    @Test
    void multipleActionTotalsCannotExceedDyeCount() {
        Item input = banner(2, false);
        assertFalse(transaction(input, dye(1), append(input, "ts"), append(input, "bs")).canExecute());
    }

    @Test
    void multipleLawfulOutputsWithinIngredientCountsRemainValid() {
        Item input = banner(2, true);
        assertTrue(transaction(input, dye(2), append(input, "ts"), append(input, "bs")).canExecute());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, 16})
    void balancedVirtualOutputCannotCreateEmptyOrExcessStacks(int count) {
        Item input = banner(1, false);
        Item output = append(input, "ts");
        output.setCount(count);
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputCannotConsumeMoreDyeThanPresent() {
        Item input = banner(3, false);
        Item output = append(input, "ts");
        output.setCount(2);
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void groupedOutputWithinBothIngredientCountsRemainsValid() {
        Item input = banner(3, true);
        Item output = append(input, "ts");
        output.setCount(2);
        assertTrue(transaction(input, dye(2), output).canExecute());
    }

    @Test
    void outputCannotChangeBaseColor() {
        Item input = banner(1, false);
        Item output = append(input, "ts");
        output.setDamage(input.getDamage() + 1);
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputCannotAddMetadataAbsentFromInput() {
        Item input = banner(1, false);
        Item output = append(input, "ts");
        output.setNamedTag(output.getNamedTag().putInt("RepairCost", 123));
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputCannotRemoveUnrelatedInputMetadata() {
        Item input = banner(1, true);
        Item output = append(input, "ts");
        output.setNamedTag(output.getNamedTag().remove("PluginMarker"));
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputCannotChangeUnrelatedInputMetadata() {
        Item input = banner(1, true);
        Item output = append(input, "ts");
        output.setNamedTag(output.getNamedTag().putString("PluginMarker", "forged"));
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputCannotReplaceExistingPatternPrefix() {
        Item input = banner(1, true);
        Item output = append(input, "ts");
        output.getNamedTag().getList("Patterns", CompoundTag.class).add(0, pattern("bs"));
        output.setNamedTag(output.getNamedTag());
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputCannotRemoveOldPatternAndReplaceItWithAnother() {
        Item input = banner(1, true);
        Item output = input.clone();
        output.setNamedTag(output.getNamedTag().putList(new ListTag<CompoundTag>("Patterns")
                .add(pattern("ls")).add(pattern("rs"))));
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputWithoutPatternListIsRejected() {
        Item input = banner(1, true);
        Item output = append(input, "ts");
        output.setNamedTag(output.getNamedTag().remove("Patterns"));
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void outputCannotUseNonCompoundPatternsOfCorrectLength() {
        Item input = banner(1, true);
        Item output = append(input, "ts");
        output.setNamedTag(output.getNamedTag().putList(new ListTag<StringTag>("Patterns")
                .add(new StringTag("", "bs")).add(new StringTag("", "ts"))));
        assertFalse(transaction(input, dye(1), output).canExecute());
    }

    @Test
    void firstPatternOnUntaggedBannerRemainsValid() {
        Item input = banner(1, false);
        assertFalse(input.hasCompoundTag());
        assertTrue(transaction(input, dye(1), append(input, "ts")).canExecute());
    }

    @Test
    void firstPatternOnExplicitlyEmptyListRemainsValid() {
        Item input = banner(1, false).setNamedTag(new CompoundTag()
                .putList(new ListTag<CompoundTag>("Patterns")));
        assertTrue(transaction(input, dye(1), append(input, "ts")).canExecute());
    }

    @Test
    void validOutputPreservesInputMetadataWithoutMutatingAnyItem() {
        Item input = banner(1, true);
        Item dye = dye(1);
        Item output = append(input, "ts");
        byte[] inputBefore = input.getCompoundTag().clone();
        byte[] dyeBefore = dye.getCompoundTag().clone();
        byte[] outputBefore = output.getCompoundTag().clone();
        CompoundTag inputTagBefore = snapshotNamedTag(input);
        LoomTransaction transaction = transaction(input, dye, output);
        CompoundTag admittedOutputBefore = snapshotNamedTag(transaction.getOutputItem());
        assertTrue(transaction.canExecute());
        assertEquals(inputTagBefore, snapshotNamedTag(input));
        assertEquals(admittedOutputBefore, snapshotNamedTag(transaction.getOutputItem()));
        assertArrayEquals(inputBefore, input.getCompoundTag());
        assertArrayEquals(dyeBefore, dye.getCompoundTag());
        assertArrayEquals(outputBefore, output.getCompoundTag());
        assertEquals(1, input.getCount());
        assertEquals(1, dye.getCount());
        assertEquals(1, output.getCount());
    }

    @Test
    void rejectedOutputDoesNotMutateInputMetadata() {
        Item input = banner(1, true);
        Item output = append(input, "ts");
        output.setNamedTag(output.getNamedTag().putString("PluginMarker", "forged"));
        byte[] inputBefore = input.getCompoundTag().clone();
        byte[] outputBefore = output.getCompoundTag().clone();
        CompoundTag inputTagBefore = snapshotNamedTag(input);
        LoomTransaction transaction = transaction(input, dye(1), output);
        CompoundTag rejectedOutputBefore = snapshotNamedTag(transaction.getOutputItem());
        assertFalse(transaction.canExecute());
        assertEquals(inputTagBefore, snapshotNamedTag(input));
        assertEquals(rejectedOutputBefore, snapshotNamedTag(transaction.getOutputItem()));
        assertArrayEquals(inputBefore, input.getCompoundTag());
        assertArrayEquals(outputBefore, output.getCompoundTag());
    }

    private static CompoundTag snapshotNamedTag(Item item) {
        if (!item.hasCompoundTag()) {
            return null;
        }
        CompoundTag snapshot = item.getNamedTag().copy();
        snapshot.setName("");
        return snapshot;
    }

    private static Item banner(int count, boolean tagged) {
        Item item = Item.get(Item.BANNER, 7, count);
        if (tagged) {
            item.setNamedTag(new CompoundTag().putString("PluginMarker", "keep")
                    .putCompound("display", new CompoundTag().putString("Name", "Наш флаг"))
                    .putList(new ListTag<CompoundTag>("Patterns").add(pattern("ms"))));
        }
        return item;
    }

    private static Item dye(int count) {
        return Item.get(Item.DYE, 1, count);
    }

    private static CompoundTag pattern(String name) {
        return new CompoundTag().putInt("Color", 1).putString("Pattern", name);
    }

    private static Item append(Item input, String name) {
        Item output = input.clone();
        output.setCount(1);
        CompoundTag tag = output.hasCompoundTag() ? output.getNamedTag().copy() : new CompoundTag();
        ListTag<CompoundTag> patterns = tag.getList("Patterns", CompoundTag.class);
        patterns.add(pattern(name));
        return output.setNamedTag(tag.putList(patterns));
    }

    private static LoomTransaction transaction(Item banner, Item dye, Item... outputs) {
        Player player = mock(Player.class);
        LoomInventory inventory = mock(LoomInventory.class);
        when(player.getWindowById(Player.LOOM_WINDOW_ID)).thenReturn(inventory);
        when(inventory.getBanner()).thenReturn(banner);
        when(inventory.getDye()).thenReturn(dye);
        when(inventory.getPattern()).thenReturn(Item.get(Item.AIR));
        // Real superclass matching balances every virtual result with its recipient.
        // Forged outputs must still be rejected by Loom admission after that match.
        List<InventoryAction> actions = new ArrayList<>();
        for (Item output : outputs) {
            actions.add(new LoomItemAction(output, Item.get(Item.AIR), inventory));
            actions.add(new InventoryAction(Item.get(Item.AIR), output) {
                @Override public boolean isValid(Player source) { return true; }
                @Override public boolean execute(Player source) { return true; }
                @Override public void onExecuteSuccess(Player source) { }
                @Override public void onExecuteFail(Player source) { }
            });
        }
        return new LoomTransaction(player, actions);
    }
}
