package cn.nukkit.level.particle;

import cn.nukkit.GameVersion;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticleTest {

    @Test
    void netEaseParticleIdsAfterSneezeAreShifted() {
        assertEquals(Particle.TYPE_SNEEZE,
                Particle.getMultiversionId(GameVersion.V1_21_93, Particle.TYPE_SNEEZE));
        assertEquals(Particle.TYPE_SNEEZE,
                Particle.getMultiversionId(GameVersion.V1_21_93_NETEASE, Particle.TYPE_SNEEZE));

        assertEquals(Particle.TYPE_SHULKER_BULLET,
                Particle.getMultiversionId(GameVersion.V1_21_93, Particle.TYPE_SHULKER_BULLET));
        assertEquals(Particle.TYPE_SHULKER_BULLET + 1,
                Particle.getMultiversionId(GameVersion.V1_21_93_NETEASE, Particle.TYPE_SHULKER_BULLET));
    }

    @Test
    void netEaseParticleShiftUsesProtocolSpecificSneezeId() {
        assertEquals(Particle.TYPE_SHULKER_BULLET - 1,
                Particle.getMultiversionId(GameVersion.V1_20_50, Particle.TYPE_SHULKER_BULLET));
        assertEquals(Particle.TYPE_SHULKER_BULLET,
                Particle.getMultiversionId(GameVersion.V1_20_50_NETEASE, Particle.TYPE_SHULKER_BULLET));
    }

    @Test
    void unknownParticleIdsAreNotShiftedForNetEase() {
        assertEquals(-1, Particle.getMultiversionId(GameVersion.V1_21_93_NETEASE, -1));
        assertEquals(98, Particle.getMultiversionId(GameVersion.V1_21_93_NETEASE, 98));
        assertEquals(9999, Particle.getMultiversionId(GameVersion.V1_21_93_NETEASE, 9999));
    }

    @Test
    void genericParticleEncodesShiftedNetEaseLevelEventParticleId() {
        DataPacket[] packets = new GenericParticle(new Vector3(0, 0, 0), Particle.TYPE_SHULKER_BULLET)
                .mvEncode(GameVersion.V1_21_93_NETEASE);

        LevelEventPacket packet = (LevelEventPacket) packets[0];
        assertEquals(LevelEventPacket.EVENT_ADD_PARTICLE_MASK | (Particle.TYPE_SHULKER_BULLET + 1), packet.evid);
    }

    @Test
    void areaEffectCloudMetadataEncodesShiftedNetEaseParticleId() {
        EntityMetadata metadata = new EntityMetadata()
                .putInt(Entity.DATA_AREA_EFFECT_CLOUD_PARTICLE_ID, Particle.TYPE_SHULKER_BULLET);

        BinaryStream stream = new BinaryStream(Binary.writeMetadata(GameVersion.V1_21_93_NETEASE, metadata));

        assertEquals(1, stream.getUnsignedVarInt());
        assertEquals(Entity.DATA_AREA_EFFECT_CLOUD_PARTICLE_ID, stream.getUnsignedVarInt());
        assertEquals(Entity.DATA_TYPE_INT, stream.getUnsignedVarInt());
        assertEquals(Particle.TYPE_SHULKER_BULLET + 1, stream.getVarInt());
    }

    @Test
    void nonParticleIntMetadataIsNotShiftedForNetEase() {
        EntityMetadata metadata = new EntityMetadata()
                .putInt(Entity.DATA_AREA_EFFECT_CLOUD_DURATION, Particle.TYPE_SHULKER_BULLET);

        BinaryStream stream = new BinaryStream(Binary.writeMetadata(GameVersion.V1_21_93_NETEASE, metadata));

        assertEquals(1, stream.getUnsignedVarInt());
        assertEquals(Entity.DATA_AREA_EFFECT_CLOUD_DURATION, stream.getUnsignedVarInt());
        assertEquals(Entity.DATA_TYPE_INT, stream.getUnsignedVarInt());
        assertEquals(Particle.TYPE_SHULKER_BULLET, stream.getVarInt());
    }
}
