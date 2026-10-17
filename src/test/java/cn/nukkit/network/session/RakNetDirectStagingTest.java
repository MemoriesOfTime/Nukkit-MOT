package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.ProtocolInfo;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ThreadExecutorMap;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.channel.raknet.RakSlidingWindow;
import org.cloudburstmc.netty.channel.raknet.packet.*;
import org.cloudburstmc.netty.handler.codec.raknet.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Isolated("Temporarily substitutes Server.instance and the default adaptive direct pool")
@Timeout(value=20,unit=TimeUnit.SECONDS,threadMode=Timeout.ThreadMode.SAME_THREAD)
public class RakNetDirectStagingTest {
    private static final Method SEND=method(RakNetPlayerSession.class,"sendPacket",byte[].class);
    private static final Method SPLIT=method(RakSessionCodec.class,"createEncapsulated",RakMessage.class);
    private static final Method SEND_DG=method(RakSessionCodec.class,"sendDatagram",ChannelHandlerContext.class,RakDatagramPacket.class,long.class,IntObjectMap.class);
    private static final Method ACK=method(RakSessionCodec.class,"onIncomingAck",RakDatagramPacket.class,long.class);
    private static final Method NACK=method(RakSessionCodec.class,"onIncomingNack",ChannelHandlerContext.class,RakDatagramPacket.class,long.class);
    private static final GameVersion[] VERSIONS={GameVersion.V1_20_0,GameVersion.V1_20_60,GameVersion.V1_26_20_26,GameVersion.V1_26_40,GameVersion.V1_26_50};
    private Object savedServer;
    @BeforeEach void stubServer()throws Exception{Field instance=field(Server.class,"instance");savedServer=instance.get(null);instance.set(null,mock(Server.class));}
    @AfterEach void restoreServer()throws Exception{field(Server.class,"instance").set(null,savedServer);}

    private static final class Fixture {
        final RakNetPlayerSession session;final AtomicReference<ByteBuf> output=new AtomicReference<>();
        final GameVersion version;final boolean initialized,encrypted;final CompressionProvider compression;
        final SecretKey key=new SecretKeySpec(data(32),"AES");final Cipher receiver;long counter;
        Fixture(GameVersion version,boolean initialized,CompressionProvider compression,boolean encrypted)throws Exception{
            this.version=version;this.initialized=initialized;this.compression=compression;this.encrypted=encrypted;
            EventLoop loop=mock(EventLoop.class);io.netty.util.concurrent.ScheduledFuture<?> timer=mock(io.netty.util.concurrent.ScheduledFuture.class);
            doReturn(timer).when(loop).scheduleAtFixedRate(any(Runnable.class),anyLong(),anyLong(),any(TimeUnit.class));
            ChannelFuture future=mock(ChannelFuture.class);RakChildChannel channel=mock(RakChildChannel.class,RETURNS_DEEP_STUBS);
            when(channel.eventLoop()).thenReturn(loop);when(channel.config().getProtocolVersion()).thenReturn(11);
            when(channel.remoteAddress()).thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(),0));
            doAnswer(call->{assertTrue(output.compareAndSet(null,call.getArgument(0)));return future;}).when(channel).writeAndFlush(any(ByteBuf.class));
            session=new RakNetPlayerSession(null,channel);session.getState().getProtocol().setGameVersion(version);
            if(initialized)session.setCompression(compression);
            receiver=encrypted?EncryptionUtils.createCipher(true,false,key):null;
            if(encrypted)session.setEncryption(key,EncryptionUtils.createCipher(true,true,key),EncryptionUtils.createCipher(true,false,key));
        }
        ByteBuf send(byte[] bytes)throws Exception{
            SEND.invoke(session,(Object)bytes);ByteBuf frame=output.getAndSet(null);assertNotNull(frame);assertTrue(frame.isDirect());assertEquals(1,frame.refCnt());assertEquals(0,frame.readerIndex());
            boolean prefix=initialized&&version.getProtocol()>=ProtocolInfo.v1_20_60;
            assertEquals(bytes.length+(prefix?10:9),frame.capacity());assertEquals(bytes.length+1+(prefix?1:0)+(encrypted?8:0),frame.readableBytes());return frame;
        }
        void verify(byte[] input,ByteBuf frame)throws Exception{
            byte[] wire=bytes(frame);assertEquals(0xfe,wire[0]&255);boolean prefix=initialized&&version.getProtocol()>=ProtocolInfo.v1_20_60;
            byte[] body=new byte[input.length+(prefix?1:0)];if(prefix)body[0]=compression.getPrefix();System.arraycopy(input,0,body,prefix?1:0,input.length);
            if(!encrypted){assertArrayEquals(body,Arrays.copyOfRange(wire,1,wire.length));return;}
            byte[] plaintext=receiver.update(Arrays.copyOfRange(wire,1,wire.length));assertNotNull(plaintext);
            assertArrayEquals(body,Arrays.copyOf(plaintext,body.length));MessageDigest digest=MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter++).array());digest.update(body);digest.update(key.getEncoded());
            assertArrayEquals(Arrays.copyOf(digest.digest(),8),Arrays.copyOfRange(plaintext,body.length,plaintext.length));
        }
    }
    /** Native fragments/datagrams/ACK; embedded encoder replaces physical UDP write only. */
    private static final class Transit implements AutoCloseable {
        final RakSessionCodec codec;final IntObjectMap<RakDatagramPacket> sent=new IntObjectHashMap<>();
        final ChannelHandlerContext ctx=mock(ChannelHandlerContext.class);final EmbeddedChannel encoder=new EmbeddedChannel(new RakDatagramCodec());
        final List<ByteBuf> carriers=new ArrayList<>();final ByteBuf backing;final byte[] rebuilt;
        Transit(ByteBuf frame)throws Exception{
            backing=frame;RakChannel channel=mock(RakChannel.class,RETURNS_DEEP_STUBS);
            when(channel.config().getMtu()).thenReturn(1492);when(channel.remoteAddress()).thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(),0));
            codec=new RakSessionCodec(channel);field(RakSessionCodec.class,"orderWriteIndex").set(codec,new int[16]);
            field(RakSessionCodec.class,"slidingWindow").set(codec,new RakSlidingWindow(codec.getMtu()));field(RakSessionCodec.class,"sentDatagrams").set(codec,sent);
            ChannelFuture future=mock(ChannelFuture.class);
            doAnswer(call->{assertTrue(encoder.writeOutbound((Object)call.getArgument(0)));ByteBuf carrier=encoder.readOutbound();assertNotNull(carrier);carriers.add(carrier);return future;}).when(ctx).write(any());
            RakMessage message=new RakMessage(frame);EncapsulatedPacket[] fragments;
            try{fragments=(EncapsulatedPacket[])SPLIT.invoke(codec,message);}finally{message.release();}
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            for(EncapsulatedPacket fragment:fragments){output.writeBytes(bytes(fragment.getBuffer()));RakDatagramPacket datagram=RakDatagramPacket.newInstance();assertTrue(datagram.tryAddPacket(fragment,codec.getMtu()));datagram.setSendTime(System.currentTimeMillis());SEND_DG.invoke(codec,ctx,datagram,System.currentTimeMillis(),sent);}
            rebuilt=output.toByteArray();assertEquals(fragments.length,sent.size());assertTrue(backing.refCnt()>0);
        }
        void nackFirst()throws Exception{
            int sequence=sent.keySet().stream().mapToInt(Integer::intValue).min().orElseThrow();RakDatagramPacket datagram=sent.remove(sequence);
            NACK.invoke(codec,ctx,datagram,System.currentTimeMillis());assertFalse(sent.containsKey(sequence));assertEquals(carriers.size()-1,sent.size());
        }
        void ackAll()throws Exception{
            for(int sequence:new ArrayList<>(sent.keySet())){RakDatagramPacket datagram=sent.remove(sequence);ACK.invoke(codec,datagram,System.currentTimeMillis());}
            assertEquals(0,sent.size());assertTrue(backing.refCnt()>0,"physical carriers still own payload after ACK");
        }
        byte[] wire(){ByteArrayOutputStream out=new ByteArrayOutputStream();for(ByteBuf carrier:carriers){byte[] value=bytes(carrier);out.write(value.length>>>24);out.write(value.length>>>16);out.write(value.length>>>8);out.write(value.length);out.writeBytes(value);}return out.toByteArray();}
        void releaseCarriers(){for(ByteBuf carrier:carriers)assertTrue(carrier.release());carriers.clear();
            // Isolated fixture: no subsequent allocation can reclaim this backing wrapper before this check.
            assertEquals(0,backing.refCnt(),"final physical release frees every backing reference");
            assertFalse(encoder.finishAndReleaseAll());}
        public void close()throws Exception{if(!sent.isEmpty())ackAll();releaseCarriers();}
    }
    @Test void nativeEncryptedFrameSplitAckByteMatrix()throws Exception{
        int cases=0;
        for(GameVersion version:VERSIONS)for(boolean initialized:new boolean[]{false,true})
            for(CompressionProvider compression:new CompressionProvider[]{CompressionProvider.NONE,CompressionProvider.ZLIB_RAW,CompressionProvider.SNAPPY})for(boolean encrypted:new boolean[]{false,true}){
                Fixture fixture=new Fixture(version,initialized,compression,encrypted);
                for(int size:new int[]{0,4096,65536}){byte[] input=data(size),saved=input.clone();ByteBuf frame=fixture.send(input);fixture.verify(input,frame);byte[] original=bytes(frame);
                    try(Transit transit=new Transit(frame)){assertArrayEquals(saved,input);assertArrayEquals(original,transit.rebuilt);
                        String name="p"+version.getProtocol()+"-"+initialized+"-c"+(compression==CompressionProvider.NONE?0:compression==CompressionProvider.ZLIB_RAW?1:2)+"-e"+encrypted+"-"+size;
                        corpus(name+"-frame",original);corpus(name+"-rebuilt",transit.rebuilt);corpus(name+"-datagrams",transit.wire());transit.ackAll();assertArrayEquals(original,allFrameBytes(transit.backing));}
                    cases++;
                }
            }
        System.out.println("RAK_MATRIX cases="+cases);
    }
    @Test void queuedCachedInputRemainsIndependent()throws Exception{
        Fixture fixture=new Fixture(GameVersion.V1_26_50,true,CompressionProvider.ZLIB_RAW,true);byte[] shared=data(65536);ByteBuf frame=fixture.send(shared);fixture.verify(shared,frame);byte[] saved=bytes(frame);Arrays.fill(shared,(byte)0);
        try(Transit transit=new Transit(frame)){assertArrayEquals(saved,transit.rebuilt);transit.ackAll();assertArrayEquals(saved,allFrameBytes(transit.backing));corpus("delayed-copy",saved);}
    }
    @Test void nativeNackRetainsPayloadUntilFinalAckAndCarrierRelease()throws Exception{
        Fixture fixture=new Fixture(GameVersion.V1_26_50,true,CompressionProvider.NONE,false);ByteBuf frame=fixture.send(data(4096));byte[] saved=bytes(frame);
        try(Transit transit=new Transit(frame)){int previous=transit.carriers.size();transit.nackFirst();assertEquals(previous+1,transit.carriers.size());transit.ackAll();assertArrayEquals(saved,allFrameBytes(transit.backing));corpus("nack-wire",transit.wire());}
    }
    @Test void ackBeforeCrossOwnerCarrierReleaseKeepsPayloadAlive()throws Exception{
        MultiThreadIoEventLoopGroup loops=new MultiThreadIoEventLoopGroup(2,NioIoHandler.newFactory());IoEventLoop child=loops.next(),parent=loops.next();
        try{Transit transit=child.submit(()->{Fixture fixture=new Fixture(GameVersion.V1_26_50,true,CompressionProvider.NONE,false);ByteBuf frame=fixture.send(data(65536));fixture.verify(data(65536),frame);Transit result=new Transit(frame);result.ackAll();return result;}).get(5,TimeUnit.SECONDS);
            parent.submit(()->{assertArrayEquals(transit.rebuilt,allFrameBytes(transit.backing));transit.releaseCarriers();}).get(5,TimeUnit.SECONDS);
            System.out.println("RAK_CROSS_OWNER childAckBeforeParentRelease=PASS");
        }finally{loops.shutdownGracefully(0,2,TimeUnit.SECONDS).syncUninterruptibly();}
    }
    @Test void largeFrameDoesNotWaitForSharedDirectMagazine()throws Exception{
        Assumptions.assumeTrue(ByteBufAllocator.DEFAULT instanceof AdaptiveByteBufAllocator);Assumptions.assumeTrue(Runtime.getRuntime().maxMemory()>512L*1024*1024);
        Fixture fixture=new Fixture(GameVersion.V1_26_50,true,CompressionProvider.NONE,false);fixture.send(data(65536)).release();
        AdaptiveByteBufAllocator global=(AdaptiveByteBufAllocator)ByteBufAllocator.DEFAULT,fresh=new AdaptiveByteBufAllocator();Field direct=field(AdaptiveByteBufAllocator.class,"direct");Object saved=direct.get(global),replacement=direct.get(fresh);
        Object group=field(replacement.getClass(),"largeBufferMagazineGroup").get(replacement);Object first=((Object[])field(group.getClass(),"magazines").get(group))[0];
        java.util.concurrent.locks.StampedLock lock=(java.util.concurrent.locks.StampedLock)field(first.getClass(),"allocationLock").get(first);
        MultiThreadIoEventLoopGroup loops=new MultiThreadIoEventLoopGroup(1,NioIoHandler.newFactory());IoEventLoop io=loops.next();AtomicReference<Thread> worker=new AtomicReference<>();Future<?> future=null;long stamp=0;boolean completed=false,nativeStack=false;
        try{direct.set(global,replacement);stamp=lock.writeLock();
            io.submit(()->{try{assertTrue(FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals());assertSame(io,ThreadExecutorMap.currentExecutor());fixture.send(data(4096)).release();}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
            System.out.println("RAK_SMALL_CONTROL completedBeforeUnlock=true");
            CountDownLatch started=new CountDownLatch(1);future=io.submit(()->{worker.set(Thread.currentThread());started.countDown();try{ByteBuf frame=fixture.send(data(65536));try{fixture.verify(data(65536),frame);}finally{frame.release();}}catch(Exception e){throw new RuntimeException(e);}});
            assertTrue(started.await(5,TimeUnit.SECONDS));try{future.get(200,TimeUnit.MILLISECONDS);completed=true;}catch(TimeoutException expected){StackTraceElement[] stack=worker.get().getStackTrace();nativeStack=Arrays.stream(stack).anyMatch(f->f.getClassName().equals("io.netty.buffer.AdaptivePoolingAllocator$Magazine")&&f.getMethodName().equals("free"));System.out.println("RAK_WAIT_STACK "+Arrays.toString(stack));}
        }finally{if(stamp!=0)lock.unlockWrite(stamp);try{if(future!=null)future.get(5,TimeUnit.SECONDS);}finally{direct.set(global,saved);loops.shutdownGracefully(0,2,TimeUnit.SECONDS).syncUninterruptibly();List<Object> groups=new ArrayList<>(Arrays.asList((Object[])field(replacement.getClass(),"sizeClassedMagazineGroups").get(replacement)));groups.add(group);for(Object value:groups)method(value.getClass(),"free").invoke(value);assertEquals(0,fresh.usedDirectMemory());}}
        System.out.println("RAK_IO_WAIT completedBeforeUnlock="+completed+" nativeRetirementStack="+nativeStack);if(!completed)assertTrue(nativeStack);assertTrue(completed,"RakNet final copy must not wait for unrelated shared-direct magazine");
    }
    public static void benchmark(int size,int iterations,boolean encrypted)throws Exception{
        Fixture fixture=new Fixture(GameVersion.V1_26_50,true,CompressionProvider.NONE,encrypted);byte[] input=data(size);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
        for(int i=0;i<64;i++)fixture.send(input).release();long id=Thread.currentThread().getId(),allocated=bean.getThreadAllocatedBytes(id),begin=System.nanoTime();long[] times=new long[iterations];
        for(int i=0;i<iterations;i++){long start=System.nanoTime();fixture.send(input).release();times[i]=System.nanoTime()-start;}allocated=bean.getThreadAllocatedBytes(id)-allocated;long elapsed=System.nanoTime()-begin;Arrays.sort(times);
        System.out.println("RAK_BENCH size="+size+" encrypted="+encrypted+" count="+iterations+" heapBytesPerFrame="+allocated/(double)iterations+" avgMs="+elapsed/(double)iterations/1e6+" p95Ms="+times[(int)Math.ceil(iterations*.95)-1]/1e6+" p99Ms="+times[(int)Math.ceil(iterations*.99)-1]/1e6+" maxMs="+times[iterations-1]/1e6);
    }
    public static AutoCloseable benchmarkServer()throws Exception{Field instance=field(Server.class,"instance");Object previous=instance.get(null);instance.set(null,mock(Server.class));return ()->instance.set(null,previous);}
    private static byte[] allFrameBytes(ByteBuf buffer){byte[] value=new byte[buffer.writerIndex()];buffer.getBytes(0,value);return value;}
    private static byte[] bytes(ByteBuf buffer){byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),bytes);return bytes;}
    private static byte[] data(int size){byte[] bytes=new byte[size];for(int i=0;i<size;i++)bytes[i]=(byte)(i*31+7);return bytes;}
    private static void corpus(String name,byte[] bytes)throws Exception{String expected=System.getProperty("p08.expectedCorpus");if(expected!=null)assertArrayEquals(Files.readAllBytes(Path.of(expected,name+".bin")),bytes);String output=System.getProperty("p08.corpus");if(output!=null){Files.createDirectories(Path.of(output));Files.write(Path.of(output,name+".bin"),bytes);}}
    private static Method method(Class<?> type,String name,Class<?>...args){for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Method result=current.getDeclaredMethod(name,args);result.setAccessible(true);return result;}catch(NoSuchMethodException ignored){}throw new IllegalStateException(name);}
    private static Field field(Class<?> type,String name)throws Exception{for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Field result=current.getDeclaredField(name);result.setAccessible(true);return result;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
}
