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

    protected final int blockId;
    protected final int blockDamage;
    protected final int index;
    protected final int face;

    public PunchBlockParticle(Vector3 pos, Block block, BlockFace face) {
        this(pos, block.getId(), block.getDamage(), face);
    }

    public PunchBlockParticle(Vector3 pos, int blockId, int blockDamage, BlockFace face) {
        super(pos.x, pos.y, pos.z);
        this.blockId = blockId;
        this.blockDamage = blockDamage;
        this.face = face.getIndex();
        this.index = this.face << 24;
    }

    @Override
    public DataPacket[] mvEncode(GameVersion protocol) {
        LevelEventPacket packet = new LevelEventPacket();
        packet.evid = LevelEventPacket.EVENT_PARTICLE_PUNCH_BLOCK;
        packet.x = (float) this.x;
        packet.y = (float) this.y;
        packet.z = (float) this.z;
        packet.data = protocol.getProtocol() <= ProtocolInfo.v1_2_10 ? (blockId | (blockDamage << 8) | (face << 16)) : GlobalBlockPalette.getOrCreateRuntimeId(protocol, blockId, blockDamage) | index;
        packet.protocol = protocol.getProtocol();
        packet.gameVersion = protocol;
        packet.tryEncode();
        return new DataPacket[]{packet};
    }
}
