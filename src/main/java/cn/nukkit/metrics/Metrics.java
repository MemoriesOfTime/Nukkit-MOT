package cn.nukkit.metrics;

import cn.nukkit.utils.MainLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;

/**
 * bStats collects some data for plugin authors.
 * <p>
 * Check out <a href="https://bStats.org/">...</a> to learn more about bStats!
 * <p>
 * DNS best IP selection and custom SSLSocketFactory adapted from
 * <a href="https://github.com/mc-dreamland/Geyser/blob/netease/1.21/core/src/main/java/org/geysermc/geyser/util/Metrics.java">Geyser Metrics</a>
 */
public class Metrics {

    private static final int B_STATS_VERSION = 1;

    private static final String BSTATS_HOST = "bStats.org";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // A list with all custom charts
    private final List<CustomChart> charts = new ArrayList<>();

    // The name of the server software
    private final String name;

    // The uuid of the server
    private final String serverUUID;

    // Should failed requests be logged?
    private static boolean logFailedRequests = false;

    // The logger for the failed requests
    private static MainLogger logger;

    // Cache for DNS best-address selection (valid for 30 minutes)
    private static final long ADDRESS_CACHE_DURATION_MS = 1000 * 60 * 30;
    private static InetAddress cachedAddress;
    private static long cacheExpireTime;

    public Metrics(String name, String serverUUID, boolean logFailedRequests, MainLogger logger) {
        this.name = name;
        this.serverUUID = serverUUID;
        Metrics.logFailedRequests = logFailedRequests;
        Metrics.logger = logger;

        // Start submitting the data
        startSubmitting();
    }

    /**
     * Adds a custom chart.
     *
     * @param chart The chart to add.
     */
    public void addCustomChart(CustomChart chart) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null!");
        }
        charts.add(chart);
    }

    /**
     * Shuts down the scheduler to prevent thread leaks.
     */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * Starts the Scheduler which submits our data every 30 minutes.
     */
    private void startSubmitting() {
        final Runnable submitTask = this::submitData;

        // Many servers tend to restart at a fixed time at xx:00 which causes an uneven distribution of requests on the
        // bStats backend. To circumvent this problem, we introduce some randomness into the initial and second delay.
        // WARNING: You must not modify and part of this Metrics class, including the submit delay or frequency!
        // WARNING: Modifying this code will get your plugin banned on bStats. Just don't do it!
        long initialDelay = (long) (1000 * 60 * (3 + ThreadLocalRandom.current().nextDouble() * 3));
        long secondDelay = (long) (1000 * 60 * (ThreadLocalRandom.current().nextDouble() * 30));
        scheduler.schedule(submitTask, initialDelay, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(submitTask, initialDelay + secondDelay, 1000 * 60 * 30, TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the plugin specific data.
     *
     * @return The plugin specific data.
     */
    private JsonObject getPluginData() {
        JsonObject data = new JsonObject();
        data.add("pluginName", new JsonPrimitive(name)); // Append the name of the server software
        JsonArray customCharts = new JsonArray();
        for (CustomChart customChart : charts) {
            // Add the data of the custom charts
            JsonObject chart = customChart.getRequestJsonObject();
            if (chart == null) { // If the chart is null, we skip it
                continue;
            }
            customCharts.add(chart);
        }
        data.add("customCharts", customCharts);
        return data;
    }

    /**
     * Gets the server specific data.
     *
     * @return The server specific data.
     */
    private JsonObject getServerData() {
        // OS specific data
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");
        int coreCount = Runtime.getRuntime().availableProcessors();

        JsonObject data = new JsonObject();
        data.add("serverUUID", new JsonPrimitive(serverUUID));
        data.add("osName", new JsonPrimitive(osName));
        data.add("osArch", new JsonPrimitive(osArch));
        data.add("osVersion", new JsonPrimitive(osVersion));
        data.add("coreCount", new JsonPrimitive(coreCount));
        return data;
    }

    /**
     * Collects the data and sends it afterwards.
     */
    private void submitData() {
        final JsonObject data = getServerData();

        JsonArray pluginData = new JsonArray();
        pluginData.add(getPluginData());
        data.add("plugins", pluginData);

        try {
            // We are still in the Thread of the timer, so nothing get blocked :)
            sendData(data);
        } catch (Exception e) {
            // Something went wrong! :(
            if (logFailedRequests) {
                logger.warning("Could not submit stats of " + name, e);
            }
        }
    }

    /**
     * Resolves all IPs for the bStats host and selects the one with the lowest TCP connect latency.
     * Falls back to the first resolved address if all TCP pings fail.
     *
     * @return The best InetAddress to connect to.
     * @throws UnknownHostException If DNS resolution fails.
     */
    private static InetAddress selectBestAddress() throws UnknownHostException {
        // Return cached result if still valid
        if (cachedAddress != null && System.currentTimeMillis() < cacheExpireTime) {
            return cachedAddress;
        }

        InetAddress[] addresses = InetAddress.getAllByName(BSTATS_HOST);
        if (addresses.length == 1) {
            cachedAddress = addresses[0];
            cacheExpireTime = System.currentTimeMillis() + ADDRESS_CACHE_DURATION_MS;
            return cachedAddress;
        }

        InetAddress bestAddress = addresses[0];
        long bestLatency = Long.MAX_VALUE;

        for (InetAddress address : addresses) {
            long start = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, 443), 3000);
                long latency = System.nanoTime() - start;
                if (latency < bestLatency) {
                    bestLatency = latency;
                    bestAddress = address;
                }
            } catch (IOException ignored) {
                // This address is unreachable, skip it
            }
        }

        // Only cache if at least one address was reachable
        if (bestLatency < Long.MAX_VALUE) {
            cachedAddress = bestAddress;
            cacheExpireTime = System.currentTimeMillis() + ADDRESS_CACHE_DURATION_MS;
        }

        return bestAddress;
    }

    /**
     * Creates an SSLSocketFactory decorator that routes hostname-based connections to the
     * pre-selected best IP address, while preserving SNI for correct TLS handshake.
     *
     * @param targetAddress The pre-selected IP address to connect to.
     * @return The routing SSLSocketFactory.
     */
    private static SSLSocketFactory createRoutingSocketFactory(InetAddress targetAddress) {
        SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        return new SSLSocketFactory() {
            @Override
            public String[] getDefaultCipherSuites() {
                return delegate.getDefaultCipherSuites();
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return delegate.getSupportedCipherSuites();
            }

            @Override
            public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                return delegate.createSocket(s, host, port, autoClose);
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                // Route to pre-selected IP instead of resolving DNS again
                return delegate.createSocket(targetAddress, port);
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                return delegate.createSocket(targetAddress, port, localHost, localPort);
            }

            @Override
            public Socket createSocket(InetAddress host, int port) throws IOException {
                return delegate.createSocket(host, port);
            }

            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                return delegate.createSocket(address, port, localAddress, localPort);
            }
        };
    }

    /**
     * Sends the data to the bStats server.
     *
     * @param data The data to send.
     * @throws Exception If the request failed.
     */
    private static void sendData(JsonObject data) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null!");
        }

        InetAddress bestAddress = selectBestAddress();

        // Use the real hostname in URL so Java's internal hostname check and SNI work naturally.
        // The custom socket factory routes the connection to the pre-selected best IP.
        URL url = URI.create("https://" + BSTATS_HOST + "/submitData/server-implementation").toURL();
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        // Route to best IP without re-resolving DNS
        connection.setSSLSocketFactory(createRoutingSocketFactory(bestAddress));

        // Set timeouts to avoid hanging requests
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);

        // Compress the data to save bandwidth
        byte[] compressedData = compress(data.toString());

        // Add headers
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Connection", "close");
        connection.addRequestProperty("Content-Encoding", "gzip"); // We gzip our request
        connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
        connection.setRequestProperty("Content-Type", "application/json"); // We send our data in JSON format
        connection.setRequestProperty("User-Agent", "MC-Server/" + B_STATS_VERSION);

        // Send data
        connection.setDoOutput(true);
        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            outputStream.write(compressedData);
            outputStream.flush();
        }

        // Check response code for debugging
        int responseCode = connection.getResponseCode();
        if (responseCode != 200 && logFailedRequests) {
            logger.warning("bStats returned HTTP " + responseCode);
        }

        connection.getInputStream().close();
    }

    /**
     * Gzips the given String.
     *
     * @param str The string to gzip.
     * @return The gzipped String.
     * @throws IOException If the compression failed.
     */
    private static byte[] compress(final String str) throws IOException {
        if (str == null) {
            return null;
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return outputStream.toByteArray();
    }

    /**
     * Represents a custom chart.
     */
    public static abstract class CustomChart {

        // The id of the chart
        final String chartId;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         */
        CustomChart(String chartId) {
            if (chartId == null || chartId.isEmpty()) {
                throw new IllegalArgumentException("ChartId cannot be null or empty!");
            }
            this.chartId = chartId;
        }

        private JsonObject getRequestJsonObject() {
            JsonObject chart = new JsonObject();
            chart.add("chartId", new JsonPrimitive(chartId));
            try {
                JsonObject data = getChartData();
                if (data == null) {
                    // If the data is null we don't send the chart.
                    return null;
                }
                chart.add("data", data);
            } catch (Throwable t) {
                return null;
            }
            return chart;
        }

        protected abstract JsonObject getChartData() throws Exception;

    }

    /**
     * Represents a custom simple pie.
     */
    public static class SimplePie extends CustomChart {

        private final Callable<String> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public SimplePie(String chartId, Callable<String> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            String value = callable.call();
            if (value == null || value.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            data.add("value", new JsonPrimitive(value));
            return data;
        }
    }

    /**
     * Represents a custom advanced pie.
     */
    public static class AdvancedPie extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public AdvancedPie(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    continue; // Skip this invalid
                }
                allSkipped = false;
                values.add(entry.getKey(), new JsonPrimitive(entry.getValue()));
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            data.add("values", values);
            return data;
        }
    }

    /**
     * Represents a custom drilldown pie.
     */
    public static class DrilldownPie extends CustomChart {

        private final Callable<Map<String, Map<String, Integer>>> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        public JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Map<String, Integer>> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean reallyAllSkipped = true;
            for (Map.Entry<String, Map<String, Integer>> entryValues : map.entrySet()) {
                JsonObject value = new JsonObject();
                boolean allSkipped = true;
                for (Map.Entry<String, Integer> valueEntry : map.get(entryValues.getKey()).entrySet()) {
                    value.add(valueEntry.getKey(), new JsonPrimitive(valueEntry.getValue()));
                    allSkipped = false;
                }
                if (!allSkipped) {
                    reallyAllSkipped = false;
                    values.add(entryValues.getKey(), value);
                }
            }
            if (reallyAllSkipped) {
                // Null = skip the chart
                return null;
            }
            data.add("values", values);
            return data;
        }
    }

    /**
     * Represents a custom single line chart.
     */
    public static class SingleLineChart extends CustomChart {

        private final Callable<Integer> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public SingleLineChart(String chartId, Callable<Integer> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            int value = callable.call();
            if (value == 0) {
                // Null = skip the chart
                return null;
            }
            data.add("value", new JsonPrimitive(value));
            return data;
        }

    }

    /**
     * Represents a custom multi line chart.
     */
    public static class MultiLineChart extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public MultiLineChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    continue; // Skip this invalid
                }
                allSkipped = false;
                values.add(entry.getKey(), new JsonPrimitive(entry.getValue()));
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            data.add("values", values);
            return data;
        }

    }

    /**
     * Represents a custom simple bar chart.
     */
    public static class SimpleBarChart extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public SimpleBarChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                JsonArray categoryValues = new JsonArray();
                categoryValues.add(entry.getValue());
                values.add(entry.getKey(), categoryValues);
            }
            data.add("values", values);
            return data;
        }

    }

    /**
     * Represents a custom advanced bar chart.
     */
    public static class AdvancedBarChart extends CustomChart {

        private final Callable<Map<String, int[]>> callable;

        /**
         * Class constructor.
         *
         * @param chartId The id of the chart.
         * @param callable The callable which is used to request the chart data.
         */
        public AdvancedBarChart(String chartId, Callable<Map<String, int[]>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, int[]> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                if (entry.getValue().length == 0) {
                    continue; // Skip this invalid
                }
                allSkipped = false;
                JsonArray categoryValues = new JsonArray();
                for (int categoryValue : entry.getValue()) {
                    categoryValues.add(categoryValue);
                }
                values.add(entry.getKey(), categoryValues);
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            data.add("values", values);
            return data;
        }

    }
}
