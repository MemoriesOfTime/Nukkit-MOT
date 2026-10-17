package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.RepairItemEvent;
import cn.nukkit.inventory.AnvilInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ConsumeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeOptionalAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Native optional-recipe planning; transaction ownership/commit behavior is a separate contract. */
class ModernAnvilRepairMaterialsTest {
    private Player player;
    private PlayerUIInventory ui;
    private AnvilInventory anvil;

    @BeforeEach void setup() {
        MockServer.init();
        MockServer.reset();
        player = mock(Player.class);
        Level level = mock(Level.class);
        when(player.getLevel()).thenReturn(level);
        ui = new PlayerUIInventory(player);
        anvil = new AnvilInventory(ui, new Position(0, 64, 0));
        when(player.getServer()).thenReturn(Server.getInstance());
        when(player.getUIInventory()).thenReturn(ui);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
        when(player.getTopWindow()).thenReturn(Optional.of(anvil));
        when(player.getWindowById(Player.ANVIL_WINDOW_ID)).thenReturn(anvil);
        when(player.getExperienceLevel()).thenReturn(10);
        player.protocol = ProtocolInfo.v1_26_50;
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:planks", "minecraft:crimson_planks", "minecraft:warped_planks",
            "minecraft:mangrove_planks", "minecraft:bamboo_planks", "minecraft:cherry_planks",
            "minecraft:pale_oak_planks"})
    void shieldRepairPlanAcceptsEveryNativePlankFamily(String namespace) {
        Item material = Item.fromString(namespace);
        assertFalse(material.isNull(), "material must resolve through the actual native registry");
        assertAcceptedPlan(Item.SHIELD, material);
    }

    @Test void turtleHelmetRepairPlanAcceptsScuteAsTheLegacyPathDoes() {
        assertAcceptedPlan(Item.TURTLE_SHELL, Item.get(Item.SCUTE));
    }

    @Test void unprocessedLogCannotAuthorShieldRepairOutput() {
        Item input = input(Item.SHIELD);
        Item material = Item.get(Item.LOG);
        assertFalse(plan(input, material).success());
        assertInputsRetained(input, material);
        assertTrue(ui.getItem(50).isNull());
    }

    @Test void turtleHelmetRejectsUnrelatedPlankMaterial() {
        Item input = input(Item.TURTLE_SHELL);
        Item material = Item.get(Item.PLANKS);
        assertFalse(plan(input, material).success());
        assertInputsRetained(input, material);
        assertTrue(ui.getItem(50).isNull());
    }

    @Test void cancelledRepairEventCannotPublishStagedShieldOutput() {
        Item input = input(Item.SHIELD);
        Item material = Item.fromString("minecraft:pale_oak_planks");
        PluginManager plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            ((RepairItemEvent) call.getArgument(0)).setCancelled();
            return null;
        }).when(plugins).callEvent(any(RepairItemEvent.class));
        assertFalse(plan(input, material).success());
        assertInputsRetained(input, material);
        assertTrue(ui.getItem(50).isNull());
        verify(player, never()).setExperience(anyInt(), anyInt());
    }

    private void assertAcceptedPlan(int id, Item material) {
        Item input = input(id);
        ActionResponse response = plan(input, material);
        assertTrue(response.success(), "the native optional recipe must accept this repair material");
        Item output = ui.getItem(50);
        assertEquals(id, output.getId());
        assertEquals(1, output.getCount());
        assertTrue(output.getDamage() < input.getDamage(), "a material repair must reduce existing damage");
        assertEquals("retained", output.getNamedTag().getString("custom_payload"));
        assertInputsRetained(input, material);
        verify(player, never()).setExperience(anyInt(), anyInt());
    }

    private void assertInputsRetained(Item input, Item material) {
        assertTrue(input.equalsExact(anvil.getInputSlot()), "planning does not debit the source");
        assertTrue(material.equalsExact(anvil.getMaterialSlot()), "planning does not debit material");
    }

    private ActionResponse plan(Item input, Item material) {
        input.autoAssignStackNetworkId();
        material.setCount(1);
        material.autoAssignStackNetworkId();
        anvil.setItemForce(0, input);
        anvil.setItemForce(1, material);
        CraftRecipeOptionalAction action = new CraftRecipeOptionalAction(0, -1);
        ItemStackRequest request = new ItemStackRequest(23, new ItemStackRequestAction[]{
                action,
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.ANVIL_INPUT,
                        1, input.getStackNetId(), null)),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.ANVIL_MATERIAL,
                        2, material.getStackNetId(), null))
        }, new String[0]);
        return new CraftRecipeOptionalProcessor().handle(action, player, new ItemStackRequestContext(request));
    }

    private static Item input(int id) {
        return Item.get(id, 100, 1).setNamedTag(new CompoundTag().putString("custom_payload", "retained"));
    }
}
