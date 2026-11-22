package cn.nukkit.level.structure;

import cn.nukkit.Server;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构模板存储：服务器根目录 {@code structures/} 下的 .mcstructure 文件，以及内存模板。
 * <p>
 * Structure template storage: .mcstructure files under the server's {@code structures/}
 * directory plus in-memory templates.
 */
@Log4j2
public final class StructureAPI {

    /**
     * 结构方块“保存到内存”模式下模板的最大存活时间不设限，与存档模板分开查找。
     * <p>
     * Templates saved by the "save to memory" redstone mode live until replaced or deleted.
     */
    private static final Map<String, Structure> MEMORY = new ConcurrentHashMap<>();

    private StructureAPI() {
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > 256) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '_' || c == '-' || c == '.' || c == ':' || c == '/';
            if (!allowed) {
                return false;
            }
        }
        if (name.contains("..") || name.contains("//") || name.startsWith("/") || name.endsWith("/")) {
            return false;
        }
        int separator = name.indexOf(':');
        return separator < 0 || separator > 0 && separator < name.length() - 1
                && name.lastIndexOf(':') == separator && name.charAt(separator + 1) != '/';
    }

    public static boolean save(Structure structure, String name) {
        File target = resolvePath(name);
        if (target == null) {
            log.warn("Rejected structure name {}", name);
            return false;
        }
        try {
            Files.createDirectories(target.toPath().getParent());
            Path temporary = Files.createTempFile(target.toPath().getParent(), target.getName(), ".saving");
            try {
                try (OutputStream stream = Files.newOutputStream(temporary)) {
                    NBTIO.write(StructureIO.toNbt(structure), stream, java.nio.ByteOrder.LITTLE_ENDIAN, false);
                }
                moveIntoPlace(temporary, target.toPath());
            } finally {
                Files.deleteIfExists(temporary);
            }
            MEMORY.remove(name);
            return true;
        } catch (IOException e) {
            log.error("Cannot save structure {}", name, e);
            return false;
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void saveMemory(Structure structure, String name) {
        if (!isValidName(name)) {
            log.warn("Rejected structure name {}", name);
            return;
        }
        MEMORY.put(name, structure);
    }

    public static Structure load(String name) {
        Structure memory = MEMORY.get(name);
        if (memory != null) {
            return memory;
        }
        File source = resolvePath(name);
        if (source == null || !source.isFile()) {
            return null;
        }
        try {
            CompoundTag tag = NBTIO.read(source, java.nio.ByteOrder.LITTLE_ENDIAN);
            if (tag == null) {
                return null;
            }
            return StructureIO.fromNbt(tag, StructureIO.statePalette());
        } catch (IOException | RuntimeException e) {
            log.warn("Cannot load structure {}", name, e);
            return null;
        }
    }

    /**
     * 校验并把模板名映射到 {@code structures/} 内的文件，拒绝路径穿越。
     * <p>
     * Resolves a template name to a file inside {@code structures/}, rejecting path traversal.
     */
    private static File resolvePath(String name) {
        if (!isValidName(name)) {
            return null;
        }
        File root = new File(Server.getInstance().getDataPath(), "structures");
        File candidate = new File(root, name.replace(':', File.separatorChar) + ".mcstructure");
        try {
            Path rootPath = root.getCanonicalFile().toPath();
            Path resolved = candidate.getCanonicalFile().toPath();
            return resolved.startsWith(rootPath) ? resolved.toFile() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
