import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    java
    application
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.6"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
}

group = "com.koshakmine"
version = "1.5.0-SNAPSHOT"
application.mainClass.set("cn.nukkit.Nukkit")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    maven {
        url = uri("https://repo.opencollab.dev/maven-releases/")
        mavenContent { releasesOnly() }
    }
    maven {
        url = uri("https://repo.opencollab.dev/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
    maven {
        url = uri("https://repo.lanink.cn/repository/maven-public/")
    }
    mavenCentral()
    maven("https://storehouse.okaeri.eu/repository/maven-public/")
}

val log4j2Version = "2.17.1"
val jlineVersion = "3.21.0"
val leveldbMcpeJavaVersion = "1.1.0"
val leveldbMcpeJniVersion = "0.0.10"
val blockStateUpdaterVersion = "1.21.110-SNAPSHOT"

dependencies {
    // Compile dependencies
    implementation("org.cloudburstmc.netty:netty-transport-raknet:1.0.0.CR3-SNAPSHOT")
    implementation("io.netty:netty-transport-native-epoll:4.1.101.Final")
    implementation("com.nukkitx:natives:1.0.3")
    implementation("org.cloudburstmc.protocol:common:3.0.0.Beta3-SNAPSHOT") {
        exclude(group = "org.cloudburstmc.math", module = "immutable")
        exclude(group = "io.netty", module = "netty-buffer")
        exclude(group = "org.cloudburstmc.fastutil.maps", module = "int-object-maps")
        exclude(group = "org.cloudburstmc.fastutil.maps", module = "object-int-maps")
    }
    implementation("it.unimi.dsi:fastutil:8.5.15")
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8") {
        exclude(group = "org.checkerframework", module = "checker-qual")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }
    implementation("org.yaml:snakeyaml:2.0")
    implementation("org.snakeyaml:snakeyaml-engine:2.7")
    implementation("eu.okaeri:okaeri-configs-yaml-snakeyaml:5.0.9")
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.2")
    implementation("org.ow2.asm:asm:9.2")
    implementation("org.apache.logging.log4j:log4j-api:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j2Version") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    compileOnly("org.projectlombok:lombok:1.18.36")
    implementation("net.minecrell:terminalconsoleappender:1.3.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
        exclude(group = "org.jline", module = "jline-reader")
        exclude(group = "org.jline", module = "jline-terminal-jna")
        exclude(group = "org.jline", module = "jline-terminal")
    }
    implementation("org.jline:jline-terminal-jna:$jlineVersion") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation("org.jline:jline-reader:$jlineVersion")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.iq80.snappy:snappy:0.5")
    implementation("com.hivemc.leveldb:leveldb:$leveldbMcpeJavaVersion")
    implementation("com.hivemc.leveldb:leveldb-api:$leveldbMcpeJavaVersion")
    implementation("cn.lanink.leveldb:leveldb-mcpe-jni:$leveldbMcpeJniVersion") {
        exclude(group = "com.hivemc.leveldb", module = "leveldb")
        exclude(group = "com.hivemc.leveldb", module = "leveldb-api")
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "io.netty", module = "netty-buffer")
        exclude(group = "net.daporkchop.lib", module = "natives")
        exclude(group = "org.iq80.snappy", module = "snappy")
    }
    implementation("net.daporkchop.lib:natives:0.5.8-SNAPSHOT") {
        exclude(group = "io.netty", module = "netty-buffer")
    }
    implementation("io.sentry:sentry:6.25.0")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")
    implementation("com.github.oshi:oshi-core:5.8.7")
    compileOnly("org.jetbrains:annotations:24.1.0")
    implementation("org.bitbucket.b_c:jose4j:0.9.6") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.cloudburstmc:block-state-updater:$blockStateUpdaterVersion")
    implementation("com.github.daniellansun:fast-reflection:08ec134a5c")
    implementation("org.jctools:jctools-core:4.0.5")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // Annotation processors
    annotationProcessor("org.projectlombok:lombok:1.18.36")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    jar {
        enabled = false
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveBaseName.set("Lumi")
        archiveClassifier.set("")
        manifest {
            attributes(
                "Main-Class" to application.mainClass.get(),
                "Add-Classpath" to "true",
                "Class-Path" to "lib/"
            )
        }
        transform(Log4j2PluginsCacheFileTransformer())
        exclude("META-INF/versions/")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar) {
                classifier = null
            }
            artifact(tasks.generateGitProperties) {
                extension = "properties"
            }
        }
    }
    repositories {
        maven {
            name = "luminiadev"
            url = uri("https://repo.luminiadev.com/snapshots")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

gitProperties {
    dateFormat = "dd.MM.yyyy '@' HH:mm:ss z"
    failOnNoGitDirectory = false
    customProperty("github.repo", "KoshakMineDev/Lumi")
}

tasks.processResources {
    dependsOn("generateGitProperties")
}