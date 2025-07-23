package cn.nukkit.level.particle;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * Created on 2015/11/21 by xtypr.
 * Package cn.nukkit.level.particle in project Nukkit .
 */
public class DestroyBlockParticle extends Particle {

    protected final Block block;

    public DestroyBlockParticle(Vector3 pos, Block block) {
        super(pos.x, pos.y, pos.z);
        this.block = block;
    }

    @Override
    public DataPacket[] mvEncode(GameVersion protocol) {
        LevelEventPacket packet = new LevelEventPacket();
        packet.evid = LevelEventPacket.EVENT_PARTICLE_DESTROY;
        packet.x = (float) this.x;
        packet.y = (float) this.y;
        packet.z = (float) this.z;
        packet.data = protocol.getProtocol() <= ProtocolInfo.v1_2_10 ? (block.getId() | (block.getDamage() << 8)) : GlobalBlockPalette.getOrCreateRuntimeId(protocol, block.getId(), block.getDamage());
        packet.protocol = protocol.getProtocol();
        packet.gameVersion = protocol;
        packet.tryEncode();
        return new DataPacket[]{packet};
    }
}
