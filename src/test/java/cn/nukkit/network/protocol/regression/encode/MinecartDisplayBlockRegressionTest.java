package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.ByteEntityData;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.data.NBTEntityData;
import cn.nukkit.entity.data.StringEntityData;
import cn.nukkit.item.Item;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for minecart display block ({@link Entity#DATA_DISPLAY_ITEM})
 * wire-format encoding across protocol versions.
 * <p>
 * The display block is stored internally as a legacy block id packed as
 * {@code id | (meta << 16)}, but the client resolves entity data key 16 against
 * its per-protocol block palette (runtime/hashed block state id). This verifies
 * that {@link cn.nukkit.utils.Binary#writeMetadata} converts the legacy id to the
 * correct runtime id for each protocol version, mirroring CloudburstMC's
 * {@code BlockDefinitionTransformer}.
 */
public class MinecartDisplayBlockRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    static Stream<Arguments> versionsFrom291() {
        return filteredVersions(291);
    }

    @ParameterizedTest(name = "Minecart display block (command_block) v{0}")
    @MethodSource("versionsFrom291")
    void testMinecartDisplayBlockEncodesAsRuntimeId(int protocolVersion) {
        GameVersion gameVersion = GameVersion.byProtocol(protocolVersion, false);

        // Legacy command_block id = 137, packed as id | (meta << 16) — what the minecart stores.
        int legacyDisplay = Block.COMMAND_BLOCK | (0 << 16);
        int expectedRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(gameVersion, Block.COMMAND_BLOCK, 0);

        var nukkitPacket = new cn.nukkit.network.protocol.SetEntityDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = gameVersion;
        nukkitPacket.eid = 1;
        EntityMetadata metadata = new EntityMetadata();
        metadata.put(new ByteEntityData(Entity.DATA_HAS_DISPLAY, 1));
        metadata.put(new IntEntityData(Entity.DATA_DISPLAY_ITEM, legacyDisplay));
        nukkitPacket.metadata = metadata;
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.frame = 0L;
        }
        nukkitPacket.encode();

        // Register a BlockDefinition at the expected runtime id so CB can resolve it.
        SimpleBlockDefinition cmdBlock = new SimpleBlockDefinition("minecraft:command_block",
                expectedRuntimeId, NbtMap.EMPTY);
        SimpleDefinitionRegistry<BlockDefinition> blockDefs =
                SimpleDefinitionRegistry.<BlockDefinition>builder().add(cmdBlock).build();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket.class,
                helper -> helper.setBlockDefinitions(blockDefs));

        assertEquals(1, cbPacket.getRuntimeEntityId());
        var resolved = cbPacket.getMetadata().get(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.DISPLAY_BLOCK_STATE);
        assertNotNull(resolved, "DISPLAY_BLOCK_STATE should be present after decode");
        assertEquals(expectedRuntimeId, resolved.getRuntimeId(),
                "Client should receive command_block runtime id " + expectedRuntimeId
                        + " for protocol " + protocolVersion + " (was receiving legacy id " + Block.COMMAND_BLOCK + ")");
    }

    /**
     * Entity data key 16 ({@link Entity#DATA_DISPLAY_ITEM}) is reused: the minecart
     * stores it as an {@code IntEntityData} (block state, processed above), while
     * {@link cn.nukkit.entity.item.EntityFirework} stores it as an {@code NBTEntityData}
     * (the firework item). They are disambiguated by data type in {@link cn.nukkit.utils.Binary#writeMetadata},
     * so the runtime-id conversion above must only apply to the INT path. This test
     * locks that separation down: a firework in key 16 must survive round-trip as a
     * {@code DISPLAY_FIREWORK} NBT, never touched by the block-state conversion.
     */
    @ParameterizedTest(name = "Firework DISPLAY_FIREWORK (NBT) not affected by block fix v{0}")
    @MethodSource("versionsFrom291")
    void testFireworkDisplayItemIsNotConverted(int protocolVersion) {
        // >= v1_12_0 the wire format is NBT; the legacy-slot branch is only for very old protocols.
        // We only assert the modern NBT path here since the bug under test is about the INT path.
        if (protocolVersion < ProtocolInfo.v1_12_0) {
            return;
        }

        GameVersion gameVersion = GameVersion.byProtocol(protocolVersion, false);
        Item firework = Item.get(Item.FIREWORKS);

        var nukkitPacket = new cn.nukkit.network.protocol.SetEntityDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = gameVersion;
        nukkitPacket.eid = 1;
        EntityMetadata metadata = new EntityMetadata();
        metadata.put(new NBTEntityData(Entity.DATA_DISPLAY_ITEM, firework));
        nukkitPacket.metadata = metadata;
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.frame = 0L;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket.class);

        // The NBT key 16 must decode back as DISPLAY_FIREWORK (NbtMap), and the
        // block-state (INT) entry must NOT be present (no accidental conversion).
        var fireworkNbt = cbPacket.getMetadata().get(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.DISPLAY_FIREWORK);
        assertNotNull(fireworkNbt, "DISPLAY_FIREWORK NBT should survive round-trip on key 16");
        var blockState = cbPacket.getMetadata().get(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.DISPLAY_BLOCK_STATE);
        assertNull(blockState, "DISPLAY_BLOCK_STATE must not be populated from a firework NBT entry");
    }

    /**
     * Regression for a crash where a command-block entity-data string key
     * ({@link Entity#DATA_COMMAND_BLOCK_COMMAND} = 71 in Nukkit's numbering) held a
     * null string (corrupt/mistyped NBT), causing an NPE in
     * {@link cn.nukkit.utils.Binary#writeMetadata} during {@code AddEntityPacket} encoding.
     * The serializer must tolerate null {@code StringEntityData} as empty string.
     */
    @ParameterizedTest(name = "Null command-block string does not crash v{0}")
    @MethodSource("versionsFrom291")
    void testNullCommandStringDoesNotCrash(int protocolVersion) {
        GameVersion gameVersion = GameVersion.byProtocol(protocolVersion, false);

        var nukkitPacket = new cn.nukkit.network.protocol.SetEntityDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = gameVersion;
        nukkitPacket.eid = 1;
        EntityMetadata metadata = new EntityMetadata();
        metadata.put(new StringEntityData(Entity.DATA_COMMAND_BLOCK_COMMAND, null));
        nukkitPacket.metadata = metadata;
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.frame = 0L;
        }
        // Must not throw NullPointerException
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket.class);
        // Nukkit-MOT's key 71 = DATA_COMMAND_BLOCK_COMMAND (matches the Bedrock spec and
        // PrismarineJS minecraft-data: 69=trading_career, 70=has_command_block,
        // 71=command_block_command, 72=last_output, 73=track_output).
        // Note: CloudburstMC Protocol's key numbering is OFF BY ONE for this group (it
        // names key 71 as COMMAND_BLOCK_LAST_OUTPUT), so the decoded type name below does
        // not reflect Nukkit-MOT's (correct) semantics — this is a CloudburstMC bug, not ours.
        // The point of this test is only that encoding does not throw; the wire value is
        // an empty string either way (CloudburstMC decodes zero-length as null).
        String value = cbPacket.getMetadata().get(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.COMMAND_BLOCK_LAST_OUTPUT);
        assertTrue(value == null || value.isEmpty(),
                "Null string entity data should serialize as empty (got: " + value + ")");
    }
}
