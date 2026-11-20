package cn.nukkit.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 应答装饰器：域名解析出的地址按该次加入补进候选，端口沿用内部候选，IP 字面量与未知 networkId 不动。
 * Answer decorator: the address a typed domain resolves to joins the candidates of that join on
 * the internal port, while IP literals and unknown network IDs stay untouched.
 */
class NetherNetHostAnswerSignalingTest {

    private static final String ANSWER = "v=0\r\n"
            + "o=rtc 3276522793 0 IN IP4 127.0.0.1\r\n"
            + "s=-\r\n"
            + "t=0 0\r\n"
            + "a=group:BUNDLE 0\r\n"
            + "a=fingerprint:sha-256 E5:36:EF:D7:E8:A4:46:33:70:CD:28:08:C1:4F:24:FA:31:B2:17:48:66:47:29:F0:AE:3A:08:08:4F:DE:57:03\r\n"
            + "m=application 19134 UDP/DTLS/SCTP webrtc-datachannel\r\n"
            + "c=IN IP4 127.0.0.1\r\n"
            + "a=mid:0\r\n"
            + "a=setup:active\r\n"
            + "a=ice-ufrag:EkS2\r\n"
            + "a=ice-pwd:U6L845lAZWUnjy9DmYJtxe\r\n"
            + "a=candidate:1 1 UDP 2114977791 127.0.0.1 19134 typ host\r\n"
            + "a=end-of-candidates\r\n"
            + "\r\n";

    private static InetAddress address(String literal) throws java.net.UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @Test
    void appendsTheResolvedAddressOnTheInternalPort() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        NetherNetHostCandidates candidates = new NetherNetHostCandidates(now::get,
                host -> new InetAddress[]{address("8.8.8.8")});
        try {
            candidates.observe("network", null, "play.example.com:19132");
            awaitResolution(candidates, "network");
            Capturing delegate = new Capturing();
            new NetherNetHostAnswerSignaling(delegate, candidates, false, NetherNetSharedPortSdp.PortSource.SHARED)
                    .sendDescription("network", ANSWER);

            assertTrue(delegate.sdp.contains("8.8.8.8 19134 typ host"), delegate.sdp);
            assertTrue(delegate.sdp.indexOf("8.8.8.8 19134") < delegate.sdp.indexOf("a=end-of-candidates"));

            // 随后的共用端口改写把它平移到对外端口 The shared-port rewrite that follows moves it to the public port
            String rewritten = NetherNetSharedPortSdp.rewriteAnswer(delegate.sdp, 19134,
                    List.of(address("192.168.1.5")), new NetherNetSharedPortSdp.ExternalPorts(19132, -1));
            assertTrue(rewritten.contains("8.8.8.8 19132 typ host"), rewritten);
            assertFalse(rewritten.contains("8.8.8.8 19134"), rewritten);
        } finally {
            candidates.close();
        }
    }

    @Test
    void anotherOfferReplacesTheAbandonedHost() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        NetherNetHostCandidates candidates = new NetherNetHostCandidates(now::get,
                host -> new InetAddress[]{address(host.equals("first.example.com") ? "8.8.8.8" : "1.1.1.1")});
        try {
            candidates.observe("network", null, "first.example.com");
            awaitResolution(candidates, "network");
            // 首个加入早已结束（超出应答窗口），重试换了域名
            // The first join is long over (past the answer window), and the retry types another host
            now.addAndGet(NetherNetHostCandidates.MAPPING_REPLACEMENT_GRACE_MILLIS);
            candidates.observe("network", null, "second.example.com");
            awaitAddress(candidates, "network", "1.1.1.1");

            Capturing delegate = new Capturing();
            new NetherNetHostAnswerSignaling(delegate, candidates, false, NetherNetSharedPortSdp.PortSource.SHARED)
                    .sendDescription("network", ANSWER);

            assertTrue(delegate.sdp.contains("1.1.1.1 19134 typ host"), delegate.sdp);
            assertFalse(delegate.sdp.contains("8.8.8.8"), delegate.sdp);
        } finally {
            candidates.close();
        }
    }

    @Test
    void aDuplicateOfferDoesNotChangeThePendingJoinsCandidates() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        NetherNetHostCandidates candidates = new NetherNetHostCandidates(now::get, host -> {
            if (host.startsWith("duplicate")) {
                throw new UnknownHostException("the refused offer's host is unreachable");
            }
            return new InetAddress[]{address("8.8.8.8")};
        });
        try {
            candidates.observe("network", null, "play.example.com");
            awaitResolution(candidates, "network");
            // 首个加入仍在等应答；这次 offer 随后会被上游按重复 networkId 拒绝，它的域名不得参与应答
            // The first join is still waiting; upstream then refuses this offer as a duplicate, so its host must not reach the answer
            candidates.observe("network", null, "duplicate.example.com");

            Capturing delegate = new Capturing();
            new NetherNetHostAnswerSignaling(delegate, candidates, false, NetherNetSharedPortSdp.PortSource.SHARED)
                    .sendDescription("network", ANSWER);

            assertTrue(delegate.sdp.contains("8.8.8.8 19134 typ host"), delegate.sdp);
        } finally {
            candidates.close();
        }
    }

    @Test
    void completedAnswerDoesNotLeakItsDomainIntoAnIpJoin() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        NetherNetHostCandidates candidates = new NetherNetHostCandidates(now::get,
                host -> new InetAddress[]{address("8.8.8.8")});
        try {
            Capturing delegate = new Capturing();
            NetherNetHostAnswerSignaling signaling = new NetherNetHostAnswerSignaling(delegate, candidates,
                    false, NetherNetSharedPortSdp.PortSource.SHARED);
            candidates.observe("network", null, "play.example.com");
            awaitResolution(candidates, "network");
            signaling.sendDescription("network", ANSWER);
            assertTrue(delegate.sdp.contains("8.8.8.8"), delegate.sdp);

            candidates.observe("network", null, "1.2.3.4:19132");
            signaling.sendDescription("network", ANSWER);
            assertEquals(ANSWER, delegate.sdp, "the previous answer consumed its domain mapping");
        } finally {
            candidates.close();
        }
    }

    @Test
    void leavesAnswersWithoutAMatchingJoinAlone() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        NetherNetHostCandidates candidates = new NetherNetHostCandidates(now::get, host -> {
            throw new AssertionError("nothing should be resolved");
        });
        try {
            Capturing delegate = new Capturing();
            NetherNetHostAnswerSignaling signaling = new NetherNetHostAnswerSignaling(delegate, candidates,
                    false, NetherNetSharedPortSdp.PortSource.SHARED);

            signaling.sendDescription("unknown", ANSWER);
            assertEquals(ANSWER, delegate.sdp, "no mapping means the answer is untouched");

            candidates.observe("network", null, "1.2.3.4:19132");
            signaling.sendDescription("network", ANSWER);
            assertEquals(ANSWER, delegate.sdp, "an IP literal is upstream's own case");
        } finally {
            candidates.close();
        }
    }

    @Test
    void waitsForColdResolutionBeforeAnswering() throws Exception {
        java.util.concurrent.CountDownLatch resolving = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicLong now = new AtomicLong(1_000);
        NetherNetHostCandidates candidates = new NetherNetHostCandidates(now::get, host -> {
            resolving.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.net.UnknownHostException("interrupted");
            }
            return new InetAddress[]{address("8.8.8.8")};
        });
        try {
            candidates.observe("network", null, "play.example.com");
            assertTrue(resolving.await(2, java.util.concurrent.TimeUnit.SECONDS));
            Capturing delegate = new Capturing();
            new NetherNetHostAnswerSignaling(delegate, candidates, false, NetherNetSharedPortSdp.PortSource.SHARED,
                    5_000).sendDescription("network", ANSWER);

            assertTrue(delegate.sdp == null, "a cold lookup must not send an incomplete answer");
            release.countDown();
            awaitAnswer(delegate);
            assertTrue(delegate.sdp.contains("8.8.8.8 19134 typ host"), delegate.sdp);
        } finally {
            release.countDown();
            candidates.close();
        }
    }

    @Test
    void answersWithoutTheCandidateWhenTheLookupMissesTheWindow() throws Exception {
        java.util.concurrent.CountDownLatch resolving = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicLong now = new AtomicLong(1_000);
        NetherNetHostCandidates candidates = new NetherNetHostCandidates(now::get, host -> {
            resolving.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.net.UnknownHostException("interrupted");
            }
            return new InetAddress[]{address("8.8.8.8")};
        });
        try {
            candidates.observe("network", null, "play.example.com");
            assertTrue(resolving.await(2, java.util.concurrent.TimeUnit.SECONDS));
            Capturing delegate = new Capturing();
            new NetherNetHostAnswerSignaling(delegate, candidates, false, NetherNetSharedPortSdp.PortSource.SHARED,
                    50).sendDescription("network", ANSWER);

            awaitAnswer(delegate);
            assertEquals(ANSWER, delegate.sdp, "the answer goes out unchanged once the budget is spent");
        } finally {
            release.countDown();
            candidates.close();
        }
    }

    private static void awaitResolution(NetherNetHostCandidates candidates, String networkId) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (candidates.addressesFor(networkId).isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    private static void awaitAddress(NetherNetHostCandidates candidates, String networkId, String expected)
            throws Exception {
        List<InetAddress> wanted = List.of(address(expected));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (!wanted.equals(candidates.addressesFor(networkId)) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    private static void awaitAnswer(Capturing delegate) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (delegate.sdp == null && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(delegate.sdp != null, "the answer should follow the completed lookup");
    }

    private static final class Capturing extends NetherNetDelegatingSignaling {

        private volatile String sdp;

        private Capturing() {
            super(null);
        }

        @Override
        public void sendDescription(String targetNetworkId, String sdp) {
            this.sdp = sdp;
        }
    }
}
