package cn.nukkit.buildtools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Minifies every {@code .json} file under the build output directory so they take up less space in
 * the packaged JAR. The source files under {@code src/main/resources} stay readable; only the copies
 * copied to {@code target/classes} by the default {@code process-resources} step are rewritten in a
 * single line without any whitespace.
 * <p>
 * Parsing and re-serialization is done with Gson, which is already on the compile classpath. The
 * operation is idempotent: re-running it on an already-minified file produces identical content.
 * <p>
 * This is a build-time tool invoked by the {@code exec-maven-plugin} during the
 * {@code process-classes} phase, mirroring the Gradle {@code processResources} hook.
 */
public final class MinifyJsonResources {

    private MinifyJsonResources() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: MinifyJsonResources <outputDirectory>");
            System.exit(1);
        }
        Path outputDir = Paths.get(args[0]);
        if (!Files.isDirectory(outputDir)) {
            // Nothing copied yet; nothing to do.
            return;
        }

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(path -> minify(path, gson));
        }
    }

    private static void minify(Path path, Gson gson) {
        JsonElement element;
        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            element = JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            gson.toJson(element, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + path, e);
        }
        System.out.println("Minified " + outputDirRel(path) + " (" + fileSize(path) + " bytes)");
    }

    private static String outputDirRel(Path path) {
        Path p = path;
        // print just filename + immediate parent for brevity
        Path parent = p.getParent();
        return (parent == null ? "" : parent.getFileName() + "/") + p.getFileName();
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return -1;
        }
    }
}
