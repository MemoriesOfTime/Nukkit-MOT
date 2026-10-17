package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockPaletteNetworkCompletionTest {
    @BeforeAll static void init(){MockServer.init();}
    @AfterAll static void cleanup(){MockServer.reset();}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void nativeTrimCannotExposeACompletedNetworkEpoch(boolean throughLock) throws Exception {
        BlockPalette palette=new BlockPalette(GameVersion.V1_20_0);
        assertEquals(BlockPalette.class,palette.getClass());
        long previous=palette.getNetworkVersion();assertTrue(previous>=0);
        if(throughLock)palette.clearStates();
        Field field=BlockPalette.class.getDeclaredField("legacyToRuntimeId");field.setAccessible(true);
        Int2IntOpenHashMap original=(Int2IntOpenHashMap)field.get(palette);
        Int2IntOpenHashMap map=spy(original);assertEquals(Int2IntOpenHashMap.class,map.getClass());field.set(palette,map);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return call.callRealMethod();}).when(map).trim();
        var pool=Executors.newSingleThreadExecutor();
        var task=pool.submit(()->{if(throughLock)palette.lock();else palette.trim();});
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertTrue(palette.getNetworkVersion()<0,"native mapping rehash is still active; cache must refuse the epoch");
        } finally {
            release.countDown();task.get(5,TimeUnit.SECONDS);pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));
            field.set(palette,original);
        }
        assertTrue(palette.getNetworkVersion()>previous,"public trim completion must invalidate the prior retained-byte key");
    }
    @Test void directTrimDoesNotFreezeAnUnlockedRegistrationPhase(){
        BlockPalette palette=new BlockPalette(GameVersion.V1_20_0);palette.clearStates();palette.trim();
        assertEquals(-1,palette.getNetworkVersion());
        palette.registerState(0,0,17,new CompoundTag().putString("name","minecraft:air"));
        assertEquals(-1,palette.getNetworkVersion());palette.lock();assertTrue(palette.getNetworkVersion()>=0);
        assertEquals(17,palette.getRuntimeId(0,0));
    }
    @Test void failedNativeTrimCannotPublishACompletedEpoch() throws Exception {
        BlockPalette palette=new BlockPalette(GameVersion.V1_20_0);
        Field field=BlockPalette.class.getDeclaredField("legacyToRuntimeId");field.setAccessible(true);
        Int2IntOpenHashMap map=spy((Int2IntOpenHashMap)field.get(palette));field.set(palette,map);
        var failure=new IllegalStateException("injected trim failure");doThrow(failure).when(map).trim();
        assertSame(failure,assertThrows(IllegalStateException.class,palette::trim));assertEquals(-1,palette.getNetworkVersion());
        doCallRealMethod().when(map).trim();palette.trim();assertTrue(palette.getNetworkVersion()>=0);
    }
    @Test void existingVirtualTrimHookStillObservesRegistrationLocked(){
        HookPalette palette=new HookPalette();int before=palette.calls;palette.lock();assertEquals(before+1,palette.calls);
    }
    static class HookPalette extends BlockPalette {
        int calls;
        HookPalette(){super(GameVersion.V1_20_0);}
        @Override public void trim(){calls++;assertThrows(IllegalStateException.class,()->registerState(0,0,0,new CompoundTag()));super.trim();}
    }
}
