package cn.nukkit;

import cn.nukkit.network.process.processor.common.RequestChunkRadiusProcessor;
import cn.nukkit.network.protocol.RequestChunkRadiusPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerChunkRadiusOrderTest {
    @Test
    void clientShrinkSchedulesReorderForNextPlayerNetworkPass() {
        Player player = mock(Player.class);
        player.chunkRadius = 12;
        player.nextChunkOrderRun = 20;
        when(player.getViewDistance()).thenReturn(12);
        RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
        packet.radius = 3;
        RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
        assertEquals(3, player.chunkRadius);
        assertEquals(0, player.nextChunkOrderRun);
    }

    @Test
    void repeatedSameRadiusDoesNotTriggerExtraReorders() {
        Player player = mock(Player.class);
        player.chunkRadius = 12;
        player.nextChunkOrderRun = 17;
        when(player.getViewDistance()).thenReturn(12);
        RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
        packet.radius = 12;
        RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
        assertEquals(17, player.nextChunkOrderRun);
    }

    @Test
    void clientGrowthSchedulesReorder() {
        Player player = mock(Player.class);
        player.chunkRadius = 3;
        player.nextChunkOrderRun = 17;
        when(player.getViewDistance()).thenReturn(12);
        RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
        packet.radius = 8;
        RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
        assertEquals(8, player.chunkRadius);
        assertEquals(0, player.nextChunkOrderRun);
    }

    @Test
    void clampedRequestWithoutEffectiveChangeKeepsTheExistingOrder() {
        Player player = mock(Player.class);
        player.chunkRadius = 3;
        player.nextChunkOrderRun = 17;
        when(player.getViewDistance()).thenReturn(12);
        RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
        packet.radius = 1;
        RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
        assertEquals(3, player.chunkRadius);
        assertEquals(17, player.nextChunkOrderRun);
    }

    @Test
    void hostRadiusChangeAlsoInvalidatesQueue() {
        Player player = mock(Player.class);
        player.chunkRadius = 12;
        player.nextChunkOrderRun = 20;
        doCallRealMethod().when(player).setViewDistance(anyInt());
        player.setViewDistance(4);
        assertEquals(0, player.nextChunkOrderRun);
        player.nextChunkOrderRun = 17;
        player.setViewDistance(4);
        assertEquals(17, player.nextChunkOrderRun);
    }
}
