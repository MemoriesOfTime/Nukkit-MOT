package cn.nukkit.network.protocol.types.inventory.itemstack.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * ItemEntry holds information on what item stack should be present in a specific slot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemStackResponseSlot {
    int slot;
    int hotbarSlot;
    int count;

    /**
     * stackNetworkID is the network ID of the new stack at a specific slot.
     */
    int stackNetworkId;

    /**
     * Holds the final custom name of a renamed item, if relevant.
     *
     * @since v422
     */
    @NonNull String customName;

    /**
     * @since v428
     */
    int durabilityCorrection;

    /**
     * @since v766
     */
    String filteredCustomName = "";
}