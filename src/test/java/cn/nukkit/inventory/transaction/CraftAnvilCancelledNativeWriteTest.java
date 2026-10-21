package cn.nukkit.inventory.transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.inventory.AnvilInventory;
import cn.nukkit.inventory.transaction.action.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CraftAnvilCancelledNativeWriteTest {
    @BeforeAll static void init(){CraftAnvilAtomicNativeAuditTest.init();}
    @ParameterizedTest @ValueSource(strings={"source","output"})
    void cancelledRealOakRecipeWritePreservesTheOriginalRecipeQuantity(String rejected) {
        var f=new CraftAnvilAtomicNativeAuditTest.Fixture();var tx=f.planks();
        assertTrue(tx.canExecute(),"real registered oak recipe must validate before the failing setter");
        assertNotNull(tx.getRecipe());AtomicInteger vetoes=new AtomicInteger();
        doAnswer(call->{Event event=call.getArgument(0);
            if(event instanceof EntityInventoryChangeEvent change
                    && (rejected.equals("source")&&change.getOldItem().getId()==Item.LOG
                    || rejected.equals("output")&&change.getNewItem().getId()==Item.PLANKS)) {
                change.setCancelled();vetoes.incrementAndGet();
            }return null;
        }).when(f.plugins).callEvent(any(Event.class));
        tx.execute();assertEquals(1,vetoes.get(),"the actual native setter event was cancelled exactly once");
        int log=f.grid.getItem(0).isNull()?0:f.grid.getItem(0).getCount();
        int planks=f.main.getItem(0).isNull()?0:f.main.getItem(0).getCount();
        assertAll("cancelled "+rejected+" oak recipe physical inputs/output; logs="+log+",planks="+planks,
                ()->assertEquals(4,log*4+planks,"one catalog oak log funds exactly four planks"),
                ()->assertEquals(1,log,"refused write must leave original recipe input"),
                ()->assertEquals(0,planks,"refused write must not retain a partial output"));
    }
    @ParameterizedTest @ValueSource(strings={"source","output"})
    void cancelledRealRenameWritePreservesTheOriginalSword(String rejected) {
        var f=new CraftAnvilAtomicNativeAuditTest.Fixture();var tx=f.rename();
        assertTrue(tx.canExecute(),"real native rename/NBT/experience validation succeeds first");
        AtomicInteger vetoes=new AtomicInteger();
        doAnswer(call->{Event event=call.getArgument(0);
            if(event instanceof EntityInventoryChangeEvent change
                    && (rejected.equals("source")&&change.getOldItem().getId()==Item.IRON_SWORD
                    || rejected.equals("output")&&change.getNewItem().getId()==Item.IRON_SWORD)) {
                change.setCancelled();vetoes.incrementAndGet();
            }return null;
        }).when(f.plugins).callEvent(any(Event.class));
        tx.execute();assertEquals(1,vetoes.get());
        int input=f.anvil.getItem(0).isNull()?0:f.anvil.getItem(0).getCount();
        int output=f.main.getItem(0).isNull()?0:f.main.getItem(0).getCount();
        assertAll("cancelled "+rejected+" rename; original="+input+",renamed="+output,
                ()->assertEquals(1,input+output,"one original sword must remain one physical sword"),
                ()->assertEquals(1,input,"original input retained on refusal"),
                ()->assertEquals(0,output,"partial renamed output refused"));
    }
    @Test void realMaterialRepairConsumesOneIngotAndProducesTheValidatedDurability() {
        var f=new CraftAnvilAtomicNativeAuditTest.Fixture();var tx=repair(f);assertTrue(tx.canExecute());
        assertTrue(tx.execute());assertTrue(f.anvil.getItem(0).isNull());assertTrue(f.anvil.getItem(1).isNull());
        assertEquals(Item.IRON_SWORD,f.main.getItem(0).getId());
        assertEquals(100-Item.get(Item.IRON_SWORD).getMaxDurability()/4,f.main.getItem(0).getDamage());
        assertEquals(1,f.main.getItem(0).getRepairCost());
    }
    @ParameterizedTest @ValueSource(strings={"source","material","output"})
    void cancelledValidMaterialRepairRestoresEveryAuthoredInputAndOutput(String rejected) {
        var f=new CraftAnvilAtomicNativeAuditTest.Fixture();var tx=repair(f);
        assertTrue(tx.canExecute(),"real sword/ingot durability and repair-cost validation succeeds");
        AtomicInteger vetoes=new AtomicInteger();
        doAnswer(call->{Event event=call.getArgument(0);
            if(event instanceof EntityInventoryChangeEvent change
                    && (rejected.equals("source")&&change.getOldItem().getId()==Item.IRON_SWORD
                    || rejected.equals("material")&&change.getOldItem().getId()==Item.IRON_INGOT
                    || rejected.equals("output")&&change.getNewItem().getId()==Item.IRON_SWORD)) {
                change.setCancelled();vetoes.incrementAndGet();
            }return null;
        }).when(f.plugins).callEvent(any(Event.class));
        tx.execute();assertEquals(1,vetoes.get());
        assertAll("cancelled "+rejected+" real repair physical state",
                ()->assertEquals(1,f.anvil.getItem(0).getCount(),"original damaged sword retained"),
                ()->assertEquals(100,f.anvil.getItem(0).getDamage(),"original damage retained"),
                ()->assertEquals(1,f.anvil.getItem(1).getCount(),"original repair material retained"),
                ()->assertTrue(f.main.getItem(0).isNull(),"partial repaired output cannot remain"));
    }
    static RepairItemTransaction repair(CraftAnvilAtomicNativeAuditTest.Fixture f) {
        f.anvil=new AnvilInventory(f.ui,new Position(0,64,0,f.level));f.anvil.setCost(1);
        when(f.player.getWindowById(Player.ANVIL_WINDOW_ID)).thenReturn(f.anvil);
        Item source=Item.get(Item.IRON_SWORD,100,1),material=Item.get(Item.IRON_INGOT,0,1),output=source.clone();
        output.setDamage(100-source.getMaxDurability()/4);output.setRepairCost(1);
        f.anvil.setItemForce(0,source);f.anvil.setItemForce(1,material);
        return new RepairItemTransaction(f.player,List.of(
                new RepairItemAction(Item.AIR_ITEM.clone(),source.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_INPUT),
                new RepairItemAction(Item.AIR_ITEM.clone(),material.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_MATERIAL),
                new RepairItemAction(output.clone(),Item.AIR_ITEM.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT),
                new SlotChangeAction(f.anvil,0,source.clone(),Item.AIR_ITEM.clone()),
                new SlotChangeAction(f.anvil,1,material.clone(),Item.AIR_ITEM.clone()),
                new SlotChangeAction(f.main,0,Item.AIR_ITEM.clone(),output.clone())));
    }
}
