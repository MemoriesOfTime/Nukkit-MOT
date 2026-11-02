package cn.nukkit;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.network.encryption.EncryptionUtils;
import lombok.extern.log4j.Log4j2;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.WriteBatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Folds player data saved under a name padded with whitespace back onto the trimmed name.
 * <p>
 * Before login names were trimmed, a client reporting {@code "Name "} derived the offline identity
 * of the padded string and got a data file of its own next to the one of {@code "Name"}. The
 * trimmed login now always opens {@code "Name"}'s file, so without this step a player would come
 * back to whichever of the two files is older, and the other file would stay behind with its own
 * copy of the inventory.
 * <p>
 * A name is folded only in the clean case, checked before anything is touched:
 * <ul>
 *     <li>every padded key and the trimmed key, if present, are offline entries, each padded key
 *     points at the identity its own spelling derives, and the trimmed key points at the trimmed
 *     identity {@code T};</li>
 *     <li>every key has its data file, and every data file involved (those of the padded keys and
 *     {@code T}'s file if it exists) reads, carries a {@code NameTag} that trims to the name and
 *     no XUID.</li>
 * </ul>
 * The only files allowed to be missing are the ones an interrupted run of this step moved itself:
 * a file sitting in quarantine with a {@code NameTag} of this name, or a padded file already renamed
 * to {@code T}, recognisable by {@code T}'s {@code NameTag} still spelling that padded key.
 * <p>
 * Anything else, including an unreadable file, is left exactly as it is, files and keys, and the
 * name is reported as blocked with the reason: offline logins with it are refused until the files
 * are checked by hand, because a login would otherwise open a profile that may not be the player's
 * latest.
 * <p>
 * In the clean case one profile is renamed to {@code T}; of several, the one played most recently
 * takes {@code T} (a profile never played, {@code lastPlayed - firstPlayed <= 1}, ranks below any
 * played one; ties go by {@code lastPlayed}, then by file time) and every other one is moved to a
 * quarantine directory with a warning. Contents are never merged: the same items can sit in both
 * files, and merging would hand out the duplicate this step exists to remove. Then the trimmed key
 * and the removal of the padded keys are written in one atomic batch.
 * <p>
 * A failed move undoes the moves already made in reverse order; a failed key batch undoes nothing,
 * since it may have been applied. In both cases the name is blocked for this run, and a later start
 * recognises the resulting state as one of its own. Every move is an atomic rename that refuses to
 * overwrite, and a start without padded keys changes nothing.
 */
@Log4j2
final class PlayerNameEdgeWhitespaceMigration {

    static final String QUARANTINE_DIRECTORY = "players-quarantine";

    /** Values this large are milliseconds, written by the PocketMine era of the network. */
    private static final long MILLISECOND_TIMESTAMPS = 100_000_000_000L;

    @FunctionalInterface
    interface FileMover {
        void move(Path source, Path destination) throws IOException;
    }

    record Move(UUID from, UUID to, Path destination) {
    }

    /**
     * @param blocked trimmed lower-case names left unfolded, with the reason; offline logins with
     *                these names must be refused
     */
    record Report(List<Move> renamed, List<Move> quarantined, int namesFixed, Map<String, String> blocked) {
        Set<String> blockedNames() {
            return blocked.keySet();
        }

        boolean changed() {
            return !renamed.isEmpty() || !quarantined.isEmpty() || namesFixed > 0;
        }
    }

    private record Profile(UUID uuid, Path file, CompoundTag tag, boolean played, long lastPlayed, long modified,
                           boolean trimmed) {
    }

    private record Step(Path from, Path to) {
    }

    /** A folding decision for one name, reached without touching anything. */
    private record Plan(UUID target, List<Profile> profiles, Server.NameEntry trimmedEntry, Set<String> paddedKeys) {
    }

    /** The name is not in the clean case; nothing may be touched. */
    private static final class Unclean extends Exception {
        private Unclean(String reason) {
            super(reason, null, false, false);
        }
    }

    private PlayerNameEdgeWhitespaceMigration() {
    }

    static Report run(DB nameLookup, File playersDirectory, File quarantineDirectory) {
        return run(nameLookup, playersDirectory, quarantineDirectory, PlayerNameEdgeWhitespaceMigration::atomicMove);
    }

    static Report run(DB nameLookup, File playersDirectory, File quarantineDirectory, FileMover mover) {
        List<Move> renamed = new ArrayList<>();
        List<Move> quarantined = new ArrayList<>();
        Map<String, String> blocked = new TreeMap<>();
        int namesFixed = 0;
        Optional<Map<String, Map<String, byte[]>>> groups = paddedNames(nameLookup);
        if (groups.isEmpty()) {
            return new Report(List.of(), List.of(), 0, Map.of());
        }
        Path players = playersDirectory.toPath();
        Path quarantine = quarantineDirectory.toPath();
        for (Map.Entry<String, Map<String, byte[]>> group : groups.get().entrySet()) {
            String trimmed = group.getKey();
            Plan plan;
            try {
                plan = plan(nameLookup, players, quarantine, trimmed, group.getValue());
            } catch (Unclean unclean) {
                blocked.put(trimmed, unclean.getMessage());
                log.warn("Not folding the player data of \"{}\": {}. Nothing was changed; offline logins with this "
                        + "name are refused until its files are checked by hand", trimmed, unclean.getMessage());
                continue;
            } catch (IOException | RuntimeException failure) {
                blocked.put(trimmed, "inspection failed: " + failure);
                log.error("Could not inspect the player data of \"{}\". Nothing was changed; offline logins with this "
                        + "name are refused for this run", trimmed, failure);
                continue;
            }
            try {
                apply(nameLookup, players, quarantine, mover, trimmed, plan, renamed, quarantined);
                namesFixed++;
            } catch (IOException | RuntimeException failure) {
                blocked.put(trimmed, "fold failed: " + failure);
                log.error("Could not fold the player data of \"{}\". Offline logins with this name are refused for this "
                        + "run; the next start looks at the files again", trimmed, failure);
            }
        }
        Report report = new Report(List.copyOf(renamed), List.copyOf(quarantined), namesFixed, Map.copyOf(blocked));
        if (report.changed() || !blocked.isEmpty()) {
            log.info("Folded padded player names: {} profile(s) renamed, {} quarantined, {} name(s) fixed, {} blocked",
                    renamed.size(), quarantined.size(), namesFixed, blocked.size());
        }
        return report;
    }

    /**
     * Padded name lookup keys grouped by their trimmed form, with their raw entries. An unreadable
     * table yields nothing: without the keys there is no name to fold or to block.
     */
    private static Optional<Map<String, Map<String, byte[]>>> paddedNames(DB nameLookup) {
        Map<String, Map<String, byte[]>> groups = new TreeMap<>();
        try (DBIterator iterator = nameLookup.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                String key = new String(entry.getKey(), StandardCharsets.UTF_8);
                String trimmed = key.strip();
                if (trimmed.equals(key) || trimmed.isEmpty()) {
                    continue;
                }
                groups.computeIfAbsent(trimmed, ignored -> new TreeMap<>()).put(key, entry.getValue());
            }
        } catch (RuntimeException failure) {
            log.error("Could not read the name lookup table; player data saved under padded names is not folded "
                    + "on this start", failure);
            return Optional.empty();
        }
        return Optional.of(groups);
    }

    private static Plan plan(DB nameLookup, Path players, Path quarantine, String trimmed,
                             Map<String, byte[]> paddedEntries) throws IOException, Unclean {
        UUID target = EncryptionUtils.deriveOfflineIdentity(trimmed);

        byte[] trimmedBytes = nameLookup.get(key(trimmed));
        Server.NameEntry trimmedEntry = null;
        if (trimmedBytes != null) {
            trimmedEntry = offlineEntry(trimmed, trimmedBytes);
            if (!trimmedEntry.uuid().equals(target)) {
                throw new Unclean("the key \"" + trimmed + "\" points at " + trimmedEntry.uuid()
                        + " instead of the name's identity " + target);
            }
        }
        Map<String, Server.NameEntry> paddedKeys = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> padded : paddedEntries.entrySet()) {
            Server.NameEntry entry = offlineEntry(padded.getKey(), padded.getValue());
            UUID derived = EncryptionUtils.deriveOfflineIdentity(padded.getKey());
            if (!entry.uuid().equals(derived)) {
                throw new Unclean("the key \"" + padded.getKey() + "\" points at " + entry.uuid()
                        + " instead of its own identity " + derived);
            }
            paddedKeys.put(padded.getKey(), entry);
        }

        List<Profile> profiles = new ArrayList<>();
        Path targetFile = dataFile(players, target);
        Profile targetProfile = null;
        if (Files.exists(targetFile)) {
            targetProfile = owned(target, targetFile, trimmed, true);
            profiles.add(targetProfile);
        } else if (trimmedEntry != null && !quarantinedByThisStep(quarantine, target, trimmed)) {
            throw new Unclean("the key \"" + trimmed + "\" has no data file " + targetFile.getFileName());
        }

        for (Map.Entry<String, Server.NameEntry> padded : paddedKeys.entrySet()) {
            UUID uuid = padded.getValue().uuid();
            Path file = dataFile(players, uuid);
            if (Files.exists(file)) {
                profiles.add(owned(uuid, file, trimmed, false));
                continue;
            }
            boolean renamedByThisStep = targetProfile != null
                    && targetProfile.tag().getString("NameTag").toLowerCase(Locale.ROOT).equals(padded.getKey());
            if (!renamedByThisStep && !quarantinedByThisStep(quarantine, uuid, trimmed)) {
                throw new Unclean("the key \"" + padded.getKey() + "\" has no data file " + file.getFileName());
            }
        }
        if (profiles.isEmpty()) {
            throw new Unclean("no data file of the name is left in players/");
        }
        return new Plan(target, profiles, trimmedEntry, paddedKeys.keySet());
    }

    private static void apply(DB nameLookup, Path players, Path quarantine, FileMover mover, String trimmed, Plan plan,
                              List<Move> renamed, List<Move> quarantined) throws IOException {
        List<Profile> profiles = plan.profiles();
        Profile winner = profiles.stream()
                .max(Comparator.comparing(Profile::played)
                        .thenComparingLong(Profile::lastPlayed)
                        .thenComparingLong(Profile::modified)
                        .thenComparing(Profile::trimmed))
                .orElseThrow();
        Path targetFile = dataFile(players, plan.target());

        Deque<Step> done = new ArrayDeque<>();
        List<Move> quarantinedHere = new ArrayList<>();
        List<Move> renamedHere = new ArrayList<>();
        try {
            for (Profile loser : profiles) {
                if (loser == winner) {
                    continue;
                }
                Path destination = quarantineDestination(quarantine, loser.uuid());
                move(mover, loser.file(), destination);
                done.push(new Step(loser.file(), destination));
                quarantinedHere.add(new Move(loser.uuid(), plan.target(), destination));
            }
            if (!winner.trimmed()) {
                move(mover, winner.file(), targetFile);
                done.push(new Step(winner.file(), targetFile));
                renamedHere.add(new Move(winner.uuid(), plan.target(), targetFile));
            }
            syncDirectory(players);
        } catch (IOException | RuntimeException failure) {
            rollback(mover, done, trimmed, failure);
            throw failure;
        }

        for (Move move : quarantinedHere) {
            Profile loser = profiles.stream().filter(profile -> profile.uuid().equals(move.from())).findFirst().orElseThrow();
            log.warn("Quarantined player data {} of \"{}\" (played {}, lastPlayed {}) to {}: the profile {} "
                            + "(played {}, lastPlayed {}) now belongs to the name as {}. The contents were not merged; "
                            + "compare them by hand before restoring anything",
                    loser.uuid(), trimmed, loser.played(), loser.lastPlayed(), move.destination(),
                    winner.uuid(), winner.played(), winner.lastPlayed(), plan.target());
        }
        for (Move move : renamedHere) {
            log.info("Moved the player data of \"{}\" from the padded identity {} to {}", trimmed, move.from(), plan.target());
        }
        quarantined.addAll(quarantinedHere);
        renamed.addAll(renamedHere);

        // The files are final now. A batch that throws may still have been applied, so it is not
        // answered by moving files back: the caller blocks the name and the next start sees either
        // the folded keys or keys it recognises as left behind by this step.
        writeKeys(nameLookup, trimmed, plan.target(), plan.trimmedEntry(), plan.paddedKeys());
    }

    /** Undo the moves of one name in reverse order; a failed undo is attached to the original failure. */
    private static void rollback(FileMover mover, Deque<Step> done, String trimmed, Exception failure) {
        while (!done.isEmpty()) {
            Step step = done.pop();
            try {
                move(mover, step.to(), step.from());
            } catch (IOException | RuntimeException undo) {
                failure.addSuppressed(undo);
                log.error("Could not undo moving {} to {} while folding \"{}\"; the files need a manual check",
                        step.from(), step.to(), trimmed, undo);
            }
        }
    }

    /** Point the trimmed key at the trimmed identity and drop the padded keys in one atomic batch. */
    private static void writeKeys(DB nameLookup, String trimmed, UUID target, Server.NameEntry current,
                                  Set<String> paddedKeys) throws IOException {
        try (WriteBatch batch = nameLookup.createWriteBatch()) {
            if (current == null) {
                batch.put(key(trimmed), Server.encodeNameEntry(target, Server.NameProvenance.OFFLINE));
            }
            for (String padded : paddedKeys) {
                batch.delete(key(padded));
            }
            nameLookup.write(batch);
        }
    }

    private static Server.NameEntry offlineEntry(String key, byte[] bytes) throws Unclean {
        Server.NameEntry entry = Server.decodeNameEntry(bytes);
        if (entry == null) {
            throw new Unclean("the key \"" + key + "\" holds an invalid entry");
        }
        if (entry.provenance() != Server.NameProvenance.OFFLINE) {
            throw new Unclean("the key \"" + key + "\" was written by a " + entry.provenance() + " login");
        }
        return entry;
    }

    /** Read a data file that must be this offline name's: readable, named after it, without an XUID. */
    private static Profile owned(UUID uuid, Path file, String trimmed, boolean isTarget) throws IOException, Unclean {
        if (!Files.isRegularFile(file)) {
            throw new Unclean(file.getFileName() + " is not a regular file");
        }
        CompoundTag tag = read(file).orElseThrow(() -> new Unclean(file.getFileName() + " cannot be read"));
        String problem = ownershipProblem(tag, trimmed);
        if (problem != null) {
            throw new Unclean(file.getFileName() + " " + problem);
        }
        long firstPlayed = seconds(tag.getLong("firstPlayed"));
        long lastPlayed = seconds(tag.getLong("lastPlayed"));
        // The same test Player uses for playedBefore: a profile created by a login that was
        // refused on the spot holds nothing the player earned and never outranks a real one.
        return new Profile(uuid, file, tag, lastPlayed - firstPlayed > 1, lastPlayed,
                Files.getLastModifiedTime(file).toMillis(), isTarget);
    }

    private static String ownershipProblem(CompoundTag tag, String trimmed) {
        if (tag.get("XUID") instanceof StringTag xuid && !xuid.data.isEmpty()) {
            return "belongs to an Xbox authenticated account";
        }
        if (!(tag.get("NameTag") instanceof StringTag nameTag)) {
            return "carries no NameTag";
        }
        if (!nameTag.data.strip().toLowerCase(Locale.ROOT).equals(trimmed)) {
            return "is saved for \"" + nameTag.data + "\"";
        }
        return null;
    }

    /** A quarantined file of this name for that identity: only this step writes there. */
    private static boolean quarantinedByThisStep(Path quarantine, UUID uuid, String trimmed) throws IOException {
        if (!Files.isDirectory(quarantine)) {
            return false;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(quarantine, uuid + "*.dat")) {
            for (Path file : files) {
                Optional<CompoundTag> tag = Files.isRegularFile(file) ? read(file) : Optional.empty();
                if (tag.isPresent() && ownershipProblem(tag.get(), trimmed) == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<CompoundTag> read(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            return Optional.of(NBTIO.readCompressed(input));
        } catch (IOException | RuntimeException unreadable) {
            log.warn("Player data {} cannot be read: {}", file, unreadable.toString());
            return Optional.empty();
        }
    }

    private static long seconds(long timestamp) {
        return timestamp >= MILLISECOND_TIMESTAMPS ? timestamp / 1000 : timestamp;
    }

    /** The identity a name derives, spelled exactly as given. */
    static UUID rawOfflineIdentity(String name) {
        return EncryptionUtils.deriveOfflineIdentity(name);
    }

    private static Path dataFile(Path players, UUID uuid) {
        return players.resolve(uuid + ".dat");
    }

    private static Path quarantineDestination(Path quarantine, UUID uuid) throws IOException {
        Files.createDirectories(quarantine);
        Path destination = quarantine.resolve(uuid + ".dat");
        if (Files.exists(destination)) {
            destination = quarantine.resolve(uuid + "-" + System.currentTimeMillis() + ".dat");
        }
        return destination;
    }

    private static void move(FileMover mover, Path source, Path destination) throws IOException {
        // An atomic rename silently replaces an existing file on POSIX, so an occupied
        // destination is refused up front rather than overwritten.
        if (Files.exists(destination)) {
            throw new IOException("Refusing to overwrite " + destination + " with " + source);
        }
        mover.move(source, destination);
    }

    private static void atomicMove(Path source, Path destination) throws IOException {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        syncDirectory(destination.getParent());
        syncDirectory(source.getParent());
    }

    private static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(Objects.requireNonNull(directory), StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Not every platform can sync a directory; the rename itself is already atomic.
        }
    }

    private static byte[] key(String name) {
        return name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    }
}
