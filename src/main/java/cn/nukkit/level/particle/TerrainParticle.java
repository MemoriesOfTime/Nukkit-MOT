package cn.nukkit.level.particle;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;

/**
 * Created on 2015/11/21 by xtypr.
 * Package cn.nukkit.level.particle in project Nukkit .
 */
public class TerrainParticle extends Particle {

    protected final int blockId;
    protected final int blockDamage;

    public TerrainParticle(Vector3 pos, Block block) {
        this(pos, block.getId(), block.getDamage());
    }

    public TerrainParticle(Vector3 pos, int blockId, int blockDamage) {
        super(pos.x, pos.y, pos.z);
        this.blockId = blockId;
        this.blockDamage = blockDamage;
    }

    @Override
    public DataPacket[] mvEncode(GameVersion protocol) {
        LevelEventPacket packet = new LevelEventPacket();
        packet.evid = (short) (LevelEventPacket.EVENT_ADD_PARTICLE_MASK | getMultiversionId(protocol, Particle.TYPE_TERRAIN));
        packet.x = (float) this.x;
        packet.y = (float) this.y;
        packet.z = (float) this.z;
        packet.data = GlobalBlockPalette.getOrCreateRuntimeId(protocol, blockId, blockDamage);
        packet.protocol = protocol.getProtocol();
        packet.gameVersion = protocol;
        packet.tryEncode();
        return new DataPacket[]{packet};
    }
}
