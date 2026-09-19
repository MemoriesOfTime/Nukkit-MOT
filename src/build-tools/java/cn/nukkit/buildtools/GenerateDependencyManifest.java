package cn.nukkit.buildtools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 构建期生成 DEPENDENCIES.txt：把 {@code dependency:list} 的运行时依赖解析为本地仓库文件名与 sha256，
 * 供 {@code cn.nukkit.Bootstrap} 在 lib/ 缺失时按清单自动下载。
 * <p>
 * Build-time generator for DEPENDENCIES.txt: resolves {@code dependency:list} output to concrete
 * local-repository file names plus sha256 digests for {@code cn.nukkit.Bootstrap} to auto-download.
 */
public final class GenerateDependencyManifest {

    private static final List<String> DEFAULT_REPOS = List.of(
            "https://repo.lanink.cn/repository/maven-public/",
            "https://repo1.maven.org/maven2/",
            "https://repo.opencollab.dev/maven-releases/",
            "https://repo.okaeri.cloud/releases",
            "https://maven.daporkchop.net/"
    );

    /** dependency:list 输出携带的 ANSI 颜色转义（ESC[36m / ESC[0;1m / ESC[m 等）。 */
    private static final Pattern ANSI_COLORS = Pattern.compile("\\u001B\\[[0-9;]*m");

    /** dependency:list 中 " -- module xxx [auto]" 后缀的分隔串。 */
    private static final String MODULE_SUFFIX_SEP = " -- ";

    /** 形如 -20260918.234329-18 的远程快照时间戳版本后缀。 */
    private static final Pattern TIMESTAMPED_VERSION = Pattern.compile("-\\d{8}\\.\\d{6}-\\d+$");

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    private GenerateDependencyManifest() {
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: GenerateDependencyManifest <dependency-list-file> <output-file> [<local-repo-root>]");
        }
        Path listFile = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);
        Path repoRoot = args.length == 3
                ? Paths.get(args[2])
                : Paths.get(System.getProperty("user.home"), ".m2", "repository");

        List<Dependency> deps;
        try {
            deps = parseDependencyList(Files.readString(listFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 dependency:list 输出 / cannot read " + listFile, e);
        }
        if (deps.isEmpty()) {
            throw new IllegalStateException("dependency:list 未解析到任何运行时依赖 / no runtime dependencies parsed from " + listFile);
        }

        StringBuilder out = new StringBuilder();
        out.append("repos=").append(String.join("|", DEFAULT_REPOS)).append('\n');
        List<String> failures = new ArrayList<>();
        for (Dependency dep : deps) {
            try {
                LocalArtifact artifact = resolveLocalArtifact(repoRoot, dep);
                String sha = sha256Hex(artifact.file());
                out.append(dep.groupId()).append(':').append(dep.artifactId()).append(':')
                        .append(artifact.dirVersion()).append(':')
                        .append(artifact.file().getFileName().toString()).append(':')
                        .append(sha).append('\n');
            } catch (Exception e) {
                failures.add(dep.groupId() + ":" + dep.artifactId()
                        + (dep.classifier().isEmpty() ? "" : ":" + dep.classifier())
                        + " -> " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("以下依赖无法在本地仓库定位可远程下载的构件 / "
                    + "cannot locate remotely downloadable artifacts for:\n  " + String.join("\n  ", failures));
        }
        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            Files.writeString(outputFile, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法写出清单 / cannot write " + outputFile, e);
        }
        System.out.println("Generated " + outputFile + " with " + deps.size() + " runtime dependencies");
    }

    // ---------------------------------------------------------------- 解析 dependency:list

    record Dependency(String groupId, String artifactId, String version, String classifier) {
    }

    static List<Dependency> parseDependencyList(String content) {
        // 依赖可能出现多个版本（不同 classifier），按 g:a:classifier 去重并保持列表顺序
        Map<String, Dependency> unique = new LinkedHashMap<>();
        for (String raw : content.split("\n", -1)) {
            String line = ANSI_COLORS.matcher(raw).replaceAll("").replace("\r", "");
            int cut = line.indexOf(MODULE_SUFFIX_SEP);
            if (cut >= 0) {
                line = line.substring(0, cut);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(":");
            Dependency dep = null;
            if (f.length == 5 && "jar".equals(f[2]) && isRuntimeScope(f[4])) {
                dep = new Dependency(f[0], f[1], f[3], "");
            } else if (f.length == 6 && "jar".equals(f[2]) && isRuntimeScope(f[5])) {
                dep = new Dependency(f[0], f[1], f[4], f[3]);
            }
            if (dep == null || "sources".equals(dep.classifier()) || "javadoc".equals(dep.classifier())) {
                continue;
            }
            unique.putIfAbsent(dep.groupId() + ':' + dep.artifactId() + ':' + dep.classifier(), dep);
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean isRuntimeScope(String scope) {
        return "compile".equals(scope) || "runtime".equals(scope);
    }

    // ---------------------------------------------------------------- 定位本地构件

    record LocalArtifact(String dirVersion, Path file) {
    }

    /** 仓库目录版本：快照（含时间戳快照）用 baseVersion，release 即版本本身。 */
    static String dirVersion(String version) {
        Matcher m = TIMESTAMPED_VERSION.matcher(version);
        if (version.endsWith("-SNAPSHOT")) {
            return version;
        }
        if (m.find()) {
            return version.substring(0, m.start()) + "-SNAPSHOT";
        }
        return version;
    }

    static LocalArtifact resolveLocalArtifact(Path repoRoot, Dependency dep) throws IOException {
        String dirVersion = dirVersion(dep.version());
        Path dir = repoRoot.resolve(dep.groupId().replace('.', '/'))
                .resolve(dep.artifactId()).resolve(dirVersion);
        if (!Files.isDirectory(dir)) {
            throw new IOException("本地仓库目录不存在 / missing directory: " + dir);
        }
        String classifierSuffix = dep.classifier().isEmpty() ? "" : "-" + dep.classifier();
        String plainName = dep.artifactId() + "-" + dirVersion + classifierSuffix + ".jar";

        if (!dirVersion.endsWith("-SNAPSHOT")) {
            Path exact = dir.resolve(dep.artifactId() + "-" + dep.version() + classifierSuffix + ".jar");
            if (Files.isRegularFile(exact)) {
                return new LocalArtifact(dirVersion, exact);
            }
            throw new IOException("缺少 release 构件 / missing release jar: " + exact.getFileName());
        }

        List<SnapshotJar> timestamped = listSnapshotJars(dir, dep.artifactId(), dirVersion, dep.classifier());
        if (!timestamped.isEmpty()) {
            // 快照产物选择：优先用 a-baseVersion.jar（Maven 解析固定时间戳版本时落下的解析产物副本）的
            // sha256 精确锁定实际解析的文件；无副本可对照时才回退到目录内最新的时间戳构件
            Path plain = dir.resolve(plainName);
            if (Files.isRegularFile(plain)) {
                String plainSha = sha256Hex(plain);
                long plainMtime = Files.getLastModifiedTime(plain).toMillis();
                SnapshotJar best = null;
                for (SnapshotJar jar : timestamped) {
                    if (!sha256Hex(jar.file()).equals(plainSha)) {
                        continue;
                    }
                    if (best == null || jar.mtimeDistance(plainMtime) < best.mtimeDistance(plainMtime)) {
                        best = jar;
                    }
                }
                if (best != null) {
                    return new LocalArtifact(dirVersion, best.file());
                }
            }
            return new LocalArtifact(dirVersion, timestamped.get(timestamped.size() - 1).file());
        }
        if (Files.isRegularFile(dir.resolve(plainName))) {
            throw new IOException("仅有本地 install 产物 " + plainName + "，远程仓库不可下载 / "
                    + "only a locally installed " + plainName + " exists, not downloadable from any repository");
        }
        throw new IOException("目录下没有可用的 jar / no usable jar in " + dir);
    }

    record SnapshotJar(Path file, String stamp, int build, long mtime) implements Comparable<SnapshotJar> {
        long mtimeDistance(long other) {
            return Math.abs(mtime - other);
        }

        @Override
        public int compareTo(SnapshotJar o) {
            int c = stamp.compareTo(o.stamp);
            return c != 0 ? c : Integer.compare(build, o.build);
        }
    }

    /** 列出 a-时间戳-序号[-classifier].jar 形态的远程解析产物，按新旧升序。 */
    private static List<SnapshotJar> listSnapshotJars(Path dir, String artifactId,
                                                       String dirVersion, String classifier) throws IOException {
        String stem = dirVersion.substring(0, dirVersion.length() - "-SNAPSHOT".length());
        Pattern p = Pattern.compile(Pattern.quote(artifactId) + "-" + Pattern.quote(stem)
                + "-(\\d{8})\\.(\\d{6})-(\\d+)"
                + (classifier.isEmpty() ? "" : Pattern.quote("-" + classifier))
                + "\\.jar");
        List<SnapshotJar> jars = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".jar"))
                    .filter(f -> !f.getFileName().toString().endsWith("-sources.jar"))
                    .filter(f -> !f.getFileName().toString().endsWith("-javadoc.jar"))
                    .toList()) {
                Matcher m = p.matcher(file.getFileName().toString());
                if (m.matches()) {
                    jars.add(new SnapshotJar(file, m.group(1) + '.' + m.group(2),
                            Integer.parseInt(m.group(3)),
                            Files.getLastModifiedTime(file).toMillis()));
                }
            }
        }
        jars.sort(Comparator.naturalOrder());
        return jars;
    }

    // ---------------------------------------------------------------- sha256

    static String sha256Hex(Path file) throws IOException {
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

    static boolean isValidSha256(String s) {
        return s != null && HEX_64.matcher(s).matches();
    }
}
