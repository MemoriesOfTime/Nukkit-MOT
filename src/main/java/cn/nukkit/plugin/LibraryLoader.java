package cn.nukkit.plugin;

import cn.nukkit.Server;
import cn.nukkit.utils.HttpUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LibraryLoader {

    /** 兜底仓库，仅在用户仓库全部失败时按顺序尝试；命中即停止。 / Fallback repositories tried in order when all user-declared ones fail; first hit wins. */
    private static final String[] FALLBACK_REPOSITORIES = {
            "https://repo.maven.apache.org/maven2/",
            "https://repo.lanink.cn/repository/maven-public/"
    };

    private static final String JAR_SUFFIX = ".jar";
    private static final String POM_SUFFIX = ".pom";

    /** 坐标 → jar URL 的进程级缓存，跨插件复用解析结果。 / coordinate → jar URL, cached process-wide across plugins. */
    private static final Map<String, URL> RESOLVED_URL_CACHE = new ConcurrentHashMap<>();

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;

    static {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(false);
        } catch (Exception e) {
            // 极端情况下回退到默认（无 XXE 防护），不影响主流程 / Fallback to defaults if hardening fails.
        }
        DOCUMENT_BUILDER_FACTORY = factory;
    }

    private LibraryLoader() {
    }

    /**
     * 解析 Maven 坐标列表，下载 jar（含传递依赖）到 {@code libraries/} 文件夹，返回所有 jar 的 URL。<br>
     * Resolves Maven coordinates, downloading jars (incl. transitive deps) into {@code libraries/}, returns all jar URLs.
     *
     * <p>仓库尝试顺序：用户仓库在前，服务端兜底仓库其次，同 URL 仅尝试一次。返回的 URL[] 应交给
     * {@link PluginClassLoader} 注入对应插件，库按插件隔离。<br>Repository trial order: user-declared first,
     * then server fallbacks; each URL is tried at most once. The returned URL[] should be injected into the
     * plugin's {@link PluginClassLoader}; libraries stay per-plugin isolated.
     *
     * @param coordinates      形如 {@code "groupId:artifactId:version"} 的坐标列表 / coordinates like {@code "groupId:artifactId:version"}
     * @param userRepositories 用户声明的额外仓库（null/空 → 只用默认仓库） / extra repositories (null/empty → defaults only)
     * @param logger           下载/解析日志输出 / logger for download/resolution progress
     * @return 所有 jar URL（含传递依赖），不含主插件 jar / URLs of all jars (incl. transitive), excluding the plugin jar
     * @throws LibraryLoadException 坐标格式错误或下载/解析失败 / on malformed coordinate or download/resolution failure
     */
    public static URL[] resolve(List<String> coordinates, List<String> userRepositories, cn.nukkit.utils.MainLogger logger) {
        if (coordinates == null || coordinates.isEmpty()) {
            return new URL[0];
        }
        File baseFolder = getBaseFolder();
        if (!baseFolder.isDirectory() && !baseFolder.mkdirs()) {
            throw new LibraryLoadException("Could not create libraries folder: " + baseFolder.getAbsolutePath());
        }

        List<String> repositories = mergeRepositories(userRepositories);

        // 按 groupId:artifactId 去重（同 g:a 不同 v → first wins），保留首次出现顺序。
        Map<String, Coordinate> resolved = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();

        for (String raw : coordinates) {
            Coordinate root = parse(raw);
            resolveTransitive(root, baseFolder, repositories, logger, resolved, visited);
        }

        List<URL> urls = new ArrayList<>(resolved.size());
        for (Coordinate c : resolved.values()) {
            // resolveTransitive 必然已把每个坐标的 URL 填进缓存；防御性跳过任何意外缺失。
            // resolveTransitive always populates the cache; defensively skip any unexpected miss.
            URL cached = RESOLVED_URL_CACHE.get(c.key());
            if (cached != null) {
                urls.add(cached);
            }
        }
        return urls.toArray(new URL[0]);
    }

    /** 合并用户仓库与兜底仓库，去重保留首次出现顺序，每个 URL 标准化为以 {@code /} 结尾。 / Merges user + fallback repos, dedupe first-wins, normalizing trailing {@code /}. */
    static List<String> mergeRepositories(List<String> userRepositories) {
        List<String> merged = new ArrayList<>();
        java.util.Set<String> seen = new HashSet<>();
        if (userRepositories != null) {
            for (String url : userRepositories) {
                if (url == null) continue;
                String normalized = normalizeRepository(url);
                if (seen.add(normalized)) {
                    merged.add(normalized);
                }
            }
        }
        for (String url : FALLBACK_REPOSITORIES) {
            if (seen.add(url)) {
                merged.add(url);
            }
        }
        return merged;
    }

    /** 规范化仓库 URL：去掉末尾空白，保证以 {@code /} 结尾（拼接 Maven 路径用）。 / Normalize URL: trim and ensure trailing {@code /}. */
    private static String normalizeRepository(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    /**
     * 递归解析一个坐标及其传递依赖。<br>
     * Recursively resolves a coordinate together with its transitive dependencies.
     */
    private static void resolveTransitive(Coordinate coord, File baseFolder, List<String> repositories,
                                          cn.nukkit.utils.MainLogger logger,
                                          Map<String, Coordinate> resolved, Set<String> visited) {
        String gaKey = coord.groupId() + ":" + coord.artifactId();
        if (!visited.add(gaKey)) {
            return; // 防止循环依赖 / cycle guard
        }

        URL jarUrl = RESOLVED_URL_CACHE.get(coord.key());
        if (jarUrl == null) {
            jarUrl = downloadArtifact(coord, "jar", baseFolder, repositories, logger);
            RESOLVED_URL_CACHE.putIfAbsent(coord.key(), jarUrl);
        }

        File pomFile = downloadArtifactFile(coord, "pom", baseFolder, repositories, logger);
        List<Coordinate> transitive = Collections.emptyList();
        try {
            transitive = parsePom(pomFile);
        } catch (LibraryLoadException e) {
            // pom 解析失败不致命：jar 仍可用，仅丢掉传递依赖。 / Non-fatal: jar still usable, transitive deps dropped.
            logger.warning("[LibraryLoader] Failed to parse pom of " + coord.key() + ": " + e.getMessage());
        }

        resolved.put(gaKey, coord);

        for (Coordinate child : transitive) {
            resolveTransitive(child, baseFolder, repositories, logger, resolved, visited);
        }
    }

    /** 下载 artifact 到 {@code .tmp} 临时文件再原子落盘，避免半成品文件。 / Downloads to {@code .tmp} then atomically moves into place to avoid partial files. */
    private static URL downloadArtifact(Coordinate coord, String type, File baseFolder, List<String> repositories, cn.nukkit.utils.MainLogger logger) {
        try {
            return downloadArtifactFile(coord, type, baseFolder, repositories, logger).toURI().toURL();
        } catch (java.net.MalformedURLException e) {
            // 不应发生（本地文件 URI），转成 LibraryLoadException 让上层 fail-fast
            throw new LibraryLoadException("Invalid URL for " + coord.key() + ": " + e.getMessage());
        }
    }

    /** 下载 artifact（jar 或 pom），按 Maven 布局放到 {@code libraries/<group>/<artifact>/<version>/}；已存在则复用。 / Downloads by Maven layout; reuses an existing file. */
    private static File downloadArtifactFile(Coordinate coord, String type, File baseFolder, List<String> repositories, cn.nukkit.utils.MainLogger logger) {
        String suffix = "jar".equals(type) ? JAR_SUFFIX : POM_SUFFIX;
        File target = artifactFile(coord, suffix, baseFolder);

        if (!target.isFile()) {
            boolean ok = false;
            Exception last = null;
            for (String repo : repositories) {
                String url = repo + coord.repositoryPath() + '/' + coord.fileName(suffix);
                try {
                    downloadToTemp(url, target, logger);
                    ok = true;
                    break;
                } catch (Exception e) {
                    last = e;
                }
            }
            if (!ok) {
                throw new LibraryLoadException("Failed to download " + coord.key() + suffix
                        + " from any repository" + (last == null ? "" : ": " + last.getMessage()));
            }
        }
        return target;
    }

    /** 下载到 {@code target.tmp} 临时文件，成功后原子移动到 {@code target}。 / Download to {@code target.tmp} then atomically move to {@code target}. */
    static void downloadToTemp(String url, File target, cn.nukkit.utils.MainLogger logger) throws IOException, InterruptedException {
        logger.info("[LibraryLoader] Downloading " + url);
        // HttpClient.BodyHandlers.ofFile 不创建父目录，必须先建好 <groupPath>/<artifact>/<version>/。
        // HttpClient.BodyHandlers.ofFile does NOT create parent dirs; create them first.
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent.getAbsolutePath());
        }
        File temp = new File(parent, target.getName() + ".tmp");
        if (temp.exists()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
        HttpUtils.downloadFile(url, temp.toPath());
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** 按坐标构造目标文件：{@code libraries/<groupPath>/<artifact>/<version>/<artifact>-<version><suffix>}。 / Target file by Maven layout. */
    private static File artifactFile(Coordinate coord, String suffix, File baseFolder) {
        return new File(new File(baseFolder, coord.repositoryPath()), coord.fileName(suffix));
    }

    /**
     * 解析 pom，提取根 {@code <dependencies>}（不含 {@code <dependencyManagement>}）里 scope ∈ {compile, runtime, 缺省}
     * 且非 optional 的 {@code <dependency>}。<br>
     * Parses a pom, extracting {@code <dependency>} entries from the root {@code <dependencies>} (excluding
     * {@code <dependencyManagement>}) with scope ∈ {compile, runtime, omitted} and optional != true.
     */
    static List<Coordinate> parsePom(File pomFile) {
        List<Coordinate> result = new ArrayList<>();
        try {
            DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            Document doc = builder.parse(pomFile);
            doc.getDocumentElement().normalize();
            // 只取 project 直接子节点的 <dependencies>，跳过 <dependencyManagement> 内的同名节点。
            // Take only <dependencies> that are direct children of <project>, skipping those inside <dependencyManagement>.
            Element root = doc.getDocumentElement();
            NodeList rootChildren = root.getChildNodes();
            for (int i = 0; i < rootChildren.getLength(); i++) {
                Node child = rootChildren.item(i);
                if (!(child instanceof Element)) continue;
                if (!"dependencies".equals(child.getNodeName())) continue;
                NodeList depNodes = ((Element) child).getElementsByTagName("dependency");
                for (int j = 0; j < depNodes.getLength(); j++) {
                    Node depNode = depNodes.item(j);
                    if (!(depNode instanceof Element)) continue;
                    Element dep = (Element) depNode;
                    String groupId = textOf(dep, "groupId");
                    String artifactId = textOf(dep, "artifactId");
                    String version = textOf(dep, "version");
                    String scope = textOf(dep, "scope");
                    String optional = textOf(dep, "optional");
                    if (groupId == null || artifactId == null || version == null) {
                        continue;
                    }
                    // 仅保留 compile/runtime/缺省；过滤 test/provided/import。
                    // Keep only compile/runtime/omitted; drop test/provided/import.
                    if (scope != null && !scope.isEmpty()
                            && !scope.equals("compile") && !scope.equals("runtime")) {
                        continue;
                    }
                    if ("true".equalsIgnoreCase(optional)) {
                        continue;
                    }
                    // 所有 ${...} 占位符都无法解析（不展开 properties）→ 跳过。
                    // Any ${...} placeholder is unresolvable (we don't expand properties) → skip.
                    if (version.startsWith("${") && version.endsWith("}")) {
                        continue;
                    }
                    result.add(new Coordinate(groupId, artifactId, version));
                }
            }
        } catch (Exception e) {
            throw new LibraryLoadException("Pom parse error for " + pomFile.getName() + ": " + e.getMessage());
        }
        return result;
    }

    private static String textOf(Element dep, String tag) {
        NodeList list = dep.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        String text = list.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    /** 解析形如 {@code groupId:artifactId:version} 的坐标字符串，各段自动去除首尾空白。 / Parses a {@code groupId:artifactId:version} string, trimming each segment. */
    static Coordinate parse(String coordinate) {
        if (coordinate == null) {
            throw new LibraryLoadException("Invalid Maven coordinate (expected groupId:artifactId:version): null");
        }
        String[] parts = coordinate.split(":");
        if (parts.length != 3) {
            throw new LibraryLoadException("Invalid Maven coordinate (expected groupId:artifactId:version): " + coordinate);
        }
        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts[2].trim();
        if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
            throw new LibraryLoadException("Invalid Maven coordinate (expected groupId:artifactId:version): " + coordinate);
        }
        return new Coordinate(groupId, artifactId, version);
    }

    /** {@code libraries} 文件夹，位于服务端数据目录下。 / The {@code libraries} folder under the server data path. */
    public static File getBaseFolder() {
        return new File(Server.getInstance().getDataPath(), "libraries");
    }

    @Deprecated
    public static void load(String library) {
        String[] split = library.split(":");
        if (split.length != 3) {
            throw new IllegalArgumentException(library);
        }
        load(new Library() {
            @Override
            public String getGroupId() {
                return split[0];
            }

            @Override
            public String getArtifactId() {
                return split[1];
            }

            @Override
            public String getVersion() {
                return split[2];
            }
        });
    }

    @Deprecated
    public static void load(Library library) {
        String filePath = library.getGroupId().replace('.', '/') + '/' + library.getArtifactId() + '/' + library.getVersion();
        String fileName = library.getArtifactId() + '-' + library.getVersion() + JAR_SUFFIX;

        File folder = new File(getBaseFolder(), filePath);
        if (folder.mkdirs()) {
            Server.getInstance().getLogger().info("[LibraryLoader] Created " + folder.getPath() + '.');
        }

        File file = new File(folder, fileName);
        if (!file.isFile()) try {
            URL url = new URL("https://repo1.maven.org/maven2/" + filePath + '/' + fileName);
            Server.getInstance().getLogger().info("[LibraryLoader] Get library from " + url + '.');
            Files.copy(url.openStream(), file.toPath());
            Server.getInstance().getLogger().info("[LibraryLoader] Get library " + fileName + " done!");
        } catch (IOException e) {
            throw new LibraryLoadException(library);
        }

        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            boolean accessible = method.isAccessible();
            if (!accessible) {
                method.setAccessible(true);
            }
            URLClassLoader classLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
            URL url = file.toURI().toURL();
            method.invoke(classLoader, url);
            method.setAccessible(accessible);
        } catch (NoSuchMethodException | MalformedURLException | IllegalAccessException | InvocationTargetException e) {
            throw new LibraryLoadException(library);
        }

        Server.getInstance().getLogger().info("[LibraryLoader] Load library " + fileName + " done!");
    }

    /**
     * Maven 坐标三元组（不可变）。 / Immutable Maven coordinate triple.
     */
    record Coordinate(String groupId, String artifactId, String version) {
        /** 含 version 的完整坐标键（与 resolveTransitive 中按 g:a 去重的 gaKey 相对）。 / g:a:v key including version. */
        String key() {
            return groupId + ":" + artifactId + ":" + version;
        }

        /** Maven 仓库布局下的相对路径 {@code group/path/artifact/version}。 / Maven repository-relative path. */
        String repositoryPath() {
            return groupId.replace('.', '/') + '/' + artifactId + '/' + version;
        }

        /** 文件名前缀 {@code artifact-version}，调用方拼接 {@code .jar}/{@code .pom} 后缀。 / File name prefix. */
        String fileName(String suffix) {
            return artifactId + '-' + version + suffix;
        }
    }
}
