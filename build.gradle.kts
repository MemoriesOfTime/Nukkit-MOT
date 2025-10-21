import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/IDEA-262280

plugins {
    id("java-library")
    id("maven-publish")
    id("application")
    alias(libs.plugins.shadow)
    alias(libs.plugins.git)
}

group = "cn.nukkit"
version = "MOT-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
}

dependencies {
    api(libs.raknet)
    api(libs.netty.epoll)
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
    api(libs.nimbus.jose.jwt)
    api(libs.asm)
    api(libs.bundles.leveldb)
    api(libs.bundles.terminal)
    api(libs.bundles.log4j)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

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

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.engine)
}

application {
    mainClass.set("cn.nukkit.Nukkit")
}

gitProperties {
    dateFormat = "dd.MM.yyyy '@' HH:mm:ss z"
    failOnNoGitDirectory = false
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // 使用 shadow jar 作为发布产物
            project.shadow.component(this)
        }
    }
    repositories {
        mavenLocal()
        maven {
            name = "repo-lanink-cn-snapshots"
            url = uri("https://repo.lanink.cn/repository/maven-snapshots/")
            credentials {
                username = System.getenv("DEPLOY_USERNAME")
                password = System.getenv("DEPLOY_PASSWORD")
            }
        }
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    jar {
        archiveClassifier.set("dev")
    }

    shadowJar {
        manifest.attributes["Multi-Release"] = "true"
        transform(Log4j2PluginsCacheFileTransformer())

        // Backwards compatible jar directory
        destinationDirectory.set(file("$projectDir/target"))
        archiveClassifier.set("")

        exclude("javax/annotation/**")
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

    // 新增任务：在构建 shadow jar 后自动安装到 MavenLocal
    register("shadowPublishToMavenLocal") {
        dependsOn(shadowJar)
        finalizedBy(publishToMavenLocal)
    }
}
