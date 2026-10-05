import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import java.security.MessageDigest

plugins {
    id("java-library")
    id("maven-publish")
    id("application")
    alias(libs.plugins.shadow)
    alias(libs.plugins.git)
}

abstract class JavaAgentArgumentProvider : CommandLineArgumentProvider {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> {
        return classpath.files.map { "-javaagent:${it.absolutePath}" }
    }
}

group = "cn.nukkit"
version = "MOT-SNAPSHOT"

java {
    // 用 JDK 21 构建，但字节码经下方 release=17 钉在 Java 17（与 pom.xml release=17 一致），
    // 产物仍可跑在 JRE 17+；缺了 release，字节码会直接跟 toolchain 变成 class file 65
    // Build with JDK 21 while release=17 below pins bytecode to Java 17 (matching pom.xml);
    // without release, the output would follow the toolchain as class file 65
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

// 覆盖 main/test/buildTools 全部编译任务，产物统一 Java 17 兼容
// （下方 compileJava21 是唯一例外：MR-JAR 版本化类，编译为 21 字节码落在 META-INF/versions/21）
// Covers every compile task (main/test/buildTools); output stays Java 17 compatible.
// Sole exception: compileJava21 below — MR-JAR versioned classes compiled at release 21
// into META-INF/versions/21.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

// MR-JAR：src/main/java21 的虚拟线程实现类以 release 21 单独编译，产物经 sourceSet output
// 进入 jar/liteJar/shadowJar 的 META-INF/versions/21；JVM 21+ 自动选中、17 自动忽略。
// 与 pom.xml 的 compile-java21 execution 对应，JavaReleaseConsistencyTest 守卫两侧一致。
// MR-JAR: virtual-thread impl under src/main/java21 compiles at release 21 into
// META-INF/versions/21 (reaches jar/liteJar/shadowJar via the sourceSet output).
// JVM 21+ picks these classes automatically, 17 ignores them. Mirrors the pom's
// compile-java21 execution; JavaReleaseConsistencyTest guards both sides.
val compileJava21 by tasks.registering(JavaCompile::class) {
    source = fileTree("src/main/java21")
    // 直接引用 compileJava 的输出目录而非 sourceSet output，避免与 output.dir(builtBy) 成环
    // Points at compileJava's output dir instead of the sourceSet output to avoid a
    // builtBy cycle with the output.dir registration below
    classpath = sourceSets.main.get().compileClasspath + files(tasks.compileJava.flatMap { it.destinationDirectory })
    destinationDirectory.set(layout.buildDirectory.dir("classes/java21/META-INF/versions/21"))
    options.release.set(21)
    options.encoding = "UTF-8"
}

sourceSets.main {
    output.dir(layout.buildDirectory.dir("classes/java21"), "builtBy" to compileJava21)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    maven("https://repo.lanink.cn/repository/maven-public/")
    maven("https://repo.okaeri.cloud/releases")
}

val mockitoAgent by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// 对齐 pom 的有效 runtime 图：jsr305（Maven provided）与 error_prone_annotations（pom 逐依赖排除）
// 不进 Maven runtime，Gradle 侧须显式排除
// Align with the pom's effective runtime graph: neither reaches Maven runtime, exclude here
configurations.runtimeClasspath {
    exclude(group = "com.google.code.findbugs", module = "jsr305")
    exclude(group = "com.google.errorprone", module = "error_prone_annotations")
}

dependencies {
    // 与 pom.xml dependencyManagement 同步的传递依赖 pin，防 Gradle 解析漂移
    // Transitive pins mirroring pom.xml dependencyManagement, guarding against resolution drift
    constraints {
        implementation("net.jodah:expiringmap:${libs.versions.expiringmap.get()}")
        implementation("org.slf4j:slf4j-api:${libs.versions.slf4j.api.get()}")
        // Maven nearest-wins 解析出 2.18.0，Gradle highest-wins 会漂到 2.20，strictly 钉死对齐
        // Maven resolves 2.18.0 while Gradle highest-wins drifts to 2.20; pin strictly
        implementation("com.fasterxml.jackson.core:jackson-annotations") { version { strictly("2.18.0") } }
        // nbt 3.0.5 把 putList(List) 改成 Collection（二进制不兼容），主代码按 3.0.3 编译；
        // Maven 有效版本恒为 3.0.3，strictly 钉死对齐
        // nbt 3.0.5 is binary incompatible (putList(List) -> Collection); Maven stays on 3.0.3
        implementation("org.cloudburstmc:nbt") { version { strictly("3.0.3.Final") } }
        // 传递快照按 Maven 解析的时间戳版本 pin，文件名与 Maven 一致（bare -SNAPSHOT 的 Gradle
        // 缓存文件名远程不可下载）
        // Pin transitive snapshots to Maven's timestamped versions so file names match
        implementation("net.daporkchop.lib:common:0.5.9-20250718.163325-7")
        implementation("net.daporkchop.lib:unsafe:0.5.9-20250718.163325-7")
    }
    api(libs.nethernet) {
        exclude("io.netty")
        // 改用下方 arch-detect：自带全部平台原生库；Use arch-detect below, it bundles every platform's natives
        exclude("dev.opencollab", "libdatachannel-java")
    }
    api(libs.libdatachannel)
    api(libs.libdatachannel.arch.detect) {
        exclude("dev.opencollab", "libdatachannel-java")
    }
    api(libs.raknet) {
        exclude("io.netty", "netty-common")
        exclude("io.netty", "netty-codec-base")
        exclude("io.netty", "netty-buffer")
        exclude("io.netty", "netty-transport")
        exclude("io.netty", "netty-transport-native-unix-common")
        exclude("io.netty", "netty-codec-haproxy")
    }
    api(libs.netty.epoll)
    api(libs.netty.codec.haproxy)
    api(libs.netty.codec.http)
    api(libs.nukkitx.natives)

    api(libs.cloudburst.common) {
        exclude("org.cloudburstmc.math", "immutable")
        exclude("io.netty", "netty-buffer")
        exclude("org.cloudburstmc.fastutil.maps", "int-object-maps")
        exclude("org.cloudburstmc.fastutil.maps", "object-int-maps")
    }

    api(libs.fastutil)
    api(libs.guava)
    api(libs.gson)
    api(libs.caffeine) {
        exclude("org.checkerframework", "checker-qual")
        exclude("com.google.errorprone", "error_prone_annotations")
    }
    api(libs.bundles.snakeyaml)
    api(libs.jackson.dataformat.toml)
    api(libs.okaeri.configs.yaml.snakeyaml)
    api(libs.nimbus.jose.jwt)
    api(libs.asm)
    api(libs.bundles.leveldb)
    api(libs.bundles.terminal)
    api(libs.bundles.log4j)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.log4j.core)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testAnnotationProcessor(libs.log4j.core)

    compileOnly(libs.jsr305)

    api(libs.snappy)

    api(libs.daporkchop.natives) {
        exclude("io.netty", "netty-buffer")
    }

    api(libs.sentry)
    api(libs.commons.math3)
    api(libs.snappy.java)
    api(libs.oshi.core)
    compileOnly(libs.annotations)

    api(libs.jose4j) {
        exclude("org.slf4j", "slf4j-api")
    }

    api(libs.block.state.updater)

    testImplementation(libs.cloudburst.bedrock.codec) {
        exclude("io.netty", "netty-buffer")
    }
    testImplementation(libs.cloudburst.math)
    testImplementation(libs.netease.protocol.extension)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.bundles.mockito)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    add("mockitoAgent", libs.mockito.core.get())
}

application {
    mainClass.set("cn.nukkit.Nukkit")
}

// Reproducible archives (mirrors the Maven setup in pom.xml)
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

gitProperties {
    // Only the fields Nukkit.GIT_INFO reads; the rest vary per build environment
    keys = listOf("git.branch", "git.commit.id.abbrev")
    failOnNoGitDirectory = false
}

publishing {
    repositories {
        maven {
            name = "repo-lanink-cn-snapshots"
            url = uri("https://repo.lanink.cn/repository/maven-snapshots/")
            credentials {
                username = System.getenv("DEPLOY_USERNAME")
                password = System.getenv("DEPLOY_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}


// ---------------------------------------------------------------------------
// Bootstrap Lite 发行版与 DEPENDENCIES.txt 生成，对齐 Maven 侧管线；
// 镜像列表双写由 DependencyManifestReposConsistencyTest 守护
// Bootstrap Lite flavor and DEPENDENCIES.txt generation, mirroring the Maven pipeline;
// the duplicated mirror list is guarded by DependencyManifestReposConsistencyTest
// ---------------------------------------------------------------------------

// build-tools 源码由 Maven 侧（build-helper 编译 + exec 运行），Gradle 不消费；
// 单独源集只为编译检查，防该文件只在 Gradle 工作流下改动时烂掉。
// Maven 侧与主代码同 classpath 编译（MinifyJsonResources 用 Gson），此处同样挂主 classpath
// The build-tools sources are compiled and run by the Maven pipeline only; this standalone
// source set is a compile check so Gradle-side edits cannot rot the file. Maven compiles
// them against the main classpath (MinifyJsonResources uses Gson); mirrored here.
val buildTools = sourceSets.create("buildTools") {
    java.srcDir("src/build-tools/java")
}
buildTools.compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath

// 与 GenerateDependencyManifest.DEFAULT_REPOS 同步（顺序即下载失败回退优先级）
// Keep in sync with GenerateDependencyManifest.DEFAULT_REPOS (order is failover priority)
val dependencyDownloadRepos = listOf(
    "https://repo1.maven.org/maven2/",
    "https://repo.opencollab.dev/maven-releases/",
    "https://repo.okaeri.cloud/releases",
    "https://maven.daporkchop.net/",
    "https://repo.lanink.cn/repository/maven-public/"
)

// 与 GenerateDependencyManifest.dirVersion 同步：时间戳快照版本折回 baseVersion-SNAPSHOT 仓库目录
// （正则里 \$ 是字面美元符而非行尾锚，用 \Z 表输入结尾）
// Mirrors GenerateDependencyManifest.dirVersion: timestamped snapshots fold back to the
// baseVersion-SNAPSHOT directory (\$ is a literal dollar, \Z is end-of-input)
val timestampedSnapshotSuffix = Regex("-\\d{8}\\.\\d{6}-\\d+\\Z")

fun repositoryDirVersion(version: String): String {
    if (version.endsWith("-SNAPSHOT")) {
        return version
    }
    val match = timestampedSnapshotSuffix.find(version) ?: return version
    return version.substring(0, match.range.first) + "-SNAPSHOT"
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

val generatedDependencyManifest = layout.buildDirectory.file("generated/dependency-manifest/DEPENDENCIES.txt")

// 解析 runtimeClasspath 生成与 Maven 同格式的 DEPENDENCIES.txt；不复用 GenerateDependencyManifest
// 是因为它按 ~/.m2 布局定位构件，Gradle-only 机器不可用
// Not reusing GenerateDependencyManifest: it locates artifacts via the ~/.m2 layout
val generateDependencyManifest by tasks.registering {
    val runtimeClasspath = configurations.runtimeClasspath.get()
    inputs.files(runtimeClasspath)
    outputs.file(generatedDependencyManifest)
    doLast {
        val entries = LinkedHashMap<String, String>()
        for (artifact in runtimeClasspath.incoming.artifacts.resolvedArtifacts.get()) {
            val component = artifact.id.componentIdentifier
            check(component is ModuleComponentIdentifier) {
                "非外部模块构件无法进下载清单 / non-module artifact in runtime classpath: ${artifact.file}"
            }
            val line = "${component.group}:${component.module}:" +
                    "${repositoryDirVersion(component.version)}:${artifact.file.name}:${sha256Hex(artifact.file)}"
            val previous = entries.putIfAbsent(artifact.file.name, line)
            check(previous == null || previous == line) {
                "lib/ 文件名冲突 / duplicate lib file name: ${artifact.file.name}"
            }
        }
        generatedDependencyManifest.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                "repos=" + dependencyDownloadRepos.joinToString("|") + "\n" +
                        entries.values.joinToString("\n") + "\n"
            )
        }
        logger.lifecycle("Generated DEPENDENCIES.txt with {} runtime dependencies", entries.size)
    }
}

// Bootstrap 引导的 Lite thin jar：Main-Class=Bootstrap + Class-Path=lib/... + 内嵌清单，
// 产物名与落点（target/*-Lite.jar）与 Maven 侧 antrun 改名一致
// Bootstrap-flavored thin jar named and placed like the Maven-side antrun rename
val liteJar by tasks.registering(Jar::class) {
    archiveClassifier.set("Lite")
    destinationDirectory.set(file("$projectDir/target"))
    dependsOn(generateDependencyManifest)
    from(sourceSets.main.get().output)
    from(generatedDependencyManifest)
    doFirst {
        // Class-Path 从生成的清单派生，文件名与 DEPENDENCIES.txt 严格一致（对应 maven-jar-plugin 的
        // addClasspath + classpathPrefix lib/）
        // Class-Path derives from the generated manifest so names always match DEPENDENCIES.txt
        val libs = generatedDependencyManifest.get().asFile.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("repos=") }
            .map { "lib/" + it.split(":")[3] }
        manifest.attributes(
            mapOf(
                "Main-Class" to "cn.nukkit.Bootstrap",
                "Class-Path" to libs.joinToString(" "),
                "Multi-Release" to "true"
            )
        )
    }
}


tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(
            listOf(
                "-Alog4j.graalvm.groupId=cn.nukkit",
                "-Alog4j.graalvm.artifactId=Nukkit"
            )
        )
    }

    compileTestJava {
        options.encoding = "UTF-8"
    }

    // 见上方 buildTools 源集：编译检查挂在 check 上 / compile check for the Maven-only
    // build-tools sources, wired into check (build reaches it transitively)
    named<JavaCompile>(buildTools.compileJavaTaskName) {
        options.encoding = "UTF-8"
    }

    check {
        dependsOn(buildTools.compileJavaTaskName)
    }

    test {
        useJUnitPlatform()
        jvmArgumentProviders.add(
            objects.newInstance<JavaAgentArgumentProvider>().apply {
                classpath.from(mockitoAgent)
            }
        )
    }

    // Minify all .json resources in the build output to shrink the JAR.
    // Source files in src/main/resources stay readable; only the copied artifacts are minified.
    // Idempotent: already-minified files are unchanged on a second pass.
    processResources {
        doLast {
            val minifyGson = com.google.gson.GsonBuilder().disableHtmlEscaping().create()
            @Suppress("DEPRECATION")
            val outDir = destinationDir
            outDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                .forEach { file ->
                    val parsed = com.google.gson.JsonParser.parseReader(file.reader(Charsets.UTF_8))
                    file.writeText(minifyGson.toJson(parsed), Charsets.UTF_8)
                    logger.debug("Minified ${file.name}")
                }
        }
    }

    jar {
        archiveClassifier.set("dev")
        manifest.attributes["Multi-Release"] = "true"
    }

    named<Jar>("sourcesJar") {
        // 版本化路径：避免与主源码集的同名文件成重复条目（无策略时 sourcesJar/assemble/build 直接失败），
        // 并与二进制 jar 的 META-INF/versions/21 结构对应
        // Versioned path: avoids duplicate entries against the main source set (which fails
        // sourcesJar/assemble/build without a strategy) and mirrors the binary jar layout
        from("src/main/java21") { into("META-INF/versions/21") }
    }

    assemble {
        dependsOn(liteJar)
    }

    shadowJar {
        manifest.attributes["Multi-Release"] = "true"

        // Shadow 9 defaults to EXCLUDE, which feeds only one source of the duplicated
        // Log4j2Plugins.dat to the transformer below. The project's own (near-empty) cache
        // then wins and log4j-core's built-in plugins are dropped, breaking log4j2.xml
        // loading at runtime (console falls back to StatusLogger with literal § codes).
        // INCLUDE restores the shadow 8 behavior; see GradleUp/shadow#1733.
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        transform(Log4j2PluginsCacheFileTransformer())

        // Backwards compatible jar directory
        destinationDirectory.set(file("$projectDir/target"))
        archiveClassifier.set("")

        // 与 Maven shaded jar 对齐：内嵌 DEPENDENCIES.txt（Bootstrap 不在其入口，仅随包分发）
        // Match the Maven shaded jar, which also embeds DEPENDENCIES.txt
        dependsOn(generateDependencyManifest)
        from(generatedDependencyManifest)

        exclude("javax/annotation/**")

        // Duplicated dependency metadata (LICENSE, netty versions, ...): INCLUDE keeps
        // same-named entries in unstable order, so drop them for reproducibility
        exclude(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES*",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/proguard/**",
            "META-INF/io.netty.versions.properties",
            "META-INF/maven/**",
        )
    }

    runShadow {
        val dir = File(projectDir, "run")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        standardInput = System.`in`
        workingDir = dir
    }

    javadoc {
        options.encoding = "UTF-8"
    }
}
