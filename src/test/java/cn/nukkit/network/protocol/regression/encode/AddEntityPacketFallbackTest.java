package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.EntityLink;
import cn.nukkit.network.protocol.types.PropertySyncData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 AddEntityPacket 在收到未注册 network id 时不再抛 IllegalStateException，
 * 而是回退为 minecraft:item 标识符（issue #800）。
 * <p>
 * Verifies AddEntityPacket no longer throws IllegalStateException on unregistered network ids
 * and instead falls back to minecraft:item.
 */
public class AddEntityPacketFallbackTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
        Attribute.init();
    }

    /**
     * The reported regression: network id 10086 on protocol 844 used to throw
     * {@code IllegalStateException} and drop the entire batched packet queue, which
     * prevented chunks from loading. It must now encode successfully.
     */
    @Test
    void unknownNetworkIdDoesNotThrow() {
        AddEntityPacket pk = newPacket(10086, ProtocolInfo.v1_21_111);
        assertDoesNotThrow(pk::encode);
    }

    /**
     * The encoded packet must round-trip through the CloudburstMC codec with the
     * safe fallback identifier, so the rest of the batched packets are delivered.
     */
    @Test
    void unknownNetworkIdFallsBackToItem() {
        AddEntityPacket pk = newPacket(10086, ProtocolInfo.v1_21_111);
        pk.encode();

        var cbPacket = crossDecode(pk, org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket.class);
        assertEquals("minecraft:item", cbPacket.getIdentifier(),
                "Unknown network id should fall back to minecraft:item to keep the batch intact");
    }

    /**
     * Regression for the specific ids reported in issue #800 (10086, 10089, 10090) at the
     * user's protocol (844). All three must encode safely.
     */
    @Test
    void issue800ReportedIdsAreSafe() {
        for (int id : new int[]{10086, 10089, 10090}) {
            AddEntityPacket pk = newPacket(id, ProtocolInfo.v1_21_111);
            assertDoesNotThrow(pk::encode, "id " + id + " must not crash encode()");
        }
    }

    private static AddEntityPacket newPacket(int type, int protocol) {
        AddEntityPacket pk = new AddEntityPacket();
        pk.protocol = protocol;
        pk.gameVersion = cn.nukkit.GameVersion.byProtocol(protocol, false);
        pk.entityUniqueId = 1L;
        pk.entityRuntimeId = 1L;
        pk.type = type;
        pk.id = null; // force getIdentifier() resolution via mapping/EntityManager
        pk.x = 0f;
        pk.y = 64f;
        pk.z = 0f;
        pk.metadata = new EntityMetadata();
        pk.attributes = new Attribute[0];
        pk.links = new EntityLink[0];
        pk.properties = new PropertySyncData(new int[]{}, new float[]{});
        return pk;
    }
}

