package cn.nukkit.network.encryption;

import cn.nukkit.Server;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.ClientChainData;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Bounded login verification; only AsyncTask.collectTask may publish a verified identity. */
public final class LoginChainVerifier {
    private static final LoginChainVerifier SHARED = new LoginChainVerifier(2, 32,
            128L * 1024 * 1024, ClientChainData::of);

    private final ThreadPoolExecutor workers;
    private final int capacity;
    private final long byteCapacity;
    private final Function<byte[], ClientChainData> decoder;
    private final Set<Verification> outstanding = new HashSet<>();
    private long retainedBytes;
    private boolean stopped;

    public static LoginChainVerifier shared() {
        return SHARED;
    }

    LoginChainVerifier(int threads, int queued, long byteCapacity, Function<byte[], ClientChainData> decoder) {
        this.capacity = threads + queued;
        this.byteCapacity = byteCapacity;
        this.decoder = decoder;
        this.workers = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queued), runnable -> {
                    Thread worker = new Thread(runnable, "Login verification");
                    worker.setDaemon(true); // DNS may ignore interruption; shutdown must never wait for it.
                    return worker;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Returns null on overload, without copying the packet or running verification on the caller. */
    public synchronized Verification submit(byte[] packet, BiConsumer<ClientChainData, Throwable> completion) {
        if (stopped || outstanding.size() >= capacity || packet.length > byteCapacity - retainedBytes) {
            return null;
        }
        Verification job = new Verification(packet.clone(), completion);
        outstanding.add(job);
        retainedBytes += packet.length;
        try {
            workers.execute(job);
            return job;
        } catch (RejectedExecutionException rejected) {
            release(job);
            return null;
        }
    }

    /** Discard queued/completed identities immediately; running DNS remains bounded to two daemons. */
    public synchronized void shutdown() {
        stopped = true;
        for (Verification job : Set.copyOf(outstanding)) {
            job.cancel();
        }
        workers.shutdownNow();
    }

    private void release(Verification job) {
        if (outstanding.remove(job)) {
            retainedBytes -= job.bytes;
            job.packet = null;
            job.result = null;
            job.failure = null;
            job.completion = null;
        }
    }

    public final class Verification extends AsyncTask {
        private byte[] packet;
        private final int bytes;
        private BiConsumer<ClientChainData, Throwable> completion;
        private ClientChainData result;
        private Throwable failure;
        private boolean cancelled;

        private Verification(byte[] packet, BiConsumer<ClientChainData, Throwable> completion) {
            this.packet = packet;
            this.bytes = packet.length;
            this.completion = completion;
        }

        public void cancel() {
            synchronized (LoginChainVerifier.this) {
                cancelled = true;
                completion = null;
                if (workers.remove(this) || AsyncTask.FINISHED_LIST.remove(this)) {
                    release(this);
                }
            }
        }

        @Override
        public void run() {
            try {
                onRun();
            } finally {
                synchronized (LoginChainVerifier.this) {
                    packet = null;
                    if (cancelled || stopped) {
                        release(this);
                    } else {
                        // Reservations include completed results until main consumes them.
                        AsyncTask.FINISHED_LIST.offer(this);
                    }
                }
            }
        }

        @Override
        public void onRun() {
            try {
                result = decoder.apply(packet);
            } catch (RuntimeException | AssertionError rejected) {
                // Discovery/OpenID failures can be AssertionError in the pinned validator.
                failure = rejected;
            }
        }

        @Override
        public void onCompletion(Server server) {
            BiConsumer<ClientChainData, Throwable> callback;
            ClientChainData validated;
            Throwable rejected;
            synchronized (LoginChainVerifier.this) {
                if (!outstanding.contains(this)) {
                    return;
                }
                callback = cancelled || stopped ? null : completion;
                validated = result;
                rejected = failure;
                release(this);
            }
            if (callback != null) {
                callback.accept(validated, rejected);
            }
        }
    }
}
