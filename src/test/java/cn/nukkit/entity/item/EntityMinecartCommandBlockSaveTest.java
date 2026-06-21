package cn.nukkit.entity.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.inventory.CommandBlockMinecartInventory;
import cn.nukkit.level.Level;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.CommandBlockUpdatePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.PacketBridgeUtil;
import cn.nukkit.network.protocol.regression.ProtocolCodecMapping;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies that the command block minecart persists {@code command} and
 * {@code customName} across a {@code saveNBT()} -> reconstruct -> {@code initEntity()}
 * round-trip.
 * <p>
 * Regression guard for the "command lost after reload" bug. The root cause was a
 * Java field-initialization-order trap: {@link Entity}'s constructor calls
 * {@code init() -> initEntity()} up the {@code super()}} chain, which runs
 * <em>before</em> the subclass's field initializers (e.g. {@code command = ""}).
 * Those initializers therefore executed <em>after</em> {@code initEntity()} had
 * loaded the persisted values from NBT, silently clobbering them with defaults.
 */
public class EntityMinecartCommandBlockSaveTest {

    private static Server serverMock;

    @BeforeAll
    static void init() throws Exception {
        MockServer.init();
        serverMock = MockServer.get();
    }

    private static Level newMockLevel() {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(serverMock);
        lenient().when(serverMock.getTick()).thenReturn(0);
        lenient().doNothing().when(level).addEntity(org.mockito.ArgumentMatchers.any());
        try {
            Field f = Level.class.getDeclaredField("isBeingConverted");
            f.setAccessible(true);
            f.setBoolean(level, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return level;
    }

    private static FullChunk newMockChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().doNothing().when(chunk).addEntity(org.mockito.ArgumentMatchers.any());
        lenient().when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
    }

    @Test
    void commandSurvivesSaveLoadRoundTrip() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartCommandBlock original = new EntityMinecartCommandBlock(chunk, baseNbt());
        original.setCommand("say hello");
        original.setCustomName("MyName");

        // Simulate chunk save: serialize the entity's namedTag to disk form.
        original.saveNBT();

        // Emulate a true disk round-trip via a fresh NBT tree (the original and the
        // reloaded entity must not share the same mutable namedTag). The constructor
        // triggers init() -> initEntity(), the same path createEntity() takes on load.
        EntityMinecartCommandBlock reloaded = new EntityMinecartCommandBlock(chunk, cloneCompound(original.namedTag));

        assertEquals("say hello", reloaded.getCommand(),
                "Command must survive save/load round-trip");
    }

    @Test
    void customNameSurvivesSaveLoadRoundTrip() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartCommandBlock original = new EntityMinecartCommandBlock(chunk, baseNbt());
        original.setCustomName("MyName");

        original.saveNBT();

        EntityMinecartCommandBlock reloaded = new EntityMinecartCommandBlock(chunk, cloneCompound(original.namedTag));
        assertEquals("MyName", reloaded.getName(),
                "CommandSender custom name must survive save/load round-trip");
    }

    @Test
    void metadataCarriesCommandAfterLoad() {
        // Regression for "command executes but minecart GUI shows it blank": the
        // editor reads the command from entity metadata index 71
        // (DATA_COMMAND_BLOCK_COMMAND), not block-entity NBT. initEntity() must write it.
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        EntityMinecartCommandBlock original = new EntityMinecartCommandBlock(chunk, baseNbt());
        original.setCommand("say hello");
        original.saveNBT();

        EntityMinecartCommandBlock reloaded = new EntityMinecartCommandBlock(chunk, cloneCompound(original.namedTag));

        // Entity.DATA_COMMAND_BLOCK_COMMAND = 71 = Bedrock COMMAND_BLOCK_NAME (modern)
        assertEquals("say hello", reloaded.getDataPropertyString(71),
                "COMMAND_BLOCK_NAME (index 71) must carry the loaded command so the client GUI can show it");
    }

    @Test
    void addEntityPacketForV126CarriesCommandMetadataAfterLoad() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);

        TestableCommandBlockMinecart original = new TestableCommandBlockMinecart(chunk, baseNbt());
        original.setCommand("say hello");
        original.saveNBT();

        TestableCommandBlockMinecart reloaded = new TestableCommandBlockMinecart(chunk, cloneCompound(original.namedTag));
        DataPacket packet = reloaded.createAddEntityPacketForTest();
        int protocol = ProtocolInfo.v1_26_20;
        GameVersion gameVersion = GameVersion.byProtocol(protocol, false);
        packet.protocol = protocol;
        packet.gameVersion = gameVersion;
        packet.encode();

        org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket decoded = decodeAddEntity(packet, gameVersion);

        assertEquals("minecraft:command_block_minecart", decoded.getIdentifier());
        assertEquals("say hello", decoded.getMetadata().get(EntityDataTypes.COMMAND_BLOCK_NAME),
                "1.26 clients reopen the minecart editor from AddEntity COMMAND_BLOCK_NAME metadata");
        assertEquals(Boolean.TRUE, decoded.getMetadata().get(EntityDataTypes.COMMAND_BLOCK_ENABLED),
                "Command block minecart metadata must mark command-block editing as enabled");
    }

    private static org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket decodeAddEntity(
            DataPacket packet, GameVersion gameVersion) {
        BedrockCodec codec = ProtocolCodecMapping.getCodec(packet.protocol);
        BedrockCodecHelper helper = codec.createHelper();
        int commandBlockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(gameVersion, Block.COMMAND_BLOCK, 0);
        BlockDefinition commandBlock = new SimpleBlockDefinition("minecraft:command_block",
                commandBlockRuntimeId, NbtMap.EMPTY);
        helper.setBlockDefinitions(SimpleDefinitionRegistry.<BlockDefinition>builder().add(commandBlock).build());

        ByteBuf buf = PacketBridgeUtil.nukkitPacketToByteBuf(packet);
        try {
            org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket decoded =
                    new org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket();
            codec.getPacketDefinition(org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket.class)
                    .getSerializer().deserialize(buf, helper, decoded);
            assertEquals(0, buf.readableBytes(), "AddEntityPacket payload should be fully decoded");
            return decoded;
        } finally {
            buf.release();
        }
    }

    private static CompoundTag cloneCompound(CompoundTag tag) {
        // Round-trip through serialization to avoid the original and reloaded entities
        // sharing the same mutable namedTag tree (emulates a real disk save/load).
        try {
            byte[] bytes = cn.nukkit.nbt.NBTIO.write(tag, java.nio.ByteOrder.LITTLE_ENDIAN);
            return cn.nukkit.nbt.NBTIO.read(bytes, java.nio.ByteOrder.LITTLE_ENDIAN);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class TestableCommandBlockMinecart extends EntityMinecartCommandBlock {
        private TestableCommandBlockMinecart(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }

        private DataPacket createAddEntityPacketForTest() {
            return super.createAddEntityPacket();
        }
    }
}
