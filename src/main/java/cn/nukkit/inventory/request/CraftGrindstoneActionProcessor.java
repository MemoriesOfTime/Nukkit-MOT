package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.GrindItemEvent;
import cn.nukkit.inventory.GrindstoneInventory;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftGrindstoneAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;

import java.util.List;

/**
 * Grindstone disenchant/repair handling. Mirrors the logic used by
 * GrindstoneTransaction so grindstone operations work through either path.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class CraftGrindstoneActionProcessor implements ItemStackRequestActionProcessor<CraftGrindstoneAction> {

    public static final String GRINDSTONE_EXP_KEY = "grindstoneExp";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_REPAIR_AND_DISENCHANT;
    }

    @Override
    public ActionResponse handle(CraftGrindstoneAction action, Player player, ItemStackRequestContext context) {
        if (!(player.getTopWindow().orElse(null) instanceof GrindstoneInventory grindstone)) {
            return context.error();
        }

        Item result = grindstone.getResult();
        if (result.isNull()) {
            return context.error();
        }

        int experience = grindstone.calculateExperience();
        GrindItemEvent event = new GrindItemEvent(
                grindstone,
                grindstone.getEquipment(),
                result,
                grindstone.getIngredient(),
                experience,
                player
        );
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return context.error();
        }

        // Stock vanilla behaviour: grinding emits the stored enchantment XP to the
        // player. Missing this was a regression from the previous implementation.
        if (experience > 0) {
            player.addExperience(experience);
        }

        Item resultClone = result.clone().autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, resultClone, false);
        context.put(GRINDSTONE_EXP_KEY, experience);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                0, 0, resultClone.getCount(), resultClone.getStackNetId(),
                resultClone.hasCustomName() ? resultClone.getCustomName() : "",
                resultClone.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }
}
