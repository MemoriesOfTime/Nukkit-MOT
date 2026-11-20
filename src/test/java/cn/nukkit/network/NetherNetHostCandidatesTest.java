package cn.nukkit.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 域名候选缓存：只留对端可达的地址、按 networkId 取用、正负缓存与 TTL、IP 字面量跳过、解析预算。
 * Domain candidate cache: reachable addresses only, per-network-ID lookup, positive and negative
 * caching with TTL, IP literals skipped, and the lookup budget.
 */
class NetherNetHostCandidatesTest {

    private static InetAddress address(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @Test
    void resolvesOnceAndServesEveryJoin() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        try (Fixture fixture = new Fixture(new AtomicLong(1_000), host -> {
            lookups.incrementAndGet();
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            fixture.observe("n1", "Play.Example.com:19132");
            awaitResolution(fixture, "n1", 1);
            assertEquals(List.of(address("8.8.8.8")), fixture.candidates.addressesFor("n1"));

            fixture.observe("n2", "play.example.com");
            assertEquals(List.of(address("8.8.8.8")), fixture.candidates.addressesFor("n2"),
                    "the cached answer serves the second join at once");
            assertEquals(1, lookups.get(), "the name is resolved once and cached");
        }
    }

    @Test
    void keepsAddressesAPeerCouldReach() throws Exception {
        try (Fixture fixture = new Fixture(new AtomicLong(1_000), host -> new InetAddress[]{
                address("10.1.2.3"), address("127.0.0.1"), address("169.254.169.254"),
                address("203.0.113.7"), address("8.8.8.8"), address("fd00::1"), address("fe80::1")})) {
            fixture.observe("n", "play.example.com");
            awaitResolution(fixture, "n", 3);
            assertEquals(List.of(address("10.1.2.3"), address("8.8.8.8"), address("fd00::1")),
                    fixture.candidates.addressesFor("n"),
                    "private and unique-local addresses stay, loopback and special-purpose ranges go");
        }
    }

    @Test
    void capsAddressesPerFamily() throws Exception {
        InetAddress[] resolved = new InetAddress[12];
        for (int i = 0; i < resolved.length; i++) {
            resolved[i] = address("8.8.8." + i);
        }
        try (Fixture fixture = new Fixture(new AtomicLong(1_000), host -> resolved)) {
            fixture.observe("n", "play.example.com");
            awaitResolution(fixture, "n", NetherNetSharedPortSdp.MAX_ADDRESSES_PER_FAMILY);
            assertEquals(NetherNetSharedPortSdp.MAX_ADDRESSES_PER_FAMILY, fixture.candidates.addressesFor("n").size());
        }
    }

    @Test
    void skipsIpLiteralsAndMalformedHosts() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        try (Fixture fixture = new Fixture(new AtomicLong(1_000), host -> {
            lookups.incrementAndGet();
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            for (String host : new String[]{"1.2.3.4", "1.2.3.4:19132", "[2606:4700:4700::1111]:19132",
                    "2606:4700:4700::1111", "", "play.example.com:abc", "999.1.1.1", "localhost", "bad name.com"}) {
                fixture.observe("n", host);
                assertTrue(fixture.candidates.addressesFor("n").isEmpty(), "no candidate for " + host);
            }
            fixture.observe("n", null);
            assertEquals(0, lookups.get(), "nothing worth resolving reached the resolver");
        }
    }

    @Test
    void negativeAnswersExpireAfterThirtySeconds() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        AtomicLong now = new AtomicLong(10_000);
        try (Fixture fixture = new Fixture(now, host -> {
            if (lookups.incrementAndGet() == 1) {
                throw new UnknownHostException("first lookup fails");
            }
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            fixture.observe("n1", "play.example.com");
            awaitLookups(lookups, 1);
            assertTrue(fixture.candidates.addressesFor("n1").isEmpty());

            fixture.observe("n2", "play.example.com");
            assertEquals(1, lookups.get(), "a negative answer is cached");

            now.addAndGet(TimeUnit.SECONDS.toMillis(31));
            fixture.observe("n3", "play.example.com");
            awaitLookups(lookups, 2);
            awaitResolution(fixture, "n3", 1);
            assertEquals(List.of(address("8.8.8.8")), fixture.candidates.addressesFor("n3"));
        }
    }

    @Test
    void expiredAnswersAreResolvedAgain() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        AtomicLong now = new AtomicLong(10_000);
        try (Fixture fixture = new Fixture(now, host -> new InetAddress[]{
                address(lookups.incrementAndGet() == 1 ? "8.8.8.8" : "1.1.1.1")})) {
            fixture.observe("n1", "play.example.com");
            awaitResolution(fixture, "n1", 1);
            assertEquals(List.of(address("8.8.8.8")), fixture.candidates.addressesFor("n1"));

            now.addAndGet(TimeUnit.MINUTES.toMillis(6));
            fixture.observe("n2", "play.example.com");
            awaitAddress(fixture, "n2", "1.1.1.1");
            assertEquals(List.of(address("1.1.1.1")), fixture.candidates.addressesFor("n2"),
                    "a DDNS change lands once the cached answer expires");
        }
    }

    @Test
    void forgetsMappingsAfterTwoMinutes() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        try (Fixture fixture = new Fixture(now, host -> new InetAddress[]{address("8.8.8.8")})) {
            fixture.observe("n", "play.example.com");
            awaitResolution(fixture, "n", 1);
            now.addAndGet(TimeUnit.MINUTES.toMillis(3));
            assertTrue(fixture.candidates.addressesFor("n").isEmpty());
        }
    }

    @Test
    void anotherHostReplacesTheAbandonedJoinsMapping() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        try (Fixture fixture = new Fixture(now,
                host -> new InetAddress[]{address(host.startsWith("first") ? "8.8.8.8" : "1.1.1.1")})) {
            fixture.observe("n", "first.example.com");
            awaitResolution(fixture, "n", 1);
            // 首个加入早已结束（超出应答窗口），重试换了域名
            // The first join is long over (past the answer window), and the retry types another host
            now.addAndGet(NetherNetHostCandidates.MAPPING_REPLACEMENT_GRACE_MILLIS);
            fixture.observe("n", "second.example.com");
            awaitAddress(fixture, "n", "1.1.1.1");
            assertEquals(List.of(address("1.1.1.1")), fixture.candidates.addressesFor("n"));
        }
    }

    @Test
    void aDuplicateOffersHostDoesNotReplaceThePendingJoinsMapping() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        try (Fixture fixture = new Fixture(now,
                host -> new InetAddress[]{address(host.startsWith("first") ? "8.8.8.8" : "1.1.1.1")})) {
            fixture.observe("n", "first.example.com");
            awaitResolution(fixture, "n", 1);
            // 首个加入仍在等应答，这次 offer 随后会被上游按重复 networkId 拒绝
            // The first join is still waiting for its answer, and upstream then refuses this offer as a duplicate
            now.addAndGet(NetherNetHostCandidates.MAPPING_REPLACEMENT_GRACE_MILLIS / 2);
            fixture.observe("n", "second.example.com");
            assertEquals(List.of(address("8.8.8.8")), fixture.candidates.addressesFor("n"),
                    "a duplicate offer leaves the pending join's candidates alone");
        }
    }

    @Test
    void aRepeatedObservationKeepsTheFirstMappingDeadline() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        try (Fixture fixture = new Fixture(now, host -> new InetAddress[]{address("8.8.8.8")})) {
            fixture.observe("n", "play.example.com");
            awaitResolution(fixture, "n", 1);
            now.addAndGet(TimeUnit.SECONDS.toMillis(100));
            fixture.observe("n", "play.example.com");
            now.addAndGet(TimeUnit.SECONDS.toMillis(21));
            assertTrue(fixture.candidates.addressesFor("n").isEmpty(),
                    "a repeated offer does not extend the mapping");
        }
    }

    @Test
    void aHungLookupDoesNotHoldTheName() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Fixture fixture = new Fixture(new AtomicLong(1_000), host -> {
            if (lookups.incrementAndGet() == 1) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UnknownHostException("interrupted");
                }
            }
            return new InetAddress[]{address("8.8.8.8")};
        }, 1);
        try {
            fixture.observe("n1", "play.example.com");
            awaitLookups(lookups, 1);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (lookups.get() < 2 && System.nanoTime() < deadline) {
                fixture.observe("n2", "play.example.com");
                Thread.sleep(10);
            }
            assertTrue(lookups.get() >= 2, "a timed-out lookup no longer blocks the name");
            release.countDown();
            awaitResolution(fixture, "n2", 1);
        } finally {
            release.countDown();
            fixture.close();
        }
    }

    @Test
    void stopsResolvingBeyondTheMinuteBudget() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        try (Fixture fixture = new Fixture(new AtomicLong(0), host -> {
            lookups.incrementAndGet();
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            for (int i = 0; i < 35; i++) {
                fixture.observe("n" + i, "host" + i + ".example.com");
                if (i < 20) {
                    awaitLookups(lookups, i + 1);
                }
            }
            assertEquals(20, lookups.get(), "the budget caps what a Host header can make the server resolve");
        }
    }

    @Test
    void capsLookupsPerRemoteAddress() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        InetAddress attacker = address("198.51.100.9");
        InetAddress other = address("198.51.100.10");
        try (Fixture fixture = new Fixture(new AtomicLong(0), host -> {
            lookups.incrementAndGet();
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            for (int i = 0; i < 5; i++) {
                fixture.candidates.observe("n" + i, attacker, "host" + i + ".example.com");
                if (i < 3) {
                    awaitLookups(lookups, i + 1);
                }
            }
            assertEquals(3, lookups.get(), "one address cannot drain the global budget");
            fixture.candidates.observe("n9", other, "other.example.com");
            awaitLookups(lookups, 4);
            assertEquals(4, lookups.get(), "another address keeps its own allowance");
        }
    }

    @Test
    void normalizesCaseAndTheRootDot() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        try (Fixture fixture = new Fixture(new AtomicLong(1_000), host -> {
            lookups.incrementAndGet();
            assertEquals("play.example.com", host);
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            fixture.candidates.prefetch(null, "PLAY.Example.COM.");
            awaitLookups(lookups, 1);
            fixture.observe("n", "Play.Example.COM:19132");
            awaitResolution(fixture, "n", 1);
            assertEquals(1, lookups.get(), "another spelling of the same name hits the cache");
            assertEquals(List.of(address("8.8.8.8")), fixture.candidates.addressesFor("n"));
        }
    }

    @Test
    void prefetchWarmsTheCacheWithoutAJoin() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        AtomicLong now = new AtomicLong(1_000);
        try (Fixture fixture = new Fixture(now, host -> {
            lookups.incrementAndGet();
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            fixture.candidates.prefetch(null, "play.example.com");
            awaitLookups(lookups, 1);
            assertTrue(fixture.candidates.addressesFor("n").isEmpty(), "no join yet, so no candidate is mapped");

            fixture.observe("n", "play.example.com");
            awaitResolution(fixture, "n", 1);
            assertEquals(1, lookups.get(), "the join reuses the warmed answer");
            assertEquals(List.of(address("8.8.8.8")), fixture.candidates.addressesFor("n"));

            now.addAndGet(TimeUnit.MINUTES.toMillis(6));
            fixture.candidates.prefetch(null, "play.example.com");
            awaitLookups(lookups, 2);
            assertEquals(2, lookups.get(), "an expired answer is refreshed in the background");
        }
    }

    private static void awaitResolution(Fixture fixture, String networkId, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (fixture.candidates.addressesFor(networkId).size() != expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    /** 轮询到新值出现：旧解析可能先被读到，只等非空会读到过期值。 Polls for the new value: the stale answer may be served first, so a non-empty check would return too early. */
    private static void awaitAddress(Fixture fixture, String networkId, String expected) throws Exception {
        List<InetAddress> wanted = List.of(address(expected));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!wanted.equals(fixture.candidates.addressesFor(networkId)) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    private static void awaitLookups(AtomicInteger lookups, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (lookups.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final NetherNetHostCandidates candidates;

        private Fixture(AtomicLong clock, NetherNetHostCandidates.HostResolver resolver) {
            this.candidates = new NetherNetHostCandidates(clock::get, resolver);
        }

        private Fixture(AtomicLong clock, NetherNetHostCandidates.HostResolver resolver, long lookupTimeoutMillis) {
            this.candidates = new NetherNetHostCandidates(clock::get, resolver, lookupTimeoutMillis);
        }

        private void observe(String networkId, String host) {
            this.candidates.observe(networkId, null, host);
        }

        @Override
        public void close() {
            this.candidates.close();
        }
    }
}
