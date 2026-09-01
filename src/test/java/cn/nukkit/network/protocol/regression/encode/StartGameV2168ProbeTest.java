package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.level.GameRules;
import cn.nukkit.network.protocol.BiomeDefinitionListPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.StartGamePacket;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StartGameV2168ProbeTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    @Test
    void startGameV2168CrossDecodes() {
        var nukkitPacket = new StartGamePacket();
        nukkitPacket.protocol = ProtocolInfo.v1_26_40;
        nukkitPacket.gameVersion = GameVersion.byProtocol(ProtocolInfo.v1_26_40, false);
        nukkitPacket.entityUniqueId = 1;
        nukkitPacket.entityRuntimeId = 2;
        nukkitPacket.playerGamemode = 1;
        nukkitPacket.x = 0;
        nukkitPacket.y = 64;
        nukkitPacket.z = 0;
        nukkitPacket.pitch = 0;
        nukkitPacket.yaw = 0;
        nukkitPacket.seed = -1;
        nukkitPacket.dimension = 0;
        nukkitPacket.generator = 1;
        nukkitPacket.worldGamemode = 1;
        nukkitPacket.difficulty = 1;
        nukkitPacket.spawnX = 0;
        nukkitPacket.spawnY = 64;
        nukkitPacket.spawnZ = 0;
        nukkitPacket.hasAchievementsDisabled = true;
        nukkitPacket.dayCycleStopTime = -1;
        nukkitPacket.eduMode = false;
        nukkitPacket.commandsEnabled = true;
        nukkitPacket.isTexturePacksRequired = false;
        nukkitPacket.gameRules = new GameRules();
        nukkitPacket.levelId = "world-id";
        nukkitPacket.worldName = "world";
        nukkitPacket.premiumWorldTemplateId = "";

        nukkitPacket.encode();
        System.out.println("ENCODED_LEN=" + nukkitPacket.getBuffer().length);
        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.StartGamePacket.class);
        assertNotNull(cbPacket);
    }

    @Test
    void biomeDefinitionListV2168CrossDecodes() throws Exception {
        var nukkitPacket = new BiomeDefinitionListPacket();
        nukkitPacket.protocol = ProtocolInfo.v1_26_40;
        nukkitPacket.gameVersion = GameVersion.byProtocol(ProtocolInfo.v1_26_40, false);

        var gson = new com.google.gson.GsonBuilder()
                .registerTypeAdapter(java.awt.Color.class, new com.google.gson.TypeAdapter<java.awt.Color>() {
                    @Override
                    public void write(com.google.gson.stream.JsonWriter out, java.awt.Color color) {
                    }

                    @Override
                    public java.awt.Color read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                            in.nextNull();
                            return null;
                        }
                        int r = 0, g = 0, b = 0, a = 255;
                        in.beginObject();
                        while (in.hasNext()) {
                            switch (in.nextName()) {
                                case "r": r = in.nextInt(); break;
                                case "g": g = in.nextInt(); break;
                                case "b": b = in.nextInt(); break;
                                case "a": a = in.nextInt(); break;
                                default: in.skipValue(); break;
                            }
                        }
                        in.endObject();
                        return new java.awt.Color(r, g, b, a);
                    }
                })
                .registerTypeAdapter(cn.nukkit.block.Block.class, new com.google.gson.JsonDeserializer<cn.nukkit.block.Block>() {
                    @Override
                    public cn.nukkit.block.Block deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
                        com.google.gson.JsonObject obj = json.getAsJsonObject();
                        return cn.nukkit.block.Block.get(obj.get("id").getAsInt(), obj.get("meta").getAsInt());
                    }
                })
                .create();
        var definitions = gson.fromJson(
                cn.nukkit.utils.Utils.loadJsonResource("biome/stripped_biome_definitions_844.json"),
                new com.google.gson.reflect.TypeToken<java.util.LinkedHashMap<String, cn.nukkit.network.protocol.types.biome.BiomeDefinitionData>>() {}.getType());
        var field = BiomeDefinitionListPacket.class.getDeclaredField("biomeDefinitions");
        field.setAccessible(true);
        field.set(nukkitPacket, definitions);

        nukkitPacket.encode();
        System.out.println("BIOME_LEN=" + nukkitPacket.getBuffer().length);

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket.class);
        assertNotNull(cbPacket);
    }

    @Test
    void resourcePacksInfoV2168CrossDecodes() {
        var nukkitPacket = new cn.nukkit.network.protocol.ResourcePacksInfoPacket();
        nukkitPacket.protocol = ProtocolInfo.v1_26_40;
        nukkitPacket.gameVersion = GameVersion.byProtocol(ProtocolInfo.v1_26_40, false);
        cn.nukkit.resourcepacks.ResourcePack pack = new cn.nukkit.resourcepacks.ResourcePack() {
            @Override
            public String getPackName() { return "Test Pack"; }

            @Override
            public UUID getPackId() { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }

            @Override
            public String getPackVersion() { return "1.0.0"; }

            @Override
            public int getPackSize() { return 1024; }

            @Override
            public byte[] getSha256() { return new byte[32]; }

            @Override
            public byte[] getPackChunk(int off, int len) { return new byte[0]; }
        };
        nukkitPacket.resourcePackEntries = new cn.nukkit.resourcepacks.ResourcePack[]{pack};
        nukkitPacket.encode();
        System.out.println("RESPACKINFO_LEN=" + nukkitPacket.getBuffer().length);

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket.class);
        assertNotNull(cbPacket);
        assertEquals(1, cbPacket.getResourcePackInfos().size());
        assertEquals("1.0.0", cbPacket.getResourcePackInfos().get(0).getPackVersion());
    }

    @Test
    void resourcePackStackV2168CrossDecodes() {
        var nukkitPacket = new cn.nukkit.network.protocol.ResourcePackStackPacket();
        nukkitPacket.protocol = ProtocolInfo.v1_26_40;
        nukkitPacket.resourcePackStack = new cn.nukkit.resourcepacks.ResourcePack[]{
                new cn.nukkit.resourcepacks.ResourcePack() {
                    @Override
                    public String getPackName() { return "Test Pack"; }

                    @Override
                    public UUID getPackId() { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }

                    @Override
                    public String getPackVersion() { return "1.0.0"; }

                    @Override
                    public int getPackSize() { return 1024; }

                    @Override
                    public byte[] getSha256() { return new byte[32]; }

                    @Override
                    public byte[] getPackChunk(int off, int len) { return new byte[0]; }
                }
        };
        nukkitPacket.encode();
        System.out.println("RESPACKSTACK_LEN=" + nukkitPacket.getBuffer().length);

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket.class);
        assertNotNull(cbPacket);
    }

    @Test
    void gameRulesChangedV2168CrossDecodes() {
        var nukkitPacket = new cn.nukkit.network.protocol.GameRulesChangedPacket();
        nukkitPacket.protocol = ProtocolInfo.v1_26_40;
        nukkitPacket.gameVersion = GameVersion.byProtocol(ProtocolInfo.v1_26_40, false);
        Map<cn.nukkit.level.GameRule, GameRules.Value> gameRulesMap = new HashMap<>();
        gameRulesMap.put(cn.nukkit.level.GameRule.DO_DAYLIGHT_CYCLE, new GameRules.Value(GameRules.Type.BOOLEAN, false));
        gameRulesMap.put(cn.nukkit.level.GameRule.SHOW_COORDINATES, new GameRules.Value(GameRules.Type.BOOLEAN, true));
        nukkitPacket.gameRulesMap = gameRulesMap;
        nukkitPacket.encode();
        System.out.println("GAMERULES_LEN=" + nukkitPacket.getBuffer().length);

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket.class);
        assertNotNull(cbPacket);
        assertEquals(2, cbPacket.getGameRules().size());
    }

    @Test
    void availableEntityIdentifiersV2168CrossDecodes() {
        var nukkitPacket = new cn.nukkit.network.protocol.AvailableEntityIdentifiersPacket();
        nukkitPacket.protocol = ProtocolInfo.v1_26_40;
        nukkitPacket.gameVersion = GameVersion.byProtocol(ProtocolInfo.v1_26_40, false);
        nukkitPacket.encode();
        System.out.println("ENTITYIDS_LEN=" + nukkitPacket.getBuffer().length);

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket.class);
        assertNotNull(cbPacket);
    }
}
