package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.task.LightPopulationTask;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

/**
 * 光照失败收敛并重试,成功后挂载副本。
 * <p>
 * Light failures are contained and retried; success mounts the copy.
 */
class LightPopulationTaskTest {

    private final Server server = mock(Server.class);
    private final ServerScheduler scheduler = mock(ServerScheduler.class);
    private final Level level = mock(Level.class);
    private final Object populationReceipt = new Object();

    private BaseFullChunk source;
    private BaseFullChunk live;

    private void setUp() {
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        when(level.getId()).thenReturn(7);
        when(server.getLevel(7)).thenReturn(level);
        when(level.isChunkLightRetryCurrent(3, 5, populationReceipt)).thenReturn(true);

        source = mock(BaseFullChunk.class);
        when(source.getX()).thenReturn(3);
        when(source.getZ()).thenReturn(5);
        live = source;
        when(live.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(3, 5)).thenReturn(live);
        when(level.completeChunkLightRetry(eq(3), eq(5), any(), same(populationReceipt))).thenReturn(true);
    }

    @Test
    void failedLightPhaseIsContainedAndRetriedAfterADelay() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();

        LightPopulationTask task = new LightPopulationTask(level, source);
        assertDoesNotThrow(task::onRun, "a failing light task must not escape its runner");
        task.onCompletion(server);

        verify(level, never()).generateChunkCallback(anyInt(), anyInt(), any());
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), retry.capture(), eq(20));

        retry.getValue().run();
        ArgumentCaptor<AsyncTask> retried = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), retried.capture());
        assertInstanceOf(LightPopulationTask.class, retried.getValue());
    }

    @Test
    void successfulLightPhaseMountsTheLitCopy() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        BaseFullChunk lit = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        when(copy.clone()).thenReturn(lit);
        when(lit.clone()).thenReturn(lit);
        when(lit.getX()).thenReturn(3);
        when(lit.getZ()).thenReturn(5);

        LightPopulationTask task = new LightPopulationTask(level, source);
        task.onRun();
        task.onCompletion(server);

        verify(level).generateChunkCallback(eq(3), eq(5), same(lit), eq(true));
        verify(scheduler, never()).scheduleDelayedTask(any(), any(), anyInt());
    }

    @Test
    void populationRetryAnnouncesTheColumnOnSuccess() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        BaseFullChunk lit = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        when(copy.clone()).thenReturn(lit);
        when(lit.clone()).thenReturn(lit);
        when(lit.getX()).thenReturn(3);
        when(lit.getZ()).thenReturn(5);

        LightPopulationTask task = new LightPopulationTask(level, source, populationReceipt);
        task.onRun();
        task.onCompletion(server);

        verify(level).completeChunkLightRetry(3, 5, lit, populationReceipt);
    }

    @Test
    void lightRetriesStopAfterTheAttemptCap() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        when(live.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();

        LightPopulationTask first = new LightPopulationTask(level, source);
        first.onRun();
        first.onCompletion(server);

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<AsyncTask> retried = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), retry.capture(), eq(20));
        retry.getValue().run();
        verify(scheduler).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), retried.capture());
        LightPopulationTask second = (LightPopulationTask) retried.getValue();
        second.onRun();
        second.onCompletion(server);

        verify(scheduler, times(2)).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), retry.capture(), eq(20));
        retry.getValue().run();
        verify(scheduler, times(2)).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), retried.capture());
        LightPopulationTask third = (LightPopulationTask) retried.getValue();
        third.onRun();
        third.onCompletion(server);

        verify(scheduler, times(2)).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), any(Runnable.class), eq(20));
        verify(level, never()).generateChunkCallback(anyInt(), anyInt(), any());
        verify(level, never()).abandonChunkPopulation(anyInt(), anyInt());
        verify(level, never()).releaseChunkPopulationLocks(anyInt(), anyInt());
    }

    @Test
    void exhaustedPopulationRetryKeepsTheEntryAndBacksOff() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        when(live.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();

        LightPopulationTask first = new LightPopulationTask(level, source, populationReceipt);
        first.onRun();
        first.onCompletion(server);

        ArgumentCaptor<Runnable> delay = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<AsyncTask> retried = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delay.capture(), eq(20));
        delay.getValue().run();
        verify(scheduler).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), retried.capture());
        LightPopulationTask second = (LightPopulationTask) retried.getValue();
        second.onRun();
        second.onCompletion(server);

        verify(scheduler, times(2)).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delay.capture(), eq(20));
        delay.getValue().run();
        verify(scheduler, times(2)).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), retried.capture());
        LightPopulationTask third = (LightPopulationTask) retried.getValue();
        third.onRun();
        third.onCompletion(server);

        verify(level, never()).abandonChunkPopulation(anyInt(), anyInt());
        verify(level).releaseChunkPopulationLocks(3, 5);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delay.capture(), eq(200));

        delay.getValue().run();
        verify(level).scheduleChunkLightRetry(3, 5, live);
    }

    @Test
    void retryIsSkippedWhenTheChunkIsGoneOrAlreadyLit() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();
        when(live.isLightPopulated()).thenReturn(true);

        LightPopulationTask task = new LightPopulationTask(level, source);
        task.onRun();
        task.onCompletion(server);

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), retry.capture(), eq(20));
        retry.getValue().run();

        verify(scheduler, never()).scheduleAsyncTask(any(), any(AsyncTask.class));
        verify(level).completeChunkLightRetry(3, 5, live, null);
    }

    @Test
    void chunkMissingOnRetryPausesTheRetry() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();
        when(level.getChunkIfLoaded(3, 5)).thenReturn(null);

        LightPopulationTask task = new LightPopulationTask(level, source, populationReceipt);
        task.onRun();
        task.onCompletion(server);

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), retry.capture(), eq(20));
        retry.getValue().run();

        verify(scheduler, never()).scheduleAsyncTask(any(), any(AsyncTask.class));
        verify(level).pauseChunkLightRetry(3, 5, populationReceipt);
        verify(level, never()).abandonChunkPopulation(anyInt(), anyInt());
    }

    @Test
    void unmountableLitCopyPausesTheRetry() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        BaseFullChunk lit = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        when(copy.clone()).thenReturn(lit);
        when(lit.clone()).thenReturn(null);
        when(lit.getX()).thenReturn(3);
        when(lit.getZ()).thenReturn(5);

        LightPopulationTask task = new LightPopulationTask(level, source, populationReceipt);
        task.onRun();
        task.onCompletion(server);

        verify(level, never()).generateChunkCallback(anyInt(), anyInt(), any(), anyBoolean());
        verify(level).pauseChunkLightRetry(3, 5, populationReceipt);
        verify(level, never()).abandonChunkPopulation(anyInt(), anyInt());
    }

    @Test
    void retryScheduleFailurePausesTheRetry() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();
        doThrow(new RuntimeException("rejected")).when(scheduler)
                .scheduleDelayedTask(any(), any(Runnable.class), anyInt());

        LightPopulationTask task = new LightPopulationTask(level, source, populationReceipt);
        task.onRun();
        task.onCompletion(server);

        verify(level).pauseChunkLightRetry(3, 5, populationReceipt);
        verify(level, never()).abandonChunkPopulation(anyInt(), anyInt());
    }

    @Test
    void alreadyLitRecoveryWaitsForNeighbourToReleaseItsLock() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();
        when(live.isLightPopulated()).thenReturn(true);
        when(level.completeChunkLightRetry(3, 5, live, populationReceipt)).thenReturn(false, true);
        LightPopulationTask task = new LightPopulationTask(level, source, populationReceipt);
        task.onRun();
        task.onCompletion(server);

        ArgumentCaptor<Runnable> delay = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delay.capture(), eq(20));
        delay.getValue().run();
        verify(scheduler, times(2)).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delay.capture(), eq(20));
        delay.getValue().run();

        verify(level, times(2)).completeChunkLightRetry(3, 5, live, populationReceipt);
        verify(scheduler, times(2)).scheduleDelayedTask(any(), any(Runnable.class), anyInt());
        verify(scheduler, never()).scheduleAsyncTask(any(), any(AsyncTask.class));
    }

    @Test
    void expiredRecoveryCannotSubmitOrCompleteAnotherTask() {
        setUp();
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(source.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();
        LightPopulationTask task = new LightPopulationTask(level, source, populationReceipt);
        task.onRun();
        task.onCompletion(server);
        ArgumentCaptor<Runnable> delay = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delay.capture(), eq(20));
        when(level.isChunkLightRetryCurrent(3, 5, populationReceipt)).thenReturn(false);

        delay.getValue().run();
        task.onCompletion(server);

        verify(scheduler, never()).scheduleAsyncTask(any(), any(AsyncTask.class));
        verify(level, never()).completeChunkLightRetry(anyInt(), anyInt(), any(), any());
        verify(level, never()).pauseChunkLightRetry(anyInt(), anyInt(), any());
        verify(scheduler).scheduleDelayedTask(any(), any(Runnable.class), anyInt());
    }
}
