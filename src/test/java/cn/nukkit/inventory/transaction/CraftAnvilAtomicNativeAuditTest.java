package cn.nukkit.inventory.transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.Event;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.plugin.PluginManager;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftAnvilAtomicNativeAuditTest {
    static CraftingManager manager;
    @BeforeAll static void init() {
        MockServer.init();manager=new CraftingManager();
    }
    @Test void actualCatalogOakLogToFourPlanksCommits() {
        Fixture f=new Fixture();CraftingTransaction tx=f.planks();
        assertTrue(tx.canExecute(),"real catalog recipe and exact native source actions");assertNotNull(tx.getRecipe());
        assertTrue(tx.execute());assertTrue(f.grid.getItem(0).isNull());assertEquals(4,f.main.getItem(0).getCount());
        assertTrue(tx.hasExecuted());
    }
    @Test void actualAnvilSwordRenameCommits() {
        Fixture f=new Fixture();RepairItemTransaction tx=f.rename();
        assertTrue(tx.canExecute(),"real rename validation plus exact native sources");
        assertTrue(tx.execute());assertTrue(f.anvil.getItem(0).isNull());assertEquals("Renamed",f.main.getItem(0).getCustomName());
    }
    static class Fixture {
        final Server server;final Player player;final PluginManager plugins;final Level level;
        final PlayerInventory main;final PlayerUIInventory ui;final CraftingGrid grid;final PlayerOffhandInventory offhand;
        AnvilInventory anvil;
        Fixture() {
            MockServer.reset();server=Server.getInstance();plugins=mock(PluginManager.class);when(server.getPluginManager()).thenReturn(plugins);when(server.getCraftingManager()).thenReturn(manager);when(server.getScheduler()).thenReturn(mock(cn.nukkit.scheduler.ServerScheduler.class));
            player=mock(Player.class);player.protocol=2193;player.craftingType=Player.CRAFTING_SMALL;player.spawned=true;
            when(player.getGameVersion()).thenReturn(cn.nukkit.GameVersion.V1_26_50);
            when(player.isAlive()).thenReturn(true);when(player.isOnline()).thenReturn(true);when(server.isPrimaryThread()).thenReturn(true);
            when(player.getServer()).thenReturn(server);level=mock(Level.class);when(level.getServer()).thenReturn(server);player.level=level;
            when(player.getLevel()).thenReturn(level);when(level.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(c->Block.get(Block.AIR));
            main=new PlayerInventory(player);ui=new PlayerUIInventory(player);grid=ui.getCraftingGrid();offhand=new PlayerOffhandInventory(player);
            when(player.getInventory()).thenReturn(main);when(player.getCraftingGrid()).thenReturn(grid);when(player.getOffhandInventory()).thenReturn(offhand);
            when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());when(player.getUIInventory()).thenReturn(ui);
            when(player.getWindowId(any(Inventory.class))).thenReturn(0);
            when(player.getExperienceLevel()).thenReturn(10);
            doAnswer(c->{Event event=c.getArgument(0);return null;}).when(plugins).callEvent(any(Event.class));
        }
        CraftingTransaction planks() {
            Item log=Item.get(Item.LOG,0,1),output=Item.get(Item.PLANKS,0,4);grid.setItemForce(0,log);
            assertNotNull(manager.matchRecipe(new ArrayList<>(List.of(log.clone())),output.clone(),new ArrayList<>()),"actual catalog must recognize vanilla oak recipe");
            return new CraftingTransaction(player,List.of(new CraftingTransferMaterialAction(Item.AIR_ITEM.clone(),log.clone(),0),
                    new CraftingTakeResultAction(output.clone(),Item.AIR_ITEM.clone()),
                    new SlotChangeAction(grid,0,log.clone(),Item.AIR_ITEM.clone()),new SlotChangeAction(main,0,Item.AIR_ITEM.clone(),output.clone())));
        }
        RepairItemTransaction rename() {
            anvil=new AnvilInventory(ui,new Position(0,64,0,level));anvil.setCost(1);
            when(player.getWindowById(Player.ANVIL_WINDOW_ID)).thenReturn(anvil);
            Item source=Item.get(Item.IRON_SWORD,0,1),output=source.clone();output.setCustomName("Renamed");anvil.setItemForce(0,source);
            return new RepairItemTransaction(player,List.of(
                    new RepairItemAction(Item.AIR_ITEM.clone(),source.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_INPUT),
                    new RepairItemAction(output.clone(),Item.AIR_ITEM.clone(),NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT),
                    new SlotChangeAction(anvil,0,source.clone(),Item.AIR_ITEM.clone()),new SlotChangeAction(main,0,Item.AIR_ITEM.clone(),output.clone())));
        }
    }
}
