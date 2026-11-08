package cn.nukkit.level.particle;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSpear;
import cn.nukkit.item.ItemSpearGold;
import cn.nukkit.item.StringItemBase;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParticleTest {

    @BeforeAll
    static void init() { MockServer.init(); }

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

    static Stream<GameVersion> clientWindow() {
        return Arrays.stream(GameVersion.values()).filter(v -> v.getProtocol() >= 589);
    }

    @ParameterizedTest
    @MethodSource("clientWindow")
    void goldenSpearUsesRecipientPaletteWithoutChangingOriginal(GameVersion version) {
        Item spear = new ItemSpearGold();
        spear.setDamage(31);
        spear.setNamedTag(new CompoundTag().putString("test_origin", "unchanged"));
        byte[] originalNbt = spear.getCompoundTag().clone();
        boolean supported = spear.isSupportedOn(version);
        int expected = supported ? (spear.getNetworkId(version) << 16 | 31)
                : Item.get(Item.INFO_UPDATE).getNetworkId(version) << 16;

        LevelEventPacket packet = encode(spear, version);

        assertEquals(expected, packet.data);
        assertEquals(version, packet.gameVersion);
        assertEquals(version.getProtocol(), packet.protocol);
        assertEquals(31, spear.getDamage());
        assertArrayEquals(originalNbt, spear.getCompoundTag());
        if (supported) assertEquals(expected, spear.getNetworkId(version) << 16 | 31);
        else assertTrue(cn.nukkit.item.RuntimeItems.getMapping(version)
                        .getNetworkIdByNamespaceId(spear.getNamespaceId()).isEmpty(),
                "particle fallback must not install a fake global mapping");
    }

    @ParameterizedTest
    @MethodSource("clientWindow")
    void existingVanillaParticleRetainsEncodedPayload(GameVersion version) {
        Item sword = Item.get(Item.DIAMOND_SWORD, 123, 1);
        LevelEventPacket packet = encode(sword, version);
        assertEquals(sword.getNetworkId(version) << 16 | 123, packet.data);
        LevelEventPacket expected = new LevelEventPacket();
        expected.protocol = version.getProtocol();
        expected.gameVersion = version;
        expected.evid = packet.evid;
        expected.x = 1; expected.y = 2; expected.z = 3;
        expected.data = sword.getNetworkId(version) << 16 | 123;
        expected.tryEncode();
        assertArrayEquals(expected.getBuffer(), packet.getBuffer());
    }

    @Test
    void unregisteredStringItemAlsoUsesSafeRecipientFallback() {
        Item unknown = new StringItemBase("test:unregistered_particle_item", "Unknown") {};
        assertTrue(unknown.isSupportedOn(GameVersion.V1_26_45));
        LevelEventPacket packet = encode(unknown, GameVersion.V1_26_45);
        assertEquals(Item.get(Item.INFO_UPDATE).getNetworkId(GameVersion.V1_26_45) << 16, packet.data);
    }

    @Test
    void breakingSpearRemovesItEvenWhenAnOldClientSeesTheParticle() throws Exception {
        Player player = mock(Player.class);
        Level level = mock(Level.class);
        PlayerInventory inventory = new PlayerInventory(player);
        when(player.getLevel()).thenReturn(level);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_26_45);
        ItemSpearGold spear = new ItemSpearGold();
        spear.setDamage(spear.getMaxDurability() - 1);
        inventory.setItemInHand(spear);
        doAnswer(call -> {
            Particle particle = call.getArgument(0);
            for (GameVersion version : clientWindow().toList()) assertDoesNotThrow(() -> particle.mvEncode(version));
            return null;
        }).when(level).addParticle(any(Particle.class));
        Method damage = ItemSpear.class.getDeclaredMethod("damageSpear", Player.class);
        damage.setAccessible(true);

        assertDoesNotThrow(() -> damage.invoke(spear, player));

        assertTrue(inventory.getItemInHand().isNull(), "broken spear must leave the hand after particles are sent");
        verify(level).addParticle(any(ItemBreakParticle.class));
    }

    private static LevelEventPacket encode(Item item, GameVersion version) {
        DataPacket[] packets = new ItemBreakParticle(new Vector3(1, 2, 3), item).mvEncode(version);
        assertEquals(1, packets.length);
        return assertInstanceOf(LevelEventPacket.class, packets[0]);
    }
}
