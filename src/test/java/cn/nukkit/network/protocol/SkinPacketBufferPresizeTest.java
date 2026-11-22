package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.PersonaPiece;
import cn.nukkit.utils.PersonaPieceTint;
import cn.nukkit.utils.SerializedImage;
import cn.nukkit.utils.SkinAnimation;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkinPacketBufferPresizeTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @AfterAll
    static void cleanup() {
        MockServer.reset();
    }

    @Test
    void estimatedSkinSizeCoversEncodedSkinAcrossProtocolWindow() {
        Skin richSkin = createRich256Skin();
        GameVersion[] versions = {
                GameVersion.V1_12_0,
                GameVersion.V1_16_100,
                GameVersion.V1_20_0,
                GameVersion.V1_21_50_NETEASE,
                GameVersion.V1_26_40,
                GameVersion.V1_26_50,
                GameVersion.V1_26_60
        };

        for (GameVersion gv : versions) {
            BinaryStream probe = new BinaryStream();
            probe.putSkin(gv, richSkin);
            int actual = probe.getCount();
            int estimated = BinaryStream.estimatedSkinSize(gv, richSkin);
            assertTrue(estimated >= actual,
                    "Estimate must cover encoded skin for " + gv + ": est=" + estimated + " actual=" + actual);
            assertTrue(estimated <= actual + 4096,
                    "Estimate should stay tight for " + gv + ": est=" + estimated + " actual=" + actual);
        }
    }

    @Test
    void playerListAndPlayerSkinPacketsPreSizeWithoutGeometricDoubling() {
        Skin richSkin = createRich256Skin();
        PlayerListPacket.Entry[] entries = new PlayerListPacket.Entry[16];
        for (int i = 0; i < entries.length; i++) {
            PlayerListPacket.Entry e = new PlayerListPacket.Entry(
                    new UUID(0x123456789ABCDEF0L + i, 0x0FEDCBAL + i),
                    1000L + i,
                    "Player_" + i,
                    richSkin,
                    "25354123456789" + i
            );
            e.platformChatId = "platform_" + i;
            e.buildPlatform = 7;
            e.color = Color.WHITE;
            entries[i] = e;
        }

        GameVersion[] versions = {
                GameVersion.V1_12_0,
                GameVersion.V1_20_0,
                GameVersion.V1_21_50_NETEASE,
                GameVersion.V1_26_40,
                GameVersion.V1_26_50,
                GameVersion.V1_26_60
        };

        for (GameVersion gv : versions) {
            PlayerListPacket list = new PlayerListPacket();
            list.type = PlayerListPacket.TYPE_ADD;
            list.entries = entries;
            list.gameVersion = gv;
            list.protocol = gv.getProtocol();
            list.encode();

            int count = list.getCount();
            int capacity = list.getBufferUnsafe().length;
            assertTrue(capacity >= count);
            assertTrue(capacity <= count + 16_384,
                    "PlayerListPacket buffer should not geometrically double for " + gv + ": cap=" + capacity + " count=" + count);

            PlayerSkinPacket skinPk = new PlayerSkinPacket();
            skinPk.uuid = entries[0].uuid;
            skinPk.skin = richSkin;
            skinPk.newSkinName = "new_skin";
            skinPk.oldSkinName = "old_skin";
            skinPk.gameVersion = gv;
            skinPk.protocol = gv.getProtocol();
            skinPk.encode();
            assertTrue(skinPk.getBufferUnsafe().length >= skinPk.getCount());
            assertTrue(skinPk.getBufferUnsafe().length <= skinPk.getCount() + 4096,
                    "PlayerSkinPacket buffer should be tightly pre-sized for " + gv);
        }

        // Measure allocation during multi-entry 256x256 PlayerListPacket (~4.5 MB wire payload).
        // Geometric growth from 32 B previously allocated >8.3 MB of intermediate byte[] copies + 8.3 MB final array (>16.6 MB total).
        for (int i = 0; i < 3; i++) {
            PlayerListPacket warm = new PlayerListPacket();
            warm.type = PlayerListPacket.TYPE_ADD;
            warm.entries = entries;
            warm.gameVersion = GameVersion.V1_26_60;
            warm.protocol = GameVersion.V1_26_60.getProtocol();
            warm.encode();
        }

        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        PlayerListPacket measured = new PlayerListPacket();
        measured.type = PlayerListPacket.TYPE_ADD;
        measured.entries = entries;
        measured.gameVersion = GameVersion.V1_26_60;
        measured.protocol = GameVersion.V1_26_60.getProtocol();
        measured.encode();
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        int payloadSize = measured.getCount();

        assertTrue(allocated < payloadSize + 262_144L,
                "Expected single-pass allocation (~" + payloadSize + " B), got " + allocated + " B");
    }

    private static Skin createRich256Skin() {
        Skin skin = new Skin();
        skin.setSkinId("custom.hd.256");
        skin.setPlayFabId("pf-987654321");
        skin.setSkinResourcePatch("{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}");
        skin.setSkinData(new SerializedImage(256, 256, new byte[256 * 256 * 4]));
        skin.setCapeData(new SerializedImage(64, 32, new byte[64 * 32 * 4]));
        skin.setGeometryData("{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.humanoid.custom\"}}]}");
        skin.setGeometryDataEngineVersion("1.26.60");
        skin.setAnimationData("{}");
        skin.setCapeId("cape_1");
        skin.setFullSkinId("custom.hd.256_cape_1");
        skin.setArmSize("wide");
        skin.setSkinColor("#b37b62");
        skin.setPersona(true);
        skin.setTrusted(true);
        skin.getAnimations().add(new SkinAnimation(new SerializedImage(32, 32, new byte[32 * 32 * 4]), 1, 2.0f, 0));
        skin.getPersonaPieces().add(new PersonaPiece("piece_1", "persona_body", UUID.randomUUID().toString(), true, "prod_1"));
        skin.getTintColors().add(new PersonaPieceTint("persona_body", List.of("#112233", "#445566", "#778899", "#aabbcc")));
        return skin;
    }
}
