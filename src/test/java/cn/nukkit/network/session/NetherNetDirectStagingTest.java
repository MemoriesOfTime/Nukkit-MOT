package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.ProtocolInfo;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ThreadExecutorMap;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannel;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Isolated("Temporarily substitutes the default adaptive direct pool")
@Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SAME_THREAD)
public class NetherNetDirectStagingTest {
    private static final int PIECE = 9999;
    private static final Method SEND = method(NetherNetPlayerSession.class, "sendPacket", byte[].class);
    private static final Method SEGMENT = method(NetherNetChannel.class, "segment", ByteBuf.class, ByteBufAllocator.class, int.class, Consumer.class);
    private static final GameVersion[] VERSIONS = {GameVersion.V1_20_0, GameVersion.V1_20_60,
            GameVersion.V1_26_20_26, GameVersion.V1_26_40, GameVersion.V1_26_50};

    private static final class Fixture {
        final NetherNetPlayerSession session;
        final AtomicReference<ByteBuf> output = new AtomicReference<>();
        Fixture(GameVersion version, boolean initialized) { this(version, initialized, CompressionProvider.NONE); }
        Fixture(GameVersion version, boolean initialized, CompressionProvider compression) {
            EventLoop loop = mock(EventLoop.class);
            io.netty.util.concurrent.ScheduledFuture<?> timer = mock(io.netty.util.concurrent.ScheduledFuture.class);
            doReturn(timer).when(loop).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            ChannelFuture future = mock(ChannelFuture.class);
            when(future.addListener(any())).thenReturn(future);
            NetherNetChildChannel channel = mock(NetherNetChildChannel.class);
            when(channel.eventLoop()).thenReturn(loop);
            when(channel.closeFuture()).thenReturn(future);
            doAnswer(call -> {
                if (!output.compareAndSet(null, call.getArgument(0))) throw new AssertionError("one outbound owner");
                return future;
            }).when(channel).writeAndFlush(any(ByteBuf.class));
            session = new NetherNetPlayerSession(null, channel);
            session.getState().getProtocol().setGameVersion(version);
            if (initialized) session.setCompression(compression);
        }
        ByteBuf send(byte[] bytes) throws Exception {
            invoke(SEND, session, bytes);
            ByteBuf payload = output.getAndSet(null);
            assertNotNull(payload); assertTrue(payload.isDirect()); assertEquals(1, payload.refCnt()); assertEquals(0, payload.readerIndex());
            return payload;
        }
    }
    @Test void nativeFrameAndSegmentByteMatrix() throws Exception {
        int count = 0;
        for (GameVersion version : VERSIONS) for (boolean initialized : new boolean[]{false,true})
                for (CompressionProvider compression : new CompressionProvider[]{CompressionProvider.NONE, CompressionProvider.ZLIB_RAW, CompressionProvider.SNAPPY}) {
            Fixture fixture = new Fixture(version, initialized, compression);
            for (int size : new int[]{0,4096,16895,65536,1048577}) {
                byte[] bytes = data(size), saved = bytes.clone();
                ByteBuf frame = fixture.send(bytes);
                try {
                    byte[] actual = bytes(frame);
                    assertArrayEquals(expected(bytes, version, initialized, compression), actual);
                    assertArrayEquals(saved, bytes);
                    byte[] rebuilt = segments(frame);
                    assertArrayEquals(actual, rebuilt); assertEquals(0, frame.readerIndex());
                    String name = "p"+version.getProtocol()+"-"+initialized+"-c"+(compression==CompressionProvider.NONE?0:compression==CompressionProvider.ZLIB_RAW?1:2)+"-"+size;
                    corpus(name+"-wire", segmentWire(frame));
                    corpus(name+"-frame", actual); corpus(name+"-segments", rebuilt);
                    count++;
                } finally { assertTrue(frame.release()); }
            }
        }
        System.out.println("NETHER_MATRIX cases="+count);
    }
    @Test void queuedCachedPayloadKeepsIndependentCopy() throws Exception {
        Fixture fixture = new Fixture(GameVersion.V1_26_50, true);
        byte[] shared = data(65536), saved = expected(shared, GameVersion.V1_26_50, true);
        ByteBuf frame = fixture.send(shared);
        try {
            Arrays.fill(shared, (byte)0);
            assertArrayEquals(saved, bytes(frame)); assertArrayEquals(saved, segments(frame));
            corpus("delayed-copy", bytes(frame));
        } finally { assertTrue(frame.release()); }
    }
    @Test void segmentLimitsAndFailureRelease() throws Exception {
        ByteBuf tooLarge = Unpooled.buffer(PIECE*256+1).writeZero(PIECE*256+1);
        try {
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> SEGMENT.invoke(null,
                    tooLarge, UnpooledByteBufAllocator.DEFAULT, PIECE, (Consumer<ByteBuffer>) ignored -> fail("over-limit callback")));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
        } finally { assertTrue(tooLarge.release()); }
        ByteBuf source = Unpooled.buffer(16).writeZero(16);
        AtomicReference<ByteBuf> leaf = new AtomicReference<>();
        ByteBufAllocator allocator = mock(ByteBufAllocator.class);
        when(allocator.directBuffer(anyInt(), anyInt())).thenAnswer(call -> {
            ByteBuf value = Unpooled.directBuffer(call.getArgument(0),call.getArgument(1)); leaf.set(value); return value;
        });
        try {
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> SEGMENT.invoke(null,
                    source, allocator, PIECE, (Consumer<ByteBuffer>) ignored -> { throw new IllegalStateException("consumer failure"); }));
            assertInstanceOf(IllegalStateException.class,error.getCause());
            assertEquals(0, leaf.get().refCnt()); // Unpooled leaf has no wrapper recycler.
        } finally { assertTrue(source.release()); }
    }
    @Test void largeFrameDoesNotWaitForSharedDirectMagazine() throws Exception {
        Assumptions.assumeTrue(ByteBufAllocator.DEFAULT instanceof AdaptiveByteBufAllocator);
        Assumptions.assumeTrue(Runtime.getRuntime().maxMemory()>512L*1024*1024);
        Fixture fixture = new Fixture(GameVersion.V1_26_50,true);
        ByteBuf warm = fixture.send(data(65536)); warm.release();
        AdaptiveByteBufAllocator global = (AdaptiveByteBufAllocator)ByteBufAllocator.DEFAULT;
        AdaptiveByteBufAllocator fresh = new AdaptiveByteBufAllocator();
        Field direct = field(AdaptiveByteBufAllocator.class,"direct"); Object saved = direct.get(global), replacement = direct.get(fresh);
        Object group = field(replacement.getClass(),"largeBufferMagazineGroup").get(replacement);
        Object first = ((Object[])field(group.getClass(),"magazines").get(group))[0];
        java.util.concurrent.locks.StampedLock lock = (java.util.concurrent.locks.StampedLock)field(first.getClass(),"allocationLock").get(first);
        MultiThreadIoEventLoopGroup loops = new MultiThreadIoEventLoopGroup(1,NioIoHandler.newFactory()); IoEventLoop io = loops.next();
        AtomicReference<Thread> worker = new AtomicReference<>();
        Future<?> future=null; long stamp=0; boolean completed=false,nativeStack=false;
        try {
            direct.set(global,replacement);
            stamp=lock.writeLock();
            io.submit(() -> { try {
                assertTrue(FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()); assertSame(io,ThreadExecutorMap.currentExecutor());
                ByteBuf small=fixture.send(data(4096)); small.release();
            } catch(Exception error){throw new RuntimeException(error);} }).get(5,TimeUnit.SECONDS);
            System.out.println("NETHER_SMALL_CONTROL completedBeforeUnlock=true");
            future=io.submit(() -> { worker.set(Thread.currentThread()); try {
                ByteBuf frame=fixture.send(data(65536)); try { assertArrayEquals(expected(data(65536),GameVersion.V1_26_50,true),bytes(frame)); }
                finally { frame.release(); }
            } catch(Exception error){throw new RuntimeException(error);} });
            try { future.get(200,TimeUnit.MILLISECONDS); completed=true; }
            catch(TimeoutException expected) {
                StackTraceElement[] stack=worker.get().getStackTrace();
                nativeStack=Arrays.stream(stack).anyMatch(f -> f.getClassName().equals("io.netty.buffer.AdaptivePoolingAllocator$Magazine")&&f.getMethodName().equals("free"));
                System.out.println("NETHER_WAIT_STACK "+Arrays.toString(stack));
            }
        } finally {
            if(stamp!=0) lock.unlockWrite(stamp);
            try { if(future!=null) future.get(5,TimeUnit.SECONDS); }
            finally {
                direct.set(global,saved); loops.shutdownGracefully(0,2,TimeUnit.SECONDS).syncUninterruptibly();
                List<Object> groups=new ArrayList<>(Arrays.asList((Object[])field(replacement.getClass(),"sizeClassedMagazineGroups").get(replacement))); groups.add(group);
                for(Object value:groups) method(value.getClass(),"free").invoke(value);
                assertEquals(0,fresh.usedDirectMemory());
            }
        }
        System.out.println("NETHER_IO_WAIT completedBeforeUnlock="+completed+" nativeRetirementStack="+nativeStack);
        if(!completed) assertTrue(nativeStack,"exact allocator waiter required");
        assertTrue(completed,"large NetherNet staging must finish while unrelated shared-direct magazine is held");
    }
    public static void benchmark(int size,int iterations) throws Exception {
        Fixture fixture=new Fixture(GameVersion.V1_26_50,true);byte[] input=data(size);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        Consumer<ByteBuffer> consume=buffer->{assertTrue(buffer.isDirect());assertTrue(buffer.remaining()<=10000);};
        for(int i=0;i<64;i++){ByteBuf frame=fixture.send(input);try{invoke(SEGMENT,null,frame,ByteBufAllocator.DEFAULT,PIECE,consume);}finally{frame.release();}}
        long id=Thread.currentThread().getId(), allocated=bean.getThreadAllocatedBytes(id),begin=System.nanoTime();
        long[] times=new long[iterations];
        for(int i=0;i<iterations;i++){long start=System.nanoTime();ByteBuf frame=fixture.send(input);try{invoke(SEGMENT,null,frame,ByteBufAllocator.DEFAULT,PIECE,consume);}finally{frame.release();}times[i]=System.nanoTime()-start;}
        allocated=bean.getThreadAllocatedBytes(id)-allocated;long elapsed=System.nanoTime()-begin;Arrays.sort(times);
        System.out.println("NETHER_BENCH size="+size+" count="+iterations+" heapBytesPerFrame="+allocated/(double)iterations
                +" avgMs="+elapsed/(double)iterations/1e6+" p95Ms="+times[(int)Math.ceil(iterations*.95)-1]/1e6
                +" p99Ms="+times[(int)Math.ceil(iterations*.99)-1]/1e6+" maxMs="+times[iterations-1]/1e6);
    }
    private static byte[] segments(ByteBuf frame) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();ByteBuf duplicate=frame.retainedDuplicate();
        int total=(frame.readableBytes()+PIECE-1)/PIECE;int[] next={0};
        try { int count=(int)invoke(SEGMENT,null,duplicate,ByteBufAllocator.DEFAULT,PIECE,(Consumer<ByteBuffer>)buffer->{
            assertTrue(buffer.isDirect());assertTrue(buffer.remaining()<=10000);assertEquals((total-next[0]++-1)&255,buffer.get()&255);byte[] part=new byte[buffer.remaining()];buffer.get(part);output.writeBytes(part);
        }); assertEquals((frame.readableBytes()+PIECE-1)/PIECE,count); }
        finally{duplicate.release();}
        return output.toByteArray();
    }
    private static byte[] segmentWire(ByteBuf frame) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteBuf duplicate = frame.retainedDuplicate();
        try { invoke(SEGMENT, null, duplicate, ByteBufAllocator.DEFAULT, PIECE, (Consumer<ByteBuffer>) buffer -> {
            byte[] wire = new byte[buffer.remaining()]; buffer.get(wire);
            output.write(wire.length >>> 24); output.write(wire.length >>> 16);
            output.write(wire.length >>> 8); output.write(wire.length); output.writeBytes(wire);
        }); } finally { duplicate.release(); }
        return output.toByteArray();
    }
    private static byte[] expected(byte[] input,GameVersion version,boolean initialized){
        return expected(input,version,initialized,CompressionProvider.NONE);
    }
    private static byte[] expected(byte[] input,GameVersion version,boolean initialized,CompressionProvider compression){
        boolean prefix=initialized&&version.getProtocol()>=ProtocolInfo.v1_20_60;
        byte[] result=new byte[input.length+(prefix?1:0)];if(prefix)result[0]=compression.getPrefix();System.arraycopy(input,0,result,prefix?1:0,input.length);return result;
    }
    private static byte[] bytes(ByteBuf buffer){byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),bytes);return bytes;}
    private static byte[] data(int size){byte[] bytes=new byte[size];for(int i=0;i<size;i++)bytes[i]=(byte)(i*31+7);return bytes;}
    private static void corpus(String name,byte[] bytes)throws Exception{
        String expected=System.getProperty("p08.expectedCorpus");if(expected!=null)assertArrayEquals(Files.readAllBytes(Path.of(expected,name+".bin")),bytes);
        String output=System.getProperty("p08.corpus");if(output!=null){Files.createDirectories(Path.of(output));Files.write(Path.of(output,name+".bin"),bytes);}
    }
    private static Object invoke(Method method,Object target,Object...args)throws Exception{return method.invoke(target,args);}
    private static Method method(Class<?> type,String name,Class<?>...args){
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Method method=current.getDeclaredMethod(name,args);method.setAccessible(true);return method;}catch(NoSuchMethodException ignored){}
        throw new IllegalStateException(name);
    }
    private static Field field(Class<?> type,String name)throws Exception{
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Field field=current.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
}
