package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockStructureBlock;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityStructureBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.StructureBlockUpdatePacket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * 处理结构方块界面提交的设置，并在按钮触发时执行保存/加载。
 * <p>
 * Applies structure block editor settings and runs save/load when the button triggered them.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StructureBlockUpdateProcessor extends DataPacketProcessor<StructureBlockUpdatePacket> {

    public static final StructureBlockUpdateProcessor INSTANCE = new StructureBlockUpdateProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull StructureBlockUpdatePacket pk) {
        Player player = playerHandle.player;
        if (!player.isOp() || !player.isCreative()) {
            return;
        }
        Vector3 pos = new Vector3(pk.x, pk.y, pk.z);
        if (pos.distanceSquared(player) > 10000) {
            return;
        }
        Level level = player.getLevel();
        Block block = level.getBlock(pos);
        if (!(block instanceof BlockStructureBlock structureBlock)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BlockEntityStructureBlock structure)) {
            return;
        }
        structure.updateFromPacket(pk, player);
        if (structure.closed || level.getBlockEntity(pos) != structure) {
            return;
        }
        if (pk.type >= BlockStructureBlock.TYPE_DATA && pk.type <= BlockStructureBlock.TYPE_EXPORT
                && structureBlock.getStructureBlockType() != pk.type) {
            structureBlock.setStructureBlockType(pk.type);
            level.setBlock(block, structureBlock, true, false);
        }
        structure.spawnToAll();
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.STRUCTURE_BLOCK_UPDATE_PACKET;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return StructureBlockUpdatePacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return true;
    }
}
