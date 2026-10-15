package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.jetbrains.annotations.ApiStatus;
import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.nio.ByteOrder;
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
        File file = new File(getPlayersDirectory(), name + ".dat");
        if (!file.exists()) {
            return Optional.empty();
        }
        return Optional.of(new FileInputStream(file));

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
        Preconditions.checkNotNull(name, "name");
        Path target = new File(getPlayersDirectory(), name + ".dat").toPath();
        Path staged = Files.createTempFile(target.getParent(), name + ".", ".tmp");
        Throwable failure = null;
        try {
            try (OutputStream output = Files.newOutputStream(staged)) {
                NBTIO.writeGZIPCompressed(tag, output, ByteOrder.BIG_ENDIAN);
            }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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

    @Override
    public boolean delete(String name, UUID uuid) throws IOException {
        File file = new File(getPlayersDirectory(), name + ".dat");
        return file.delete();
    }
}
