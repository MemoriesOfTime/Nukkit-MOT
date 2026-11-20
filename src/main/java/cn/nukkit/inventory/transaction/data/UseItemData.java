package cn.nukkit.inventory.transaction.data;

import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import lombok.ToString;

/**
 * @author CreeperFace
 */
@ToString
public class UseItemData implements TransactionData {

    public static final int TRIGGER_UNKNOWN = 0;
    public static final int TRIGGER_PLAYER_INPUT = 1;
    public static final int TRIGGER_SIMULATION_TICK = 2;

    public int actionType;
    public int triggerType;
    public boolean hasTriggerType;
    public BlockVector3 blockPos;
    public BlockFace face;
    public int hotbarSlot;
    public Item itemInHand;
    public Vector3 playerPos;
    public Vector3f clickPos;
    public int blockRuntimeId;
    public int clientInteractPrediction;
    public int clientCooldownState;
    /**
     * @since v2192
     */
    public int hand;
}
