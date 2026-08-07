package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCommandBlock;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCommandBlock;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityMinecartCommandBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.CommandBlockUpdatePacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommandBlockUpdateProcessor extends DataPacketProcessor<CommandBlockUpdatePacket> {

    public static final CommandBlockUpdateProcessor INSTANCE = new CommandBlockUpdateProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull CommandBlockUpdatePacket pk) {
        Player player = playerHandle.player;
        if (!player.isOp() || !player.isCreative()) {
            return;
        }
        if (!pk.isBlock) {
            // Command block minecart: only command/name/track-output apply (no mode/conditional/redstone).
            Entity entity = player.getLevel().getEntity(pk.minecartEid);
            if (!(entity instanceof EntityMinecartCommandBlock minecart)) {
                return;
            }
            if (entity.distanceSquared(player) > 10000) {
                return;
            }
            minecart.setCommand(pk.command);
            minecart.setCustomName(pk.name);
            minecart.setTrackOutput(pk.shouldTrackOutput);
            return;
        }

        Vector3 pos = new Vector3(pk.x, pk.y, pk.z);
        if (pos.distanceSquared(player) > 10000) {
            return;
        }

        Level level = player.getLevel();
        Block block = level.getBlock(pos);
        if (!(block instanceof BlockCommandBlock cmdBlock)) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BlockEntityCommandBlock commandBlock)) {
            return;
        }

        int requestedMode = pk.commandBlockMode;
        int targetBlockId = switch (requestedMode) {
            case 1 -> BlockCommandBlock.REPEATING_COMMAND_BLOCK;
            case 2 -> BlockCommandBlock.CHAIN_COMMAND_BLOCK;
            default -> BlockCommandBlock.COMMAND_BLOCK;
        };

        if (block.getId() != targetBlockId) {
            Block newBlock = Block.get(targetBlockId, block.getDamage());
            level.setBlock(block, newBlock, true, false);
            cmdBlock = (BlockCommandBlock) newBlock;
            be = level.getBlockEntity(pos);
            if (be instanceof BlockEntityCommandBlock cb) {
                commandBlock = cb;
                if (requestedMode == 1) {
                    commandBlock.scheduleUpdate();
                }
            } else {
                return;
            }
        }

        cmdBlock.setConditionalBit(pk.isConditional);
        level.setBlock(pos, cmdBlock, true, false);

        commandBlock.setCommand(pk.command);
        commandBlock.setName(pk.name);
        commandBlock.setTrackOutput(pk.shouldTrackOutput);
        commandBlock.setConditional(pk.isConditional);
        commandBlock.setTickDelay(pk.tickDelay);
        commandBlock.setExecutingOnFirstTick(pk.executingOnFirstTick);

        boolean isRedstoneMode = pk.isRedstoneMode;
        commandBlock.setAuto(!isRedstoneMode);

        if (!isRedstoneMode && requestedMode == 0) {
            commandBlock.trigger();
        }

        for (BlockFace face : BlockFace.values()) {
            level.updateAroundRedstone(block.getSide(face), face.getOpposite());
        }

        commandBlock.spawnToAll();
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.COMMAND_BLOCK_UPDATE_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return CommandBlockUpdatePacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return true;
    }
}
