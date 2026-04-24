package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityBeacon;
import cn.nukkit.inventory.BeaconInventory;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.BeaconPaymentAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.potion.Effect;

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
        if (!isValidPayment(beaconInventory.getItem(0).getId())) {
            return context.error();
        }

        int primary = action.getPrimaryEffect();
        int secondary = action.getSecondaryEffect();
        if (primary != 0 && !isValidEffect(primary)) {
            return context.error();
        }
        if (secondary != 0 && !isValidEffect(secondary)) {
            return context.error();
        }

        Position holder = beaconInventory.getHolder();
        if (holder != null) {
            if (holder.level.getBlockEntity(holder) instanceof BlockEntityBeacon beacon) {
                context.onCommit(() -> {
                    beacon.setPrimaryPower(primary);
                    beacon.setSecondaryPower(secondary);
                });
            }
        }
        return null;
    }

    private static boolean isValidEffect(int effectId) {
        try {
            return Effect.getEffect(effectId) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isValidPayment(int itemId) {
        return itemId == ItemID.NETHERITE_INGOT
                || itemId == ItemID.EMERALD
                || itemId == ItemID.DIAMOND
                || itemId == ItemID.GOLD_INGOT
                || itemId == ItemID.IRON_INGOT;
    }
}
