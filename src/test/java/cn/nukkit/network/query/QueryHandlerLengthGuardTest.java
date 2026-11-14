package cn.nukkit.network.query;

import cn.nukkit.Server;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.network.Network;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QueryHandlerLengthGuardTest {
    private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 19132);
    private static final int SESSION = 0x12345678;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void truncatedHandshakeIsIgnoredAtNonzeroReaderIndex(int length) {
        Fixture f = new Fixture();
        f.dispatch(Arrays.copyOf(f.request(QueryHandler.HANDSHAKE, null, 0), length));
        verifyNoInteractions(f.network);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void truncatedStatisticsIsIgnoredWithoutReadingBeyondPayload(int length) {
        Fixture f = new Fixture();
        f.dispatch(Arrays.copyOf(f.request(QueryHandler.STATISTICS, f.challenge(false), 0), length));
        verifyNoInteractions(f.network);
    }

    @Test
    void handshakeReplyStillEchoesSessionAndCurrentChallenge() {
        Fixture f = new Fixture();
        f.dispatch(f.request(QueryHandler.HANDSHAKE, null, 0));
        assertArrayEquals(f.request(QueryHandler.HANDSHAKE, f.challenge(false), 1), f.singleReply());
    }

    @Test
    void basicStatisticsStillAcceptCurrentAndPreviousChallenge() {
        for (boolean previous : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.dispatch(f.request(QueryHandler.STATISTICS, f.challenge(previous), 0));
            assertArrayEquals(f.reply(f.shortData), f.singleReply());
            verify(f.network, never()).blockAddress(any(), anyInt());
        }
    }

    @Test
    void existingLongStatisticsSelectorAndFourBytePaddingRemainUnchanged() {
        for (int padding : new int[]{4, 8}) {
            Fixture f = new Fixture();
            f.dispatch(f.request(QueryHandler.STATISTICS, f.challenge(false), padding));
            assertArrayEquals(f.reply(padding == 8 ? f.longData : f.shortData), f.singleReply());
        }
    }

    @Test
    void completeStatisticsWithInvalidChallengeStillGetsNoReply() {
        Fixture f = new Fixture();
        byte[] invalid = new byte[4];
        assertFalse(Arrays.equals(invalid, f.challenge(false)));
        assertFalse(Arrays.equals(invalid, f.challenge(true)));
        f.dispatch(f.request(QueryHandler.STATISTICS, invalid, 0));
        verifyNoInteractions(f.network);
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final Network network = mock(Network.class);
        final QueryRegenerateEvent queryInfo = mock(QueryRegenerateEvent.class);
        final QueryHandler handler;
        final byte[] shortData = {11, 12, 13};
        final byte[] longData = {21, 22, 23, 24};
        final List<byte[]> replies = new ArrayList<>();
        private byte[] currentChallenge;
        private byte[] previousChallenge;

        Fixture() {
            when(server.getNetwork()).thenReturn(network);
            when(server.getQueryInformation()).thenReturn(queryInfo);
            when(queryInfo.getShortQuery()).thenReturn(shortData);
            when(queryInfo.getLongQuery()).thenReturn(longData);
            when(queryInfo.getTimeout()).thenReturn(Integer.MAX_VALUE);

            // 静态 mock 仅覆盖构造期对 Server.getInstance() 的读取；handle() 之后只触达实例字段。
            // The static mock only covers Server.getInstance() during construction;
            // handle() afterwards only touches instance state.
            try (MockedStatic<Server> servers = mockStatic(Server.class)) {
                servers.when(Server::getInstance).thenReturn(server);
                handler = new QueryHandler();
            }
            doAnswer(call -> {
                ByteBuf reply = call.getArgument(1);
                try {
                    replies.add(ByteBufUtil.getBytes(reply));
                } finally {
                    reply.release();
                }
                return null;
            }).when(network).sendPacket(eq(ADDRESS), any(ByteBuf.class));

            // 通过握手回包观察随机 token 派生的 challenge；轮换一次后当前/上一代各得其一。
            // Observe the challenge derived from the random token via the handshake reply;
            // rotate once so each generation gets its own.
            previousChallenge = handshakeChallenge();
            handler.regenerateToken();
            currentChallenge = handshakeChallenge();
        }

        byte[] challenge(boolean previous) {
            return previous ? previousChallenge : currentChallenge;
        }

        private byte[] handshakeChallenge() {
            dispatch(request(QueryHandler.HANDSHAKE, null, 0));
            byte[] reply = singleReply();
            replies.clear();
            clearInvocations(network);
            return Arrays.copyOfRange(reply, 5, 9); // type(1) + session(4), then the 4-byte challenge
        }

        byte[] request(byte type, byte[] challenge, int padding) {
            ByteBuf packet = Unpooled.buffer();
            try {
                packet.writeByte(type).writeInt(SESSION);
                if (challenge != null) packet.writeBytes(challenge);
                packet.writeZero(padding);
                return ByteBufUtil.getBytes(packet);
            } finally {
                packet.release();
            }
        }

        byte[] reply(byte[] data) {
            byte[] reply = request(QueryHandler.STATISTICS, null, data.length);
            System.arraycopy(data, 0, reply, 5, data.length);
            return reply;
        }

        void dispatch(byte[] body) {
            ByteBuf packet = Unpooled.buffer(64);
            try {
                packet.writeZero(3); // Readable bytes, rather than capacity or absolute index, bound parsing.
                packet.readerIndex(3);
                packet.writeBytes(body);
                assertDoesNotThrow(() -> handler.handle(ADDRESS, packet));
            } finally {
                packet.release();
            }
        }

        byte[] singleReply() {
            assertEquals(1, replies.size());
            return replies.get(0);
        }
    }
}
