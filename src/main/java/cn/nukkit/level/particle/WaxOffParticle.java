package cn.nukkit.level.particle;

import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;

public class WaxOffParticle extends GenericParticle {
    public WaxOffParticle(Vector3 pos) {
        super(pos, Particle.TYPE_WAX);
    }

    @Override
    public DataPacket[] mvEncode(int protocol) {
        LevelEventPacket pk = new LevelEventPacket();
        pk.evid = LevelEventPacket.EVENT_PARTICLE_WAX_OFF;
        pk.x = (float) this.x;
        pk.y = (float) this.y;
        pk.z = (float) this.z;
        pk.data = this.data;
        pk.protocol = protocol;
        pk.tryEncode();
        return new DataPacket[]{pk};
    }
}