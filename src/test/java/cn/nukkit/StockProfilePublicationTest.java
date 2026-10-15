package cn.nukkit;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.stream.NBTOutputStream;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.DefaultPlayerDataSerializer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(20)
class StockProfilePublicationTest {
    @TempDir Path directory;
    PlayerQueuedProfileSaveTest.Fixture fixture;
    DefaultPlayerDataSerializer serializer;
    Path target;
    @BeforeEach void setup() throws Exception {
        fixture=new PlayerQueuedProfileSaveTest.Fixture();
        Files.createDirectories(directory.resolve("players"));
        target=directory.resolve("players/player.dat");
        serializer=new DefaultPlayerDataSerializer(directory.toString());
        fixture.inject("playerDataSerializer",serializer);
        when(fixture.server.getDataPath()).thenReturn(directory.resolve("different-server-directory").toString());
        when(fixture.server.getLanguage()).thenReturn(new cn.nukkit.lang.BaseLang("eng"));
        try(var output=serializer.write("player",null)){NBTIO.writeGZIPCompressed(new CompoundTag().putString("marker","old"),output);}
    }
    CompoundTag read() throws IOException {try(var input=Files.newInputStream(target)){return NBTIO.readCompressed(input);}}
    void assertOldAndNoStaging() throws IOException {
        assertEquals("old",read().getString("marker"));
        try(var files=Files.list(target.getParent())){assertEquals(List.of(target),files.sorted().toList());}
    }
    @Test void successfulNativeSavePublishesOneCompleteGzipInTheSerializerOwnedDirectory() throws Exception {
        AtomicInteger encoded=new AtomicInteger();
        CompoundTag profile=new CountedProfile(encoded,null).putString("marker","new");
        fixture.server.saveOfflinePlayerData("player",profile,false);
        assertEquals(1,encoded.get());assertEquals("new",read().getString("marker"));
        assertFalse(Files.exists(directory.resolve("different-server-directory/players/player.dat")));
        try(var files=Files.list(target.getParent())){assertEquals(List.of(target),files.sorted().toList());}
    }
    @Test void nativeEncodeErrorKeepsThePreviousGzipAndThePrimaryErrorIdentity() throws Exception {
        Error failure=new AssertionError("native NBT extension");
        assertSame(failure,assertThrows(Error.class,()->fixture.server.saveOfflinePlayerData("player",new CountedProfile(new AtomicInteger(),failure),false)));
        assertOldAndNoStaging();
    }
    @Test void nativeCloseFailureKeepsThePreviousGzipAndTheVoidFailureContract() throws Exception {
        IOException failure=new IOException("native stream close failure");AtomicInteger closed=new AtomicInteger();
        try(var files=mockStatic(Files.class,CALLS_REAL_METHODS)) {
            files.when(()->Files.newOutputStream(any(Path.class),any(OpenOption[].class))).thenAnswer(call->{
                OutputStream actual=(OutputStream)call.callRealMethod();
                return new FilterOutputStream(actual) {
                    @Override public void close() throws IOException {
                        super.close();if(closed.getAndIncrement()==0)throw failure;
                    }
                };
            });
            assertDoesNotThrow(()->fixture.server.saveOfflinePlayerData("player",new CompoundTag().putString("marker","new"),false));
        }
        assertTrue(closed.get()>0);assertOldAndNoStaging();
    }
    @Test void unavailableAtomicReplacementKeepsThePreviousGzipWithoutAnOverwriteFallback() throws Exception {
        AtomicInteger attempted=new AtomicInteger();
        try(var files=mockStatic(Files.class,CALLS_REAL_METHODS)) {
            files.when(()->Files.move(any(Path.class),eq(target),any(CopyOption[].class))).thenAnswer(call->{
                attempted.incrementAndGet();throw new AtomicMoveNotSupportedException("stage",target.toString(),"native provider refusal");
            });
            assertDoesNotThrow(()->fixture.server.saveOfflinePlayerData("player",new CompoundTag().putString("marker","new"),false));
        }
        assertEquals(1,attempted.get());assertOldAndNoStaging();
    }
    @Test void aDefaultSerializerSubclassStillOwnsItsOriginalVirtualWriteHook() throws Exception {
        AtomicInteger calls=new AtomicInteger();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        fixture.inject("playerDataSerializer",new DefaultPlayerDataSerializer(directory.resolve("custom-owned-path").toString()) {
            @Override public OutputStream write(String name,UUID uuid){calls.incrementAndGet();return bytes;}
        });
        fixture.server.saveOfflinePlayerData("player",new CompoundTag().putString("marker","custom"),false);
        assertEquals(1,calls.get());assertEquals("custom",NBTIO.readCompressed(new ByteArrayInputStream(bytes.toByteArray())).getString("marker"));
        assertOldAndNoStaging();
    }
    static final class CountedProfile extends CompoundTag {
        final AtomicInteger encoded;final Error failure;
        CountedProfile(AtomicInteger encoded,Error failure){this.encoded=encoded;this.failure=failure;}
        @Override public CompoundTag copy(){var copy=new CountedProfile(encoded,failure);for(var tag:getAllTags())copy.put(tag.getName(),tag.copy());return copy;}
        @Override public void write(NBTOutputStream output)throws IOException{encoded.incrementAndGet();super.write(output);if(failure!=null)throw failure;}
    }
}
