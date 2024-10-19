package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDoor;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ProjectileItem;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.network.protocol.v113.UseItemPacketV113;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class UseItemProcessor_v113 extends DataPacketProcessor<UseItemPacketV113> {

    public static final UseItemProcessor_v113 INSTANCE = new UseItemProcessor_v113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull UseItemPacketV113 pk) {
        //TODO check
        //尝试翻译为InventoryTransactionPacket

        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }

        UseItemPacketV113 useItemPacket = pk;

        Vector3 blockVector = new Vector3(useItemPacket.x, useItemPacket.y, useItemPacket.z);

        player.craftingType = Player.CRAFTING_SMALL;

        Item item = null;
        if (useItemPacket.face >= 0 && useItemPacket.face <= 5) {
            BlockFace face = BlockFace.fromIndex(useItemPacket.face);
            player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);

            if (!player.canInteract(blockVector.add(0.5, 0.5, 0.5), player.isCreative() ? 13 : 7)) {
            } else if (player.isCreative()) {
                Item i = player.getInventory().getItemInHand();
                if (player.level.useItemOn(blockVector, i, face, useItemPacket.fx, useItemPacket.fy, useItemPacket.fz, player, true) != null) {
                    return;
                }
            } else if (!player.getInventory().getItemInHand().deepEquals(useItemPacket.item)) {
                player.getInventory().sendHeldItem(player);
            } else {
                item = player.getInventory().getItemInHand();
                Item oldItem = item.clone();
                //TODO: Implement adventure mode checks
                if ((item = player.level.useItemOn(blockVector, item, face, useItemPacket.fx, useItemPacket.fy, useItemPacket.fz, player, true)) != null) {
                    if (!item.deepEquals(oldItem) || item.getCount() != oldItem.getCount()) {
                        player.getInventory().setItemInHand(item);
                        player.getInventory().sendHeldItem(player.hasSpawned.values());
                    }
                    return;
                }
            }

            player.getInventory().sendHeldItem(player);

            if (blockVector.distanceSquared(player) > 10000) {
                return;
            }

            Block target = player.level.getBlock(blockVector);
            Block block = target.getSide(face);

            if (target instanceof BlockDoor) {
                BlockDoor door = (BlockDoor) target;

                Block part;

                if ((door.getDamage() & 0x08) > 0) { //up
                    part = target.down();

                    if (part.getId() == target.getId()) {
                        target = part;
                    }
                }
            }

            player.level.sendBlocks(new Player[]{player}, new Block[]{target, block}, UpdateBlockPacket.FLAG_ALL_PRIORITY);
            return;
        } else if (useItemPacket.face == -1) {
            Vector3 aimPos = player.getDirectionVector();

            if (player.isCreative()) {
                item = player.getInventory().getItemInHand();
            } else if (!player.getInventory().getItemInHand().deepEquals(useItemPacket.item)) {
                player.getInventory().sendHeldItem(player);
                return;
            } else {
                item = player.getInventory().getItemInHand();
            }

            PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(player, item, aimPos, null, PlayerInteractEvent.Action.RIGHT_CLICK_AIR);

            player.getServer().getPluginManager().callEvent(playerInteractEvent);

            if (playerInteractEvent.isCancelled()) {
                player.getInventory().sendHeldItem(player);
                return;
            }

            if (item instanceof ProjectileItem) {
                item.onClickAir(player, aimPos);
            }

            player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, true);
            playerHandle.setStartAction(player.getServer().getTick());
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(UseItemPacketV113.NETWORK_ID);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
