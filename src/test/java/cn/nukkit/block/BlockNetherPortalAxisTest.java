package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BlockNetherPortalAxisTest {
    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void constructorPreservesPortalAxis(int meta) {
        assertEquals(meta, new BlockNetherPortal(meta).getDamage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void factoryReturnsPassablePortalInsteadOfUnknownBlock(int meta) {
        Block portal = Block.get(Block.NETHER_PORTAL, meta);
        assertInstanceOf(BlockNetherPortal.class, portal);
        assertEquals(meta, portal.getDamage());
        assertTrue(portal.canPassThrough());
        assertFalse(portal.isSolid());
        portal.setDamage(0);
        assertEquals(meta, Block.get(Block.NETHER_PORTAL, meta).getDamage(),
                "a detached clone must not change the cached portal prototype");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void persistedAxisResolvesBackToPassablePortal(int meta) {
        var mapping = BlockStateMapping.get();
        var state = mapping.getState(Block.NETHER_PORTAL, meta);
        assertEquals("minecraft:portal", state.getVanillaState().getString("name"));
        assertEquals(meta == 1 ? "x" : "z",
                state.getVanillaState().getCompound("states").getString("portal_axis"));
        int fullId = mapping.getFullId(state.getRuntimeId());
        assertEquals(Block.NETHER_PORTAL, fullId >> Block.DATA_BITS);
        Block restored = Block.get(fullId >> Block.DATA_BITS, fullId & Block.DATA_MASK);
        assertInstanceOf(BlockNetherPortal.class, restored);
        assertEquals(meta, restored.getDamage());
        assertTrue(restored.canPassThrough());
    }

    static Stream<Arguments> axesAndProtocols() {
        return ProtocolInfo.SUPPORTED_PROTOCOLS.stream().distinct()
                .filter(protocol -> protocol >= ProtocolInfo.v1_20_0 && protocol <= ProtocolInfo.v1_26_50)
                .flatMap(protocol -> Stream.of(Arguments.of(protocol, 1), Arguments.of(protocol, 2)));
    }

    @ParameterizedTest
    @MethodSource("axesAndProtocols")
    void existingClientPalettesKeepBothAxes(int protocol, int meta) {
        Block portal = Block.get(Block.NETHER_PORTAL, meta);
        assertInstanceOf(BlockNetherPortal.class, portal);
        int runtime = GlobalBlockPalette.getOrCreateRuntimeId(protocol, portal.getId(), portal.getDamage());
        assertEquals((Block.NETHER_PORTAL << Block.DATA_BITS) | meta,
                GlobalBlockPalette.getLegacyFullId(protocol, runtime));
    }

    @Test
    void defaultPortalKeepsLegacyZeroMeta() {
        assertEquals(0, new BlockNetherPortal().getDamage());
        assertInstanceOf(BlockNetherPortal.class, Block.get(Block.NETHER_PORTAL, 0));
    }
}
