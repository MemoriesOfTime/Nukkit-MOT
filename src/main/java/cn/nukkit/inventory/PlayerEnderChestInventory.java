package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.block.BlockEnderChest;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.BlockEventPacket;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerType;

public class PlayerEnderChestInventory extends BaseInventory {

    public PlayerEnderChestInventory(EntityHumanType player) {
        super(player, InventoryType.ENDER_CHEST);
    }

    @Override
    public EntityHuman getHolder() {
        return (EntityHuman) this.holder;
    }

    @Override
    public void onOpen(Player who) {
        if (who != this.getHolder()) {
            return;
        }
        super.onOpen(who);
        ContainerOpenPacket containerOpenPacket = new ContainerOpenPacket();
        containerOpenPacket.windowId = who.getWindowId(this);
        containerOpenPacket.type = this.getType().getNetworkType();
        BlockEnderChest chest = who.getViewingEnderChest();
        if (chest != null) {
            containerOpenPacket.x = (int) chest.getX();
            containerOpenPacket.y = (int) chest.getY();
            containerOpenPacket.z = (int) chest.getZ();
        } else {
            containerOpenPacket.x = containerOpenPacket.y = containerOpenPacket.z = 0;
        }

        who.dataPacket(containerOpenPacket);

        this.sendContents(who);

        if (chest != null && chest.getViewers().size() == 1) {
            BlockEventPacket blockEventPacket = new BlockEventPacket();
            blockEventPacket.x = (int) chest.getX();
            blockEventPacket.y = (int) chest.getY();
            blockEventPacket.z = (int) chest.getZ();
            blockEventPacket.eventType = 1;
            blockEventPacket.eventData = 1;

            broadcastLid(chest, who, blockEventPacket, LevelSoundEventPacket.SOUND_ENDERCHEST_OPEN);
        }
    }

    @Override
    public void onClose(Player who) {
        if (who.getClosingWindowId() != Integer.MAX_VALUE) {
            ContainerClosePacket containerClosePacket = new ContainerClosePacket();
            containerClosePacket.windowId = who.getWindowId(this);
            containerClosePacket.wasServerInitiated = who.getClosingWindowId() != containerClosePacket.windowId;
            containerClosePacket.type = ContainerType.from(this.type.getNetworkType());
            who.dataPacket(containerClosePacket);
        }

        super.onClose(who);

        BlockEnderChest chest = who.getViewingEnderChest();
        if (chest != null && chest.getViewers().size() == 1) {
            BlockEventPacket blockEventPacket = new BlockEventPacket();
            blockEventPacket.x = (int) chest.getX();
            blockEventPacket.y = (int) chest.getY();
            blockEventPacket.z = (int) chest.getZ();
            blockEventPacket.eventType = 1;
            blockEventPacket.eventData = 0;

            broadcastLid(chest, who, blockEventPacket, LevelSoundEventPacket.SOUND_ENDERCHEST_CLOSED);

            who.setViewingEnderChest(null);
        }

        super.onClose(who);
    }

    /**
     * The lid animation belongs to the ender chest block, not to the player who opened it.
     * Addressing the chunk of the holder sent the close event wherever the player stood at
     * the moment the window closed: a player teleported with the chest open (respawn,
     * plugin teleport, level change) closes the window after the move, so the event went to
     * the destination chunk and the lid stayed open for everyone around the chest.
     */
    private void broadcastLid(BlockEnderChest chest, Player who, BlockEventPacket packet, int sound) {
        Level level = chest.getLevel() != null ? chest.getLevel() : who.getLevel();
        if (level == null) {
            return;
        }
        level.addLevelSoundEvent(chest.add(0.5, 0.5, 0.5), sound);
        level.addChunkPacket(chest.getFloorX() >> 4, chest.getFloorZ() >> 4, packet);
    }
}
