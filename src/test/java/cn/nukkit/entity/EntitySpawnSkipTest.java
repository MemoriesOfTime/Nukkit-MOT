package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.mob.EntityZombie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 {@link Entity#getIdentifier(int)} 对未知 network id 返回 {@code null}，这是
 * {@link Entity#spawnTo(cn.nukkit.Player)} 的跳过守卫的前置条件（issue #800）。
 * <p>
 * Verifies {@link Entity#getIdentifier(int)} returns {@code null} for unknown network ids,
 * which is the precondition for the skip guard in {@link Entity#spawnTo(cn.nukkit.Player)}.
 */
public class EntitySpawnSkipTest {

    private static final int UNKNOWN_NETWORK_ID = 10086;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    /**
     * The reported regression: the unknown id 10086 (and its siblings 10089, 10090 from
     * issue #800) must resolve to a null identifier, which is the gate condition for
     * {@link Entity#spawnTo(cn.nukkit.Player)} to skip the entity instead of producing
     * an {@code AddEntityPacket} that throws during encode.
     */
    @Test
    void unknownNetworkIdHasNullIdentifier() {
        assertNull(Entity.getIdentifier(UNKNOWN_NETWORK_ID),
                "Network id " + UNKNOWN_NETWORK_ID + " should not resolve to any identifier");
        assertNull(Entity.getIdentifier(10089),
                "Network id 10089 (issue #800) should not resolve to any identifier");
        assertNull(Entity.getIdentifier(10090),
                "Network id 10090 (issue #800) should not resolve to any identifier");
    }

    /**
     * Identifiers in the custom-entity allocator range (>= 10000) that have no registered
     * {@code EntityDefinition} also resolve to null. This is the scenario the reporter is
     * most likely hitting (residual custom entities from a removed plugin).
     */
    @Test
    void unregisteredCustomEntityRangeResolvesToNull() {
        for (int id = 10000; id <= 10099; id++) {
            assertNull(Entity.getIdentifier(id),
                    "Unregistered id " + id + " in custom-entity range should not resolve");
        }
    }

    /**
     * Sanity: a vanilla entity id (zombie) still resolves correctly, so the guard does
     * not regress known entities.
     */
    @Test
    void knownEntityIdentifierStillResolves() {
        assertEquals("minecraft:zombie", Entity.getIdentifier(EntityZombie.NETWORK_ID).toString(),
                "Vanilla zombie network id must still resolve to its identifier");
    }

    /**
     * Sanity: the lookup via {@link Server} is not required — the static helper works
     * even before any player is connected, which is what happens during chunk load.
     */
    @Test
    void staticLookupWorksStandalone() {
        assertNull(Entity.getIdentifier(Integer.MAX_VALUE),
                "An obviously unregistered id must resolve to null");
    }
}
