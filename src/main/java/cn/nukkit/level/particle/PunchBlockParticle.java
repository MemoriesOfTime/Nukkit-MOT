package cn.nukkit.level.particle;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class PunchBlockParticle extends Particle {

    // BlockFace index order: DOWN(0) UP(1) NORTH(2) SOUTH(3) WEST(4) EAST(5)
    // 方块朝向索引顺序: 下(0) 上(1) 北(2) 南(3) 西(4) 东(5)
    private static final int[] BREAK_BLOCK_EVENTS = {
            LevelEventPacket.EVENT_PARTICLE_BREAK_BLOCK_DOWN,
            LevelEventPacket.EVENT_PARTICLE_BREAK_BLOCK_UP,
            LevelEventPacket.EVENT_PARTICLE_BREAK_BLOCK_NORTH,
            LevelEventPacket.EVENT_PARTICLE_BREAK_BLOCK_SOUTH,
            LevelEventPacket.EVENT_PARTICLE_BREAK_BLOCK_WEST,
            LevelEventPacket.EVENT_PARTICLE_BREAK_BLOCK_EAST
    };

    protected final int blockId;
    protected final int blockDamage;
    protected final int face;

    public PunchBlockParticle(Vector3 pos, Block block, BlockFace face) {
        this(pos, block.getId(), block.getDamage(), face);
    }

    public PunchBlockParticle(Vector3 pos, int blockId, int blockDamage, BlockFace face) {
        super(pos.x, pos.y, pos.z);
        this.blockId = blockId;
        this.blockDamage = blockDamage;
        this.face = face.getIndex();
    }

    @Override
    public DataPacket[] mvEncode(GameVersion protocol) {
        LevelEventPacket packet = new LevelEventPacket();
        packet.x = (float) this.x;
        packet.y = (float) this.y;
        packet.z = (float) this.z;
        if (protocol.getProtocol() >= ProtocolInfo.v1_19_80) {
            packet.evid = BREAK_BLOCK_EVENTS[this.face];
            packet.data = GlobalBlockPalette.getOrCreateRuntimeId(protocol, blockId, blockDamage);
        } else {
            packet.evid = LevelEventPacket.EVENT_PARTICLE_PUNCH_BLOCK;
            if (protocol.getProtocol() <= ProtocolInfo.v1_2_10) {
                packet.data = blockId | (blockDamage << 8) | (face << 16);
            } else {
                packet.data = GlobalBlockPalette.getOrCreateRuntimeId(protocol, blockId, blockDamage) | (face << 24);
            }
        }
        packet.protocol = protocol.getProtocol();
        packet.gameVersion = protocol;
        packet.tryEncode();
        return new DataPacket[]{packet};
    }
}
