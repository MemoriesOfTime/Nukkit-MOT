package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.BlockStructureBlock;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityStructureBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.structure.*;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.StructureTemplateDataRequestPacket;
import cn.nukkit.network.protocol.StructureTemplateDataResponsePacket;
import cn.nukkit.network.protocol.types.structure.StructureSettings;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * 响应客户端的结构导出/查询请求。
 * <p>
 * Answers the client's structure export and query requests.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StructureTemplateDataRequestProcessor extends DataPacketProcessor<StructureTemplateDataRequestPacket> {

    public static final StructureTemplateDataRequestProcessor INSTANCE = new StructureTemplateDataRequestProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull StructureTemplateDataRequestPacket pk) {
        Player player = playerHandle.player;
        if (!player.isOp() || !player.isCreative()) {
            return;
        }
        Vector3 pos = new Vector3(pk.x, pk.y, pk.z);
        if (pos.distanceSquared(player) > 10000) {
            return;
        }
        Level level = player.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BlockEntityStructureBlock structure)) {
            return;
        }

        CompoundTag tag = null;
        if (pk.operation == StructureTemplateDataRequestPacket.OPERATION_EXPORT_FROM_SAVED_MODE) {
            tag = captureFromRequest(level, structure, pk);
        } else if (pk.operation == StructureTemplateDataRequestPacket.OPERATION_EXPORT_FROM_LOAD_MODE
                || pk.operation == StructureTemplateDataRequestPacket.OPERATION_QUERY_SAVED_STRUCTURE) {
            tag = loadRequested(structure, pk.name);
        }

        StructureTemplateDataResponsePacket response = new StructureTemplateDataResponsePacket();
        response.name = pk.name;
        response.save = tag != null;
        response.tag = tag;
        response.type = switch (pk.operation) {
            case StructureTemplateDataRequestPacket.OPERATION_EXPORT_FROM_SAVED_MODE,
                 StructureTemplateDataRequestPacket.OPERATION_EXPORT_FROM_LOAD_MODE ->
                    StructureTemplateDataResponsePacket.RESPONSE_EXPORT;
            case StructureTemplateDataRequestPacket.OPERATION_QUERY_SAVED_STRUCTURE ->
                    StructureTemplateDataResponsePacket.RESPONSE_QUERY;
            default -> StructureTemplateDataResponsePacket.RESPONSE_NONE;
        };
        player.dataPacket(response);
    }

    /**
     * 查询/加载模式导出必须按请求里的名字查，而不是方块实体现存的名字：
     * 客户端每次打开界面或改名都会用新名字发 QUERY，用它来决定加载按钮是否可用。
     * <p>
     * Query/load-mode exports must use the requested name, not the block entity's stored
     * name: the client sends a QUERY on open and on every rename, and enables the Load
     * button from that response.
     */
    private static CompoundTag loadRequested(BlockEntityStructureBlock structure, String name) {
        String requested = name == null || name.isEmpty() ? structure.getStructureName() : name;
        Structure saved = StructureAPI.load(requested);
        return saved == null ? null : StructureIO.toNbt(saved);
    }

    /**
     * 保存模式的导出以请求携带的编辑器设置抓取当前区域。
     * <p>
     * Save-mode export captures the current region using the editor settings carried by the request.
     */
    private static CompoundTag captureFromRequest(Level level, BlockEntityStructureBlock structure,
                                                  StructureTemplateDataRequestPacket pk) {
        StructureSettings settings = pk.settings;
        int sizeX = clamp(settings.sizeX, 1, StructureIO.MAX_SIZE_X);
        int sizeY = clamp(settings.sizeY, 1, StructureIO.MAX_SIZE_Y);
        int sizeZ = clamp(settings.sizeZ, 1, StructureIO.MAX_SIZE_Z);
        int offsetX = clamp(settings.offsetX, -StructureIO.MAX_SIZE_Y, StructureIO.MAX_SIZE_Y);
        int offsetY = clamp(settings.offsetY, -StructureIO.MAX_SIZE_Y, StructureIO.MAX_SIZE_Y);
        int offsetZ = clamp(settings.offsetZ, -StructureIO.MAX_SIZE_Y, StructureIO.MAX_SIZE_Y);
        if (structure.getStructureType() == BlockStructureBlock.TYPE_SAVE && !pk.name.isEmpty()) {
            // 有角落方块时以角落定义的范围为准（与原版 DETECT 一致）
            // A same-named corner block wins over the request box, matching vanilla detect.
            int[] detected = structure.detectBox(pk.name);
            if (detected != null) {
                offsetX = detected[0];
                offsetY = detected[1];
                offsetZ = detected[2];
                sizeX = detected[3];
                sizeY = detected[4];
                sizeZ = detected[5];
            }
        }
        return StructureIO.toNbt(StructureCapture.capture(level,
                new StructureBox(new BlockVector3(pk.x + offsetX, pk.y + offsetY, pk.z + offsetZ),
                        new BlockVector3(sizeX, sizeY, sizeZ)),
                !settings.ignoringEntities));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.STRUCTURE_TEMPLATE_DATA_EXPORT_REQUEST;
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return StructureTemplateDataRequestPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return true;
    }
}
