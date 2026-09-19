package cn.nukkit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * DEPENDENCIES.txt 依赖清单的解析模型与 sha256 工具，供 {@link Bootstrap} 下载引导使用。
 * <p>
 * Parsing model and sha256 helpers for the DEPENDENCIES.txt manifest consumed by {@link Bootstrap}.
 * 只允许使用 JDK：本类运行在任何第三方依赖尚未就位的引导阶段。
 * <p>
 * JDK-only by design: it runs before any third-party dependency is on the classpath.
 */
public final class DependencyManifest {

    private static final String REPOS_PREFIX = "repos=";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String RESOURCE = "/DEPENDENCIES.txt";

    private final List<String> repos;
    private final List<Entry> entries;

    private DependencyManifest(List<String> repos, List<Entry> entries) {
        this.repos = List.copyOf(repos);
        this.entries = List.copyOf(entries);
    }

    public List<String> repos() {
        return repos;
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * 解析清单文本：首个有效行必须是 repos=...，其后每行 g:a:dirVersion:fileName:sha256，支持 # 注释与空行。
     * 格式损坏或 fileName 重复（lib/ 扁平命名空间会互相覆盖）直接抛出异常，不做静默跳过。
     * <p>
     * Parses manifest text: the first effective line must be repos=..., each following line an entry;
     * comments and blank lines are allowed. Malformed lines and duplicate fileNames (which would
     * overwrite each other in the flat lib/ namespace) throw instead of being silently skipped.
     */
    public static DependencyManifest parse(String content) {
        List<String> repos = null;
        List<Entry> entries = new ArrayList<>();
        Set<String> fileNames = new HashSet<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace("\r", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (repos == null) {
                if (!line.startsWith(REPOS_PREFIX)) {
                    throw new IllegalArgumentException("DEPENDENCIES.txt 第 " + (i + 1) + " 行应为 " + REPOS_PREFIX + "...");
                }
                repos = new ArrayList<>();
                for (String repo : line.substring(REPOS_PREFIX.length()).split("\\|")) {
                    String trimmed = repo.trim();
                    if (!trimmed.isEmpty()) {
                        repos.add(trimmed);
                    }
                }
                if (repos.isEmpty()) {
                    throw new IllegalArgumentException("DEPENDENCIES.txt 第 " + (i + 1) + " 行 repos 列表为空");
                }
                continue;
            }
            String[] f = line.split(":", -1);
            if (f.length != 5 || f[0].isEmpty() || f[1].isEmpty() || f[2].isEmpty() || f[3].isEmpty()
                    || !SHA256.matcher(f[4]).matches()) {
                throw new IllegalArgumentException("DEPENDENCIES.txt 第 " + (i + 1) + " 行格式损坏: " + line);
            }
            if (!fileNames.add(f[3])) {
                // lib/ 是扁平命名空间，重名会互相覆盖导致另一依赖缺失 / flat lib/ namespace: a duplicate
                // fileName would overwrite the other artifact and leave it missing at runtime
                throw new IllegalArgumentException("DEPENDENCIES.txt 第 " + (i + 1) + " 行文件名重复 / duplicate fileName: " + f[3]);
            }
            entries.add(new Entry(f[0], f[1], f[2], f[3], f[4]));
        }
        if (repos == null) {
            throw new IllegalArgumentException("DEPENDENCIES.txt 缺少 repos= 行");
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("DEPENDENCIES.txt 没有任何依赖条目");
        }
        return new DependencyManifest(repos, entries);
    }

    /**
     * 从 classpath（与 Bootstrap 同一 jar）读取 DEPENDENCIES.txt。
     * <p>
     * Loads DEPENDENCIES.txt from the classpath (the same jar as Bootstrap).
     */
    public static DependencyManifest loadFromClasspath() throws IOException {
        byte[] bytes;
        try (InputStream in = DependencyManifest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("classpath 中缺少 " + RESOURCE);
            }
            bytes = in.readAllBytes();
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    /** 单条依赖：groupId、artifactId、仓库目录版本、本地文件名与期望 sha256。 */
    public static final class Entry {
        private final String groupId;
        private final String artifactId;
        private final String dirVersion;
        private final String fileName;
        private final String sha256;

        Entry(String groupId, String artifactId, String dirVersion, String fileName, String sha256) {
            this.groupId = Objects.requireNonNull(groupId);
            this.artifactId = Objects.requireNonNull(artifactId);
            this.dirVersion = Objects.requireNonNull(dirVersion);
            this.fileName = Objects.requireNonNull(fileName);
            this.sha256 = Objects.requireNonNull(sha256);
        }

        public String groupId() {
            return groupId;
        }

        public String artifactId() {
            return artifactId;
        }

        public String dirVersion() {
            return dirVersion;
        }

        public String fileName() {
            return fileName;
        }

        public String sha256() {
            return sha256;
        }

        /** 落地到 lib/ 目录的文件。 */
        public Path libFile(Path libsDir) {
            return libsDir.resolve(fileName);
        }

        /** 仓库内相对路径：g(点换斜线)/a/dirVersion/fileName。 */
        public String repositoryPath() {
            return groupId.replace('.', '/') + '/' + artifactId + '/' + dirVersion + '/' + fileName;
        }

        /** 仓库基地址（自动补尾部斜线）拼出的下载 URL。 */
        public String downloadUrl(String repoBase) {
            String base = repoBase.endsWith("/") ? repoBase : repoBase + "/";
            return base + repositoryPath();
        }

        @Override
        public String toString() {
            return groupId + ':' + artifactId + ':' + dirVersion + ':' + fileName;
        }
    }

    /**
     * 流式计算文件 sha256，返回小写十六进制。
     * <p>
     * Streams the file and returns its sha256 as lowercase hex.
     */
    public static String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
