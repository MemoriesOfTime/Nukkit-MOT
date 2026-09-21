package cn.nukkit.network.encryption;

import cn.nukkit.MockServer;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ClientChainData;
import com.google.gson.Gson;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LoginChainVerifierTest {
    @Test
    void blockedLookupNeverRunsOnMainAndCompletedResultKeepsItsByteReservation() throws Exception {
        MockServer.init();
        Thread main = Thread.currentThread();
        CountDownLatch entered = new CountDownLatch(1), unblock = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>(), callback = new AtomicReference<>();
        AtomicInteger observedByte = new AtomicInteger();
        LoginChainVerifier verifier = new LoginChainVerifier(1, 1, 1, packet -> {
            worker.set(Thread.currentThread());
            entered.countDown();
            await(unblock);
            observedByte.set(packet[0]);
            return null;
        });
        try {
            byte[] input = {42};
            assertNotNull(verifier.submit(input, (result, failure) -> callback.set(Thread.currentThread())));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            input[0] = 7;
            assertNotEquals(main, worker.get());
            assertNull(callback.get());
            assertNull(verifier.submit(new byte[1], (r, f) -> fail("over budget")));
            unblock.countDown();
            waitForFinished();
            assertNull(callback.get(), "the worker may never publish an identity");
            assertNull(verifier.submit(new byte[1], (r, f) -> fail("result still retained")));
            AsyncTask.collectTask();
            assertEquals(main, callback.get());
            assertEquals(42, observedByte.get(), "the verifier owns an immutable packet snapshot");
            assertNotNull(verifier.submit(new byte[1], (r, f) -> {}));
        } finally {
            unblock.countDown();
            verifier.shutdown();
        }
    }

    @Test
    void twoWorkersAndThirtyTwoQueuedJobsRemainBoundedEvenAfterRunningCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(2), unblock = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        LoginChainVerifier verifier = new LoginChainVerifier(2, 32, 128L * 1024 * 1024, packet -> {
            entered.countDown();
            await(unblock); // Deliberately behaves like DNS that ignores interruption.
            return null;
        });
        try {
            var jobs = new ArrayList<LoginChainVerifier.Verification>();
            for (int i = 0; i < 34; i++) {
                jobs.add(verifier.submit(new byte[1], (r, f) -> callbacks.incrementAndGet()));
                assertNotNull(jobs.get(i));
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertNull(verifier.submit(new byte[1], (r, f) -> fail("unbounded jobs")));
            jobs.get(0).cancel();
            assertNull(verifier.submit(new byte[1], (r, f) -> fail("running DNS still owns memory")));
            jobs.get(2).cancel();
            assertNotNull(verifier.submit(new byte[1], (r, f) -> callbacks.incrementAndGet()));
            long before = System.nanoTime();
            verifier.shutdown();
            assertTrue(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(1));
            assertNull(verifier.submit(new byte[1], (r, f) -> fail("after shutdown")));
        } finally {
            unblock.countDown();
            verifier.shutdown();
        }
        assertEquals(0, callbacks.get());
    }

    @Test
    void cancellingACompletedResultDropsItsIdentityAndReleasesTheByteReservation() throws Exception {
        LoginChainVerifier verifier = new LoginChainVerifier(1, 1, 1, packet -> null);
        AtomicInteger callbacks = new AtomicInteger();
        try {
            LoginChainVerifier.Verification job = verifier.submit(new byte[1], (r, f) -> callbacks.incrementAndGet());
            assertNotNull(job);
            waitForFinished();
            assertNull(verifier.submit(new byte[1], (r, f) -> fail("completed result still occupies byte budget")));
            job.cancel();
            AsyncTask.collectTask();
            assertEquals(0, callbacks.get());
            assertNotNull(verifier.submit(new byte[1], (r, f) -> {}));
        } finally {
            verifier.shutdown();
        }
    }

    @Test
    void realJwtSignatureExpiryIssuerAndAudienceStillFailClosedOffThread() throws Exception {
        MockServer.init();
        KeyPair trusted = EncryptionUtils.createKeyPair(), attacker = EncryptionUtils.createKeyPair();
        Field consumer = Class.forName(EncryptionUtils.class.getName() + "$JwtConsumerHolder")
                .getDeclaredField("MOJANG_CONSUMER");
        consumer.setAccessible(true);
        Object previous = consumer.get(null);
        consumer.set(null, new JwtConsumerBuilder().setVerificationKey(trusted.getPublic())
                .setRequireExpirationTime().setRequireSubject().setExpectedIssuer("test-issuer")
                .setExpectedAudience(true, "api://auth-minecraft-services/multiplayer").build());
        LoginChainVerifier verifier = new LoginChainVerifier(2, 32, 1024 * 1024, ClientChainData::of);
        try {
            long future = System.currentTimeMillis() / 1000 + 600;
            for (byte[] packet : new byte[][] {
                    token(attacker, trusted, future, "test-issuer", "api://auth-minecraft-services/multiplayer"),
                    token(trusted, trusted, 1, "test-issuer", "api://auth-minecraft-services/multiplayer"),
                    token(trusted, trusted, future, "wrong-issuer", "api://auth-minecraft-services/multiplayer"),
                    token(trusted, trusted, future, "test-issuer", "wrong-audience")}) {
                AtomicReference<ClientChainData> identity = new AtomicReference<>();
                AtomicReference<Throwable> error = new AtomicReference<>();
                assertNotNull(verifier.submit(packet, (r, f) -> { identity.set(r); error.set(f); }));
                waitForFinished();
                AsyncTask.collectTask();
                assertNull(identity.get());
                assertNotNull(error.get());
            }
            AtomicReference<ClientChainData> identity = new AtomicReference<>();
            assertNotNull(verifier.submit(token(trusted, trusted, future, "test-issuer",
                    "api://auth-minecraft-services/multiplayer"), (r, f) -> {
                assertNull(f);
                identity.set(r);
            }));
            waitForFinished();
            AsyncTask.collectTask();
            assertTrue(identity.get().isXboxAuthed());
            assertEquals("VerifiedPlayer", identity.get().getUsername());
            assertTrue(identity.get().isAuthenticationCurrent());
            Field expiration = ClientChainData.class.getDeclaredField("authenticationExpiresAt");
            expiration.setAccessible(true);
            expiration.setLong(identity.get(), 1);
            assertFalse(identity.get().isAuthenticationCurrent(), "expired completed result cannot reach login");
        } finally {
            verifier.shutdown();
            consumer.set(null, previous);
        }
    }

    private static byte[] token(KeyPair signer, KeyPair identity, long expires, String issuer, String audience) throws Exception {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(EncryptionUtils.ALGORITHM_TYPE);
        signature.setKey(signer.getPrivate());
        signature.setPayload(new Gson().toJson(Map.of("exp", expires, "iss", issuer, "aud", audience,
                "sub", "player", "xname", "VerifiedPlayer", "xid", "2535412345678901",
                "cpk", Base64.getEncoder().encodeToString(identity.getPublic().getEncoded()))));
        byte[] auth = new Gson().toJson(Map.of("AuthenticationType", 0, "Token", signature.getCompactSerialization()))
                .getBytes(StandardCharsets.UTF_8);
        byte[] skin = "header.e30=.signature".getBytes(StandardCharsets.UTF_8);
        BinaryStream buffer = new BinaryStream();
        buffer.putLInt(auth.length);
        buffer.put(auth);
        buffer.putLInt(skin.length);
        buffer.put(skin);
        return buffer.getBuffer();
    }

    private static void waitForFinished() throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (AsyncTask.FINISHED_LIST.isEmpty() && System.nanoTime() < until) {
            Thread.sleep(1);
        }
        assertFalse(AsyncTask.FINISHED_LIST.isEmpty(), "verification did not complete");
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
