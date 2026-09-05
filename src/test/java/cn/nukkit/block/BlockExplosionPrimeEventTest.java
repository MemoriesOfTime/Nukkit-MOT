package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.block.BlockExplosionPrimeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockExplosionPrimeEventTest {

    @Test
    void cancelledRespawnAnchorPrimeKeepsTheSourceBlock() {
        Level level = cancellableExplosionLevel(Level.DIMENSION_OVERWORLD);
        BlockRespawnAnchor anchor = place(new BlockRespawnAnchor(1), level);

        anchor.explode(mock(Player.class));

        verify(level, never()).setBlock(any(), any(Block.class));
    }

    @Test
    void cancelledBedPrimeKeepsTheSourceBlock() {
        Level level = cancellableExplosionLevel(Level.DIMENSION_NETHER);
        BlockBed bed = place(new BlockBed(), level);

        bed.onActivate(Item.get(Item.AIR), mock(Player.class));

        verify(level, never()).setBlock(any(), any(Block.class), anyBoolean(), anyBoolean());
    }

    private static Level cancellableExplosionLevel(int dimension) {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        GameRules rules = mock(GameRules.class);
        when(level.getServer()).thenReturn(server);
        when(level.getDimension()).thenReturn(dimension);
        when(level.getGameRules()).thenReturn(rules);
        when(rules.getBoolean(GameRule.RESPAWN_BLOCKS_EXPLODE)).thenReturn(true);
        when(server.getPluginManager()).thenReturn(plugins);
        doAnswer(invocation -> {
                    ((BlockExplosionPrimeEvent) invocation.getArgument(0)).setCancelled(true);
                    return null;
                })
                .when(plugins)
                .callEvent(any(BlockExplosionPrimeEvent.class));
        return level;
    }

    private static <T extends Block> T place(T block, Level level) {
        block.level = level;
        block.x = 10;
        block.y = 64;
        block.z = -5;
        return block;
    }
}
