package cn.nukkit.level;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.SubChunkRequestPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 延期子区块请求的条目预算排空：预算按昂贵条目扣减，单个超大请求不再独占整 tick——
 * 答到预算为止，剩余 offset 由 answerer 重排队尾下 tick 继续；廉价条目不占预算。
 * 曾经过量请求会一次性序列化全部条目，单包可达数十 MB。
 * <p>
 * Entry-budgeted drain of deferred sub-chunk requests: the budget is spent on expensive
 * entries only; an oversized request no longer takes the whole tick — it is answered up
 * to the budget and its remaining offsets are requeued by the answerer for the next
 * tick. Cheap entries cost no budget. Previously an oversized request serialized every
 * entry at once, producing multi-MB single packets.
 */
public class PlayerSubChunkRequestStateTest {

    private static SubChunkRequestPacket request(int offsets) {
        SubChunkRequestPacket pk = new SubChunkRequestPacket();
        pk.dimension = 0;
        pk.subChunkPosition = new BlockVector3(0, 0, 0);
        for (int i = 0; i < offsets; i++) {
            pk.positionOffsets.add(new BlockVector3(0, 0, 0));
        }
        return pk;
    }

    private static PlayerSubChunkRequestState enqueue(int count, int offsetsEach) {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        for (int i = 0; i < count; i++) {
            assertTrue(state.tryDefer(request(offsetsEach)));
        }
        return state;
    }

    @Test
    public void backlogDrainsInBulkWithinBudget() {
        PlayerSubChunkRequestState state = enqueue(20, 4);
        List<SubChunkRequestPacket> answered = new ArrayList<>();

        int spent = state.drain((pk, budget) -> {
            answered.add(pk);
            return 4;
        }, 64, () -> true);

        // 4 份工作/请求 × 16 个 = 预算 64，一次排空远多于一个 / 4 work each × 16 = budget
        // 64; a single call answers far more than one request
        assertEquals(64, spent);
        assertEquals(16, answered.size());
        assertEquals(4, state.deferredPacketCount());
        assertEquals(16, state.deferredOffsetCount());
    }

    @Test
    public void oversizedRequestAnsweredUpToBudgetAndResidualRequeued() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        state.tryDefer(request(100));
        List<Integer> budgetsSeen = new ArrayList<>();

        int spent = state.drain((pk, budget) -> {
            budgetsSeen.add(budget);
            int residual = 100 - budget;
            if (residual > 0) {
                // 模拟 answer 路径的剩余重排队 / simulate the residual requeue of the answer path
                state.requeue(request(residual));
            }
            return budget;
        }, 64, () -> true);

        // 预算耗尽即停，剩余 36 份工作留给下一轮 / stops at the budget; the remaining
        // 36 work waits for the next round
        assertEquals(64, spent);
        assertEquals(1, budgetsSeen.size());
        assertEquals(36, state.peekDeferred().positionOffsets.size());
        assertEquals(36, state.deferredOffsetCount());
    }

    @Test
    public void zeroWorkRequestsDrainWithoutBudget() {
        PlayerSubChunkRequestState state = enqueue(100, 0);

        int spent = state.drain((pk, budget) -> 0, 64, () -> true);

        // 廉价条目不占预算，一轮清空 / cheap requests cost no budget and drain in one round
        assertEquals(0, spent);
        assertFalse(state.hasDeferred());
    }

    @Test
    public void byteCeilingStopsDrain() {
        PlayerSubChunkRequestState state = enqueue(20, 4);

        int spent = state.drain((pk, budget) -> 4, 64, () -> false);

        assertEquals(0, spent);
        assertEquals(20, state.deferredPacketCount());
    }

    @Test
    public void zeroBudgetDrainsNothingUntilNextTick() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        state.tryDefer(request(1));

        // 预算 0 = 本 tick 已被立即应答耗尽：本轮不排空（账本跨 tick 清零，下 tick 必有进展）
        // / Budget 0 = exhausted this tick by immediate answers: nothing drains now (the
        // ledger resets across ticks; the next tick always makes progress)
        List<SubChunkRequestPacket> answered = new ArrayList<>();
        int spent = state.drain((pk, budget) -> {
            answered.add(pk);
            return 1;
        }, 0, () -> true);

        assertEquals(0, spent);
        assertTrue(answered.isEmpty());
        assertTrue(state.hasDeferred(), "the queue must survive a zero-budget round");
    }

    @Test
    public void emptyQueueDrainsNothing() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        assertEquals(0, state.drain((pk, budget) -> 1, 64, () -> true));
    }

    @Test
    public void consecutiveDrainsConsumeWholeBacklog() {
        PlayerSubChunkRequestState state = enqueue(10, 4);
        int total = 0;
        int rounds = 0;
        while (state.hasDeferred()) {
            total += state.drain((pk, budget) -> 4, 64, () -> true);
            rounds++;
        }
        assertEquals(40, total);
        // 预算内应远少于 10 轮 / well under 10 rounds
        assertTrue(rounds <= 3, "rounds=" + rounds);
    }

    @Test
    public void packetCapRejectsFurtherDefers() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        for (int i = 0; i < 256; i++) {
            assertTrue(state.tryDefer(request(1)));
        }
        // 超限丢弃新请求，客户端会重新请求 / over the cap the new request is dropped
        // (the client re-requests)
        assertFalse(state.tryDefer(request(1)));
        assertEquals(256, state.deferredPacketCount());
    }

    @Test
    public void offsetCapRejectsFurtherDefers() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        assertTrue(state.tryDefer(request(65536)));
        assertFalse(state.tryDefer(request(1)));
        assertEquals(65536, state.deferredOffsetCount());
    }

    @Test
    public void clearResetsQueueAndOffsets() {
        PlayerSubChunkRequestState state = enqueue(3, 5);
        state.accountResponseBytes(1, 512);
        state.accountSpentEntries(1, 10);

        state.clear();

        assertFalse(state.hasDeferred());
        assertEquals(0, state.deferredOffsetCount());
        // 账本随 tick 自愈，但清空后立即重置 / the ledgers self-heal across ticks,
        // but clear resets them immediately
        assertFalse(state.responseBytesExceeded(1));
        assertEquals(64, state.remainingEntryBudget(1, 64));
    }

    @Test
    public void responseByteLedgerIsPerTick() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        state.accountResponseBytes(7, 4 * 1024 * 1024);
        assertTrue(state.responseBytesExceeded(7));
        // 跨 tick 账本清零重计：3MB 不再受上一 tick 的 4MB 影响 / across ticks the
        // ledger restarts: 3MB is no longer burdened by the previous tick's 4MB
        state.accountResponseBytes(8, 3 * 1024 * 1024);
        assertFalse(state.responseBytesExceeded(8));
        state.accountResponseBytes(8, 1024 * 1024);
        assertTrue(state.responseBytesExceeded(8));
    }

    @Test
    public void spentEntryLedgerIsCumulativeWithinATick() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        assertEquals(64, state.remainingEntryBudget(7, 64), "a fresh tick starts at the full budget");

        // 模拟本 tick 的多次立即应答各消耗若干：高频单 offset 请求包合计不得超过预算
        // / Simulates several immediate answers this tick each spending a few: bursts
        // of single-offset packets must not exceed the budget in aggregate
        state.accountSpentEntries(7, 30);
        assertEquals(34, state.remainingEntryBudget(7, 64));
        state.accountSpentEntries(7, 4);
        assertEquals(30, state.remainingEntryBudget(7, 64));
        state.accountSpentEntries(7, 30);
        assertEquals(0, state.remainingEntryBudget(7, 64), "over-spend clamps at zero, never negative");
        state.accountSpentEntries(7, 5);
        assertEquals(0, state.remainingEntryBudget(7, 64));

        // 跨 tick 清零重计 / resets across ticks
        assertEquals(64, state.remainingEntryBudget(8, 64));
    }

    @Test
    public void zeroOrNegativeSpentIsNotAccounted() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        state.accountSpentEntries(3, 0);
        state.accountSpentEntries(3, -5);
        assertEquals(64, state.remainingEntryBudget(3, 64));
    }

    @Test
    public void duplicateOffsetsCollapse() {
        SubChunkRequestPacket pk = new SubChunkRequestPacket();
        pk.positionOffsets.add(new BlockVector3(1, 2, 3));
        pk.positionOffsets.add(new BlockVector3(1, 2, 3));
        pk.positionOffsets.add(new BlockVector3(-1, 0, 127));

        PlayerSubChunkRequestState.dedupeRequestOffsets(pk);

        assertEquals(2, pk.positionOffsets.size());
        assertEquals(1, pk.positionOffsets.get(0).x);
        assertEquals(-1, pk.positionOffsets.get(1).x);
    }

    @Test
    public void emptyAndSingleOffsetListsStayUntouched() {
        SubChunkRequestPacket empty = new SubChunkRequestPacket();
        PlayerSubChunkRequestState.dedupeRequestOffsets(empty);
        assertTrue(empty.positionOffsets.isEmpty());
        assertEquals(1, PlayerSubChunkRequestState.requestOffsetCount(empty));

        SubChunkRequestPacket single = new SubChunkRequestPacket();
        single.positionOffsets.add(new BlockVector3(0, 0, 0));
        PlayerSubChunkRequestState.dedupeRequestOffsets(single);
        assertEquals(1, single.positionOffsets.size());
        assertEquals(1, PlayerSubChunkRequestState.requestOffsetCount(single));
    }

    @Test
    public void missBacklogQueuesPollsAndPushesFrontInOrder() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        assertTrue(state.tryDeferMissBatch(new long[]{1, 2}));
        assertTrue(state.tryDeferMissBatch(new long[]{3}));
        assertEquals(3, state.deferredMissHashCount());

        assertArrayEquals(new long[]{1, 2}, state.pollMissBatch());
        assertEquals(1, state.deferredMissHashCount());

        // 本 tick 预算耗尽：剩余推回队头，下轮先补发它 / budget out this tick: the
        // residual goes back to the head and is served first next round
        state.pushMissBatchFront(new long[]{4, 5});
        assertArrayEquals(new long[]{4, 5}, state.pollMissBatch());
        assertArrayEquals(new long[]{3}, state.pollMissBatch());
        assertNull(state.pollMissBatch());
        assertEquals(0, state.deferredMissHashCount());
    }

    @Test
    public void missBacklogCapRejectsAndClearResets() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        assertTrue(state.tryDeferMissBatch(new long[65536]));
        assertFalse(state.tryDeferMissBatch(new long[]{1}), "over the hash cap the batch is rejected");

        state.clear();
        assertEquals(0, state.deferredMissHashCount());
        assertTrue(state.tryDeferMissBatch(new long[]{1}), "clear resets the backlog");
    }

    @Test
    public void remainingResponseBytesSharesTheTickLedger() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        assertEquals(4 * 1024 * 1024, state.remainingResponseBytes(7), "a fresh tick starts at the full budget");

        state.accountResponseBytes(7, 1000);
        assertEquals(4 * 1024 * 1024 - 1000, state.remainingResponseBytes(7));

        state.accountResponseBytes(7, 4 * 1024 * 1024);
        assertEquals(0, state.remainingResponseBytes(7), "clamped at zero, never negative");

        assertEquals(4 * 1024 * 1024, state.remainingResponseBytes(8), "the ledger resets across ticks");
    }

    @Test
    public void inFlightAcquireClampsAtCapAndReleases() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        assertEquals(200, state.acquireInFlightOffsets(200));
        assertEquals(PlayerSubChunkRequestState.MAX_IN_FLIGHT_OFFSETS - 200, state.remainingInFlightOffsets());
        // 部分获批：只给到剩余额度 / partial grant: only the remaining credit
        int granted = state.acquireInFlightOffsets(100);
        assertEquals(PlayerSubChunkRequestState.MAX_IN_FLIGHT_OFFSETS - 200, granted);
        assertEquals(0, state.remainingInFlightOffsets());
        assertEquals(0, state.acquireInFlightOffsets(1), "a full window grants nothing");
        assertEquals(0, state.acquireInFlightOffsets(0), "zero asks for nothing");

        state.releaseInFlightOffsets(granted);
        assertEquals(granted, state.remainingInFlightOffsets());
        // 释放不向下溢出 / releases never underflow
        state.releaseInFlightOffsets(1000);
        assertEquals(0, state.inFlightOffsetCount());
    }

    @Test
    public void completionsQueueFifoWithPushFront() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        PlayerSubChunkRequestState.CompletedSubChunkAnswer a = answer(1);
        PlayerSubChunkRequestState.CompletedSubChunkAnswer b = answer(2);
        state.offerCompletion(a);
        state.offerCompletion(b);
        assertTrue(state.hasCompletions());

        assertSame(a, state.pollCompletion());
        // 预算耗尽推回队头：下轮先发它 / budget-blocked head goes back to the front
        state.pushCompletionFront(a);
        assertSame(a, state.pollCompletion());
        assertSame(b, state.pollCompletion());
        assertNull(state.pollCompletion());
        assertFalse(state.hasCompletions());
    }

    @Test
    public void clearBumpsEpochAndResetsInFlightAndCompletions() {
        PlayerSubChunkRequestState state = new PlayerSubChunkRequestState();
        int epochBefore = state.epoch();
        state.acquireInFlightOffsets(64);
        PlayerSubChunkRequestState.CompletedSubChunkAnswer stale = answer(1);
        state.offerCompletion(stale);

        state.clear();

        assertEquals(epochBefore + 1, state.epoch(), "clear invalidates in-flight answers");
        assertEquals(0, state.inFlightOffsetCount());
        assertFalse(state.hasCompletions());
        assertEquals(PlayerSubChunkRequestState.MAX_IN_FLIGHT_OFFSETS, state.remainingInFlightOffsets());
    }

    private static PlayerSubChunkRequestState.CompletedSubChunkAnswer answer(int tag) {
        return new PlayerSubChunkRequestState.CompletedSubChunkAnswer(0, new SubChunkRequestPacket(), false,
                java.util.Collections.emptyList(), null, null, null, tag);
    }
}
