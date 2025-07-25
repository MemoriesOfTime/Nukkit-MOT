package cn.nukkit.level.particle;

import cn.nukkit.GameVersion;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.utils.BlockColor;

/**
 * Created on 2015/12/27 by xtypr.
 * Package cn.nukkit.level.particle in project Nukkit .
 * The name "spell" comes from minecraft wiki.
 */
public class SpellParticle extends Particle {

    protected final int data;

    public SpellParticle(Vector3 pos) {
        this(pos, 0);
    }

    public SpellParticle(Vector3 pos, int data) {
        super(pos.x, pos.y, pos.z);
        this.data = data;
    }

    public SpellParticle(Vector3 pos, BlockColor blockColor) {
        //alpha is ignored
        this(pos, blockColor.getRed(), blockColor.getGreen(), blockColor.getBlue());
    }

    public SpellParticle(Vector3 pos, int r, int g, int b) {
        this(pos, r, g, b, 0x00);
    }

    protected SpellParticle(Vector3 pos, int r, int g, int b, int a) {
        this(pos, ((a & 0xff) << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff));
    }

    @Override
    public DataPacket[] mvEncode(GameVersion protocol) {
        LevelEventPacket packet = new LevelEventPacket();
        packet.evid = LevelEventPacket.EVENT_PARTICLE_SPLASH;
        packet.x = (float) this.x;
        packet.y = (float) this.y;
        packet.z = (float) this.z;
        packet.data = this.data;
        packet.protocol = protocol.getProtocol();
        packet.gameVersion = protocol;
        packet.tryEncode();
        return new DataPacket[]{packet};
    }
}
