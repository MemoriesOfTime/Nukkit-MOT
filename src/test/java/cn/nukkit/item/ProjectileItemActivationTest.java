package cn.nukkit.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * Touch clients tap blocks to interact, and the Bedrock client sends a separate air-use
 * transaction for a real throw, so block item-use must not activate projectiles.
 */
class ProjectileItemActivationTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void projectileBlockUseDoesNotActivate() {
        TestProjectile item = new TestProjectile();

        assertFalse(item.canBeActivated());
        assertFalse(item.onActivate(null, mock(Player.class), null, null, BlockFace.UP, 0, 0, 0));
        assertFalse(item.airUse);
    }

    private static final class TestProjectile extends ProjectileItem {

        private boolean airUse;

        private TestProjectile() {
            super(ItemID.SNOWBALL, 0, 1, "Snowball");
        }

        @Override
        public String getProjectileEntityType() {
            return "Snowball";
        }

        @Override
        public float getThrowForce() {
            return 1.5f;
        }

        @Override
        public boolean onClickAir(Player player, Vector3 directionVector) {
            this.airUse = true;
            return true;
        }
    }
}
