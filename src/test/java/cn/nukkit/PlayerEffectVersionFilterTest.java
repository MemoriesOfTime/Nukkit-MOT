package cn.nukkit;

import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for issue #765: special arrows (Wind Charged / Weaving /
 * Oozing / Infested) were introduced in 1.21.0. Their effect packets must not
 * be sent to older clients, otherwise the client crashes.
 * <p>
 * The version gate lives in {@link Player#canReceiveEffectPacket(int)} and is
 * consumed by {@code Effect.add}/{@code Effect.remove}/{@code
 * Entity.sendPotionEffects}. The effect itself is still applied server-side,
 * so {@code canBeAffected} must NOT block these effects.
 */
class PlayerEffectVersionFilterTest {

    @Test
    void pre121SuppressesPacketsForNewEffects() {
        TestPlayer player = createPlayer();
        player.protocol = ProtocolInfo.v1_20_10; // 1.20.12 maps to protocol 594

        assertTrue(player.canReceiveEffectPacket(Effect.SPEED));
        assertTrue(player.canReceiveEffectPacket(Effect.SLOWNESS));
        assertTrue(player.canReceiveEffectPacket(Effect.WITHER));

        assertFalse(player.canReceiveEffectPacket(Effect.WIND_CHARGED));
        assertFalse(player.canReceiveEffectPacket(Effect.WEAVING));
        assertFalse(player.canReceiveEffectPacket(Effect.OOZING));
        assertFalse(player.canReceiveEffectPacket(Effect.INFESTED));
    }

    @Test
    void v121AllowsAllKnownEffectPackets() {
        TestPlayer player = createPlayer();
        player.protocol = ProtocolInfo.v1_21_0;

        assertTrue(player.canReceiveEffectPacket(Effect.WIND_CHARGED));
        assertTrue(player.canReceiveEffectPacket(Effect.WEAVING));
        assertTrue(player.canReceiveEffectPacket(Effect.OOZING));
        assertTrue(player.canReceiveEffectPacket(Effect.INFESTED));
    }

    /**
     * Even on pre-1.21.0 clients the effects are still applied server-side
     * (the death-triggered side effects etc. run regardless); only the packet
     * is suppressed. So {@code canBeAffected} must keep returning true.
     */
    @Test
    void pre121StillAppliesNewEffectsServerSide() {
        TestPlayer player = createPlayer();
        player.protocol = ProtocolInfo.v1_20_10;

        assertTrue(player.canBeAffected(Effect.WIND_CHARGED));
        assertTrue(player.canBeAffected(Effect.WEAVING));
        assertTrue(player.canBeAffected(Effect.OOZING));
        assertTrue(player.canBeAffected(Effect.INFESTED));
    }

    private static TestPlayer createPlayer() {
        MockServer.reset();

        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));

        return new TestPlayer(sourceInterface);
    }

    private static final class TestPlayer extends Player {

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }
    }
}
