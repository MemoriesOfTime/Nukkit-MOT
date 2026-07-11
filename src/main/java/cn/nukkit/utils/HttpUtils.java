package cn.nukkit.utils;

import cn.nukkit.Nukkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/** Shared HTTP helpers built on {@link java.net.http.HttpClient}. */
public final class HttpUtils {

    /** User-Agent for every request; reuses the git-derived {@code Nukkit.VERSION}. */
    public static final String USER_AGENT = "Nukkit-MOT/" + Nukkit.VERSION;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpUtils() {
    }

    /**
     * GET the response body as a string. Attaches {@link #USER_AGENT} and timeouts.
     *
     * @param url absolute URL to fetch
     * @return the response body
     * @throws IOException          on non-200 status or transport failure
     * @throws InterruptedException if the request is interrupted
     */
    public static String fetchString(String url) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(url).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    /**
     * GET the response body and write it to {@code destination}. Caller handles
     * temp-file/verification/atomic-move orchestration; this just streams bytes to the path.
     *
     * @param url         absolute URL to download
     * @param destination path to write the body to
     * @throws IOException          on non-200 status or transport failure
     * @throws InterruptedException if the request is interrupted
     */
    public static void downloadFile(String url, Path destination) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(url).GET().build();
        HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
    }

    private static HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT);
    }
}
