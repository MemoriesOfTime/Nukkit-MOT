import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import org.gradle.api.tasks.bundling.AbstractArchiveTask

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
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
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

dependencies {
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
