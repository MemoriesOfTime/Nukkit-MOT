package cn.nukkit.plugin;

import cn.nukkit.Server;
import cn.nukkit.utils.HttpUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
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
import java.nio.file.Path;
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

    /** Maven 坐标段允许的字符集（字母、数字、点、连字符、下划线），排除 {@code /} {@code \} 以杜绝路径穿越。 / Allowed charset for a Maven coordinate segment (alphanumeric, dot, hyphen, underscore); excludes {@code /} {@code \} to prevent path traversal. */
    private static final java.util.regex.Pattern COMPONENT_FORBIDDEN_PATTERN =
            java.util.regex.Pattern.compile("[^A-Za-z0-9.\\-_]");

    /** 坐标 → jar URL 的进程级缓存，跨插件复用解析结果。 / coordinate → jar URL, cached process-wide across plugins. */
    private static final Map<String, URL> RESOLVED_URL_CACHE = new ConcurrentHashMap<>();

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;

    static {
        // XXE 防护分层：标准 JAXP/SAX 特性 fail-closed（失败即中止类初始化），Apache 专有特性 best-effort。
        // / XXE hardening, layered: standard JAXP/SAX features are fail-closed; Apache-specific ones best-effort.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            // 纵深防御；部分 parser 不识别这两个属性，忽略即可。 / Defense-in-depth; some parsers reject these.
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) {
            }
            // Apache Xerces 专有；非 Xerces parser 可能不支持，跳过即可。 / Apache Xerces-specific; skip on non-Xerces.
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to harden DocumentBuilderFactory against XXE: " + e);
        }
        DOCUMENT_BUILDER_FACTORY = factory;
    }

    private LibraryLoader() {
    }

    /**
     * 解析 Maven 坐标，下载 jar（含传递依赖）到 {@code libraries/}，返回所有 jar URL。仓库尝试顺序：用户仓库在前，
     * 兜底仓库其次。隔离语义见 {@link PluginClassLoader}（版本优先，非访问控制）。
     * <p>
     * <b>限制</b>：仅处理根 {@code <dependencies>} 中字面量 version、scope ∈ {compile, runtime}、非 optional 的条目；
     * 不支持 parent 继承、dependencyManagement、BOM、exclusions、relocation、version range、classifier。
     * / Resolves Maven coordinates, downloading jars (incl. transitive deps) into {@code libraries/}, returning all jar URLs.
     * Repository trial order: user-declared first, fallbacks second. Isolation semantics: see {@link PluginClassLoader}
     * (version-preference, not access control).
     * <p>
     * <b>Limitations</b>: handles only root {@code <dependencies>} with literal versions, scope ∈ {compile, runtime},
     * optional != true; no parent inheritance, dependencyManagement, BOM, exclusions, relocation, version ranges, classifiers.
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
        Path basePath = baseFolder.getAbsoluteFile().toPath().normalize();

        List<String> repositories = mergeRepositories(userRepositories);

        // nearest-wins：Pass 1 先注册直接声明，Pass 2 处理传递依赖时跳过已注册的 g:a。
        // / nearest-wins: Pass 1 registers direct deps first; Pass 2 skips any g:a already registered.
        Map<String, Coordinate> resolved = new LinkedHashMap<>();
        Set<String> directGAKeys = new LinkedHashSet<>();

        List<Coordinate> directCoords = new ArrayList<>(coordinates.size());
        for (String raw : coordinates) {
            Coordinate root = parse(raw);
            String gaKey = root.groupId() + ":" + root.artifactId();
            if (directGAKeys.add(gaKey)) {
                directCoords.add(root);
                ensureArtifactDownloaded(root, basePath, repositories, logger, resolved);
            } else {
                Coordinate first = resolved.get(gaKey);
                if (first != null && !first.version().equals(root.version())) {
                    logger.warning("[LibraryLoader] Duplicate direct declaration for " + gaKey
                            + ": keeping " + first.key() + ", ignoring " + root.key());
                }
            }
        }

        Set<String> visited = new HashSet<>(directGAKeys);
        for (Coordinate root : directCoords) {
            resolveTransitive(root, basePath, repositories, logger, resolved, visited);
        }

        List<URL> urls = new ArrayList<>(resolved.size());
        for (Coordinate c : resolved.values()) {
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

    /** 下载并缓存一个坐标的 jar（含路径穿越校验），不解析 POM。 / Downloads and caches a coordinate's jar (with path-traversal validation); does not parse the POM. */
    private static void ensureArtifactDownloaded(Coordinate coord, Path basePath, List<String> repositories,
                                                 cn.nukkit.utils.MainLogger logger, Map<String, Coordinate> resolved) {
        String gaKey = coord.groupId() + ":" + coord.artifactId();
        if (resolved.containsKey(gaKey)) {
            return;
        }

        URL jarUrl = RESOLVED_URL_CACHE.get(coord.key());
        if (jarUrl == null) {
            jarUrl = downloadArtifact(coord, "jar", basePath, repositories, logger);
            RESOLVED_URL_CACHE.putIfAbsent(coord.key(), jarUrl);
        }
        resolved.put(gaKey, coord);
    }

    /** 递归解析传递依赖；调用方须保证 coord 的 jar 已在 {@code resolved} 中。 / Recursively resolves transitive deps; caller must ensure coord's jar is already in {@code resolved}. */
    private static void resolveTransitive(Coordinate coord, Path basePath, List<String> repositories,
                                          cn.nukkit.utils.MainLogger logger,
                                          Map<String, Coordinate> resolved, Set<String> visited) {
        File pomFile = downloadArtifactFile(coord, "pom", basePath, repositories, logger);
        List<Coordinate> transitive = Collections.emptyList();
        try {
            transitive = parsePom(pomFile);
        } catch (LibraryLoadException e) {
            // pom 解析失败不致命：jar 仍可用，仅丢掉传递依赖。 / Non-fatal: jar still usable, transitive deps dropped.
            logger.warning("[LibraryLoader] Failed to parse pom of " + coord.key() + ": " + e.getMessage());
        }

        for (Coordinate child : transitive) {
            String gaKey = child.groupId() + ":" + child.artifactId();
            if (!visited.add(gaKey)) {
                continue;
            }
            // nearest-wins：已被注册（直接声明或先到传递依赖）则跳过。 / nearest-wins: skip if already registered.
            if (resolved.containsKey(gaKey)) {
                continue;
            }
            ensureArtifactDownloaded(child, basePath, repositories, logger, resolved);
            resolveTransitive(child, basePath, repositories, logger, resolved, visited);
        }
    }

    /** 下载 artifact 到 {@code .tmp} 临时文件再原子落盘，避免半成品文件。 / Downloads to {@code .tmp} then atomically moves into place to avoid partial files. */
    private static URL downloadArtifact(Coordinate coord, String type, Path basePath, List<String> repositories, cn.nukkit.utils.MainLogger logger) {
        try {
            return downloadArtifactFile(coord, type, basePath, repositories, logger).toURI().toURL();
        } catch (java.net.MalformedURLException e) {
            // 本地文件 URI 不应抛此异常；转 fail-fast。 / Should not happen for local file URIs; fail fast.
            throw new LibraryLoadException("Invalid URL for " + coord.key() + ": " + e.getMessage());
        }
    }

    /** 下载 artifact（jar 或 pom），按 Maven 布局放到 {@code libraries/<group>/<artifact>/<version>/}；已存在则复用。 / Downloads by Maven layout; reuses an existing file. */
    private static File downloadArtifactFile(Coordinate coord, String type, Path basePath, List<String> repositories, cn.nukkit.utils.MainLogger logger) {
        String suffix = "jar".equals(type) ? JAR_SUFFIX : POM_SUFFIX;
        File target = artifactFile(coord, suffix, basePath);

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
        // HttpClient.BodyHandlers.ofFile 不创建父目录。 / HttpClient.BodyHandlers.ofFile does not create parent dirs.
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

    /**
     * 按坐标构造目标文件。{@link #parse(String)} 已校验各段字符；此方法再做一层归一化比对，
     * 确保目标仍在 {@code basePath} 之下（防 {@code ..} 或绝对路径穿越）。
     * / Builds the target file by Maven layout. {@link #parse(String)} validates each segment; this adds
     * a second layer ensuring the normalized target stays under {@code basePath}.
     */
    private static File artifactFile(Coordinate coord, String suffix, Path basePath) {
        File target = new File(basePath.toFile(), coord.repositoryPath() + '/' + coord.fileName(suffix));
        Path normalized = target.toPath().toAbsolutePath().normalize();
        if (!normalized.startsWith(basePath)) {
            throw new LibraryLoadException("Coordinate escapes libraries directory: " + coord.key());
        }
        return normalized.toFile();
    }

    /** 解析 pom，提取根 {@code <dependencies>}（不含 {@code <dependencyManagement>}）里 scope ∈ {compile, runtime} 且非 optional 的依赖。 / Parses a pom, extracting root {@code <dependencies>} (not {@code <dependencyManagement>}) with scope ∈ {compile, runtime} and optional != true. */
    static List<Coordinate> parsePom(File pomFile) {
        List<Coordinate> result = new ArrayList<>();
        try {
            DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            Document doc = builder.parse(pomFile);
            doc.getDocumentElement().normalize();
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
                    if (scope != null && !scope.isEmpty()
                            && !scope.equals("compile") && !scope.equals("runtime")) {
                        continue;
                    }
                    if ("true".equalsIgnoreCase(optional)) {
                        continue;
                    }
                    // ${...} 占位符无法解析（不展开 properties）。 / ${...} placeholders can't be resolved.
                    if (version.indexOf('$') >= 0 || version.indexOf('{') >= 0 || version.indexOf('}') >= 0) {
                        continue;
                    }
                    try {
                        result.add(parse(groupId + ":" + artifactId + ":" + version));
                    } catch (LibraryLoadException e) {
                        // 来自 POM 的坐标也过路径校验；非法则跳过而非让整个 POM 失败。 / POM-sourced coords also validated; skip on failure rather than failing the whole POM.
                    }
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

    /**
     * 解析 {@code groupId:artifactId:version}。每段经 {@link #validateComponent} 校验，
     * 配合 {@link Coordinate#repositoryPath()} 杜绝路径穿越。
     * / Parses {@code groupId:artifactId:version}; each segment is validated by {@link #validateComponent},
     * and combined with {@link Coordinate#repositoryPath()} prevents path traversal.
     */
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
        validateComponent(groupId, "groupId", coordinate);
        validateComponent(artifactId, "artifactId", coordinate);
        validateComponent(version, "version", coordinate);
        return new Coordinate(groupId, artifactId, version);
    }

    /**
     * 校验单个 Maven 坐标段：禁止路径分隔符、纯点段、首尾点、控制字符。
     * / Validates a single Maven coordinate segment: forbids path separators, pure-dot segments,
     * leading/trailing dots, and control characters.
     */
    private static void validateComponent(String value, String which, String original) {
        if (value.isEmpty()) {
            throw new LibraryLoadException("Invalid Maven coordinate (empty " + which + "): " + original);
        }
        if (value.equals("..") || value.equals(".")) {
            throw new LibraryLoadException("Invalid Maven coordinate (" + which + " is a dot segment): " + original);
        }
        if (value.startsWith(".") || value.endsWith(".")) {
            // 阻断 ".." 穿越 + 规避 Windows/Unix 解析差异。 / Blocks ".." traversal + platform-specific quirks.
            throw new LibraryLoadException("Invalid Maven coordinate (" + which + " has leading/trailing dot): " + original);
        }
        if (COMPONENT_FORBIDDEN_PATTERN.matcher(value).find()) {
            // 主要拦截路径分隔符 / \。 / Primarily blocks path separators / \.
            throw new LibraryLoadException("Invalid Maven coordinate (" + which + " contains illegal character): " + original);
        }
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
