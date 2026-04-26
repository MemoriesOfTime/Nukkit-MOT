package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityBeacon;
import cn.nukkit.inventory.BeaconInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.BeaconPaymentAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.DestroyAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;

public class BeaconPaymentActionProcessor implements ItemStackRequestActionProcessor<BeaconPaymentAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.BEACON_PAYMENT;
    }

    @Override
    public ActionResponse handle(BeaconPaymentAction action, Player player, ItemStackRequestContext context) {
        if (!(player.getTopWindow().orElse(null) instanceof BeaconInventory beaconInventory)) {
            return context.error();
        }
        if (!BlockEntityBeacon.isPaymentItem(beaconInventory.getItem(0).getId())) {
            return context.error();
        }
        if (!hasValidPaymentDestroyAction(player, beaconInventory, context)) {
            return context.error();
        }

        int primary = action.getPrimaryEffect();
        int secondary = action.getSecondaryEffect();
        if (!BlockEntityBeacon.isAllowedEffect(primary)) {
            return context.error();
        }
        if (!BlockEntityBeacon.isAllowedEffect(secondary)) {
            return context.error();
        }

        Position holder = beaconInventory.getHolder();
        if (holder == null || !(holder.level.getBlockEntity(holder) instanceof BlockEntityBeacon beacon)) {
            return context.error();
        }
        if (beacon.getPowerLevel() < 1) {
            return context.error();
        }
        context.onCommit(() -> {
            beacon.setPrimaryPower(primary);
            beacon.setSecondaryPower(secondary);
        });
        return null;
    }

    static boolean hasValidPaymentDestroyAction(Player player, BeaconInventory beaconInventory, ItemStackRequestContext context) {
        ItemStackRequestAction[] actions = context.getItemStackRequest().getActions();
        int destroyIndex = context.getCurrentActionIndex() + 1;
        if (destroyIndex >= actions.length || !(actions[destroyIndex] instanceof DestroyAction destroy)) {
            return false;
        }
        if (destroy.getCount() != 1) {
            return false;
        }

        ItemStackRequestSlotData source = destroy.getSource();
        if (source == null) {
            return false;
        }
        if (source.getContainer() != ContainerSlotType.BEACON_PAYMENT) {
            return false;
        }
        Inventory destroyInventory = NetworkMapping.getInventory(player, source.getContainer(), source.getDynamicId());
        if (destroyInventory != beaconInventory) {
            return false;
        }
        if (NetworkMapping.toInternalSlot(source.getContainer(), source.getSlot()) != 0) {
            return false;
        }

        Item payment = beaconInventory.getItem(0);
        return !payment.isNull()
                && payment.getCount() >= destroy.getCount()
                && BlockEntityBeacon.isPaymentItem(payment.getId())
                && !hasStackNetworkIdMismatch(payment.getStackNetId(), source.getStackNetworkId());
    }

    private static boolean hasStackNetworkIdMismatch(int serverNetId, int clientNetId) {
        return serverNetId > 0 && clientNetId > 0 && serverNetId != clientNetId;
    }
}
