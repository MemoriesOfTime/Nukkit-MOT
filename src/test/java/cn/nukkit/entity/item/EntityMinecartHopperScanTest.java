package cn.nukkit.entity.item;

import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A cart with an inventory notifies the hopper under its rail and the hopper above it. Both scans
 * cover the cells the cart occupies, on either side of zero and below y = 0.
 */
class EntityMinecartHopperScanTest {

    private static final double[][] CARTS = {
            {10.5, 64.0625, 20.5},
            {-10.5, 64.0625, -20.5},
            {-0.5, -30.9375, 0.5},
            {7.25, -12.0, -3.75},
    };

    private static boolean scan(String method, double[] cart, int hopperX, int hopperY, int hopperZ,
                                BlockEntityHopper hopper, InventoryHolder holder) throws Exception {
        Level level = mock(Level.class);
        when(level.getBlockEntity(any(BlockVector3.class))).thenAnswer(invocation -> {
            BlockVector3 at = invocation.getArgument(0);
            return at.x == hopperX && at.y == hopperY && at.z == hopperZ ? hopper : null;
        });
        EntityMinecartAbstract minecart = mock(EntityMinecartAbstract.class, CALLS_REAL_METHODS);
        minecart.level = level;

        double top = method.equals("checkPickupHopper") ? cart[1] : cart[1] + 2;
        double bottom = method.equals("checkPickupHopper") ? cart[1] - 1 : cart[1];
        AxisAlignedBB area = new SimpleAxisAlignedBB(cart[0], bottom, cart[2], cart[0], top, cart[2]);

        Method scan = EntityMinecartAbstract.class.getDeclaredMethod(method, AxisAlignedBB.class, InventoryHolder.class);
        scan.setAccessible(true);
        return (boolean) scan.invoke(minecart, area, holder);
    }

    @Test
    void cartNotifiesTheHopperUnderItsRail() throws Exception {
        for (double[] cart : CARTS) {
            BlockEntityHopper hopper = mock(BlockEntityHopper.class);
            InventoryHolder holder = mock(InventoryHolder.class);
            int x = (int) Math.floor(cart[0]);
            int y = (int) Math.floor(cart[1]) - 1;
            int z = (int) Math.floor(cart[2]);

            assertTrue(scan("checkPickupHopper", cart, x, y, z, hopper, holder),
                    "hopper under the cart at " + cart[0] + ", " + cart[1] + ", " + cart[2]);
            verify(hopper).setMinecartInvPickupFrom(holder);
        }
    }

    @Test
    void cartNotifiesTheHopperAboveIt() throws Exception {
        for (double[] cart : CARTS) {
            BlockEntityHopper hopper = mock(BlockEntityHopper.class);
            InventoryHolder holder = mock(InventoryHolder.class);
            int x = (int) Math.floor(cart[0]);
            int y = (int) Math.floor(cart[1]) + 1;
            int z = (int) Math.floor(cart[2]);

            assertTrue(scan("checkPushHopper", cart, x, y, z, hopper, holder),
                    "hopper above the cart at " + cart[0] + ", " + cart[1] + ", " + cart[2]);
            verify(hopper).setMinecartInvPushTo(holder);
        }
    }
}
