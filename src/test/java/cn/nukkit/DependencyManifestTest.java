package cn.nukkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DEPENDENCIES.txt 解析、路径构建与 sha256 工具的行为锁定。
 * <p>
 * Locks the behavior of DEPENDENCIES.txt parsing, path building and the sha256 helper.
 */
class DependencyManifestTest {

    private static final String VALID_SHA = "a".repeat(64);
    private static final String OTHER_SHA = "b".repeat(64);

    @Test
    void parsesReposLineEntriesCommentsAndBlanks() {
        String content = ""
                + "# 生成于构建期 / generated at build time\n"
                + "\n"
                + "repos=https://repo.lanink.cn/repository/maven-public/|https://repo1.maven.org/maven2/\n"
                + "# 中间注释 / inline comment\n"
                + "it.unimi.dsi:fastutil:8.5.15:fastutil-8.5.15.jar:" + VALID_SHA + "\r\n"
                + "org.cloudburstmc.protocol:common:3.0.0.Beta13-SNAPSHOT:common-3.0.0.Beta13-20260915.235937-24.jar:" + OTHER_SHA + "\n";
        DependencyManifest manifest = DependencyManifest.parse(content);

        assertEquals(2, manifest.repos().size());
        assertEquals("https://repo.lanink.cn/repository/maven-public/", manifest.repos().get(0));
        assertEquals("https://repo1.maven.org/maven2/", manifest.repos().get(1));

        assertEquals(2, manifest.entries().size());
        DependencyManifest.Entry fastutil = manifest.entries().get(0);
        assertEquals("it.unimi.dsi", fastutil.groupId());
        assertEquals("fastutil", fastutil.artifactId());
        assertEquals("8.5.15", fastutil.dirVersion());
        assertEquals("fastutil-8.5.15.jar", fastutil.fileName());
        assertEquals(VALID_SHA, fastutil.sha256());
        assertEquals("org.cloudburstmc.protocol:common:3.0.0.Beta13-SNAPSHOT:common-3.0.0.Beta13-20260915.235937-24.jar",
                manifest.entries().get(1).toString());
    }

    @Test
    void corruptedEntryThrows() {
        String header = "repos=https://repo1.maven.org/maven2/\n";

        // 字段数不足 / too few fields
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "it.unimi.dsi:fastutil:8.5.15:fastutil-8.5.15.jar\n"));
        // 字段数过多 / too many fields
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "g:a:1.0:a-1.0.jar:" + VALID_SHA + ":extra\n"));
        // sha256 非法（长度不足） / invalid sha256 length
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "g:a:1.0:a-1.0.jar:abc123\n"));
        // sha256 非法（大写十六进制） / uppercase hex is rejected
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "g:a:1.0:a-1.0.jar:" + "A".repeat(64) + "\n"));
    }

    @Test
    void missingOrMalformedReposLineThrows() {
        // 缺少 repos= 行 / no repos line at all
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse("g:a:1.0:a-1.0.jar:" + VALID_SHA + "\n"));
        // repos 列表为空 / empty repos list
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse("repos=\ng:a:1.0:a-1.0.jar:" + VALID_SHA + "\n"));
        // 只有 repos、没有任何条目 / repos but no entries
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse("repos=https://repo1.maven.org/maven2/\n"));
    }

    @Test
    void duplicateFileNameThrows() {
        // lib/ 扁平命名空间：重名文件会互相覆盖 / duplicate fileNames would overwrite each other in lib/
        assertThrows(IllegalArgumentException.class, () -> DependencyManifest.parse(""
                + "repos=https://repo1.maven.org/maven2/\n"
                + "g.one:a:1.0:a-1.0.jar:" + VALID_SHA + "\n"
                + "g.two:b:2.0:a-1.0.jar:" + OTHER_SHA + "\n"));
    }

    @Test
    void buildsLibFileNameAndDownloadUrl() {
        DependencyManifest manifest = DependencyManifest.parse(""
                + "repos=https://repo.example.com/releases\n"
                + "org.cloudburstmc.protocol:common:3.0.0.Beta13-SNAPSHOT:common-3.0.0.Beta13-20260915.235937-24.jar:" + VALID_SHA + "\n");
        DependencyManifest.Entry entry = manifest.entries().get(0);

        assertEquals(Path.of("somewhere/lib/common-3.0.0.Beta13-20260915.235937-24.jar"), entry.libFile(Path.of("somewhere/lib")));

        assertEquals("org/cloudburstmc/protocol/common/3.0.0.Beta13-SNAPSHOT/common-3.0.0.Beta13-20260915.235937-24.jar",
                entry.repositoryPath());
        // 缺尾部斜线的仓库基地址要自动补上 / a missing trailing slash is normalized
        assertEquals("https://repo.example.com/releases/org/cloudburstmc/protocol/common/3.0.0.Beta13-SNAPSHOT/common-3.0.0.Beta13-20260915.235937-24.jar",
                entry.downloadUrl("https://repo.example.com/releases"));
        assertEquals("https://repo.example.com/releases/org/cloudburstmc/protocol/common/3.0.0.Beta13-SNAPSHOT/common-3.0.0.Beta13-20260915.235937-24.jar",
                entry.downloadUrl("https://repo.example.com/releases/"));
    }

    @Test
    void sha256OfTempFileMatchesKnownVector() throws IOException {
        Path file = Files.createTempFile("dependency-manifest-sha", ".bin");
        try {
            Files.writeString(file, "abc");
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    DependencyManifest.sha256Hex(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
