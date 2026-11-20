package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 协议号 → 调色板直查缓存的守卫：同阈值区间共享实例，标准/网易同号协议不得串味。
 * <p>
 * Guards the protocol → palette lookup cache: one instance per threshold range, no aliasing
 * between standard and NetEase protocols sharing a number.
 */
class GlobalBlockPaletteLookupCacheTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void repeatedLookupReturnsTheSameInstance() {
        BlockPalette first = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_60);
        BlockPalette second = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_60);

        assertSame(first, second);
        assertTrue(first.getRuntimeId(Block.STONE, 0) != -1, "palette kept behind the cache must stay usable");
    }

    @Test
    void versionsSharingOneThresholdShareOnePalette() {
        // V1_26_44 与 V1_26_40 同 wire 协议号 2168，阈值回退到同一锚点
        assertSame(
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_40),
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_44)
        );
    }

    @Test
    void protocolsInsideOneThresholdRangeShareTheAnchorPalette() {
        // 2169（1.26.45）不在阈值表里，必须 floor 到 2168 的锚点
        assertSame(
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_40),
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_45)
        );
    }

    @Test
    void differentThresholdsResolveToDifferentPalettes() {
        assertNotSame(
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_10),
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_60)
        );
    }

    @Test
    void standardAndNetEaseLookupsDoNotAlias() {
        // 1.21.93 标准版与网易版共用协议号 819，标准版回退到 818 的调色板
        assertEquals(GameVersion.V1_21_93.getProtocol(), GameVersion.V1_21_93_NETEASE.getProtocol());

        assertNotSame(
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_21_93),
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_21_93_NETEASE)
        );
    }

    @Test
    @SuppressWarnings("deprecation")
    void protocolIntOverloadSharesTheLookupCache() {
        assertSame(
                GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_21_90),
                GlobalBlockPalette.getPaletteByProtocol(ProtocolInfo.v1_21_90)
        );
    }

    @Test
    void concurrentLookupsConvergeOnOneInstance() throws Exception {
        int workers = 4;
        Set<BlockPalette> seen = new HashSet<>();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 200; j++) {
                        BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50);
                        synchronized (seen) {
                            seen.add(palette);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(10));
        }

        assertEquals(1, seen.size());
    }
}
