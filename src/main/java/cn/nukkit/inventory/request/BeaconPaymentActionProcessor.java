package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityBeacon;
import cn.nukkit.inventory.BeaconInventory;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.BeaconPaymentAction;
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
}
