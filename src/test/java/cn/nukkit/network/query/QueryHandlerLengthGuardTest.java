package cn.nukkit.network.query;

import cn.nukkit.Server;
import cn.nukkit.network.Network;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
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
    void truncatedHandshakeIsIgnoredAtNonzeroReaderIndex(int length) throws Exception {
        Fixture f = new Fixture();
        f.dispatch(Arrays.copyOf(f.request(QueryHandler.HANDSHAKE, null, 0), length), false);
        verifyNoInteractions(f.network);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void truncatedStatisticsIsIgnoredWithoutReadingBeyondPayload(int length) throws Exception {
        Fixture f = new Fixture();
        f.dispatch(Arrays.copyOf(f.request(QueryHandler.STATISTICS, f.challenge(false), 0), length), false);
        verifyNoInteractions(f.network);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void truncatedStatisticsThroughServerNeverBlocksSender(int length) throws Exception {
        Fixture f = new Fixture();
        f.dispatch(Arrays.copyOf(f.request(QueryHandler.STATISTICS, f.challenge(false), 0), length), true);
        verifyNoInteractions(f.network);
    }

    @Test
    void handshakeReplyStillEchoesSessionAndCurrentChallenge() throws Exception {
        Fixture f = new Fixture();
        f.dispatch(f.request(QueryHandler.HANDSHAKE, null, 0), true);
        assertArrayEquals(f.request(QueryHandler.HANDSHAKE, f.challenge(false), 1), f.singleReply());
    }

    @Test
    void basicStatisticsStillAcceptCurrentAndPreviousChallenge() throws Exception {
        for (boolean previous : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.dispatch(f.request(QueryHandler.STATISTICS, f.challenge(previous), 0), true);
            assertArrayEquals(f.reply(f.shortData), f.singleReply());
            verify(f.network, never()).blockAddress(any(), anyInt());
        }
    }

    @Test
    void existingLongStatisticsSelectorAndFourBytePaddingRemainUnchanged() throws Exception {
        for (int padding : new int[]{4, 8}) {
            Fixture f = new Fixture();
            f.dispatch(f.request(QueryHandler.STATISTICS, f.challenge(false), padding), true);
            assertArrayEquals(f.reply(padding == 8 ? f.longData : f.shortData), f.singleReply());
        }
    }

    @Test
    void completeStatisticsWithInvalidChallengeStillGetsNoReply() throws Exception {
        Fixture f = new Fixture();
        byte[] invalid = new byte[4];
        assertFalse(Arrays.equals(invalid, f.challenge(false)));
        assertFalse(Arrays.equals(invalid, f.challenge(true)));
        f.dispatch(f.request(QueryHandler.STATISTICS, invalid, 0), true);
        verifyNoInteractions(f.network);
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final Network network = mock(Network.class);
        final QueryHandler handler = mock(QueryHandler.class, CALLS_REAL_METHODS);
        final byte[] currentToken = {1, 2, 3, 4, 5, 6, 7, 8};
        final byte[] previousToken = {8, 7, 6, 5, 4, 3, 2, 1};
        final byte[] shortData = {11, 12, 13};
        final byte[] longData = {21, 22, 23, 24};
        final List<byte[]> replies = new ArrayList<>();

        Fixture() throws Exception {
            when(server.getNetwork()).thenReturn(network);
            set(server, "network", network);
            set(server, "queryHandler", handler);
            set(handler, "server", server);
            set(handler, "token", currentToken);
            set(handler, "lastToken", previousToken);
            set(handler, "shortData", shortData);
            set(handler, "longData", longData);
            set(handler, "timeout", Long.MAX_VALUE);
            doCallRealMethod().when(server).handlePacket(eq(ADDRESS), any(ByteBuf.class));
            doAnswer(call -> {
                ByteBuf reply = call.getArgument(1);
                try {
                    replies.add(ByteBufUtil.getBytes(reply));
                } finally {
                    reply.release();
                }
                return null;
            }).when(network).sendPacket(eq(ADDRESS), any(ByteBuf.class));
        }

        byte[] challenge(boolean previous) {
            return QueryHandler.getTokenString(previous ? previousToken : currentToken, ADDRESS.getAddress());
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

        void dispatch(byte[] body, boolean throughServer) {
            ByteBuf packet = Unpooled.buffer(64);
            try {
                packet.writeZero(3); // Readable bytes, rather than capacity or absolute index, bound parsing.
                packet.readerIndex(3);
                if (throughServer) packet.writeByte(0xfe).writeByte(0xfd);
                packet.writeBytes(body);
                assertDoesNotThrow(() -> {
                    if (throughServer) server.handlePacket(ADDRESS, packet);
                    else handler.handle(ADDRESS, packet);
                });
            } finally {
                packet.release();
            }
        }

        byte[] singleReply() {
            assertEquals(1, replies.size());
            return replies.get(0);
        }

        private static void set(Object target, String name, Object value) throws Exception {
            Field field = (target instanceof Server ? Server.class : QueryHandler.class).getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}
