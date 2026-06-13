package cn.nukkit.network.process.processor.v113;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.CraftingEventPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class CraftingEvent_v113AdapterTest {

    private CraftingManager craftingManager;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        MockServer.reset();
        craftingManager = Mockito.mock(CraftingManager.class);
        Mockito.lenient().when(MockServer.get().getCraftingManager()).thenReturn(craftingManager);
    }

    @Test
    void rejectsInventoryOnlyMaterialsWhenCraftingGridIsEmpty() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        Mockito.lenient().when(craftingManager.matchRecipe(any(), any(), any())).thenReturn(recipe);

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe, recipe.getResult()));
        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void processorRejectsInventoryOnlyMaterialsWhenPacketInputIsEmpty() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        recipe.setId(java.util.UUID.randomUUID());
        player.getInventory().setItem(0, Item.get(BlockID.LOG));

        Mockito.lenient().when(craftingManager.getRecipes()).thenReturn(List.of(recipe));
        Mockito.lenient().when(craftingManager.matchRecipe(any(), any(), any())).thenReturn(recipe);

        CraftingEventPacket packet = new CraftingEventPacket();
        packet.windowId = ContainerIds.INVENTORY;
        packet.id = recipe.getId();
        packet.input = Item.EMPTY_ARRAY;
        packet.output = new Item[]{recipe.getResult()};

        CraftingEventProcessor_v113.INSTANCE.handle(new PlayerHandle(player), packet);

        assertTrue(player.getInventory().contains(Item.get(BlockID.LOG)));
        assertFalse(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    @Test
    void rejectsCraftingTableRecipeInSmallGrid() {
        TestPlayer player = new TestPlayer();
        ShapedRecipe recipe = threeByThreeRecipe();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, Item.get(BlockID.PLANKS));
        }

        Mockito.lenient().when(craftingManager.matchRecipe(any(), any(), any())).thenReturn(recipe);

        assertFalse(CraftingEvent_v113Adapter.execute(player, recipe, recipe.getResult()));
        assertFalse(player.getInventory().contains(Item.get(ItemID.DIAMOND)));
    }

    @Test
    void craftsFromServerCraftingGrid() {
        TestPlayer player = new TestPlayer();
        ShapelessRecipe recipe = logToPlanksRecipe();
        player.getCraftingGrid().setItem(0, Item.get(BlockID.LOG));

        Mockito.lenient().when(craftingManager.matchRecipe(any(), any(), any())).thenReturn(recipe);

        assertTrue(CraftingEvent_v113Adapter.execute(player, recipe, recipe.getResult()));
        assertEquals(Item.AIR, player.getCraftingGrid().getItem(0).getId());
        assertTrue(player.getInventory().contains(Item.get(BlockID.PLANKS, 0, 4)));
    }

    private static ShapelessRecipe logToPlanksRecipe() {
        return new ShapelessRecipe(Item.get(BlockID.PLANKS, 0, 4), List.of(Item.get(BlockID.LOG)));
    }

    private static ShapedRecipe threeByThreeRecipe() {
        return new ShapedRecipe(
                Item.get(ItemID.DIAMOND),
                new String[]{"PPP", "PPP", "PPP"},
                Map.of('P', Item.get(BlockID.PLANKS)),
                List.of()
        );
    }

    private static final class TestPlayer extends Player {

        private TestPlayer() {
            super(source(), 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.inventory = new PlayerInventory(this);
            this.playerUIInventory = new PlayerUIInventory(this);
            this.craftingGrid = this.playerUIInventory.getCraftingGrid();
            this.addWindow(this.inventory, ContainerIds.INVENTORY, true, true);
            this.addWindow(this.playerUIInventory, ContainerIds.UI, true, true);
            this.addWindow(this.craftingGrid, ContainerIds.NONE, true, true);
            this.craftingType = Player.CRAFTING_SMALL;
            this.protocol = ProtocolInfo.v1_1_0;
            this.gameVersion = GameVersion.V1_1_0;
            this.spawned = true;
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return false;
        }

        @Override
        public String getName() {
            return "TestPlayer";
        }
    }

    private static SourceInterface source() {
        SourceInterface source = Mockito.mock(SourceInterface.class);
        NetworkPlayerSession session = Mockito.mock(NetworkPlayerSession.class);
        Mockito.lenient().when(source.getSession(any())).thenReturn(session);
        return source;
    }
}
