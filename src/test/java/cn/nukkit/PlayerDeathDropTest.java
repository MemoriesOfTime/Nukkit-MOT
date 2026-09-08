package cn.nukkit;

import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlayerDeathDropTest {

    @Test
    void deathDropsUseCompactMotionAndKeepTheirPickupDelay() {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        Level level = mock(Level.class);
        Item item = mock(Item.class);
        doReturn(level).when(player).getLevel();

        player.dropDeathItem(item);

        verify(level).dropItem(same(player), same(item), isNull(), eq(false), eq(40));
    }
}
