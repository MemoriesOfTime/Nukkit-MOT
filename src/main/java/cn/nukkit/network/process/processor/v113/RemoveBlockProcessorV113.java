package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.network.protocol.v113.RemoveBlockPacketV113;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@Log4j2
public class RemoveBlockProcessorV113 extends DataPacketProcessor<RemoveBlockPacketV113> {

    public static final RemoveBlockProcessorV113 INSTANCE = new RemoveBlockProcessorV113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull RemoveBlockPacketV113 pk) {
        //TODO
        //InventoryTransactionPacket.USE_ITEM_ACTION_BREAK_BLOCK
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }

        player.resetCraftingGridType();

        PlayerInventory inventory = player.getInventory();
        Item i = inventory.getItemInHand();

        Item oldItem = i.clone();

        BlockVector3 blockVector = new BlockVector3(pk.x, pk.y, pk.z);

        if (player.canInteract(blockVector.add(0.5, 0.5, 0.5), player.isCreative() ? 13 : 7) && (i = player.level.useBreakOn(blockVector.asVector3(), player.getHorizontalFacing(), i, player, true)) != null) {
            if (player.isSurvival() || player.isAdventure()) {
                player.getFoodData().updateFoodExpLevel(0.005);
                if (!i.equals(oldItem) || i.getCount() != oldItem.getCount()) {
                    if (oldItem.getId() == i.getId() || i.getId() == 0) {
                        inventory.setItemInHand(i);
                    } else {
                        log.debug("Tried to set item " + i.getId() + " but " + playerHandle.getUsername() + " had item " + oldItem.getId() + " in their hand slot");
                    }
                    inventory.sendHeldItem(player.getViewers().values());
                }
            }
            return;
        }

        inventory.sendContents(player);
        inventory.sendHeldItem(player);

        if (blockVector.distanceSquared(player) < 10000) {
            Block target = player.level.getBlock(blockVector.asVector3());
            player.level.sendBlocks(new Player[]{player}, new Block[]{target}, UpdateBlockPacket.FLAG_ALL_PRIORITY);

            BlockEntity blockEntity = player.level.getBlockEntity(blockVector.asVector3());
            if (blockEntity instanceof BlockEntitySpawnable) {
                ((BlockEntitySpawnable) blockEntity).spawnTo(player);
            }
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(RemoveBlockPacketV113.NETWORK_ID);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
