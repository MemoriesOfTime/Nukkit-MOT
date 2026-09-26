package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityAbsorptionAttributeTest {

    @BeforeAll
    static void init() {
        MockServer.init();
        Attribute.init();
    }

    @Test
    void sendsFiniteHudCapacityToVersion126Clients() {
        Attribute full = Entity.clientAbsorptionAttribute(16.0f, ProtocolInfo.v1_26_0);
        Attribute damaged = Entity.clientAbsorptionAttribute(7.0f, ProtocolInfo.v1_26_0);

        assertEquals(16.0f, full.getValue());
        assertEquals(16.0f, full.getMaxValue());
        assertEquals(7.0f, damaged.getValue());
        assertEquals(16.0f, damaged.getMaxValue());
    }

    @Test
    void keepsLargeCustomAbsorptionRepresentable() {
        Attribute custom = Entity.clientAbsorptionAttribute(24.0f, ProtocolInfo.v1_26_0);

        assertEquals(24.0f, custom.getValue());
        assertEquals(24.0f, custom.getMaxValue());
    }

    @Test
    void leavesOlderClientPacketsUnchanged() {
        Attribute old = Entity.clientAbsorptionAttribute(16.0f, ProtocolInfo.v1_21_60);

        assertEquals(Float.MAX_VALUE, old.getMaxValue());
    }
}
