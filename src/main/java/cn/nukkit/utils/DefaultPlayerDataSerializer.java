package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.jetbrains.annotations.ApiStatus;
import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * Default player data serializer that saves the player data as .dat files into the 'players' folder.
 */
@RequiredArgsConstructor
public class DefaultPlayerDataSerializer implements PlayerDataSerializer {

    private String dataPath;

    public DefaultPlayerDataSerializer(Server server) {
        this(server.getDataPath());
    }

    public DefaultPlayerDataSerializer(String dataPath) {
        this.dataPath = dataPath;
    }

    File getPlayersDirectory() {
        return new File(dataPath, "players");
    }

    @Override
    public Optional<InputStream> read(String name, UUID uuid) throws IOException {
        return readStrict(name, uuid);
    }

    /** Missing and unreadable profiles must remain distinguishable during login. */
    @ApiStatus.Internal
    public final Optional<InputStream> readStrict(String name, UUID uuid) throws IOException {
        Preconditions.checkNotNull(name, "name");
        try { return Optional.of(Files.newInputStream(new File(getPlayersDirectory(), name + ".dat").toPath())); }
        catch (NoSuchFileException missing) { return Optional.empty(); }
    }

    @Override
    public boolean supportsConcurrentReads() {
        // Subclasses keep their virtual legacy reader unless they explicitly opt in.
        return getClass() == DefaultPlayerDataSerializer.class;
    }

    @Override
    public OutputStream write(String name, UUID uuid) throws IOException {
        Preconditions.checkNotNull(name, "name");
        File file = new File(getPlayersDirectory(), name + ".dat");
        return new FileOutputStream(file);
    }

    /** Native complete-profile publication; custom serializers keep their existing write stream contract. */
    @ApiStatus.Internal
    public final void writeAtomically(String name, UUID uuid, CompoundTag tag) throws IOException {
        writeAtomicallyAcknowledged(name, uuid, tag);
    }

    /** Returns the actual successful native publication contract; post-rename failure throws. */
    @ApiStatus.Internal
    public final WriteAcknowledgement writeAtomicallyAcknowledged(String name, UUID uuid, CompoundTag tag) throws IOException {
        Preconditions.checkNotNull(name, "name");
        Path target = new File(getPlayersDirectory(), name + ".dat").toPath();
        Path staged = Files.createTempFile(target.getParent(), name + ".", ".tmp");
        Throwable failure = null;
        try {
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                // NBTIO closes its gzip stream to finish the trailer. Keep its actual writing channel open for force.
                OutputStream output = new FilterOutputStream(Channels.newOutputStream(channel)) {
                    @Override public void close() throws IOException { flush(); }
                };
                NBTIO.writeGZIPCompressed(tag, output, ByteOrder.BIG_ENDIAN);
                channel.force(true);
            }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // Failure here is an unknown durability ACK, even though replacement may be visible.
            return syncDirectory() ? WriteAcknowledgement.ATOMIC_FILE_AND_DIRECTORY_SYNC : WriteAcknowledgement.ATOMIC_FILE_SYNC;
        } catch (IOException | RuntimeException | Error thrown) {
            failure = thrown;
            throw thrown;
        } finally {
            try { Files.deleteIfExists(staged); }
            catch (IOException | RuntimeException | Error cleanup) {
                if (failure == null) throw cleanup;
                if (failure != cleanup) failure.addSuppressed(cleanup);
            }
        }
    }

    /** The OpenJDK Windows file provider cannot open directory channels. Qualify before publishing. */
    static boolean supportsDirectorySync(Path directory) {
        return !directory.getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider");
    }

    /** Also used after the existing native migration mover publishes a renamed profile. */
    @ApiStatus.Internal
    public final boolean syncDirectory() throws IOException {
        Path directory = getPlayersDirectory().toPath();
        if (!supportsDirectorySync(directory)) return false;
        forceDirectory(directory);
        return true;
    }

    void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }

    @Override
    public boolean delete(String name, UUID uuid) throws IOException {
        File file = new File(getPlayersDirectory(), name + ".dat");
        return file.delete();
    }
}
